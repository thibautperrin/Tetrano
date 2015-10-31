package thibautperrin.tetrano.packageChallenge;

import android.content.Context;
import android.test.AndroidTestCase;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.Arrays;
import java.util.Random;

/**
 * Created by Thibaut PERRIN on 09/10/2015.
 */
public class BinaryFileReadingWritingTest extends AndroidTestCase {

    public void testInt() throws Exception {
        FileOutputStream fos = getContext().openFileOutput("TestFile", Context.MODE_PRIVATE);
        int[] tab = new int[2000];
        Random random = new Random();
        for (int i=0; i < 2000; i++) {
            tab[i] = random.nextInt();
            BinaryFileReadingWriting.writeInt(fos, tab[i]);
        }
        fos.close();
        FileInputStream fis = getContext().openFileInput("TestFile");
        for (int i=0; i < 2000; i++) {
            assertEquals(BinaryFileReadingWriting.readInt(fis), tab[i]);
        }
        fis.close();
    }

    public void testBool() throws Exception {
        FileOutputStream fos = getContext().openFileOutput("TestFile2", Context.MODE_PRIVATE);
        BinaryFileReadingWriting.writeBool(fos, true);
        BinaryFileReadingWriting.writeBool(fos, false);
        fos.close();
        FileInputStream fis = getContext().openFileInput("TestFile2");
        assertTrue(BinaryFileReadingWriting.readBool(fis));
        assertFalse(BinaryFileReadingWriting.readBool(fis));
        fis.close();
    }

    public void testByteArray() throws Exception {
        FileOutputStream fos = getContext().openFileOutput("TestFile3", Context.MODE_PRIVATE);
        byte[] tab1 = new byte[200];
        byte[] tab2 = new byte[56];
        for (int i=0; i < 200; i++) {
            tab1[i] = (byte) i;
        }
        for (int i = 0; i<56; i++) {
            tab2[i] = (byte) (200+i);
            assertTrue(tab2[i] < 0);
        }
        BinaryFileReadingWriting.writeByteArray(fos, tab1);
        BinaryFileReadingWriting.writeByteArray(fos, null);
        BinaryFileReadingWriting.writeByteArray(fos, tab2);
        fos.close();
        FileInputStream fis = getContext().openFileInput("TestFile3");
        assertTrue(Arrays.equals(BinaryFileReadingWriting.readByteArray(fis), tab1));
        assertNull(BinaryFileReadingWriting.readByteArray(fis));
        assertTrue(Arrays.equals(BinaryFileReadingWriting.readByteArray(fis),tab2));
        fis.close();
    }

    public void testByte() throws Exception {
        FileOutputStream fos = getContext().openFileOutput("TestFile4", Context.MODE_PRIVATE);
        byte[] tab = new byte[256];
        for (int i=0; i < 256; i++) {
            tab[i] = (byte) i;
            BinaryFileReadingWriting.writeByte(fos, tab[i]);
        }
        fos.close();
        FileInputStream fis = getContext().openFileInput("TestFile4");
        for (int i=0; i < 256; i++) {
            assertEquals(BinaryFileReadingWriting.readByte(fis), tab[i]);
        }
        fis.close();
    }
}