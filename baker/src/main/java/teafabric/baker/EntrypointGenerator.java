package teafabric.baker;

import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import net.fabricmc.loader.impl.ModContainerImpl;
import net.fabricmc.loader.impl.metadata.EntrypointMetadata;
import net.fabricmc.loader.impl.metadata.LoaderModMetadata;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodNode;

/**
 * Generates {@code teafabric.generated.Entrypoints}, the ahead-of-time replacement for Fabric's
 * reflective entrypoint instantiation:
 *
 * <pre>
 * public static void create(String key, java.util.List out) {
 *     if (key.equals("client")) {
 *         out.add(new Object[] {"modid", new com.example.ClientInit()});
 *         ...
 *     }
 * }
 * </pre>
 *
 * Supported value forms are those of Fabric's default language adapter that need no reflection:
 * {@code a.b.C} (public no-arg constructor) and {@code a.b.C::FIELD} (static field). Anything
 * else is reported and skipped.
 */
final class EntrypointGenerator implements Opcodes {
    static final String CLASS_NAME = "teafabric/generated/Entrypoints";

    private final Map<String, byte[]> classes;

    EntrypointGenerator(Map<String, byte[]> classes) {
        this.classes = classes;
    }

    byte[] generate(List<ModContainerImpl> mods) {
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
        cw.visit(V1_8, ACC_PUBLIC | ACC_FINAL | ACC_SUPER, CLASS_NAME, null, "java/lang/Object", null);
        MethodVisitor mv = cw.visitMethod(ACC_PUBLIC | ACC_STATIC, "create", "(Ljava/lang/String;Ljava/util/List;)V",
                null, null);
        mv.visitCode();
        TreeSet<String> keys = new TreeSet<>();
        for (ModContainerImpl mod : mods) {
            keys.addAll(mod.getMetadata().getEntrypointKeys());
        }
        int count = 0;
        for (String key : keys) {
            Label next = new Label();
            mv.visitVarInsn(ALOAD, 0);
            mv.visitLdcInsn(key);
            mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/String", "equals", "(Ljava/lang/Object;)Z", false);
            mv.visitJumpInsn(IFEQ, next);
            for (ModContainerImpl mod : mods) {
                LoaderModMetadata meta = mod.getMetadata();
                for (EntrypointMetadata ep : meta.getEntrypoints(key)) {
                    if (emit(mv, meta.getId(), key, ep)) {
                        count++;
                    }
                }
            }
            mv.visitInsn(RETURN);
            mv.visitLabel(next);
        }
        mv.visitInsn(RETURN);
        mv.visitMaxs(0, 0);
        mv.visitEnd();
        cw.visitEnd();
        System.out.println("[baker] Generated " + CLASS_NAME + " with " + count + " entrypoints over "
                + keys.size() + " keys");
        return cw.toByteArray();
    }

