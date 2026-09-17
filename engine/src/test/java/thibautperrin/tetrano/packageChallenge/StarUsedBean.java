package thibautperrin.tetrano.packageChallenge;

/**
 * Created by Thibaut PERRIN on 03/10/2015.
 */
public class StarUsedBean {
    private byte ringUser;
    private byte ringComputer;
    private byte[] matrix12;
    private boolean needRestart;
    private boolean isFinishedJustAfterStar;

    public byte getRingUser() {
        return ringUser;
    }

    public void setRingUser(byte ringUser) {
        this.ringUser = ringUser;
    }

    public byte getRingComputer() {
        return ringComputer;
    }

    public void setRingComputer(byte ringComputer) {
        this.ringComputer = ringComputer;
    }

    public byte[] getMatrix12() {
        return matrix12;
    }

    public void setMatrix12(byte[] matrix12) {
        this.matrix12 = matrix12;
    }

    public boolean isNeedRestart() {
        return needRestart;
    }

    public void setNeedRestart(boolean needRestart) {
        this.needRestart = needRestart;
    }

    public boolean isFinishedJustAfterStar() {
        return isFinishedJustAfterStar;
    }

    public void setIsFinishedJustAfterStar(boolean isFinishedJustAfterStar) {
        this.isFinishedJustAfterStar = isFinishedJustAfterStar;
    }
}
