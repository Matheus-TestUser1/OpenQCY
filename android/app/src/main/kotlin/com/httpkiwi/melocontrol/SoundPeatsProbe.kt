package com.httpkiwi.melocontrol

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.ParcelUuid
import android.os.SystemClock
import java.io.IOException
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/** Bounded discovery and GET-only probe of the single bonded device chosen by the user. */
@SuppressLint("MissingPermission")
class SoundPeatsProbe(private val context: Context) : AutoCloseable {
    private val cancelled = AtomicBoolean(false)
    private val socket = AtomicReference<BluetoothSocket?>(null)
    private val timer = Executors.newSingleThreadScheduledExecutor()
    private val lines = mutableListOf<String>()
    private var identityConfirmed = false
    private val decoder = PeatsReadProtocol.Decoder()

    companion object {
        fun bondedChoices(context: Context): List<Map<String, String>> {
            val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter
                ?: throw IllegalStateException("Bluetooth indisponível")
            check(adapter.isEnabled) { "Ative o Bluetooth" }
            return adapter.bondedDevices.map {
                mapOf("address" to it.address, "name" to (it.name ?: "Dispositivo sem nome"))
            }.sortedBy { it["name"] }
        }
    }

    private fun log(message: String) {
        if (lines.size < 400) lines.add(message)
        else if (lines.size == 400) lines.add("Limite de registros atingido.")
    }
    private fun safeError(error: Exception): String = (error.message ?: error.javaClass.simpleName)
        .replace(Regex("(?i)([0-9a-f]{2}:){5}[0-9a-f]{2}"), "[endereço]")

