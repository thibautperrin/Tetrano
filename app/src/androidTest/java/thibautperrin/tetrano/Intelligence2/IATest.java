package thibautperrin.tetrano.Intelligence2;

import junit.framework.TestCase;

/**
 * Created by Thibaut PERRIN on 12/07/2015.
 */
public class IATest extends TestCase {

    public void testIsFourchetteSimple() throws Exception {
        AnoAlig anoAlig = new AnoAlig();
        IA ia = new IA(anoAlig);
        assertEquals(-1, ia.isFourchetteSimple((byte) 1));
        assertEquals(-1, ia.isFourchetteSimple((byte) 2));
        anoAlig.jouer((byte) 0, (byte) 1);
        assertEquals(-1, ia.isFourchetteSimple((byte) 1));
        assertEquals(-1, ia.isFourchetteSimple((byte) 2));
        anoAlig.jouer((byte) 25, (byte) 2);
        assertEquals(-1, ia.isFourchetteSimple((byte) 1));
        assertEquals(-1, ia.isFourchetteSimple((byte) 2));
        anoAlig.jouer((byte) 3, (byte) 1);
        assertEquals(-1, ia.isFourchetteSimple((byte) 1));
        assertEquals(-1, ia.isFourchetteSimple((byte) 2));
        anoAlig.jouer((byte) 26, (byte) 2);
        assertEquals(-1, ia.isFourchetteSimple((byte) 1));
        assertEquals(-1, ia.isFourchetteSimple((byte) 2));
        anoAlig.jouer((byte) 22, (byte) 1);
        assertEquals(-1, ia.isFourchetteSimple((byte) 1));
        assertEquals(-1, ia.isFourchetteSimple((byte) 2));
        anoAlig.jouer((byte) 8, (byte) 2);
        assertEquals(-1, ia.isFourchetteSimple((byte) 1));
        assertEquals(-1, ia.isFourchetteSimple((byte) 2));
        anoAlig.jouer((byte) 42, (byte) 1);
        assertEquals(2, ia.isFourchetteSimple((byte) 1));
        assertEquals(-1, ia.isFourchetteSimple((byte) 2));
        anoAlig.jouer((byte) 40, (byte) 2);
        assertEquals(2, ia.isFourchetteSimple((byte) 1));
        assertEquals(24, ia.isFourchetteSimple((byte) 2));
        anoAlig.jouer((byte) 24, (byte) 1);
        assertEquals(2, ia.isFourchetteSimple((byte) 1));
        assertEquals(-1, ia.isFourchetteSimple((byte) 2));
        anoAlig.jouer((byte) 2, (byte) 2);
        assertEquals(-1, ia.isFourchetteSimple((byte) 1));
        assertEquals(-1, ia.isFourchetteSimple((byte) 2));
        anoAlig.dejouer((byte) 2, (byte) 2);
        assertEquals(2, ia.isFourchetteSimple((byte) 1));
        assertEquals(-1, ia.isFourchetteSimple((byte) 2));
        anoAlig.dejouer((byte) 24, (byte) 1);
        assertEquals(2, ia.isFourchetteSimple((byte) 1));
        assertEquals(24, ia.isFourchetteSimple((byte) 2));
        anoAlig.jouer((byte) 27, (byte) 1);
        assertEquals(2, ia.isFourchetteSimple((byte) 1));
        assertEquals(-1, ia.isFourchetteSimple((byte) 2));
        anoAlig.dejouer((byte) 27, (byte) 1);
        assertEquals(2, ia.isFourchetteSimple((byte) 1));
        assertEquals(24, ia.isFourchetteSimple((byte) 2));
        anoAlig.jouer((byte) 56, (byte) 1);
        assertEquals(2, ia.isFourchetteSimple((byte) 1));
        assertEquals(-1, ia.isFourchetteSimple((byte) 2));
        anoAlig.jouer((byte) 1, (byte) 2);
        assertEquals(-1, ia.isFourchetteSimple((byte) 1));
        assertEquals(-1, ia.isFourchetteSimple((byte) 2));
        anoAlig.dejouer((byte) 1, (byte) 2);
        assertEquals(2, ia.isFourchetteSimple((byte) 1));
        assertEquals(-1, ia.isFourchetteSimple((byte) 2));
        anoAlig.jouer((byte) 62, (byte) 2);
        assertEquals(-1, ia.isFourchetteSimple((byte) 1));
        assertEquals(-1, ia.isFourchetteSimple((byte) 2));
        anoAlig.dejouer((byte) 62, (byte) 2);
        assertEquals(2, ia.isFourchetteSimple((byte) 1));
        assertEquals(-1, ia.isFourchetteSimple((byte) 2));
        anoAlig.restart();
        assertEquals(-1, ia.isFourchetteSimple((byte) 1));
        assertEquals(-1, ia.isFourchetteSimple((byte) 2));
    }

