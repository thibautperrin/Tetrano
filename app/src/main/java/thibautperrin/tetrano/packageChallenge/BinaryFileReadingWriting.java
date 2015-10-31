package thibautperrin.tetrano.packageChallenge;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

/**
 * Created by Thibaut PERRIN on 09/10/2015.
 */
public final class BinaryFileReadingWriting {

    public static int readInt(FileInputStream fis) throws IOException {
        int byte1 = fis.read();
        int byte2 = fis.read();
        int byte3 = fis.read();
        int byte4 = fis.read();

        int number = (byte1) + 256 * (byte2) + 65536 * (byte3) + 16777216 * (byte4 % 128);
        if (byte4 >= 128) number = -number;
        return number;
    }

    public static void writeInt(FileOutputStream fos, int number) throws IOException {
        boolean negatif = number < 0;
        int nb = (negatif ? -number : number);
        int octet1 = nb % 256;
        int octet2 = (nb / 256) % 256;
        int octet3 = (nb / 65536) % 256;
        int octet4 = (nb / 16777216);
        if (negatif) octet4 += 128;
        fos.write(octet1);
        fos.write(octet2);
        fos.write(octet3);
        fos.write(octet4);
    }

    public static boolean readBool(FileInputStream fis) throws IOException {
        int res = fis.read();
        if (res == 1) {
            return true;
        } else if (res == 0) {
            return false;
        } else {
            throw new IOException("Problem in readBool: read value different from 0 and 1.");
        }
    }

    public static void writeBool(FileOutputStream fos, boolean bool) throws IOException {
        fos.write(bool ? 1 : 0);
    }

    /**
     * This method allows to store a byte array of less than 255 bytes.
     * There is no difference between an array of 0 bytes and the array null.
     * So, when we will read this data, an array of 0 bytes will be the null array
     * by convention.
     * @param bytes : null or a byte array of 0 to 255 bytes.
     * @throws IOException
     */
    public static void writeByteArray(FileOutputStream fos, byte[] bytes) throws IOException {
        if (bytes == null) {
            fos.write(0);
            return;
        }
        int length = bytes.length;
        if (length > 255) {
            throw new IOException("This method is not designed for arrays of more than 255 bytes.");
        }
        fos.write(length);
        fos.write(bytes);
    }

    /**
     * This method allows to read a byte array of less than 255 bytes.
     * If the array stored contained 0 bytes, this method doesn't see the difference
     * with a null array and return null.
     * @return null or an array of 1 to 255 bytes.
     * @throws IOException
     */
    public static byte[] readByteArray(FileInputStream fis) throws IOException {
        int length = fis.read();
        if (length == 0) return null;
        byte[] bytes = new byte[length];
        int res = fis.read(bytes, 0, length);
        if (res == -1) {
            throw new IOException("Byte array is not complete in the file.");
        }
        return bytes;
    }


    public static byte readByte(FileInputStream fis) throws IOException {
        int res = fis.read();
        return (byte) res;
    }

    public static void writeByte(FileOutputStream fos, byte b) throws IOException {
        fos.write(b);
    }
}
