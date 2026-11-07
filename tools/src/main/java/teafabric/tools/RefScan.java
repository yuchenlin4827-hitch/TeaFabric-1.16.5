package teafabric.tools;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.Handle;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

/**
 * Lists every member of a given package prefix referenced from a set of jars, with reference counts.
 * Used to size the browser replacements for native libraries, e.g.
 * {@code RefScan org/lwjgl/ baked.jar} prints the exact LWJGL surface Minecraft uses.
 */
public final class RefScan {
    private final String prefix;
    private final Map<String, Integer> refs = new TreeMap<>();
    private final Map<String, TreeSet<String>> users = new TreeMap<>();

    private RefScan(String prefix) {
        this.prefix = prefix;
    }

    public static void main(String[] args) throws IOException {
        RefScan scan = new RefScan(args[0]);
        boolean showUsers = Boolean.getBoolean("refscan.users");
        for (int i = 1; i < args.length; i++) {
            try (JarFile jar = new JarFile(args[i])) {
                for (JarEntry e : java.util.Collections.list(jar.entries())) {
                    if (e.getName().endsWith(".class") && !e.getName().startsWith(args[0])) {
                        try (InputStream in = jar.getInputStream(e)) {
                            scan.scan(in.readAllBytes());
                        }
                    }
                }
            }
        }
        for (Map.Entry<String, Integer> e : scan.refs.entrySet()) {
            System.out.println(e.getKey() + "  x" + e.getValue()
                    + (showUsers ? "  <- " + String.join(",", scan.users.get(e.getKey())) : ""));
        }
    }

    private void add(String key, String user) {
        refs.merge(key, 1, Integer::sum);
        TreeSet<String> u = users.computeIfAbsent(key, k -> new TreeSet<>());
        if (u.size() < 4) {
            u.add(user);
        }
    }

    private void type(Type t, String user) {
        if (t.getSort() == Type.ARRAY) {
            t = t.getElementType();
        }
        if (t.getSort() == Type.OBJECT && t.getInternalName().startsWith(prefix)) {
            add(t.getInternalName(), user);
        }
    }

    private void desc(String desc, String user) {
        Type t = Type.getType(desc);
        if (t.getSort() == Type.METHOD) {
            for (Type a : t.getArgumentTypes()) {
                type(a, user);
            }
            type(t.getReturnType(), user);
        } else {
            type(t, user);
        }
    }

    private void scan(byte[] bytes) {
        new ClassReader(bytes).accept(new ClassVisitor(Opcodes.ASM9) {
            String cls;

            @Override
            public void visit(int version, int access, String name, String signature, String superName,
                    String[] interfaces) {
                cls = name;
                if (superName != null && superName.startsWith(prefix)) {
                    add(superName + " (extends)", cls);
                }
                if (interfaces != null) {
                    for (String i : interfaces) {
                        if (i.startsWith(prefix)) {
                            add(i + " (implements)", cls);
                        }
                    }
                }
            }

            @Override
            public FieldVisitor visitField(int access, String name, String descriptor, String signature,
                    Object value) {
                desc(descriptor, cls);
                return null;
            }

            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor, String signature,
                    String[] exceptions) {
                desc(descriptor, cls);
                String user = cls;
                return new MethodVisitor(Opcodes.ASM9) {
                    @Override
                    public void visitMethodInsn(int opcode, String owner, String mname, String mdesc,
                            boolean itf) {
                        if (owner.startsWith(prefix)) {
                            add(owner + "." + mname + mdesc, user);
                        }
                    }

                    @Override
                    public void visitFieldInsn(int opcode, String owner, String fname, String fdesc) {
                        if (owner.startsWith(prefix)) {
                            add(owner + "." + fname + ":" + fdesc, user);
                        }
                    }

                    @Override
                    public void visitTypeInsn(int opcode, String type) {
                        type(Type.getObjectType(type), user);
                    }

                    @Override
                    public void visitLdcInsn(Object value) {
                        if (value instanceof Type) {
                            type((Type) value, user);
                        }
                    }

                    @Override
                    public void visitInvokeDynamicInsn(String n, String d, Handle bsm, Object... args) {
                        desc(d, user);
                        for (Object a : args) {
                            if (a instanceof Handle && ((Handle) a).getOwner().startsWith(prefix)) {
                                Handle h = (Handle) a;
                                add(h.getOwner() + "." + h.getName() + h.getDesc(), user);
                            }
                        }
                    }
                };
            }
        }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
    }
}
