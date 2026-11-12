package teafabric.tools;

import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.IincInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.LineNumberNode;
import org.objectweb.asm.tree.LookupSwitchInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TableSwitchInsnNode;
import org.objectweb.asm.tree.TryCatchBlockNode;
import org.objectweb.asm.tree.VarInsnNode;
import org.objectweb.asm.tree.analysis.Analyzer;
import org.objectweb.asm.tree.analysis.BasicInterpreter;
import org.objectweb.asm.tree.analysis.BasicValue;
import org.objectweb.asm.tree.analysis.Frame;

/**
 * Splits huge static initialisers ({@code Blocks.<clinit>}, {@code Items.<clinit>}, …) into a chain of
 * private static methods before TeaVM sees them.
 *
 * <p>Compiled to Wasm (strict mode, coroutine-transformed), {@code Blocks.<clinit>} is a 230 KB function.
 * When V8 tiers it up, Turboshaft's Wasm GC type analysis runs out of zone memory and kills the renderer
 * ("V8 process OOM (Zone)"). Splitting has to happen on bytecode: after TeaVM's coroutine transformation
 * the Wasm function is a state machine without safe cut points.
 *
 * <p>A cut is placed between two instructions only if the operand stack is empty there, the previous
 * instruction falls through, no jump, switch or try/catch range crosses it, and no local variable is
 * live across it (backward liveness over the control-flow graph). Methods containing early returns or
 * monitors are left alone. Output: a jar with the rewritten classes, put ahead of the baked jar on the
 * TeaVM class path. Frames are not kept (TeaVM does not need them).
 *
 * <p>It also restores the JVM's class initialisation order. TeaVM 0.16 (Wasm GC) does not initialise a
 * superclass before its subclass: in Minecraft {@code LivingEntity.<clinit>} ran before
 * {@code Entity.<clinit>}, so {@code DataTracker.registerData} handed out ids that collide with
 * Entity's ("Duplicate id value for 0!" when joining a server). Every class whose ancestors have a
 * static initialiser gets a trigger method {@code teafabric$init()} and a {@code <clinit>} that starts
 * by calling its superclass's trigger, which makes TeaVM initialise the superclass first.
 */
public final class SplitClinits {
    private static final int MIN_INSNS = 2500;
    private static final int CHUNK_INSNS = 800;

