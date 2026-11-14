package teafabric.compiler.plugin;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.teavm.model.BasicBlock;
import org.teavm.model.ClassHolder;
import org.teavm.model.ClassHolderTransformer;
import org.teavm.model.ClassHolderTransformerContext;
import org.teavm.model.Instruction;
import org.teavm.model.MethodDescriptor;
import org.teavm.model.MethodHolder;
import org.teavm.model.MethodReference;
import org.teavm.model.Program;
import org.teavm.model.ValueType;
import org.teavm.model.Variable;
import org.teavm.model.instructions.ConstructInstruction;
import org.teavm.model.instructions.InvocationType;
import org.teavm.model.instructions.InvokeInstruction;

/**
 * Rewrites call sites of JDK / library methods that cannot work in a browser to static methods of
 * the web platform layer. Rules are read from every {@code META-INF/teafabric/redirects.txt} on the
 * program class path, one per line:
 *
 * <pre>
 *   owner.Class  methodName  (Ljava/lang/String;)V  -&gt;  target.Class
 * </pre>
 *
 * An instance call {@code owner.m(args)} becomes {@code target.m(owner, args)}; a static call keeps
 * its parameter list. An owner written as {@code *owner.Class} matches the method on any receiver class
 * (call sites of inherited/default methods name the subclass); the target still takes {@code owner.Class}.
 * Super calls ({@code super.m(...)}) are left alone: redirecting them would re-dispatch to the override.
 * A target written as {@code target.Class#name(desc)} names the replacement exactly (the receiver, if any,
 * becomes its first parameter): it can take {@code Object} for types the platform layer cannot compile
 * against, such as classes of an optional mod. Constructors ({@code <init>}) are not supported. Classes whose name starts
 * with an excluded prefix (the redirect targets themselves, TeaVM's class library) are untouched.
 */
public class RedirectTransformer implements ClassHolderTransformer {
    private final Map<MethodReference, String> rules = new HashMap<>();
    /** Rules that match by name and descriptor on any owner: descriptor -> {declared owner, target}. */
    private final Map<MethodDescriptor, String[]> anyOwnerRules = new HashMap<>();
    private final List<String> excluded = new ArrayList<>();

    public RedirectTransformer(ClassLoader classLoader) {
        // TeaVM presents its class library under the java.* names; its natives are intrinsics and must
        // never be touched, and its internals never call the APIs we redirect.
        excluded.add("org.teavm.");
        excluded.add("java.");
        excluded.add("javax.");
        excluded.add("jdk.");
        excluded.add("sun.");
        excluded.add("teafabric.web.compat.");
        try {
            for (URL url : Collections.list(classLoader.getResources("META-INF/teafabric/redirects.txt"))) {
                try (InputStream in = url.openStream();
                     BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = r.readLine()) != null) {
                        parse(line.trim());
                    }
                }
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        System.out.println("[teafabric-plugin] " + rules.size() + " call-site redirects");
    }

    private void parse(String line) {
        if (line.isEmpty() || line.startsWith("#")) {
            return;
        }
        if (line.startsWith("exclude ")) {
            excluded.add(line.substring(8).trim());
            return;
        }
        String[] parts = line.split("\\s+");
        if (parts.length != 5 || !parts[3].equals("->")) {
            throw new IllegalArgumentException("Bad redirect rule: " + line);
        }
        if (parts[0].startsWith("*")) {
            anyOwnerRules.putIfAbsent(MethodDescriptor.parse(parts[1] + parts[2]),
                    new String[] {parts[0].substring(1), parts[4]});
            return;
        }
        // first rule wins: hand-written rules (platform) come before generated stub rules on the class path
        rules.putIfAbsent(new MethodReference(parts[0], MethodDescriptor.parse(parts[1] + parts[2])), parts[4]);
    }

    @Override
    public void transformClass(ClassHolder cls, ClassHolderTransformerContext context) {
        for (String prefix : excluded) {
            if (cls.getName().startsWith(prefix)) {
                return;
            }
        }
        for (MethodHolder method : cls.getMethods()) {
            if (method.hasModifier(org.teavm.model.ElementModifier.NATIVE) && isPlainJni(method)) {
                stubNative(method, context);
                continue;
            }
            Program program = method.getProgram();
            if (program == null) {
                continue;
            }
            for (int i = 0; i < program.basicBlockCount(); i++) {
                BasicBlock block = program.basicBlockAt(i);
                for (Instruction insn : block) {
                    if (insn instanceof InvokeInstruction) {
                        rewrite((InvokeInstruction) insn, cls.getName());
                    }
                }
            }
        }
    }

