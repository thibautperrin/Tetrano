package thibautperrin.tetrano.packageConnexions;

/**
 * Created by Thibaut PERRIN on 15/06/2015.
 */
public class GestionMessage {
    /**
     * Cette méthode existe déjà dans Arrays, mais elle n'est disponible qu'à partir de l'API 9.
     *
     * @param original the original array
     * @param start the start index, inclusive
     * @param end the end index, exclusive
     * @return the new array
     */
    protected static byte[] copyOfRange(byte[] original, int start, int end) {
        if (start < 0 || start > original.length) {
            throw new ArrayIndexOutOfBoundsException();
        }
        if (start > end) {
            throw new IllegalArgumentException();
        }
        byte[] nouveau = new byte[end - start + 1];
        System.arraycopy(original, start, nouveau, 0, nouveau.length);
        return nouveau;
    }

}
