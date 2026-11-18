package fr.polo.cockpit.vehicle

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/** Résultat du test de connexion, affiché étape par étape. */
data class ObdTestResult(
    val adapterOk: Boolean,       // un ELM327 a répondu
    val ecuOk: Boolean,           // le calculateur moteur a répondu
    val steps: List<String>,
    val summary: String,
    val supportedPids: Set<Int> = emptySet(),
)

/**
 * Lecture de données moteur via un adaptateur OBD2 ELM327 (Bluetooth classique ou Wi-Fi).
 * SÉCURITÉ : uniquement des requêtes mode 01 (lecture), filtrées par [ObdProtocol.isAllowed].
 */
class ObdElm327Source(private val context: Context) : VehicleSource {
    override val id = "obd"
    override val label = "OBD2 ELM327"
    private val _state = MutableStateFlow(VehicleState())
    override val state: StateFlow<VehicleState> = _state
    private val _status = MutableStateFlow(SourceStatus())
    override val status: StateFlow<SourceStatus> = _status

    var config: ObdConfig = ObdConfig()
    private var job: Job? = null
    @Volatile private var link: ObdLink? = null

    private var tripFuelL = 0.0
    private var tripKm = 0.0
    private var lastTick = 0L

    /** Test complet avant activation : liaison, adaptateur, calculateur. N'active rien. */
    suspend fun testConnection(cfg: ObdConfig): ObdTestResult = withContext(Dispatchers.IO) {
        val steps = mutableListOf<String>()
        if (!cfg.ready) return@withContext ObdTestResult(false, false, steps, "Choisissez d'abord un adaptateur.")
        if (job?.isActive == true) { stop(); steps += "Lecture en cours arrêtée pour le test." }
        var l: ObdLink? = null
        try {
            l = ObdLinks.open(context, cfg) { steps += it }
            val s = ElmSession(l)
            val version = s.init()
            if (version == null) {
                steps += "Réponse à ATZ/ATI : aucune signature ELM327"
                return@withContext ObdTestResult(false, false, steps,
                    "Une liaison s'ouvre mais l'appareil ne répond pas comme un ELM327. Vérifiez que c'est bien l'adaptateur OBD.")
            }
            steps += "Adaptateur : $version"
            s.voltage()?.let { steps += "Tension batterie (ATRV) : $it V" }
            val err = s.readSupported()
            if (err != null) {
                steps += "Calculateur moteur : $err"
                return@withContext ObdTestResult(true, false, steps,
                    "Adaptateur OK, mais le calculateur ne répond pas (« $err »). Mettez le contact puis refaites le test.")
            }
            steps += "Calculateur moteur : ${s.supported.size} PID disponibles"
            val names = mapOf(0x0D to "vitesse", 0x0C to "régime", 0x05 to "temp. moteur", 0x2F to "carburant",
                0x10 to "débit d'air", 0x0B to "pression admission", 0x0F to "temp. admission", 0x46 to "temp. extérieure")
            names.forEach { (pid, n) -> steps += "  • $n (PID %02X) : ${if (pid in s.supported) "disponible" else "non fourni par le calculateur"}".format(pid) }
            ObdTestResult(true, true, steps, "Connexion vérifiée : vous pouvez activer la lecture.", s.supported.toSet())
        } catch (e: Throwable) {
            steps += "Erreur technique : ${e.message ?: e.javaClass.simpleName}"
            ObdTestResult(false, false, steps, ObdProtocol.explain(e.message ?: e.javaClass.simpleName))
        } finally {
            l?.close()
        }
    }

