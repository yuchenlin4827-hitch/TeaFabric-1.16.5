package teafabric.baker;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.entrypoint.PreLaunchEntrypoint;
import net.fabricmc.loader.api.metadata.CustomValue;
import net.fabricmc.loader.api.metadata.ModDependency;
import net.fabricmc.loader.api.metadata.Person;
import net.fabricmc.loader.impl.FabricLoaderImpl;
import net.fabricmc.loader.impl.ModContainerImpl;
import net.fabricmc.loader.impl.launch.FabricLauncher;
import net.fabricmc.loader.impl.launch.FabricLauncherBase;
import net.fabricmc.loader.impl.metadata.EntrypointMetadata;
import net.fabricmc.loader.impl.metadata.LoaderModMetadata;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.MixinEnvironment;
import org.spongepowered.asm.mixin.transformer.IMixinTransformer;

/**
 * Build-time "mixin baking".
 *
 * <p>This runs as a {@code preLaunch} entrypoint inside the <em>real</em> Fabric Loader (Knot), after
 * mod discovery, intermediary remapping, access wideners and Mixin bootstrap, but before Minecraft's
 * own {@code main}. Every class of every loaded mod (including Minecraft itself) is pushed through
 * Knot's transformation chain and the resulting bytes are written to a jar. That jar is what TeaVM
 * compiles: the browser never needs a class loader, Mixin or ASM at runtime.
 *
 * <p>Mod metadata and entrypoints are serialised to {@code teafabric/mods.json} so that the web
 * runtime can provide {@code FabricLoader} APIs without scanning jars.
 */
public final class Baker implements PreLaunchEntrypoint {
    private static final String SYNTHETIC_PREFIX = "org/spongepowered/asm/synthetic/";
    /** Resource prefix of each (non-Minecraft) mod's root inside the baked jar. */
    static final String MOD_ROOT = "teafabric/mods/";

    private final List<ModContainerImpl> entrypoints = new ArrayList<>();

    private final Map<String, byte[]> classes = new LinkedHashMap<>();
    private final Map<String, byte[]> resources = new LinkedHashMap<>();
    private final Set<String> mixinPackages = new HashSet<>();
    private int transformed;
    private int failed;
    private int skippedMixins;

    @Override
    public void onPreLaunch() {
        String outProp = System.getProperty("teafabric.bake.output");
        if (outProp == null) {
            System.err.println("[baker] teafabric.bake.output not set; not baking");
            return;
        }
        long start = System.currentTimeMillis();
        try {
            bake(Paths.get(outProp));
        } catch (Throwable t) {
            t.printStackTrace();
            System.exit(2);
        }
        System.out.println("[baker] Done in " + (System.currentTimeMillis() - start) + " ms");
        System.out.flush();
        // We only wanted the transformed bytecode; never start the actual game.
        System.exit(0);
    }

    private void bake(Path output) throws IOException {
        FabricLoaderImpl loader = FabricLoaderImpl.INSTANCE;
        FabricLauncher launcher = FabricLauncherBase.getLauncher();
        List<ModContainerImpl> mods = loader.getModsInternal();
        collectMixinPackages(mods);
        System.out.println("[baker] Mixin packages: " + mixinPackages);

        StringBuilder json = new StringBuilder();
        json.append("{\"loaderVersion\":").append(str(FabricLoaderImpl.VERSION))
                .append(",\"gameVersion\":").append(str(loader.getRawGameVersion()))
                .append(",\"mods\":[");
        boolean first = true;
        for (ModContainerImpl mod : mods) {
            LoaderModMetadata meta = mod.getMetadata();
            String id = meta.getId();
            System.out.println("[baker] Mod " + id + " " + meta.getVersion().getFriendlyString()
                    + " (" + mod.getOrigin() + ")");
            if (id.equals("teafabric_baker")) {
                continue;
            }
            if (!first) {
                json.append(',');
            }
            first = false;
            writeModJson(json, mod);
            if (id.equals("java") || id.equals("teafabric_baker")) {
                continue;
            }
            for (Path root : mod.getRootPaths()) {
                collectRoot(launcher, id, root);
            }
            entrypoints.add(mod);
        }
        json.append("]}");
        resources.put("teafabric/mods.json", json.toString().getBytes(StandardCharsets.UTF_8));

        ModCompat.apply(classes, resources);
        writeReflectiveClasses();
        generateSyntheticClasses();
        collectMixinExtrasClasses();
        classes.put(EntrypointGenerator.CLASS_NAME, new EntrypointGenerator(classes).generate(entrypoints));
        classes.put(GenericSupersGenerator.CLASS_NAME, new GenericSupersGenerator(classes).generate());

        Files.createDirectories(output.toAbsolutePath().getParent());
        try (OutputStream fos = Files.newOutputStream(output);
             ZipOutputStream zip = new ZipOutputStream(new BufferedOutputStream(fos, 1 << 20))) {
            for (Map.Entry<String, byte[]> e : classes.entrySet()) {
                put(zip, e.getKey() + ".class", e.getValue());
            }
            for (Map.Entry<String, byte[]> e : resources.entrySet()) {
                if (!classes.containsKey(stripClass(e.getKey()))) {
                    put(zip, e.getKey(), e.getValue());
                }
            }
        }
        System.out.println("[baker] Wrote " + output + ": " + classes.size() + " classes ("
                + transformed + " through Knot transformers, " + skippedMixins + " mixin classes dropped, "
                + failed + " failures), " + resources.size() + " resources");
    }

