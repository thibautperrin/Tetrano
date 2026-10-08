import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Turns a proof into an opening book, and checks the resulting player
 * (book + runtime rules) against every defender reply.
 *
 * Runtime rules of the attacker, in order (mirrored by the app):
 * 1. complete one of its own lines if it can;
 * 2. block the opponent's 3-in-a-line if there is one;
 * 3. play the book move, if the (canonical) position is in the book;
 * 4. play the first move of a VCF found within [RUNTIME_VCF_BUDGET] nodes,
 *    searched with an empty table;
 * 5. (never expected) give up.
 */
final class Strategy {
    static final long RUNTIME_VCF_BUDGET = 200_000;

    record Key(long a, long d) {}

    final Map<Key, Integer> book = new HashMap<>();
    private final Set<Key> visited = new HashSet<>();
    private final Solver solver;
    private Vcf runtimeVcf = new Vcf(20);
    long leafVcf, leafWin, resolves;

    Strategy(Solver solver) {
        this.solver = solver;
    }

    int runtimeVcfMove(long a, long d) {
        runtimeVcf = new Vcf(20);
        return runtimeVcf.solve(a, d, RUNTIME_VCF_BUDGET);
    }

    /** Extracts the strategy below canonical attacker-to-move position (a, d), known to be won. */
    void extract(long a, long d) {
        Key key = new Key(a, d);
        if (!visited.add(key)) return;
        if (Qubic.threats(a, d) != 0) {
            leafWin++;
            return;
        }
        long td = Qubic.threats(d, a);
        int move;
        if (td != 0) {
            if (Long.bitCount(td) > 1) throw new IllegalStateException("lost position in proof\n" + Qubic.show(a, d));
            move = Long.numberOfTrailingZeros(td);
        } else if (runtimeVcfMove(a, d) >= 0) {
            leafVcf++;
            return;
        } else {
            move = provenMove(a, d);
            book.put(key, move);
        }
        expandDefender(a | 1L << move, d);
    }

    private void expandDefender(long a, long d) {
        long ta = Qubic.threats(a, d);
        long moves = ta != 0 ? ta : ~(a | d);
        if (Long.bitCount(ta) > 1) return; // defender can't block both
        List<long[]> kids = new ArrayList<>();
        Set<Key> seen = new HashSet<>();
        while (moves != 0) {
            int c = Long.numberOfTrailingZeros(moves);
            moves &= moves - 1;
            Qubic.canonical(a, d | 1L << c);
            Key k = new Key(Qubic.canonA, Qubic.canonD);
            if (seen.add(k)) kids.add(new long[]{k.a(), k.d()});
        }
        for (long[] k : kids) extract(k[0], k[1]);
    }

    /** A move from canonical (a, d) to a proven child, re-solving children if the table lost them. */
    private int provenMove(long a, long d) {
        long[] ca = new long[64], cd = new long[64];
        int[] mv = new int[64];
        int n = Solver.children(a, d, ca, cd, mv);
        int best = -1;
        int bestWork = Integer.MAX_VALUE;
        for (int i = 0; i < n; i++) {
            int e = solver.find(ca[i], cd[i]);
            if (e >= 0 && solver.pnAt(e) == 0) {
                // prefer children whose proof stayed in the table (cheapest to re-walk)
                best = i;
                break;
            }
        }
        if (best < 0) {
            // order children by current pn, then solve until one is proven
            Integer[] order = new Integer[n];
            int[] pn = new int[n];
            for (int i = 0; i < n; i++) {
                order[i] = i;
                int e = solver.find(ca[i], cd[i]);
                pn[i] = e >= 0 ? solver.pnAt(e) : 1;
            }
            java.util.Arrays.sort(order, (x, y) -> Integer.compare(pn[x], pn[y]));
            for (int i : order) {
                if (pn[i] >= Solver.INF) continue;
                resolves++;
                if (solver.solve(ca[i], cd[i])) {
                    best = i;
                    break;
                }
            }
        }
        if (best < 0) throw new IllegalStateException("no proven move\n" + Qubic.show(a, d));
        bestWork = 0;
        return mv[best];
    }

    // ---- verification of the runtime player ----

    long verifiedPositions;
    private final Set<Key> verified = new HashSet<>();

    /** The runtime attacker's move in (a, d), real (not canonical) coordinates. */
    int choose(long a, long d) {
        long ta = Qubic.threats(a, d);
        if (ta != 0) return Long.numberOfTrailingZeros(ta);
        long td = Qubic.threats(d, a);
        if (td != 0) return Long.numberOfTrailingZeros(td);
        Qubic.canonical(a, d);
        int s = Qubic.canonSym;
        Integer m = book.get(new Key(Qubic.canonA, Qubic.canonD));
        if (m != null) return Qubic.SYMS[Qubic.INV[s]][m];
        return runtimeVcfMove(a, d);
    }

    /** Throws if the runtime attacker can fail to win from attacker-to-move (a, d). */
    void verify(long a, long d) {
        Qubic.canonical(a, d);
        Key key = new Key(Qubic.canonA, Qubic.canonD);
        if (verified.contains(key)) return;
        int m = choose(a, d);
        if (m < 0) throw new IllegalStateException("no move\n" + Qubic.show(a, d));
        if (((a | d) >>> m & 1) != 0) throw new IllegalStateException("illegal move " + m + "\n" + Qubic.show(a, d));
        long na = a | 1L << m;
        if (!Qubic.hasWon(na)) {
            if (Qubic.threats(d, na) != 0) throw new IllegalStateException("defender wins next\n" + Qubic.show(na, d));
            long ta = Qubic.threats(na, d);
            if (Long.bitCount(ta) < 2) {
                long moves = ta != 0 ? ta : ~(na | d);
                if (moves == 0) throw new IllegalStateException("draw\n" + Qubic.show(na, d));
                while (moves != 0) {
                    int c = Long.numberOfTrailingZeros(moves);
                    moves &= moves - 1;
                    verify(na, d | 1L << c);
                }
            }
        }
        verified.add(key);
        verifiedPositions++;
    }

    // ---- book file ----

    void save(File f) throws IOException {
        List<Map.Entry<Key, Integer>> entries = new ArrayList<>(book.entrySet());
        entries.sort((x, y) -> {
            int c = Long.compareUnsigned(x.getKey().a(), y.getKey().a());
            return c != 0 ? c : Long.compareUnsigned(x.getKey().d(), y.getKey().d());
        });
        try (DataOutputStream out = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(f)))) {
            out.writeInt(entries.size());
            for (Map.Entry<Key, Integer> e : entries) {
                out.writeLong(e.getKey().a());
                out.writeLong(e.getKey().d());
                out.writeByte(e.getValue());
            }
        }
    }

    void load(File f) throws IOException {
        try (DataInputStream in = new DataInputStream(new BufferedInputStream(new FileInputStream(f)))) {
            int n = in.readInt();
            for (int i = 0; i < n; i++) {
                long a = in.readLong(), d = in.readLong();
                book.put(new Key(a, d), (int) in.readByte());
            }
        }
    }
}
