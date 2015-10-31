package thibautperrin.tetrano.reglesDuJeu;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import thibautperrin.tetrano.R;


public class ExamplesActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_examples);

        try {
            byte[][] anneaux = new byte[6][4];
            anneaux[0][0] = 48;
            anneaux[0][1] = 53;
            anneaux[0][2] = 58;
            anneaux[0][3] = 63;

            anneaux[1][0] = 40;
            anneaux[1][1] = 41;
            anneaux[1][2] = 42;
            anneaux[1][3] = 43;

            anneaux[2][0] = 9;
            anneaux[2][1] = 25;
            anneaux[2][2] = 41;
            anneaux[2][3] = 57;

            anneaux[3][0] = 2;
            anneaux[3][1] = 22;
            anneaux[3][2] = 42;
            anneaux[3][3] = 62;

            anneaux[4][0] = 12;
            anneaux[4][1] = 25;
            anneaux[4][2] = 38;
            anneaux[4][3] = 51;

            anneaux[5][0] = 1;
            anneaux[5][1] = 5;
            anneaux[5][2] = 9;
            anneaux[5][3] = 13;

            ((GrilleView) findViewById(R.id.grilleView1)).precise(anneaux[0]);
            ((GrilleView) findViewById(R.id.grilleView2)).precise(anneaux[1]);
            ((GrilleView) findViewById(R.id.grilleView3)).precise(anneaux[2]);
            ((GrilleView) findViewById(R.id.grilleView4)).precise(anneaux[3]);
            ((GrilleView) findViewById(R.id.grilleView5)).precise(anneaux[4]);
            ((GrilleView) findViewById(R.id.grilleView6)).precise(anneaux[5]);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
