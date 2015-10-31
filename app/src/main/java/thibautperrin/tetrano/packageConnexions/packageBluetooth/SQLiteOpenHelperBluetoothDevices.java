package thibautperrin.tetrano.packageConnexions.packageBluetooth;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import thibautperrin.tetrano.StaticFields;

/**
 * Created by Thibaut PERRIN on 14/09/2015.
 */
public class SQLiteOpenHelperBluetoothDevices extends SQLiteOpenHelper {
    private static final int DATABASE_VERSION = 1;
    protected static final String TABLE_NAME = "devices";
    protected static final String COLUMN_MAC = "mac";
    protected static final String COLUMN_NAME = "name";

    private static final String DATABASE_CREATE = "CREATE TABLE " + TABLE_NAME + " (" +
            COLUMN_MAC + " TEXT PRIMARY KEY, " +
            COLUMN_NAME + " TEXT);";

    public SQLiteOpenHelperBluetoothDevices(Context context) {
        super(context, StaticFields.BLUETOOTH_DEVICES_DATABASE_FILE, null, DATABASE_VERSION);
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
