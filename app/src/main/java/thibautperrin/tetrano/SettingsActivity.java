package thibautperrin.tetrano;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

public class SettingsActivity extends AppCompatActivity {

    private ApplicationTetrano app;
    private TextView textNomUtilisateur;
    private EditText editTextNomUtilisateur;
    private TextView textCouleurUtilisateur;
    private TextView textCouleurAdversaire;
    private int couleurUtilisateur;
    private int couleurAdversaire;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);
        textNomUtilisateur = (TextView) findViewById(R.id.TextNomUtilisateur);
        editTextNomUtilisateur = (EditText) findViewById(R.id.editTextNomUtilisateur);
        textCouleurUtilisateur = (TextView) findViewById(R.id.TextCouleurUtilisateur);
        textCouleurAdversaire = (TextView) findViewById(R.id.TextCouleurAdversaire);
        Button boutonValider = (Button) findViewById(R.id.boutonValiderReglages);

       findViewById(R.id.tacheGrise5).setOnClickListener(tacheUtilisateurListener);
       findViewById(R.id.tacheNoir5).setOnClickListener(tacheUtilisateurListener);
       findViewById(R.id.tacheMarron5).setOnClickListener(tacheUtilisateurListener);
       findViewById(R.id.tacheOrange5).setOnClickListener(tacheUtilisateurListener);
       findViewById(R.id.tacheRouge5).setOnClickListener(tacheUtilisateurListener);
       findViewById(R.id.tacheViolette5).setOnClickListener(tacheUtilisateurListener);
       findViewById(R.id.tacheBleue5).setOnClickListener(tacheUtilisateurListener);
       findViewById(R.id.tacheVerte5).setOnClickListener(tacheUtilisateurListener);

       findViewById(R.id.tacheGrise6).setOnClickListener(tacheAdversaireListener);
       findViewById(R.id.tacheNoir6).setOnClickListener(tacheAdversaireListener);
       findViewById(R.id.tacheMarron6).setOnClickListener(tacheAdversaireListener);
       findViewById(R.id.tacheOrange6).setOnClickListener(tacheAdversaireListener);
       findViewById(R.id.tacheRouge6).setOnClickListener(tacheAdversaireListener);
       findViewById(R.id.tacheViolette6).setOnClickListener(tacheAdversaireListener);
       findViewById(R.id.tacheBleue6).setOnClickListener(tacheAdversaireListener);
       findViewById(R.id.tacheVerte6).setOnClickListener(tacheAdversaireListener);

        app = (ApplicationTetrano) getApplication();
        couleurUtilisateur = app.getCouleurUtilisateur();
        couleurAdversaire = app.getCouleurAdversaire();
        editTextNomUtilisateur.setHint(app.getNomUtilisateur());
        textNomUtilisateur.setTextColor(couleurUtilisateur);
        editTextNomUtilisateur.setTextColor(couleurUtilisateur);
        editTextNomUtilisateur.setHintTextColor(couleurUtilisateur);
        textCouleurUtilisateur.setTextColor(couleurUtilisateur);
        textCouleurAdversaire.setTextColor(couleurAdversaire);

        boutonValider.setOnClickListener(BoutonValiderListener);
    }


    private View.OnClickListener tacheUtilisateurListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            int couleur = ((TacheView) v).getColor();
            if (couleur != couleurAdversaire) {
                couleurUtilisateur = couleur;
                textNomUtilisateur.setTextColor(couleurUtilisateur);
                editTextNomUtilisateur.setTextColor(couleurUtilisateur);
                editTextNomUtilisateur.setHintTextColor(couleurUtilisateur);
                textCouleurUtilisateur.setTextColor(couleurUtilisateur);
            }
        }
    };

    private View.OnClickListener tacheAdversaireListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            int couleur = ((TacheView) v).getColor();
            if (couleur != couleurUtilisateur) {
                couleurAdversaire = couleur;
                textCouleurAdversaire.setTextColor(couleurAdversaire);
            }
        }
    };


        private View.OnClickListener BoutonValiderListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            avantSortir();
            finish();
        }
    };


    @Override
    public void onBackPressed() {
        avantSortir();
        super.onBackPressed();
    }

    private void avantSortir() {
        String nomUtilisateur = editTextNomUtilisateur.getText().toString();
        if (nomUtilisateur.toCharArray().length != 0) {
            app.setNomUtilisateur(nomUtilisateur);
        }
        app.setCouleurUtilisateur(couleurUtilisateur);
        app.setCouleurAdversaire(couleurAdversaire);
        Toast.makeText(this, getResources().getString(R.string.Settings_Saved), Toast.LENGTH_SHORT).show();
    }
}