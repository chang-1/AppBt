package com.example.btmessage

import android.Manifest
import android.bluetooth.*
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.util.Log
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import kotlinx.android.synthetic.main.activity_main.*

class MainActivity : AppCompatActivity(), AdapterView.OnItemSelectedListener {

    private var mBtAdapter: BluetoothAdapter? = null
    private var mDevices: MutableList<BluetoothDevice> = mutableListOf()
    private var mSelectedDevice: BluetoothDevice? = null
    private var mConnectionThread: ConnectionThread? = null
    private val mHandler: Handler = object : Handler(Looper.getMainLooper()) {
        override fun handleMessage(msg: Message?) {
            when (msg?.what) {
                MESSAGE_READ -> onBtMessage(String(msg.obj as ByteArray, 0, msg.arg1))
                MESSAGE_WRITE -> {}
                MESSAGE_TOAST -> showToast(msg.data.getString(MESSAGE_KEY_TOAST))
                MESSAGE_CONNECTED -> showToast("Connected!")
            }
        }
    }

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when(intent.action) {
                BluetoothDevice.ACTION_FOUND -> {
                    val device: BluetoothDevice =
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                    mDevices.add(device)
                    spinnerUpdate()
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        requestPermissions()

        mBtAdapter = BluetoothAdapter.getDefaultAdapter()

        requestBluetoothActivation()
        registerReceiver(receiver, IntentFilter(BluetoothDevice.ACTION_FOUND))

        buttonScan.setOnClickListener {
            btStartScan()
        }
        buttonConnect.setOnClickListener {
            btConnectDevice()
        }
        buttonMessage.setOnClickListener {
            btSendMessage("Just a message")
        }
    }

    fun showToast(text: String?) {
        Toast.makeText(this, text, Toast.LENGTH_LONG).show()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_PERMISSIONS) {
            requestPermissions()
        }
        if(requestCode == REQUEST_ENABLE_BLUETOOTH) {
            if(!mBtAdapter!!.isEnabled){
                requestBluetoothActivation()
            }
        }
    }

    private fun btStartScan() {
        mBtAdapter?.startDiscovery()
        showToast("Start scanning")

        Handler().postDelayed({
            btStopScan()
        }, 15000)
    }

    private fun btStopScan() {
        mBtAdapter?.cancelDiscovery()
    }

    private fun btConnectDevice() {
        if (mSelectedDevice == null) {
            return
        }
        if (mConnectionThread?.isAlive == true) {
            mConnectionThread?.cancel()
        }
        mConnectionThread = ConnectionThread(mSelectedDevice!!, mHandler)
        mConnectionThread?.start()
    }

    private fun btSendMessage(msg: String) {
        mConnectionThread?.write(msg.toByteArray())
    }

    private fun onBtMessage(msg: String) {
        Log.i(TAG,"onBtMessage: ${msg.toString()}")
    }

    private fun requestPermissions() {
        val permissions = arrayOf(
            Manifest.permission.BLUETOOTH,
            Manifest.permission.BLUETOOTH_ADMIN,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
        val allPermissionsGranted = permissions.all {
            permission: String ->
            ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
        }
        if (!allPermissionsGranted) {
            ActivityCompat.requestPermissions(this,
                permissions,
                REQUEST_PERMISSIONS)
        }
    }

    private fun requestBluetoothActivation() {
        if (!mBtAdapter!!.isEnabled) {
            val enableBluetoothIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            startActivityForResult(enableBluetoothIntent, REQUEST_ENABLE_BLUETOOTH)
        }
    }

    override fun onNothingSelected(parent: AdapterView<*>?) {
        Log.i(TAG, "nothing selected")
    }

    override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
        mSelectedDevice = mDevices[position]
        Log.i(TAG, mSelectedDevice.toString())
    }

    private fun spinnerUpdate() {
        spinnerDeviceSelect!!.onItemSelectedListener = this

        val listItems = mDevices.map { device -> DeviceItem(device) }
        val adapter = ArrayAdapter<DeviceItem>(
            this,
            android.R.layout.simple_list_item_1,
            listItems
        )
        spinnerDeviceSelect.adapter = adapter
        adapter.notifyDataSetChanged()
    }

    private data class DeviceItem(val device: BluetoothDevice) {
        override fun toString(): String {
            return "${device.name} ${device.address}"
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(receiver)
        mConnectionThread?.cancel()
    }
}
