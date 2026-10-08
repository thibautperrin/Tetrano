/**
 * Depth-first proof-number search (df-pn, with the 1+epsilon trick) proving
 * that the attacker (the player who moved first: equal stone counts means
 * the attacker is to move) wins. Positions are stored under their canonical
 * form over the 192 board symmetries. Attacker-to-move leaves are closed by
 * a VCF search.
 */
final class Solver {
    static final int INF = 100_000_000;
    static final double EPS = 0.25;

    // ---- transposition table (4-way buckets) ----
    private final long[] ka, kd;
    private final int[] tpn, tdn, twork;
    private final int bucketMask;

    final Vcf vcf;
    final long vcfBudget;
    final boolean defenderVcf;

    long midCalls, evals, ttStores;
    /** Initial pn of a defender-to-move node where the attacker threatens no VCF (0 = off). */
    int nullMovePn = 0;

    Solver(int log2Entries, Vcf vcf, long vcfBudget, boolean defenderVcf) {
        int size = 1 << log2Entries;
        ka = new long[size];
        kd = new long[size];
        tpn = new int[size];
        tdn = new int[size];
        twork = new int[size];
        bucketMask = (size >> 2) - 1;
        this.vcf = vcf;
        this.vcfBudget = vcfBudget;
        this.defenderVcf = defenderVcf;
    }

    private int bucket(long a, long d) {
        long h = a * 0x9E3779B97F4A7C15L ^ Long.rotateLeft(d * 0xC2B2AE3D27D4EB4FL, 27);
        h ^= h >>> 31;
        return ((int) h & bucketMask) << 2;
    }

    /** Index of the entry for (a, d), or -1. */
    int find(long a, long d) {
        int b = bucket(a, d);
        for (int i = b; i < b + 4; i++) {
            if (ka[i] == a && kd[i] == d && (tpn[i] | tdn[i]) != 0) return i;
        }
        return -1;
    }

    int pnAt(int i) { return tpn[i]; }
    int dnAt(int i) { return tdn[i]; }

    void store(long a, long d, int pn, int dn, int work) {
        ttStores++;
        int b = bucket(a, d);
        int victim = -1;
        int victimWork = Integer.MAX_VALUE;
        for (int i = b; i < b + 4; i++) {
            if ((tpn[i] | tdn[i]) == 0 || (ka[i] == a && kd[i] == d)) {
                victim = i;
                break;
            }
            // solved entries are worth a bit more than unsolved ones with the same work
            int w = twork[i] + ((tpn[i] == 0 || tdn[i] == 0) ? 2 : 0);
            if (w < victimWork) {
                victimWork = w;
                victim = i;
            }
        }
        ka[victim] = a;
        kd[victim] = d;
        tpn[victim] = pn;
        tdn[victim] = dn;
        twork[victim] = work;
    }

    static boolean attackerToMove(long a, long d) {
        return Long.bitCount(a) == Long.bitCount(d);
    }

    /**
     * Static evaluation of a canonical position: stores (pn, dn) in the TT.
     * Returns the entry index.
     */
    int evaluate(long a, long d) {
        evals++;
        int pn = 1, dn = 1;
        if (attackerToMove(a, d)) {
            if (Qubic.threats(a, d) != 0) {
                pn = 0; dn = INF;
            } else {
                long td = Qubic.threats(d, a);
                if (Long.bitCount(td) >= 2 || (a | d) == Qubic.FULL) {
                    pn = INF; dn = 0;
                } else {
                    int r = vcf.solve(a, d, vcfBudget);
                    if (r >= 0) {
                        pn = 0; dn = INF;
                    }
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
                } else if (defenderVcf && ta == 0 && vcf.solve(d, a, vcfBudget) >= 0) {
                    pn = INF; dn = 0;
                } else if (ta == 0) {
                    // Defender has a free choice. If the attacker has no VCF even
                    // when the defender passes, the attacker's last move threatened
                    // nothing: expect a lot to prove.
                    pn = nullMovePn > 0 && vcf.solve(a, d, vcfBudget) < 0 ? nullMovePn : 2;
                }
            }
        }
        store(a, d, pn, dn, 0);
        return find(a, d);
    }

