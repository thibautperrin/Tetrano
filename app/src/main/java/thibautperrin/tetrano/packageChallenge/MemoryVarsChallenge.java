package thibautperrin.tetrano.packageChallenge;

import android.content.Context;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Random;

import thibautperrin.tetrano.ApplicationTetrano;
import thibautperrin.tetrano.Intelligence2.AnoAlig;
import thibautperrin.tetrano.R;
import thibautperrin.tetrano.StaticFields;

/**
 * Created by Thibaut PERRIN on 09/10/2015.
 */
public class MemoryVarsChallenge {

    private static MemoryVarsChallenge instance; // The only one instance.

    private Random rand = new Random();
    private final ApplicationTetrano app;
    private byte[] dataChallenge;

    // The 6 vars stored in memory:
    private int levelChallenge;
    private int numberStars;
    private byte numberSymetrieChallenge;
    private boolean nextTimeAdvert;
    private byte[] normallyPlayedChallenge;
    private byte[] playedByStarsChallenge;

    /**
     * Private constructeur: we can't create a MemoryVarsChallenge from outside, without
     * using getMemoryVarsChallenge.
     */
    private MemoryVarsChallenge(ApplicationTetrano app) {
        this.app = app;
    }

    /**
     * This method return the instance of MemoryVarsChallenge if it has already been created.
     * Else it create the instance, read the datas  (the raw file datachallenge and the 6 vars).
     * It take care of solving issues if there are.
     *
     * @return the only one instance of MemoryVarsChallenge
     */
    public static synchronized MemoryVarsChallenge getMemoryVarsChallenge(ApplicationTetrano app) {
        if (instance != null) {
            return instance;
        } else {
            instance = new MemoryVarsChallenge(app);
            instance.readDataChallenge();
            instance.readVars();
            return instance;
        }
    }

    private void readDataChallenge() {
        dataChallenge = new byte[StaticFields.NUMBER_BYTES_CHALLENGE_FILE];
        InputStream inputStream = app.getResources().openRawResource(R.raw.challenge);
        try {
            int res = inputStream.read(dataChallenge, 0, StaticFields.NUMBER_BYTES_CHALLENGE_FILE);
            inputStream.close();
            if (res != StaticFields.NUMBER_BYTES_CHALLENGE_FILE) {
                throw new IOException("Incomplete reading of challenge.bin");
            }
        } catch (IOException e) {
            try {
                inputStream.close();
            } catch (IOException e1) {
                throw new RuntimeException(e1);
            }
            throw new RuntimeException("Problem in reading the file challenge.bin");
        }

    }

    private void readVars() {
        FileInputStream fis;
        try {
            fis = app.openFileInput(StaticFields.FILE_CHALLENGE_VARS);
        } catch (FileNotFoundException e) {
            instance.setDefaultValue();
            return;
        }
        try {
            instance.levelChallenge = BinaryFileReadingWriting.readInt(fis);
            instance.numberStars = BinaryFileReadingWriting.readInt(fis);
            instance.numberSymetrieChallenge = BinaryFileReadingWriting.readByte(fis);
            instance.nextTimeAdvert = BinaryFileReadingWriting.readBool(fis);
            instance.normallyPlayedChallenge = BinaryFileReadingWriting.readByteArray(fis);
            instance.playedByStarsChallenge = BinaryFileReadingWriting.readByteArray(fis);
            fis.close();
            instance.verifyCorrectAndSaveData();
        } catch (IOException e) {
            if (fis != null) {
                try {
                    fis.close();
                } catch (IOException ignored) {
                }
            }
            instance.verifyCorrectAndSaveData();
            throw new RuntimeException(e);
        }
    }

    private void setDefaultValue() {
        levelChallenge = 1;
        numberStars = 3;
        numberSymetrieChallenge = (byte) rand.nextInt(48);
        nextTimeAdvert = false;
        normallyPlayedChallenge = null;
        playedByStarsChallenge = null;
    }


