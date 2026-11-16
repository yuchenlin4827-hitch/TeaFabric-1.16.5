package teafabric.compiler.plugin;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Set;
import org.teavm.classlib.ReflectionContext;
import org.teavm.classlib.ReflectionSupplier;
import org.teavm.model.ClassReader;
import org.teavm.model.MethodDescriptor;
import org.teavm.model.MethodReader;

/**
 * Reflection metadata policy. Fabric mods (Fabric API included) commonly locate Minecraft's
 * private inner classes by intermediary name and instantiate them reflectively, e.g.
 * {@code Class.forName("net.minecraft.class_702$class_4090").getDeclaredConstructor(...)}.
 * TeaVM keeps no metadata unless asked, so: Minecraft / Fabric classes are findable by name, and
 * Minecraft's nested classes keep their constructors.
 *
 * <p>Some mods also ask whether an entity class overrides a method ({@code getDeclaredMethod} along the
 * superclass chain): those methods keep their metadata in every Minecraft class that declares them.
 */
@SuppressWarnings("deprecation")
public class TeaFabricReflection implements ReflectionSupplier {
    /** name + descriptor of Minecraft methods that mods look up with getDeclaredMethod. */
    private static final Set<String> LOOKED_UP = Set.of(
            // Lithium (EntityClassGroup): Entity.isCollidable / collidesWith, to group entities by collision
            "method_30948()Z",
            "method_30949(Lnet/minecraft/class_1297;)Z");

    private static boolean findable(String name) {
        return name.startsWith("net.minecraft.") || name.startsWith("net.fabricmc.")
                || name.startsWith("com.mojang.");
    }

    /** Classes the baker found named in mod metadata (META-INF/teafabric/reflective-classes.txt). */
    private java.util.Set<String> named;
    /** Config data classes ("data " lines): fields and constructors are reflective. */
    private final java.util.Set<String> data = new java.util.HashSet<>();

    private java.util.Set<String> named(ReflectionContext context) {
        if (named == null) {
            named = new java.util.HashSet<>();
            try (java.io.InputStream in = context.getClassLoader()
                    .getResourceAsStream("META-INF/teafabric/reflective-classes.txt")) {
                if (in != null) {
                    for (String line : new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)
                            .split("\n")) {
                        String l = line.trim();
                        if (l.startsWith("data ")) {
                            l = l.substring(5);
                            data.add(l);
                        }
                        if (!l.isEmpty()) {
                            named.add(l);
                        }
                    }
                }
            } catch (java.io.IOException e) {
                // none
            }
        }
        return named;
    }

    @Override
    public boolean isClassFoundByName(ReflectionContext context, String name) {
        // Classes mods load by name are found through the baker's lookup table (LangCompat.forName); making
        // every mod class findable here linked all of them into the program.
        return findable(name) || named(context).contains(name);
    }

    @Override
    public Collection<String> getAccessibleFields(ReflectionContext context, String className) {
        named(context);
        if (!data.contains(className)) {
            return Collections.emptyList();
        }
        ClassReader cls = context.getClassSource().get(className);
        Collection<String> fields = new ArrayList<>();
        if (cls != null) {
            for (org.teavm.model.FieldReader f : cls.getFields()) {
                fields.add(f.getName());
            }
        }
        return fields;
    }

    @Override
    public Collection<MethodDescriptor> getAccessibleMethods(ReflectionContext context, String className) {
        if (className.equals("io.netty.channel.ChannelInitializer")) {
            // Protocol translators (ViaVersion's ViaChannelInitializer) look it up in a static initialiser.
            return Collections.singletonList(MethodDescriptor.parse("initChannel(Lio/netty/channel/Channel;)V"));
        }
        if (named(context).contains(className)) {
            ClassReader cls = context.getClassSource().get(className);
            Collection<MethodDescriptor> ctors = new ArrayList<>();
            if (cls != null) {
                for (MethodReader method : cls.getMethods()) {
                    if (method.getName().equals("<init>")) {
                        ctors.add(method.getDescriptor());
                    }
                }
            }
            return ctors;
        }
        if (!className.startsWith("net.minecraft.")) {
            return Collections.emptyList();
        }
        ClassReader cls = context.getClassSource().get(className);
        if (cls == null) {
            return Collections.emptyList();
        }
        boolean nested = className.indexOf('$') >= 0;
        Collection<MethodDescriptor> result = new ArrayList<>();
        for (MethodReader method : cls.getMethods()) {
            if (nested && method.getName().equals("<init>")
                    || LOOKED_UP.contains(method.getName() + method.getDescriptor().signatureToString())) {
                result.add(method.getDescriptor());
            }
        }
        return result;
    }
}
