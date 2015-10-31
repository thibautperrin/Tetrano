package thibautperrin.tetrano.packageSavedGame;

/**
 * Created by Thibaut PERRIN on 25/09/2015.
 */
public class SavedGame {
    private String gameName;
    private byte[] ringsPlayed;
    private boolean isUserBegin;
    private int userColor;
    private int adversaryColor;


    public SavedGame(String gameName, byte[] ringsPlayed, boolean isUserBegin,
                     int userColor, int adversaryColor) {
        this.isUserBegin = isUserBegin;
        this.userColor = userColor;
        this.adversaryColor = adversaryColor;
        if (gameName.length() > 100) {
            this.gameName = gameName.substring(0, 100);
        } else {
            this.gameName = gameName;
        }
        int ringsNumber = ringsPlayed.length;
        this.ringsPlayed = new byte[ringsNumber];
        System.arraycopy(ringsPlayed, 0, this.ringsPlayed, 0, ringsNumber);
    }

    public String getGameName() {
        return gameName;
    }

    public byte[] getRingsPlayed() {
        return ringsPlayed;
    }

    public boolean isUserBegin() {
        return isUserBegin;
    }

    public int getUserColor() {
        return userColor;
    }

    public int getAdversaryColor() {
        return adversaryColor;
    }

    public void rename(String newName) {
        this.gameName = newName;
    }

    public byte[] getMatrix() {
        byte[] matrix = new byte[64];
        byte player = (byte) (isUserBegin ? 1 : 2);
        for (byte b : ringsPlayed) {
            matrix[b] = player;
            player = (byte) (3 - player);
        }
        return matrix;
    }
}