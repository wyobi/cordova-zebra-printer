package com.apache.cordova.plugins.zebra;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.pm.PackageManager;
import android.os.Build;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.CallbackContext;

import org.apache.cordova.PluginResult;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import com.megster.cordova.ble.central.Peripheral;
import com.zebra.sdk.comm.BluetoothConnection;
import com.zebra.sdk.comm.Connection;
import com.zebra.sdk.comm.ConnectionException;

import com.zebra.sdk.btleComm.BluetoothLeConnection;
import com.zebra.sdk.btleComm.BluetoothLeDiscoverer;
import com.zebra.sdk.btleComm.DiscoveredPrinterBluetoothLe;
import com.zebra.sdk.printer.PrinterLanguage;
import com.zebra.sdk.printer.PrinterStatus;
import com.zebra.sdk.printer.ZebraPrinterFactory;

import com.zebra.sdk.printer.ZebraPrinterLanguageUnknownException;


public class ZebraPrinter extends CordovaPlugin implements AutoCloseable {
    private static Connection printerConnection;
    private static com.zebra.sdk.printer.ZebraPrinter printer;
    private static PrinterLanguage printerLanguage;

    private static HashMap<String, String> addressTypeMap = new HashMap<String, String>();

    private static final String lock = "ZebraPluginLock";

    @Override
    public boolean execute(String action, JSONArray args, CallbackContext callbackContext) {
        Log.v("EMO", "Execute on ZebraPrinter Plugin called");
        switch (action) {
            case "discover":
                this.discover(callbackContext);
                return true;
            case "connect":
                this.connect(args, callbackContext);
                return true;
            case "print":
                this.print(args, callbackContext);
                return true;
            case "isConnected":
                this.isConnected(callbackContext);
                return true;
            case "disconnect":
                this.disconnect(callbackContext);
                return true;
            case "printerStatus":
                this.printerStatus(callbackContext);
                return true;
        }
        return false;
    }

    /***
     * Get the printer status. Cordova boilerplate.
     * @param callbackContext
     */
    private void printerStatus(final CallbackContext callbackContext) {
        final ZebraPrinter instance = this;

        cordova.getThreadPool().execute(() -> {
            JSONObject status = instance.GetPrinterStatus();
            if (status != null) {
                callbackContext.success(status);
            } else {
                callbackContext.error("Failed to get status.");
            }
        });
    }

    /***
     * Discover Zebra bluetooth devices. Cordova boilerplate
     * @param callbackContext
     */
    private void discover(final CallbackContext callbackContext) {
        final ZebraPrinter instance = this;
        cordova.getThreadPool().execute(() -> {
            JSONArray printers = instance.NonZebraDiscovery();
            if (printers != null) {
                callbackContext.success(printers);
            } else {
                callbackContext.error("Discovery Failed");
            }
        });
    }

    /***
     * Connect to a printer identified by it's macAddress. Cordova boilerplate.
     * @param args
     * @param callbackContext
     */
    private void connect(JSONArray args, final CallbackContext callbackContext) {
        final ZebraPrinter instance = this;
        final String address;
        try {
            address = args.getString(0);
        } catch (JSONException e) {
            e.printStackTrace();
            callbackContext.error("Connect Failed: " + e.getMessage());
            return;
        }

        String type = null;
        if(addressTypeMap.containsKey(address)) {
            type = addressTypeMap.get(address);
        }

        if(type == "DEVICE_TYPE_CLASSIC" || type == "UNKNOWN" || type == null) {
            cordova.getThreadPool().execute(() -> {
                if (instance.connect(address)) {
                    callbackContext.success();
                }
            });
        }
        else if(type == "DEVICE_TYPE_DUAL") {
            cordova.getThreadPool().execute(() -> {
                if (instance.connectBLE(address)) {
                    callbackContext.success();
                } else {
                    if (instance.connect(address)) {
                        callbackContext.success();
                    } else {
                        callbackContext.error("Connect Failed");
                    }
                }
            });
        }
        else if(type == "DEVICE_TYPE_LE") {
            cordova.getThreadPool().execute(() -> {
                if (instance.connectBLE(address)) {
                    callbackContext.success();
                } else {
                    callbackContext.error("Connect Failed");
                }
            });
        }
    }

    /***
     * Print the cpcl to the currently connected zebra printer. Cordova boilerplate
     * @param args
     * @param callbackContext
     */
    private void print(JSONArray args, final CallbackContext callbackContext) {
        final ZebraPrinter instance = this;

        final String zplOrcpcl;
        try {
            zplOrcpcl = args.getString(0);
        } catch (JSONException e) {
            e.printStackTrace();
            callbackContext.error("Print Failed: " + e.getMessage());
            return;
        }
        cordova.getThreadPool().execute(() -> {
            if (instance.printCPCLOrZpl(zplOrcpcl)) {
                callbackContext.success();
            } else {
                callbackContext.error("Print Failed. Printer Likely Disconnected.");
            }
        });
    }

