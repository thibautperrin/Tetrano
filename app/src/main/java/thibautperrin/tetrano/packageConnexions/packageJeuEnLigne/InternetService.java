package thibautperrin.tetrano.packageConnexions.packageJeuEnLigne;

import android.os.Handler;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;

import thibautperrin.tetrano.StaticFields;


/**
 * Cette classe possède les méthodes permettant la connexion internet dans le
 * cas d'une partie online.
 * Created by Thibaut PERRIN on 15/06/2015.
 */
public class InternetService {
    private int mState;
    private Handler mHandler;
    private ConnectThread mConnectThread;
    private ConnectedThread mConnectedThread;

    public InternetService(Handler handler) {
        mState = StaticFields.STATE_NONE;
        mHandler = handler;
    }

    private synchronized void setState(int state) {
        mState = state;
        mHandler.obtainMessage(StaticFields.MESSAGE_SERVICE_STATE_CHANGE, state, -1).sendToTarget();
    }
    public synchronized int getState() {
        return mState;
    }

    public synchronized void start() {

        // Cancel any thread attempting to make a connection
        if (mConnectThread != null) {
            mConnectThread.cancel();
            mConnectThread = null;
        }

        // Cancel any thread currently running a connection
        if (mConnectedThread != null) {
            mConnectedThread.cancel();
            mConnectedThread = null;
        }

        setState(StaticFields.STATE_CONNECTING);
        mConnectThread = new ConnectThread();
        mConnectThread.start();
    }

    private synchronized void connected(Socket socket) {

        // Cancel the thread that completed the connection
        if (mConnectThread != null) {
            mConnectThread.cancel();
            mConnectThread = null;
        }

        // Cancel any thread currently running a connection
        if (mConnectedThread != null) {
            mConnectedThread.cancel();
            mConnectedThread = null;
        }

        // Start the thread to manage the connection and perform transmissions
        mConnectedThread = new ConnectedThread(socket);
        mConnectedThread.start();
        setState(StaticFields.STATE_CONNECTED);
    }

    /**
     * Stop all threads
     */
    public synchronized void stop() {
        if (mConnectThread != null) {
            mConnectThread.cancel();
            mConnectThread = null;
        }

        if (mConnectedThread != null) {
            Socket socket = mConnectedThread.getSocket();
            PrevenirSortirThread sortirT = new PrevenirSortirThread(socket);
            sortirT.start();
        }
    }

    public void write(byte[] out) {
        // Create temporary object
        ConnectedThread r;
        // Synchronize a copy of the ConnectedThread
        synchronized (this) {
            if (mState != StaticFields.STATE_CONNECTED) return;
            r = mConnectedThread;
        }
        // Perform the write unsynchronized
        r.write(out);
    }

    /**
     * Indicate that the connection attempt failed and notify the UI Activity.
     */
    private void connectionFailed(boolean initiatingConnection) {
        // Send a failure message back to the Activity
        if (initiatingConnection) {
            mHandler.obtainMessage(StaticFields.MESSAGE_SERVICE_CONNECTION_FAILED_INITIATION)
                    .sendToTarget();
        } else {
            mHandler.obtainMessage(StaticFields.MESSAGE_SERVICE_CONNECTION_FAILED_NOT_IN_INITIATION)
                    .sendToTarget();
        }
    }


    private class ConnectThread extends Thread {
        private Socket socket = null;

        @Override
        public void run() {
            try {
                socket = new Socket(StaticFields.SERVER_IP_ADDRESS, StaticFields.SERVER_PORT_NUMBER);
            } catch (IOException e) {
                try {
                    if (socket != null) {
                        socket.close();
                        socket = null;
                    }
                } catch (IOException e1) {
                    e1.printStackTrace();
                }
                e.printStackTrace();
                connectionFailed(true);
            }
            synchronized (InternetService.this) {
                mConnectThread = null;
            }
            if (socket != null) connected(socket);
        }

        public void cancel() {
            try {
                if (socket != null) {
                    socket.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private class ConnectedThread extends Thread {
        private final Socket socket;
        private final InputStream inStream;
        private final OutputStream outStream;

        public ConnectedThread(Socket socket) {
            InputStream tmpIn = null;
            OutputStream tmpOut = null;
            try {
                tmpIn = socket.getInputStream();
                tmpOut = socket.getOutputStream();
            } catch (IOException e) {
                e.printStackTrace();
            }
            this.inStream = tmpIn;
            this.outStream = tmpOut;
            this.socket = socket;
        }

        @Override
        public void run() {
            byte[] buffer;
            int bytes;
            while (true) {
                try {
                    buffer = new byte[200];

                    // Read from the InputStream
                    bytes = inStream.read(buffer);
                    if (bytes >0) {

                        // Send the obtained bytes to the UI Activity
                        mHandler.obtainMessage(StaticFields.MESSAGE_SERVICE_READ, bytes, -1, buffer)
                                .sendToTarget();
                    } else {
                        break;
                    }
                } catch (IOException e) {
                    connectionFailed(false);
                    break;
                }
            }
        }

        public void cancel() {
            try {
                outStream.close();
                inStream.close();
                socket.close();
            } catch (IOException e) {
            }
        }

        public void write(byte[] buffer) {
            try {
                outStream.write(buffer);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        public Socket getSocket() {
            return socket;
        }
    }

    private class PrevenirSortirThread extends Thread {
        private final Socket socket;
        public PrevenirSortirThread(Socket socket) {
            this.socket = socket;
        }

        @Override
        public void run() {
            try {
                OutputStream outStream = socket.getOutputStream();
                byte[] buffer = new byte[1];
                buffer[0] = StaticFields.INTERNET_CLOSE_CONNECTION;
                outStream.write(buffer);
                outStream.close();
                socket.close();
                setState(StaticFields.STATE_NONE);
            } catch (IOException e) {
                System.out.println("Fichtre, je n'arrive même pas à sortir...");
            } finally {
                if (mConnectedThread != null) mConnectedThread.cancel();
                mConnectThread = null;
                mConnectedThread = null;
            }
        }
    }
}
