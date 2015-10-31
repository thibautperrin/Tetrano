package thibautperrin.tetrano.packageConnexions.packageBluetooth;

import android.bluetooth.BluetoothDevice;

/**
 * Created by Thibaut PERRIN on 14/09/2015.
 * This class is a java bean representing a Bluetooth Device.
 */
public class Device {

    private String mac;
    private String name;
    private boolean isPlayed = true;
    private boolean isReachable = false;

    public Device(String macAdress, String name) {
        this.mac = macAdress;
        this.name = name;
    }

    public Device(BluetoothDevice bluetoothDevice) {
        this.mac = bluetoothDevice.getAddress();
        this.name = bluetoothDevice.getName();
        this.isPlayed = false;
        this.isReachable = true;
    }

    public void setIsPlayed(boolean isPlayed) {
        this.isPlayed = isPlayed;
    }


    public boolean isPlayed() {
        return isPlayed;
    }

    public boolean isReachable() {
        return isReachable;
    }

    public String getMac() {
        return mac;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getImportance() {
        if (isReachable) {
            if (isPlayed) {
                return 3;
            } else {
                return 2;
            }
        } else if (isPlayed) {
            return 1;
        } else {
            return 0;
        }
    }

    @Override
    public String toString() {
        return mac + " " + name + (isPlayed ? "Played" : "not played") +
                " " + (isReachable ? "Reachable" : "not reachable");
    }
}