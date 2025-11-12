package com.example.flutter_coletor

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.Looper
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.uk.tsl.rfid.asciiprotocol.AsciiCommander
import com.uk.tsl.rfid.asciiprotocol.commands.AbortCommand
import com.uk.tsl.rfid.asciiprotocol.commands.BatteryStatusCommand
import com.uk.tsl.rfid.asciiprotocol.commands.InventoryCommand
import com.uk.tsl.rfid.asciiprotocol.commands.SwitchActionCommand
import com.uk.tsl.rfid.asciiprotocol.enumerations.TriState
import com.uk.tsl.rfid.asciiprotocol.enumerations.SwitchAction
import com.uk.tsl.rfid.asciiprotocol.enumerations.SwitchState
import com.uk.tsl.rfid.asciiprotocol.device.ConnectionState
import com.uk.tsl.rfid.asciiprotocol.device.IAsciiTransport
import com.uk.tsl.rfid.asciiprotocol.device.ObservableReaderList
import com.uk.tsl.rfid.asciiprotocol.device.Reader
import com.uk.tsl.rfid.asciiprotocol.device.ReaderManager
import com.uk.tsl.rfid.asciiprotocol.device.TransportType
import com.uk.tsl.rfid.asciiprotocol.responders.LoggerResponder
import com.uk.tsl.rfid.asciiprotocol.responders.TransponderData
import com.uk.tsl.rfid.asciiprotocol.responders.ITransponderReceivedDelegate
import com.uk.tsl.rfid.asciiprotocol.responders.SwitchResponder
import com.uk.tsl.rfid.asciiprotocol.responders.ISwitchStateReceivedDelegate
import com.uk.tsl.utils.Observable
import android.util.Log
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.MethodChannel.MethodCallHandler
import io.flutter.plugin.common.MethodChannel.Result

class RfidPlugin(private val context: Context) : MethodCallHandler {
    
    companion object {
        const val CHANNEL = "flutter_coletor/rfid"
        const val TAG = "RFID"
        
        const val EVENT_TAG = "tag"
        const val EVENT_CONNECTION = "connection"
        const val EVENT_TRIGGER = "trigger"
        const val EVENT_INVENTORY = "inventory"
    }

    private var mReader: Reader? = null
    private var methodChannel: MethodChannel? = null
    private var isInitialized = false
    private var mInventoryCommand: InventoryCommand? = null
    @Volatile
    private var isInventorying = false
    private val mainHandler = Handler(Looper.getMainLooper())
    private var inventoryRunnable: Runnable? = null
    private var mSwitchResponder: SwitchResponder? = null

    fun setMethodChannel(channel: MethodChannel) {
        this.methodChannel = channel
    }

    private fun sendEvent(type: String, data: Map<String, Any?>) {
        mainHandler.post {
            val event = mapOf(
                "type" to type,
                "timestamp" to System.currentTimeMillis(),
                "data" to data
            )
            methodChannel?.invokeMethod("onRfidEvent", event)
        }
    }

    override fun onMethodCall(call: MethodCall, result: Result) {
        when (call.method) {
            "initialize" -> {
                initialize()
                result.success(true)
            }
            "checkConnection" -> {
                checkAndNotifyConnectionState()
                result.success(true)
            }
            "connect" -> {
                connect(result)
            }
            "disconnect" -> {
                disconnect(result)
            }
            "getStatus" -> {
                result.success(getStatus())
            }
            "getReaderName" -> {
                result.success(mReader?.displayName ?: "Nenhum leitor")
            }
            "startInventory" -> {
                startInventory(result)
            }
            "stopInventory" -> {
                stopInventory(result)
            }
            "isInventorying" -> {
                result.success(isInventorying)
            }
            else -> {
                result.notImplemented()
            }
        }
    }

    private fun initialize() {
        if (isInitialized) return
        
        AsciiCommander.createSharedInstance(context)
        val commander = getCommander()
        
        commander.clearResponders()
        commander.addResponder(LoggerResponder())
        commander.addSynchronousResponder()
        
        mSwitchResponder = SwitchResponder()
        mSwitchResponder?.switchStateReceivedDelegate = mSwitchDelegate
        commander.addResponder(mSwitchResponder)
        
        ReaderManager.create(context)
        
        ReaderManager.sharedInstance().readerList.readerAddedEvent().addObserver(mAddedObserver)
        ReaderManager.sharedInstance().readerList.readerUpdatedEvent().addObserver(mUpdatedObserver)
        ReaderManager.sharedInstance().readerList.readerRemovedEvent().addObserver(mRemovedObserver)
        
        LocalBroadcastManager.getInstance(context).registerReceiver(
            mMessageReceiver,
            IntentFilter(AsciiCommander.STATE_CHANGED_NOTIFICATION)
        )
        
        isInitialized = true
        checkAndNotifyConnectionState()
    }

