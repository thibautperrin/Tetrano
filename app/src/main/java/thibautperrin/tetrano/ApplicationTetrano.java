package thibautperrin.tetrano;

import android.app.AlertDialog;
import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.style.ImageSpan;

import java.sql.SQLException;
import java.util.List;

import thibautperrin.tetrano.packageChallenge.MemoryVarsChallenge;
import thibautperrin.tetrano.packageConnexions.packageBluetooth.BluetoothHandler;
import thibautperrin.tetrano.packageConnexions.packageBluetooth.BluetoothService;
import thibautperrin.tetrano.packageConnexions.packageBluetooth.DAODevice;
import thibautperrin.tetrano.packageConnexions.packageBluetooth.Device;
import thibautperrin.tetrano.packageGestionJeu.GameLog;
import thibautperrin.tetrano.packageSavedGame.DAOSavedGame;

/**
 * This class is instantiated at the start of Tetrano.
 * The instance can be accessed via the getApplication method.
 * This class behave as a memory cache. The data are retrieved when needed.
 * When we modify the data, we save the changes in the memory.
 * Created by Thibaut PERRIN on 13/06/2015.
 */
public class ApplicationTetrano extends Application {

    // Fields:
    private BluetoothHandler bluetoothHandler;
    private BluetoothService bluetoothService;
    private SharedPreferences preferences;
    private DAOSavedGame openedDAOSavedGame;
    private DAODevice daoDevice;
    private GameLog gameLog;
    private int couleurJoueur1;
    private int couleurJoueur2;
    private int couleurUtilisateur;
    private int couleurAdversaire;
    private int niveauJeu;

    private String nomJoueur1;
    private String nomJoueur2;
    private String nomUtilisateur;
    private ImageSpan star;

    // Methods Concerning the variables saved in the application:
    private void chargeLesReglages() {
        Resources res = getResources();
        preferences = getSharedPreferences(StaticFields.FILE_PREFERENCES, Context.MODE_PRIVATE);
        couleurJoueur1 = preferences.getInt(StaticFields.LABEL_PLAYER1_COLOR, 0xffff0000);
        couleurJoueur2 = preferences.getInt(StaticFields.LABEL_PLAYER2_COLOR, 0xff0000ff);
        couleurUtilisateur = preferences.getInt(StaticFields.LABEL_USER_COLOR, 0xffff0000);
        couleurAdversaire = preferences.getInt(StaticFields.LABEL_ADVERSARY_COLOR, 0xff0000ff);
        nomJoueur1 = preferences.getString(StaticFields.LABEL_PLAYER1_NAME, res.getString(R.string.Joueur1));
        nomJoueur2 = preferences.getString(StaticFields.LABEL_PLAYER2_NAME, res.getString(R.string.Joueur2));
        nomUtilisateur = preferences.getString(StaticFields.LABEL_USER_NAME, res.getString(R.string.User));
        niveauJeu = preferences.getInt(StaticFields.LABEL_LEVEL, 10);
    }

    public GameLog getGameLog() {
        if (gameLog == null) {
            gameLog = new GameLog(this);
        }
        return gameLog;
    }

    public int getCouleurJoueur1() {
        if (preferences == null) chargeLesReglages();
        return couleurJoueur1;
    }

    public int getCouleurJoueur2() {
        if (preferences == null) chargeLesReglages();
        return couleurJoueur2;
    }

    public int getCouleurUtilisateur() {
        if (preferences == null) chargeLesReglages();
        return couleurUtilisateur;
    }

    public int getCouleurAdversaire() {
        if (preferences == null) chargeLesReglages();
        return couleurAdversaire;
    }

    public String getNomJoueur1() {
        if (preferences == null) chargeLesReglages();
        return nomJoueur1;
    }

    public String getNomJoueur2() {
        if (preferences == null) chargeLesReglages();
        return nomJoueur2;
    }

    public String getNomUtilisateur() {
        if (preferences == null) chargeLesReglages();
        return nomUtilisateur;
    }

    public int getNiveauJeu() {
        if (preferences == null) chargeLesReglages();
        return niveauJeu;
    }

    public void setCouleurJoueur1(int couleurJoueur1) {
        if (preferences == null) chargeLesReglages();
        if (this.couleurJoueur1 != couleurJoueur1) {
            this.couleurJoueur1 = couleurJoueur1;
            SharedPreferences.Editor editor = preferences.edit();
            editor.putInt(StaticFields.LABEL_PLAYER1_COLOR, couleurJoueur1);
            editor.commit();
        }
    }

    public void setCouleurJoueur2(int couleurJoueur2) {
        if (preferences == null) chargeLesReglages();
        if (this.couleurJoueur2 != couleurJoueur2) {
            this.couleurJoueur2 = couleurJoueur2;
            SharedPreferences.Editor editor = preferences.edit();
            editor.putInt(StaticFields.LABEL_PLAYER2_COLOR, couleurJoueur2);
            editor.commit();
        }
    }

    public void setCouleurUtilisateur(int couleurUtilisateur) {
        if (preferences == null) chargeLesReglages();
        if (this.couleurUtilisateur != couleurUtilisateur) {
            this.couleurUtilisateur = couleurUtilisateur;
            SharedPreferences.Editor editor = preferences.edit();
            editor.putInt(StaticFields.LABEL_USER_COLOR, couleurUtilisateur);
            editor.commit();
        }
    }

