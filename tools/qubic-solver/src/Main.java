import java.util.TreeMap;

/** Command-line entry point for the experiments. */
public final class Main {
    public static void main(String[] args) throws Exception {
        String cmd = args.length > 0 ? args[0] : "info";
        switch (cmd) {
            case "info" -> info();
            case "solve" -> solve(args);
            case "bench" -> Bench.run(args);
            case "children" -> {
                long a = 0, d = 0;
                for (int i = 1; i < args.length; i++) {
                    int c = Integer.parseInt(args[i]);
                    if ((i - 1) % 2 == 0) a |= 1L << c; else d |= 1L << c;
                }
                long[] ca = new long[64], cd = new long[64];
                int[] mv = new int[64];
                int n = childrenCopy(a, d, ca, cd, mv);
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < n; i++) sb.append(mv[i]).append(' ');
                System.out.println(sb.toString().trim());
            }
            default -> System.out.println("unknown command " + cmd);
        }
    }

    static void info() {
        System.out.println("lines=" + Qubic.LINES.length + " syms=" + Qubic.SYM_COUNT);
        // cell orbits
        TreeMap<Integer, Integer> orbitOf = new TreeMap<>();
        for (int c = 0; c < 64; c++) {
            int min = 64;
            for (int s = 0; s < Qubic.SYM_COUNT; s++) min = Math.min(min, Qubic.SYMS[s][c]);
            orbitOf.merge(min, 1, Integer::sum);
        }
        System.out.println("cell orbits (representative=size): " + orbitOf);
    }

    static String opt(String name, String def) {
        String v = System.getProperty(name);
        return v != null ? v : def;
    }

    /**
     * solve [moves...]: moves alternate starting with the attacker.
     * Options (-Dname=value): vcfBudget, nullPn, defVcf, tt (log2 entries).
     */
    static void solve(String[] args) throws Exception {
        long a = 0, d = 0;
        for (int i = 1; i < args.length; i++) {
            int c = Integer.parseInt(args[i]);
            if ((i - 1) % 2 == 0) a |= 1L << c; else d |= 1L << c;
        }
        final long ra = a, rd = d;
        System.out.print(Qubic.show(a, d));
        Vcf vcf = new Vcf(24);
        Solver s = new Solver(Integer.parseInt(opt("tt", "25")), vcf, Long.parseLong(opt("vcfBudget", "100000")),
                Boolean.parseBoolean(opt("defVcf", "true")));
        s.nullMovePn = Integer.parseInt(opt("nullPn", "0"));
        long t0 = System.currentTimeMillis();
        Thread mon = new Thread(() -> {
            try {
                while (true) {
                    Thread.sleep(Long.parseLong(opt("every", "30")) * 1000);
                    System.out.printf("  t=%ds mid=%d evals=%d vcfNodes=%d%n",
                            (System.currentTimeMillis() - t0) / 1000, s.midCalls, s.evals, vcf.nodes);
                    System.out.println("    " + rootReport(s, ra, rd));
                }
            } catch (InterruptedException ignored) {
            }
        });
        mon.setDaemon(true);
        mon.start();
        boolean win = s.solve(a, d);
        System.out.printf("result=%s t=%dms mid=%d evals=%d vcfNodes=%d%n", win ? "ATTACKER WINS" : "not proven",
                System.currentTimeMillis() - t0, s.midCalls, s.evals, vcf.nodes);
        System.out.println("    " + rootReport(s, ra, rd));
    }

    /** pn/dn of each root child, by move (racy reads: monitoring only). */
    static String rootReport(Solver s, long a, long d) {
        long[] ca = new long[64], cd = new long[64];
        int[] mv = new int[64];
        // children() uses Qubic's static canonical fields: compute on a snapshot under a lock-free race;
        // good enough for a progress display.
        int n;
        synchronized (Qubic.class) {
            n = childrenCopy(a, d, ca, cd, mv);
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < n; i++) {
            int e = s.find(ca[i], cd[i]);
            sb.append(mv[i]).append(':');
            if (e < 0) sb.append("?");
            else sb.append(fmt(s.pnAt(e))).append('/').append(fmt(s.dnAt(e)));
            sb.append(' ');
        }
        return sb.toString();
    }

    private static String fmt(int v) { return v >= Solver.INF ? "inf" : Integer.toString(v); }

    /** Same as Solver.children but with its own canonicalization (the monitor runs concurrently). */
    static int childrenCopy(long a, long d, long[] ca, long[] cd, int[] mv) {
        boolean att = Solver.attackerToMove(a, d);
        long m = Solver.moves(a, d);
        int n = 0;
        while (m != 0) {
            int c = Long.numberOfTrailingZeros(m);
            m &= m - 1;
            long na = att ? a | 1L << c : a;
            long nd = att ? d : d | 1L << c;
            long ba = na, bd = nd;
            for (int s = 1; s < Qubic.SYM_COUNT; s++) {
                long ta = Qubic.transform(s, na), td = Qubic.transform(s, nd);
                int cmp = Long.compareUnsigned(ta, ba);
                if (cmp < 0 || (cmp == 0 && Long.compareUnsigned(td, bd) < 0)) {
                    ba = ta;
                    bd = td;
                }
            }
            boolean dup = false;
            for (int i = 0; i < n; i++) if (ca[i] == ba && cd[i] == bd) dup = true;
            if (!dup) {
                ca[n] = ba;
                cd[n] = bd;
                mv[n] = c;
                n++;
            }
        }
        return n;
    }
}
