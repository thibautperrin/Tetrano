package thibautperrin.tetrano.packageSavedGame;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by Thibaut PERRIN on 25/09/2015.
 */
public class DAOSavedGame {
    private SQLiteDatabase db;
    private SQLiteOpenHelperSavedGame dbHelper;

    public DAOSavedGame(Context context) {
        dbHelper = new SQLiteOpenHelperSavedGame(context);
    }

    public void open() throws SQLException {
        db = dbHelper.getWritableDatabase();
    }

    /**
     * This method add a saved game to the database.
     *
     * @param game : a bean SavedGame
     * @return true if the saved game has been corectly added. False if there have been a
     * mistake. In particular, if the name already exist in the database, this method return
     * false.
     */
    public boolean addSavedGame(SavedGame game) {
        ContentValues values = new ContentValues();
        values.put(SQLiteOpenHelperSavedGame.COLUMN_NAME, game.getGameName());
        values.put(SQLiteOpenHelperSavedGame.COLUMN_RINGS, game.getRingsPlayed());
        values.put(SQLiteOpenHelperSavedGame.COLUMN_IS_USER_BEGIN, game.isUserBegin() ? 1 : 0);
        values.put(SQLiteOpenHelperSavedGame.COLUMN_USER_COLOR, game.getUserColor());
        values.put(SQLiteOpenHelperSavedGame.COLUMN_ADVERSARY_COLOR, game.getAdversaryColor());
        return db.insert(SQLiteOpenHelperSavedGame.TABLE_NAME, null, values) != -1;
    }

    /**
     * This method retrieves in the database the saved games.
     *
     * @return a list of beans: SavedGame.
     */
    public List<SavedGame> getSavedGames() {
        Cursor cursor = db.query(SQLiteOpenHelperSavedGame.TABLE_NAME, null, null, null, null, null, null);
        ArrayList<SavedGame> res = new ArrayList<>(cursor.getCount());
        while (cursor.moveToNext()) {
            res.add(new SavedGame(
                    cursor.getString(0),
                    cursor.getBlob(1),
                    cursor.getInt(2) == 1,
                    cursor.getInt(3),
                    cursor.getInt(4)));
        }
        cursor.close();
        return res;
    }

    public ArrayList<String> getSavedGameNames() {
        Cursor cursor = db.query(SQLiteOpenHelperSavedGame.TABLE_NAME,
                new String[]{SQLiteOpenHelperSavedGame.COLUMN_NAME}, null, null, null, null, null, null);
        ArrayList<String> res = new ArrayList<>(cursor.getCount());
        while (cursor.moveToNext()) {
            res.add(cursor.getString(0));
        }
        cursor.close();
        return res;
    }

    public SavedGame getSavedGame(String gameName) {
        Cursor cursor = db.query(SQLiteOpenHelperSavedGame.TABLE_NAME, null,
                SQLiteOpenHelperSavedGame.COLUMN_NAME + " = ?",
                new String[]{gameName}, null, null, null);
        SavedGame savedGame = null;
        if (cursor.moveToFirst()) {
            savedGame = new SavedGame(
                    cursor.getString(0),
                    cursor.getBlob(1),
                    cursor.getInt(2) == 1,
                    cursor.getInt(3),
                    cursor.getInt(4));
        }
        cursor.close();
        return savedGame;
    }


    /**
     * This method renames a saved game.
     *
     * @param previousName the previous name of the saved game.
     * @param newName      the new name for the saved game.
     * @return true if the operation updated one and only one row.
     */
    public boolean rename(String previousName, String newName) {
        ContentValues values = new ContentValues(1);
        values.put(SQLiteOpenHelperSavedGame.COLUMN_NAME, newName);
        return db.update(SQLiteOpenHelperSavedGame.TABLE_NAME, values,
                SQLiteOpenHelperSavedGame.COLUMN_NAME + " = ?",
                new String[]{previousName}) == 1;
    }

    /**
     * This method delete a saved game
     *
     * @param gameName : the name of the saved game to delete.
     * @return true if exactly one saved game have been deleted.
     */
    public boolean delete(String gameName) {
        return db.delete(SQLiteOpenHelperSavedGame.TABLE_NAME,
                SQLiteOpenHelperSavedGame.COLUMN_NAME + " = ?",
                new String[]{gameName}) == 1;
    }
}