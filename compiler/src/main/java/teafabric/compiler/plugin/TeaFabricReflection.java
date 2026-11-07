package teafabric.compiler.plugin;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
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
 */
@SuppressWarnings("deprecation")
public class TeaFabricReflection implements ReflectionSupplier {
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
        if (!className.startsWith("net.minecraft.") || className.indexOf('$') < 0) {
            return Collections.emptyList();
        }
        ClassReader cls = context.getClassSource().get(className);
        if (cls == null) {
            return Collections.emptyList();
        }
        Collection<MethodDescriptor> result = new ArrayList<>();
        for (MethodReader method : cls.getMethods()) {
            if (method.getName().equals("<init>")) {
                result.add(method.getDescriptor());
            }
        }
        return result;
    }
}
