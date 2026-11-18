package fr.polo.cockpit.vehicle

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.math.roundToInt

/**
 * Source CanBox en lecture seule : écoute les diffusions du fabricant désignées par les
 * associations de l'utilisateur ([CanMapping]) et ne remplit que les champs associés.
 * Sans association, elle ne produit rien. Elle n'émet jamais de diffusion ni de commande.
 */
class CanBoxBroadcastSource(private val context: Context) : VehicleSource {
    override val id = "canbox"
    override val label = "CanBox Raise (diffusions du fabricant)"
    private val _state = MutableStateFlow(VehicleState())
    override val state: StateFlow<VehicleState> = _state
    private val _status = MutableStateFlow(SourceStatus(false, "Aucune association définie"))
    override val status: StateFlow<SourceStatus> = _status

    var mappings: List<CanMapping> = emptyList()
    private var receiver: BroadcastReceiver? = null
    private var doors = DoorState()
    private var lights = LightState()
    private var received = 0

    override fun start(scope: CoroutineScope) {
        stop()
        if (mappings.isEmpty()) { _status.value = SourceStatus(false, "Aucune association définie"); return }
        val filter = IntentFilter().apply { mappings.map { it.action }.distinct().forEach { addAction(it) } }
        val r = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) = runCatching { handle(intent) }.let { }
        }
        val sticky = runCatching { ContextCompat.registerReceiver(context, r, filter, ContextCompat.RECEIVER_EXPORTED) }
            .onFailure { _status.value = SourceStatus(false, "Écoute impossible : ${it.message}") }.getOrNull()
        receiver = r
        _status.value = SourceStatus(false, "En attente de données (${mappings.size} association(s))")
        sticky?.let { runCatching { handle(it) } }
    }

    @Suppress("DEPRECATION")
    private fun handle(intent: Intent) {
        val extras = intent.extras ?: return
        var st = _state.value
        val o = DataOrigin.CANBOX
        for (m in mappings.filter { it.action == intent.action }) {
            if (!extras.containsKey(m.extra)) continue
            val v = m.numeric(extras.get(m.extra)) ?: continue
            val on = v != 0.0
            st = when (m.field) {
                "vitesse" -> st.copy(speedKmh = Reading(v.roundToInt(), o))
                "regime" -> st.copy(rpm = Reading(v.roundToInt(), o))
                "temp_moteur" -> st.copy(coolantC = Reading(v.roundToInt(), o))
                "carburant" -> st.copy(fuelPercent = Reading(v.roundToInt(), o))
                "autonomie" -> st.copy(rangeKm = Reading(v.roundToInt(), o))
                "temp_ext" -> st.copy(outsideC = Reading(v.roundToInt(), o))
                "batterie" -> st.copy(batteryV = Reading(v.toFloat(), o))
                "frein_main" -> st.copy(handbrake = Reading(on, o))
                "porte_avg" -> { doors = doors.copy(frontLeft = on); st.copy(doors = Reading(doors, o)) }
                "porte_avd" -> { doors = doors.copy(frontRight = on); st.copy(doors = Reading(doors, o)) }
                "porte_arg" -> { doors = doors.copy(rearLeft = on); st.copy(doors = Reading(doors, o)) }
                "porte_ard" -> { doors = doors.copy(rearRight = on); st.copy(doors = Reading(doors, o)) }
                "coffre" -> { doors = doors.copy(trunk = on); st.copy(doors = Reading(doors, o)) }
                "feux_croisement" -> { lights = lights.copy(lowBeam = on); st.copy(lights = Reading(lights, o)) }
                "feux_route" -> { lights = lights.copy(highBeam = on); st.copy(lights = Reading(lights, o)) }
                "clignotant_g" -> { lights = lights.copy(leftIndicator = on); st.copy(lights = Reading(lights, o)) }
                "clignotant_d" -> { lights = lights.copy(rightIndicator = on); st.copy(lights = Reading(lights, o)) }
                else -> st
            }
        }
        received++
        _state.value = st
        _status.value = SourceStatus(true, "$received diffusion(s) reçue(s) — dernière à ${java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.FRANCE).format(java.util.Date())}")
    }

    override fun stop() {
        receiver?.let { runCatching { context.unregisterReceiver(it) } }
        receiver = null
        doors = DoorState(); lights = LightState(); received = 0
        _state.value = VehicleState()
    }
}