    private void verifyCorrectAndSaveData() {
        if (levelChallenge < 1
                || levelChallenge > StaticFields.NUMBER_CHALLENGE_LEVELS + 1) {
            // +1 for the situation where the player passed the last level.
            setDefaultValue();
            save();
            throw new RuntimeException("Problem with a level challenge not in the interval level");
        }
        if (numberStars < 0) {
            numberStars = 3;
            numberSymetrieChallenge = (byte) rand.nextInt(48);
            nextTimeAdvert = false;
            normallyPlayedChallenge = null;
            playedByStarsChallenge = null;
            save();
            throw new RuntimeException("Problem with a numberStars negative");
        }
        if (numberSymetrieChallenge < 0
                || numberSymetrieChallenge > 47) {
            numberSymetrieChallenge = (byte) rand.nextInt(48);
            nextTimeAdvert = false;
            normallyPlayedChallenge = null;
            playedByStarsChallenge = null;
            save();
            throw new RuntimeException("Problem with a numberSymetrieChallenge not in the interval");
        }
        int totalRings = 0;
        boolean correct = true;
        if (normallyPlayedChallenge != null) {
            if (normallyPlayedChallenge.length % 2 != 0) {
                correct = false;
            }
            totalRings += normallyPlayedChallenge.length;
            for (byte ring : normallyPlayedChallenge) {
                if (ring < 0 || ring > 63) {
                    correct = false;
                }
            }
        }
        if (playedByStarsChallenge != null) {
            if (playedByStarsChallenge.length % 2 != 0) {
                correct = false;
            }
            totalRings += playedByStarsChallenge.length;
            try {
                int forkOrder = getForkOrder();
                if (totalRings > forkOrder * 2 + 2) {
                    correct = false;
                }
            } catch (ChallengeModeFinishedException ignored) {
            }
            for (byte ring : playedByStarsChallenge) {
                if (ring < 0 || ring > 63) {
                    correct = false;
                }
            }
        }
        if (correct && levelChallenge <= StaticFields.NUMBER_CHALLENGE_LEVELS) {
            try {
                AnoAlig anoAlig = new AnoAlig(getSignature(), numberSymetrieChallenge, isInvertPlayer());
                byte player = 1;
                if (playedByStarsChallenge != null) {
                    for (byte ring : playedByStarsChallenge) {
                        if (anoAlig.anneaux[ring] == 0) {
                            anoAlig.jouer(ring, player);
                            if (anoAlig.ensembles[4].length() != 0 ||
                                    anoAlig.ensembles[8].length() != 0) {
                                correct = false;
                                break;
                            }
                        } else {
                            correct = false;
                            break;
                        }
                        player = (byte) (3 - player);
                    }
                }
                if (correct && normallyPlayedChallenge != null) {
                    for (byte ring : normallyPlayedChallenge) {
                        if (anoAlig.anneaux[ring] == 0) {
                            anoAlig.jouer(ring, player);
                            if (anoAlig.ensembles[4].length() != 0 ||
                                    anoAlig.ensembles[8].length() != 0) {
                                correct = false;
                                break;
                            }
                        } else {
                            correct = false;
                            break;
                        }
                        player = (byte) (3 - player);
                    }
                }
            } catch (ChallengeModeFinishedException ignored) {
            }
        }
        if (!correct) {
            normallyPlayedChallenge = null;
            playedByStarsChallenge = null;
            save();
            throw new RuntimeException("Problem with one of the 2 tabs.");
        }
    }

    private synchronized void save() {
        FileOutputStream fos;
        try {
            fos = app.openFileOutput(StaticFields.FILE_CHALLENGE_VARS, Context.MODE_PRIVATE);
        } catch (FileNotFoundException e) {
            throw new RuntimeException(e);
        }
        try {
            BinaryFileReadingWriting.writeInt(fos, levelChallenge);
            BinaryFileReadingWriting.writeInt(fos, numberStars);
            BinaryFileReadingWriting.writeByte(fos, numberSymetrieChallenge);
            BinaryFileReadingWriting.writeBool(fos, nextTimeAdvert);
            BinaryFileReadingWriting.writeByteArray(fos, normallyPlayedChallenge);
            BinaryFileReadingWriting.writeByteArray(fos, playedByStarsChallenge);
            fos.close();
        } catch (IOException e) {
            if (fos != null) {
                try {
                    fos.close();
                } catch (IOException e1) {
                    throw new RuntimeException(e1);
                }
            }
            throw new RuntimeException(e);
        }
    }

    /**
     * @return a number between -1 and saying 10 (Today at least).
     * @throws ChallengeModeFinishedException
     */
    private int getForkOrder() throws ChallengeModeFinishedException {
        if (levelChallenge == StaticFields.NUMBER_CHALLENGE_LEVELS + 1) {
            throw new ChallengeModeFinishedException();
        }
        int lastByte = dataChallenge[levelChallenge * 14 - 1];
        return lastByte / 2 - 1;
    }

    /**
     * This method indicate if the user used to begin the game, at the very beginning.
     * Of course, in the challenge mode, the user will begin, but it is not necessary the
     * user who began the game from the first ring.
     *
     * @return true if we invert the player, meaning the user wasn't the player one and now he is.
     * Thus, we return true if the computer began the game, false otherwise.
     * @throws ChallengeModeFinishedException : if the user passed all the levels.
     */
    private boolean isInvertPlayer() throws ChallengeModeFinishedException {
        if (levelChallenge == StaticFields.NUMBER_CHALLENGE_LEVELS + 1) {
            throw new ChallengeModeFinishedException();
        }
        int lastByte = dataChallenge[levelChallenge * 14 - 1];
        return lastByte % 2 == 0;
    }

    private byte[] getSignature() throws ChallengeModeFinishedException {
        if (levelChallenge == StaticFields.NUMBER_CHALLENGE_LEVELS + 1) {
            throw new ChallengeModeFinishedException();
        }
        byte[] sign = new byte[13];
        System.arraycopy(dataChallenge, (levelChallenge - 1) * 14, sign, 0, 13);
        return sign;
    }