    public boolean assertCompleteNotFork(IA ia, int ordreJ1, int ordreJ2) throws Exception {
        for (int i = 0; i <= ordreJ1; i++) {
            if (ia.fourchetteAvancee((byte) 1, (byte) i) != null) return true;
        }
        for (int i = 0; i <= ordreJ2; i++) {
            if (ia.fourchetteAvancee((byte) 2, (byte) i) != null) return true;
        }
        return false;
    }

    public void testFourchetteAvancee() throws Exception {
        AnoAlig anoAlig = new AnoAlig();
        IA ia = new IA(anoAlig);
        assertFalse(assertCompleteNotFork(ia, 5, 5));
        anoAlig.jouer((byte) 0, (byte) 1);
        assertFalse(assertCompleteNotFork(ia, 5, 5));
        anoAlig.jouer((byte) 63, (byte) 2);
        assertFalse(assertCompleteNotFork(ia, 5, 5));
        anoAlig.jouer((byte) 3, (byte) 1);
        assertFalse(assertCompleteNotFork(ia, 5, 5));
        anoAlig.jouer((byte) 62, (byte) 2);
        assertFalse(assertCompleteNotFork(ia, 5, 5));
        anoAlig.jouer((byte) 15, (byte) 1);
        assertFalse(assertCompleteNotFork(ia, 3, 5));
        assertNotNull(ia.fourchetteAvancee((byte) 1, 4));
        assertNotNull(ia.fourchetteAvancee((byte) 1, 5));
        anoAlig.jouer((byte) 59, (byte) 2);
        assertFalse(assertCompleteNotFork(ia, 3, 5));
        assertNotNull(ia.fourchetteAvancee((byte) 1, 4));
        assertNotNull(ia.fourchetteAvancee((byte) 1, 5));
        anoAlig.dejouer((byte) 59, (byte) 2);
        assertFalse(assertCompleteNotFork(ia, 3, 5));
        assertNotNull(ia.fourchetteAvancee((byte) 1, 4));
        assertNotNull(ia.fourchetteAvancee((byte) 1, 5));
        anoAlig.jouer((byte) 12, (byte) 2);
        assertFalse(assertCompleteNotFork(ia, 5, 5));
    }

    /**
     * Juste pour vérifier qu'aucune exception n'est lancée.
     *
     * @throws Exception
     */
    public void testVaguementjouerNiveau() throws Exception {
        AnoAlig anoAlig = new AnoAlig();
        IA ia = new IA(anoAlig);
        byte numAnneau = ia.jouerNiveau((byte) 1, 7,null);
        anoAlig.jouer(numAnneau, (byte) 1);
        numAnneau = ia.jouerNiveau((byte) 2, 89, null);
        anoAlig.jouer(numAnneau, (byte) 2);
        numAnneau = ia.jouerNiveau((byte) 1, 0, null);
        anoAlig.jouer(numAnneau, (byte) 1);
        numAnneau = ia.jouerNiveau((byte) 2, 80, null);
        anoAlig.jouer(numAnneau, (byte) 2);
        numAnneau = ia.jouerNiveau((byte) 1, 40, null);
        anoAlig.jouer(numAnneau, (byte) 1);
        numAnneau = ia.jouerNiveau((byte) 2, 50, null);
        anoAlig.jouer(numAnneau, (byte) 2);
        numAnneau = ia.jouerNiveau((byte) 1, 70, null);
        anoAlig.jouer(numAnneau, (byte) 1);
        numAnneau = ia.jouerNiveau((byte) 2, 69,null);
        anoAlig.jouer(numAnneau, (byte) 2);
        numAnneau = ia.jouerNiveau((byte) 1, 21, null);
        anoAlig.jouer(numAnneau, (byte) 1);
        numAnneau = ia.jouerNiveau((byte) 2, 20, null);
        anoAlig.jouer(numAnneau, (byte) 2);
    }

