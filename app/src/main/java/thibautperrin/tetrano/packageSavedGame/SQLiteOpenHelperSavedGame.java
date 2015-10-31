package thibautperrin.tetrano.packageSavedGame;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import thibautperrin.tetrano.StaticFields;

/**
 * Created by Thibaut PERRIN on 25/09/2015.
 */
public class SQLiteOpenHelperSavedGame extends SQLiteOpenHelper {
    private static final int DATABASE_VERSION = 1;
    protected static final String TABLE_NAME = "savedgames";
    protected static final String COLUMN_NAME = "name";
    protected static final String COLUMN_RINGS = "rings";
    protected static final String COLUMN_IS_USER_BEGIN = "begin";
    protected static final String COLUMN_USER_COLOR = "ucolor";
    protected static final String COLUMN_ADVERSARY_COLOR = "acolor";
    private static final String DATABASE_CREATE = "CREATE TABLE " + TABLE_NAME + " (" +
            COLUMN_NAME + " TEXT PRIMARY KEY, " +
            COLUMN_RINGS + " TEXT, " +
            COLUMN_IS_USER_BEGIN + " INTEGER, " +
            COLUMN_USER_COLOR + " INTEGER, " +
            COLUMN_ADVERSARY_COLOR + " INTEGER);";

    public SQLiteOpenHelperSavedGame(Context context) {
        super(context, StaticFields.SAVED_GAMES_DATABASE_FILE, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL(DATABASE_CREATE);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // nothing to do currently
    }
}