    fun onResume() {
        if (!isInitialized) return
        
        val readerManagerDidCauseOnPause = ReaderManager.sharedInstance().didCauseOnPause()
        
        ReaderManager.sharedInstance().onResume()
        ReaderManager.sharedInstance().updateList()
        
        autoSelectReader(!readerManagerDidCauseOnPause)
        checkAndNotifyConnectionState()
    }

    fun onPause() {
        if (!isInitialized) return
        ReaderManager.sharedInstance().onPause()
    }

    fun onDestroy() {
        if (!isInitialized) return
        
        if (mReader != null) {
            mReader?.disconnect()
            mReader = null
        }
        
        LocalBroadcastManager.getInstance(context).unregisterReceiver(mMessageReceiver)
        
        ReaderManager.sharedInstance().readerList.readerAddedEvent().removeObserver(mAddedObserver)
        ReaderManager.sharedInstance().readerList.readerUpdatedEvent().removeObserver(mUpdatedObserver)
        ReaderManager.sharedInstance().readerList.readerRemovedEvent().removeObserver(mRemovedObserver)
        
        isInitialized = false
    }

    private fun connect(result: Result) {
        if (!isInitialized) {
            initialize()
        }
        
        ReaderManager.sharedInstance().updateList()
        
        val readerList = ReaderManager.sharedInstance().readerList
        val ignoredDevices = mutableListOf<String>()
        val rfidDevices = mutableListOf<String>()
        
        for (reader in readerList.list()) {
            val deviceName = reader.displayName ?: "Desconhecido"
            if (isRfidReader(reader)) {
                rfidDevices.add(deviceName)
            } else {
                ignoredDevices.add(deviceName)
            }
        }
        
        autoSelectReader(true)
        
        if (mReader != null) {
            checkAndNotifyConnectionState()
            result.success(mapOf(
                "connected" to true,
                "readerName" to (mReader?.displayName ?: "Desconhecido"),
                "ignoredDevices" to ignoredDevices,
                "rfidDevices" to rfidDevices
            ))
        } else {
            val errorMessage = if (ignoredDevices.isNotEmpty()) {
                "Nenhum leitor RFID encontrado.\n\n" +
                "Dispositivos Bluetooth ignorados:\n${ignoredDevices.joinToString("\n") { "• $it" }}\n\n" +
                "Certifique-se de que o leitor RFID está pareado e ligado."
            } else {
                "Nenhum leitor RFID encontrado.\n\n" +
                "Nenhum dispositivo Bluetooth pareado foi encontrado.\n" +
                "Vá em Configurações > Bluetooth e pareie o leitor RFID."
            }
            
            result.error(
                "NO_READER", 
                errorMessage,
                mapOf(
                    "ignoredDevices" to ignoredDevices,
                    "totalDevices" to readerList.list().size
                )
            )
        }
    }

    private fun disconnect(result: Result) {
        mReader?.disconnect()
        mReader = null
        result.success(true)
    }

    private fun getStatus(): String {
        val commander = getCommander()
        return when {
            commander.isConnected -> "connected"
            commander.connectionState == ConnectionState.CONNECTING -> "connecting"
            else -> "disconnected"
        }
    }
    
    private fun checkAndNotifyConnectionState() {
        if (methodChannel == null) {
            Log.w(TAG, "MethodChannel not configured yet, skipping notification")
            return
        }
        
        val commander = getCommander()
        
        when {
            commander.isConnected -> {
                val bCommand = BatteryStatusCommand.synchronousCommand()
                commander.executeCommand(bCommand)
                val batteryLevel = bCommand.batteryLevel ?: 0
                val readerName = mReader?.displayName ?: "Desconhecido"
                
                Log.i(TAG, "Reader already connected: $readerName")
                
                sendEvent(EVENT_CONNECTION, mapOf(
                    "status" to "connected",
                    "readerName" to readerName,
                    "batteryLevel" to batteryLevel
                ))
            }
            commander.connectionState == ConnectionState.CONNECTING -> {
                Log.i(TAG, "Reader connecting")
                sendEvent(EVENT_CONNECTION, mapOf(
                    "status" to "connecting"
                ))
            }
            else -> {
                Log.i(TAG, "Reader disconnected")
                sendEvent(EVENT_CONNECTION, mapOf(
                    "status" to "disconnected"
                ))
            }
        }
    }