    override fun start(scope: CoroutineScope) {
        if (job?.isActive == true) return
        val cfg = config
        if (!cfg.ready) { _status.value = SourceStatus(false, "Aucun adaptateur choisi"); return }
        job = scope.launch(Dispatchers.IO) {
            var attempt = 0
            while (isActive) {
                try {
                    session(cfg)
                } catch (e: Throwable) {
                    if (!isActive) break
                    attempt++
                    _status.value = SourceStatus(false, "${ObdProtocol.explain(e.message ?: e.javaClass.simpleName)} (nouvel essai dans 10 s, tentative $attempt)")
                    _state.value = VehicleState()
                    link?.close(); link = null
                }
                delay(10_000)
            }
        }
    }

    private suspend fun session(cfg: ObdConfig) {
        _status.value = SourceStatus(false, "Connexion…")
        val l = ObdLinks.open(context, cfg) { }
        link = l
        val s = ElmSession(l)
        s.init() ?: error("l'appareil ne répond pas comme un ELM327")
        s.readSupported()?.let { error(it) }
        _status.value = SourceStatus(true, "Connecté (${l.description}) — ${s.supported.size} PID")
        lastTick = System.currentTimeMillis()
        val o = DataOrigin.OBD
        var slow = 0
        while (true) {
            var st = _state.value
            val speed = s.pid(0x0D)?.let { ObdProtocol.speedKmh(it) }
            val rpm = s.pid(0x0C)?.let { ObdProtocol.rpm(it) }
            st = st.copy(
                speedKmh = speed?.let { Reading(it, o) } ?: Reading.none(),
                rpm = rpm?.let { Reading(it, o) } ?: Reading.none(),
            )
            // Consommation ESTIMÉE (débit d'air mesuré, sinon calcul pression/température)
            val maf = s.pid(0x10)?.let { ObdProtocol.mafGs(it) } ?: speedDensity(s, rpm)
            if (maf != null && speed != null) {
                val lph = maf / 14.7 / 745.0 * 3600.0
                val now = System.currentTimeMillis()
                val dtH = (now - lastTick) / 3_600_000.0
                lastTick = now
                tripFuelL += lph * dtH; tripKm += speed * dtH
                st = st.copy(
                    instantL100 = if (speed > 3) Reading((lph / speed * 100).toFloat(), DataOrigin.ESTIMATED) else Reading.none(),
                    averageL100 = if (tripKm > 0.5) Reading((tripFuelL / tripKm * 100).toFloat(), DataOrigin.ESTIMATED) else st.averageL100,
                )
            }
            if (slow++ % 10 == 0) {
                st = st.copy(
                    coolantC = s.pid(0x05)?.let { Reading(ObdProtocol.tempC(it), o) } ?: Reading.none(),
                    intakeC = s.pid(0x0F)?.let { Reading(ObdProtocol.tempC(it), o) } ?: Reading.none(),
                    outsideC = s.pid(0x46)?.let { Reading(ObdProtocol.tempC(it), o) } ?: Reading.none(),
                    batteryV = s.voltage()?.let { Reading(it, o) } ?: Reading.none(),
                )
                val fuel = s.pid(0x2F)?.let { ObdProtocol.percent(it) }
                st = if (fuel != null) {
                    val avg = st.averageL100.value ?: 6.0f
                    st.copy(fuelPercent = Reading(fuel, o),
                        rangeKm = Reading((fuel / 100f * PoloSpecs.TANK_LITERS / avg * 100f).roundToInt(), DataOrigin.ESTIMATED))
                } else st.copy(fuelPercent = Reading.none(), rangeKm = Reading.none())
            }
            _state.value = st
            delay(150)
        }
    }

    private fun speedDensity(s: ElmSession, rpm: Int?): Double? {
        if (rpm == null) return null
        val map = s.pid(0x0B)?.get(0) ?: return null
        val iatK = (_state.value.intakeC.value ?: 25) + 273.15
        return map * PoloSpecs.DISPLACEMENT_L * (rpm / 120.0) * 0.80 * 28.97 / (8.314 * iatK)
    }

    override fun stop() {
        job?.cancel(); job = null
        link?.close(); link = null
        _state.value = VehicleState()
        _status.value = SourceStatus(false, "Arrêtée")
    }
}
