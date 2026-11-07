package teafabric.web.compat;

import java.io.InputStream;
import java.lang.reflect.Type;
import java.net.URL;
import java.time.Instant;
import java.util.Collections;
import java.util.Date;
import java.util.Enumeration;
import java.util.NavigableSet;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.Spliterator;
import java.util.Spliterators;
import teafabric.generated.GenericSupers;
import teafabric.web.vfs.WebResources;

/** Redirect targets for java.lang / java.util members missing from TeaVM's class library. */
public final class LangCompat {
    private LangCompat() {
    }

    // ---- System / Runtime

    /** There is no process to exit; stop the game loop and tell the page. */
    public static void exit(int status) {
        throw teafabric.web.WebMain.requestExit(status);
    }

    /** The deprecated String(byte[] ascii, int hibyte, int offset, int count) constructor. */
    @SuppressWarnings("unused")
    public static String constructString(byte[] ascii, int hibyte, int offset, int count) {
        char[] chars = new char[count];
        int hi = (hibyte & 0xff) << 8;
        for (int i = 0; i < count; i++) {
            chars[i] = (char) (hi | (ascii[offset + i] & 0xff));
        }
        return new String(chars);
    }

    // ---- Class

    public static URL getResource(Class<?> cls, String name) {
        return WebResources.getResource(cls, name);
    }

    public static InputStream getResourceAsStream(Class<?> cls, String name) {
        return WebResources.getResourceAsStream(cls, name);
    }

    public static Object[] getSigners(Class<?> cls) {
        return null;
    }

    public static boolean isAnonymousClass(Class<?> cls) {
        String name = cls.getName();
        int dollar = name.lastIndexOf('$');
        if (dollar < 0 || dollar == name.length() - 1) {
            return false;
        }
        for (int i = dollar + 1; i < name.length(); i++) {
            if (!Character.isDigit(name.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    /** Generic supertypes come from the table the baker generates for TypeToken subclasses. */
    public static Type getGenericSuperclass(Class<?> cls) {
        Type t = GenericSupers.get(cls);
        return t != null ? t : cls.getSuperclass();
    }

    public static Type[] getGenericInterfaces(Class<?> cls) {
        return cls.getInterfaces();
    }

    // ---- ClassLoader

    public static URL getResource(ClassLoader loader, String name) {
        return WebResources.getResource(null, "/" + name);
    }

    public static InputStream getResourceAsStream(ClassLoader loader, String name) {
        return WebResources.getResourceAsStream(null, "/" + name);
    }

    public static Enumeration<URL> getResources(ClassLoader loader, String name) {
        URL url = WebResources.getResource(null, "/" + name);
        return url == null ? Collections.emptyEnumeration()
                : Collections.enumeration(Collections.singletonList(url));
    }

    public static URL getSystemResource(String name) {
        return WebResources.getResource(null, "/" + name);
    }

    public static InputStream getSystemResourceAsStream(String name) {
        return WebResources.getResourceAsStream(null, "/" + name);
    }

    // ---- java.util

    public static <T> SortedSet<T> unmodifiableSortedSet(SortedSet<T> s) {
        return new UnmodifiableSortedSet<>(s);
    }

    public static <T> NavigableSet<T> unmodifiableNavigableSet(NavigableSet<T> s) {
        return s;
    }

    public static <K, V> SortedMap<K, V> unmodifiableSortedMap(SortedMap<K, V> m) {
        return m;
    }

    public static Date from(Instant instant) {
        return new Date(instant.toEpochMilli());
    }

    public static <T> Spliterator<T> emptySpliterator() {
        return Spliterators.spliterator(new Object[0], 0);
    }

    public static long parseUnsignedLong(String s, int radix) {
        if (s.length() <= 12) {
            return Long.parseLong(s, radix);
        }
        long first = Long.parseLong(s.substring(0, s.length() - 1), radix);
        int second = Character.digit(s.charAt(s.length() - 1), radix);
        return first * radix + second;
    }
}