    fun run(address: String): String {
        log("SoundPEATS SPP: diagnóstico somente de leitura")
        log("Destino: um dispositivo emparelhado escolhido pelo usuário; endereço omitido.")
        try {
            timer.schedule({
                cancelled.set(true)
                try { socket.get()?.close() } catch (_: IOException) { }
            }, 30, TimeUnit.SECONDS)
            val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter
                ?: throw IllegalStateException("Bluetooth indisponível")
            check(adapter.isEnabled) { "Ative o Bluetooth" }
            val device = adapter.bondedDevices.firstOrNull { it.address.equals(address, true) }
                ?: throw IllegalArgumentException("Escolha um fone já emparelhado para áudio no Android")
            log("Tipo Bluetooth: ${when (device.type) {
                BluetoothDevice.DEVICE_TYPE_CLASSIC -> "Classic"
                BluetoothDevice.DEVICE_TYPE_DUAL -> "Dual (Classic + LE)"
                BluetoothDevice.DEVICE_TYPE_LE -> "LE"
                else -> "desconhecido"
            }}")
            val services = discoverServices(device)
            log("UUIDs retornados pelo Android (SDP pode usar cache):")
            services.forEach { log(it.toString()) }
            if (services.isEmpty()) log("Nenhum UUID retornado; isso não demonstra ausência de serviço.")
            val spp = UUID.fromString(PeatsReadProtocol.SPP_UUID)
            log("SPP listado: ${services.contains(spp)}")
            if (cancelled.get()) throw IOException("Diagnóstico cancelado")
            try { adapter.cancelDiscovery() } catch (_: SecurityException) {
                log("Sem permissão para cancelar descoberta; tentativa SPP continuará.")
            }

            // One standard UUID lookup only. No reflection, hidden channel 11, or port sweep.
            val connection = device.createRfcommSocketToServiceRecord(spp)
            socket.set(connection)
            val timedOut = AtomicBoolean(false)
            val deadline = timer.schedule({
                timedOut.set(true)
                try { connection.close() } catch (_: IOException) { }
            }, 8, TimeUnit.SECONDS)
            try {
                log("Tentando SPP pelo UUID padrão, limite de 8 segundos…")
                connection.connect()
            } catch (error: IOException) {
                log(if (timedOut.get()) "SPP: tempo de conexão esgotado." else "SPP: conexão não estabelecida: ${safeError(error)}")
                log("Nenhuma consulta SoundPEATS foi enviada. Esse resultado não determina suporte a LDAC.")
                return lines.joinToString("\n")
            } finally { deadline.cancel(false) }
            log("Conexão SPP estabelecida. Isso, por si só, não identifica o protocolo.")

            query(connection, PeatsReadProtocol.VERSION, 2)
            query(connection, PeatsReadProtocol.BATTERY, 1)
            if (!identityConfirmed) {
                log("Nenhuma resposta de versão/bateria compatível. Consulta LDAC não enviada.")
                log("Não foi possível identificar o protocolo SoundPEATS neste canal.")
                return lines.joinToString("\n")
            }
            log("Resposta de identificação compatível com o protocolo SoundPEATS.")
            val state = query(connection, PeatsReadProtocol.LDAC, 2)?.ldac()
            log(when (state) {
                true -> "O canal informou preferência LDAC ativada; codec A2DP real não medido."
                false -> "O canal informou preferência LDAC desativada. Nenhuma ativação foi enviada."
                null -> "Estado LDAC não confirmado por este canal."
            })
        } catch (error: Exception) {
            log("Falha no diagnóstico: ${safeError(error)}")
        } finally { close() }
        return lines.joinToString("\n")
    }

    @Suppress("DEPRECATION")
    private fun discoverServices(device: BluetoothDevice): List<UUID> {
        val answer = AtomicReference<List<UUID>?>(null)
        val received = CountDownLatch(1)
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action != BluetoothDevice.ACTION_UUID) return
                val sender = if (Build.VERSION.SDK_INT >= 33)
                    intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                else intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
                if (sender?.address != device.address) return
                val uuids = if (Build.VERSION.SDK_INT >= 33)
                    intent.getParcelableArrayExtra(BluetoothDevice.EXTRA_UUID, ParcelUuid::class.java)?.toList()
                else intent.getParcelableArrayExtra(BluetoothDevice.EXTRA_UUID)?.filterIsInstance<ParcelUuid>()
                answer.set(uuids?.map { it.uuid } ?: emptyList())
                received.countDown()
            }
        }
        val filter = IntentFilter(BluetoothDevice.ACTION_UUID)
        if (Build.VERSION.SDK_INT >= 33) context.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
        else context.registerReceiver(receiver, filter)
        try {
            log("Consultando SDP do dispositivo selecionado…")
            if (device.fetchUuidsWithSdp()) {
                if (!received.await(6, TimeUnit.SECONDS)) log("SDP sem evento no prazo; lendo UUIDs em cache.")
            } else log("Android não iniciou SDP; lendo UUIDs em cache.")
            return answer.get() ?: device.uuids?.map { it.uuid } ?: emptyList()
        } finally { context.unregisterReceiver(receiver) }
    }

    private fun query(connection: BluetoothSocket, command: Int, attempts: Int): PeatsReadProtocol.Frame? {
        repeat(attempts) { attempt ->
            if (cancelled.get()) throw IOException("Diagnóstico cancelado")
            val request = PeatsReadProtocol.request(command)
            log("TX GET (${attempt + 1}/$attempts): ${PeatsReadProtocol.hex(request)}")
            connection.outputStream.write(request)
            connection.outputStream.flush()
            val until = SystemClock.elapsedRealtime() + 1500
            while (SystemClock.elapsedRealtime() < until && !cancelled.get()) {
                val available = connection.inputStream.available()
                if (available <= 0) { Thread.sleep(20); continue }
                val data = ByteArray(minOf(available, 512))
                val count = connection.inputStream.read(data)
                if (count < 0) throw IOException("Canal SPP encerrado pelo dispositivo")
                val bytes = data.copyOf(count)
                log("RX RAW: ${PeatsReadProtocol.hex(bytes)}")
                val frames = decoder.feed(bytes)
                var matching: PeatsReadProtocol.Frame? = null
                for (frame in frames) {
                    log("Quadro: vendor=%04x cmd=%04x payload=%s".format(frame.vendor, frame.command, PeatsReadProtocol.hex(frame.payload)))
                    frame.firmware()?.let { log("Versão lida pelo SPP: $it") }
                    frame.battery()?.let { log("Bateria lida pelo SPP: $it%") }
                    if (frame.firmware() != null || frame.battery() != null) identityConfirmed = true
                    if (frame.matches(command)) matching = frame
                }
                if (matching != null) return matching
            }
            log("Sem resposta reconhecida para GET %02x nesta tentativa.".format(command))
        }
        return null
    }

    override fun close() {
        cancelled.set(true)
        try { socket.getAndSet(null)?.close() } catch (_: IOException) { }
        timer.shutdownNow()
    }
}
