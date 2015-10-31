package thibautperrin.tetrano;

import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.Button;

import com.purplebrain.adbuddiz.sdk.AdBuddiz;

import thibautperrin.tetrano.reglesDuJeu.NoticeFormelleActivity;


public class MainActivity extends AppCompatActivity {

    private AlertDialog dialogAPropos;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        AdBuddiz.setPublisherKey(StaticFields.PUBLISHER_KEY_ADBUDDIZ);
        AdBuddiz.cacheAds(this);
        //AdBuddiz.setTestModeActive();  //TODO to delete before submitting to store

        Button buttonJouer = (Button) findViewById(R.id.BoutonJouer);
        Button buttonCommentJouer = (Button) findViewById(R.id.BoutonCommentJouer);
        Button buttonAPropos = (Button) findViewById(R.id.BoutonAPropos);
        Button buttonReglages = (Button) findViewById(R.id.BoutonReglages);

        buttonJouer.setOnClickListener(buttonJouerListener);
        buttonCommentJouer.setOnClickListener(buttonCommentJouerListener);
        buttonAPropos.setOnClickListener(buttonAProposListener);
        buttonReglages.setOnClickListener(buttonReglagesListener);

        AlertDialog.Builder builderAPropos = new AlertDialog.Builder(this);
        builderAPropos.setMessage(getResources().getString(R.string.Long_Sentence_About));
        builderAPropos.setTitle(getResources().getString(R.string.About));
        dialogAPropos = builderAPropos.create();


    }

    // Quand on clique sur le bouton Jouer:
    private View.OnClickListener buttonJouerListener = new View.OnClickListener() {
        public void onClick(final View v) {
            v.setBackgroundColor(Color.BLUE);
            v.postDelayed(new Runnable() {
                @Override
                public void run() {
                    v.setBackgroundColor(ContextCompat.getColor(MainActivity.this, R.color.jauneBoutonMain));
                }
            }, 500);
            Intent ActiviteeJouer = new Intent(MainActivity.this, JouerActivity.class);
            startActivity(ActiviteeJouer);
        }
    };

    // Quand on clique sur le bouton A Propos :
    private View.OnClickListener buttonAProposListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            dialogAPropos.show();
        }
    };

    // Quand on clique sur le bouton comment jouer? :
    private View.OnClickListener buttonCommentJouerListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            Intent activiteeNoticeFormelle = new Intent(MainActivity.this, NoticeFormelleActivity.class);
            startActivity(activiteeNoticeFormelle);
        }
    };

    // Quand on clique sur le bouton de réglages? :
    private View.OnClickListener buttonReglagesListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            Intent activiteeReglages = new Intent(MainActivity.this, SettingsActivity.class);
            startActivity(activiteeReglages);
        }
    };
}
