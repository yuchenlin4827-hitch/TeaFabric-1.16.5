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
 * its parameter list. Constructors ({@code <init>}) are not supported. Classes whose name starts
 * with an excluded prefix (the redirect targets themselves, TeaVM's class library) are untouched.
 */
public class RedirectTransformer implements ClassHolderTransformer {
    private final Map<MethodReference, String> rules = new HashMap<>();
    private final List<String> excluded = new ArrayList<>();

    public RedirectTransformer(ClassLoader classLoader) {
        excluded.add("org.teavm.");
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
        rules.put(new MethodReference(parts[0], MethodDescriptor.parse(parts[1] + parts[2])), parts[4]);
    }

    @Override
    public void transformClass(ClassHolder cls, ClassHolderTransformerContext context) {
        for (String prefix : excluded) {
            if (cls.getName().startsWith(prefix)) {
                return;
            }
        }
        for (MethodHolder method : cls.getMethods()) {
            Program program = method.getProgram();
            if (program == null) {
                continue;
            }
            for (int i = 0; i < program.basicBlockCount(); i++) {
                BasicBlock block = program.basicBlockAt(i);
                for (Instruction insn : block) {
                    if (insn instanceof InvokeInstruction) {
                        rewrite((InvokeInstruction) insn);
                    }
                }
            }
        }
    }

    private void rewrite(InvokeInstruction insn) {
        MethodReference ref = insn.getMethod();
        String target = rules.get(ref);
        if (target == null) {
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
}
