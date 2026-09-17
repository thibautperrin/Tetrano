package android.support.v4.util;

import java.util.HashMap;

/**
 * Minimal stand-in for {@code android.support.v4.util.LongSparseArray},
 * just enough for the legacy {@code IA} class (see docs/PLAN.md P3.2) to
 * compile and run outside Android. Backed by a plain {@link HashMap}: it
 * is not meant to be fast, only correct — this is test-only code.
 */
public class LongSparseArray<E> {
    private final HashMap<Long, E> map = new HashMap<>();

    public E get(long key) {
        return map.get(key);
    }

    public void put(long key, E value) {
        map.put(key, value);
    }

    public void clear() {
        map.clear();
    }
}
