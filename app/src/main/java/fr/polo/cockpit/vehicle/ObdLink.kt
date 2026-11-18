package fr.polo.cockpit.vehicle

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import java.io.Closeable
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.util.UUID

/** Configuration choisie par l'utilisateur pour l'adaptateur ELM327. */
data class ObdConfig(
    val transport: String = "bt",          // "bt" ou "wifi"
    val btAddress: String? = null,
    val wifiHost: String = "192.168.0.10",
    val wifiPort: Int = 35000,
) {
    /** Identifiant utilisé pour mémoriser qu'une connexion a été testée avec succès. */
    val key: String get() = if (transport == "wifi") "wifi:$wifiHost:$wifiPort" else "bt:${btAddress ?: ""}"
    val ready: Boolean get() = if (transport == "wifi") wifiHost.isNotBlank() && wifiPort in 1..65535 else !btAddress.isNullOrBlank()
}

/** Canal ouvert vers l'adaptateur. */
class ObdLink(val input: InputStream, val output: OutputStream, val description: String, private val closer: Closeable) : Closeable {
    override fun close() { runCatching { closer.close() } }
}

data class BtDevice(val name: String, val address: String, val kind: String)

data class BtDiagnostic(
    val adapterPresent: Boolean,
    val enabled: Boolean,
    val permissionOk: Boolean,
    val devices: List<BtDevice>,
    val message: String,
)

object ObdLinks {
    private val SPP: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    private const val PERM_CONNECT = "android.permission.BLUETOOTH_CONNECT"   // Android 12+ uniquement

    fun hasBtPermission(ctx: Context): Boolean =
        Build.VERSION.SDK_INT < 31 || ctx.checkSelfPermission(PERM_CONNECT) == PackageManager.PERMISSION_GRANTED

    fun adapter(ctx: Context): BluetoothAdapter? = runCatching {
        (ctx.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter
    }.getOrNull()

    /** État du Bluetooth Android et liste des appareils associés, avec un message clair. */
    @SuppressLint("MissingPermission")
    fun diagnose(ctx: Context): BtDiagnostic {
        val a = adapter(ctx)
        if (a == null) return BtDiagnostic(false, false, true, emptyList(),
            "Aucun adaptateur Bluetooth accessible à Android. Sur beaucoup d'autoradios, le Bluetooth est piloté par le " +
                "module du fabricant (appli Bluetooth d'origine) et n'est pas ouvert aux autres applis : utilisez alors un ELM327 Wi-Fi.")
        if (!hasBtPermission(ctx)) return BtDiagnostic(true, false, false, emptyList(), "Autorisation Bluetooth à accorder.")
        val enabled = runCatching { a.isEnabled }.getOrDefault(false)
        if (!enabled) return BtDiagnostic(true, false, true, emptyList(), "Le Bluetooth Android est désactivé.")
        val devices = runCatching {
            a.bondedDevices.map { d ->
                val kind = when (runCatching { d.type }.getOrDefault(BluetoothDevice.DEVICE_TYPE_UNKNOWN)) {
                    BluetoothDevice.DEVICE_TYPE_CLASSIC -> "classique"
                    BluetoothDevice.DEVICE_TYPE_LE -> "BLE (non compatible)"
                    BluetoothDevice.DEVICE_TYPE_DUAL -> "double mode"
                    else -> "type inconnu"
                }
                BtDevice(d.name ?: "Appareil sans nom", d.address, kind)
            }.sortedByDescending { looksLikeObd(it.name) }
        }.getOrElse { return BtDiagnostic(true, true, true, emptyList(), "Lecture des appareils associés impossible : ${it.message}") }
        val msg = if (devices.isEmpty())
            "Aucun appareil associé visible par Android. Associez l'ELM327 (code 1234 ou 0000) dans les paramètres Bluetooth. " +
                "S'il n'apparaît que dans l'appli Bluetooth d'origine, il n'est pas accessible aux applis : utilisez un ELM327 Wi-Fi."
        else "${devices.size} appareil(s) associé(s)."
        return BtDiagnostic(true, true, true, devices, msg)
    }

    fun looksLikeObd(name: String): Boolean {
        val n = name.lowercase()
        return listOf("obd", "elm", "v-link", "vlink", "obdii", "konnwei", "vgate", "icar").any { it in n }
    }

    /**
     * Ouvre la liaison série Bluetooth. Trois méthodes sont essayées dans l'ordre, car beaucoup
     * de clones ELM327 refusent la méthode standard : sécurisée, non sécurisée, canal RFCOMM 1.
     */
    @SuppressLint("MissingPermission")
    fun openBluetooth(ctx: Context, address: String, log: (String) -> Unit): ObdLink {
        val a = adapter(ctx) ?: error("Aucun adaptateur Bluetooth accessible à Android")
        if (!hasBtPermission(ctx)) throw SecurityException("permission Bluetooth refusée")
        if (!a.isEnabled) error("Le Bluetooth Android est désactivé")
        val device = a.getRemoteDevice(address)
        runCatching { a.cancelDiscovery() }
        val attempts = listOf<Pair<String, () -> BluetoothSocket>>(
            "SPP sécurisé" to { device.createRfcommSocketToServiceRecord(SPP) },
            "SPP non sécurisé" to { device.createInsecureRfcommSocketToServiceRecord(SPP) },
            "canal RFCOMM 1" to {
                device.javaClass.getMethod("createRfcommSocket", Int::class.javaPrimitiveType).invoke(device, 1) as BluetoothSocket
            },
        )
        var last: Throwable? = null
        for ((label, make) in attempts) {
            val t0 = System.currentTimeMillis()
            try {
                val s = make()
                s.connect()
                log("Connexion Bluetooth ($label) : OK en ${System.currentTimeMillis() - t0} ms")
                return ObdLink(s.inputStream, s.outputStream, "Bluetooth ${device.name ?: address} ($label)", s)
            } catch (e: Throwable) {
                last = e
                log("Connexion Bluetooth ($label) : échec — ${e.message ?: e.javaClass.simpleName}")
            }
        }
        throw last ?: IllegalStateException("connexion impossible")
    }

    /** Ouvre la liaison TCP vers un ELM327 Wi-Fi. */
    fun openWifi(host: String, port: Int, log: (String) -> Unit): ObdLink {
        val s = Socket()
        val t0 = System.currentTimeMillis()
        s.connect(InetSocketAddress(host, port), 5000)
        s.soTimeout = 3000
        log("Connexion Wi-Fi $host:$port : OK en ${System.currentTimeMillis() - t0} ms")
        return ObdLink(s.getInputStream(), s.getOutputStream(), "Wi-Fi $host:$port", s)
    }

    fun open(ctx: Context, cfg: ObdConfig, log: (String) -> Unit): ObdLink =
        if (cfg.transport == "wifi") openWifi(cfg.wifiHost, cfg.wifiPort, log)
        else openBluetooth(ctx, cfg.btAddress ?: error("Aucun adaptateur Bluetooth choisi"), log)
}

/** Dialogue ELM327 en lecture seule sur une liaison ouverte. */
class ElmSession(private val link: ObdLink) {
    val supported = mutableSetOf<Int>()