    /** A JNI native: no TeaVM interop / generator / JSO annotation on it. */
    private static boolean isPlainJni(MethodHolder method) {
        for (org.teavm.model.AnnotationHolder a : method.getAnnotations().all()) {
            String name = a.getType();
            if (name.startsWith("org.teavm.")) {
                return false;
            }
        }
        return true;
    }

    /** Library JNI methods (Netty epoll/unix transport, JNA) cannot link in Wasm: make them throw. */
    private static void stubNative(MethodHolder method, ClassHolderTransformerContext context) {
        method.getModifiers().remove(org.teavm.model.ElementModifier.NATIVE);
        org.teavm.model.emit.ProgramEmitter pe = org.teavm.model.emit.ProgramEmitter.create(method,
                context.getHierarchy());
        pe.construct(UnsatisfiedLinkError.class, pe.constant(method.getReference() + " is a JNI method; "
                + "native libraries are not available in the browser")).raise();
    }

    private void rewrite(InvokeInstruction insn, String caller) {
        MethodReference ref = insn.getMethod();
        // A super call (super.m(...) in a subclass) is never redirected: the target would call m
        // virtually and land back in the overriding method that made the super call (infinite
        // recursion). Private calls (SPECIAL on the caller's own class) are still redirected.
        if (insn.getType() == InvocationType.SPECIAL && insn.getInstance() != null
                && !ref.getName().equals("<init>") && !ref.getClassName().equals(caller)) {
            return;
        }
        String target = rules.get(ref);
        if (target == null) {
            String[] any = insn.getInstance() != null ? anyOwnerRules.get(ref.getDescriptor()) : null;
            if (any == null) {
                return;
            }
            ref = new MethodReference(any[0], ref.getDescriptor());
            target = any[1];
        }
        if (ref.getName().equals("<init>")) {
            rewriteConstructor(insn, ref, target);
            return;
        }
        int hash = target.indexOf('#');
        if (hash >= 0) {
            List<? extends Variable> args = insn.getArguments();
            List<Variable> newArgs = new ArrayList<>();
            if (insn.getInstance() != null) {
                newArgs.add(insn.getInstance());
            }
            newArgs.addAll(args);
            insn.setInstance(null);
            insn.setType(InvocationType.SPECIAL);
            insn.setMethod(new MethodReference(target.substring(0, hash),
                    MethodDescriptor.parse(target.substring(hash + 1))));
            insn.setArguments(newArgs.toArray(new Variable[0]));
            return;
        }
        if (insn.getInstance() == null) {
            insn.setMethod(new MethodReference(target, ref.getDescriptor()));
            insn.setType(InvocationType.SPECIAL);
            return;
        }
        ValueType[] sig = ref.getSignature();
        ValueType[] newSig = new ValueType[sig.length + 1];
        newSig[0] = ValueType.object(ref.getClassName());
        System.arraycopy(sig, 0, newSig, 1, sig.length);
        List<? extends Variable> args = insn.getArguments();
        Variable[] newArgs = new Variable[args.size() + 1];
        newArgs[0] = insn.getInstance();
        for (int i = 0; i < args.size(); i++) {
            newArgs[i + 1] = args.get(i);
        }
        insn.setInstance(null);
        insn.setType(InvocationType.SPECIAL);
        insn.setMethod(new MethodReference(target, ref.getName(), newSig));
        insn.setArguments(newArgs);
    }

    /**
     * {@code v = new X; v.<init>(args)} becomes {@code v = target.constructX(args)}. Super-constructor
     * calls (no matching {@code new}) are left alone.
     */
    private void rewriteConstructor(InvokeInstruction insn, MethodReference ref, String target) {
        Variable instance = insn.getInstance();
        Program program = insn.getProgram();
        ConstructInstruction construct = null;
        for (int i = 0; i < program.basicBlockCount() && construct == null; i++) {
            for (Instruction other : program.basicBlockAt(i)) {
                if (other instanceof ConstructInstruction && ((ConstructInstruction) other).getReceiver() == instance) {
                    construct = (ConstructInstruction) other;
                    break;
                }
            }
        }
        if (construct == null) {
            return;
        }
        construct.delete();
        String owner = ref.getClassName();
        String simple = owner.substring(owner.lastIndexOf('.') + 1).replace('$', '_');
        ValueType[] params = ref.getParameterTypes();
        ValueType[] sig = new ValueType[params.length + 1];
        System.arraycopy(params, 0, sig, 0, params.length);
        sig[params.length] = ValueType.object(owner);
        insn.setInstance(null);
        insn.setType(InvocationType.SPECIAL);
        insn.setReceiver(instance);
        insn.setMethod(new MethodReference(target, "construct" + simple, sig));
    }
}
