package com.httpkiwi.melocontrol

import android.content.Intent
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel
import java.util.concurrent.Executors

class MainActivity : FlutterActivity() {
    private val probeExecutor = Executors.newSingleThreadExecutor()
    private var activeProbe: SoundPeatsProbe? = null

    private fun hasBluetoothPermission(): Boolean = Build.VERSION.SDK_INT < 31 ||
        checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED

    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)
        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, "openqcy/system")
            .setMethodCallHandler { call, result ->
                if (call.method == "openBluetoothSettings") {
                    try {
                        startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
                        result.success(null)
                    } catch (error: Exception) {
                        result.error("SETTINGS_UNAVAILABLE", error.message, null)
                    }
                } else if (call.method == "listProbeDevices") {
                    if (!hasBluetoothPermission()) {
                        result.error("BLUETOOTH_PERMISSION", "Conecte o fone no app para conceder a permissão de dispositivos próximos.", null)
                    } else {
                        try { result.success(SoundPeatsProbe.bondedChoices(applicationContext)) }
                        catch (error: Exception) { result.error("BLUETOOTH_DEVICES", error.message, null) }
                    }
                } else if (call.method == "probeSoundPeats") {
                    val address = call.argument<String>("address")
                    if (!hasBluetoothPermission() || address == null) {
                        result.error("BLUETOOTH_PERMISSION", "Selecione um fone emparelhado e conceda a permissão Bluetooth.", null)
                    } else if (activeProbe != null) {
                        result.error("PROBE_BUSY", "Já existe um diagnóstico em andamento.", null)
                    } else {
                        val probe = SoundPeatsProbe(applicationContext)
                        activeProbe = probe
                        probeExecutor.execute {
                            val report = probe.run(address)
                            runOnUiThread {
                                activeProbe = null
                                if (!isDestroyed) result.success(report)
                            }
                        }
                    }
                } else {
                    result.notImplemented()
                }
            }
    }

    override fun onDestroy() {
        activeProbe?.close()
        probeExecutor.shutdownNow()
        super.onDestroy()
    }
}
