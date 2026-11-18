package fr.polo.cockpit.vehicle

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Simulation d'un trajet urbain/route. Toutes les valeurs sont marquées [DataOrigin.DEMO]
 * et l'interface affiche un bandeau « SIMULATION ».
 */
class DemoSource : VehicleSource {
    override val id = "demo"
    override val label = "Mode démonstration"
    private val _state = MutableStateFlow(VehicleState())
    override val state: StateFlow<VehicleState> = _state
    private val _status = MutableStateFlow(SourceStatus())
    override val status: StateFlow<SourceStatus> = _status
    private var job: Job? = null

    override fun start(scope: CoroutineScope) {
        if (job?.isActive == true) return
        _status.value = SourceStatus(true, "Simulation active")
        job = scope.launch {
            var t = 0.0
            var fuel = 62.0
            var coolant = 22.0
            while (isActive) {
                t += 0.1
                // cycle de 60 s : arrêt, accélération, croisière, freinage
                val phase = (t % 60.0) / 60.0
                val speed = max(0.0, 85 * sin(phase * PI) - 8).coerceAtMost(90.0)
                val gearRatio = when {
                    speed < 18 -> 120.0; speed < 32 -> 75.0; speed < 50 -> 52.0
                    speed < 70 -> 40.0; else -> 33.0
                }
                val rpm = if (speed < 1) 780.0 else (speed * gearRatio).coerceIn(1100.0, 4200.0)
                coolant = (coolant + 0.25).coerceAtMost(90.0)
                fuel = (fuel - 0.0004).coerceAtLeast(5.0)
                val instant = if (speed < 1) 0f else (4.2 + 3.5 * sin(phase * 2 * PI + 1).coerceAtLeast(0.0)).toFloat()
                val blink = speed in 5.0..25.0 && phase < 0.25 && ((t * 2.5).toInt() % 2 == 0)
                val trunkOpen = speed < 0.5 && phase > 0.95

                val d = DataOrigin.DEMO
                _state.value = VehicleState(
                    speedKmh = Reading(speed.roundToInt(), d),
                    rpm = Reading(rpm.roundToInt(), d),
                    coolantC = Reading(coolant.roundToInt(), d),
                    fuelPercent = Reading(fuel.roundToInt(), d),
                    rangeKm = Reading((fuel / 100 * PoloSpecs.TANK_LITERS / 5.9 * 100).roundToInt(), d),
                    instantL100 = Reading(instant, d),
                    averageL100 = Reading(5.9f, d),
                    batteryV = Reading(14.1f, d),
                    intakeC = Reading(31, d),
                    outsideC = Reading(29, d),
                    doors = Reading(DoorState(trunk = trunkOpen), d),
                    handbrake = Reading(speed < 0.5, d),
                    lights = Reading(LightState(lowBeam = true, leftIndicator = blink), d),
                    alerts = Reading(if (fuel < 10) listOf("Réserve carburant") else emptyList(), d),
                )
                delay(100)
            }
        }
    }

    override fun stop() {
        job?.cancel(); job = null
        _state.value = VehicleState()
        _status.value = SourceStatus(false, "Arrêtée")
    }
}