    private static String stripClass(String name) {
        return name.endsWith(".class") ? name.substring(0, name.length() - 6) : name;
    }

    private void collectMixinPackages(List<ModContainerImpl> mods) {
        for (ModContainerImpl mod : mods) {
            for (String config : mod.getMetadata().getMixinConfigs(EnvType.CLIENT)) {
                for (Path root : mod.getRootPaths()) {
                    Path p = root.resolve(config);
                    if (Files.isRegularFile(p)) {
                        try {
                            String text = new String(Files.readAllBytes(p), StandardCharsets.UTF_8);
                            java.util.regex.Matcher m = java.util.regex.Pattern
                                    .compile("\"package\"\\s*:\\s*\"([^\"]+)\"").matcher(text);
                            if (m.find()) {
                                mixinPackages.add(m.group(1).replace('.', '/') + "/");
                            }
                        } catch (IOException e) {
                            System.err.println("[baker] Cannot read mixin config " + p + ": " + e);
                        }
                    }
                }
            }
        }
    }

    private boolean inMixinPackage(String internalName) {
        for (String pkg : mixinPackages) {
            if (internalName.startsWith(pkg)) {
                return true;
            }
        }
        return false;
    }

    private void collectRoot(FabricLauncher launcher, String modId, Path root) throws IOException {
        List<Path> files = new ArrayList<>();
        try (Stream<Path> walk = Files.walk(root)) {
            walk.filter(Files::isRegularFile).forEach(files::add);
        }
        Map<String, Path> multiRelease = new LinkedHashMap<>();
        Map<String, Integer> multiReleaseVersion = new LinkedHashMap<>();
        for (Path file : files) {
            String rel = root.relativize(file).toString().replace('\\', '/');
            int version = multiReleaseVersion(rel);
            if (version > 0 && version <= Runtime.version().feature()) {
                // Multi-release jar: on the JVM this class shadows (or only exists in) the root.
                String internal = rel.substring(rel.indexOf('/', "META-INF/versions/".length()) + 1, rel.length() - 6);
                Integer seen = multiReleaseVersion.get(internal);
                if (seen == null || seen < version) {
                    multiReleaseVersion.put(internal, version);
                    multiRelease.put(internal, file);
                }
            }
            if (rel.startsWith("META-INF/") && (rel.endsWith(".SF") || rel.endsWith(".RSA")
                    || rel.endsWith(".DSA") || rel.equals("META-INF/MANIFEST.MF"))) {
                continue;
            }
            if (rel.startsWith("META-INF/jars/")) {
                continue; // nested jars are separate mod containers
            }
            byte[] raw = Files.readAllBytes(file);
            if (!rel.endsWith(".class") || rel.equals("module-info.class") || rel.startsWith("META-INF/versions/")) {
                // Minecraft's resources stay at the root (vanilla looks them up on the class path); every
                // other mod gets its own root so ModContainer.getRootPath() behaves like a mod jar.
                resources.putIfAbsent(modId.equals("minecraft") ? rel : MOD_ROOT + modId + "/" + rel, raw);
                continue;
            }
            String internal = rel.substring(0, rel.length() - 6);
            if (classes.containsKey(internal)) {
                continue;
            }
            if (inMixinPackage(internal)) {
                // Mixin bodies are merged into their targets. Only interface mixins (accessors / invokers,
                // which are referenced through casts) survive as real classes.
                if ((new ClassReader(raw).getAccess() & Opcodes.ACC_INTERFACE) != 0) {
                    // Accessor/invoker interfaces: Mixin implements their *static* members by
                    // transforming the interface itself, so it must go through the transformer too.
                    classes.put(internal, transformAccessor(internal, raw));
                } else {
                    skippedMixins++;
                }
                continue;
            }
            classes.put(internal, transform(launcher, modId, internal, raw));
        }
        // Versioned classes without a root counterpart (e.g. the jvmdg API stubs that ViaVersion's
        // downgraded build only ships under META-INF/versions/N/). Root classes win otherwise: they are the
        // Java 8 variants, which TeaVM's class library covers best.
        for (Map.Entry<String, Path> e : multiRelease.entrySet()) {
            String internal = e.getKey();
            if (!classes.containsKey(internal)) {
                classes.put(internal, transform(launcher, modId, internal, Files.readAllBytes(e.getValue())));
                System.out.println("[baker] Multi-release class " + internal + " (Java "
                        + multiReleaseVersion.get(internal) + ")");
            }
        }
    }

