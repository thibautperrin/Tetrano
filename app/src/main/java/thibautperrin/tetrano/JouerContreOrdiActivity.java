package thibautperrin.tetrano;

import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.Button;
import android.widget.RadioButton;
import android.widget.SeekBar;
import android.widget.TextView;

/**
 * This Activity happens when the user want to play against the IA, or when he wants to
 * continue a saved game.
 * The difficult point is the boolean userBegin:
 * When the user just want to start a new game against the computer, he can begin or not
 * the game. Here it is easy.
 * However, when the user wants to continue a saved game, this activity receives a piece of
 * information saying whether the player 1 (assumed to be the user) began the game or not.
 * Next the user can choose to be the first to continue, or to let the computer be the
 * first to continue the game. By default, all is done so as to avoid to change the roles.
 * Thus, if the user 1 is the next one to play, as we assume that he is the user, the next
 * player is by default the user and reciprocally.
 * At the end, if the checkbox have been changed from its default value, we change the
 * value of the user having begun the game...
 */
public class JouerContreOrdiActivity extends AppCompatActivity {
    private RadioButton radioButtonCommence;
    private RadioButton radioButtonNeCommencePas;
    private View boutonGauche;
    private View boutonDroit;
    private TextView textNiveauPourcent;
    private SeekBar barNiveau;
    private ApplicationTetrano app;
    private Bundle bundle;
    private boolean fromSavedGame = false;
    private boolean userBeginByDefault;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_jouer_contre_ordi);
        radioButtonCommence = (RadioButton) findViewById(R.id.radioButtonCommencer);
        radioButtonNeCommencePas = (RadioButton) findViewById(R.id.radioButtonNePasCommencer);
        radioButtonCommence.setOnClickListener(choixQuiCommenceListener);
        radioButtonNeCommencePas.setOnClickListener(choixQuiCommenceListener);
        Button buttonJouer = (Button) findViewById(R.id.buttonJouer);
        buttonJouer.setOnClickListener(buttonJouerListener);
        boutonGauche = findViewById(R.id.boutonGauche);
        boutonDroit = findViewById(R.id.boutonDroit);
        boutonGauche.setOnClickListener(buttonNiveauPrecisListener);
        boutonDroit.setOnClickListener(buttonNiveauPrecisListener);
        textNiveauPourcent = (TextView) findViewById(R.id.TextNiveauPourcent);
        barNiveau = (SeekBar) findViewById(R.id.seekBarLevel);
        barNiveau.setOnSeekBarChangeListener(barNiveauListener);
        app = (ApplicationTetrano) getApplication();
        int niveauPrecedent = app.getNiveauJeu();
        barNiveau.setProgress(niveauPrecedent);
        Intent intent = this.getIntent();
        bundle = intent.getExtras();
        fromSavedGame = bundle.getBoolean(StaticFields.LABEL_FROM_SAVED_GAME);
        if (fromSavedGame) {
            boolean userBegin = bundle.getBoolean(StaticFields.LABEL_USER_BEGINS);
            byte[] ringsPlayed = bundle.getByteArray(StaticFields.LABEL_RINGS_PLAYED);
            int ringsNumber = ringsPlayed == null ? 0 : ringsPlayed.length;
            if ((userBegin && ringsNumber % 2 == 0) || (!userBegin && ringsNumber % 2 == 1)) {
                userBeginByDefault = true;
                radioButtonCommence.setChecked(true);
                radioButtonNeCommencePas.setChecked(false);
            } else {
                userBeginByDefault = false;
                radioButtonCommence.setChecked(false);
                radioButtonNeCommencePas.setChecked(true);
            }
        }
    }

    private View.OnClickListener choixQuiCommenceListener = new View.OnClickListener() {
        public void onClick(View v) {
            RadioButton boutonClicke = (RadioButton) v;
            if (boutonClicke.equals(radioButtonCommence)) {
                radioButtonCommence.setChecked(true);
                radioButtonNeCommencePas.setChecked(false);
            } else {
                radioButtonCommence.setChecked(false);
                radioButtonNeCommencePas.setChecked(true);
            }
        }
    };

    private View.OnClickListener buttonJouerListener = new View.OnClickListener() {
        public void onClick(View v) {
            app.setNiveauJeu(barNiveau.getProgress());
            String nomUtilisateur = app.getNomUtilisateur();
            String nomAdversaire = getResources().getString(R.string.Computer);
            int couleurUtilisateur = app.getCouleurUtilisateur();
            int couleurAdversaire = app.getCouleurAdversaire();
            boolean utilisateurCommence = radioButtonCommence.isChecked();
            bundle.putString(StaticFields.LABEL_USER_NAME, nomUtilisateur);
            bundle.putString(StaticFields.LABEL_ADVERSARY_NAME, nomAdversaire);
            if (!fromSavedGame) {
                bundle.putInt(StaticFields.LABEL_USER_COLOR, couleurUtilisateur);
                bundle.putInt(StaticFields.LABEL_ADVERSARY_COLOR, couleurAdversaire);
                bundle.putBoolean(StaticFields.LABEL_USER_BEGINS, utilisateurCommence);
            } else {
                boolean userBegin = bundle.getBoolean(StaticFields.LABEL_USER_BEGINS);
                if (utilisateurCommence != userBeginByDefault) {
                    userBegin = !userBegin;
                    bundle.putBoolean(StaticFields.LABEL_USER_BEGINS, userBegin);
                    int userColor = bundle.getInt(StaticFields.LABEL_USER_COLOR);
                    int adversaryColor = bundle.getInt(StaticFields.LABEL_ADVERSARY_COLOR);
                    bundle.putInt(StaticFields.LABEL_USER_COLOR, adversaryColor);
                    bundle.putInt(StaticFields.LABEL_ADVERSARY_COLOR, userColor);
                }
            }
            bundle.putInt(StaticFields.LABEL_GAME_CODE, StaticFields.GAME_AGAINST_COMPUTER);
            bundle.putInt(StaticFields.LABEL_LEVEL, barNiveau.getProgress());
            Intent intent = new Intent(JouerContreOrdiActivity.this, JeuActivity.class);
            intent.putExtras(bundle);
            if (fromSavedGame) {
                startActivityForResult(intent, StaticFields.REQUEST_CODE_FROM_SAVED_GAME);
            } else {
                startActivity(intent);
            }
        }
    };


    private SeekBar.OnSeekBarChangeListener barNiveauListener = new SeekBar.OnSeekBarChangeListener() {
        public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
            textNiveauPourcent.setText(String.format(" %d", progress));
        }

        @Override
        public void onStartTrackingTouch(SeekBar seekBar) {
        }

        @Override
        public void onStopTrackingTouch(SeekBar seekBar) {
        }
    };

    private View.OnClickListener buttonNiveauPrecisListener = new View.OnClickListener() {
        public void onClick(View v) {
            if (v == boutonGauche) {
                if (barNiveau.getProgress() > 0) {
                    barNiveau.setProgress(barNiveau.getProgress() - 1);
                }
            } else if (v == boutonDroit) {
                if (barNiveau.getProgress() < 100) {
                    barNiveau.setProgress(barNiveau.getProgress() + 1);
                }
            }
        }
    };

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == StaticFields.REQUEST_CODE_FROM_SAVED_GAME) finish();
    }
}
