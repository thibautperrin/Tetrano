package thibautperrin.tetrano.packageConnexions.packageBluetooth;

import android.app.Activity;
import android.app.ProgressDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import thibautperrin.tetrano.ApplicationTetrano;
import thibautperrin.tetrano.JeuActivity;
import thibautperrin.tetrano.R;
import thibautperrin.tetrano.StaticFields;

public class ConnexionBluetoothActivity extends AppCompatActivity {

    private BluetoothService bluetoothService;
    private BluetoothAdapter mBluetoothAdapter;
    private Button buttonDecouvrable;
    private Button scanButton;
    private TextView textVousEtesTrouvable;
    private TextView textScan;
    private Toast toast;
    private ApplicationTetrano app;
    private boolean receiverRegistre = false;
    private ArrayAdapterListDevice adapterListDevice;
    private ListView listDevice;
    private ProgressDialog dialog;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_connexion_bluetooth);

        // Set result CANCELED in case the user backs out
        setResult(Activity.RESULT_CANCELED);

        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (mBluetoothAdapter == null) {
            afficher(getResources().getString(R.string.Sentence_Bluetooth_Not_Supported));
            this.finish();
        }
        scanButton = (Button) findViewById(R.id.button_scan);
        textVousEtesTrouvable = (TextView) findViewById(R.id.textVousEtesTrouvable);
        buttonDecouvrable = (Button) findViewById(R.id.buttonDecouverable);
        textScan = (TextView) findViewById(R.id.textSelectDevice);
        app = (ApplicationTetrano) getApplication();
        listDevice = (ListView) findViewById(R.id.listDevices);
        adapterListDevice = new ArrayAdapterListDevice(this, app.getMacAdressBluetooth());
        listDevice.setAdapter(adapterListDevice);
    }

    public void initialisationCasBluetoothEnabled() {
        if (mBluetoothAdapter.getScanMode() == BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE) {
            textVousEtesTrouvable.setVisibility(View.VISIBLE);
            buttonDecouvrable.setVisibility(View.GONE);
        } else {
            textVousEtesTrouvable.setVisibility(View.GONE);
            buttonDecouvrable.setVisibility(View.VISIBLE);
        }

        BluetoothHandler bluetoothHandler = new BluetoothHandler(this);
        bluetoothService = new BluetoothService(bluetoothHandler);
        app.setBluetoothService(bluetoothService, bluetoothHandler);

        scanButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                doDiscovery();
                v.setVisibility(View.GONE);
                textScan.setVisibility(View.VISIBLE);
            }
        });

        buttonDecouvrable.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mBluetoothAdapter.getScanMode() !=
                        BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE) {
                    Intent discoverableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);
                    discoverableIntent.putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 300);
                    startActivity(discoverableIntent);
                }
            }
        });


        IntentFilter filter = new IntentFilter();
        filter.addAction(BluetoothDevice.ACTION_FOUND);
        filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
        filter.addAction(BluetoothAdapter.ACTION_SCAN_MODE_CHANGED);
        filter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);
        this.registerReceiver(mReceiver, filter);
        receiverRegistre = true;

        // Get a set of currently paired devices
        listDevice.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view,
                                    int position, long id) {
                Device device = adapterListDevice.getDevice(position);
                String name = device.getName();
                displayConnectingDialog(name);
                String address = device.getMac();
                // Cancel discovery because it's costly and we're about to connect
                mBluetoothAdapter.cancelDiscovery();
                BluetoothDevice bluetoothDevice = mBluetoothAdapter.getRemoteDevice(address);
                bluetoothService.connect(bluetoothDevice);
            }
        });

        bluetoothService.start();
    }


    @Override
    public void onStart() {
        super.onStart();
        // If BT is not on, request that it be enabled.
        // setupChat() will then be called during onActivityResult
        if (!mBluetoothAdapter.isEnabled()) {
            Intent enableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableIntent, StaticFields.REQUEST_ENABLE_BLUETOOTH);
        } else {
            initialisationCasBluetoothEnabled();
        }
    }

    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {
            case StaticFields.REQUEST_PLAY:
                sortir(true);
                break;
            case StaticFields.REQUEST_ENABLE_BLUETOOTH:
                // When the request to enable Bluetooth returns
                if (resultCode == Activity.RESULT_OK) {
                    initialisationCasBluetoothEnabled();
                } else {
                    // User did not enable Bluetooth or an error occurred
                    sortir(false);
                }
                break;
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        // Make sure we're not doing discovery anymore
        if (mBluetoothAdapter != null) {
            mBluetoothAdapter.cancelDiscovery();
        }

        // Unregister broadcast listeners
        if (receiverRegistre) {
            this.unregisterReceiver(mReceiver);
        }
    }

    /**
     * Start device discover with the BluetoothAdapter
     */
    private void doDiscovery() {
        // If we're already discovering, stop it
        if (mBluetoothAdapter.isDiscovering()) {
            mBluetoothAdapter.cancelDiscovery();
        }

        // Request discover from BluetoothAdapter
        mBluetoothAdapter.startDiscovery();
    }


    /**
     * The BroadcastReceiver that listens for discovered devices and changes the title when
     * discovery is finished
     */
    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();

            // When discovery finds a device
            if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                // Get the BluetoothDevice object from the Intent
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                adapterListDevice.addSeenBluetoothDevice(device);

                // When discovery is finished, change the Activity title
            } else if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(action)) {
                textScan.setVisibility(View.GONE);
                scanButton.setVisibility(View.VISIBLE);
            } else if (BluetoothAdapter.ACTION_SCAN_MODE_CHANGED.equals(action)) {
                int scanMode = intent.getIntExtra(BluetoothAdapter.EXTRA_SCAN_MODE, 0);
                if (scanMode == BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE) {
                    textVousEtesTrouvable.setVisibility(View.VISIBLE);
                    buttonDecouvrable.setVisibility(View.GONE);
                } else {
                    textVousEtesTrouvable.setVisibility(View.GONE);
                    buttonDecouvrable.setVisibility(View.VISIBLE);
                }
            } else if (BluetoothAdapter.ACTION_STATE_CHANGED.equals(action)) {
                int etat = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, 0);
                if (etat == BluetoothAdapter.STATE_OFF) {
                    sortir(false);
                }
            }
        }
    };

    public void afficher(String string) {
        if (toast != null) {
            toast.cancel();
        }
        toast = Toast.makeText(this, string, Toast.LENGTH_SHORT);
        toast.show();
    }

    public void sortir(boolean ok) {
        if (ok) {
            setResult(Activity.RESULT_OK);
        } else {
            setResult(Activity.RESULT_CANCELED);
        }
        finish();
    }

    public void apresConnexion(boolean b, BluetoothDevice bluetoothDevice) {
        int couleurUtilisateur = app.getCouleurUtilisateur();
        String nomUtilisateur = app.getNomUtilisateur();
        int couleurAdversaire = app.getCouleurAdversaire();
        String nomAdversaire = getResources().getString(R.string.Bluetooth_Adversary);
        Bundle bundle = new Bundle();
        bundle.putString(StaticFields.LABEL_USER_NAME, nomUtilisateur);
        bundle.putString(StaticFields.LABEL_ADVERSARY_NAME, nomAdversaire);
        bundle.putInt(StaticFields.LABEL_USER_COLOR, couleurUtilisateur);
        bundle.putInt(StaticFields.LABEL_ADVERSARY_COLOR, couleurAdversaire);
        bundle.putInt(StaticFields.LABEL_GAME_CODE, StaticFields.GAME_BLUETOOTH);
        bundle.putBoolean(StaticFields.LABEL_USER_BEGINS, b);
        bundle.putString(StaticFields.LABEL_DIST_MAC_ADDRESS, bluetoothDevice.getAddress());
        bundle.putString(StaticFields.LABEL_DIST_DEVICE_NAME, bluetoothDevice.getName());
        Intent intent = new Intent(ConnexionBluetoothActivity.this, JeuActivity.class);
        intent.putExtras(bundle);
        dismissConnectingDialog();
        startActivityForResult(intent, StaticFields.REQUEST_PLAY);
    }

    public void displayConnectingDialog(String nameAdversary) {
        if (dialog != null) {
            dialog.dismiss();
            dialog.setMessage(nameAdversary);
        } else {
            dialog = new ProgressDialog(this);
            dialog.setTitle(R.string.Connecting);
            dialog.setMessage(nameAdversary);
            dialog.setIndeterminate(true);
            dialog.setCancelable(true);
            dialog.setButton(DialogInterface.BUTTON_NEGATIVE,
                    getResources().getString(R.string.Cancel),
                    new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    dismissConnectingDialog();
                }
            });
            dialog.setOnDismissListener(new DialogInterface.OnDismissListener() {
                @Override
                public void onDismiss(DialogInterface dialog) {
                    if (bluetoothService.getState() == StaticFields.STATE_CONNECTING) {
                        bluetoothService.start();
                    }
                }
            });
        }
        dialog.show();
    }
    public void dismissConnectingDialog() {
        if (dialog != null) {
            dialog.dismiss();
        }
    }
}