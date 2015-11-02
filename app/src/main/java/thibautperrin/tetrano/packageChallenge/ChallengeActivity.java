package thibautperrin.tetrano.packageChallenge;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.os.Bundle;
import android.os.Handler;
import android.support.v7.app.AppCompatActivity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.purplebrain.adbuddiz.sdk.AdBuddiz;
import com.purplebrain.adbuddiz.sdk.AdBuddizDelegate;
import com.purplebrain.adbuddiz.sdk.AdBuddizError;

import thibautperrin.tetrano.ApplicationTetrano;
import thibautperrin.tetrano.R;
import thibautperrin.tetrano.SettingsActivity;
import thibautperrin.tetrano.StaticFields;
import thibautperrin.tetrano.packageGestionJeu.InterfaceGameManagement;
import thibautperrin.tetrano.packageSurfaceGraphView.SurfaceGraphView;

/**
 * Created by Thibaut PERRIN on 15/08/2015.
 * This Activity take care of the game in challenge mode.
 */
public class ChallengeActivity extends AppCompatActivity implements InterfaceGameManagement {

    private static final byte SITUATION_USER_WINS = 0;
    private static final byte SITUATION_COMPUTER_WINS = 1;
    private static final byte SITUATION_RINGS_NUMBER_PASSED = 2;
    private static final byte SITUATION_ABANDONED = 3;

