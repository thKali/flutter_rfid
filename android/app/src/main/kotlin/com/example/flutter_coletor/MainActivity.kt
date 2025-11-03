package com.example.flutter_coletor

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel

class MainActivity : FlutterActivity() {
    
    private lateinit var rfidPlugin: RfidPlugin
    private val BLUETOOTH_PERMISSION_REQUEST_CODE = 100
    
    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)
        
        // Create and configure RFID plugin
        rfidPlugin = RfidPlugin(applicationContext)
        
        val channel = MethodChannel(flutterEngine.dartExecutor.binaryMessenger, RfidPlugin.CHANNEL)
        rfidPlugin.setMethodChannel(channel)
        channel.setMethodCallHandler(rfidPlugin)
    }
    
    override fun onResume() {
        super.onResume()
        
        // Request Bluetooth permissions for Android 12+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            checkAndRequestBluetoothPermissions()
        }
        
        rfidPlugin.onResume()
    }
    
    override fun onPause() {
        super.onPause()
        rfidPlugin.onPause()
    }
    
    override fun onDestroy() {
        super.onDestroy()
        rfidPlugin.onDestroy()
    }
    
    private fun checkAndRequestBluetoothPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val permissions = mutableListOf<String>()
            
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) 
                != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.BLUETOOTH_CONNECT)
            }
            
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) 
                != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.BLUETOOTH_SCAN)
            }
            
            if (permissions.isNotEmpty()) {
                ActivityCompat.requestPermissions(
                    this,
                    permissions.toTypedArray(),
                    BLUETOOTH_PERMISSION_REQUEST_CODE
                )
            }
        }
    }
    
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        
        if (requestCode == BLUETOOTH_PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                // Permissions granted, update reader list
                rfidPlugin.onResume()
            }
        }
    }
}