    private fun startInventory(result: Result) {
        val commander = getCommander()
        
        if (!commander.isConnected) {
            result.error("NOT_CONNECTED", "Leitor não conectado", null)
            return
        }
        
        if (isInventorying) {
            result.error("ALREADY_RUNNING", "Inventário já está rodando", null)
            return
        }
        
        mInventoryCommand = InventoryCommand().apply {
            includeTransponderRssi = TriState.YES
            includeChecksum = TriState.YES
            includePC = TriState.YES
            includeDateTime = TriState.NO
            includeIndex = TriState.YES
            includePhase = TriState.NO
            resetParameters = TriState.YES
            useAlert = TriState.NO
            
            transponderReceivedDelegate = ITransponderReceivedDelegate { transponder, moreAvailable ->
                val epc = transponder.epc ?: ""
                val rssi = transponder.rssi ?: 0
                
                Log.d(TAG, "Tag detected - EPC: $epc, RSSI: $rssi dBm")
                
                sendEvent(EVENT_TAG, mapOf(
                    "epc" to epc,
                    "rssi" to rssi
                ))
            }
        }
        
        commander.addResponder(mInventoryCommand)
        
        inventoryRunnable = object : Runnable {
            override fun run() {
                if (isInventorying && mInventoryCommand != null) {
                    commander.executeCommand(mInventoryCommand)
                    mainHandler.postDelayed(this, 100)
                }
            }
        }
        
        Log.i(TAG, "Inventory started")
        isInventorying = true
        inventoryRunnable?.run()
        
        sendEvent(EVENT_INVENTORY, mapOf(
            "status" to "started",
            "isRunning" to true
        ))
        
        result.success(true)
    }

    private fun stopInventory(result: Result) {
        if (!isInventorying) {
            result.success(false)
            return
        }
        
        val commander = getCommander()
        
        isInventorying = false
        inventoryRunnable?.let { mainHandler.removeCallbacks(it) }
        inventoryRunnable = null
        
        Log.i(TAG, "Inventory stopped")
        commander.executeCommand(AbortCommand())
        
        if (mInventoryCommand != null) {
            commander.removeResponder(mInventoryCommand)
        }
        mInventoryCommand = null
        
        sendEvent(EVENT_INVENTORY, mapOf(
            "status" to "stopped",
            "isRunning" to false
        ))
        
        result.success(true)
    }

    private fun isRfidReader(reader: Reader): Boolean {
        val displayName = reader.displayName?.uppercase() ?: ""
        
        val knownRfidIdentifiers = listOf(
            "TSL",      // TSL readers
            "1128",     // TSL 1128 (ex: "011420-br-1128")
            "1153",     // TSL 1153
            "1166",     // TSL 1166
            "2128",     // TSL 2128
            "2166",     // TSL 2166
            "ACURA",    // ACURA readers
            "BTL",      // ACURA BTL series
            "RFID",     // Generic RFID
            "UHF",      // UHF RFID readers
            "-BR-",     // Brazilian RFID readers (ex: "011420-br-1128")
            "-US-",     // US RFID readers
            "-UK-"      // UK RFID readers
        )
        
        for (identifier in knownRfidIdentifiers) {
            if (displayName.contains(identifier)) {
                Log.d(TAG, "Found RFID reader by name: $displayName (matched: $identifier)")
                return true
            }
        }
        
        // Padrão adicional: números-país-modelo (ex: "011420-br-1128")
        // Formato: XXXXXX-CC-NNNN onde X=números, CC=país, N=modelo
        val rfidPattern = Regex("""\d{5,7}-[A-Z]{2}-\d{4}""")
        if (rfidPattern.containsMatchIn(displayName)) {
            Log.d(TAG, "Found RFID reader by pattern: $displayName")
            return true
        }
        
        Log.d(TAG, "Skipping non-RFID device: $displayName")
        return false
    }
    