    public void testBloqueFourchette() {
        AnoAlig anoAlig = new AnoAlig();
        IA ia = new IA(anoAlig);
        anoAlig.jouer((byte) (0 + 4 * 0 + 16 * 0), (byte) 1);
        anoAlig.jouer((byte) (1 + 4 * 0 + 16 * 0), (byte) 2);
        anoAlig.jouer((byte) (0 + 4 * 3 + 16 * 0), (byte) 1);
        anoAlig.jouer((byte) (2 + 4 * 2 + 16 * 1), (byte) 2);
        anoAlig.jouer((byte) (0 + 4 * 1 + 16 * 1), (byte) 1);
        anoAlig.jouer((byte) (2 + 4 * 3 + 16 * 0), (byte) 2);
        anoAlig.jouer((byte) (0 + 4 * 1 + 16 * 2), (byte) 1);

        assertNotNull(ia.fourchetteAvancee((byte) 1, 0));
        assertEquals(0 + 4 * 1 + 0 * 16, ia.fourchetteAvancee((byte) 1, 0)[0]);

        assertEquals(0 + 4 * 1 + 0 * 16, ia.isFourchetteSimple((byte) 1));

        anoAlig.jouer(ia.bloqueFourchetteAvanceeSimplifiee((byte) 2, 2, null), (byte) 2);
        assertNull(ia.fourchetteAvancee((byte) 1, 0));
    }

    public void testGetDejaJoue() {
        long l = IA.getDejaJoue(442);
        assertEquals(1152921504606847418L, l);
        l = IA.getDejaJoue(l, 623);
        assertEquals(2305843009214332346L, l);
        l = IA.getDejaJoue(l, 0);
        assertEquals(3458764514474256384L, l);
        l = IA.getDejaJoue(l, 911);
        assertEquals(4611686997259905024L, l);
        l = IA.getDejaJoue(l, 13);
        assertEquals(5765609847531779072L, l);
        l = IA.getDejaJoue(l, 128);
        assertEquals(7943909313246934016L, l);
    }

    public void testBigFourchette() {
        AnoAlig anoAlig = new AnoAlig();
        IA ia = new IA(anoAlig);
        assertFalse(assertCompleteNotBigFork(ia, 8, 8));
        anoAlig.jouer((byte) 0, (byte) 1);
        assertFalse(assertCompleteNotBigFork(ia, 8, 8));
        anoAlig.jouer((byte) 63, (byte) 2);
        assertFalse(assertCompleteNotBigFork(ia, 8, 8));
        anoAlig.jouer((byte) 3, (byte) 1);
        assertFalse(assertCompleteNotBigFork(ia, 8, 8));
        anoAlig.jouer((byte) 62, (byte) 2);
        assertFalse(assertCompleteNotBigFork(ia, 8, 8));
        anoAlig.jouer((byte) 15, (byte) 1);
        assertFalse(assertCompleteNotBigFork(ia, 3, 5));
        assertTrue(-1 != ia.bigFourchetteAvancee((byte) 1, (byte) 4, null));
        assertTrue(-1 != ia.bigFourchetteAvancee((byte) 1, (byte) 5, null));
        assertTrue(-1 != ia.bigFourchetteAvancee((byte) 1, (byte) 6, null));
        anoAlig.jouer((byte) 59, (byte) 2);
        assertFalse(assertCompleteNotBigFork(ia, 3, 5));
        assertTrue(-1 != ia.bigFourchetteAvancee((byte) 1, (byte) 4, null));
        assertTrue(-1 != ia.bigFourchetteAvancee((byte) 1, (byte) 5, null));
        assertTrue(-1 != ia.bigFourchetteAvancee((byte) 1, (byte) 6, null));
        anoAlig.dejouer((byte) 59, (byte) 2);
        assertFalse(assertCompleteNotBigFork(ia, 3, 5));
        assertTrue(-1 != ia.bigFourchetteAvancee((byte) 1, (byte) 4, null));
        assertTrue(-1 != ia.bigFourchetteAvancee((byte) 1, (byte) 5, null));
        assertTrue(-1 != ia.bigFourchetteAvancee((byte) 1, (byte) 6, null));
        anoAlig.jouer((byte) 12, (byte) 2);
        assertFalse(assertCompleteNotBigFork(ia, 5, 5));
    }

    public boolean assertCompleteNotBigFork(IA ia, int ordreJ1, int ordreJ2) {
        for (int i = 0; i <= ordreJ1; i++) {
            if (ia.bigFourchetteAvancee((byte) 1, (byte) i, null) != -1) return true;
        }
        for (int i = 0; i <= ordreJ2; i++) {
            if (ia.bigFourchetteAvancee((byte) 2, (byte) i, null) != -1) return true;
        }
        return false;
    }