    public static void main(String[] args) throws Exception {
        Map<String, String> o = Setup.parseArgs(args);
        Map<String, byte[]> classes = new LinkedHashMap<>();
        for (String entry : o.get("classpath").split(File.pathSeparator)) {
            File f = new File(entry);
            if (f.isDirectory()) {
                try (Stream<Path> files = Files.walk(f.toPath())) {
                    for (Path p : (Iterable<Path>) files::iterator) {
                        String name = f.toPath().relativize(p).toString().replace(File.separatorChar, '/');
                        if (name.endsWith(".class") && !classes.containsKey(name)) {
                            classes.put(name, Files.readAllBytes(p));
                        }
                    }
                }
            } else if (f.isFile() && f.getName().endsWith(".jar")) {
                try (JarFile jar = new JarFile(f)) {
                    for (JarEntry e : Collections.list(jar.entries())) {
                        String name = e.getName();
                        if (name.endsWith(".class") && !name.startsWith("META-INF/") && !classes.containsKey(name)) {
                            try (InputStream in = jar.getInputStream(e)) {
                                classes.put(name, in.readAllBytes());
                            }
                        }
                    }
                }
            }
        }
        // Class initialisation order: which classes (or their ancestors) have a static initialiser.
        Map<String, String> superOf = new HashMap<>();
        Set<String> hasClinit = new HashSet<>();
        Set<String> interfaces = new HashSet<>();
        for (Map.Entry<String, byte[]> e : classes.entrySet()) {
            String name = e.getKey().substring(0, e.getKey().length() - 6);
            if (excluded(name)) {
                continue;
            }
            ClassReader reader = new ClassReader(e.getValue());
            superOf.put(name, reader.getSuperName());
            if ((reader.getAccess() & Opcodes.ACC_INTERFACE) != 0) {
                interfaces.add(name);
            }
            reader.accept(new org.objectweb.asm.ClassVisitor(Opcodes.ASM9) {
                @Override
                public org.objectweb.asm.MethodVisitor visitMethod(int access, String mname, String desc,
                        String signature, String[] exceptions) {
                    if (mname.equals("<clinit>")) {
                        hasClinit.add(name);
                    }
                    return null;
                }
            }, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        }
        Map<String, Boolean> needsTrigger = new HashMap<>();
        for (String name : superOf.keySet()) {
            needsTrigger(name, superOf, hasClinit, interfaces, needsTrigger);
        }

        Path out = Path.of(o.get("out"));
        Files.createDirectories(out.toAbsolutePath().getParent());
        int count = 0;
        int methods = 0;
        int ordered = 0;
        try (OutputStream fos = Files.newOutputStream(out); ZipOutputStream zip = new ZipOutputStream(fos)) {
            for (Map.Entry<String, byte[]> e : classes.entrySet()) {
                byte[] bytes = e.getValue();
                String name = e.getKey().substring(0, e.getKey().length() - 6);
                // TeaVM's own class library is renamed (T-prefixed) while loading; leave it alone.
                if (excluded(name)) {
                    continue;
                }
                boolean trigger = Boolean.TRUE.equals(needsTrigger.get(name));
                if (!trigger && !mayHaveLargeClinit(bytes)) {
                    continue;
                }
                ClassNode cn = new ClassNode();
                new ClassReader(bytes).accept(cn, ClassReader.SKIP_FRAMES);
                if (trigger) {
                    String superName = cn.superName;
                    addInitOrder(cn, superName != null && Boolean.TRUE.equals(needsTrigger.get(superName))
                            ? superName : null);
                    ordered++;
                }
                int parts = split(cn);
                if (parts == 0 && !trigger) {
                    continue;
                }
                ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
                cn.accept(cw);
                ZipEntry ze = new ZipEntry(e.getKey());
                ze.setTime(0);
                zip.putNextEntry(ze);
                zip.write(cw.toByteArray());
                zip.closeEntry();
                if (parts > 0) {
                    count++;
                    methods += parts;
                    System.out.println("[split] " + cn.name + ".<clinit> -> " + parts + " methods");
                }
            }
        }
        System.out.println("[split] " + count + " class initialisers split into " + methods + " methods");
        System.out.println("[init-order] " + ordered + " classes initialise their superclass first");
    }

    private static boolean excluded(String name) {
        return name.startsWith("org/teavm/") || name.startsWith("java/") || name.startsWith("javax/")
                || name.startsWith("jdk/") || name.startsWith("sun/");
    }

    /** A class needs a trigger if it, or a (program) ancestor class, has a static initialiser. */
    private static boolean needsTrigger(String name, Map<String, String> superOf, Set<String> hasClinit,
            Set<String> interfaces, Map<String, Boolean> memo) {
        if (name == null || !superOf.containsKey(name) || interfaces.contains(name)) {
            return false;
        }
        Boolean known = memo.get(name);
        if (known != null) {
            return known;
        }
        memo.put(name, false); // cycles cannot happen in valid class files; be safe anyway
        boolean result = hasClinit.contains(name)
                || needsTrigger(superOf.get(name), superOf, hasClinit, interfaces, memo);
        memo.put(name, result);
        return result;
    }

    static final String TRIGGER = "teafabric$init";

    /**
     * Adds the trigger method and, when the superclass needs initialising, makes {@code <clinit>} start with a
     * call to the superclass's trigger (TeaVM initialises a class before running one of its static methods).
     */
    private static void addInitOrder(ClassNode cn, String superToInit) {
        boolean present = false;
        MethodNode clinit = null;
        for (MethodNode m : cn.methods) {
            if (m.name.equals(TRIGGER) && m.desc.equals("()V")) {
                present = true;
            }
            if (m.name.equals("<clinit>")) {
                clinit = m;
            }
        }
        if (!present) {
            MethodNode trigger = new MethodNode(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC | Opcodes.ACC_SYNTHETIC,
                    TRIGGER, "()V", null, null);
            trigger.instructions.add(new InsnNode(Opcodes.RETURN));
            trigger.maxStack = 0;
            trigger.maxLocals = 0;
            cn.methods.add(trigger);
        }
        if (superToInit == null) {
            return;
        }
        if (clinit == null) {
            clinit = new MethodNode(Opcodes.ACC_STATIC, "<clinit>", "()V", null, null);
            clinit.instructions.add(new InsnNode(Opcodes.RETURN));
            clinit.maxStack = 0;
            clinit.maxLocals = 0;
            cn.methods.add(clinit);
        }
        clinit.instructions.insert(new MethodInsnNode(Opcodes.INVOKESTATIC, superToInit, TRIGGER, "()V", false));
    }

    /** Cheap pre-filter: class files with a big code attribute somewhere. */
    private static boolean mayHaveLargeClinit(byte[] bytes) {
        return bytes.length > MIN_INSNS * 2;
    }

    private static int split(ClassNode cn) {
        if ((cn.access & Opcodes.ACC_INTERFACE) != 0) {
            return 0;
        }
        MethodNode clinit = null;
        for (MethodNode m : cn.methods) {
            if (m.name.equals("<clinit>")) {
                clinit = m;
            }
        }
        if (clinit == null || realSize(clinit.instructions) < MIN_INSNS) {
            return 0;
        }
        AbstractInsnNode[] insns = clinit.instructions.toArray();
        int n = insns.length;
        Map<LabelNode, Integer> labelIndex = new HashMap<>();
        for (int i = 0; i < n; i++) {
            if (insns[i] instanceof LabelNode) {
                labelIndex.put((LabelNode) insns[i], i);
            }
        }
        // Early returns / monitors make splitting unsafe.
        int lastReal = n - 1;
        while (lastReal >= 0 && insns[lastReal].getOpcode() < 0) {
            lastReal--;
        }
        for (int i = 0; i < n; i++) {
            int op = insns[i].getOpcode();
            if (op == Opcodes.RETURN && i != lastReal || op == Opcodes.MONITORENTER || op == Opcodes.MONITOREXIT
                    || op == Opcodes.JSR || op == Opcodes.RET) {
                return 0;
            }
        }
        if (lastReal < 0 || insns[lastReal].getOpcode() != Opcodes.RETURN) {
            return 0;
        }

        Frame<BasicValue>[] frames;
        try {
            frames = new Analyzer<>(new BasicInterpreter()).analyze(cn.name, clinit);
        } catch (Exception ex) {
            System.out.println("[split] cannot analyse " + cn.name + ".<clinit>: " + ex.getMessage());
            return 0;
        }

        // Positions p (cut before insns[p]) crossed by a jump, switch or try/catch range.
        int[] crossing = new int[n + 2];
        List<int[]> edges = new ArrayList<>();
        List<List<Integer>> succ = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            succ.add(new ArrayList<>());
        }
        for (int i = 0; i < n; i++) {
            AbstractInsnNode insn = insns[i];
            List<LabelNode> targets = new ArrayList<>();
            if (insn instanceof JumpInsnNode) {
                targets.add(((JumpInsnNode) insn).label);
            } else if (insn instanceof TableSwitchInsnNode) {
                targets.add(((TableSwitchInsnNode) insn).dflt);
                targets.addAll(((TableSwitchInsnNode) insn).labels);
            } else if (insn instanceof LookupSwitchInsnNode) {
                targets.add(((LookupSwitchInsnNode) insn).dflt);
                targets.addAll(((LookupSwitchInsnNode) insn).labels);
            }
            for (LabelNode t : targets) {
                int ti = labelIndex.get(t);
                edges.add(new int[] {i, ti});
                succ.get(i).add(ti);
            }
            if (fallsThrough(insn) && i + 1 < n) {
                succ.get(i).add(i + 1);
            }
        }
        for (TryCatchBlockNode tc : clinit.tryCatchBlocks) {
            int s = labelIndex.get(tc.start);
            int e = labelIndex.get(tc.end);
            int h = labelIndex.get(tc.handler);
            edges.add(new int[] {Math.min(s, h), Math.max(e, h)});
            edges.add(new int[] {s, e});
            for (int i = s; i < e; i++) {
                succ.get(i).add(h);
            }
        }
        for (int[] edge : edges) {
            int lo = Math.min(edge[0], edge[1]);
            int hi = Math.max(edge[0], edge[1]);
            crossing[lo + 1]++;
            crossing[hi + 1]--;
        }

        // Backward liveness of local variable slots.
        BitSet[] liveIn = new BitSet[n];
        for (int i = 0; i < n; i++) {
            liveIn[i] = new BitSet();
        }
        boolean changed = true;
        while (changed) {
            changed = false;
            for (int i = n - 1; i >= 0; i--) {
                BitSet live = new BitSet();
                for (int s : succ.get(i)) {
                    live.or(liveIn[s]);
                }
                AbstractInsnNode insn = insns[i];
                if (insn instanceof VarInsnNode) {
                    int var = ((VarInsnNode) insn).var;
                    int op = insn.getOpcode();
                    if (op >= Opcodes.ISTORE && op <= Opcodes.ASTORE) {
                        live.clear(var);
                        if (op == Opcodes.LSTORE || op == Opcodes.DSTORE) {
                            live.clear(var + 1);
                        }
                    } else {
                        live.set(var);
                    }
                } else if (insn instanceof IincInsnNode) {
                    live.set(((IincInsnNode) insn).var);
                }
                if (!live.equals(liveIn[i])) {
                    liveIn[i] = live;
                    changed = true;
                }
            }
        }

        // Choose cut points greedily.
        List<Integer> cuts = new ArrayList<>();
        int chunk = 0;
        int cross = 0;
        for (int p = 1; p <= lastReal; p++) {
            cross += crossing[p];
            if (insns[p - 1].getOpcode() >= 0) {
                chunk++;
            }
            if (chunk < CHUNK_INSNS || cross != 0) {
                continue;
            }
            Frame<BasicValue> frame = frames[p];
            if (frame == null || frame.getStackSize() != 0 || !liveIn[p].isEmpty()) {
                continue;
            }
            AbstractInsnNode prev = previousReal(insns, p);
            if (prev == null || !fallsThrough(prev)) {
                continue;
            }
            cuts.add(p);
            chunk = 0;
        }
        if (cuts.isEmpty()) {
            return 0;
        }

        // Move [start, cut) ranges into new methods; the last range keeps the final RETURN.
        Set<String> names = new HashSet<>();
        for (MethodNode m : cn.methods) {
            names.add(m.name);
        }
        Set<String> written = new HashSet<>();
        InsnList newBody = new InsnList();
        List<MethodNode> parts = new ArrayList<>();
        int start = 0;
        cuts.add(n);
        for (int cut : cuts) {
            String name;
            int k = parts.size();
            do {
                name = "teafabric$clinit" + k++;
            } while (names.contains(name));
            names.add(name);
            MethodNode part = new MethodNode(Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC | Opcodes.ACC_SYNTHETIC,
                    name, "()V", null, null);
            Map<LabelNode, LabelNode> map = new HashMap<>();
            for (int i = start; i < cut; i++) {
                if (insns[i] instanceof LabelNode) {
                    map.put((LabelNode) insns[i], new LabelNode());
                }
            }
            for (int i = start; i < cut; i++) {
                AbstractInsnNode insn = insns[i];
                if (insn instanceof LineNumberNode && !map.containsKey(((LineNumberNode) insn).start)) {
                    continue;
                }
                if (insn instanceof FieldInsnNode && insn.getOpcode() == Opcodes.PUTSTATIC
                        && ((FieldInsnNode) insn).owner.equals(cn.name)) {
                    written.add(((FieldInsnNode) insn).name);
                }
                part.instructions.add(insn.clone(map));
            }
            if (cut != n) {
                part.instructions.add(new InsnNode(Opcodes.RETURN));
            }
            for (TryCatchBlockNode tc : clinit.tryCatchBlocks) {
                int s = labelIndex.get(tc.start);
                if (s >= start && s < cut) {
                    part.tryCatchBlocks.add(new TryCatchBlockNode(map.get(tc.start), map.get(tc.end),
                            map.get(tc.handler), tc.type));
                }
            }
            part.maxLocals = clinit.maxLocals;
            part.maxStack = clinit.maxStack;
            parts.add(part);
            newBody.add(new MethodInsnNode(Opcodes.INVOKESTATIC, cn.name, name, "()V", false));
            start = cut;
        }
        newBody.add(new InsnNode(Opcodes.RETURN));
        clinit.instructions = newBody;
        clinit.tryCatchBlocks = new ArrayList<>();
        clinit.localVariables = null;
        clinit.visibleLocalVariableAnnotations = null;
        clinit.invisibleLocalVariableAnnotations = null;
        cn.methods.addAll(parts);
        // Static finals are now assigned outside <clinit>; TeaVM does not verify, but keep the class honest.
        for (FieldNode f : cn.fields) {
            if ((f.access & Opcodes.ACC_STATIC) != 0 && written.contains(f.name)) {
                f.access &= ~Opcodes.ACC_FINAL;
            }
        }
        return parts.size();
    }

    private static AbstractInsnNode previousReal(AbstractInsnNode[] insns, int p) {
        for (int i = p - 1; i >= 0; i--) {
            if (insns[i].getOpcode() >= 0) {
                return insns[i];
            }
        }
        return null;
    }

    private static boolean fallsThrough(AbstractInsnNode insn) {
        int op = insn.getOpcode();
        return !(op == Opcodes.GOTO || op == Opcodes.ATHROW || op == Opcodes.RETURN || op == Opcodes.ARETURN
                || op == Opcodes.IRETURN || op == Opcodes.LRETURN || op == Opcodes.FRETURN || op == Opcodes.DRETURN
                || op == Opcodes.TABLESWITCH || op == Opcodes.LOOKUPSWITCH);
    }

    private static int realSize(InsnList list) {
        int n = 0;
        for (AbstractInsnNode insn = list.getFirst(); insn != null; insn = insn.getNext()) {
            if (insn.getOpcode() >= 0) {
                n++;
            }
        }
        return n;
    }
}