    private fun autoSelectReader(attemptReconnect: Boolean) {
        val readerList: ObservableReaderList = ReaderManager.sharedInstance().readerList
        var selectedReader: Reader? = null
        
        if (readerList.list().size >= 1) {
            // Priority: USB > Bluetooth > Others
            
            for (reader in readerList.list()) {
                if (reader.hasTransportOfType(TransportType.USB) && isRfidReader(reader)) {
                    selectedReader = reader
                    Log.i(TAG, "Selected USB RFID reader: ${reader.displayName}")
                    break
                }
            }
            
            if (selectedReader == null) {
                for (reader in readerList.list()) {
                    if (reader.hasTransportOfType(TransportType.BLUETOOTH) && isRfidReader(reader)) {
                        selectedReader = reader
                        Log.i(TAG, "Selected Bluetooth RFID reader: ${reader.displayName}")
                        break
                    }
                }
            }
            
            // Only fallback to first device if it's an RFID reader
            if (selectedReader == null && readerList.list().isNotEmpty()) {
                val firstReader = readerList.list()[0]
                if (isRfidReader(firstReader)) {
                    selectedReader = firstReader
                    Log.i(TAG, "Selected first RFID reader: ${firstReader.displayName}")
                } else {
                    Log.w(TAG, "No RFID readers found in reader list. Available devices:")
                    for (reader in readerList.list()) {
                        Log.w(TAG, "  - ${reader.displayName}")
                    }
                }
            }
        }

        if (mReader == null) {
            if (selectedReader != null) {
                mReader = selectedReader
                getCommander().reader = mReader
            }
        } else {
            val activeTransport: IAsciiTransport? = mReader?.activeTransport
            if (activeTransport != null && activeTransport.type() != TransportType.USB && selectedReader != null) {
                if (selectedReader.hasTransportOfType(TransportType.USB)) {
                    mReader?.disconnect()
                    mReader = selectedReader
                    getCommander().reader = mReader
                }
            }
        }

        if (mReader != null &&
            !mReader!!.isConnecting &&
            (mReader!!.activeTransport == null || mReader!!.activeTransport.connectionStatus().value() == ConnectionState.DISCONNECTED)
        ) {
            if (attemptReconnect) {
                if (mReader!!.allowMultipleTransports() || mReader!!.lastTransportType == null) {
                    mReader!!.connect()
                } else {
                    mReader!!.connect(mReader!!.lastTransportType)
                }
            }
        }
    }

    private fun getCommander(): AsciiCommander {
        return AsciiCommander.sharedInstance()
    }

    private val mAddedObserver = Observable.Observer<Reader> { _, _ ->
        autoSelectReader(true)
    }

    private val mUpdatedObserver = Observable.Observer<Reader> { _, _ -> }

    private val mRemovedObserver = Observable.Observer<Reader> { _, reader ->
        if (reader == mReader) {
            mReader = null
            getCommander().reader = null
        }
    }

    private val mMessageReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val commander = getCommander()
            
            when {
                commander.isConnected -> {
                    val bCommand = BatteryStatusCommand.synchronousCommand()
                    commander.executeCommand(bCommand)
                    val batteryLevel = bCommand.batteryLevel
                    val readerName = mReader?.displayName ?: "Desconhecido"
                    
                    configureTriggerReporting()
                    
                    sendEvent(EVENT_CONNECTION, mapOf(
                        "status" to "connected",
                        "readerName" to readerName,
                        "batteryLevel" to batteryLevel
                    ))
                }
                commander.connectionState == ConnectionState.CONNECTING -> {
                    sendEvent(EVENT_CONNECTION, mapOf(
                        "status" to "connecting"
                    ))
                }
                commander.connectionState == ConnectionState.DISCONNECTED -> {
                    if (mReader != null && !mReader!!.wasLastConnectSuccessful()) {
                        mReader = null
                    }
                    
                    sendEvent(EVENT_CONNECTION, mapOf(
                        "status" to "disconnected"
                    ))
                }
            }
        }
    }

    private val mSwitchDelegate = ISwitchStateReceivedDelegate { state ->
        val stateString = when (state) {
            SwitchState.OFF -> "OFF"
            SwitchState.SINGLE -> "SINGLE"
            SwitchState.DOUBLE -> "DOUBLE"
            else -> "UNKNOWN"
        }
        
        Log.d(TAG, "Trigger pressed - State: $stateString")
        
        val mode = when (state) {
            SwitchState.SINGLE -> "single"
            SwitchState.DOUBLE -> "double"
            else -> "none"
        }
        
        val pressing = state != SwitchState.OFF
        
        sendEvent(EVENT_TRIGGER, mapOf(
            "state" to stateString,
            "mode" to mode,
            "isPressing" to pressing
        ))
    }

    private fun configureTriggerReporting() {
        try {
            val commander = getCommander()
            val saCommand = SwitchActionCommand.synchronousCommand()
            
            saCommand.asynchronousReportingEnabled = TriState.YES
            saCommand.singlePressAction = SwitchAction.OFF
            saCommand.doublePressAction = SwitchAction.OFF
            
            commander.executeCommand(saCommand)
            Log.i(TAG, "Trigger configured")
        } catch (e: Exception) {
            Log.e(TAG, "Error configuring trigger: ${e.message}")
        }
    }
}


