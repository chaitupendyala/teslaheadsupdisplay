package com.chaitalkstech.teslaheadsupdisplay

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class BluetoothReceiver : BroadcastReceiver() {
    @SuppressLint("MissingPermission")
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (BluetoothDevice.ACTION_ACL_CONNECTED == action) {
            val device: BluetoothDevice? = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
            val deviceName = device?.name
            Log.d("BluetoothReceiver", "Device Connected: $deviceName")
            
            if (deviceName == "Chai's Tesla") {
                Log.d("BluetoothReceiver", "Match found! Launching app...")
                val launchIntent = Intent(context, MainActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
                }
                context.startActivity(launchIntent)
            }
        }
    }
}
