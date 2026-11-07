package teafabric.tools;

import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.objectweb.asm.ClassReader;

/**
 * Generates whole-program tables over the TeaVM class path (game, mods and libraries) that replace
 * run-time reflection: currently {@code teafabric.generated.GenericSupers} for Gson TypeTokens.
 */
public final class GenerateTables {
    public static void main(String[] args) throws Exception {
        Map<String, String> o = Setup.parseArgs(args);
        Map<String, byte[]> tokens = new HashMap<>();
        for (String entry : o.get("classpath").split(File.pathSeparator)) {
            File f = new File(entry);
            if (!f.isFile() || !f.getName().endsWith(".jar")) {
                continue;
            }
            try (JarFile jar = new JarFile(f)) {
                for (JarEntry e : Collections.list(jar.entries())) {
                    if (!e.getName().endsWith(".class") || e.getName().startsWith("META-INF/")) {
                        continue;
                    }
                    try (InputStream in = jar.getInputStream(e)) {
                        byte[] bytes = in.readAllBytes();
                        String sup = new ClassReader(bytes).getSuperName();
                        if ("com/google/gson/reflect/TypeToken".equals(sup)) {
                            tokens.putIfAbsent(new ClassReader(bytes).getClassName(), bytes);
                        }
                    }
                }
            }
        }
        byte[] table = new GenericSupersGenerator(tokens).generate();
        Path out = Path.of(o.get("out"));
        Files.createDirectories(out.toAbsolutePath().getParent());
        try (OutputStream fos = Files.newOutputStream(out); ZipOutputStream zip = new ZipOutputStream(fos)) {
            ZipEntry e = new ZipEntry(GenericSupersGenerator.CLASS_NAME + ".class");
            e.setTime(0);
            zip.putNextEntry(e);
            zip.write(table);
            zip.closeEntry();
        }
    }

    @SuppressWarnings("unused")
    private static List<String> unused() {
        return List.of();
    }
}
