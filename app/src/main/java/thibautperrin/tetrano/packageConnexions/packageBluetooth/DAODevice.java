package thibautperrin.tetrano.packageConnexions.packageBluetooth;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by Thibaut PERRIN on 14/09/2015.
 * This class is the Data Access Object for the database concerning
 * the bluetooth devices already played.
 */
public class DAODevice {
    private SQLiteDatabase db;
    private SQLiteOpenHelperBluetoothDevices dbHelper;
    private static String[] allColumns =
            {SQLiteOpenHelperBluetoothDevices.COLUMN_MAC,
            SQLiteOpenHelperBluetoothDevices.COLUMN_NAME};


    public DAODevice(Context context) {
        dbHelper = new SQLiteOpenHelperBluetoothDevices(context);
    }

    public void open() throws SQLException {
        db = dbHelper.getWritableDatabase();
    }

    /**
     * This method adds a device to the database containing already played
     * devices. If the MAC address already exist, the row is updated.
     * @param mac : the MAC address
     * @param name : The name of the device
     * @return true if the device is a new one, (meaning a new MAC address)
     */
    public boolean addRemoteBluetoothDevice(String mac, String name) {
        // We look for row having the same MAC.
        Cursor cursor = db.query(SQLiteOpenHelperBluetoothDevices.TABLE_NAME,allColumns,
                SQLiteOpenHelperBluetoothDevices.COLUMN_MAC + " = ?",
                new String[]{mac}, null, null, null);
        // If cursor isn't empty:
        if (cursor.moveToFirst()) {
            // If the name is different in the row, we update it:
            if (!cursor.getString(1).equals(name)) {
                ContentValues values = new ContentValues();
                values.put(SQLiteOpenHelperBluetoothDevices.COLUMN_NAME, name);
                db.update(SQLiteOpenHelperBluetoothDevices.TABLE_NAME, values,
                        SQLiteOpenHelperBluetoothDevices.COLUMN_MAC + " = ?",
                        new String[]{mac});
            }
            cursor.close();
            return false;
        } else {
            cursor.close();
            // There is no row having this MAC, we can add it:
            ContentValues values = new ContentValues();
            values.put(SQLiteOpenHelperBluetoothDevices.COLUMN_MAC, mac);
            values.put(SQLiteOpenHelperBluetoothDevices.COLUMN_NAME, name);
            db.insert(SQLiteOpenHelperBluetoothDevices.TABLE_NAME, null, values);
            return true;
        }
    }

    public List<Device> getRemoteBluetoothDevices() {
        ArrayList<Device> result = new ArrayList<>();
        Cursor cursor = db.query(SQLiteOpenHelperBluetoothDevices.TABLE_NAME,
                allColumns, null, null, null, null, null);
        while (cursor.moveToNext()) {
            result.add(new Device(cursor.getString(0), cursor.getString(1)));
        }
        cursor.close();
        return result;
    }

}