    private SurfaceGraphView surfaceGraphView;
    private TextView textView;
    private View buttonAbandon;
    private int colorUser;
    private int colorComputer;
    private ApplicationTetrano app;
    private Challenge challenge;
    private AlertDialog dialogEnd;
    private AlertDialog dialogNbRings;
    private AlertDialog dialogExplanations1;
    private AlertDialog dialogExplanations2;
    private final Handler handler = new Handler();
    private Resources res;
    private AlertDialog dialogConfirmStars;
    private MemoryVarsChallenge memVars;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_challenge);
        textView = (TextView) findViewById(R.id.textViewRingsUsed2);
        res = getResources();
        app = (ApplicationTetrano) getApplication();
        memVars = MemoryVarsChallenge.getMemoryVarsChallenge(app);
        buttonAbandon = findViewById(R.id.buttonRestartChallenge);
        buttonAbandon.setOnClickListener(onClickListener);
        surfaceGraphView = (SurfaceGraphView) findViewById(R.id.surfaceGraphViewChallenge);
        surfaceGraphView.setGameManagement(this);
        colorUser = app.getCouleurUtilisateur();
        colorComputer = app.getCouleurAdversaire();
        try {
            challenge = memVars.getPartieChallenge();
            updateTitle();
            updateTextAndButton();
            if (memVars.getLevelChallenge() == 1) {
                displayExplanations(true);
            }
            doAdBudizStuff();
        } catch (ChallengeModeFinishedException e) {
            endDueToNoMoreLevels();
        }
    }

    private void endDueToNoMoreLevels() {
        Toast.makeText(this, res.getString(R.string.All_Levels_Passed), Toast.LENGTH_LONG).show();
        finish();
    }

    private void doAdBudizStuff() {
        // OPTIONAL, to get more info about the SDK behavior
        AdBuddiz.setDelegate(new AdBuddizDelegate() {
            @Override
            public void didCacheAd() {
            }

            @Override
            public void didShowAd() {
            }

            @Override
            public void didFailToShowAd(AdBuddizError adBuddizError) {
            }

            @Override
            public void didClick() {
                //Toast.makeText(ChallengeActivity.this,res.getString(R.string.Message_On_Ad_Click), Toast.LENGTH_SHORT).show();
                // TODO one day: display a message inviting the user to download the app.
            }

            @Override
            public void didHideAd() {
            }
        });
    }


    private View.OnClickListener onClickListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            displayDialogEnd(memVars.getLevelChallenge(), SITUATION_ABANDONED);
            memVars.nextLevelChallenge(false);
        }
    };

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_challenge, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();
        if (id == R.id.menu_settings_challenge) {
            Intent activiteeReglages = new Intent(ChallengeActivity.this, SettingsActivity.class);
            startActivityForResult(activiteeReglages, StaticFields.REQUEST_CODE_SETTINGS);
        } else if (id == R.id.menu_explanations_challenge) {
            displayExplanations(true);
        } else if (id == R.id.menu_use_a_star) {
            if (memVars.getNumberStars() > 0) {
                askConfirmUseStar();
            } else {
                app.displayMessageStars(this, res.getString(R.string.No_More_Stars));
            }
        }
        return super.onOptionsItemSelected(item);
    }


    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {
            case StaticFields.REQUEST_CODE_SETTINGS:
                colorUser = app.getCouleurUtilisateur();
                colorComputer = app.getCouleurAdversaire();
                refreshSettings();
                break;
        }
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        setContentView(R.layout.activity_challenge);
        surfaceGraphView = (SurfaceGraphView) findViewById(R.id.surfaceGraphViewChallenge);
        surfaceGraphView.setGameManagement(this);
        textView = (TextView) findViewById(R.id.textViewRingsUsed2);
        buttonAbandon = findViewById(R.id.buttonRestartChallenge);
        buttonAbandon.setOnClickListener(onClickListener);
        updateTextAndButton();
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
    }

    @Override
    public void setSurfaceGraphView(SurfaceGraphView surface) {
    }

    @Override
    public void begin() {
        refreshSettings();
        surfaceGraphView.redemarrerPartieDepuis(challenge.getAnoAlig().anneaux);
        if (challenge.userCanPlay()) {
            surfaceGraphView.autoriserDeplacement((byte) 1, 0);
        } else if (!challenge.gameFinished()) {
            postRingPlayedByUser();
        }
    }

    private void refreshSettings() {
        surfaceGraphView.changerCouleur(colorUser, 1);
        surfaceGraphView.changerCouleur(colorComputer, 2);
    }

    @Override
    public boolean userTriedPlaying(byte ringNumber) {
        return challenge.userTriedPlaying(ringNumber);
    }

    @Override
    public void postRingPlayedByUser() {
        updateTextAndButton();
        if (challenge.gameFinished()) {
            if (challenge.isWinnedByUser()) {
                blinkLine(0);
                displayDialogEnd(memVars.getLevelChallenge(), SITUATION_USER_WINS);
                memVars.nextLevelChallenge(true);
            } else {
                displayDialogEnd(memVars.getLevelChallenge(), SITUATION_RINGS_NUMBER_PASSED);
                memVars.nextLevelChallenge(false);
            }
        } else {
            byte ringComputer = challenge.playComputer();
            if (challenge.gameFinished()) {
                surfaceGraphView.jouerAdversaireAnimer(ringComputer, (byte) 2, (byte) 0, 1200, 40);
                blinkLine(1200);
                displayDialogEnd(memVars.getLevelChallenge(), SITUATION_COMPUTER_WINS, 2200);
                memVars.nextLevelChallenge(false);
            } else {
                surfaceGraphView.jouerAdversaireAnimer(ringComputer, (byte) 2, (byte) 1, 1200, 40);
                surfaceGraphView.cliquerClignoter(ringComputer, 5, 400, 1200);
                memVars.playTwoRingsChallenge(challenge.validateRingPlayedByUser(), ringComputer);
            }
        }
    }

    private void blinkLine(int delay) {
        byte[] blinkingRings = challenge.getBlinkingRings();
        for (byte ring : blinkingRings) {
            surfaceGraphView.cliquerClignoter(ring, 5, 400, delay);
        }
    }

    private void followingGame() {
        try {
            challenge = memVars.getPartieChallenge();
        } catch (ChallengeModeFinishedException e) {
            endDueToNoMoreLevels();
            return;
        }
        surfaceGraphView.redemarrerPartieDepuis(challenge.getAnoAlig().anneaux);
        updateTextAndButton();
        surfaceGraphView.autoriserDeplacement((byte) 1, 0);
        updateTitle();
    }

    private void useAStar() {
        if (memVars.useAStar()) {
            surfaceGraphView.autoriserDeplacement((byte) 0, 0);
            StarUsedBean bean = challenge.useAStar();
            if (bean.isNeedRestart()) {
                surfaceGraphView.redemarrerPartieDepuis(bean.getMatrix12());
            }
            if (bean.isFinishedJustAfterStar()) {
                surfaceGraphView.jouerAdversaireAnimer(bean.getRingUser(), (byte) 1, (byte) 0, 1200, 40);
                blinkLine(1200);
                displayDialogEnd(memVars.getLevelChallenge() + 1, SITUATION_USER_WINS, 2000);
                memVars.nextLevelChallenge(true);
            } else {
                memVars.playTwoRingsFromStar(bean.getRingUser(), bean.getRingComputer());
                surfaceGraphView.jouerAdversaireAnimer(bean.getRingUser(), (byte) 1, (byte) 2, 1000, 30);
                surfaceGraphView.afficherDeplacementAnneau(bean.getRingComputer(), 1000, 1000, 30, true);
                surfaceGraphView.jouerAnneau((byte) 2, bean.getRingComputer(), 2000);
                surfaceGraphView.cliquerClignoter(bean.getRingComputer(), 5, 400, 2000);
                surfaceGraphView.autoriserDeplacement((byte) 1, 2000);
                updateTextAndButton();
            }
        }
    }


    private void updateTitle() {
        setTitle(String.format("%s      %s %d", res.getString(R.string.Challenge), res.getString(R.string.Level), memVars.getLevelChallenge()));
    }

    private void updateTextAndButton() {
        int usedRings = challenge.getUsedRings();
        int totalRings = challenge.getAuthorizedRings();
        textView.setText(String.format("%d / %d", usedRings, totalRings));
        if (challenge.getNumberUserRings() == 0 || usedRings == totalRings) {
            buttonAbandon.setVisibility(View.INVISIBLE);
        } else {
            buttonAbandon.setVisibility(View.VISIBLE);
        }
    }

    private void displayDialogEnd(int level, byte situation) {
        boolean adShown = false;
        if ((situation == SITUATION_ABANDONED ||
                situation == SITUATION_COMPUTER_WINS ||
                situation == SITUATION_RINGS_NUMBER_PASSED) && memVars.tryAdvert() && AdBuddiz.isReadyToShowAd(this)) {
            AdBuddiz.showAd(this);
            adShown = true;
        }
        if (dialogEnd == null) {
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle("nothing");
            builder.setMessage("nothing");
            builder.setPositiveButton("nothing", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    followingGame();
                }
            });
            builder.setNegativeButton(res.getString(R.string.Quit), new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    finish();
                }
            });
            builder.setOnCancelListener(new DialogInterface.OnCancelListener() {
                @Override
                public void onCancel(DialogInterface dialog) {
                    finish();
                }
            });
            dialogEnd = builder.create();
        }
        dialogEnd.show();
        String s;
        Button buttonPositive = dialogEnd.getButton(DialogInterface.BUTTON_POSITIVE);
        switch (situation) {
            case SITUATION_USER_WINS:
                dialogEnd.setTitle(res.getString(R.string.Sentence_End_Win));
                dialogEnd.setMessage(res.getString(R.string.You_Passed_Level) + " " + level);
                buttonPositive.setText(res.getString(R.string.Go_To_Next_Level));
                break;
            case SITUATION_COMPUTER_WINS:
                dialogEnd.setTitle(res.getString(R.string.Sentence_End_Lost));
                s = res.getString(R.string.You_Lost_At_Level) + " " + level
                        + "\n" + res.getString(R.string.Computer_Won_Before);
                s += "\n\n" + (adShown ? res.getString(R.string.Ad_Displayed)
                        : res.getString(R.string.Next_Time_Ad));
                dialogEnd.setMessage(s);
                buttonPositive.setText(res.getString(R.string.Try_Again));
                break;
            case SITUATION_RINGS_NUMBER_PASSED:
                dialogEnd.setTitle(res.getString(R.string.Sentence_End_Lost));
                s = res.getString(R.string.You_Lost_At_Level) + " " + level
                        + "\n" + res.getString(R.string.Sentence_Number_Rings);
                s += "\n\n" + (adShown ? res.getString(R.string.Ad_Displayed)
                        : res.getString(R.string.Next_Time_Ad));
                dialogEnd.setMessage(s);
                buttonPositive.setText(res.getString(R.string.Try_Again));
                break;
            case SITUATION_ABANDONED:
                dialogEnd.setTitle(res.getString(R.string.Abandoned));
                s = res.getString(R.string.You_Lost_At_Level) + " " + level;
                s += "\n\n" + (adShown ? res.getString(R.string.Ad_Displayed)
                        : res.getString(R.string.Next_Time_Ad));
                dialogEnd.setMessage(s);
                buttonPositive.setText(res.getString(R.string.Try_Again));
        }
        dialogEnd.show();

    }

    private void displayDialogEnd(final int level, final byte situation, int delay) {
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                displayDialogEnd(level, situation);
            }
        }, delay);
    }

    public void onClickNbRings(View v) {
        if (dialogNbRings == null) {
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setMessage("nothing");
            builder.setCancelable(true);
            dialogNbRings = builder.create();
        }
        dialogNbRings.show();
        dialogNbRings.setMessage(res.getString(R.string.Explanation_Rings_Number)
                + "\n\n" + res.getString(R.string.In_This_Level)
                + "\n\t" + res.getString(R.string.Number_Of_Rings_Allowed)
                + " " + challenge.getAuthorizedRings()
                + "\n\t" + res.getString(R.string.Rings_used)
                + " " + challenge.getUsedRings());
        dialogNbRings.show();
    }


    private void displayExplanations(boolean firstPage) {
        if (firstPage) {
            if (dialogExplanations1 == null) {
                AlertDialog.Builder builder = new AlertDialog.Builder(this);
                builder.setTitle(res.getString(R.string.Explanations) + " 1/2");
                builder.setMessage(res.getString(R.string.Explanations_Challenge1));
                builder.setCancelable(false);
                builder.setPositiveButton(res.getString(R.string.Next), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                        displayExplanations(false);
                    }
                });
                dialogExplanations1 = builder.create();
            }
            dialogExplanations1.show();
        } else {
            if (dialogExplanations2 == null) {
                AlertDialog.Builder builder = new AlertDialog.Builder(this);
                builder.setTitle(res.getString(R.string.Explanations) + " 2/2");
                builder.setMessage(res.getString(R.string.Explanations_Challenge2));
                builder.setCancelable(true);
                builder.setNegativeButton(res.getString(R.string.Play), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                    }
                });
                dialogExplanations2 = builder.create();
            }
            dialogExplanations2.show();
        }
    }

    private void askConfirmUseStar() {
        if (dialogConfirmStars == null) {
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setMessage(res.getString(R.string.Sentence_Confirm_Use_Star));
            builder.setPositiveButton(res.getString(R.string.OK), new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    useAStar();
                }
            });
            builder.setNegativeButton(res.getString(R.string.Cancel), new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    // Nothing
                }
            });
            builder.setTitle(app.getSpannableForTitle());
            dialogConfirmStars = builder.create();
            builder.setCancelable(true);
        } else {
            dialogConfirmStars.setTitle(app.getSpannableForTitle());
        }
        dialogConfirmStars.show();
    }
}