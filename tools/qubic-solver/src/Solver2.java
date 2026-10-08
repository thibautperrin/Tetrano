import java.util.Random;

/**
 * df-pn like [Solver], but positions are identified by a 64-bit hash that is
 * invariant under the 192 symmetries (min over symmetries of a Zobrist hash),
 * maintained incrementally: about 10x cheaper than exact canonical forms.
 * A hash collision could only make a proof wrong, which the exhaustive
 * verification of the extracted strategy would catch.
 */
final class Solver2 {
    static final int INF = 100_000_000;
    static final int S = Qubic.SYM_COUNT;
    /** ZS[side][c * S + s] = Zobrist of side's stone on the image of c under symmetry s. */
    static final long[][] ZS = new long[2][64 * S];

    static {
        Random r = new Random(20261008L);
        long[][] z = new long[2][64];
        for (int side = 0; side < 2; side++) for (int c = 0; c < 64; c++) z[side][c] = r.nextLong();
        for (int side = 0; side < 2; side++)
            for (int c = 0; c < 64; c++)
                for (int s = 0; s < S; s++) ZS[side][c * S + s] = z[side][Qubic.SYMS[s][c]];
    }

    private final long[] key;
    private final int[] tpn, tdn, twork;
    private final int bucketMask;

    final Vcf vcf;
    final long vcfBudget;
    double eps = 0.25;
    int nullPn = 10;
    int nullDn = 0;
    boolean defenderVcf = true;
    /** If > 0, the attacker only considers its [topK] best moves by heuristic score (sound for proofs). */
    int topK = 0;

    long midCalls, evals;
    /** Abort (with [Abort]) once midCalls exceeds this. */
    long maxMid = Long.MAX_VALUE;

    static final class Abort extends RuntimeException {
        Abort() { super(null, null, false, false); }
    }

    Solver2(int log2Entries, Vcf vcf, long vcfBudget) {
        int size = 1 << log2Entries;
        key = new long[size];
        tpn = new int[size];
        tdn = new int[size];
        twork = new int[size];
        bucketMask = (size >> 2) - 1;
        this.vcf = vcf;
        this.vcfBudget = vcfBudget;
    }

    static long[] hashes(long a, long d) {
        long[] h = new long[S];
        for (long b = a; b != 0; b &= b - 1) xorInto(h, 0, Long.numberOfTrailingZeros(b));
        for (long b = d; b != 0; b &= b - 1) xorInto(h, 1, Long.numberOfTrailingZeros(b));
        return h;
    }

    static void xorInto(long[] h, int side, int c) {
        long[] z = ZS[side];
        int base = c * S;
        for (int s = 0; s < S; s++) h[s] ^= z[base + s];
    }

    static long minHash(long[] h) {
        long m = Long.MAX_VALUE;
        for (long v : h) if (v < m) m = v;
        return m;
    }

    static long childKey(long[] h, int side, int c) {
        long[] z = ZS[side];
        int base = c * S;
        long m = Long.MAX_VALUE;
        for (int s = 0; s < S; s++) {
            long v = h[s] ^ z[base + s];
            if (v < m) m = v;
        }
        return m;
    }

    private int bucket(long k) {
        long h = k * 0x9E3779B97F4A7C15L;
        return ((int) (h >>> 32) & bucketMask) << 2;
    }

    int find(long k) {
        int b = bucket(k);
        for (int i = b; i < b + 4; i++) if (key[i] == k && (tpn[i] | tdn[i]) != 0) return i;
        return -1;
    }

    int pnAt(int i) { return tpn[i]; }
    int dnAt(int i) { return tdn[i]; }

    void store(long k, int pn, int dn, int work) {
        int b = bucket(k);
        int victim = -1;
        int victimWork = Integer.MAX_VALUE;
        for (int i = b; i < b + 4; i++) {
            if ((tpn[i] | tdn[i]) == 0 || key[i] == k) {
                victim = i;
                break;
            }
            int w = twork[i] + ((tpn[i] == 0 || tdn[i] == 0) ? 2 : 0);
            if (w < victimWork) {
                victimWork = w;
                victim = i;
            }
        }
        key[victim] = k;
        tpn[victim] = pn;
        tdn[victim] = dn;
        twork[victim] = work;
    }

    static boolean attackerToMove(long a, long d) {
        return Long.bitCount(a) == Long.bitCount(d);
    }

    /** Static evaluation; stores and returns the entry index. */
    int evaluate(long a, long d, long k) {
        evals++;
        int pn = 1, dn = 1;
        if (attackerToMove(a, d)) {
            if (Qubic.threats(a, d) != 0) {
                pn = 0; dn = INF;
            } else {
                long td = Qubic.threats(d, a);
                if (Long.bitCount(td) >= 2 || (a | d) == Qubic.FULL) {
                    pn = INF; dn = 0;
                } else if (vcf.solve(a, d, vcfBudget) >= 0) {
                    pn = 0; dn = INF;
                } else if (nullDn > 0 && td == 0 && vcf.solve(d, a, vcfBudget) < 0) {
                    dn = nullDn; // the defender threatens nothing: hard to disprove
                }
            }
        } else {
            if (Qubic.threats(d, a) != 0) {
                pn = INF; dn = 0;
            } else {
                long ta = Qubic.threats(a, d);
                if (Long.bitCount(ta) >= 2) {
                    pn = 0; dn = INF;
                } else if ((a | d) == Qubic.FULL) {
                    pn = INF; dn = 0;
                } else if (ta == 0) {
                    if (defenderVcf && vcf.solve(d, a, vcfBudget) >= 0) {
                        pn = INF; dn = 0;
                    } else {
                        pn = nullPn > 0 && vcf.solve(a, d, vcfBudget) < 0 ? nullPn : 2;
                    }
                }
            }
        }
        store(k, pn, dn, 0);
        return find(k);
    }

