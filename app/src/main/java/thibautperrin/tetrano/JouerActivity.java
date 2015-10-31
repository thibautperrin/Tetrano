package thibautperrin.tetrano;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import thibautperrin.tetrano.packageChallenge.ChallengeActivity;
import thibautperrin.tetrano.packageConnexions.packageBluetooth.ConnexionBluetoothActivity;
import thibautperrin.tetrano.packageSavedGame.ListePartiesEnregistreesActivity;


public class JouerActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_jouer);

        findViewById(R.id.BoutonJouerADeux).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Bundle bundle = new Bundle();
                bundle.putBoolean(StaticFields.LABEL_FROM_SAVED_GAME, false);
                Intent intent = new Intent(JouerActivity.this, JouerDeuxJoueurActivity.class);
                intent.putExtras(bundle);
                startActivity(intent);
            }
        });

        findViewById(R.id.BoutonJouerContreOrdi).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Bundle bundle = new Bundle();
                bundle.putBoolean(StaticFields.LABEL_FROM_SAVED_GAME, false);
                Intent intent = new Intent(JouerActivity.this, JouerContreOrdiActivity.class);
                intent.putExtras(bundle);
                startActivity(intent);
            }
        });

        findViewById(R.id.BoutonJouerBluetooth).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(JouerActivity.this, ConnexionBluetoothActivity.class));
            }
        });

        findViewById(R.id.BoutonJouerEnLigne).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ConnectivityManager connectivityManager = (ConnectivityManager) JouerActivity.this.getSystemService(Context.CONNECTIVITY_SERVICE);
                NetworkInfo networkInfo = connectivityManager.getActiveNetworkInfo();
                if (networkInfo == null || !networkInfo.isConnected()) {
                    Toast.makeText(JouerActivity.this,
                            JouerActivity.this.getResources().getString(R.string.No_Internet_Connection),
                            Toast.LENGTH_LONG).show();
                    return;
                }
                Intent intent = new Intent(JouerActivity.this, JeuActivity.class);
                Bundle bundle = new Bundle();
                ApplicationTetrano app = (ApplicationTetrano) getApplication();
                bundle.putInt(StaticFields.LABEL_USER_COLOR, app.getCouleurUtilisateur());
                bundle.putInt(StaticFields.LABEL_ADVERSARY_COLOR, app.getCouleurAdversaire());
                bundle.putInt(StaticFields.LABEL_GAME_CODE, StaticFields.GAME_ONLINE);
                bundle.putString(StaticFields.LABEL_USER_NAME, app.getNomUtilisateur());
                bundle.putString(StaticFields.LABEL_ADVERSARY_NAME, "adversaireWeb");
                intent.putExtras(bundle);
                startActivity(intent);
            }
        });

        findViewById(R.id.BoutonPartieSauvegarde).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(JouerActivity.this, ListePartiesEnregistreesActivity.class));
            }
        });

        findViewById(R.id.BoutonChallenge).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(JouerActivity.this, ChallengeActivity.class));
            }
        });
    }
}
