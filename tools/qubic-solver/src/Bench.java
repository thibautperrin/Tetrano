import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** Generates mid-game benchmark positions and times the solver on them. */
final class Bench {
    /** Line-based heuristic score of [c] for the side owning [p]. */
    static int score(long p, long o, int c) {
        int sc = 0;
        for (int li : Qubic.CELL_LINES[c]) {
            long l = Qubic.LINES[li];
            int mine = Long.bitCount(l & p), theirs = Long.bitCount(l & o);
            if (theirs == 0) sc += new int[]{1, 4, 20, 1000}[mine];
            else if (mine == 0) sc += new int[]{1, 3, 15, 500}[theirs];
        }
        return sc;
    }

    /** A heuristic move with randomness among the [top] best; null-safe for full boards. */
    static int heuristicMove(long p, long o, Random r, int top) {
        long win = Qubic.threats(p, o);
        if (win != 0) return Long.numberOfTrailingZeros(win);
        long block = Qubic.threats(o, p);
        if (block != 0) return Long.numberOfTrailingZeros(block);
        List<int[]> cand = new ArrayList<>();
        for (long m = ~(p | o); m != 0; m &= m - 1) {
            int c = Long.numberOfTrailingZeros(m);
            cand.add(new int[]{c, score(p, o, c) * 16 + r.nextInt(16)});
        }
        cand.sort((x, y) -> y[1] - x[1]);
        return cand.get(r.nextInt(Math.min(top, cand.size())))[0];
    }

    /** Position after [plies] heuristic plies, or null if the game ended or tactics are pending. */
    static long[] position(long seed, int plies, int top) {
        Random r = new Random(seed);
        long a = 0, d = 0;
        for (int i = 0; i < plies; i++) {
            if (i % 2 == 0) {
                int c = heuristicMove(a, d, r, top);
                a |= 1L << c;
            } else {
                int c = heuristicMove(d, a, r, top);
                d |= 1L << c;
            }
            if (Qubic.hasWon(a) || Qubic.hasWon(d)) return null;
        }
        if (Qubic.threats(a, d) != 0 || Qubic.threats(d, a) != 0) return null;
        return new long[]{a, d};
    }

    static void run(String[] args) {
        int plies = Integer.parseInt(Main.opt("plies", "12"));
        int count = Integer.parseInt(Main.opt("count", "20"));
        long maxMid = Long.parseLong(Main.opt("maxMid", "2000000"));
        int top = Integer.parseInt(Main.opt("top", "4"));
        long seed0 = Long.parseLong(Main.opt("seed", "1"));
        int solved = 0, wins = 0;
        long totalMid = 0;
        long t0 = System.currentTimeMillis();
        int done = 0;
        for (long seed = seed0; done < count; seed++) {
            long[] pos = position(seed, plies, top);
            if (pos == null) continue;
            done++;
            Vcf vcf = new Vcf(22);
            Solver2 s = new Solver2(22, vcf, 100_000);
            s.nullPn = Integer.parseInt(Main.opt("nullPn", "10"));
            s.nullDn = Integer.parseInt(Main.opt("nullDn", "0"));
            s.eps = Double.parseDouble(Main.opt("eps", "0.25"));
            s.maxMid = maxMid;
            s.topK = Integer.parseInt(Main.opt("topK", "0"));
            String res;
            long t = System.currentTimeMillis();
            try {
                boolean w = s.solve(pos[0], pos[1]);
                res = w ? "WIN " : "NOWIN";
                solved++;
                if (w) wins++;
                totalMid += s.midCalls;
            } catch (Solver2.Abort e) {
                res = "?    ";
                totalMid += maxMid;
            }
            System.out.printf("seed=%d %s mid=%d evals=%d t=%dms%n", seed, res, s.midCalls, s.evals,
                    System.currentTimeMillis() - t);
        }
        System.out.printf("solved=%d/%d wins=%d totalMid=%d t=%ds%n", solved, count, wins, totalMid,
                (System.currentTimeMillis() - t0) / 1000);
    }
}