    public void testFourchettes() {
        AnoAlig anoAlig = new AnoAlig();
        IA ia = new IA(anoAlig);
        anoAlig.jouer((byte) (3 + 4 * 3 + 16 * 0), (byte) 1);
        anoAlig.jouer((byte) (1 + 4 * 2 + 16 * 2), (byte) 2);
        anoAlig.jouer((byte) (0 + 4 * 3 + 16 * 0), (byte) 1);
        anoAlig.jouer((byte) (1 + 4 * 1 + 16 * 1), (byte) 2);
        anoAlig.jouer((byte) (0 + 4 * 0 + 16 * 0), (byte) 1);
        anoAlig.jouer((byte) (2 + 4 * 1 + 16 * 1), (byte) 2);
        anoAlig.jouer((byte) (0 + 4 * 3 + 16 * 3), (byte) 1);
        anoAlig.jouer((byte) (1 + 4 * 1 + 16 * 2), (byte) 2);
        anoAlig.jouer((byte) (1 + 4 * 2 + 16 * 1), (byte) 1);
        anoAlig.jouer((byte) (2 + 4 * 1 + 16 * 2), (byte) 2);
        anoAlig.jouer((byte) (0 + 4 * 1 + 16 * 0), (byte) 1);
        anoAlig.jouer((byte) (0 + 4 * 2 + 16 * 0), (byte) 2);
        anoAlig.jouer((byte) (3 + 4 * 0 + 16 * 2), (byte) 1);
        byte[] res1 = ia.fourchetteAvancee((byte) 2, (byte) 4);
        if (res1 == null) {
            assertEquals(-1, ia.bigFourchetteAvancee((byte) 2, (byte) 4, null));
        } else {
            assertEquals(-1, ia.bigFourchetteAvancee((byte) 2, (byte) (res1.length / 2 - 1), null));
            assertTrue(-1 != ia.bigFourchetteAvancee((byte) 2, (byte) (res1.length / 2), null));
        }
        anoAlig.jouer((byte) (0 + 4 * 1 + 16 * 1), (byte) 2);
        anoAlig.jouer((byte) (3 + 4 * 1 + 16 * 1), (byte) 1);
        anoAlig.jouer((byte) (1 + 4 * 0 + 16 * 0), (byte) 2);
        anoAlig.jouer((byte) (1 + 4 * 3 + 16 * 3), (byte) 1);
    }

    public void testFourchettes2() {
        AnoAlig anoAlig = new AnoAlig();
        IA ia = new IA(anoAlig);
        anoAlig.jouer((byte) (3 + 4 * 3 + 16 * 0), (byte) 1);
        anoAlig.jouer((byte) (1 + 4 * 2 + 16 * 2), (byte) 2);

        anoAlig.jouer((byte) (0 + 4 * 3 + 16 * 0), (byte) 1);
        anoAlig.jouer((byte) (1 + 4 * 1 + 16 * 1), (byte) 2);

        anoAlig.jouer((byte) (3 + 4 * 0 + 16 * 0), (byte) 1);
        anoAlig.jouer((byte) (2 + 4 * 2 + 16 * 2), (byte) 2);

        anoAlig.jouer((byte) (2 + 4 * 1 + 16 * 1), (byte) 1);
        anoAlig.jouer((byte) (0 + 4 * 0 + 16 * 3), (byte) 2);

        anoAlig.jouer((byte) (1 + 4 * 1 + 16 * 2), (byte) 1);
        anoAlig.jouer((byte) (3 + 4 * 1 + 16 * 0), (byte) 2);

        anoAlig.jouer((byte) (3 + 4 * 0 + 16 * 3), (byte) 1);
        anoAlig.jouer((byte) (1 + 4 * 2 + 16 * 1), (byte) 2);

        anoAlig.jouer((byte) (2 + 4 * 2 + 16 * 1), (byte) 1);
        byte res = ia.bigFourchetteAvancee((byte) 2, (byte) 4, null);
        anoAlig.jouer((byte) (0 + 4 * 2 + 16 * 2), (byte) 2);
    }

