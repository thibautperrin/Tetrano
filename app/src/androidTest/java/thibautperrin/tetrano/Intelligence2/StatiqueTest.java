package thibautperrin.tetrano.Intelligence2;

import junit.framework.TestCase;

import java.util.Arrays;

/**
 * Test de la classe Statique.
 * Ces tests supposent que tabAlignements est correctement rempli, et vérifient que les
 * deux méthodes fonctionnent.
 * Created by Thibaut PERRIN on 11/07/2015.
 */
public class StatiqueTest extends TestCase {

    public void testIsAnneauAppAlig() throws Exception {
        for (int ano = 0; ano < 64; ano++) {
            for (int alig = 0; alig < 76; alig++) {
                if (Statique.isAnneauAppAlig(ano, alig)) {
                    assertTrue(Statique.tabAlignements[alig][0] == ano ||
                            Statique.tabAlignements[alig][1] == ano ||
                            Statique.tabAlignements[alig][2] == ano ||
                            Statique.tabAlignements[alig][3] == ano);
                } else {
                    assertTrue(Statique.tabAlignements[alig][0] != ano);
                    assertTrue(Statique.tabAlignements[alig][1] != ano);
                    assertTrue(Statique.tabAlignements[alig][2] != ano);
                    assertTrue(Statique.tabAlignements[alig][3] != ano);
                }
            }
        }
    }

    public void testGetAlignements() throws Exception {
        for (int ano = 0; ano < 64; ano ++) {
            byte[] aligs = Statique.getAlignements(ano);
            assertTrue(aligs.length == 4 || aligs.length == 7);
            for (byte alig : aligs) {
                assertTrue(Statique.tabAlignements[alig][0] == ano ||
                        Statique.tabAlignements[alig][1] == ano ||
                        Statique.tabAlignements[alig][2] == ano ||
                        Statique.tabAlignements[alig][3] == ano);
            }
        }
        for (int alig = 0; alig < 76; alig ++) {
            for (int i = 0; i < 4; i ++) {
                int ano = Statique.tabAlignements[alig][i];
                byte[]aligs = Statique.getAlignements(ano);
                if (aligs.length == 4) {
                    System.out.println("alig: "+ alig+ " aligs: "+ Arrays.toString(aligs));
                    assertTrue(aligs[0] == alig ||
                            aligs[1] == alig ||
                            aligs[2] == alig ||
                            aligs[3] == alig);
                } else {
                    assertTrue(aligs.length == 7);
                    assertTrue(aligs[0] == alig ||
                            aligs[1] == alig ||
                            aligs[2] == alig ||
                            aligs[3] == alig ||
                            aligs[4] == alig ||
                            aligs[5] == alig ||
                            aligs[6] == alig);
                }
            }
        }
    }

    public void testGetAnneauSymetrique() {
        for (byte sym = 0; sym < 48; sym++) {
            for (byte ano = 0; ano < 64; ano++) {
                assertEquals(Statique.getAnneauSymetrieInverse(Statique.getAnneauSymetrique(ano, sym), sym), ano);
            }
        }
    }
}