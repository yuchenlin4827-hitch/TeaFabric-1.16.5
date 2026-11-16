package teafabric.baker;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;

/**
 * Architectury's EventFactory builds an event's invoker with java.lang.reflect.Proxy and calls each listener
 * through MethodHandles; neither exists in a browser build. For every event interface the mods create an
 * event for (a class literal or an empty array of the interface at the call site), this generates the
 * invoker the proxy would have been: a class implementing the interface whose methods call every listener
 * in order, with the same rules for results (PASS continues, any other result is returned; EventResult and
 * CompoundEventResult stop when they interrupt further evaluation). The call sites are redirected to it.
 */
final class ArchitecturyEvents {
    private static final String FACTORY = "me/shedaniel/architectury/event/EventFactory";
    private static final String EVENT = "Lme/shedaniel/architectury/event/Event;";
    private static final String OUT = "teafabric/generated/arch/";
    private static final String RESULT = "net/minecraft/class_1269"; // ActionResult (InteractionResult)
    private static final String HOLDER = "net/minecraft/class_1271"; // TypedActionResult (InteractionResultHolder)
    private static final String EVENT_RESULT = "me/shedaniel/architectury/event/EventResult";
    private static final String COMPOUND = "me/shedaniel/architectury/event/CompoundEventResult";

    private enum Kind { LOOP, RESULT, EVENT_RESULT, HOLDER, COMPOUND }

    private static final Map<String, Kind> PER_INTERFACE = new HashMap<>();

    static {
        PER_INTERFACE.put("createLoop", Kind.LOOP);
        PER_INTERFACE.put("createInteractionResult", Kind.RESULT);
        PER_INTERFACE.put("createEventResult", Kind.EVENT_RESULT);
        PER_INTERFACE.put("createInteractionResultHolder", Kind.HOLDER);
        PER_INTERFACE.put("createCompoundEventResult", Kind.COMPOUND);
    }

    private final Map<String, byte[]> classes;
    private final Map<String, String> generated = new LinkedHashMap<>(); // kind + interface -> invoker class
    private int counter;

    private ArchitecturyEvents(Map<String, byte[]> classes) {
        this.classes = classes;
    }

    static void apply(Map<String, byte[]> classes) {
        if (!classes.containsKey(FACTORY)) {
            return;
        }
        ArchitecturyEvents a = new ArchitecturyEvents(classes);
        int sites = a.rewriteCallSites();
        a.patchFixedLoops();
        System.out.println("[baker] Compat patch: Architectury events: " + sites + " call site(s), "
                + a.generated.size() + " generated invoker(s) instead of java.lang.reflect.Proxy");
    }

    // ------------------------------------------------------------------ call sites

    private int rewriteCallSites() {
        int sites = 0;
        byte[] marker = FACTORY.getBytes(java.nio.charset.StandardCharsets.ISO_8859_1);
        for (String name : new ArrayList<>(classes.keySet())) {
            if (name.startsWith(OUT) || !contains(classes.get(name), marker)) {
                continue;
            }
            ClassNode node = new ClassNode();
            new ClassReader(classes.get(name)).accept(node, 0);
            int here = 0;
            for (MethodNode m : node.methods) {
                for (AbstractInsnNode insn = m.instructions.getFirst(); insn != null; insn = insn.getNext()) {
                    if (insn.getOpcode() != Opcodes.INVOKESTATIC) {
                        continue;
                    }
                    MethodInsnNode call = (MethodInsnNode) insn;
                    Kind kind = PER_INTERFACE.get(call.name);
                    if (!call.owner.equals(FACTORY) || kind == null) {
                        continue;
                    }
                    String iface = null;
                    AbstractInsnNode prev = previous(insn);
                    if (call.desc.equals("(Ljava/lang/Class;)" + EVENT) && prev instanceof LdcInsnNode
                            && ((LdcInsnNode) prev).cst instanceof Type) {
                        iface = ((Type) ((LdcInsnNode) prev).cst).getInternalName();
                    } else if (call.desc.equals("([Ljava/lang/Object;)" + EVENT) && prev instanceof TypeInsnNode
                            && prev.getOpcode() == Opcodes.ANEWARRAY) {
                        iface = ((TypeInsnNode) prev).desc;
                    }
                    if (iface == null) {
                        System.out.println("[baker] WARNING: Architectury event with no static interface in " + name
                                + "." + m.name + " (still uses Proxy)");
                        continue;
                    }
                    String invoker = invoker(kind, iface);
                    if (invoker == null) {
                        continue;
                    }
                    // the Class / array argument stays on the stack; the factory ignores it
                    MethodInsnNode direct = new MethodInsnNode(Opcodes.INVOKESTATIC, invoker, "event", call.desc,
                            false);
                    m.instructions.set(call, direct);
                    insn = direct; // continue after the replacement (the removed node has no successor)
                    here++;
                }
            }
            if (here > 0) {
                ClassWriter w = new ClassWriter(ClassWriter.COMPUTE_MAXS);
                node.accept(w);
                classes.put(name, w.toByteArray());
                sites += here;
            }
        }
        return sites;
    }