    private boolean emit(MethodVisitor mv, String modId, String key, EntrypointMetadata ep) {
        if (!ep.getAdapter().equals("default")) {
            System.err.println("[baker] Skipping entrypoint " + key + " of " + modId + ": language adapter '"
                    + ep.getAdapter() + "' is not supported ahead of time");
            return false;
        }
        String value = ep.getValue();
        String className = value;
        String member = null;
        int sep = value.indexOf("::");
        if (sep >= 0) {
            className = value.substring(0, sep);
            member = value.substring(sep + 2);
        }
        String internal = className.replace('.', '/');
        byte[] bytes = classes.get(internal);
        if (bytes == null) {
            System.err.println("[baker] Skipping entrypoint " + key + " of " + modId + ": class " + className
                    + " not found");
            return false;
        }
        ClassNode node = new ClassNode();
        new ClassReader(bytes).accept(node, ClassReader.SKIP_CODE);
        String fieldDesc = null;
        String proxy = null;
        if (member != null) {
            for (FieldNode f : node.fields) {
                if (f.name.equals(member) && (f.access & ACC_STATIC) != 0) {
                    fieldDesc = f.desc;
                }
            }
            if (fieldDesc == null) {
                proxy = generateProxy(modId, key, internal, member, node);
                if (proxy == null) {
                    return false;
                }
            }
        } else {
            boolean hasCtor = false;
            for (MethodNode m : node.methods) {
                if (m.name.equals("<init>") && m.desc.equals("()V")) {
                    hasCtor = true;
                }
            }
            if (!hasCtor || (node.access & (ACC_ABSTRACT | ACC_INTERFACE)) != 0) {
                System.err.println("[baker] Skipping entrypoint " + key + " of " + modId + ": " + className
                        + " has no no-arg constructor");
                return false;
            }
        }
        // out.add(new Object[] {modId, instance});
        mv.visitVarInsn(ALOAD, 1);
        mv.visitInsn(ICONST_2);
        mv.visitTypeInsn(ANEWARRAY, "java/lang/Object");
        mv.visitInsn(DUP);
        mv.visitInsn(ICONST_0);
        mv.visitLdcInsn(modId);
        mv.visitInsn(AASTORE);
        mv.visitInsn(DUP);
        mv.visitInsn(ICONST_1);
        if (proxy != null) {
            mv.visitTypeInsn(NEW, proxy);
            mv.visitInsn(DUP);
            mv.visitMethodInsn(INVOKESPECIAL, proxy, "<init>", "()V", false);
        } else if (member != null) {
            mv.visitFieldInsn(GETSTATIC, internal, member, fieldDesc);
        } else {
            mv.visitTypeInsn(NEW, internal);
            mv.visitInsn(DUP);
            mv.visitMethodInsn(INVOKESPECIAL, internal, "<init>", "()V", false);
        }
        mv.visitInsn(AASTORE);
        mv.visitMethodInsn(INVOKEINTERFACE, "java/util/List", "add", "(Ljava/lang/Object;)Z", true);
        mv.visitInsn(POP);
        return true;
    }

    /** Entrypoint keys whose interface Fabric's default adapter can proxy a method reference to. */
    private static final Map<String, String[]> PROXYABLE = Map.of(
            "main", new String[] {"net/fabricmc/api/ModInitializer", "onInitialize"},
            "client", new String[] {"net/fabricmc/api/ClientModInitializer", "onInitializeClient"},
            "server", new String[] {"net/fabricmc/api/DedicatedServerModInitializer", "onInitializeServer"},
            "preLaunch", new String[] {"net/fabricmc/loader/api/entrypoint/PreLaunchEntrypoint", "onPreLaunch"});

    private int proxyCount;

    /**
     * {@code a.b.C::method} with a no-arg method: emit a class implementing the entrypoint interface
     * whose single method calls it (statically, or on a fresh instance for instance methods).
     */
    private String generateProxy(String modId, String key, String owner, String member, ClassNode node) {
        String[] iface = PROXYABLE.get(key);
        MethodNode target = null;
        for (MethodNode m : node.methods) {
            if (m.name.equals(member) && m.desc.equals("()V")) {
                target = m;
            }
        }
        if (iface == null || target == null) {
            System.err.println("[baker] Skipping entrypoint " + key + " of " + modId + ": cannot proxy "
                    + owner + "::" + member + " without reflection");
            return null;
        }
        String name = "teafabric/generated/EntrypointProxy" + (proxyCount++);
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
        cw.visit(V1_8, ACC_PUBLIC | ACC_FINAL | ACC_SUPER, name, null, "java/lang/Object", new String[] {iface[0]});
        MethodVisitor init = cw.visitMethod(ACC_PUBLIC, "<init>", "()V", null, null);
        init.visitCode();
        init.visitVarInsn(ALOAD, 0);
        init.visitMethodInsn(INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
        init.visitInsn(RETURN);
        init.visitMaxs(0, 0);
        init.visitEnd();
        MethodVisitor mv = cw.visitMethod(ACC_PUBLIC, iface[1], "()V", null, null);
        mv.visitCode();
        boolean itf = (node.access & ACC_INTERFACE) != 0;
        if ((target.access & ACC_STATIC) != 0) {
            mv.visitMethodInsn(INVOKESTATIC, owner, member, "()V", itf);
        } else {
            mv.visitTypeInsn(NEW, owner);
            mv.visitInsn(DUP);
            mv.visitMethodInsn(INVOKESPECIAL, owner, "<init>", "()V", false);
            mv.visitMethodInsn(INVOKEVIRTUAL, owner, member, "()V", false);
        }
        mv.visitInsn(RETURN);
        mv.visitMaxs(0, 0);
        mv.visitEnd();
        cw.visitEnd();
        classes.put(name, cw.toByteArray());
        return name;
    }
}
