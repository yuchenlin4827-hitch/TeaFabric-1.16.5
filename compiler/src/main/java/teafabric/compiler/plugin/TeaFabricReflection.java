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

    @Override
    public boolean isClassFoundByName(ReflectionContext context, String name) {
        return findable(name);
    }

    @Override
    public Collection<MethodDescriptor> getAccessibleMethods(ReflectionContext context, String className) {
        if (className.equals("io.netty.channel.ChannelInitializer")) {
            // Protocol translators (ViaVersion's ViaChannelInitializer) look it up in a static initialiser.
            return Collections.singletonList(MethodDescriptor.parse("initChannel(Lio/netty/channel/Channel;)V"));
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
