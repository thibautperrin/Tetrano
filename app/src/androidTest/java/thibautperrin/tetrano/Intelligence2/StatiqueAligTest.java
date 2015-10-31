package thibautperrin.tetrano.Intelligence2;

import junit.framework.TestCase;

/**
 * Created by Thibaut PERRIN on 12/07/2015.
 */
public class StatiqueAligTest extends TestCase {

    public void testNbAno1NbAno2NumEnsemble() throws Exception {
        byte alig = 0;
        assertEquals(0, StatiqueAlig.nbAno1(alig));
        assertEquals(0, StatiqueAlig.nbAno2(alig));
        assertEquals(0, StatiqueAlig.numEnsemble(alig));

        alig = 1;
        assertEquals(1, StatiqueAlig.nbAno1(alig));
        assertEquals(0, StatiqueAlig.nbAno2(alig));
        assertEquals(1, StatiqueAlig.numEnsemble(alig));

        alig = 2;
        assertEquals(2, StatiqueAlig.nbAno1(alig));
        assertEquals(0, StatiqueAlig.nbAno2(alig));
        assertEquals(2, StatiqueAlig.numEnsemble(alig));

        alig = 3;
        assertEquals(3, StatiqueAlig.nbAno1(alig));
        assertEquals(0, StatiqueAlig.nbAno2(alig));
        assertEquals(3, StatiqueAlig.numEnsemble(alig));

        alig = 4;
        assertEquals(4, StatiqueAlig.nbAno1(alig));
        assertEquals(0, StatiqueAlig.nbAno2(alig));
        assertEquals(4, StatiqueAlig.numEnsemble(alig));

        alig = 5;
        assertEquals(0, StatiqueAlig.nbAno1(alig));
        assertEquals(1, StatiqueAlig.nbAno2(alig));
        assertEquals(5, StatiqueAlig.numEnsemble(alig));

        alig = 6;
        assertEquals(1, StatiqueAlig.nbAno1(alig));
        assertEquals(1, StatiqueAlig.nbAno2(alig));
        assertEquals(9, StatiqueAlig.numEnsemble(alig));

        alig = 7;
        assertEquals(2, StatiqueAlig.nbAno1(alig));
        assertEquals(1, StatiqueAlig.nbAno2(alig));
        assertEquals(9, StatiqueAlig.numEnsemble(alig));

        alig = 8;
        assertEquals(3, StatiqueAlig.nbAno1(alig));
        assertEquals(1, StatiqueAlig.nbAno2(alig));
        assertEquals(9, StatiqueAlig.numEnsemble(alig));

        alig = 10;
        assertEquals(0, StatiqueAlig.nbAno1(alig));
        assertEquals(2, StatiqueAlig.nbAno2(alig));
        assertEquals(6, StatiqueAlig.numEnsemble(alig));

        alig = 11;
        assertEquals(1, StatiqueAlig.nbAno1(alig));
        assertEquals(2, StatiqueAlig.nbAno2(alig));
        assertEquals(9, StatiqueAlig.numEnsemble(alig));

        alig = 12;
        assertEquals(2, StatiqueAlig.nbAno1(alig));
        assertEquals(2, StatiqueAlig.nbAno2(alig));
        assertEquals(9, StatiqueAlig.numEnsemble(alig));

        alig = 15;
        assertEquals(0, StatiqueAlig.nbAno1(alig));
        assertEquals(3, StatiqueAlig.nbAno2(alig));
        assertEquals(7, StatiqueAlig.numEnsemble(alig));

        alig = 16;
        assertEquals(1, StatiqueAlig.nbAno1(alig));
        assertEquals(3, StatiqueAlig.nbAno2(alig));
        assertEquals(9, StatiqueAlig.numEnsemble(alig));

        alig = 20;
        assertEquals(0, StatiqueAlig.nbAno1(alig));
        assertEquals(4, StatiqueAlig.nbAno2(alig));
        assertEquals(8, StatiqueAlig.numEnsemble(alig));
    }