    /** createConsumerLoop / createActorLoop / createEventActorLoop: the interface is fixed, so the factory itself is patched. */
    private void patchFixedLoops() {
        ClassNode node = new ClassNode();
        new ClassReader(classes.get(FACTORY)).accept(node, 0);
        int done = 0;
        for (MethodNode m : node.methods) {
            String iface;
            Kind kind;
            switch (m.name) {
                case "createConsumerLoop": iface = "java/util/function/Consumer"; kind = Kind.LOOP; break;
                case "createActorLoop": iface = "me/shedaniel/architectury/event/Actor"; kind = Kind.RESULT; break;
                case "createEventActorLoop":
                    iface = "me/shedaniel/architectury/event/EventActor";
                    kind = Kind.EVENT_RESULT;
                    break;
                default: continue;
            }
            if (!m.desc.equals("(Ljava/lang/Class;)" + EVENT)) {
                continue; // the varargs variants call this one
            }
            String invoker = invoker(kind, iface);
            if (invoker == null) {
                continue;
            }
            InsnList code = new InsnList();
            code.add(new InsnNode(Opcodes.ACONST_NULL));
            code.add(new MethodInsnNode(Opcodes.INVOKESTATIC, invoker, "event", "(Ljava/lang/Class;)" + EVENT, false));
            code.add(new InsnNode(Opcodes.ARETURN));
            m.instructions = code;
            m.tryCatchBlocks.clear();
            if (m.localVariables != null) {
                m.localVariables.clear();
            }
            done++;
        }
        if (done > 0) {
            ClassWriter w = new ClassWriter(ClassWriter.COMPUTE_MAXS);
            node.accept(w);
            classes.put(FACTORY, w.toByteArray());
        }
    }

    private static AbstractInsnNode previous(AbstractInsnNode insn) {
        AbstractInsnNode p = insn.getPrevious();
        while (p != null && p.getOpcode() < 0) {
            p = p.getPrevious();
        }
        return p;
    }

    private static boolean contains(byte[] bytes, byte[] part) {
        outer:
        for (int i = 0, n = bytes.length - part.length; i <= n; i++) {
            for (int j = 0; j < part.length; j++) {
                if (bytes[i + j] != part[j]) {
                    continue outer;
                }
            }
            return true;
        }
        return false;
    }

    // ------------------------------------------------------------------ generation

    /** The generated class for (kind, interface); its static event(...) methods return the Event. */
    private String invoker(Kind kind, String iface) {
        String key = kind + " " + iface;
        String existing = generated.get(key);
        if (existing != null) {
            return existing;
        }
        List<MethodNode> methods = new ArrayList<>();
        if (!abstractMethods(iface, methods, new java.util.HashSet<>())) {
            System.out.println("[baker] WARNING: cannot read event interface " + iface);
            return null;
        }
        int n = ++counter;
        String impl = OUT + "Listeners" + n;
        String invoker = OUT + "Invoker" + n;
        classes.put(impl, implementation(impl, iface, kind, methods));
        classes.put(invoker, factory(invoker, impl));
        generated.put(key, invoker);
        return invoker;
    }

    private boolean abstractMethods(String iface, List<MethodNode> out, java.util.Set<String> seen) {
        byte[] bytes = classes.get(iface);
        if (bytes == null) {
            bytes = jdkClass(iface);
        }
        if (bytes == null) {
            return false;
        }
        ClassNode node = new ClassNode();
        new ClassReader(bytes).accept(node, ClassReader.SKIP_CODE);
        for (MethodNode m : node.methods) {
            if ((m.access & Opcodes.ACC_ABSTRACT) != 0 && (m.access & Opcodes.ACC_STATIC) == 0
                    && seen.add(m.name + m.desc)) {
                out.add(m);
            }
        }
        for (String sup : node.interfaces) {
            if (!abstractMethods(sup, out, seen)) {
                return false;
            }
        }
        return true;
    }

