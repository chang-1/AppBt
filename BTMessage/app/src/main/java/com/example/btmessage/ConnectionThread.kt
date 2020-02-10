package com.example.btmessage

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.os.Bundle
import android.os.Handler
import android.util.Log
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.*

class ConnectionThread(
    private val device: BluetoothDevice,
    private val handler: Handler
) : Thread() {

    private var mmSocket: BluetoothSocket? = null
    private var mmInStream: InputStream? = null
    private var mmOutStream: OutputStream? = null
    private val mmBuffer: ByteArray = ByteArray(BT_MESSAGE_BUFFER_SIZE)

    override fun run() {
        mmSocket = device.createInsecureRfcommSocketToServiceRecord(UUID.fromString(BT_SERVICE_CLASS_UUID))
        try {
            mmSocket?.connect()
            handler.sendMessage(handler.obtainMessage(MESSAGE_CONNECTED))
        } catch (e: IOException) {
            Log.i(TAG, e.message)
        }

        mmSocket?.use { socket ->
            mmInStream = socket.inputStream
            mmOutStream = socket.outputStream

            var numBytes: Int

            while (true) {
                // Read from the InputStream.
                numBytes = try {
                    mmInStream!!.read(mmBuffer)
                } catch (e: IOException) {
                    Log.d(TAG, "Input stream was disconnected", e)
                    break
                }

                // Send the obtained bytes to the UI activity.
                val readMsg = handler.obtainMessage(
                    MESSAGE_READ, numBytes, -1,
                    mmBuffer)
                readMsg.sendToTarget()
            }
        }
    }

    // Call this from the main activity to send data to the remote device.
    fun write(bytes: ByteArray) {
        try {
            mmOutStream?.write(bytes)
        } catch (e: IOException) {
            Log.e(TAG, "Error occurred when sending data", e)

            // Send a failure message back to the activity.
            val writeErrorMsg = handler.obtainMessage(MESSAGE_TOAST)
            val bundle = Bundle().apply {
                putString(MESSAGE_KEY_TOAST, "Couldn't send data to the device")
            }
            writeErrorMsg.data = bundle
            handler.sendMessage(writeErrorMsg)
            return
        }

        // Share the sent message with the UI activity.
        val writtenMsg = handler.obtainMessage(
            MESSAGE_WRITE, -1, -1, mmBuffer)
        writtenMsg.sendToTarget()
    }

    // Closes the client socket and causes the thread to finish.
    fun cancel() {
        try {
            mmSocket?.close()
        } catch (e: IOException) {
            Log.e(TAG, "Could not close the client socket", e)
        }
    }
}