    /** N for {@code META-INF/versions/N/...class}, otherwise 0. */
    private static int multiReleaseVersion(String rel) {
        if (!rel.startsWith("META-INF/versions/") || !rel.endsWith(".class")) {
            return 0;
        }
        int start = "META-INF/versions/".length();
        int slash = rel.indexOf('/', start);
        if (slash < 0) {
            return 0;
        }
        try {
            return Integer.parseInt(rel.substring(start, slash));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private byte[] transformAccessor(String internal, byte[] raw) {
        String name = internal.replace('/', '.');
        try {
            byte[] out = mixinTransformer().transformClassBytes(name, name, raw);
            if (out != null) {
                transformed++;
                return out;
            }
        } catch (Throwable t) {
            System.err.println("[baker] Accessor transform failed for " + internal + ": " + t);
        }
        return raw;
    }

    private byte[] transform(FabricLauncher launcher, String modId, String internal, byte[] raw) {
        if (modId.equals("fabricloader") || internal.startsWith("org/spongepowered/")
                || internal.startsWith("com/llamalad7/mixinextras/")) {
            return raw;
        }
        try {
            String name = internal.replace('/', '.');
            // Knot's getClassByteArray(name, true) only applies the Fabric transformers (environment
            // stripping, class tweakers / access wideners, game entrypoint patches). Mixin is the final
            // stage of KnotClassDelegate.getPostMixinClassByteArray; replicate it with the live transformer.
            byte[] out = launcher.getClassByteArray(name, true);
            if (out != null) {
                out = mixinTransformer().transformClassBytes(name, name, out);
                transformed++;
                return out;
            }
        } catch (Throwable t) {
            failed++;
            System.err.println("[baker] Transform failed for " + internal + " (" + modId + "): " + t);
        }
        return raw;
    }

    private IMixinTransformer transformer;

    private IMixinTransformer mixinTransformer() {
        if (transformer == null) {
            transformer = (IMixinTransformer) MixinEnvironment.getCurrentEnvironment().getActiveTransformer();
        }
        return transformer;
    }

    /** Mixin may reference classes it generates on demand (e.g. {@code @ModifyArgs} argument bundles). */
    private void generateSyntheticClasses() {
        IMixinTransformer transformer = mixinTransformer();
        Deque<String> queue = new ArrayDeque<>();
        Set<String> seen = new HashSet<>();
        for (byte[] bytes : classes.values()) {
            scanReferences(bytes, queue, seen);
        }
        while (!queue.isEmpty()) {
            String name = queue.poll();
            byte[] generated = transformer.generateClass(MixinEnvironment.getCurrentEnvironment(),
                    name.replace('/', '.'));
            if (generated != null) {
                System.out.println("[baker] Generated synthetic class " + name);
                classes.put(name, generated);
                scanReferences(generated, queue, seen);
            } else {
                System.err.println("[baker] Could not generate synthetic class " + name);
            }
        }
    }

    /**
     * MixinExtras defines classes at runtime while mixins are applied, e.g. the {@code LocalRef}
     * implementations behind {@code @Local LocalRef<T>} parameters (ViaFabric uses them). They exist only
     * in its in-memory table, so they are copied from there after everything has been transformed.
     */
    @SuppressWarnings("unchecked")
    private void collectMixinExtrasClasses() {
        Set<ClassLoader> loaders = new java.util.LinkedHashSet<>();
        loaders.add(Baker.class.getClassLoader());
        loaders.add(MixinEnvironment.class.getClassLoader());
        loaders.add(Thread.currentThread().getContextClassLoader());
        for (ClassLoader loader : loaders) {
            Map<String, byte[]> definitions;
            try {
                Class<?> utils = Class.forName("com.llamalad7.mixinextras.utils.ClassGenUtils", true, loader);
                definitions = (Map<String, byte[]>) utils.getMethod("getDefinitions").invoke(null);
            } catch (ReflectiveOperationException | LinkageError e) {
                continue;
            }
            for (Map.Entry<String, byte[]> e : definitions.entrySet()) {
                String name = e.getKey().replace('.', '/');
                if (classes.putIfAbsent(name, e.getValue()) == null) {
                    System.out.println("[baker] MixinExtras generated class " + name);
                }
            }
        }
    }

    private void scanReferences(byte[] bytes, Deque<String> queue, Set<String> seen) {
        ClassReader reader = new ClassReader(bytes);
        char[] buf = new char[reader.getMaxStringLength()];
        for (int i = 1; i < reader.getItemCount(); i++) {
            int offset = reader.getItem(i);
            if (offset > 0 && reader.b[offset - 1] == 7 /* CONSTANT_Class */) {
                String name = reader.readUTF8(offset, buf);
                // Mixin generates synthetic argument bundles and copies of mixin inner classes
                // (switch maps, anonymous classes) named Target$Anonymous$<hash> on demand.
                if (name != null && (name.startsWith(SYNTHETIC_PREFIX) || name.contains("$Anonymous$"))
                        && !classes.containsKey(name) && seen.add(name)) {
                    queue.add(name);
                }
            }
        }
    }

    private static void put(ZipOutputStream zip, String name, byte[] data) throws IOException {
        ZipEntry entry = new ZipEntry(name);
        entry.setTime(0);
        zip.putNextEntry(entry);
        zip.write(data);
        zip.closeEntry();
    }

    // ------------------------------------------------------------------ JSON

    private static void writeModJson(StringBuilder sb, ModContainerImpl mod) {
        LoaderModMetadata m = mod.getMetadata();
        sb.append("{\"id\":").append(str(m.getId()))
                .append(",\"version\":").append(str(m.getVersion().getFriendlyString()))
                .append(",\"type\":").append(str(m.getType()))
                .append(",\"name\":").append(str(m.getName()))
                .append(",\"description\":").append(str(m.getDescription()))
                .append(",\"environment\":").append(str(m.getEnvironment().name()))
                .append(",\"provides\":").append(strArray(m.getProvides()))
                .append(",\"license\":").append(strArray(m.getLicense()))
                .append(",\"authors\":").append(people(m.getAuthors()))
                .append(",\"contributors\":").append(people(m.getContributors()))
                .append(",\"contact\":").append(stringMap(m.getContact().asMap()))
                .append(",\"icon\":").append(str(m.getIconPath(64).orElse(null)))
                .append(",\"parent\":").append(str(mod.getContainingMod().map(c -> c.getMetadata().getId())
                        .orElse(null)));
        sb.append(",\"depends\":{");
        boolean first = true;
        for (ModDependency dep : m.getDependencies()) {
            if (dep.getKind() != ModDependency.Kind.DEPENDS) {
                continue;
            }
            if (!first) {
                sb.append(',');
            }
            first = false;
            sb.append(str(dep.getModId())).append(':').append(str(String.valueOf(dep.getVersionRequirements())));
        }
        sb.append("},\"entrypoints\":{");
        first = true;
        for (String key : new TreeSet<>(m.getEntrypointKeys())) {
            if (!first) {
                sb.append(',');
            }
            first = false;
            sb.append(str(key)).append(":[");
            boolean f2 = true;
            for (EntrypointMetadata ep : m.getEntrypoints(key)) {
                if (!f2) {
                    sb.append(',');
                }
                f2 = false;
                sb.append("{\"adapter\":").append(str(ep.getAdapter()))
                        .append(",\"value\":").append(str(ep.getValue())).append('}');
            }
            sb.append(']');
        }
        sb.append("},\"custom\":{");
        first = true;
        for (Map.Entry<String, CustomValue> e : m.getCustomValues().entrySet()) {
            if (!first) {
                sb.append(',');
            }
            first = false;
            sb.append(str(e.getKey())).append(':');
            customValue(sb, e.getValue());
        }
        sb.append("}}");
    }

    /** Every string in the mods' custom metadata (some name classes that a mod instantiates by name). */
    private static final java.util.Set<String> CUSTOM_STRINGS = new java.util.TreeSet<>();

    /**
     * Classes named in custom metadata (WTHIT's "waila:plugins" initializers, for instance) are created with
     * Class.forName(name).getConstructor().newInstance(); the compiler keeps them findable by name with their
     * constructors (TeaFabricReflection reads this list).
     */
    private void writeReflectiveClasses() {
        StringBuilder list = new StringBuilder();
        StringBuilder shown = new StringBuilder();
        for (String s : CUSTOM_STRINGS) {
            String internal = s.replace('.', '/');
            if (s.indexOf('.') > 0 && s.indexOf(' ') < 0 && classes.containsKey(internal)) {
                list.append(s).append('\n');
                shown.append(' ').append(s);
            }
        }
        java.util.Set<String> data = dataClasses();
        for (String d : data) {
            list.append("data ").append(d.replace('/', '.')).append('\n');
        }
        resources.put("META-INF/teafabric/reflective-classes.txt", list.toString().getBytes(StandardCharsets.UTF_8));
        System.out.println("[baker] Classes named in mod metadata, findable by name:" + shown);
        System.out.println("[baker] Config data classes with reflective fields and constructors: " + data.size());
    }

    /**
     * Classes that config libraries fill by reflection: AutoConfig's ConfigData implementations and classes
     * passed as a class literal to a fromJson method (Gson, Jankson), with the classes of their fields
     * (generic arguments included) and their superclasses, as far as they belong to mods.
     */
    private java.util.Set<String> dataClasses() {
        java.util.Set<String> roots = new java.util.TreeSet<>();
        byte[] configData = "ConfigData".getBytes(StandardCharsets.ISO_8859_1);
        byte[] fromJson = "fromJson".getBytes(StandardCharsets.ISO_8859_1);
        for (Map.Entry<String, byte[]> e : classes.entrySet()) {
            byte[] b = e.getValue();
            boolean cd = contains(b, configData);
            boolean fj = contains(b, fromJson);
            if (!cd && !fj) {
                continue;
            }
            org.objectweb.asm.tree.ClassNode node = new org.objectweb.asm.tree.ClassNode();
            new org.objectweb.asm.ClassReader(b).accept(node, 0);
            if (cd) {
                for (String i : node.interfaces) {
                    if (i.endsWith("/autoconfig/ConfigData") || i.endsWith("/autoconfig1u/ConfigData")) {
                        roots.add(node.name);
                    }
                }
            }
            if (fj) {
                for (org.objectweb.asm.tree.MethodNode m : node.methods) {
                    for (org.objectweb.asm.tree.AbstractInsnNode insn = m.instructions.getFirst(); insn != null;
                            insn = insn.getNext()) {
                        if (!(insn instanceof org.objectweb.asm.tree.MethodInsnNode)
                                || !((org.objectweb.asm.tree.MethodInsnNode) insn).name.equals("fromJson")
                                || !((org.objectweb.asm.tree.MethodInsnNode) insn).desc.contains("Ljava/lang/Class;)")) {
                            continue;
                        }
                        org.objectweb.asm.tree.AbstractInsnNode p = insn.getPrevious();
                        while (p != null && p.getOpcode() < 0) {
                            p = p.getPrevious();
                        }
                        if (p instanceof org.objectweb.asm.tree.LdcInsnNode
                                && ((org.objectweb.asm.tree.LdcInsnNode) p).cst instanceof org.objectweb.asm.Type) {
                            org.objectweb.asm.Type t = (org.objectweb.asm.Type) ((org.objectweb.asm.tree.LdcInsnNode) p).cst;
                            if (t.getSort() == org.objectweb.asm.Type.OBJECT) {
                                roots.add(t.getInternalName());
                            }
                        }
                    }
                }
            }
        }
        java.util.Set<String> out = new java.util.TreeSet<>();
        java.util.ArrayDeque<String> queue = new java.util.ArrayDeque<>(roots);
        while (!queue.isEmpty()) {
            String c = queue.poll();
            if (!isModClass(c) || !out.add(c)) {
                continue;
            }
            org.objectweb.asm.tree.ClassNode node = new org.objectweb.asm.tree.ClassNode();
            new org.objectweb.asm.ClassReader(classes.get(c)).accept(node, org.objectweb.asm.ClassReader.SKIP_CODE);
            if (node.superName != null) {
                queue.add(node.superName);
            }
            for (org.objectweb.asm.tree.FieldNode f : node.fields) {
                if ((f.access & Opcodes.ACC_STATIC) != 0) {
                    continue;
                }
                String sig = f.signature != null ? f.signature : f.desc;
                java.util.regex.Matcher m = java.util.regex.Pattern.compile("L([\\w/$]+)[;<]").matcher(sig);
                while (m.find()) {
                    queue.add(m.group(1));
                }
            }
        }
        return out;
    }

    private boolean isModClass(String c) {
        return classes.containsKey(c) && !c.startsWith("java/") && !c.startsWith("net/minecraft/")
                && !c.startsWith("com/mojang/") && !c.startsWith("com/google/") && !c.startsWith("it/unimi/");
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

    private static void customValue(StringBuilder sb, CustomValue v) {
        switch (v.getType()) {
            case OBJECT: {
                sb.append('{');
                boolean first = true;
                for (Map.Entry<String, CustomValue> e : v.getAsObject()) {
                    if (!first) {
                        sb.append(',');
                    }
                    first = false;
                    sb.append(str(e.getKey())).append(':');
                    customValue(sb, e.getValue());
                }
                sb.append('}');
                break;
            }
            case ARRAY: {
                sb.append('[');
                boolean first = true;
                for (CustomValue e : v.getAsArray()) {
                    if (!first) {
                        sb.append(',');
                    }
                    first = false;
                    customValue(sb, e);
                }
                sb.append(']');
                break;
            }
            case STRING:
                sb.append(str(v.getAsString()));
                CUSTOM_STRINGS.add(v.getAsString());
                break;
            case NUMBER:
                sb.append(v.getAsNumber());
                break;
            case BOOLEAN:
                sb.append(v.getAsBoolean());
                break;
            default:
                sb.append("null");
        }
    }

    private static String people(Collection<Person> people) {
        List<String> names = new ArrayList<>();
        for (Person p : people) {
            names.add(p.getName());
        }
        return strArray(names);
    }

    private static String stringMap(Map<String, String> map) {
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, String> e : map.entrySet()) {
            if (!first) {
                sb.append(',');
            }
            first = false;
            sb.append(str(e.getKey())).append(':').append(str(e.getValue()));
        }
        return sb.append('}').toString();
    }

    private static String strArray(Collection<String> values) {
        StringBuilder sb = new StringBuilder("[");
        boolean first = true;
        for (String s : values) {
            if (!first) {
                sb.append(',');
            }
            first = false;
            sb.append(str(s));
        }
        return sb.append(']').toString();
    }

    static String str(String s) {
        if (s == null) {
            return "null";
        }
        StringBuilder sb = new StringBuilder("\"");
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"': sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                default:
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
            }
        }
        return sb.append('"').toString();
    }
}
