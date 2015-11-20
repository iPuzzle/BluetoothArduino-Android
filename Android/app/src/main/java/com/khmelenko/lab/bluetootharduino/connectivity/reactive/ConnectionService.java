package com.khmelenko.lab.bluetootharduino.connectivity.reactive;

import android.app.Service;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Intent;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.support.annotation.Nullable;
import android.util.Log;

import com.khmelenko.lab.bluetootharduino.BtApplication;
import com.khmelenko.lab.bluetootharduino.connectivity.CommunicationThread;

import java.io.IOException;
import java.util.UUID;

import rx.Observable;
import rx.Subscriber;
import rx.Subscription;
import rx.android.schedulers.AndroidSchedulers;
import rx.schedulers.Schedulers;

/**
 * Service for connectivity
 *
 * @author Dmytro Khmelenko
 */
public final class ConnectionService extends Service {

    public static final int RECEIVE_MESSAGE = 1;

    private static final UUID CLIENT_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

    private final IBinder mBinder = new ConnectionBinder();

    private BluetoothSocket mBtSocket;
    private BluetoothDevice mDevice;
    private CommunicationThread mConnectedThread;

    // TODO Replace with Rx for communication thread
    private Handler mHandler;

    /**
     * Binder for communication with the service
     */
    public class ConnectionBinder extends Binder {

        public ConnectionService getService() {
            return ConnectionService.this;
        }
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    /**
     * Establishes connection with the device
     *
     * @param device     Device for connection
     * @param handler    Handler for processing responses
     * @param subscriber Connection subscriber
     */
    public void connect(BluetoothDevice device, Handler handler, Subscriber<BluetoothDevice> subscriber) {

        // disconnect previous connection
        disconnect();
        mDevice = device;
        mHandler = handler;

        subscribeForConnect(subscriber);
    }

    /**
     * Establishes connection with the device
     *
     * @param device     Device for connection
     * @param subscriber Connection subscriber
     */
    public void connect(BluetoothDevice device, Subscriber<BluetoothDevice> subscriber) {
        connect(device, null, subscriber);
    }

    /**
     * Sets a receiver for messages
     *
     * @param handler Handler for processing messages
     */
    public void setReceiver(Handler handler) {
        if (mConnectedThread != null) {
            mConnectedThread.setHandler(handler);
        }
    }

    /**
     * Disconnects connected device
     */
    public void disconnect() {
        if (mBtSocket != null) {
            closeConnection();
            mBtSocket = null;
        }

        stopCommunicationThread();
    }

    /**
     * Stops communication thread
     */
    private void stopCommunicationThread() {
        if (mConnectedThread != null) {
            mConnectedThread.interrupt();
            mConnectedThread = null;
        }
    }

    /**
     * Starts communication thread
     *
     * @param handler Handler for receiving messages
     */
    private void startCommunicationThread(Handler handler) {
        mConnectedThread = new CommunicationThread(mBtSocket, handler);
        mConnectedThread.start();
    }

    /**
     * Sends data to the connected device
     *
     * @param data Data for sending
     */
    public void send(String data) {
        if (mConnectedThread != null) {
            mConnectedThread.send(data);
        }
    }

    /**
     * Checks whether device connected or not
     *
     * @return True, if device connected. False otherwise
     */
    public boolean isConnected() {
        return mBtSocket != null && mBtSocket.isConnected();
    }

    /**
     * Closes socket connection
     */
    private void closeConnection() {
        try {
            mBtSocket.close();
        } catch (IOException e) {
            Log.d(BtApplication.TAG, "Failed to close connection\n" + e.getMessage());
        }
    }

    private Subscription subscribeForConnect(Subscriber<BluetoothDevice> subscriber) {
        return generateObservable()
                .subscribeOn(Schedulers.newThread())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(subscriber);
    }

    private Observable<BluetoothDevice> generateObservable() {
        return Observable.create(new Observable.OnSubscribe<android.bluetooth.BluetoothDevice>() {
            @Override
            public void call(Subscriber<? super android.bluetooth.BluetoothDevice> subscriber) {

                // Start connection
                doConnect();

                if (isConnected()) {
                    startCommunicationThread(mHandler);
                    subscriber.onNext(mDevice);
                    subscriber.onCompleted();
                } else {
                    // TODO subscriber.onError();
                }
            }
        });
    }

    private void doConnect() {
        try {
            mBtSocket = mDevice.createRfcommSocketToServiceRecord(CLIENT_UUID);
        } catch (IOException e) {
            e.printStackTrace();
            Log.d(BtApplication.TAG, "Socket create failed\n" + e.getMessage());
        }

        // Establish the connection.  This will block until it connects.
        Log.d(BtApplication.TAG, "Connecting...");
        try {
            mBtSocket.connect();
            Log.d(BtApplication.TAG, "Connected");
        } catch (IOException e) {
            e.printStackTrace();
            closeConnection();
        }
    }

}
