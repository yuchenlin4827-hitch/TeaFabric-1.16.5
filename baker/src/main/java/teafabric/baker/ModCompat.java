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
        slightGuiModifications(classes);
        architecturyPlatformMethods(classes);
        notEnoughCrashes(classes);
        lightOverlay(classes);
        mainHooks(classes);
        ArchitecturyEvents.apply(classes);
        amecs(classes);
        shulkerBoxTooltip(classes);
        nightConfig(classes);
    }

    /**
     * Night Config (shaded into LambDynamicLights and LambdaBetterGrass) finds its file formats by loading them
     * with Class.forName, whose static initialiser registers the file extension. The static initialiser of
     * FormatDetector registers each format of the same copy directly instead (by its instance() method).
     */
    private static void nightConfig(Map<String, byte[]> classes) {
        String suffix = "/core/file/FormatDetector";
        String[][] formats = {{"toml/TomlFormat", "toml"}, {"json/JsonFormat", "json"}, {"hocon/HoconFormat", "conf"},
                {"yaml/YamlFormat", "yaml"}};
        for (String detector : new java.util.ArrayList<>(classes.keySet())) {
            if (!detector.endsWith(suffix) || !detector.contains("nightconfig")) {
                continue;
            }
            String root = detector.substring(0, detector.length() - suffix.length());
            String configFormat = "L" + root + "/core/ConfigFormat;";
            patch(classes, detector, node -> {
                MethodNode clinit = method(node, "<clinit>", "()V");
                MethodNode register = method(node, "registerExtension", "(Ljava/lang/String;" + configFormat + ")V");
                if (clinit == null || register == null) {
                    return false;
                }
                InsnList code = new InsnList();
                for (String[] f : formats) {
                    String format = root + "/" + f[0];
                    byte[] bytes = classes.get(format);
                    if (bytes == null) {
                        continue;
                    }
                    ClassNode fn = new ClassNode();
                    new ClassReader(bytes).accept(fn, ClassReader.SKIP_CODE);
                    if (method(fn, "instance", "()L" + format + ";") == null) {
                        continue;
                    }
                    code.add(new org.objectweb.asm.tree.LdcInsnNode(f[1]));
                    code.add(new MethodInsnNode(Opcodes.INVOKESTATIC, format, "instance", "()L" + format + ";", false));
                    code.add(new MethodInsnNode(Opcodes.INVOKESTATIC, detector, "registerExtension",
                            "(Ljava/lang/String;" + configFormat + ")V", false));
                    System.out.println("[baker] Night Config: " + format + " registered for ." + f[1]);
                }
                if (code.size() == 0) {
                    return false;
                }
                for (AbstractInsnNode insn = clinit.instructions.getFirst(); insn != null; insn = insn.getNext()) {
                    if (insn.getOpcode() == Opcodes.RETURN) {
                        InsnList copy = new InsnList();
                        for (AbstractInsnNode c = code.getFirst(); c != null; c = c.getNext()) {
                            copy.add(c.clone(java.util.Collections.emptyMap()));
                        }
                        clinit.instructions.insertBefore(insn, copy);
                    }
                }
                return true;
            });
        }
    }

    /**
     * ShulkerBoxTooltip validates its config after loading by creating the validator classes named in field
     * annotations reflectively, which fails here; the validators only clamp values a player typed, so the
     * validation is skipped.
     */
    private static void shulkerBoxTooltip(Map<String, byte[]> classes) {
        String config = "com/misterpemodder/shulkerboxtooltip/impl/config/Configuration";
        if (!classes.containsKey(config)) {
            return;
        }
        patch(classes, config, node -> {
            MethodNode m = method(node, "validatePostLoad", "()V");
            if (m == null) {
                return false;
            }
            InsnList code = new InsnList();
            code.add(new InsnNode(Opcodes.RETURN));
            replace(m, code);
            return true;
        });
    }

    /**
     * Amecs (bundled in Mouse Wheelie) reaches the key binding map through a static method its mixin adds to
     * KeyBinding, looked up by reflection; the call becomes direct (without it, loading the options failed).
     */
    private static void amecs(Map<String, byte[]> classes) {
        String utils = "de/siphalor/amecs/api/KeyBindingUtils";
        String key = "net/minecraft/class_304";
        if (!classes.containsKey(utils) || !classes.containsKey(key)) {
            return;
        }
        boolean[] found = new boolean[1];
        patch(classes, key, node -> {
            MethodNode m = method(node, "amecs$getIdToKeyBindingMap", "()Ljava/util/Map;");
            if (m == null) {
                return false;
            }
            m.access = (m.access & ~Opcodes.ACC_PRIVATE) | Opcodes.ACC_PUBLIC;
            found[0] = true;
            return true;
        });
        if (!found[0]) {
            return;
        }
        patch(classes, utils, node -> {
            MethodNode m = method(node, "getIdToKeyBindingMap", "()Ljava/util/Map;");
            if (m == null) {
                return false;
            }
            InsnList code = new InsnList();
            code.add(new MethodInsnNode(Opcodes.INVOKESTATIC, key, "amecs$getIdToKeyBindingMap", "()Ljava/util/Map;",
                    false));
            code.add(new InsnNode(Opcodes.ARETURN));
            replace(m, code);
            return true;
        });
    }

    static final String MAIN_HOOKS = "teafabric/generated/MainHooks";

    /**
     * The browser build starts the client from WebLauncher, not from Minecraft's Main.main, so mixins injected
     * at the head of Main.main never ran. Not Enough Crashes installs its platform layer there (without it the
     * game stops with a NullPointerException), Smooth Boot reads its config there. The handler calls at the
     * head of main are collected into the generated {@code MainHooks} ({@code count()}, {@code run(i, args)}),
     * which WebLauncher calls first; the handlers become public so it can. Always generated, empty when no mod
     * injects there.
     */
    private static void mainHooks(Map<String, byte[]> classes) {
        String main = "net/minecraft/client/main/Main";
        java.util.List<MethodInsnNode> calls = new java.util.ArrayList<>();
        if (classes.containsKey(main)) {
            ClassNode node = new ClassNode();
            new ClassReader(classes.get(main)).accept(node, 0);
            MethodNode m = method(node, "main", "([Ljava/lang/String;)V");
            java.util.Set<String> handlers = new java.util.HashSet<>();
            if (m != null) {
                for (AbstractInsnNode insn = m.instructions.getFirst(); insn != null; insn = insn.getNext()) {
                    int op = insn.getOpcode();
                    if (op < 0 || op == Opcodes.ALOAD || op == Opcodes.ACONST_NULL) {
                        continue;
                    }
                    if (op == Opcodes.INVOKESTATIC && ((MethodInsnNode) insn).owner.equals(main)
                            && ((MethodInsnNode) insn).name.startsWith("handler$")) {
                        calls.add((MethodInsnNode) insn);
                        handlers.add(((MethodInsnNode) insn).name + ((MethodInsnNode) insn).desc);
                        continue;
                    }
                    break; // the original code of main starts here
                }
            }
            if (!handlers.isEmpty()) {
                for (MethodNode h : node.methods) {
                    if (handlers.contains(h.name + h.desc)) {
                        h.access = (h.access & ~(Opcodes.ACC_PRIVATE | Opcodes.ACC_PROTECTED)) | Opcodes.ACC_PUBLIC;
                    }
                }
                ClassWriter w = new ClassWriter(ClassWriter.COMPUTE_MAXS);
                node.accept(w);
                classes.put(main, w.toByteArray());
            }
        }
        // count() and run(i, args): WebLauncher calls each injection on its own, so one that fails (it is
        // logged) does not keep the others from running.
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
        cw.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL | Opcodes.ACC_SUPER, MAIN_HOOKS, null,
                "java/lang/Object", null);
        org.objectweb.asm.MethodVisitor count = cw.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "count", "()I",
                null, null);
        count.visitCode();
        count.visitLdcInsn(calls.size());
        count.visitInsn(Opcodes.IRETURN);
        count.visitMaxs(0, 0);
        count.visitEnd();
        org.objectweb.asm.MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "run",
                "(I[Ljava/lang/String;)V", null, null);
        mv.visitCode();
        for (int i = 0; i < calls.size(); i++) {
            MethodInsnNode call = calls.get(i);
            org.objectweb.asm.Label next = new org.objectweb.asm.Label();
            mv.visitVarInsn(Opcodes.ILOAD, 0);
            mv.visitLdcInsn(i);
            mv.visitJumpInsn(Opcodes.IF_ICMPNE, next);
            for (org.objectweb.asm.Type t : org.objectweb.asm.Type.getArgumentTypes(call.desc)) {
                if (t.getDescriptor().equals("[Ljava/lang/String;")) {
                    mv.visitVarInsn(Opcodes.ALOAD, 1);
                } else {
                    mv.visitInsn(Opcodes.ACONST_NULL); // the CallbackInfo, which these injections do not use
                }
            }
            mv.visitMethodInsn(Opcodes.INVOKESTATIC, call.owner, call.name, call.desc, false);
            mv.visitInsn(Opcodes.RETURN);
            mv.visitLabel(next);
        }
        mv.visitInsn(Opcodes.RETURN);
        mv.visitMaxs(0, 0);
        mv.visitEnd();
        org.objectweb.asm.MethodVisitor nm = cw.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "name",
                "(I)Ljava/lang/String;", null, null);
        nm.visitCode();
        for (int i = 0; i < calls.size(); i++) {
            org.objectweb.asm.Label next = new org.objectweb.asm.Label();
            nm.visitVarInsn(Opcodes.ILOAD, 0);
            nm.visitLdcInsn(i);
            nm.visitJumpInsn(Opcodes.IF_ICMPNE, next);
            nm.visitLdcInsn(calls.get(i).name);
            nm.visitInsn(Opcodes.ARETURN);
            nm.visitLabel(next);
        }
        nm.visitLdcInsn("?");
        nm.visitInsn(Opcodes.ARETURN);
        nm.visitMaxs(0, 0);
        nm.visitEnd();
        cw.visitEnd();
        classes.put(MAIN_HOOKS, cw.toByteArray());
        System.out.println("[baker] Generated " + MAIN_HOOKS + " with " + calls.size() + " Main.main injection(s)");
    }

    /**
     * Light Overlay calls its Fabric implementation of the frustum test through a MethodHandle looked up by
     * name at run time; the call becomes a direct one.
     */
    private static void lightOverlay(Map<String, byte[]> classes) {
        String overlay = "me/shedaniel/lightoverlay/common/fabric/LightOverlay";
        String impl = "me/shedaniel/lightoverlay/fabric/LightOverlayImpl";
        if (!classes.containsKey(overlay) || !classes.containsKey(impl)) {
            return;
        }
        String desc = "(Lnet/minecraft/class_4604;DDDDDD)Z";
        patch(classes, overlay, node -> {
            MethodNode m = method(node, "isFrustumVisible", desc);
            if (m == null) {
                return false;
            }
            InsnList code = new InsnList();
            code.add(new VarInsnNode(Opcodes.ALOAD, 0));
            for (int i = 0; i < 6; i++) {
                code.add(new VarInsnNode(Opcodes.DLOAD, 1 + 2 * i));
            }
            code.add(new MethodInsnNode(Opcodes.INVOKESTATIC, impl, "isFrustumVisible", desc, false));
            code.add(new InsnNode(Opcodes.IRETURN));
            replace(m, code);
            return true;
        });
    }

    /**
     * Not Enough Crashes, at start-up, downloads Yarn mappings (to show crash stack traces with readable names),
     * installs a Log4j rewrite policy for the same purpose, and installs a SecurityManager that turns
     * System.exit into an exception. None of these exist in a browser; the crash and recovery screens, the
     * mod's purpose, do not depend on them. Identifying the mods in a stack trace reads each class's code
     * source (its jar), which compiled classes do not have, so the list of suspected mods stays empty.
     */
    private static void notEnoughCrashes(Map<String, byte[]> classes) {
        String deobf = "fudge/notenoughcrashes/fabric/StacktraceDeobfuscator";
        if (classes.containsKey(deobf)) {
            patch(classes, deobf, node -> {
                int done = 0;
                for (MethodNode m : node.methods) {
                    InsnList code = new InsnList();
                    if ((m.name.equals("init") && m.desc.equals("()V"))
                            || (m.name.equals("deobfuscateThrowable") && m.desc.equals("(Ljava/lang/Throwable;)V"))) {
                        code.add(new InsnNode(Opcodes.RETURN));
                    } else if (m.name.equals("deobfuscateStacktrace")
                            && m.desc.equals("([Ljava/lang/StackTraceElement;Z)[Ljava/lang/StackTraceElement;")) {
                        code.add(new VarInsnNode(Opcodes.ALOAD, 0));
                        code.add(new InsnNode(Opcodes.ARETURN));
                    } else {
                        continue;
                    }
                    replace(m, code);
                    done++;
                }
                return done == 3;
            });
        }
        String exitBlock = "fudge/notenoughcrashes/utils/SystemExitBlock";
        if (classes.containsKey(exitBlock)) {
            patch(classes, exitBlock, node -> {
                MethodNode m = method(node, "forbidSystemExitCall", "()V");
                if (m == null) {
                    return false;
                }
                InsnList code = new InsnList();
                code.add(new InsnNode(Opcodes.RETURN));
                replace(m, code);
                return true;
            });
        }
        String identifier = "fudge/notenoughcrashes/stacktrace/ModIdentifier";
        if (classes.containsKey(identifier)) {
            patch(classes, identifier, node -> {
                MethodNode m = method(node, "identifyFromStacktrace", "(Ljava/lang/Throwable;)Ljava/util/Set;");
                if (m == null) {
                    return false;
                }
                InsnList code = new InsnList();
                code.add(new TypeInsnNode(Opcodes.NEW, "java/util/HashSet"));
                code.add(new InsnNode(Opcodes.DUP));
                code.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, "java/util/HashSet", "<init>", "()V", false));
                code.add(new InsnNode(Opcodes.ARETURN));
                replace(m, code);
                return true;
            });
        }
    }

    /**
     * Architectury's @ExpectPlatform methods (used by Architectury API and by mods built with it: Cloth Config,
     * BetterF3, Light Overlay, Not Enough Crashes) are invokedynamic calls whose bootstrap method, PlatformMethods
     * .platform, finds the loader's implementation by name at run time and returns a call site for it. TeaVM
     * cannot run bootstrap methods, so each call becomes the direct call the bootstrap would have produced: a
     * static method of the same name and type in {@code <package>.fabric.<class name without $>Impl}.
     */
    private static void architecturyPlatformMethods(Map<String, byte[]> classes) {
        byte[] marker = "PlatformMethods".getBytes(java.nio.charset.StandardCharsets.ISO_8859_1);
        int sites = 0;
        int patched = 0;
        for (Map.Entry<String, byte[]> e : classes.entrySet()) {
            if (!contains(e.getValue(), marker)) {
                continue;
            }
            String caller = e.getKey();
            ClassNode node = new ClassNode();
            new ClassReader(e.getValue()).accept(node, 0);
            int slash = caller.lastIndexOf('/');
            String impl = caller.substring(0, slash + 1) + "fabric/" + caller.substring(slash + 1).replace("$", "")
                    + "Impl";
            int here = 0;
            for (MethodNode m : node.methods) {
                for (AbstractInsnNode insn = m.instructions.getFirst(); insn != null; insn = insn.getNext()) {
                    if (!(insn instanceof InvokeDynamicInsnNode)) {
                        continue;
                    }
                    InvokeDynamicInsnNode indy = (InvokeDynamicInsnNode) insn;
                    if (!indy.bsm.getName().equals("platform") || !indy.bsm.getOwner().endsWith("/PlatformMethods")
                            || !indy.bsm.getDesc().equals("(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;"
                                    + "Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/CallSite;")) {
                        continue;
                    }
                    if (!classes.containsKey(impl)) {
                        System.out.println("[baker] WARNING: no platform implementation " + impl + " for "
                                + caller + "." + indy.name + indy.desc);
                        continue;
                    }
                    MethodInsnNode call = new MethodInsnNode(Opcodes.INVOKESTATIC, impl, indy.name, indy.desc, false);
                    m.instructions.set(indy, call);
                    insn = call;
                    here++;
                }
            }
            if (here > 0) {
                ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
                node.accept(writer);
                e.setValue(writer.toByteArray());
                sites += here;
                patched++;
            }
        }
        if (sites > 0) {
            System.out.println("[baker] Compat patch: " + sites + " Architectury platform call(s) in " + patched
                    + " class(es) made direct");
        }
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

    /**
     * Slight GUI Modifications runs config/slightguimodifications/cts.groovy (custom title screen elements, off by
     * default) with an embedded Groovy, which generates classes at run time and cannot run here. Loading the
     * script becomes a no-op and the Groovy shell is never created, so none of Groovy is compiled in (about
     * 3,300 classes); the mod's other features are unaffected.
     */
    private static void slightGuiModifications(Map<String, byte[]> classes) {
        String registry = "me/shedaniel/slightguimodifications/gui/cts/CtsRegistry";
        if (!classes.containsKey(registry)) {
            return;
        }
        patch(classes, registry, node -> {
            int done = 0;
            for (MethodNode m : node.methods) {
                InsnList code = new InsnList();
                if ((m.name.equals("loadScripts") || m.name.equals("loadScriptsAsync")) && m.desc.equals("()V")) {
                    code.add(new InsnNode(Opcodes.RETURN));
                } else if (m.name.startsWith("lambda$") && m.desc.equals("()Lgroovy/lang/GroovyShell;")) {
                    code.add(new InsnNode(Opcodes.ACONST_NULL));
                    code.add(new InsnNode(Opcodes.ARETURN));
                } else {
                    continue;
                }
                replace(m, code);
                done++;
            }
            return done >= 2;
        });
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
     * until the whole queue was built (minutes after joining a world). One worker is enough, and after every
     * job it calls SodiumCompat.chunkWorkerYield, which gives the page a turn once the worker has run for a
     * few milliseconds.
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
            run.instructions.insertBefore(first, new MethodInsnNode(Opcodes.INVOKESTATIC,
                    "teafabric/web/compat/SodiumCompat", "chunkWorkerYield", "()V", false));
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