    /***
     * Determine if the printer is currently connected. Cordova boilerplate.
     * @param callbackContext
     */
    private void isConnected(final CallbackContext callbackContext) {
        final ZebraPrinter instance = this;
        cordova.getThreadPool().execute(() -> {
            boolean result = instance.isConnected();
            callbackContext.sendPluginResult(new PluginResult(PluginResult.Status.OK, result));
            callbackContext.success();
        });
    }

    /***
     * Disconnect from the currently connected printer. Cordova boilerplate.
     * @param callbackContext
     */
    private void disconnect(final CallbackContext callbackContext) {
        final ZebraPrinter instance = this;
        cordova.getThreadPool().execute(() -> {
            instance.disconnect();
            callbackContext.success();
        });
    }

    /***
     * Prints the CPCL formatted message to the currently connected printer.
     * @param cpcl
     * @return
     */
    private boolean printCPCLOrZpl(String cpcl) {
        try {
            if (!isConnected()) {
                Log.v("EMO", "Printer Not Connected");
                return false;
            }

            byte[] configLabel = cpcl.getBytes();
            printerConnection.write(configLabel);

            if (printerConnection instanceof BluetoothConnection) {
                String friendlyName = ((BluetoothConnection) printerConnection).getFriendlyName();
                System.out.println(friendlyName);
            }
            else if (printerConnection instanceof BluetoothLeConnection) {
                String friendlyName = ((BluetoothLeConnection) printerConnection).getFriendlyName();
                System.out.println(friendlyName);
            }
        } catch (ConnectionException e) {
            Log.v("EMO", "Error Printing", e);
            return false;
        }
        return true;
    }

    /***
     * Returns boolean indicating if there is a printer currently connected
     * @return
     */
    private boolean isConnected() {
        return printerConnection != null && printerConnection.isConnected();
    }

    /***
     * Connects to a printer identified by the macAddress
     * @param macAddress
     * @return
     */
    private boolean connect(String macAddress) {
        synchronized (ZebraPrinter.lock) {
            Log.v("EMO", "Printer - Connecting to " + macAddress);
            //disconnect if we are already connected
            try {
                if (printerConnection != null && printerConnection.isConnected()) {
                    printerConnection.close();
                    printerConnection = null;
                    printer = null;
                    printerLanguage = null;
                }
            }catch (Exception ex){
                Log.v("EMO", "Printer - Failed to close connection before connecting", ex);
            }

            //create a new BT connection
            printerConnection = new BluetoothConnection(macAddress);

            //check that it isn't null
            if(printerConnection == null){
                return false;
            }

            //open that connection
            try {
                printerConnection.open();
            } catch (Exception e) {
                Log.v("EMO", "Printer - Failed to open connection", e);
                printerConnection = null;
                printer = null;
                printerLanguage = null;
                return false;
            }

            //check if it opened
            if (printerConnection != null && printerConnection.isConnected()) {
                //try to get a printer
                try {
                    printer = ZebraPrinterFactory.getInstance(printerConnection);
                    printerLanguage = printer.getPrinterControlLanguage();

                } catch (Exception e) {
                    Log.v("EMO", "Printer - Error...", e);
                    printerLanguage = null;
                    closePrinter();
                    return false;
                }
                return true;
            }else {
                //printer was null or not connected
                return false;
            }
        }
    }

    private boolean connectBLE(String macAddress) {
        synchronized (ZebraPrinter.lock) {
            Log.v("EMO", "Printer - Connecting to " + macAddress);
            //disconnect if we are already connected
            try {
                if (printerConnection != null && printerConnection.isConnected()) {
                    printerConnection.close();
                    printerConnection = null;
                    printer = null;
                    printerLanguage = null;
                }
            }catch (Exception ex){
                Log.v("EMO", "Printer - Failed to close connection before connecting", ex);
            }

            //create a new BT connection
            printerConnection = new BluetoothLeConnection(macAddress, this.cordova.getContext());

            //check that it isn't null
            if(printerConnection == null){
                return false;
            }

            //open that connection
            try {
                printerConnection.open();
            } catch (Exception e) {
                Log.v("EMO", "Printer - Failed to open connection", e);
                printerConnection = null;
                printer = null;
                printerLanguage = null;
                return false;
            }

            //check if it opened
            if (printerConnection != null && printerConnection.isConnected()) {
                //try to get a printer
                try {
                    printer = ZebraPrinterFactory.getInstance(printerConnection);
                    printerLanguage = printer.getPrinterControlLanguage();
                } catch (Exception e) {
                    Log.v("EMO", "Printer - Error...", e);
                    printerLanguage = null;
                    closePrinter();
                    return false;
                }
                return true;
            }else {
                //printer was null or not connected
                return false;
            }
        }
    }

