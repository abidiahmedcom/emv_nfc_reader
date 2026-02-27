package com.antigravity.emv.emv_nfc_reader

import android.app.Activity
import android.nfc.NfcAdapter
import android.nfc.Tag
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.embedding.engine.plugins.activity.ActivityAware
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.MethodChannel.MethodCallHandler
import io.flutter.plugin.common.MethodChannel.Result

/** EmvNfcReaderPlugin */
class EmvNfcReaderPlugin :
    FlutterPlugin,
    MethodCallHandler,
    ActivityAware,
    NfcAdapter.ReaderCallback {
    
    private lateinit var channel: MethodChannel
    private var activity: Activity? = null
    private var nfcAdapter: NfcAdapter? = null
    private var pendingResult: Result? = null

    override fun onAttachedToEngine(flutterPluginBinding: FlutterPlugin.FlutterPluginBinding) {
        channel = MethodChannel(flutterPluginBinding.binaryMessenger, "emv_nfc_reader")
        channel.setMethodCallHandler(this)
    }

    override fun onMethodCall(call: MethodCall, result: Result) {
        when (call.method) {
            "startReading" -> {
                if (nfcAdapter == null) {
                    result.error("NFC_NOT_AVAILABLE", "NFC adapter is null", null)
                    return
                }
                pendingResult = result
                nfcAdapter?.enableReaderMode(
                    activity,
                    this,
                    NfcAdapter.FLAG_READER_NFC_A or NfcAdapter.FLAG_READER_NFC_B or NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK,
                    null
                )
            }
            "stopReading" -> {
                nfcAdapter?.disableReaderMode(activity)
                result.success(null)
            }
            else -> {
                result.notImplemented()
            }
        }
    }

    override fun onTagDiscovered(tag: Tag) {
        val reader = EmvCardReader(tag)
        val data = reader.readCard()
        
        activity?.runOnUiThread {
            if (data != null) {
                pendingResult?.success(data)
            } else {
                pendingResult?.error("READ_FAILED", "Could not read EMV data", null)
            }
            pendingResult = null
            nfcAdapter?.disableReaderMode(activity)
        }
    }

    override fun onDetachedFromEngine(binding: FlutterPlugin.FlutterPluginBinding) {
        channel.setMethodCallHandler(null)
    }

    override fun onAttachedToActivity(binding: ActivityPluginBinding) {
        activity = binding.activity
        nfcAdapter = NfcAdapter.getDefaultAdapter(activity)
    }

    override fun onDetachedFromActivityForConfigChanges() {
        activity = null
    }

    override fun onReattachedToActivityForConfigChanges(binding: ActivityPluginBinding) {
        activity = binding.activity
    }

    override fun onDetachedFromActivity() {
        activity = null
    }
}
