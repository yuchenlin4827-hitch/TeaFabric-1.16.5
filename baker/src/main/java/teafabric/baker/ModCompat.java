package teafabric.baker;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.VarInsnNode;

/**
 * Build-time fixes for mods that rely on JVM features a browser build does not have (defining classes at
 * run time, method handles). Each fix applies only when the mod's classes are present, and only rewrites
 * the few methods involved, so the mod otherwise runs unchanged.
 */
final class ModCompat {
    private static final Pattern GOOGLE_ACCESS =
            Pattern.compile("^" + Pattern.quote(Baker.MOD_ROOT) + "[^/]+/googleaccess/(.+)\\.class_manual$");

    private ModCompat() {
    }

    static void apply(Map<String, byte[]> classes, Map<String, byte[]> resources) {
        ferriteCore(classes, resources);
    }

    /**
     * FerriteCore (memory savings) defines two classes inside Guava's package at run time, then creates its
     * compact property map through a method handle, and reads the block state cache through another. Here
     * the two classes become ordinary classes and both method handles become direct code.
     */
    private static void ferriteCore(Map<String, byte[]> classes, Map<String, byte[]> resources) {
        int defined = 0;
        for (Map.Entry<String, byte[]> e : resources.entrySet()) {
            Matcher m = GOOGLE_ACCESS.matcher(e.getKey());
            if (m.matches() && !classes.containsKey(m.group(1))) {
                classes.put(m.group(1), e.getValue());
                defined++;
            }
        }
        String definer = "malte0811/ferritecore/classloading/FastImmutableMapDefiner";
        String map = "malte0811/ferritecore/fastmap/immutable/FastMapEntryImmutableMap";
        String holder = "malte0811/ferritecore/ducks/FastMapStateHolder";
        if (classes.containsKey(definer) && classes.containsKey(map)) {
            patch(classes, definer, node -> {
                MethodNode makeMap = method(node, "makeMap", "(L" + holder + ";)Lcom/google/common/collect/ImmutableMap;");
                if (makeMap == null) {
                    return false;
                }
                InsnList code = new InsnList();
                code.add(new TypeInsnNode(Opcodes.NEW, map));
                code.add(new InsnNode(Opcodes.DUP));
                code.add(new VarInsnNode(Opcodes.ALOAD, 0));
                code.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, map, "<init>", "(L" + holder + ";)V", false));
                code.add(new InsnNode(Opcodes.ARETURN));
                replace(makeMap, code);
                return true;
            });
        }

        String cacheImpl = "malte0811/ferritecore/impl/BlockStateCacheImpl";
        String state = "net/minecraft/class_4970$class_4971";
        String shapeCache = "Lnet/minecraft/class_4970$class_4971$class_3752;";
        String access = "malte0811/ferritecore/ducks/BlockStateCacheAccess";
        if (classes.containsKey(cacheImpl) && hasField(classes.get(state), "field_23166", shapeCache)) {
            patch(classes, cacheImpl, node -> {
                // lambda$static$1 builds the getter (reflection + unreflectGetter); lambda$static$0 calls it.
                MethodNode build = method(node, "lambda$static$1", "()Ljava/util/function/Function;");
                MethodNode get = method(node, "lambda$static$0",
                        "(Ljava/lang/invoke/MethodHandle;L" + state + ";)L" + access + ";");
                InvokeDynamicInsnNode indy = build == null ? null : firstIndy(build);
                if (get == null || indy == null) {
                    return false;
                }
                InsnList make = new InsnList();
                make.add(new InsnNode(Opcodes.ACONST_NULL)); // the lambda's unused method handle
                make.add(indy.clone(null));
                make.add(new InsnNode(Opcodes.ARETURN));
                replace(build, make);
                InsnList read = new InsnList();
                read.add(new VarInsnNode(Opcodes.ALOAD, 1));
                read.add(new FieldInsnNode(Opcodes.GETFIELD, state, "field_23166", shapeCache));
                read.add(new TypeInsnNode(Opcodes.CHECKCAST, access));
                read.add(new InsnNode(Opcodes.ARETURN));
                replace(get, read);
                return true;
            });
        }
        if (defined > 0) {
            System.out.println("[baker] FerriteCore: " + defined + " Guava access classes defined at build time");
        }
    }

    private interface Patch {
        boolean apply(ClassNode node);
    }

    private static void patch(Map<String, byte[]> classes, String name, Patch patch) {
        ClassNode node = new ClassNode();
        new ClassReader(classes.get(name)).accept(node, 0);
        if (!patch.apply(node)) {
            System.out.println("[baker] WARNING: compat patch for " + name + " did not match; left unchanged");
            return;
        }
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        node.accept(writer);
        classes.put(name, writer.toByteArray());
        System.out.println("[baker] Compat patch: " + name);
    }

    private static MethodNode method(ClassNode node, String name, String desc) {
        for (MethodNode m : node.methods) {
            if (m.name.equals(name) && m.desc.equals(desc)) {
                return m;
            }
        }
        return null;
    }

    private static boolean hasField(byte[] bytes, String name, String desc) {
        if (bytes == null) {
            return false;
        }
        ClassNode node = new ClassNode();
        new ClassReader(bytes).accept(node, ClassReader.SKIP_CODE);
        for (FieldNode f : node.fields) {
            if (f.name.equals(name) && f.desc.equals(desc)) {
                return true;
            }
        }
        return false;
    }

    private static InvokeDynamicInsnNode firstIndy(MethodNode m) {
        for (AbstractInsnNode insn = m.instructions.getFirst(); insn != null; insn = insn.getNext()) {
            if (insn instanceof InvokeDynamicInsnNode) {
                return (InvokeDynamicInsnNode) insn;
            }
        }
        return null;
    }

    /** Straight-line replacement body: no branches, so no stack map frames are needed. */
    private static void replace(MethodNode m, InsnList code) {
        m.instructions = code;
        m.tryCatchBlocks.clear();
        if (m.localVariables != null) {
            m.localVariables.clear();
        }
        m.visibleLocalVariableAnnotations = null;
        m.invisibleLocalVariableAnnotations = null;
    }
}
