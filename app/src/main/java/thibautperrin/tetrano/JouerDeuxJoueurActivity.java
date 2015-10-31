package thibautperrin.tetrano;

import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

public class JouerDeuxJoueurActivity extends AppCompatActivity {

    private ApplicationTetrano app;
    private EditText textNomJoueur1;
    private EditText textNomJoueur2;
    private TextView textQuestionNomJoueur1;
    private TextView textQuestionNomJoueur2;
    private int couleurJoueur1;
    private int couleurJoueur2;
    private Bundle bundle;
    private boolean fromSavedGame;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_jouer_deux_joueur);
        Button buttonJouer = (Button) findViewById(R.id.buttonJouer);
        buttonJouer.setOnClickListener(buttonJouerListener);
        textNomJoueur1 = (EditText) findViewById(R.id.editTextNomJoueur1);
        textNomJoueur2 = (EditText) findViewById(R.id.editTextNomJoueur2);
        textQuestionNomJoueur1 = (TextView) findViewById(R.id.textQuestionNomJoueur1);
        textQuestionNomJoueur2 = (TextView) findViewById(R.id.textQuestionNomJoueur2);
        findViewById(R.id.tacheBleue1).setOnClickListener(couleurJoueur1Listener);
        findViewById(R.id.tacheGrise1).setOnClickListener(couleurJoueur1Listener);
        findViewById(R.id.tacheMarron1).setOnClickListener(couleurJoueur1Listener);
        findViewById(R.id.tacheNoir1).setOnClickListener(couleurJoueur1Listener);
        findViewById(R.id.tacheOrange1).setOnClickListener(couleurJoueur1Listener);
        findViewById(R.id.tacheRouge1).setOnClickListener(couleurJoueur1Listener);
        findViewById(R.id.tacheVerte1).setOnClickListener(couleurJoueur1Listener);
        findViewById(R.id.tacheViolette1).setOnClickListener(couleurJoueur1Listener);
        findViewById(R.id.tacheBleue2).setOnClickListener(couleurJoueur2Listener);
        findViewById(R.id.tacheGrise2).setOnClickListener(couleurJoueur2Listener);
        findViewById(R.id.tacheMarron2).setOnClickListener(couleurJoueur2Listener);
        findViewById(R.id.tacheNoir2).setOnClickListener(couleurJoueur2Listener);
        findViewById(R.id.tacheOrange2).setOnClickListener(couleurJoueur2Listener);
        findViewById(R.id.tacheRouge2).setOnClickListener(couleurJoueur2Listener);
        findViewById(R.id.tacheVerte2).setOnClickListener(couleurJoueur2Listener);
        findViewById(R.id.tacheViolette2).setOnClickListener(couleurJoueur2Listener);
        app = (ApplicationTetrano) getApplication();
        textNomJoueur1.setHint(app.getNomJoueur1());
        textNomJoueur2.setHint(app.getNomJoueur2());
        Intent intent = this.getIntent();
        bundle = intent.getExtras();
        fromSavedGame = bundle.getBoolean(StaticFields.LABEL_FROM_SAVED_GAME);
        if (fromSavedGame) {
            couleurJoueur1 = bundle.getInt(StaticFields.LABEL_PLAYER1_COLOR);
            couleurJoueur2 = bundle.getInt(StaticFields.LABEL_PLAYER2_COLOR);
        } else {
            couleurJoueur1= app.getCouleurJoueur1();
            couleurJoueur2= app.getCouleurJoueur2();
        }
        textNomJoueur1.setTextColor(couleurJoueur1);
        textNomJoueur2.setTextColor(couleurJoueur2);
        textNomJoueur1.setHintTextColor(couleurJoueur1);
        textNomJoueur2.setHintTextColor(couleurJoueur2);
        textQuestionNomJoueur1.setTextColor(couleurJoueur1);
        textQuestionNomJoueur2.setTextColor(couleurJoueur2);
    }


    private View.OnClickListener buttonJouerListener = new View.OnClickListener() {
        public void onClick(View v) {
            String nomJoueur1 = textNomJoueur1.getText().toString();
            if (nomJoueur1.toCharArray().length == 0) nomJoueur1= textNomJoueur1.getHint().toString();
            String nomJoueur2 = textNomJoueur2.getText().toString();
            if (nomJoueur2.toCharArray().length == 0) nomJoueur2= textNomJoueur2.getHint().toString();
            app.setNomJoueur1(nomJoueur1);
            app.setNomJoueur2(nomJoueur2);
            if (!fromSavedGame) {
                app.setCouleurJoueur1(couleurJoueur1);
                app.setCouleurJoueur2(couleurJoueur2);
            }
            bundle.putString(StaticFields.LABEL_PLAYER1_NAME, nomJoueur1);
            bundle.putString(StaticFields.LABEL_PLAYER2_NAME, nomJoueur2);
            bundle.putInt(StaticFields.LABEL_PLAYER1_COLOR, couleurJoueur1);
            bundle.putInt(StaticFields.LABEL_PLAYER2_COLOR, couleurJoueur2);
            bundle.putInt(StaticFields.LABEL_GAME_CODE, StaticFields.GAME_2_PLAYERS);
            Intent intent = new Intent(JouerDeuxJoueurActivity.this, JeuActivity.class);
            intent.putExtras(bundle);
            if (fromSavedGame) {
                startActivityForResult(intent, StaticFields.REQUEST_CODE_FROM_SAVED_GAME);
            } else {
                startActivity(intent);
            }

        }
    };

    private View.OnClickListener couleurJoueur1Listener = new View.OnClickListener() {
        public void onClick(View v) {
            int couleur = ((TacheView) v).getColor();
            if (couleur != couleurJoueur2) {
                couleurJoueur1 = couleur;
                textNomJoueur1.setTextColor(couleur);
                textNomJoueur1.setHintTextColor(couleur);
                textQuestionNomJoueur1.setTextColor(couleur);
            }
        }
    };
    private View.OnClickListener couleurJoueur2Listener = new View.OnClickListener() {
        public void onClick(View v) {
            int couleur = ((TacheView) v).getColor();
            if (couleur != couleurJoueur1) {
                couleurJoueur2 = couleur;
                textNomJoueur2.setTextColor(couleur);
                textNomJoueur2.setHintTextColor(couleur);
                textQuestionNomJoueur2.setTextColor(couleur);
            }
        }
    };

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == StaticFields.REQUEST_CODE_FROM_SAVED_GAME) finish();
    }
}
