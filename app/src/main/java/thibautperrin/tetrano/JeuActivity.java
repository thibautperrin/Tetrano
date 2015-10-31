package thibautperrin.tetrano;


import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Color;
import android.os.Bundle;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;

import java.util.Calendar;
import java.util.Date;

import thibautperrin.tetrano.packageGestionJeu.GestionJeu;
import thibautperrin.tetrano.packageGestionJeu.SauvegardePartie;
import thibautperrin.tetrano.packageSurfaceGraphView.SurfaceGraphView;



public class JeuActivity extends AppCompatActivity {

    private SurfaceGraphView surfaceGraphView;
    private GestionJeu gestionJeu;
    private Button boutonAnnuler;
    private Button boutonRecommencer;
    private Button boutonClignoter;
    private AlertDialog alertDialogSauvegarder = null;
    private int typePartie;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_jeu);

        Intent intent = this.getIntent();
        Bundle bundle = intent.getExtras();
        SauvegardePartie sauvegarde;
        if (savedInstanceState == null) {
            sauvegarde = new SauvegardePartie();
        } else {
            sauvegarde = savedInstanceState.getParcelable(StaticFields.LABEL_PARCELABLE);
        }
        typePartie = bundle.getInt(StaticFields.LABEL_GAME_CODE);
        surfaceGraphView = (SurfaceGraphView) findViewById(R.id.surfaceGraphView);
        boutonAnnuler = (Button) findViewById(R.id.boutonAnnuler);
        boutonRecommencer = (Button) findViewById(R.id.boutonRecommencer);
        boutonClignoter = (Button) findViewById(R.id.boutonClignoter);
        try {
            gestionJeu = GestionJeu.CreeGestionJeu(bundle, sauvegarde, surfaceGraphView, this);
        } catch (Exception e) {
            e.printStackTrace();
        }
        surfaceGraphView.setGameManagement(gestionJeu);
        boutonClignoter.setOnClickListener(clignoterListener);
        boutonAnnuler.setOnClickListener(annulerListener);
        boutonRecommencer.setOnClickListener(recommencerListener);
    }

    private View.OnClickListener annulerListener = new View.OnClickListener() {
        public void onClick(View v) {
            gestionJeu.cliqueAnnuler();
        }
    };

    private View.OnClickListener clignoterListener = new View.OnClickListener() {
        public void onClick(View v) {
            gestionJeu.cliqueClignoter();
        }
    };

    private View.OnClickListener recommencerListener = new View.OnClickListener() {
        public void onClick(View v) {
            gestionJeu.cliqueRecommencer();
        }
    };

    public void retourUtilisateurBoutonAnnuler() {
        boutonAnnuler.setBackgroundColor(Color.BLACK);
        surfaceGraphView.postDelayed(new Runnable() {
            @Override
            public void run() {
                boutonAnnuler.setBackgroundColor(ContextCompat.getColor(JeuActivity.this, R.color.boutonAnnuler));
            }
        }, 500);
    }

    public void retourUtilisateurBoutonRecommencer() {
        boutonRecommencer.setBackgroundColor(Color.BLACK);
        surfaceGraphView.postDelayed(new Runnable() {
            @Override
            public void run() {
                boutonRecommencer.setBackgroundColor(ContextCompat.getColor(JeuActivity.this, R.color.boutonRecommencer));
            }
        }, 500);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_jeu, menu);
        if (typePartie == StaticFields.GAME_2_PLAYERS) {
            menu.getItem(3).setVisible(false);
        }
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        if (id == R.id.menu_jeu_annuler) {
            gestionJeu.cliqueAnnuler();
        } else if (id == R.id.menu_jeu_recommencer) {
            gestionJeu.cliqueRecommencer();
        } else if (id == R.id.menu_jeu_clignoter) {
            gestionJeu.cliqueClignoter();
        } else if (id == R.id.menu_parametres) {
            Intent activiteeReglages = new Intent(JeuActivity.this, SettingsActivity.class);
            startActivityForResult(activiteeReglages, StaticFields.REQUEST_CODE_SETTINGS);
        } else if (id == R.id.menu_sauvegarder) {
            if (alertDialogSauvegarder == null) {
                AlertDialog.Builder builder = new AlertDialog.Builder(this);
                final EditText edittext = new EditText(this);
                builder.setMessage(getResources().getString(R.string.Enter_A_Name));
                builder.setTitle(getResources().getString(R.string.Save_The_Game));
                builder.setView(edittext);
                Date currentDate = Calendar.getInstance().getTime();
                java.text.DateFormat dateFormat = android.text.format.DateFormat.getDateFormat(this);
                String formattedCurrentDate = dateFormat.format(currentDate);
                edittext.setText(String.format("%s %s", getResources().getString(R.string.Game_Of), formattedCurrentDate));
                builder.setPositiveButton(getResources().getString(R.string.Save_The_Game), new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int whichButton) {
                        String YouEditTextValue = edittext.getText().toString();
                        gestionJeu.sauvegarderPartiePourPlusTard(YouEditTextValue);
                    }
                });

                builder.setNegativeButton(getResources().getString(R.string.Cancel), new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int whichButton) {
                        // what ever you want to do with No option.
                    }
                });
                alertDialogSauvegarder = builder.create();
            }
            alertDialogSauvegarder.show();
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {
            case StaticFields.REQUEST_CODE_SETTINGS:
                gestionJeu.actualiserReglages();
                break;
        }
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        setContentView(R.layout.activity_jeu);
        surfaceGraphView = (SurfaceGraphView) findViewById(R.id.surfaceGraphView);
        boutonAnnuler = (Button) findViewById(R.id.boutonAnnuler);
        boutonRecommencer = (Button) findViewById(R.id.boutonRecommencer);
        boutonClignoter = (Button) findViewById(R.id.boutonClignoter);
        gestionJeu.setSurfaceGraphView(surfaceGraphView); // TODO: regarder si on ne peut pas faire mieux que ça...
        surfaceGraphView.setGameManagement(gestionJeu);
        boutonClignoter.setOnClickListener(clignoterListener);
        boutonAnnuler.setOnClickListener(annulerListener);
        boutonRecommencer.setOnClickListener(recommencerListener);
    }

    @Override
    public void onBackPressed() {
        gestionJeu.sortir();
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        if (gestionJeu != null) {
            outState.putParcelable(StaticFields.LABEL_PARCELABLE, gestionJeu.sauvegarder());
        }
    }
}