    /***
     * Disconnects from the currently connected printer
     */
    private void disconnect() {
        synchronized (ZebraPrinter.lock) {
            closePrinter();
        }
    }

    /***
     * Essentially does a disconnect but outside of the lock. Only use this inside of a lock.
     */
    private void closePrinter(){
        try {
            if (printerConnection != null) {
                printerConnection.close();
                printerConnection = null;
            }
            printer = null;
        } catch (ConnectionException e) {
            e.printStackTrace();
        }
    }

    /***
     * Get the status of the currently connected printer
     * @return
     */
    private JSONObject GetPrinterStatus() {
        JSONObject errorStatus = new JSONObject();
        try{
            errorStatus.put("connected", false);
            errorStatus.put("isReadyToPrint", false);
            errorStatus.put("isPaused", false);
            errorStatus.put("isReceiveBufferFull", false);
            errorStatus.put("isRibbonOut", false);
            errorStatus.put("isPaperOut", false);
            errorStatus.put("isHeadTooHot", false);
            errorStatus.put("isHeadOpen", false);
            errorStatus.put("isHeadCold", false);
            errorStatus.put("isPartialFormatInProgress", false);
            errorStatus.put("controlLanguage", null);
        } catch (Exception e) {
            System.err.println(e.getMessage());
        }

        if (isConnected() && printer != null) {
            try{
                JSONObject status = new JSONObject();
                PrinterStatus zebraStatus = printer.getCurrentStatus();
                printerLanguage = printer.getPrinterControlLanguage();
                status.put("connected", true);
                status.put("isReadyToPrint", zebraStatus.isReadyToPrint);
                status.put("isPaused", zebraStatus.isPaused);
                status.put("isReceiveBufferFull", zebraStatus.isReceiveBufferFull);
                status.put("isRibbonOut", zebraStatus.isRibbonOut);
                status.put("isPaperOut", zebraStatus.isPaperOut);
                status.put("isHeadTooHot", zebraStatus.isHeadTooHot);
                status.put("isHeadOpen", zebraStatus.isHeadOpen);
                status.put("isHeadCold", zebraStatus.isHeadCold);
                status.put("isPartialFormatInProgress", false);
                status.put("controlLanguage", printerLanguage.toString());
                return status;
            } catch (Exception e) {
                System.err.println(e.getMessage());
                return errorStatus;
            }
        }

        return errorStatus;
    }

    /***
     * Find Zebra printers we can connect to
     * @return
     */
    private JSONArray NonZebraDiscovery() {
        JSONArray printers = new JSONArray();

        if (ContextCompat.checkSelfPermission(this.cordova.getContext(), Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_DENIED)
        {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
            {
                ActivityCompat.requestPermissions(this.cordova.getActivity(), new String[]{Manifest.permission.BLUETOOTH_CONNECT}, 2);
            }
        }

        if (ContextCompat.checkSelfPermission(this.cordova.getContext(), Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_DENIED)
        {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
            {
                ActivityCompat.requestPermissions(this.cordova.getActivity(), new String[]{Manifest.permission.BLUETOOTH_SCAN}, 2);
            }
        }

        try {
            BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
            Set<BluetoothDevice> devices = adapter.getBondedDevices();

            for (BluetoothDevice device : devices) {
                String name = device.getName();
                String mac = device.getAddress();
                int deviceTypeNum = device.getType();
                String deviceType = "DEVICE_TYPE_CLASSIC";

                if(deviceTypeNum == BluetoothDevice.DEVICE_TYPE_DUAL) {
                    deviceType = "DEVICE_TYPE_DUAL";
                }
                else if(deviceTypeNum == BluetoothDevice.DEVICE_TYPE_LE) {
                    deviceType = "DEVICE_TYPE_LE";
                }
                else if(deviceTypeNum > BluetoothDevice.DEVICE_TYPE_LE) {
                    deviceType = "UNKNOWN";
                }

                addressTypeMap.put(mac, deviceType);

                JSONObject p = new JSONObject();
                p.put("name", name);
                p.put("address", mac);
                p.put("type", deviceType);
                printers.put(p);

            }
        } catch (Exception e) {
            System.err.println(e.getMessage());
        }

        return printers;
    }

    public void onDestroy() {
        disconnect();
    }

    public void onReset() {
        disconnect();
    }

    @Override
    public void close() throws Exception {
        disconnect();
    }
}
