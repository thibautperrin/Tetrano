package thibautperrin.tetrano.reglesDuJeu;

import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.View;

import thibautperrin.tetrano.R;


public class NoticeFormelleActivity extends AppCompatActivity {


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_notice_formelle);
        findViewById(R.id.buttonExemples).setOnClickListener(buttonExamplesListener);
    }

    private View.OnClickListener buttonExamplesListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            Intent activiteeExempleNotice = new Intent(NoticeFormelleActivity.this, ExamplesActivity.class);
            startActivity(activiteeExempleNotice);
        }
    };
}