    static long moves(long a, long d) {
        long empty = ~(a | d);
        if (attackerToMove(a, d)) {
            long td = Qubic.threats(d, a);
            return td != 0 ? td : empty;
        }
        long ta = Qubic.threats(a, d);
        return ta != 0 ? ta : empty;
    }

    /** Reorders mv/ck so the [k] best attacker moves come first; returns k. */
    static int keepTop(long a, long d, int[] mv, long[] ck, int n, int k) {
        int[] sc = new int[n];
        for (int i = 0; i < n; i++) sc[i] = Bench.score(a, d, mv[i]);
        for (int i = 0; i < k; i++) {
            int b = i;
            for (int j = i + 1; j < n; j++) if (sc[j] > sc[b]) b = j;
            int t = sc[i]; sc[i] = sc[b]; sc[b] = t;
            t = mv[i]; mv[i] = mv[b]; mv[b] = t;
            long u = ck[i]; ck[i] = ck[b]; ck[b] = u;
        }
        return k;
    }

    private static int cap(long v) { return v >= INF ? INF : (int) v; }

    /** Solves (a, d). Returns true iff the attacker wins. */
    boolean solve(long a, long d) {
        long[] h = hashes(a, d);
        long k = minHash(h);
        int e = find(k);
        if (e < 0) e = evaluate(a, d, k);
        if (tpn[e] != 0 && tdn[e] != 0) mid(a, d, h, k, INF, INF);
        e = find(k);
        return e >= 0 && tpn[e] == 0;
    }

    /** pn of (a, d) in the table, or -1 if absent. */
    int pnOf(long a, long d) {
        int e = find(minHash(hashes(a, d)));
        return e < 0 ? -1 : tpn[e];
    }

    int dnOf(long a, long d) {
        int e = find(minHash(hashes(a, d)));
        return e < 0 ? -1 : tdn[e];
    }

    void mid(long a, long d, long[] h, long k, int thpn, int thdn) {
        if (++midCalls > maxMid) throw new Abort();
        long startCalls = midCalls;
        boolean or = attackerToMove(a, d);
        int side = or ? 0 : 1;
        int[] mv = new int[64];
        long[] ck = new long[64];
        int n = 0;
        for (long m = moves(a, d); m != 0; m &= m - 1) {
            int c = Long.numberOfTrailingZeros(m);
            long kk = childKey(h, side, c);
            boolean dup = false;
            for (int i = 0; i < n; i++) if (ck[i] == kk) { dup = true; break; }
            if (!dup) {
                ck[n] = kk;
                mv[n] = c;
                n++;
            }
        }
        if (or && topK > 0 && n > topK) n = keepTop(a, d, mv, ck, n, topK);
        int[] cpn = new int[n];
        int[] cdn = new int[n];
        int pn, dn;
        while (true) {
            for (int i = 0; i < n; i++) {
                int e = find(ck[i]);
                if (e < 0) {
                    long bit = 1L << mv[i];
                    e = evaluate(or ? a | bit : a, or ? d : d | bit, ck[i]);
                }
                cpn[i] = tpn[e];
                cdn[i] = tdn[e];
            }
            int best = -1;
            int second = INF;
            if (n == 0) {
                pn = INF;
                dn = 0;
            } else if (or) {
                long sumDn = 0;
                int minPn = INF + 1;
                for (int i = 0; i < n; i++) {
                    sumDn += cdn[i];
                    if (cpn[i] < minPn) {
                        second = Math.min(second, minPn);
                        minPn = cpn[i];
                        best = i;
                    } else if (cpn[i] < second) {
                        second = cpn[i];
                    }
                }
                pn = Math.min(minPn, INF);
                dn = cap(sumDn);
            } else {
                long sumPn = 0;
                int minDn = INF + 1;
                for (int i = 0; i < n; i++) {
                    sumPn += cpn[i];
                    if (cdn[i] < minDn) {
                        second = Math.min(second, minDn);
                        minDn = cdn[i];
                        best = i;
                    } else if (cdn[i] < second) {
                        second = cdn[i];
                    }
                }
                pn = cap(sumPn);
                dn = Math.min(minDn, INF);
            }
            if (pn >= thpn || dn >= thdn) break;
            int childThPn, childThDn;
            int secondTh = second >= INF ? INF : Math.max(second + 1, (int) Math.ceil(second * (1 + eps)));
            if (or) {
                childThPn = Math.min(thpn, secondTh);
                childThDn = cap((long) thdn - dn + cdn[best]);
            } else {
                childThDn = Math.min(thdn, secondTh);
                childThPn = cap((long) thpn - pn + cpn[best]);
            }
            long[] hc = h.clone();
            xorInto(hc, side, mv[best]);
            long bit = 1L << mv[best];
            mid(or ? a | bit : a, or ? d : d | bit, hc, ck[best], childThPn, childThDn);
        }
        long work = midCalls - startCalls + 1;
        store(k, pn, dn, (int) Math.min(work, Integer.MAX_VALUE));
    }
}
