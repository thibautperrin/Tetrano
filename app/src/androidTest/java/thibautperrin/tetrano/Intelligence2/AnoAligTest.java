package thibautperrin.tetrano.Intelligence2;

import junit.framework.TestCase;

import java.util.Arrays;

/**
 * Created by Thibaut PERRIN on 12/07/2015.
 */
public class AnoAligTest extends TestCase {

    public void testGlobal() throws Exception {
        AnoAlig anoAlig = new AnoAlig();
        assertEquals(0,anoAlig.getNbAnneauxJoues());
        assertEquals(76, anoAlig.ensembles[0].length());
        for (int i=1; i<10; i++) {
            assertEquals(0, anoAlig.ensembles[i].length());
            assertEquals(-1, anoAlig.ensembles[i].next((byte) -1));
        }
        for (int i=0; i< 64; i++) {
            assertEquals(0, anoAlig.anneaux[i]);
        }
        for (int i=0; i< 76; i++) {
            assertEquals(0, anoAlig.alignements[i]);
        }

        anoAlig.jouer((byte) 0, (byte) 1);

        assertEquals(1, anoAlig.anneaux[0]);
        assertEquals(7, anoAlig.ensembles[1].length());
        assertEquals(69, anoAlig.ensembles[0].length());
        assertEquals(1, anoAlig.getNbAnneauxJoues());
        assertEquals(1, anoAlig.alignements[0]);
        assertEquals(1, anoAlig.alignements[1]);
        assertEquals(1, anoAlig.alignements[2]);
        assertEquals(1, anoAlig.alignements[48]);
        assertEquals(1, anoAlig.alignements[49]);
        assertEquals(1, anoAlig.alignements[50]);
        assertEquals(1, anoAlig.alignements[72]);

        anoAlig.dejouer((byte) 0, (byte) 1);

        assertEquals(0,anoAlig.getNbAnneauxJoues());
        assertEquals(76, anoAlig.ensembles[0].length());
        for (int i=1; i<10; i++) {
            assertEquals(0, anoAlig.ensembles[i].length());
            assertEquals(-1, anoAlig.ensembles[i].next((byte) -1));
        }
        for (int i=0; i< 64; i++) {
            assertEquals(0, anoAlig.anneaux[i]);
        }
        for (int i=0; i< 76; i++) {
            assertEquals(0, anoAlig.alignements[i]);
        }

        anoAlig.jouer((byte) 0, (byte) 2);

        assertEquals(2, anoAlig.anneaux[0]);
        assertEquals(7, anoAlig.ensembles[5].length());
        assertEquals(69, anoAlig.ensembles[0].length());
        assertEquals(1, anoAlig.getNbAnneauxJoues());
        assertEquals(5, anoAlig.alignements[0]);
        assertEquals(5, anoAlig.alignements[1]);
        assertEquals(5, anoAlig.alignements[2]);
        assertEquals(5, anoAlig.alignements[48]);
        assertEquals(5, anoAlig.alignements[49]);
        assertEquals(5, anoAlig.alignements[50]);
        assertEquals(5, anoAlig.alignements[72]);

        anoAlig.dejouer((byte) 0, (byte) 2);

        assertEquals(0,anoAlig.getNbAnneauxJoues());
        assertEquals(76, anoAlig.ensembles[0].length());
        for (int i=1; i<10; i++) {
            assertEquals(0, anoAlig.ensembles[i].length());
            assertEquals(-1, anoAlig.ensembles[i].next((byte) -1));
        }
        for (int i=0; i< 64; i++) {
            assertEquals(0, anoAlig.anneaux[i]);
        }
        for (int i=0; i< 76; i++) {
            assertEquals(0, anoAlig.alignements[i]);
        }

        anoAlig.jouer((byte) 0, (byte) 1);
        anoAlig.jouer((byte) 48, (byte) 2);

        assertEquals(1, anoAlig.anneaux[0]);
        assertEquals(2, anoAlig.anneaux[48]);
        assertEquals(6, anoAlig.ensembles[5].length());
        assertEquals(6, anoAlig.ensembles[1].length());
        assertEquals(63, anoAlig.ensembles[0].length());
        assertEquals(1, anoAlig.ensembles[9].length());
        assertEquals(2, anoAlig.getNbAnneauxJoues());
        assertEquals(1, anoAlig.alignements[0]);
        assertEquals(6, anoAlig.alignements[1]);
        assertEquals(1, anoAlig.alignements[2]);
        assertEquals(1, anoAlig.alignements[48]);
        assertEquals(1, anoAlig.alignements[49]);
        assertEquals(1, anoAlig.alignements[50]);
        assertEquals(1, anoAlig.alignements[72]);
        assertEquals(5, anoAlig.alignements[9]);
        assertEquals(5, anoAlig.alignements[11]);
        assertEquals(5, anoAlig.alignements[51]);
        assertEquals(5, anoAlig.alignements[53]);
        assertEquals(5, anoAlig.alignements[67]);
        assertEquals(5, anoAlig.alignements[75]);

        anoAlig.dejouer((byte) 48, (byte) 2);

        assertEquals(1, anoAlig.anneaux[0]);
        assertEquals(7, anoAlig.ensembles[1].length());
        assertEquals(69, anoAlig.ensembles[0].length());
        assertEquals(1, anoAlig.getNbAnneauxJoues());
        assertEquals(1, anoAlig.alignements[0]);
        assertEquals(1, anoAlig.alignements[1]);
        assertEquals(1, anoAlig.alignements[2]);
        assertEquals(1, anoAlig.alignements[48]);
        assertEquals(1, anoAlig.alignements[49]);
        assertEquals(1, anoAlig.alignements[50]);
        assertEquals(1, anoAlig.alignements[72]);

        anoAlig.dejouer((byte) 0, (byte) 1);

        assertEquals(0,anoAlig.getNbAnneauxJoues());
        assertEquals(76, anoAlig.ensembles[0].length());
        for (int i=1; i<10; i++) {
            assertEquals(0, anoAlig.ensembles[i].length());
            assertEquals(-1, anoAlig.ensembles[i].next((byte) -1));
        }
        for (int i=0; i< 64; i++) {
            assertEquals(0, anoAlig.anneaux[i]);
        }
        for (int i=0; i< 76; i++) {
            assertEquals(0, anoAlig.alignements[i]);
        }

        anoAlig.jouer((byte)62, (byte) 1);
        anoAlig.jouer((byte)38, (byte) 2);
        anoAlig.jouer((byte)12, (byte) 1);
        anoAlig.jouer((byte)3, (byte) 2);
        anoAlig.jouer((byte)2, (byte) 1);
        anoAlig.jouer((byte)0, (byte) 2);
        anoAlig.restart();

        assertEquals(0,anoAlig.getNbAnneauxJoues());
        assertEquals(76, anoAlig.ensembles[0].length());
        for (int i=1; i<10; i++) {
            assertEquals(0, anoAlig.ensembles[i].length());
            assertEquals(-1, anoAlig.ensembles[i].next((byte) -1));
        }
        for (int i=0; i< 64; i++) {
            assertEquals(0, anoAlig.anneaux[i]);
        }
        for (int i=0; i< 76; i++) {
            assertEquals(0, anoAlig.alignements[i]);
        }
    }

