package com.apache.cordova.plugins.zebra;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;

import com.zebra.sdk.printer.discovery.UsbDiscoverer;

public abstract class UsbHelper {

    private BroadcastReceiver usbDisconnectReceiver;
    private BroadcastReceiver usbPermissionReceiver;
    private Activity parentActivity;

    public static final String USB_PERMISSION_GRANTED_ACTION = "com.apache.cordova.plugins.zebra.usbPermissionGranted";

    public UsbHelper(Activity parentActivity) {
        this.parentActivity = parentActivity;
    }

    public void onCreate(Intent intent) {
        if (android.os.Build.VERSION.SDK_INT < 12) {
            return;
        }

        UsbDevice device = (UsbDevice) intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
        if (device != null && UsbDiscoverer.isZebraUsbDevice(device)) {
            usbConnectedAndPermissionGranted(device);
        }
        usbDisconnectReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                final String action = intent.getAction();

                if (UsbManager.ACTION_USB_DEVICE_DETACHED.equals(action)) {
                    UsbDevice device = (UsbDevice)intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                    if (device != null && UsbDiscoverer.isZebraUsbDevice(device)) {
                        usbDisconnected(device);
                    }
                }
            }
        };

        usbPermissionReceiver = new BroadcastReceiver() {

            @Override
            public void onReceive(Context context, Intent intent) {
                final String action = intent.getAction();

                if (USB_PERMISSION_GRANTED_ACTION.equals(action)) {
                    synchronized (this) {
                        UsbDevice device = (UsbDevice)intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                        boolean permissionGranted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false);
                        if (device != null && permissionGranted && UsbDiscoverer.isZebraUsbDevice(device)) {
                            usbConnectedAndPermissionGranted(device);
                        }
                    }
                }
            }
        };

        IntentFilter filter = new IntentFilter();
        filter.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED);
        parentActivity.registerReceiver(usbDisconnectReceiver, filter);

        filter = new IntentFilter();
        filter.addAction(USB_PERMISSION_GRANTED_ACTION);
        parentActivity.registerReceiver(usbPermissionReceiver, filter);
    }

    public void onResume() {
        IntentFilter filter = new IntentFilter();
        filter.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED);
        parentActivity.registerReceiver(usbDisconnectReceiver, filter);

        filter = new IntentFilter();
        filter.addAction(USB_PERMISSION_GRANTED_ACTION);
        parentActivity.registerReceiver(usbPermissionReceiver, filter);
    }

    public void onPause() {
        parentActivity.unregisterReceiver(usbDisconnectReceiver);
        parentActivity.unregisterReceiver(usbPermissionReceiver);
    }


    public void onNewIntent(Intent intent) {
        if (android.os.Build.VERSION.SDK_INT < 12) {
            return;
        }

        UsbDevice device = (UsbDevice) intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
        if (device != null) {
            usbConnectedAndPermissionGranted(device);
        }
    }

    public void requestUsbPermission(final UsbManager manager, final UsbDevice device) {
        PendingIntent permissionIntent = PendingIntent.getBroadcast(parentActivity, 0, new Intent(USB_PERMISSION_GRANTED_ACTION), PendingIntent.FLAG_IMMUTABLE);
        manager.requestPermission(device, permissionIntent);
    }

    public abstract void usbConnectedAndPermissionGranted(UsbDevice device) ;
    public abstract void usbDisconnected(UsbDevice device);
}

