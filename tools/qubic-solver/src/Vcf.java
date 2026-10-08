/**
 * Victory by Continuous Forcing: can [p] (to move) win with a sequence of
 * threats, each forcing [o] to block, ending on a double threat? Sound, not
 * complete. Results are cached in a transposition table keyed on the raw
 * (p, o) boards.
 */
final class Vcf {
    static final int UNKNOWN = -2;
    static final int FAIL = -1;

    private final long[] keyP;
    private final long[] keyO;
    /** 0 = empty slot, 1 = fail, 2 + cell = win starting with cell. */
    private final byte[] value;
    private final int mask;

    long nodes;
    private long budget;

    Vcf(int log2Size) {
        int size = 1 << log2Size;
        keyP = new long[size];
        keyO = new long[size];
        value = new byte[size];
        mask = size - 1;
    }

    /** The first move of a VCF win for p, FAIL, or UNKNOWN if [maxNodes] was exhausted. */
    int solve(long p, long o, long maxNodes) {
        budget = maxNodes;
        return search(p, o);
    }

    private int slot(long p, long o) {
        long h = p * 0x9E3779B97F4A7C15L ^ Long.rotateLeft(o * 0xC2B2AE3D27D4EB4FL, 31);
        h ^= h >>> 29;
        return (int) h & mask;
    }

    private int search(long p, long o) {
        nodes++;
        long tp = Qubic.threats(p, o);
        if (tp != 0) return Long.numberOfTrailingZeros(tp);
        long to = Qubic.threats(o, p);
        if (Long.bitCount(to) >= 2) return FAIL;
        int h = slot(p, o);
        if (value[h] != 0 && keyP[h] == p && keyO[h] == o) return value[h] == 1 ? FAIL : value[h] - 2;
        if (--budget < 0) return UNKNOWN;

        int result = FAIL;
        if (to != 0) {
            int c = Long.numberOfTrailingZeros(to);
            long nt = Qubic.newThreats(p, o, c);
            if (nt != 0) {
                if (Long.bitCount(nt) >= 2) {
                    result = c;
                } else {
                    int r = search(p | 1L << c, o | nt);
                    if (r == UNKNOWN) return UNKNOWN;
                    if (r >= 0) result = c;
                }
            }
        } else {
            long cand = Qubic.threatMoves(p, o);
            // Double threats first: they end the search immediately.
            long rest = cand;
            boolean unknown = false;
            while (rest != 0) {
                int c = Long.numberOfTrailingZeros(rest);
                rest &= rest - 1;
                if (Long.bitCount(Qubic.newThreats(p, o, c)) >= 2) {
                    result = c;
                    break;
                }
            }
            if (result == FAIL) {
                rest = cand;
                while (rest != 0) {
                    int c = Long.numberOfTrailingZeros(rest);
                    rest &= rest - 1;
                    long nt = Qubic.newThreats(p, o, c);
                    int r = search(p | 1L << c, o | nt);
                    if (r == UNKNOWN) {
                        unknown = true;
                        continue;
                    }
                    if (r >= 0) {
                        result = c;
                        break;
                    }
                }
            }
            if (result == FAIL && unknown) return UNKNOWN;
        }
        keyP[h] = p;
        keyO[h] = o;
        value[h] = (byte) (result == FAIL ? 1 : result + 2);
        return result;
    }
}