    public void testBigFourchette2() {
        Partie partie = new Partie();
        IA iA = new IA(partie.getAnoAlig());
        partie.jouer((byte) 3);
        partie.jouer((byte) 48);
        partie.jouer((byte) 12);
        partie.jouer((byte) 25);
        partie.jouer((byte) 37);
        partie.jouer((byte) 26);
        partie.jouer((byte) 60);
        partie.jouer((byte) 42);
        assertFalse(iA.bigFourchetteAvancee((byte) 1, (byte) 10, null) == -1);
        assertFalse(iA.bigFourchetteAvancee((byte) 1, (byte) 6, null) == -1);
        assertEquals(-1, iA.bigFourchetteAvancee((byte) 1, (byte) 5, null));
        partie.jouer((byte) 28);
        partie.jouer((byte) 44);
        assertFalse(iA.bigFourchetteAvancee((byte) 1, (byte) 10, null) == -1);
        assertFalse(iA.bigFourchetteAvancee((byte) 1, (byte) 5, null) == -1);
        assertEquals(41, iA.bigFourchetteAvancee((byte) 1, (byte) 5, null));
        assertEquals(-1, iA.bigFourchetteAvancee((byte) 1, (byte) 4, null));
        partie.jouer((byte) 41);
        partie.jouer((byte) 22);
        assertFalse(iA.bigFourchetteAvancee((byte) 1, (byte) 10, null) == -1);
        assertFalse(iA.bigFourchetteAvancee((byte) 1, (byte) 4, null) == -1);
        assertEquals(-1, iA.bigFourchetteAvancee((byte) 1, (byte) 3, null));
        partie.jouer((byte) 45);
        partie.jouer((byte) 33);
        assertFalse(iA.bigFourchetteAvancee((byte) 1, (byte) 10, null) == -1);
        assertFalse(iA.bigFourchetteAvancee((byte) 1, (byte) 3, null) == -1);
        assertEquals(-1, iA.bigFourchetteAvancee((byte) 1, (byte) 2, null));
        partie.jouer((byte) 30);
        partie.jouer((byte) 15);
        assertFalse(iA.bigFourchetteAvancee((byte) 1, (byte) 10, null) == -1);
        assertFalse(iA.bigFourchetteAvancee((byte) 1, (byte) 2, null) == -1);
        assertEquals(-1, iA.bigFourchetteAvancee((byte) 1, (byte) 1, null));
        partie.jouer((byte) 29);
        partie.jouer((byte) 31);
        assertFalse(iA.bigFourchetteAvancee((byte) 1, (byte) 10, null) == -1);
        assertFalse(iA.bigFourchetteAvancee((byte) 1, (byte) 1, null) == -1);
        assertEquals(-1, iA.bigFourchetteAvancee((byte) 1, (byte) 0, null));
        partie.jouer((byte) 63);
        partie.jouer((byte) 46);
        assertEquals(61, iA.bigFourchetteAvancee((byte) 1, (byte) 0, null));
    }

    public void testBigFourchette3() {
        Partie partie = new Partie();
        IA iA = new IA(partie.getAnoAlig());
        partie.jouer((byte) 26);
        partie.jouer((byte) 21);
        partie.jouer((byte) 15);
        partie.jouer((byte) 60);
        partie.jouer((byte) 63);
        partie.jouer((byte) 41);
        partie.jouer((byte) 12);
        partie.jouer((byte) 37);
        partie.jouer((byte) 47);
        partie.jouer((byte) 31);
        partie.jouer((byte) 14);
        partie.jouer((byte) 13);
        assertFalse(iA.bigFourchetteAvancee((byte) 1, (byte) 10, null) == -1);
        byte res = iA.bigFourchetteAvancee((byte) 1, (byte) 5, null);
        assertFalse(res == -1);
        assertEquals(-1, iA.bigFourchetteAvancee((byte) 1, (byte) 4, null));
        partie.jouer((byte) 38);
        partie.jouer((byte) 50);
        assertFalse(iA.bigFourchetteAvancee((byte) 1, (byte) 4, null) == -1);
        assertEquals(-1, iA.bigFourchetteAvancee((byte) 1, (byte) 3, null));
        partie.jouer((byte) 46);
        partie.jouer((byte) 29);
        assertFalse(iA.bigFourchetteAvancee((byte) 1, (byte) 3, null) == -1);
        assertEquals(-1, iA.bigFourchetteAvancee((byte) 1, (byte) 2, null));
        partie.jouer((byte) 42);
        partie.jouer((byte) 34);
        assertFalse(iA.bigFourchetteAvancee((byte) 1, (byte) 2, null) == -1);
        assertEquals(-1, iA.bigFourchetteAvancee((byte) 1, (byte) 1, null));
        partie.jouer((byte) 10);
        partie.jouer((byte) 58);
        assertFalse(iA.bigFourchetteAvancee((byte) 1, (byte) 1, null) == -1);
        assertEquals(-1, iA.bigFourchetteAvancee((byte) 1, (byte) 0, null));
        partie.jouer((byte) 2);
        partie.jouer((byte) 6);
        assertEquals(62, iA.bigFourchetteAvancee((byte) 1, (byte) 0, null));

    }
}