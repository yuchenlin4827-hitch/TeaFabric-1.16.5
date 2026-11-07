package teafabric.tools;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Modifier;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

/**
 * Generates compile-time stubs for platform APIs that cannot exist in a browser (raw sockets,
 * NIO selectors, SSL, AWT, JNA, ...) from TeaVM's {@code missing.tsv}.
 *
 * <ul>
 *   <li>A missing <b>class</b> becomes a stub with the real class's shape taken from the host JDK
 *       (superclass, interfaces, interface/abstract flags). {@code java.*} classes are emitted as
 *       TeaVM class-library substitutes ({@code org.teavm.classlib.java.x.TName}).</li>
 *   <li>A missing <b>method</b> of a stub is added with a body that throws
 *       {@code UnsupportedOperationException("... is not available in the browser")}; throwable
 *       stubs get working constructors.</li>
 *   <li>A missing method of a class that <em>does</em> exist (TeaVM's class library is incomplete)
 *       becomes a throwing static redirect target plus a rule for the call-site redirect plugin.</li>
 * </ul>
 *
 * Requests accumulate in {@code stubs.tsv} so repeated runs converge; every stub is listed in the
 * generated jar's {@code META-INF/teafabric/stubs.txt}.
 */
public final class AutoStub implements Opcodes {
    private final Set<String> present = new HashSet<>();          // internal names on the program class path
    private final Map<String, Integer> invokeOpcode = new HashMap<>(); // owner.name+desc -> opcode
    private final Map<String, String> fieldDesc = new HashMap<>();     // owner.name -> desc
    private final Map<String, Boolean> fieldStatic = new HashMap<>();
    /** Types TeaVM does not report when missing: catch clauses, checkcast and instanceof targets. */
    private final Set<String> silentTypes = new TreeSet<>();
    private final ClassLoader host;

    private final Map<String, StubClass> stubs = new TreeMap<>();     // original internal name -> stub
    private final Map<String, Set<String[]>> redirects = new TreeMap<>(); // owner -> {name, desc, static}
    private final List<String> unsupported = new ArrayList<>();

    static final class StubClass {
        String original;
        boolean isInterface;
        boolean isAbstract;
        String superName = "java/lang/Object";
        final List<String> interfaces = new ArrayList<>();
        final Map<String, Boolean> methods = new TreeMap<>(); // name+desc -> static
        final Map<String, String[]> fields = new TreeMap<>();  // name -> {desc, static}
        boolean throwable;
    }

    private AutoStub(List<File> classpath) throws IOException {
        List<URL> urls = new ArrayList<>();
        for (File f : classpath) {
            urls.add(f.toURI().toURL());
            index(f);
        }
        host = new URLClassLoader(urls.toArray(new URL[0]), ClassLoader.getPlatformClassLoader());
    }

    public static void main(String[] args) throws Exception {
        Map<String, String> o = Setup.parseArgs(args);
        List<File> cp = new ArrayList<>();
        for (String s : o.get("classpath").split(File.pathSeparator)) {
            if (!s.isEmpty() && new File(s).exists()) {
                cp.add(new File(s));
            }
        }
        Path state = Path.of(o.get("state"));
        Set<String> requests = new TreeSet<>();
        if (Files.exists(state)) {
            requests.addAll(Files.readAllLines(state, StandardCharsets.UTF_8));
        }
        Path missing = Path.of(o.get("missing"));
        int before = requests.size();
        if (Files.exists(missing)) {
            requests.addAll(Files.readAllLines(missing, StandardCharsets.UTF_8));
        }
        requests.remove("");
        Files.createDirectories(state.toAbsolutePath().getParent());
        Files.write(state, requests, StandardCharsets.UTF_8);
        AutoStub stub = new AutoStub(cp);
        for (String r : requests) {
            stub.request(r.split("\t"));
        }
        stub.stubSilentTypes();
        stub.write(Path.of(o.get("out")));
        Setup.log("AutoStub: " + requests.size() + " requests (" + (requests.size() - before) + " new), "
                + stub.stubs.size() + " stub classes, " + stub.redirects.size() + " redirected owners, "
                + stub.unsupported.size() + " unsupported");
        for (String u : stub.unsupported) {
            Setup.log("  unsupported: " + u);
        }
    }

    // ------------------------------------------------------------------ indexing