    private static byte[] jdkClass(String name) {
        try (InputStream in = Object.class.getResourceAsStream("/" + name + ".class")) {
            return in == null ? null : in.readAllBytes();
        } catch (IOException e) {
            return null;
        }
    }

    private static byte[] factory(String invoker, String impl) {
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        cw.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL | Opcodes.ACC_SUPER, invoker, null,
                "java/lang/Object", new String[] {"java/util/function/Function"});
        MethodVisitor ctor = cw.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
        ctor.visitCode();
        ctor.visitVarInsn(Opcodes.ALOAD, 0);
        ctor.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
        ctor.visitInsn(Opcodes.RETURN);
        ctor.visitMaxs(0, 0);
        ctor.visitEnd();
        MethodVisitor apply = cw.visitMethod(Opcodes.ACC_PUBLIC, "apply", "(Ljava/lang/Object;)Ljava/lang/Object;",
                null, null);
        apply.visitCode();
        apply.visitTypeInsn(Opcodes.NEW, impl);
        apply.visitInsn(Opcodes.DUP);
        apply.visitVarInsn(Opcodes.ALOAD, 1);
        apply.visitTypeInsn(Opcodes.CHECKCAST, "java/util/List");
        apply.visitMethodInsn(Opcodes.INVOKESPECIAL, impl, "<init>", "(Ljava/util/List;)V", false);
        apply.visitInsn(Opcodes.ARETURN);
        apply.visitMaxs(0, 0);
        apply.visitEnd();
        for (String desc : new String[] {"(Ljava/lang/Class;)" + EVENT, "([Ljava/lang/Object;)" + EVENT}) {
            MethodVisitor ev = cw.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "event", desc, null, null);
            ev.visitCode();
            ev.visitTypeInsn(Opcodes.NEW, invoker);
            ev.visitInsn(Opcodes.DUP);
            ev.visitMethodInsn(Opcodes.INVOKESPECIAL, invoker, "<init>", "()V", false);
            ev.visitMethodInsn(Opcodes.INVOKESTATIC, FACTORY, "of", "(Ljava/util/function/Function;)" + EVENT, false);
            ev.visitInsn(Opcodes.ARETURN);
            ev.visitMaxs(0, 0);
            ev.visitEnd();
        }
        cw.visitEnd();
        return cw.toByteArray();
    }

    private static byte[] implementation(String impl, String iface, Kind kind, List<MethodNode> methods) {
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES) {
            @Override
            protected String getCommonSuperClass(String a, String b) {
                return "java/lang/Object";
            }
        };
        cw.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL | Opcodes.ACC_SUPER, impl, null,
                "java/lang/Object", new String[] {iface});
        cw.visitField(Opcodes.ACC_PRIVATE | Opcodes.ACC_FINAL, "listeners", "Ljava/util/List;", null, null).visitEnd();
        MethodVisitor ctor = cw.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "(Ljava/util/List;)V", null, null);
        ctor.visitCode();
        ctor.visitVarInsn(Opcodes.ALOAD, 0);
        ctor.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
        ctor.visitVarInsn(Opcodes.ALOAD, 0);
        ctor.visitVarInsn(Opcodes.ALOAD, 1);
        ctor.visitFieldInsn(Opcodes.PUTFIELD, impl, "listeners", "Ljava/util/List;");
        ctor.visitInsn(Opcodes.RETURN);
        ctor.visitMaxs(0, 0);
        ctor.visitEnd();
        for (MethodNode m : methods) {
            Type[] args = Type.getArgumentTypes(m.desc);
            Type ret = Type.getReturnType(m.desc);
            MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PUBLIC, m.name, m.desc, null, null);
            mv.visitCode();
            int slot = 1;
            for (Type a : args) {
                slot += a.getSize();
            }
            int it = slot;
            int result = slot + 1;
            mv.visitVarInsn(Opcodes.ALOAD, 0);
            mv.visitFieldInsn(Opcodes.GETFIELD, impl, "listeners", "Ljava/util/List;");
            mv.visitMethodInsn(Opcodes.INVOKEINTERFACE, "java/util/List", "iterator", "()Ljava/util/Iterator;", true);
            mv.visitVarInsn(Opcodes.ASTORE, it);
            Label loop = new Label();
            Label end = new Label();
            mv.visitLabel(loop);
            mv.visitVarInsn(Opcodes.ALOAD, it);
            mv.visitMethodInsn(Opcodes.INVOKEINTERFACE, "java/util/Iterator", "hasNext", "()Z", true);
            mv.visitJumpInsn(Opcodes.IFEQ, end);
            mv.visitVarInsn(Opcodes.ALOAD, it);
            mv.visitMethodInsn(Opcodes.INVOKEINTERFACE, "java/util/Iterator", "next", "()Ljava/lang/Object;", true);
            mv.visitTypeInsn(Opcodes.CHECKCAST, iface);
            int s = 1;
            for (Type a : args) {
                mv.visitVarInsn(a.getOpcode(Opcodes.ILOAD), s);
                s += a.getSize();
            }
            mv.visitMethodInsn(Opcodes.INVOKEINTERFACE, iface, m.name, m.desc, true);
            if (kind == Kind.LOOP || ret.getSort() != Type.OBJECT) {
                if (ret.getSize() == 2) {
                    mv.visitInsn(Opcodes.POP2);
                } else if (ret.getSize() == 1) {
                    mv.visitInsn(Opcodes.POP);
                }
            } else {
                String expected = kind == Kind.RESULT ? RESULT : kind == Kind.HOLDER ? HOLDER
                        : kind == Kind.EVENT_RESULT ? EVENT_RESULT : COMPOUND;
                mv.visitTypeInsn(Opcodes.CHECKCAST, expected);
                mv.visitVarInsn(Opcodes.ASTORE, result);
                mv.visitVarInsn(Opcodes.ALOAD, result);
                switch (kind) {
                    case RESULT:
                        mv.visitFieldInsn(Opcodes.GETSTATIC, RESULT, "field_5811", "L" + RESULT + ";"); // PASS
                        mv.visitJumpInsn(Opcodes.IF_ACMPEQ, loop);
                        break;
                    case HOLDER:
                        mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, HOLDER, "method_5467", "()L" + RESULT + ";", false);
                        mv.visitFieldInsn(Opcodes.GETSTATIC, RESULT, "field_5811", "L" + RESULT + ";");
                        mv.visitJumpInsn(Opcodes.IF_ACMPEQ, loop);
                        break;
                    default:
                        mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, expected, "interruptsFurtherEvaluation", "()Z", false);
                        mv.visitJumpInsn(Opcodes.IFEQ, loop);
                        break;
                }
                mv.visitVarInsn(Opcodes.ALOAD, result);
                mv.visitInsn(Opcodes.ARETURN);
            }
            mv.visitJumpInsn(Opcodes.GOTO, loop);
            mv.visitLabel(end);
            if (kind != Kind.LOOP && ret.getSort() == Type.OBJECT) {
                switch (kind) {
                    case RESULT:
                        mv.visitFieldInsn(Opcodes.GETSTATIC, RESULT, "field_5811", "L" + RESULT + ";");
                        break;
                    case HOLDER:
                        mv.visitInsn(Opcodes.ACONST_NULL);
                        mv.visitMethodInsn(Opcodes.INVOKESTATIC, HOLDER, "method_22430",
                                "(Ljava/lang/Object;)L" + HOLDER + ";", false);
                        break;
                    case EVENT_RESULT:
                        mv.visitMethodInsn(Opcodes.INVOKESTATIC, EVENT_RESULT, "pass", "()L" + EVENT_RESULT + ";", false);
                        break;
                    default:
                        mv.visitMethodInsn(Opcodes.INVOKESTATIC, COMPOUND, "pass", "()L" + COMPOUND + ";", false);
                        break;
                }
                mv.visitInsn(Opcodes.ARETURN);
            } else {
                defaultReturn(mv, ret);
            }
            mv.visitMaxs(0, 0);
            mv.visitEnd();
        }
        cw.visitEnd();
        return cw.toByteArray();
    }

    private static void defaultReturn(MethodVisitor mv, Type ret) {
        switch (ret.getSort()) {
            case Type.VOID: mv.visitInsn(Opcodes.RETURN); return;
            case Type.LONG: mv.visitInsn(Opcodes.LCONST_0); mv.visitInsn(Opcodes.LRETURN); return;
            case Type.FLOAT: mv.visitInsn(Opcodes.FCONST_0); mv.visitInsn(Opcodes.FRETURN); return;
            case Type.DOUBLE: mv.visitInsn(Opcodes.DCONST_0); mv.visitInsn(Opcodes.DRETURN); return;
            case Type.OBJECT: case Type.ARRAY: mv.visitInsn(Opcodes.ACONST_NULL); mv.visitInsn(Opcodes.ARETURN); return;
            default: mv.visitInsn(Opcodes.ICONST_0); mv.visitInsn(Opcodes.IRETURN);
        }
    }
}
