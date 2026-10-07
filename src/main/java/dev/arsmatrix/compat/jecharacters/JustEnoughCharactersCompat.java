package dev.arsmatrix.compat.jecharacters;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.Locale;

/** Optional bridge that gives Matrix-owned search boxes JECharacters pinyin matching. */
public final class JustEnoughCharactersCompat {
    private static final MethodHandle CONTAINS = findContains();

    private JustEnoughCharactersCompat() {}

    public static boolean contains(CharSequence text, String query) {
        if (query == null || query.isEmpty()) return true;
        String haystack = text == null ? "" : text.toString().toLowerCase(Locale.ROOT);
        String needle = query.toLowerCase(Locale.ROOT);
        if (haystack.contains(needle)) return true;
        if (CONTAINS == null) return false;
        try {
            return (boolean) CONTAINS.invokeExact(haystack, (CharSequence) needle);
        } catch (Throwable ignored) {
            // JECharacters is optional and may change its implementation between versions.
            return false;
        }
    }

    private static MethodHandle findContains() {
        try {
            Class<?> match = Class.forName("me.towdium.jecharacters.utils.Match");
            return MethodHandles.publicLookup().findStatic(
                    match,
                    "contains",
                    MethodType.methodType(boolean.class, String.class, CharSequence.class)
            );
        } catch (ClassNotFoundException | NoSuchMethodException | IllegalAccessException
                 | LinkageError ignored) {
            return null;
        }
    }
}