    /** Legal moves worth considering (forced blocks only, when there is a threat to block). */
    static long moves(long a, long d) {
        long empty = ~(a | d);
        if (attackerToMove(a, d)) {
            long td = Qubic.threats(d, a);
            return td != 0 ? td : empty;
        } else {
            long ta = Qubic.threats(a, d);
            return ta != 0 ? ta : empty;
        }
    }

    /** Canonical children of (a, d); returns count, fills ca/cd. */
    static int children(long a, long d, long[] ca, long[] cd, int[] moveOut) {
        boolean att = attackerToMove(a, d);
        long mv = moves(a, d);
        int n = 0;
        while (mv != 0) {
            int c = Long.numberOfTrailingZeros(mv);
            mv &= mv - 1;
            long na = att ? a | 1L << c : a;
            long nd = att ? d : d | 1L << c;
            Qubic.canonical(na, nd);
            long xa = Qubic.canonA, xd = Qubic.canonD;
            boolean dup = false;
            for (int i = 0; i < n; i++) {
                if (ca[i] == xa && cd[i] == xd) {
                    dup = true;
                    break;
                }
            }
            if (!dup) {
                ca[n] = xa;
                cd[n] = xd;
                if (moveOut != null) moveOut[n] = c;
                n++;
            }
        }
        return n;
    }

    private static int cap(long v) { return v >= INF ? INF : (int) v; }

    /** Solves a canonical position. Returns true iff the attacker wins. */
    boolean solve(long a, long d) {
        Qubic.canonical(a, d);
        a = Qubic.canonA;
        d = Qubic.canonD;
        int e = find(a, d);
        if (e < 0) e = evaluate(a, d);
        if (tpn[e] != 0 && tdn[e] != 0) mid(a, d, INF, INF);
        e = find(a, d);
        return tpn[e] == 0;
    }

    void mid(long a, long d, int thpn, int thdn) {
        midCalls++;
        long startCalls = midCalls;
        boolean or = attackerToMove(a, d);
        long[] ca = new long[64];
        long[] cd = new long[64];
        int n = children(a, d, ca, cd, null);
        int[] cpn = new int[n];
        int[] cdn = new int[n];
        int pn, dn;
        while (true) {
            for (int i = 0; i < n; i++) {
                int e = find(ca[i], cd[i]);
                if (e < 0) e = evaluate(ca[i], cd[i]);
                cpn[i] = tpn[e];
                cdn[i] = tdn[e];
            }
            int best = -1;
            int second = INF;
            if (or) {
                long sumDn = 0;
                int minPn = INF;
                for (int i = 0; i < n; i++) {
                    sumDn += cdn[i];
                    if (cpn[i] < minPn) {
                        second = minPn;
                        minPn = cpn[i];
                        best = i;
                    } else if (cpn[i] < second) {
                        second = cpn[i];
                    }
                }
                pn = minPn;
                dn = cap(sumDn);
            } else {
                long sumPn = 0;
                int minDn = INF;
                for (int i = 0; i < n; i++) {
                    sumPn += cpn[i];
                    if (cdn[i] < minDn) {
                        second = minDn;
                        minDn = cdn[i];
                        best = i;
                    } else if (cdn[i] < second) {
                        second = cdn[i];
                    }
                }
                pn = cap(sumPn);
                dn = minDn;
            }
            if (n == 0) { // cannot happen in Qubic before the board is full; treat as draw
                pn = INF;
                dn = 0;
            }
            if (pn >= thpn || dn >= thdn) break;
            int childThPn, childThDn;
            if (or) {
                childThPn = Math.min(thpn, Math.max(second + 1, (int) Math.ceil(second * (1 + EPS))));
                childThDn = cap((long) thdn - dn + cdn[best]);
            } else {
                childThDn = Math.min(thdn, Math.max(second + 1, (int) Math.ceil(second * (1 + EPS))));
                childThPn = cap((long) thpn - pn + cpn[best]);
            }
            mid(ca[best], cd[best], childThPn, childThDn);
        }
        long work = midCalls - startCalls + 1;
        store(a, d, pn, dn, (int) Math.min(work, Integer.MAX_VALUE));
    }
}
