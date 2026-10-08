import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Tetrano board geometry as bitboards: cell = x + 4*y + 16*z (same numbering
 * as the app's rings), 76 winning lines, and the 192 automorphisms of the
 * 4x4x4 board (the 48 cube symmetries times 4 "scrambling" maps that apply
 * the same permutation of {0,1,2,3} to every coordinate).
 */
final class Qubic {
    static final int CELLS = 64;
    static final long FULL = -1L;
    static final long[] LINES = new long[76];
    /** CELL_LINES[c] = indices of the 4 or 7 lines through cell c. */
    static final int[][] CELL_LINES = new int[CELLS][];
    /** SYMS[s][c] = image of cell c under symmetry s (s = 0 is the identity). */
    static final int[][] SYMS;
    static final int SYM_COUNT;
    /** INV[s] = index of the inverse of symmetry s. */
    static final int[] INV;
    /** Byte-wise lookup: TBL[s][byteIndex*256 + byteValue] = image bits. */
    private static final long[][] TBL;

    static int cell(int x, int y, int z) { return x + 4 * y + 16 * z; }

    static {
        int n = 0;
        int[][] dirs = new int[13][];
        int k = 0;
        for (int dx = -1; dx <= 1; dx++)
            for (int dy = -1; dy <= 1; dy++)
                for (int dz = -1; dz <= 1; dz++) {
                    if (dx == 0 && dy == 0 && dz == 0) continue;
                    // keep one of each opposite pair: first non-zero component positive
                    int first = dx != 0 ? dx : (dy != 0 ? dy : dz);
                    if (first < 0) continue;
                    dirs[k++] = new int[]{dx, dy, dz};
                }
        for (int[] d : dirs) {
            for (int x = 0; x < 4; x++)
                for (int y = 0; y < 4; y++)
                    for (int z = 0; z < 4; z++) {
                        if (!inRange(x + 3 * d[0], y + 3 * d[1], z + 3 * d[2])) continue;
                        if (inRange(x - d[0], y - d[1], z - d[2])) continue;
                        long m = 0;
                        for (int i = 0; i < 4; i++) m |= 1L << cell(x + i * d[0], y + i * d[1], z + i * d[2]);
                        LINES[n++] = m;
                    }
        }
        if (n != 76) throw new IllegalStateException("lines " + n);
        for (int c = 0; c < CELLS; c++) {
            List<Integer> l = new ArrayList<>();
            for (int i = 0; i < 76; i++) if ((LINES[i] >>> c & 1) != 0) l.add(i);
            CELL_LINES[c] = l.stream().mapToInt(Integer::intValue).toArray();
        }

        // symmetry group closure from generators
        List<int[]> gens = new ArrayList<>();
        gens.add(coordMap((x, y, z) -> new int[]{y, x, z}));
        gens.add(coordMap((x, y, z) -> new int[]{y, z, x}));
        gens.add(coordMap((x, y, z) -> new int[]{3 - x, y, z}));
        int[] inside = {0, 2, 1, 3};
        gens.add(coordMap((x, y, z) -> new int[]{inside[x], inside[y], inside[z]}));
        int[] pairs = {1, 0, 3, 2};
        gens.add(coordMap((x, y, z) -> new int[]{pairs[x], pairs[y], pairs[z]}));
        int[] id = new int[CELLS];
        for (int c = 0; c < CELLS; c++) id[c] = c;
        Map<String, int[]> seen = new HashMap<>();
        List<int[]> group = new ArrayList<>();
        ArrayDeque<int[]> q = new ArrayDeque<>();
        seen.put(Arrays.toString(id), id);
        group.add(id);
        q.add(id);
        while (!q.isEmpty()) {
            int[] p = q.poll();
            for (int[] g : gens) {
                int[] r = new int[CELLS];
                for (int c = 0; c < CELLS; c++) r[c] = g[p[c]];
                String key = Arrays.toString(r);
                if (seen.putIfAbsent(key, r) == null) {
                    group.add(r);
                    q.add(r);
                }
            }
        }
        SYM_COUNT = group.size();
        SYMS = group.toArray(new int[0][]);
        Set<Long> lineSet = new HashSet<>();
        for (long l : LINES) lineSet.add(l);
        for (int[] s : SYMS)
            for (long l : LINES)
                if (!lineSet.contains(apply(s, l))) throw new IllegalStateException("not an automorphism");
        INV = new int[SYM_COUNT];
        for (int s = 0; s < SYM_COUNT; s++) {
            int[] inv = new int[CELLS];
            for (int c = 0; c < CELLS; c++) inv[SYMS[s][c]] = c;
            INV[s] = group.indexOf(seen.get(Arrays.toString(inv)));
        }
        TBL = new long[SYM_COUNT][8 * 256];
        for (int s = 0; s < SYM_COUNT; s++)
            for (int b = 0; b < 8; b++)
                for (int v = 0; v < 256; v++) {
                    long r = 0;
                    for (int i = 0; i < 8; i++) if ((v >>> i & 1) != 0) r |= 1L << SYMS[s][8 * b + i];
                    TBL[s][b * 256 + v] = r;
                }
    }

    private interface CoordFn { int[] map(int x, int y, int z); }

    private static int[] coordMap(CoordFn f) {
        int[] p = new int[CELLS];
        for (int c = 0; c < CELLS; c++) {
            int[] r = f.map(c & 3, (c >> 2) & 3, c >> 4);
            p[c] = cell(r[0], r[1], r[2]);
        }
        return p;
    }

    private static boolean inRange(int x, int y, int z) {
        return x >= 0 && x < 4 && y >= 0 && y < 4 && z >= 0 && z < 4;
    }

    static long apply(int[] perm, long bits) {
        long r = 0;
        while (bits != 0) {
            int c = Long.numberOfTrailingZeros(bits);
            bits &= bits - 1;
            r |= 1L << perm[c];
        }
        return r;
    }

    static long transform(int s, long b) {
        long[] t = TBL[s];
        return t[(int) (b & 255)] | t[256 + (int) (b >>> 8 & 255)] | t[512 + (int) (b >>> 16 & 255)]
                | t[768 + (int) (b >>> 24 & 255)] | t[1024 + (int) (b >>> 32 & 255)]
                | t[1280 + (int) (b >>> 40 & 255)] | t[1536 + (int) (b >>> 48 & 255)]
                | t[1792 + (int) (b >>> 56 & 255)];
    }

    /** Result of [canonical]: canonical boards and the symmetry that maps the input onto them. */
    static long canonA, canonD;
    static int canonSym;

    /**
     * Lowest (a, d) pair, comparing as unsigned longs, over all symmetries.
     * Results in the static fields (single-threaded use only).
     */
    static void canonical(long a, long d) {
        long ba = a, bd = d;
        int bs = 0;
        for (int s = 1; s < SYM_COUNT; s++) {
            long ta = transform(s, a);
            int cmp = Long.compareUnsigned(ta, ba);
            if (cmp > 0) continue;
            long td = transform(s, d);
            if (cmp < 0 || Long.compareUnsigned(td, bd) < 0) {
                ba = ta;
                bd = td;
                bs = s;
            }
        }
        canonA = ba;
        canonD = bd;
        canonSym = bs;
    }

    /** Cells completing a line of [p] that [o] hasn't touched (p's immediate wins). */
    static long threats(long p, long o) {
        long t = 0;
        for (long l : LINES) {
            if ((l & o) == 0 && Long.bitCount(l & p) == 3) t |= l & ~p;
        }
        return t;
    }

    /** Threat cells created by [p] newly playing cell [c] (only lines through c). */
    static long newThreats(long p, long o, int c) {
        long t = 0;
        long pc = p | (1L << c);
        for (int li : CELL_LINES[c]) {
            long l = LINES[li];
            if ((l & o) == 0 && Long.bitCount(l & pc) == 3) t |= l & ~pc;
        }
        return t;
    }

    /** Empty cells on a line where [p] has 2 and [o] has none: moves creating a threat. */
    static long threatMoves(long p, long o) {
        long t = 0;
        for (long l : LINES) {
            if ((l & o) == 0 && Long.bitCount(l & p) == 2) t |= l & ~p;
        }
        return t;
    }

    static boolean hasWon(long p) {
        for (long l : LINES) if ((l & p) == l) return true;
        return false;
    }

    static String show(long a, long d) {
        StringBuilder sb = new StringBuilder();
        for (int y = 3; y >= 0; y--) {
            for (int z = 0; z < 4; z++) {
                for (int x = 0; x < 4; x++) {
                    int c = cell(x, y, z);
                    sb.append((a >>> c & 1) != 0 ? 'X' : (d >>> c & 1) != 0 ? 'O' : '.');
                }
                sb.append("  ");
            }
            sb.append('\n');
        }
        return sb.toString();
    }
}