    public void testJouerDejouerAlig() {
        AnoAlig anoAlig = new AnoAlig();
        byte numAlig = 42;

        assertEquals(0, StatiqueAlig.nbAno1(anoAlig.alignements[numAlig]));
        assertEquals(0, StatiqueAlig.nbAno2(anoAlig.alignements[numAlig]));

        StatiqueAlig.jouerAlig(anoAlig, numAlig, (byte) 1);
        assertEquals(1, StatiqueAlig.nbAno1(anoAlig.alignements[numAlig]));
        assertEquals(0, StatiqueAlig.nbAno2(anoAlig.alignements[numAlig]));

        StatiqueAlig.dejouerAlig(anoAlig, numAlig, (byte) 1);
        assertEquals(0, StatiqueAlig.nbAno1(anoAlig.alignements[numAlig]));
        assertEquals(0, StatiqueAlig.nbAno2(anoAlig.alignements[numAlig]));

        StatiqueAlig.jouerAlig(anoAlig, numAlig, (byte) 1);
        assertEquals(1, StatiqueAlig.nbAno1(anoAlig.alignements[numAlig]));
        assertEquals(0, StatiqueAlig.nbAno2(anoAlig.alignements[numAlig]));

        StatiqueAlig.jouerAlig(anoAlig, numAlig, (byte) 1);
        assertEquals(2, StatiqueAlig.nbAno1(anoAlig.alignements[numAlig]));
        assertEquals(0, StatiqueAlig.nbAno2(anoAlig.alignements[numAlig]));

        StatiqueAlig.dejouerAlig(anoAlig, numAlig, (byte) 1);
        assertEquals(1, StatiqueAlig.nbAno1(anoAlig.alignements[numAlig]));
        assertEquals(0, StatiqueAlig.nbAno2(anoAlig.alignements[numAlig]));

        StatiqueAlig.dejouerAlig(anoAlig, numAlig, (byte) 1);
        assertEquals(0, StatiqueAlig.nbAno1(anoAlig.alignements[numAlig]));
        assertEquals(0, StatiqueAlig.nbAno2(anoAlig.alignements[numAlig]));

        StatiqueAlig.jouerAlig(anoAlig, numAlig, (byte) 1);
        assertEquals(1, StatiqueAlig.nbAno1(anoAlig.alignements[numAlig]));
        assertEquals(0, StatiqueAlig.nbAno2(anoAlig.alignements[numAlig]));

        StatiqueAlig.jouerAlig(anoAlig, numAlig, (byte) 2);
        assertEquals(1, StatiqueAlig.nbAno1(anoAlig.alignements[numAlig]));
        assertEquals(1, StatiqueAlig.nbAno2(anoAlig.alignements[numAlig]));

        StatiqueAlig.jouerAlig(anoAlig, numAlig, (byte) 2);
        assertEquals(1, StatiqueAlig.nbAno1(anoAlig.alignements[numAlig]));
        assertEquals(2, StatiqueAlig.nbAno2(anoAlig.alignements[numAlig]));

        StatiqueAlig.dejouerAlig(anoAlig, numAlig, (byte) 1);
        assertEquals(0, StatiqueAlig.nbAno1(anoAlig.alignements[numAlig]));
        assertEquals(2, StatiqueAlig.nbAno2(anoAlig.alignements[numAlig]));

        StatiqueAlig.dejouerAlig(anoAlig, numAlig, (byte) 2);
        assertEquals(0, StatiqueAlig.nbAno1(anoAlig.alignements[numAlig]));
        assertEquals(1, StatiqueAlig.nbAno2(anoAlig.alignements[numAlig]));

        StatiqueAlig.dejouerAlig(anoAlig, numAlig, (byte) 2);
        assertEquals(0, StatiqueAlig.nbAno1(anoAlig.alignements[numAlig]));
        assertEquals(0, StatiqueAlig.nbAno2(anoAlig.alignements[numAlig]));
    }