    /**
     * @return the number of stars available for getting a clue in challenge mode.
     */
    public int getNumberStars() {
        return numberStars;
    }

    /**
     * @return the level of the current challenge (between 1 and 245)
     */
    public int getLevelChallenge() {
        return levelChallenge;
    }


    /**
     * Increase the number of stars by 1.
     */
    public void incrementsNumberStars() {
        if (numberStars < 0) {
            verifyCorrectAndSaveData();
            return;
        }
        numberStars++;
        save();
    }

    /**
     * If the number of stars is >= 1, this method decrease the number of stars by 1
     * and return true. Otherwise, this method just return false.
     */
    public boolean useAStar() {
        if (numberStars < 0) {
            verifyCorrectAndSaveData();
            throw new RuntimeException("Problem with numberStar (this exception should" +
                    " never be thrown, because another should be thrown just before...)");
        } else if (numberStars == 0) {
            return false;
        } else {
            numberStars--;
            save();
            return true;
        }
    }

    /**
     * This method adjust all the challenge variables, such as the boolean for advert
     * and the symmetry number, depending on the parameter.
     *
     * @param passed if the boolean passed is true, we consider that the user passed this
     *               level. So, next time, he will go at the upper level. Otherwise, the
     *               user failed and next time, he will try again on this level.
     */
    public void nextLevelChallenge(boolean passed) {
        normallyPlayedChallenge = null;
        if (passed) {
            nextTimeAdvert = false;
            playedByStarsChallenge = null;
            levelChallenge++;
            numberSymetrieChallenge = (byte) rand.nextInt(48);
        }
        save();
    }

    /**
     * This method allows to save the 2 rings played by user and computer.
     *
     * @param ringUser     a byte between 0 and 63 corresponding to the user's ring number.
     * @param ringComputer a byte between 0 and 63 corresponding to the computer rings'number.
     */
    public void playTwoRingsChallenge(byte ringUser, byte ringComputer) {
        if (normallyPlayedChallenge == null) {
            normallyPlayedChallenge = new byte[2];
            normallyPlayedChallenge[0] = ringUser;
            normallyPlayedChallenge[1] = ringComputer;
        } else {
            int size = normallyPlayedChallenge.length;
            byte[] newArray = new byte[size + 2];
            System.arraycopy(normallyPlayedChallenge, 0, newArray, 0, size);
            newArray[size] = ringUser;
            newArray[size + 1] = ringComputer;
            normallyPlayedChallenge = newArray;
        }
        save();
    }

    /**
     * This method allow to save the 2 rings played automatically when the user consumes
     * a star. In that way, even if the user failed the level, the clue obtained by the star
     * will remained.
     *
     * @param ringUser     a byte between 0 and 63 corresponding to the user's ring number.
     * @param ringComputer a byte between 0 and 63 corresponding to the computer rings'number.
     */
    public void playTwoRingsFromStar(byte ringUser, byte ringComputer) {
        normallyPlayedChallenge = null;
        nextTimeAdvert = false;
        if (playedByStarsChallenge == null) {
            playedByStarsChallenge = new byte[2];
            playedByStarsChallenge[0] = ringUser;
            playedByStarsChallenge[1] = ringComputer;
        } else {
            int size = playedByStarsChallenge.length;
            byte[] newArray = new byte[size + 2];
            System.arraycopy(playedByStarsChallenge, 0, newArray, 0, size);
            newArray[size] = ringUser;
            newArray[size + 1] = ringComputer;
            playedByStarsChallenge = newArray;
        }
        save();
    }

    /**
     * This method look if an advert should be displayed.
     * An advert is displayed if the user failed in a level consecutively twice.
     * The value of the boolean is changed at each time this method is used.
     *
     * @return true if the advert should be displayed, false otherwise.
     */
    public boolean tryAdvert() {
        boolean res = nextTimeAdvert;
        nextTimeAdvert = !nextTimeAdvert;
        save();
        return res;
    }

    /**
     * This method is used when the user arrives in the challenge mode.
     * The object Challenge is then built from the vars saved in memory.
     *
     * @return the corresponding challenge object.
     */
    public Challenge getPartieChallenge() throws ChallengeModeFinishedException {
        byte[] signature = getSignature();
        boolean invertPlayer = isInvertPlayer();
        int forkOrder = getForkOrder();
        AnoAlig anoAlig = new AnoAlig(signature, numberSymetrieChallenge, invertPlayer);
        int nbStarRings = 0;
        if (playedByStarsChallenge != null) {
            byte player = 1;
            for (byte numRing : playedByStarsChallenge) {
                anoAlig.jouer(numRing, player);
                player = (byte) (3 - player);
            }
            nbStarRings = playedByStarsChallenge.length / 2;
        }
        return new Challenge(anoAlig, forkOrder, nbStarRings, normallyPlayedChallenge);
    }
}