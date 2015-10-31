package thibautperrin.tetrano.packageGestionJeu;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Resources;

import java.util.Arrays;

import thibautperrin.tetrano.ApplicationTetrano;
import thibautperrin.tetrano.R;
import thibautperrin.tetrano.StaticFields;

/**
 * Created by Thibaut PERRIN on 09/10/2015.
 */
public class GameLog {

    private final ApplicationTetrano app;
    private final Resources res;
    private SharedPreferences vars;
    public int nbGames2P;
    public int nbGamesComp;
    public int nbGamesWonComp;
    public int nbGamesOnline;
    public int nbGamesBlt;
    public static final int tab[] = {5,10,20,50,100,200,500,1000};

    public GameLog(ApplicationTetrano app) {
        this.app = app;
        this.res = app.getResources();
    }

    public void addGame(int gameType, Boolean won, Context activityContext) {
        if (vars == null) {
            getVars();
        }
        SharedPreferences.Editor editor = vars.edit();
        switch (gameType) {
            case StaticFields.GAME_2_PLAYERS:
                nbGames2P++;
                editor.putInt(StaticFields.LABEL_NUMBER_GAME_IN_2_PLAYERS_MODE, nbGames2P);
                if (Arrays.binarySearch(tab, nbGames2P) >= 0) {
                    app.incrementsNumberStars();
                    app.displayMessageStars(activityContext, res.getString(R.string.Star_Won_Two_Players, nbGames2P));
                }
                if (nbGames2P == 1) {
                    testAllModeTested(activityContext);
                }
                break;
            case StaticFields.GAME_AGAINST_COMPUTER:
                nbGamesComp++;
                editor.putInt(StaticFields.LABEL_NUMBER_GAMES_AGAINST_COMPUTER, nbGamesComp);
                if (Arrays.binarySearch(tab, nbGamesComp) >= 0) {
                    app.incrementsNumberStars();
                    app.displayMessageStars(activityContext, res.getString(R.string.Star_Won_Against_Computer, nbGamesComp));

                }
                if (nbGamesComp == 1) {
                    testAllModeTested(activityContext);
                }
                if (won) {
                    nbGamesWonComp++;
                    editor.putInt(StaticFields.LABEL_NUMBER_GAME_WINNED_AGAINST_COMPUTER, nbGamesWonComp);
                    if (Arrays.binarySearch(tab, nbGamesWonComp) >= 0) {
                        app.incrementsNumberStars();
                        app.displayMessageStars(activityContext, res.getString(R.string.Star_Won_Win_Against_Computer, nbGamesWonComp));
                    }
                }
                break;
            case StaticFields.GAME_BLUETOOTH:
                nbGamesBlt++;
                editor.putInt(StaticFields.LABEL_NUMBER_GAMES_BLUETOOTH, nbGamesBlt);
                if (nbGamesBlt == 1) {
                    testAllModeTested(activityContext);
                }
                if (Arrays.binarySearch(tab, nbGamesBlt) >= 0) {
                    app.incrementsNumberStars();
                    app.displayMessageStars(activityContext, res.getString(R.string.Star_Won_Bluetooth, nbGamesBlt));
                }
                break;
            case StaticFields.GAME_ONLINE:
                nbGamesOnline++;
                editor.putInt(StaticFields.LABEL_NUMBER_GAMES_ONLINE, nbGamesOnline);
                if (nbGamesOnline == 1) {
                    testAllModeTested(activityContext);
                }
                if (Arrays.binarySearch(tab, nbGamesOnline) >= 0) {
                    app.incrementsNumberStars();
                    app.displayMessageStars(activityContext, res.getString(R.string.Star_Won_Online, nbGamesOnline));
                }
                break;
        }
        editor.commit();

    }

    private void testAllModeTested(Context activityContext) {
        if (nbGames2P > 0 && nbGamesComp > 0 && nbGamesBlt > 0 && nbGamesOnline > 0) {
            app.incrementsNumberStars();
            app.displayMessageStars(activityContext, res.getString(R.string.Star_Won_All_Games));
        }
    }

    public void getVars() {
        vars = app.getSharedPreferences(StaticFields.GAME_LOG_FILE, Context.MODE_PRIVATE);
        nbGames2P = vars.getInt(StaticFields.LABEL_NUMBER_GAME_IN_2_PLAYERS_MODE, 0);
        nbGamesComp = vars.getInt(StaticFields.LABEL_NUMBER_GAMES_AGAINST_COMPUTER, 0);
        nbGamesWonComp = vars.getInt(StaticFields.LABEL_NUMBER_GAME_WINNED_AGAINST_COMPUTER, 0);
        nbGamesBlt = vars.getInt(StaticFields.LABEL_NUMBER_GAMES_BLUETOOTH,0);
        nbGamesOnline = vars.getInt(StaticFields.LABEL_NUMBER_GAMES_ONLINE,0);
    }
}