    public void testTrovAno() throws Exception {
        AnoAlig anoAlig = new AnoAlig();
        anoAlig.jouer((byte) 0, (byte) 1);
        anoAlig.jouer((byte) 48, (byte) 1);
        assertEquals(16, StatiqueAlig.trov2ano(anoAlig, (byte) 1));
        assertEquals(32, StatiqueAlig.trov2anoBis(anoAlig, (byte) 1));
        anoAlig.jouer((byte) 16, (byte) 1);
        assertEquals(32, StatiqueAlig.trovLastAno(anoAlig, (byte) 1));
        anoAlig.restart();

        anoAlig.jouer((byte) 12, (byte) 1);
        anoAlig.jouer((byte) 28, (byte) 1);
        assertEquals(44, StatiqueAlig.trov2ano(anoAlig, (byte) 10));
        assertEquals(60, StatiqueAlig.trov2anoBis(anoAlig, (byte) 10));
        anoAlig.dejouer((byte) 12, (byte) 1);
        anoAlig.dejouer((byte) 28, (byte) 1);

        anoAlig.jouer((byte) 12, (byte) 1);
        anoAlig.jouer((byte) 44, (byte) 1);
        assertEquals(28, StatiqueAlig.trov2ano(anoAlig, (byte) 10));
        assertEquals(60, StatiqueAlig.trov2anoBis(anoAlig, (byte) 10));
        anoAlig.dejouer((byte) 12, (byte) 1);
        anoAlig.dejouer((byte) 44, (byte) 1);

        anoAlig.jouer((byte) 12, (byte) 1);
        anoAlig.jouer((byte) 60, (byte) 1);
        assertEquals(28, StatiqueAlig.trov2ano(anoAlig, (byte) 10));
        assertEquals(44, StatiqueAlig.trov2anoBis(anoAlig, (byte) 10));
        anoAlig.dejouer((byte) 12, (byte) 1);
        anoAlig.dejouer((byte) 60, (byte) 1);

        anoAlig.jouer((byte) 28, (byte) 1);
        anoAlig.jouer((byte) 60, (byte) 1);
        assertEquals(12, StatiqueAlig.trov2ano(anoAlig, (byte) 10));
        assertEquals(44, StatiqueAlig.trov2anoBis(anoAlig, (byte) 10));
        anoAlig.dejouer((byte) 28, (byte) 1);
        anoAlig.dejouer((byte) 60, (byte) 1);

        anoAlig.jouer((byte) 44, (byte) 1);
        anoAlig.jouer((byte) 60, (byte) 1);
        assertEquals(12, StatiqueAlig.trov2ano(anoAlig, (byte) 10));
        assertEquals(28, StatiqueAlig.trov2anoBis(anoAlig, (byte) 10));
        anoAlig.dejouer((byte) 44, (byte) 1);
        anoAlig.dejouer((byte) 60, (byte) 1);

        anoAlig.jouer((byte) 28, (byte) 1);
        anoAlig.jouer((byte) 44, (byte) 1);
        assertEquals(12, StatiqueAlig.trov2ano(anoAlig, (byte) 10));
        assertEquals(60, StatiqueAlig.trov2anoBis(anoAlig, (byte) 10));
        anoAlig.dejouer((byte) 28, (byte) 1);
        anoAlig.dejouer((byte) 44, (byte) 1);

        anoAlig.jouer((byte) 12, (byte) 1);
        anoAlig.jouer((byte) 28, (byte) 1);
        anoAlig.jouer((byte) 44, (byte) 1);
        assertEquals(60, StatiqueAlig.trovLastAno(anoAlig, (byte) 10));

        anoAlig.dejouer((byte) 44, (byte) 1);
        anoAlig.jouer((byte) 60, (byte) 1);
        assertEquals(44, StatiqueAlig.trovLastAno(anoAlig, (byte) 10));
        anoAlig.dejouer((byte) 28, (byte) 1);
        anoAlig.jouer((byte) 44, (byte) 1);
        assertEquals(28, StatiqueAlig.trovLastAno(anoAlig, (byte) 10));
        anoAlig.dejouer((byte) 12, (byte) 1);
        anoAlig.jouer((byte) 28, (byte) 1);
        assertEquals(12, StatiqueAlig.trovLastAno(anoAlig, (byte) 10));
        anoAlig.restart();
    }
}