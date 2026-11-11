package io.netty.util.internal;

/**
 * Replacement for Netty's matcher, which resolves a handler's type parameter with generics reflection
 * ({@code getGenericSuperclass}, {@code getTypeParameters}) that TeaVM does not provide.
 *
 * <p>{@link #find} therefore accepts every message. That is sound for Minecraft's pipeline, where each
 * typed handler only ever sees its own message type (packets above the encoder, buffers below it).
 */
public abstract class TypeParameterMatcher {
    private static final TypeParameterMatcher NOOP = new TypeParameterMatcher() {
        @Override
        public boolean match(Object msg) {
            return true;
        }
    };

    TypeParameterMatcher() {
    }

    public static TypeParameterMatcher get(final Class<?> parameterType) {
        if (parameterType == null || parameterType == Object.class) {
            return NOOP;
        }
        return new TypeParameterMatcher() {
            @Override
            public boolean match(Object msg) {
                return parameterType.isInstance(msg);
            }
        };
    }

    public static TypeParameterMatcher find(Object object, Class<?> parametrizedSuperclass, String typeParamName) {
        return NOOP;
    }

    public abstract boolean match(Object msg);
}