    public void testConstructeurFromSignature() {
        AnoAlig anoAlig1 = new AnoAlig();
        anoAlig1.jouer((byte)1,(byte)1);
        anoAlig1.jouer((byte)45,(byte)2);
        anoAlig1.jouer((byte)8,(byte)1);
        anoAlig1.jouer((byte)63,(byte)2);
        anoAlig1.jouer((byte)42,(byte)1);
        byte[] signature = anoAlig1.getSignature(false);
        AnoAlig anoAlig2 = new AnoAlig(signature, false);
        anoAlig2.dejouer((byte)42,(byte)1);
        anoAlig2.dejouer((byte) 63, (byte) 2);
        anoAlig2.dejouer((byte) 8, (byte) 1);
        anoAlig2.dejouer((byte) 45, (byte) 2);
        anoAlig2.dejouer((byte) 1, (byte) 1);
        assertTrue(Arrays.equals(
                new byte[]{-128,-128,-128,-128,-128,-128,-128,-128,-128,-128,-128,-128,-128},
                anoAlig2.getSignature(false)));
        assertEquals(0,anoAlig2.getNbAnneauxJoues());
        assertEquals(76, anoAlig2.ensembles[0].length());
        for (int i=1; i<10; i++) {
            assertEquals(0, anoAlig2.ensembles[i].length());
            assertEquals(-1, anoAlig2.ensembles[i].next((byte) -1));
        }
        for (int i=0; i< 64; i++) {
            assertEquals(0, anoAlig2.anneaux[i]);
        }
        for (int i=0; i< 76; i++) {
            assertEquals(0, anoAlig2.alignements[i]);
        }

        AnoAlig anoAlig3 = new AnoAlig(signature, true);
        anoAlig3.dejouer((byte)42,(byte)2);
        anoAlig3.dejouer((byte) 63, (byte) 1);
        anoAlig3.dejouer((byte) 8, (byte) 2);
        anoAlig3.dejouer((byte) 45, (byte) 1);
        anoAlig3.dejouer((byte) 1, (byte) 2);
        System.out.println(Arrays.toString(anoAlig3.getSignature(false)));
        assertTrue(Arrays.equals(
                new byte[]{-128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128},
                anoAlig3.getSignature(false)));
        assertEquals(0,anoAlig3.getNbAnneauxJoues());
        assertEquals(76, anoAlig3.ensembles[0].length());
        for (int i=1; i<10; i++) {
            assertEquals(0, anoAlig3.ensembles[i].length());
            assertEquals(-1, anoAlig3.ensembles[i].next((byte) -1));
        }
        for (int i=0; i< 64; i++) {
            assertEquals(0, anoAlig3.anneaux[i]);
        }
        for (int i=0; i< 76; i++) {
            assertEquals(0, anoAlig3.alignements[i]);
        }
    }

}