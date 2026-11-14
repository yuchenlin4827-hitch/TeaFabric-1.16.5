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
        lithium(classes);
        sodium(classes);
    }

    /**
     * Sodium's video settings cycle through every graphics mode, Fabulous! included, which the WebGL
     * emulation cannot render (vanilla's screen is limited to Fast and Fancy by GraphicsCompat). Its
     * CyclingControl shows names by ordinal but cycles through allowedValues, so that list is filtered by
     * SodiumCompat.allowedValues after the constructor filled it.
     */
    private static void sodium(Map<String, byte[]> classes) {
        sodiumChunkBuilder(classes);
        String control = "me/jellysquid/mods/sodium/client/gui/options/control/CyclingControl";
        if (!classes.containsKey(control)) {
            return;
        }
        patch(classes, control, node -> {
            MethodNode init = method(node, "<init>", "(Lme/jellysquid/mods/sodium/client/gui/options/Option;"
                    + "Ljava/lang/Class;[Ljava/lang/String;)V");
            if (init == null) {
                return false;
            }
            AbstractInsnNode ret = null;
            for (AbstractInsnNode insn = init.instructions.getFirst(); insn != null; insn = insn.getNext()) {
                if (insn.getOpcode() == Opcodes.RETURN) {
                    if (ret != null) {
                        return false; // expected a single return
                    }
                    ret = insn;
                }
            }
            if (ret == null) {
                return false;
            }
            InsnList filter = new InsnList();
            filter.add(new VarInsnNode(Opcodes.ALOAD, 0));
            filter.add(new VarInsnNode(Opcodes.ALOAD, 0));
            filter.add(new FieldInsnNode(Opcodes.GETFIELD, control, "allowedValues", "[Ljava/lang/Enum;"));
            filter.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "teafabric/web/compat/SodiumCompat", "allowedValues",
                    "([Ljava/lang/Enum;)[Ljava/lang/Enum;", false));
            filter.add(new FieldInsnNode(Opcodes.PUTFIELD, control, "allowedValues", "[Ljava/lang/Enum;"));
            init.instructions.insertBefore(ret, filter);
            return true;
        });
    }

    /**
     * Lithium's mixin plugin loads lithium.properties and stores it in LithiumMod.CONFIG; its initializer
     * fails when that did not happen. The plugin runs here at build time (it decides which mixins are baked
     * in), not in the browser, and nothing else reads CONFIG, so the check is dropped.
     */
    private static void lithium(Map<String, byte[]> classes) {
        String mod = "me/jellysquid/mods/lithium/common/LithiumMod";
        if (classes.containsKey(mod)) {
            patch(classes, mod, node -> {
                MethodNode init = method(node, "onInitialize", "()V");
                if (init == null) {
                    return false;
                }
                InsnList code = new InsnList();
                code.add(new InsnNode(Opcodes.RETURN));
                replace(init, code);
                return true;
            });
        }
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
        // FerriteConfig's static initialiser hands its options to ConfigFileHandler.finish through reflection
        // (Class.forName + getMethod + invoke): call it directly.
        String builder = "malte0811/ferritecore/mixin/config/FerriteConfig$ConfigBuilder";
        String fileHandler = "malte0811/ferritecore/mixin/config/ConfigFileHandler";
        if (classes.containsKey(builder) && classes.containsKey(fileHandler)) {
            patch(classes, builder, node -> {
                MethodNode finish = method(node, "finish", "()V");
                if (finish == null) {
                    return false;
                }
                InsnList code = new InsnList();
                code.add(new VarInsnNode(Opcodes.ALOAD, 0));
                code.add(new FieldInsnNode(Opcodes.GETFIELD, builder, "options", "Ljava/util/List;"));
                code.add(new MethodInsnNode(Opcodes.INVOKESTATIC, fileHandler, "finish", "(Ljava/util/List;)V", false));
                code.add(new InsnNode(Opcodes.RETURN));
                replace(finish, code);
                return true;
            });
        }
        if (defined > 0) {
            System.out.println("[baker] FerriteCore: " + defined + " Guava access classes defined at build time");
        }
    }

    /**
     * Sodium meshes chunks on worker threads, one per core. In the browser those are green threads sharing
     * the page's only thread, and a worker runs job after job without blocking, so the page drew no frames
     * until the whole queue was built (minutes after joining a world). One worker is enough, and it yields
     * after every job: Thread.yield hands the browser a turn when the current time slice is used up, like
     * Minecraft's own worker (MinecraftCompat.YieldingWorker).
     */
    private static void sodiumChunkBuilder(Map<String, byte[]> classes) {
        String builder = "me/jellysquid/mods/sodium/client/render/chunk/compile/ChunkBuilder";
        String worker = builder + "$WorkerRunnable";
        if (!classes.containsKey(builder) || !classes.containsKey(worker)) {
            return;
        }
        patch(classes, builder, node -> {
            MethodNode count = method(node, "getOptimalThreadCount", "()I");
            if (count == null) {
                return false;
            }
            InsnList code = new InsnList();
            code.add(new InsnNode(Opcodes.ICONST_1));
            code.add(new InsnNode(Opcodes.IRETURN));
            replace(count, code);
            return true;
        });
        patch(classes, worker, node -> {
            MethodNode run = method(node, "run", "()V");
            if (run == null) {
                return false;
            }
            // The loop starts at the method's first instruction (aload_0; getfield running): every job, and
            // every skipped cancelled job, jumps back there.
            AbstractInsnNode first = run.instructions.getFirst();
            while (first != null && first.getOpcode() < 0) {
                first = first.getNext();
            }
            AbstractInsnNode next = first == null ? null : first.getNext();
            if (first == null || first.getOpcode() != Opcodes.ALOAD || !(next instanceof FieldInsnNode)
                    || !((FieldInsnNode) next).name.equals("running")) {
                return false;
            }
            run.instructions.insertBefore(first,
                    new MethodInsnNode(Opcodes.INVOKESTATIC, "java/lang/Thread", "yield", "()V", false));
            return true;
        });
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
