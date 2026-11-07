package teafabric.compiler;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.teavm.callgraph.CallGraph;
import org.teavm.diagnostics.DefaultProblemTextConsumer;
import org.teavm.diagnostics.Problem;
import org.teavm.diagnostics.ProblemSeverity;
import org.teavm.model.CallLocation;
import org.teavm.tooling.ConsoleTeaVMToolLog;
import org.teavm.tooling.TeaVMProblemRenderer;
import org.teavm.tooling.TeaVMTargetType;
import org.teavm.tooling.TeaVMTool;
import org.teavm.vm.TeaVMOptimizationLevel;

/**
 * Drives TeaVM's ahead-of-time compiler.
 *
 * <p>The program class path is loaded through its own {@link URLClassLoader}, in the order given, so
 * the web platform layer can shadow classes from Minecraft's libraries (first entry wins), and none
 * of the host tool dependencies leak into the compiled program.
 *
 * <p>Besides the TeaVM diagnostics, a compact summary of missing classes/methods/fields is written
 * to {@code <out>/missing.txt}, grouped and counted, which is what drives porting work.
 */
public final class Compile {
    public static void main(String[] args) throws Exception {
        // TeaVM's analyses recurse over the call graph; Minecraft's is deep enough to need a big stack.
        Throwable[] failure = new Throwable[1];
        Thread worker = new Thread(null, () -> {
            try {
                run(args);
            } catch (Throwable t) {
                failure[0] = t;
            }
        }, "teavm", 2L << 30);
        worker.start();
        worker.join();
        if (failure[0] != null) {
            failure[0].printStackTrace();
            System.exit(1);
        }
    }

    private static void run(String[] args) throws Exception {
        Map<String, String> opts = new TreeMap<>();
        for (String arg : args) {
            int eq = arg.indexOf('=');
            opts.put(arg.substring(2, eq), arg.substring(eq + 1));
        }
        String target = opts.getOrDefault("target", "wasm-gc");
        File outDir = new File(opts.get("out"));
        List<URL> urls = new ArrayList<>();
        List<File> files = new ArrayList<>();
        for (String entry : opts.get("classpath").split(File.pathSeparator)) {
            if (entry.isEmpty()) {
                continue;
            }
            File f = new File(entry);
            if (!f.exists()) {
                System.err.println("[teavm] warning: class path entry missing: " + f);
                continue;
            }
            files.add(f);
            urls.add(f.toURI().toURL());
        }

        TeaVMTool tool = new TeaVMTool();
        ConsoleTeaVMToolLog log = new ConsoleTeaVMToolLog(Boolean.parseBoolean(opts.getOrDefault("verbose", "false")));
        tool.setLog(log);
        tool.setTargetType(target.equals("js") ? TeaVMTargetType.JAVASCRIPT : TeaVMTargetType.WEBASSEMBLY_GC);
        tool.setMainClass(opts.get("main"));
        tool.setEntryPointName(opts.getOrDefault("entry", "main"));
        tool.setTargetDirectory(outDir);
        tool.setTargetFileName(opts.getOrDefault("file", target.equals("js") ? "classes.js" : "classes.wasm"));
        tool.setClassLoader(new URLClassLoader(urls.toArray(new URL[0]), Compile.class.getClassLoader()));
        tool.setClassPath(files);
        tool.setOptimizationLevel(TeaVMOptimizationLevel.valueOf(opts.getOrDefault("opt", "ADVANCED")));
        tool.setObfuscated(Boolean.parseBoolean(opts.getOrDefault("obfuscated", "true")));
        tool.setFastDependencyAnalysis(Boolean.parseBoolean(opts.getOrDefault("fast", "false")));
        tool.setDebugInformationGenerated(Boolean.parseBoolean(opts.getOrDefault("debug", "false")));
        tool.setIncremental(false);
        if (opts.containsKey("min-heap")) {
            tool.setMinHeapSize(Integer.parseInt(opts.get("min-heap")) * 1024 * 1024);
        }
        if (opts.containsKey("max-heap")) {
            tool.setMaxHeapSize(Integer.parseInt(opts.get("max-heap")) * 1024 * 1024);
        }
        if (opts.containsKey("preserve")) {
            for (String c : opts.get("preserve").split(",")) {
                tool.getClassesToPreserve().add(c);
            }
        }
        if (opts.containsKey("transformers")) {
            for (String c : opts.get("transformers").split(",")) {
                tool.getTransformers().add(c);
            }
        }
        for (Map.Entry<String, String> e : opts.entrySet()) {
            if (e.getKey().startsWith("prop.")) {
                tool.getProperties().setProperty(e.getKey().substring(5), e.getValue());
            }
        }

        long start = System.currentTimeMillis();
        tool.generate();
        System.out.println("[teavm] " + target + " compilation finished in "
                + (System.currentTimeMillis() - start) / 1000 + " s, " + tool.getClasses().size() + " classes");

        Files.createDirectories(outDir.toPath());
        writeMissingReport(tool, new File(outDir, "missing.txt"));
        writeMissingTsv(tool, new File(outDir, "missing.tsv"));
        int limit = Integer.parseInt(opts.getOrDefault("problem-limit", "400"));
        List<Problem> severe = tool.getProblemProvider().getSevereProblems();
        if (!severe.isEmpty()) {
            CallGraph cg = tool.getDependencyInfo().getCallGraph();
            if (severe.size() <= limit) {
                TeaVMProblemRenderer.describeProblems(cg, tool.getProblemProvider(), log);
            }
            System.err.println("[teavm] " + severe.size() + " severe problems; summary in "
                    + new File(outDir, "missing.txt"));
            System.exit(1);
        }
        for (File f : tool.getGeneratedFiles()) {
            System.out.println("[teavm] wrote " + f + " (" + f.length() / 1024 + " KiB)");
        }
    }

