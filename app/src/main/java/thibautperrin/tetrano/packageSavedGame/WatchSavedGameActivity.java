package thibautperrin.tetrano.packageSavedGame;

import android.content.Intent;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;

import thibautperrin.tetrano.ApplicationTetrano;
import thibautperrin.tetrano.JouerContreOrdiActivity;
import thibautperrin.tetrano.JouerDeuxJoueurActivity;
import thibautperrin.tetrano.R;
import thibautperrin.tetrano.StaticFields;
import thibautperrin.tetrano.packageSurfaceGraphView.SurfaceGraphView;


public class WatchSavedGameActivity extends AppCompatActivity {

    private TextView textView;
    private SurfaceGraphView surfaceGraphView;
    private int cursor;
    private SavedGame savedGame;

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_watch_saved_game);
        Intent intent = this.getIntent();
        Bundle bundle = intent.getExtras();
        String gameName = bundle.getString(StaticFields.LABEL_GAME_NAME);
        ApplicationTetrano app = (ApplicationTetrano) getApplication();
        DAOSavedGame dao = app.getOpenedDAOSavedGame();
        savedGame = dao.getSavedGame(gameName);
        setTitle(savedGame.getGameName());
        surfaceGraphView = (SurfaceGraphView) findViewById(R.id.surfaceGraphViewWatchSavedGame);
        surfaceGraphView.setFormeCarre(true);
        surfaceGraphView.changerCouleur(savedGame.getUserColor(), 1);
        surfaceGraphView.changerCouleur(savedGame.getAdversaryColor(), 2);
        surfaceGraphView.redemarrerPartieDepuis(savedGame.getMatrix());
        cursor = savedGame.getRingsPlayed().length;
        textView = (TextView) findViewById(R.id.textDescriptionAnneau);
        actualiseTextView();
        View boutonPrecedent = findViewById(R.id.boutonPrecedent);
        View boutonSuivant = findViewById(R.id.boutonSuivant);
        boutonPrecedent.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onPrevious();
            }
        });
        boutonSuivant.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onNext();
            }
        });
    }

    private void onPrevious() {
        if (cursor > 0) {
            cursor--;
            surfaceGraphView.jouerAnneau((byte) 0, savedGame.getRingsPlayed()[cursor], 0);
            actualiseTextView();
        }
    }

    private void onNext() {
        if (cursor < savedGame.getRingsPlayed().length) {
            surfaceGraphView.jouerAnneau(getProchainJoueur(), savedGame.getRingsPlayed()[cursor], 0);
            cursor++;
            actualiseTextView();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_watch_saved_game, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        if (id == R.id.menu_play_against_computer) {
            playAgainstComputer();
        } else if (id == R.id.menu_play_2_players) {
            playTwoPlayers();
        }
        return super.onOptionsItemSelected(item);
    }

    private byte getProchainJoueur() {
        if (savedGame.isUserBegin()) {
            if (cursor % 2 == 0) {
                return (byte) 1;
            } else {
                return (byte) 2;
            }
        } else {
            if (cursor % 2 == 0) {
                return (byte) 2;
            } else {
                return (byte) 1;
            }
        }
    }

    private void actualiseTextView() {
        textView.setText(String.format("%s %d / %d", getResources().getString(R.string.Ring), cursor, savedGame.getRingsPlayed().length));
    }

    private void playAgainstComputer() {
        Bundle bundle = new Bundle();
        bundle.putBoolean(StaticFields.LABEL_FROM_SAVED_GAME, true);
        bundle.putBoolean(StaticFields.LABEL_USER_BEGINS, savedGame.isUserBegin());
        bundle.putInt(StaticFields.LABEL_USER_COLOR, savedGame.getUserColor());
        bundle.putInt(StaticFields.LABEL_ADVERSARY_COLOR, savedGame.getAdversaryColor());
        byte[] ringsPlayedUntilCursor = new byte[cursor];
        System.arraycopy(savedGame.getRingsPlayed(), 0, ringsPlayedUntilCursor, 0, cursor);
        bundle.putByteArray(StaticFields.LABEL_RINGS_PLAYED, ringsPlayedUntilCursor);
        Intent intent = new Intent(WatchSavedGameActivity.this, JouerContreOrdiActivity.class);
        intent.putExtras(bundle);
        startActivityForResult(intent, StaticFields.REQUEST_CODE_PLAY_AGAINST_COMPUTER);
    }

    private void playTwoPlayers() {
        Bundle bundle = new Bundle();
        bundle.putBoolean(StaticFields.LABEL_FROM_SAVED_GAME, true);
        if (savedGame.isUserBegin()) {
            bundle.putInt(StaticFields.LABEL_PLAYER1_COLOR, savedGame.getUserColor());
            bundle.putInt(StaticFields.LABEL_PLAYER2_COLOR, savedGame.getAdversaryColor());
        } else {
            bundle.putInt(StaticFields.LABEL_PLAYER1_COLOR, savedGame.getAdversaryColor());
            bundle.putInt(StaticFields.LABEL_PLAYER2_COLOR, savedGame.getUserColor());
        }
        byte[] ringsPlayedUntilCursor = new byte[cursor];
        System.arraycopy(savedGame.getRingsPlayed(), 0, ringsPlayedUntilCursor, 0, cursor);
        bundle.putByteArray(StaticFields.LABEL_RINGS_PLAYED, ringsPlayedUntilCursor);
        Intent intent = new Intent(WatchSavedGameActivity.this, JouerDeuxJoueurActivity.class);
        intent.putExtras(bundle);
        startActivityForResult(intent, StaticFields.REQUEST_CODE_PLAY_2_PLAYERS);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        setResult(StaticFields.RESULT_CODE_LEAVE);
        finish();
    }
}