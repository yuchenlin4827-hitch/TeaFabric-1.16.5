package teafabric.tools;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import net.fabricmc.tinyremapper.IMappingProvider;
import net.fabricmc.tinyremapper.NonClassCopyMode;
import net.fabricmc.tinyremapper.OutputConsumerPath;
import net.fabricmc.tinyremapper.TinyRemapper;
import net.fabricmc.tinyremapper.TinyUtils;

/**
 * Remaps a jar with Tiny mappings stored inside a mappings jar ({@code mappings/mappings.tiny}).
 *
 * <ul>
 *   <li>{@code official -> intermediary}: produces the compile classpath for {@code :webmod} mixins
 *       (Fabric mods are always compiled against / shipped in intermediary names);</li>
 *   <li>{@code intermediary -> named} with Yarn: an optional human-readable copy for local reference.</li>
 * </ul>
 */
public final class Remap {
    public static void main(String[] args) throws Exception {
        Map<String, String> o = Setup.parseArgs(args);
        Path input = Path.of(o.get("input"));
        Path output = Path.of(o.get("output"));
        Path mappingsJar = Path.of(o.get("mappings"));
        String from = o.get("from");
        String to = o.get("to");
        List<Path> classpath = new ArrayList<>();
        if (o.containsKey("classpath")) {
            for (String s : o.get("classpath").split(java.io.File.pathSeparator)) {
                if (!s.isEmpty()) {
                    classpath.add(Path.of(s));
                }
            }
        }

        // The mapping provider reads lazily, so extract the tiny file instead of streaming from the zip.
        Path tiny = output.toAbsolutePath().resolveSibling(mappingsJar.getFileName() + ".tiny");
        Files.createDirectories(tiny.getParent());
        try (ZipFile zip = new ZipFile(mappingsJar.toFile())) {
            ZipEntry entry = zip.getEntry("mappings/mappings.tiny");
            Files.copy(zip.getInputStream(entry), tiny, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }
        IMappingProvider mappings = TinyUtils.createTinyMappingProvider(tiny, from, to);
        TinyRemapper remapper = TinyRemapper.newRemapper()
                .withMappings(mappings)
                .renameInvalidLocals(true)
                .rebuildSourceFilenames(true)
                .ignoreConflicts(true)
                .build();
        Files.createDirectories(output.toAbsolutePath().getParent());
        Files.deleteIfExists(output);
        try (OutputConsumerPath consumer = new OutputConsumerPath.Builder(output).build()) {
            consumer.addNonClassFiles(input, NonClassCopyMode.FIX_META_INF, remapper);
            remapper.readInputs(input);
            remapper.readClassPath(classpath.toArray(new Path[0]));
            remapper.apply(consumer);
        } finally {
            remapper.finish();
        }
        Setup.log("Remapped " + input.getFileName() + " (" + from + " -> " + to + ") -> " + output);
    }
}
