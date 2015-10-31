package thibautperrin.tetrano.packageConnexions.packageBluetooth;

import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import thibautperrin.tetrano.R;

/**
 * Created by Thibaut PERRIN on 15/09/2015.
 * This class is used to display the bluetooth devices
 * properly in a ListView.
 */
public class ArrayAdapterListDevice extends ArrayAdapter<Device> {
    private final Context context;
    private ArrayList<View> viewList = new ArrayList<>();
    private List<Device> listDevices;


    public ArrayAdapterListDevice(Context context, List<Device> listDevices) {
        super(context, R.layout.row_layout_devices_bluetooth, listDevices);
        this.listDevices = listDevices;
        this.context = context;
    }
    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        View rowView;
        if (position > viewList.size()-1) {
            if (position != viewList.size()) {
                throw  new RuntimeException("PROBLEME PAS COOL");
            }
            LayoutInflater inflater = (LayoutInflater) context
                    .getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            rowView = inflater.inflate(R.layout.row_layout_devices_bluetooth, parent, false);
            viewList.add(rowView);
        } else {
            rowView = viewList.get(position);
        }
        TextView textView = (TextView) rowView.findViewById(R.id.label);
        ImageView imageViewApply = (ImageView) rowView.findViewById(R.id.apply);
        ImageView imageViewReseau = (ImageView) rowView.findViewById(R.id.reseau);
        Device device = listDevices.get(position);
        textView.setText(device.getName());
        if (device.isPlayed()) {
            imageViewApply.setVisibility(View.VISIBLE);
        } else {
            imageViewApply.setVisibility(View.INVISIBLE);
        }
        if (device.isReachable()) {
            imageViewReseau.setVisibility(View.VISIBLE);
        } else {
            imageViewReseau.setVisibility(View.INVISIBLE);
        }
        return rowView;
    }

    public void addSeenBluetoothDevice(BluetoothDevice bluetoothDevice) {
        Device device = new Device(bluetoothDevice);
        int pos = positionIfBelong(listDevices, device);
        if (pos != -1) {
            device.setIsPlayed(listDevices.get(pos).isPlayed());
            listDevices.remove(pos);
        } else {
            pos = listDevices.size();
        }
        insertAtTheGoodPosition(device, pos);
        this.notifyDataSetChanged();
    }

    private int positionIfBelong(List<Device> liste, Device device) {
        int length = liste.size();
        for (int i = 0; i < length; i++) {
            Device d = liste.get(i);
            if (device.getMac().equals(d.getMac())) {
                return i;
            }
        }
        return -1;
    }

    private void insertAtTheGoodPosition(Device device, int InitialPos) {
        int newPos = InitialPos;
        int importance = device.getImportance();
        while (newPos > 0 && listDevices.get(newPos-1).getImportance() < importance) {
            newPos--;
        }
        listDevices.add(newPos, device);
    }

    public Device getDevice(int position) {
        return listDevices.get(position);
    }
}
