package joptsimple.internal;

import java.io.File;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import joptsimple.ValueConverter;

/**
 * jopt-simple's Reflection helper without run-time reflection: value types Minecraft's command line
 * uses are converted explicitly (TeaVM keeps no metadata for {@code valueOf(String)} lookups).
 */
public final class Reflection {
    private Reflection() {
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public static <V> ValueConverter<V> findConverter(Class<V> clazz) {
        if (clazz == Integer.class || clazz == int.class) {
            return (ValueConverter) converter(Integer.class, Integer::valueOf);
        }
        if (clazz == Long.class || clazz == long.class) {
            return (ValueConverter) converter(Long.class, Long::valueOf);
        }
        if (clazz == Double.class || clazz == double.class) {
            return (ValueConverter) converter(Double.class, Double::valueOf);
        }
        if (clazz == Float.class || clazz == float.class) {
            return (ValueConverter) converter(Float.class, Float::valueOf);
        }
        if (clazz == Boolean.class || clazz == boolean.class) {
            return (ValueConverter) converter(Boolean.class, Boolean::valueOf);
        }
        if (clazz == String.class) {
            return (ValueConverter) converter(String.class, s -> s);
        }
        if (clazz == File.class) {
            return (ValueConverter) converter(File.class, File::new);
        }
        throw new IllegalArgumentException(clazz + " is not a value type");
    }

    private static <V> ValueConverter<V> converter(Class<V> type, java.util.function.Function<String, V> parse) {
        return new ValueConverter<V>() {
            @Override
            public V convert(String value) {
                try {
                    return parse.apply(value);
                } catch (RuntimeException e) {
                    throw new joptsimple.ValueConversionException("Cannot convert '" + value + "' to "
                            + type.getSimpleName(), e);
                }
            }

            @Override
            public Class<? extends V> valueType() {
                return type;
            }

            @Override
            public String valuePattern() {
                return null;
            }
        };
    }

    public static <T> T instantiate(Constructor<T> constructor, Object... args) {
        try {
            return constructor.newInstance(args);
        } catch (Exception e) {
            throw new ReflectionException(e);
        }
    }

    public static Object invoke(Method method, Object... args) {
        try {
            return method.invoke(null, args);
        } catch (Exception e) {
            throw new ReflectionException(e);
        }
    }

    public static <V> V convertWith(ValueConverter<V> converter, String raw) {
        return converter == null ? null : converter.convert(raw);
    }
}