    private void index(File f) throws IOException {
        if (f.isDirectory()) {
            try (var walk = Files.walk(f.toPath())) {
                for (Path p : (Iterable<Path>) walk::iterator) {
                    if (p.toString().endsWith(".class")) {
                        try (InputStream in = Files.newInputStream(p)) {
                            indexClass(in.readAllBytes());
                        }
                    }
                }
            }
            return;
        }
        try (JarFile jar = new JarFile(f)) {
            for (JarEntry e : Collections.list(jar.entries())) {
                if (e.getName().endsWith(".class") && !e.getName().startsWith("META-INF/")) {
                    try (InputStream in = jar.getInputStream(e)) {
                        indexClass(in.readAllBytes());
                    }
                }
            }
        }
    }

    private void indexClass(byte[] bytes) {
        ClassReader reader = new ClassReader(bytes);
        present.add(reader.getClassName());
        reader.accept(new ClassVisitor(ASM9) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor, String signature,
                    String[] exceptions) {
                return new MethodVisitor(ASM9) {
                    @Override
                    public void visitMethodInsn(int opcode, String owner, String mname, String mdesc, boolean itf) {
                        invokeOpcode.putIfAbsent(owner + "." + mname + mdesc, opcode);
                    }

                    @Override
                    public void visitTryCatchBlock(org.objectweb.asm.Label start, org.objectweb.asm.Label end,
                            org.objectweb.asm.Label handler, String type) {
                        if (type != null) {
                            silentTypes.add(type);
                        }
                    }

                    @Override
                    public void visitTypeInsn(int opcode, String type) {
                        if ((opcode == CHECKCAST || opcode == INSTANCEOF) && !type.startsWith("[")) {
                            silentTypes.add(type);
                        }
                    }

                    @Override
                    public void visitFieldInsn(int opcode, String owner, String fname, String fdesc) {
                        fieldDesc.putIfAbsent(owner + "." + fname, fdesc);
                        fieldStatic.putIfAbsent(owner + "." + fname, opcode == GETSTATIC || opcode == PUTSTATIC);
                    }
                };
            }
        }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
    }

    // ------------------------------------------------------------------ resolution

    /** Name under which TeaVM will look the class up. */
    static String substituteName(String internal) {
        if (internal.startsWith("java/")) {
            int slash = internal.lastIndexOf('/');
            return "org/teavm/classlib/" + internal.substring(0, slash + 1) + "T" + internal.substring(slash + 1);
        }
        return internal;
    }

    private boolean exists(String internal) {
        if (internal.startsWith("java/")) {
            return present.contains(substituteName(internal));
        }
        return present.contains(internal);
    }

    private Class<?> hostClass(String internal) {
        try {
            return Class.forName(internal.replace('/', '.'), false, host);
        } catch (ClassNotFoundException | LinkageError e) {
            return null;
        }
    }

    private StubClass stub(String internal) {
        StubClass s = stubs.get(internal);
        if (s != null) {
            return s;
        }
        s = new StubClass();
        s.original = internal;
        stubs.put(internal, s);
        Class<?> c = hostClass(internal);
        if (c != null) {
            s.isInterface = c.isInterface();
            s.isAbstract = Modifier.isAbstract(c.getModifiers()) && !c.isInterface();
            s.throwable = Throwable.class.isAssignableFrom(c);
            if (c.getSuperclass() != null && !c.isInterface()) {
                s.superName = ensure(Type.getInternalName(c.getSuperclass()));
            }
            for (Class<?> i : c.getInterfaces()) {
                String in = Type.getInternalName(i);
                if (ensureOptional(in)) {
                    s.interfaces.add(in);
                }
            }
        }
        return s;
    }

    /** Makes sure a superclass exists, stubbing it if needed; falls back to Object. */
    private String ensure(String internal) {
        if (internal.equals("java/lang/Object") || exists(internal)) {
            return internal;
        }
        if (internal.startsWith("java/lang/") && !internal.startsWith("java/lang/management/")
                && !internal.startsWith("java/lang/invoke/") && hostClass(internal) == null) {
            return "java/lang/Object";
        }
        stub(internal);
        return internal;
    }

    private boolean ensureOptional(String internal) {
        if (exists(internal)) {
            return true;
        }
        if (internal.startsWith("java/") || internal.startsWith("javax/")) {
            stub(internal);
            return true;
        }
        return false;
    }

    private void request(String[] r) {
        switch (r[0]) {
            case "class": {
                String internal = r[1].replace('.', '/');
                if (!exists(internal)) {
                    stub(internal);
                }
                break;
            }
            case "method": {
                String owner = r[1].replace('.', '/');
                String name = r[2];
                String desc = r[3];
                Integer op = invokeOpcode.get(owner + "." + name + desc);
                boolean isStatic = op != null && op == INVOKESTATIC;
                if (!exists(owner) || stubs.containsKey(owner)) {
                    stub(owner).methods.put(name + desc, isStatic);
                } else {
                    redirects.computeIfAbsent(owner, k -> new LinkedHashSet<>())
                            .add(new String[] {name, desc, String.valueOf(isStatic)});
                }
                break;
            }
            case "field": {
                String owner = r[1].replace('.', '/');
                String key = owner + "." + r[2];
                String desc = fieldDesc.getOrDefault(key, "Ljava/lang/Object;");
                boolean isStatic = fieldStatic.getOrDefault(key, true);
                if (!exists(owner) || stubs.containsKey(owner)) {
                    stub(owner).fields.put(r[2], new String[] {desc, String.valueOf(isStatic)});
                } else {
                    unsupported.add("field " + key + " (class exists)");
                }
                break;
            }
            default:
                break;
        }
    }

    /** JDK types used only in catch clauses / casts that are missing from TeaVM's class library. */
    private void stubSilentTypes() {
        for (String type : silentTypes) {
            if ((type.startsWith("java/") || type.startsWith("javax/")) && !exists(type) && hostClass(type) != null) {
                stub(type);
            }
        }
    }

    // ------------------------------------------------------------------ generation

    private void write(Path out) throws IOException {
        Files.createDirectories(out.toAbsolutePath().getParent());
        StringBuilder rules = new StringBuilder("# Generated by teafabric.tools.AutoStub\n");
        StringBuilder listing = new StringBuilder();
        try (OutputStream fos = Files.newOutputStream(out); ZipOutputStream zip = new ZipOutputStream(fos)) {
            for (StubClass s : new ArrayList<>(stubs.values())) {
                put(zip, substituteName(s.original) + ".class", generate(s));
                listing.append(s.original.replace('/', '.')).append('\n');
            }
            for (Map.Entry<String, Set<String[]>> e : redirects.entrySet()) {
                String target = "teafabric/web/compat/auto/" + e.getKey().replace('/', '_').replace('$', '_');
                put(zip, target + ".class", generateRedirectTarget(target, e.getKey(), e.getValue()));
                for (String[] m : e.getValue()) {
                    rules.append(e.getKey().replace('/', '.')).append(' ').append(m[0]).append(' ').append(m[1])
                            .append(" -> ").append(target.replace('/', '.')).append('\n');
                }
            }
            put(zip, "META-INF/teafabric/redirects.txt", rules.toString().getBytes(StandardCharsets.UTF_8));
            put(zip, "META-INF/teafabric/stubs.txt", listing.toString().getBytes(StandardCharsets.UTF_8));
        }
    }

    private static void put(ZipOutputStream zip, String name, byte[] data) throws IOException {
        ZipEntry e = new ZipEntry(name);
        e.setTime(0);
        zip.putNextEntry(e);
        zip.write(data);
        zip.closeEntry();
    }

    private static void throwUnsupported(MethodVisitor mv, String what) {
        mv.visitTypeInsn(NEW, "java/lang/UnsupportedOperationException");
        mv.visitInsn(DUP);
        mv.visitLdcInsn(what + " is not available in the browser");
        mv.visitMethodInsn(INVOKESPECIAL, "java/lang/UnsupportedOperationException", "<init>",
                "(Ljava/lang/String;)V", false);
        mv.visitInsn(ATHROW);
    }

    private byte[] generate(StubClass s) {
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES) {
            @Override
            protected String getCommonSuperClass(String a, String b) {
                return "java/lang/Object";
            }
        };
        int access = ACC_PUBLIC | (s.isInterface ? ACC_INTERFACE | ACC_ABSTRACT : ACC_SUPER)
                | (s.isAbstract ? ACC_ABSTRACT : 0);
        String name = substituteName(s.original);
        cw.visit(V1_8, access, name, null, s.isInterface ? "java/lang/Object" : s.superName,
                s.interfaces.toArray(new String[0]));
        String pretty = s.original.replace('/', '.');
        for (Map.Entry<String, String[]> f : s.fields.entrySet()) {
            boolean st = Boolean.parseBoolean(f.getValue()[1]);
            cw.visitField(ACC_PUBLIC | (st ? ACC_STATIC : 0), f.getKey(), f.getValue()[0], null, null).visitEnd();
        }
        Set<String> ctors = new TreeSet<>();
        for (Map.Entry<String, Boolean> m : s.methods.entrySet()) {
            String key = m.getKey();
            int paren = key.indexOf('(');
            String mname = key.substring(0, paren);
            String mdesc = key.substring(paren);
            if (mname.equals("<init>")) {
                ctors.add(mdesc);
                continue;
            }
            if (mname.equals("<clinit>")) {
                continue;
            }
            boolean st = m.getValue();
            if (s.isInterface && !st) {
                cw.visitMethod(ACC_PUBLIC | ACC_ABSTRACT, mname, mdesc, null, null).visitEnd();
                continue;
            }
            MethodVisitor mv = cw.visitMethod(ACC_PUBLIC | (st ? ACC_STATIC : 0), mname, mdesc, null, null);
            mv.visitCode();
            throwUnsupported(mv, pretty + "." + mname);
            mv.visitMaxs(0, 0);
            mv.visitEnd();
        }
        if (!s.isInterface) {
            if (s.throwable) {
                ctors.add("()V");
                ctors.add("(Ljava/lang/String;)V");
                ctors.add("(Ljava/lang/String;Ljava/lang/Throwable;)V");
                ctors.add("(Ljava/lang/Throwable;)V");
            } else {
                ctors.add("()V");
            }
            for (String desc : ctors) {
                MethodVisitor mv = cw.visitMethod(ACC_PUBLIC, "<init>", desc, null, null);
                mv.visitCode();
                boolean delegate = s.throwable && (desc.equals("()V") || desc.equals("(Ljava/lang/String;)V")
                        || desc.equals("(Ljava/lang/String;Ljava/lang/Throwable;)V")
                        || desc.equals("(Ljava/lang/Throwable;)V"));
                if (delegate) {
                    mv.visitVarInsn(ALOAD, 0);
                    Type[] args = Type.getArgumentTypes(desc);
                    for (int i = 0; i < args.length; i++) {
                        mv.visitVarInsn(ALOAD, i + 1);
                    }
                    mv.visitMethodInsn(INVOKESPECIAL, s.superName, "<init>", desc, false);
                    mv.visitInsn(RETURN);
                } else if (desc.equals("()V")) {
                    // Allows subclassing (e.g. anonymous Authenticator subclasses) without failing.
                    mv.visitVarInsn(ALOAD, 0);
                    mv.visitMethodInsn(INVOKESPECIAL, s.superName, "<init>", "()V", false);
                    mv.visitInsn(RETURN);
                } else {
                    mv.visitVarInsn(ALOAD, 0);
                    mv.visitMethodInsn(INVOKESPECIAL, s.superName, "<init>", "()V", false);
                    throwUnsupported(mv, "new " + pretty);
                }
                mv.visitMaxs(0, 0);
                mv.visitEnd();
            }
        }
        cw.visitEnd();
        return cw.toByteArray();
    }

    private static byte[] generateRedirectTarget(String target, String owner, Set<String[]> methods) {
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES) {
            @Override
            protected String getCommonSuperClass(String a, String b) {
                return "java/lang/Object";
            }
        };
        cw.visit(V1_8, ACC_PUBLIC | ACC_FINAL | ACC_SUPER, target, null, "java/lang/Object", null);
        Set<String> seen = new HashSet<>();
        for (String[] m : methods) {
            boolean st = Boolean.parseBoolean(m[2]);
            String name = m[0];
            String desc = st ? m[1] : "(L" + owner + ";" + m[1].substring(1);
            if (name.equals("<init>")) { // constructor redirect: static factory returning the owner type
                name = "construct" + owner.substring(owner.lastIndexOf('/') + 1).replace('$', '_');
                desc = m[1].substring(0, m[1].length() - 1) + "L" + owner + ";";
            }
            if (!seen.add(name + desc)) {
                continue;
            }
            MethodVisitor mv = cw.visitMethod(ACC_PUBLIC | ACC_STATIC, name, desc, null, null);
            mv.visitCode();
            throwUnsupported(mv, owner.replace('/', '.') + "." + m[0]);
            mv.visitMaxs(0, 0);
            mv.visitEnd();
        }
        cw.visitEnd();
        return cw.toByteArray();
    }
}