    fun send(cmd: String, timeoutMs: Long = 1500): String {
        require(ObdProtocol.isAllowed(cmd)) { "Commande OBD refusée (lecture seule) : $cmd" }
        // vide d'éventuels restes avant d'envoyer
        while (runCatching { link.input.available() }.getOrDefault(0) > 0) link.input.read()
        link.output.write("$cmd\r".toByteArray()); link.output.flush()
        val sb = StringBuilder()
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (link.input.available() > 0) {
                val c = link.input.read()
                if (c < 0 || c.toChar() == '>') break
                sb.append(c.toChar())
            } else Thread.sleep(5)
        }
        return sb.toString().trim()
    }

    /** Initialise l'adaptateur ; renvoie sa version (null si ce n'est pas un ELM327). */
    fun init(): String? {
        val z = send("ATZ", 3000)
        val version = ObdProtocol.elmVersion(z) ?: ObdProtocol.elmVersion(send("ATI", 1500))
        for (c in listOf("ATE0", "ATL0", "ATS0", "ATH0", "ATAT1", "ATSP0")) send(c)
        return version
    }

    /** Lit les masques de PID supportés. Renvoie le message d'erreur ELM éventuel (contact coupé…). */
    fun readSupported(): String? {
        supported.clear()
        for (base in listOf(0x00, 0x20, 0x40)) {
            val raw = send("01%02X".format(base), if (base == 0) 6000 else 1500)   // 1re requête : recherche du protocole
            val bytes = ObdProtocol.parseMode01(raw, base)
            if (bytes == null) return if (base == 0) (ObdProtocol.errorToken(raw) ?: "aucune réponse du calculateur") else null
            supported += ObdProtocol.decodeSupported(base, bytes)
            if (base + 0x20 !in supported) break
        }
        return null
    }

    fun pid(pid: Int): IntArray? =
        if (supported.isNotEmpty() && pid !in supported) null
        else ObdProtocol.parseMode01(send("01%02X".format(pid), 1000), pid)

    fun voltage(): Float? = ObdProtocol.voltage(send("ATRV"))
}
