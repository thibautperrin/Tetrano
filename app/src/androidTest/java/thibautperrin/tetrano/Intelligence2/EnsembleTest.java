package thibautperrin.tetrano.Intelligence2;

import junit.framework.TestCase;

/**
 * Created by Thibaut PERRIN on 11/07/2015.
 */
public class EnsembleTest extends TestCase {

    public void testEnsemble() {
        Ensemble ensemble = new Ensemble((byte) 76, false);
        assertEquals(0, ensemble.length());
        assertFalse(ensemble.contains((byte) 5));
        ensemble.add((byte) 5);
        assertEquals(1, ensemble.length());
        assertTrue(ensemble.contains((byte) 5));
        assertEquals(5, ensemble.next((byte) -1));
        assertEquals(-1, ensemble.next((byte) 5));
        ensemble.remove((byte) 5);
        assertEquals(0, ensemble.length());
        assertFalse(ensemble.contains((byte) 5));
        assertEquals(-1, ensemble.next((byte) -1));
        ensemble.add((byte) 5);
        assertEquals(1, ensemble.length());
        assertEquals(5, ensemble.next((byte) -1));
        assertEquals(-1, ensemble.next((byte) 5));
        ensemble.add((byte) 9);
        assertEquals(2, ensemble.length());
        assertTrue(ensemble.contains((byte) 9));
        assertEquals(9, ensemble.next((byte) -1));
        assertEquals(5, ensemble.next((byte) 9));
        assertEquals(-1, ensemble.next((byte) 5));
        ensemble.remove((byte) 9);
        assertEquals(1, ensemble.length());
        assertEquals(5, ensemble.next((byte) -1));
        assertEquals(-1, ensemble.next((byte) 5));
        ensemble.add((byte) 9);
        assertEquals(2, ensemble.length());
        assertEquals(9, ensemble.next((byte) -1));
        assertEquals(5, ensemble.next((byte) 9));
        assertEquals(-1, ensemble.next((byte) 5));
        ensemble.remove((byte) 5);
        assertEquals(1, ensemble.length());
        assertEquals(9, ensemble.next((byte) -1));
        assertEquals(-1, ensemble.next((byte) 9));
        ensemble.remove((byte) 9);
        assertEquals(0, ensemble.length());
        assertEquals(-1, ensemble.next((byte) -1));

        ensemble.add((byte) 1);
        ensemble.add((byte) 2);
        ensemble.add((byte) 3);
        ensemble.remove((byte) 2);
        assertEquals(3, ensemble.next((byte) -1));
        assertEquals(1, ensemble.next((byte) 3));
        assertEquals(-1, ensemble.next((byte) 1));
        assertEquals(2, ensemble.length());

        ensemble.clear();
        assertEquals(0, ensemble.length());
        assertEquals(-1, ensemble.next((byte) -1));
    }
}