    /** Machine-readable list for the stub generator: kind, owner, name, descriptor. */
    private static void writeMissingTsv(TeaVMTool tool, File file) throws IOException {
        java.util.Set<String> lines = new java.util.TreeSet<>();
        for (Problem p : tool.getProblemProvider().getProblems()) {
            if (p.getSeverity() != ProblemSeverity.ERROR) {
                continue;
            }
            String text = p.getText();
            for (Object param : p.getParams()) {
                if (param instanceof org.teavm.model.MethodReference && text.contains("was not found")) {
                    org.teavm.model.MethodReference m = (org.teavm.model.MethodReference) param;
                    lines.add("method\t" + m.getClassName() + "\t" + m.getName() + "\t" + m.getDescriptor()
                            .toString().substring(m.getName().length()));
                } else if (param instanceof org.teavm.model.FieldReference && text.contains("was not found")) {
                    org.teavm.model.FieldReference f = (org.teavm.model.FieldReference) param;
                    lines.add("field\t" + f.getClassName() + "\t" + f.getFieldName());
                } else if (param instanceof String && (text.contains("was not found")
                        || text.contains("missing in the classpath"))) {
                    lines.add("class\t" + param);
                }
            }
        }
        java.nio.file.Files.write(file.toPath(), lines, StandardCharsets.UTF_8);
    }

    /** Groups problems by their rendered text (e.g. "Method X was not found") and records one caller. */
    private static void writeMissingReport(TeaVMTool tool, File file) throws IOException {
        Map<String, Integer> counts = new TreeMap<>();
        Map<String, String> example = new TreeMap<>();
        for (Problem p : tool.getProblemProvider().getProblems()) {
            DefaultProblemTextConsumer text = new DefaultProblemTextConsumer();
            p.render(text);
            String key = (p.getSeverity() == ProblemSeverity.ERROR ? "E " : "W ") + text.getText();
            counts.merge(key, 1, Integer::sum);
            CallLocation loc = p.getLocation();
            if (loc != null && loc.getMethod() != null) {
                example.putIfAbsent(key, loc.getMethod().toString());
            }
        }
        try (PrintWriter w = new PrintWriter(Files.newBufferedWriter(file.toPath(), StandardCharsets.UTF_8))) {
            for (Map.Entry<String, Integer> e : counts.entrySet()) {
                w.println(e.getKey() + "  [x" + e.getValue() + "]  <- " + example.getOrDefault(e.getKey(), "?"));
            }
        }
    }
}
