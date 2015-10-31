package thibautperrin.tetrano.Intelligence2;

import android.provider.Telephony;

import junit.framework.TestCase;

import java.util.Arrays;

/**
 * Created by Thibaut PERRIN on 16/07/2015.
 */
public class PartieTest extends TestCase {

    public void testGetSignature() throws Exception {
        Partie partie = new Partie();
        byte[] signature = partie.getSignature();
        for (int i = 0; i < 13; i++) {
            assertEquals(Byte.MIN_VALUE, signature[i]);
        }

        partie.jouer((byte) 0);
        signature = partie.getSignature();
        assertEquals(-127, signature[0]);
        for (int i = 1; i < 13; i++) {
            assertEquals(-128, signature[i]);
        }

        partie.dejouer();
        partie.jouer((byte) 42);
        signature = partie.getSignature();
        // 42 = 8*5+2
        assertEquals(-119, signature[8]);

        partie.dejouer();
        partie.jouer((byte) 19);
        // 19 = 3*5+4
        signature = partie.getSignature();
        assertEquals((byte) -128 + 81, signature[3]);

        partie.jouer((byte) 63);
        partie.jouer((byte) 33);
        partie.jouer((byte) 63);
        partie.jouer((byte) 33);
        partie.jouer((byte) 12);
        partie.jouer((byte) 11);
        partie.jouer((byte) 10);
        partie.jouer((byte) 47);
        partie.jouer((byte) 48);
        partie.jouer((byte) 26);
        partie.jouer((byte) 29);
        partie.jouer((byte) 2);
        Partie partie2 = new Partie();
        partie2.jouer((byte) 19);
        partie2.jouer((byte) 63);
        partie2.jouer((byte) 33);
        partie2.jouer((byte) 63);
        partie2.jouer((byte) 33);
        partie2.jouer((byte) 12);
        partie2.jouer((byte) 11);
        partie2.jouer((byte) 10);
        partie2.jouer((byte) 47);
        partie2.jouer((byte) 48);
        partie2.jouer((byte) 26);
        partie2.jouer((byte) 29);
        partie2.jouer((byte) 2);
        assertTrue(Arrays.equals(partie.getSignature(), partie2.getSignature()));
    }

    public void testGetLowestSignature() {
        Partie partie1 = new Partie();
        Partie partie2 = new Partie();
        partie1.jouer((byte) 0);
        partie2.jouer((byte) 63);
        assertTrue(Arrays.equals(partie1.getLowestSignature(), partie2.getLowestSignature()));
        partie1.jouer((byte) 3);
        partie2.jouer((byte) 15);
        assertTrue(Arrays.equals(partie1.getLowestSignature(), partie2.getLowestSignature()));
        partie1.jouer((byte) 2);
        partie2.jouer((byte) 60);
        byte[] tab1 = partie1.getLowestSignature();
        byte[] tab2 = partie2.getLowestSignature();
        assertFalse(Arrays.equals(tab1, tab2));
    }

    public void testConstructeurSignature() {
        Partie partie1 = new Partie();
        partie1.jouer((byte) 10);
        partie1.jouer((byte) 20);
        partie1.jouer((byte) 13);
        partie1.jouer((byte) 63);
        partie1.jouer((byte) 3);
        partie1.jouer((byte) 1);
        byte[] signature = partie1.getSignature();
        Partie partie2 = new Partie(signature, false);
        assertTrue(Arrays.equals(new byte[]{3,1,10,20,13,63}, partie2.getAnneauxJoues()));

        Partie partie3 = new Partie();
        partie3.restart(true);
        partie3.jouer((byte) 18);
        partie3.jouer((byte) 11);
        partie3.jouer((byte) 3);
        partie3.jouer((byte) 53);
        partie3.jouer((byte) 45);
        partie3.jouer((byte) 12);
        signature = partie3.getSignature();
        Partie partie4 = new Partie(signature, true);
        assertTrue(Arrays.equals(new byte[]{3,11,18,12,45,53}, partie4.getAnneauxJoues()));
    }
}