    public void setCouleurAdversaire(int couleurAdversaire) {
        if (preferences == null) chargeLesReglages();
        if (this.couleurAdversaire != couleurAdversaire) {
            this.couleurAdversaire = couleurAdversaire;
            SharedPreferences.Editor editor = preferences.edit();
            editor.putInt(StaticFields.LABEL_ADVERSARY_COLOR, couleurAdversaire);
            editor.commit();
        }
    }

    public void setNomJoueur1(String nomJoueur1) {
        if (preferences == null) chargeLesReglages();
        if (!(this.nomJoueur1).equals(nomJoueur1)) {
            this.nomJoueur1 = nomJoueur1;
            SharedPreferences.Editor editor = preferences.edit();
            editor.putString(StaticFields.LABEL_PLAYER1_NAME, nomJoueur1);
            editor.commit();
        }
    }

    public void setNomJoueur2(String nomJoueur2) {
        if (preferences == null) chargeLesReglages();
        if (!(this.nomJoueur2).equals(nomJoueur2)) {
            this.nomJoueur2 = nomJoueur2;
            SharedPreferences.Editor editor = preferences.edit();
            editor.putString(StaticFields.LABEL_PLAYER2_NAME, nomJoueur2);
            editor.commit();
        }
    }

    public void setNomUtilisateur(String nomUtilisateur) {
        if (preferences == null) chargeLesReglages();
        if (!(this.nomUtilisateur).equals(nomUtilisateur)) {
            this.nomUtilisateur = nomUtilisateur;
            SharedPreferences.Editor editor = preferences.edit();
            editor.putString(StaticFields.LABEL_USER_NAME, nomUtilisateur);
            editor.commit();
        }
    }

    public void setNiveauJeu(int niveauJeu) {
        if (preferences == null) chargeLesReglages();
        if (this.niveauJeu != niveauJeu) {
            this.niveauJeu = niveauJeu;
            SharedPreferences.Editor editor = preferences.edit();
            editor.putInt(StaticFields.LABEL_LEVEL, niveauJeu);
            editor.commit();
        }
    }

    // Methods Concerning the Bluetooth:
    public void setBluetoothService(BluetoothService bluetoothService, BluetoothHandler bluetoothHandler) {
        this.bluetoothHandler = bluetoothHandler;
        this.bluetoothService = bluetoothService;
    }

    public BluetoothHandler getBluetoothHandler() {
        return bluetoothHandler;
    }

    public BluetoothService getBluetoothService() {
        return bluetoothService;
    }

    /**
     * Cette méthode permet d'ajouter une adresse MAC à la liste des addresses MAC
     * des périphériques contre lesquelles on a déjà joué en Bluetooth.
     * Cette méthode doit être utilisée uniquement une fois que l'on est sur que le
     * périphérique est capable de jouer au Tetrano.
     *
     * @param macAdress : Une String correspondant à une adresse MAC sous la forme:
     *                  "00:11:22:AA:BB:CC"
     */
    public void addMacAdressBluetooth(Context context, String macAdress, String name) {
        if (daoDevice == null) {
            daoDevice = new DAODevice(this);
            daoDevice.open();
        }
        boolean isNewOne = daoDevice.addRemoteBluetoothDevice(macAdress, name);
        if (isNewOne) {
            incrementsNumberStars();
            displayMessageStars(context, getResources().getString(R.string.New_Star_Bluetooth));
        }
    }

    public List<Device> getMacAdressBluetooth() {
        if (daoDevice == null) {
            daoDevice = new DAODevice(this);
            daoDevice.open();
        }
        return daoDevice.getRemoteBluetoothDevices();
    }

    public DAOSavedGame getOpenedDAOSavedGame() {
        if (openedDAOSavedGame == null) {
            openedDAOSavedGame = new DAOSavedGame(this);
            try {
                openedDAOSavedGame.open();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
        return openedDAOSavedGame;
    }


    public SpannableStringBuilder getSpannableForTitle() {
        if (star == null) {
            Bitmap bmp = BitmapFactory.decodeResource(getResources(), R.drawable.star);
            star = new ImageSpan(this, bmp);
        }
        int numberStars = MemoryVarsChallenge.getMemoryVarsChallenge(this).getNumberStars();
        SpannableStringBuilder ssb = new SpannableStringBuilder(numberStars + "     ");
        ssb.setSpan(star, 5, 6, Spannable.SPAN_INCLUSIVE_INCLUSIVE);
        return ssb;
    }


    public void displayMessageStars(Context activityContext, String msg) {
        AlertDialog.Builder builder = new AlertDialog.Builder(activityContext);
        builder.setTitle(getSpannableForTitle());
        builder.setCancelable(true);
        builder.setMessage(msg + "\n\n" + getResources().getString(R.string.Explanation_Star));
        AlertDialog dialog = builder.create();
        dialog.show();
    }

    public void incrementsNumberStars() {
        MemoryVarsChallenge.getMemoryVarsChallenge(this).incrementsNumberStars();
    }
}