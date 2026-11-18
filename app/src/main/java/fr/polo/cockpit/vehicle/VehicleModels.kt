package fr.polo.cockpit.vehicle

/**
 * Origine d'une donnée. Toute valeur affichée porte son origine :
 * l'interface ne montre JAMAIS une simulation comme une donnée réelle.
 */
enum class DataOrigin(val label: String) {
    CANBOX("CanBox"),
    OBD("OBD2"),
    ESTIMATED("Estimé"),
    DEMO("SIMULATION"),
    UNAVAILABLE("Indisponible");

    val isReal get() = this == CANBOX || this == OBD
}

data class Reading<out T>(
    val value: T?,
    val origin: DataOrigin,
    val updatedAt: Long = System.currentTimeMillis(),
) {
    val available get() = value != null && origin != DataOrigin.UNAVAILABLE
    val simulated get() = origin == DataOrigin.DEMO

    companion object {
        private val NONE = Reading<Nothing>(null, DataOrigin.UNAVAILABLE, 0L)
        fun <T> none(): Reading<T> = NONE
    }
}

data class DoorState(
    val frontLeft: Boolean = false,
    val frontRight: Boolean = false,
    val rearLeft: Boolean = false,
    val rearRight: Boolean = false,
    val trunk: Boolean = false,
) {
    val anyOpen get() = frontLeft || frontRight || rearLeft || rearRight || trunk
}

data class LightState(
    val lowBeam: Boolean = false,
    val highBeam: Boolean = false,
    val leftIndicator: Boolean = false,
    val rightIndicator: Boolean = false,
)

data class VehicleState(
    val speedKmh: Reading<Int> = Reading.none(),
    val rpm: Reading<Int> = Reading.none(),
    val coolantC: Reading<Int> = Reading.none(),
    val fuelPercent: Reading<Int> = Reading.none(),
    val rangeKm: Reading<Int> = Reading.none(),
    val instantL100: Reading<Float> = Reading.none(),
    val averageL100: Reading<Float> = Reading.none(),
    val batteryV: Reading<Float> = Reading.none(),
    val intakeC: Reading<Int> = Reading.none(),
    val outsideC: Reading<Int> = Reading.none(),
    val doors: Reading<DoorState> = Reading.none(),
    val handbrake: Reading<Boolean> = Reading.none(),
    val lights: Reading<LightState> = Reading.none(),
    val alerts: Reading<List<String>> = Reading.none(),
) {
    /** Fusion champ par champ : on garde la valeur de [this] si elle est disponible, sinon celle de [other]. */
    infix fun fillWith(other: VehicleState) = VehicleState(
        speedKmh = speedKmh.or(other.speedKmh),
        rpm = rpm.or(other.rpm),
        coolantC = coolantC.or(other.coolantC),
        fuelPercent = fuelPercent.or(other.fuelPercent),
        rangeKm = rangeKm.or(other.rangeKm),
        instantL100 = instantL100.or(other.instantL100),
        averageL100 = averageL100.or(other.averageL100),
        batteryV = batteryV.or(other.batteryV),
        intakeC = intakeC.or(other.intakeC),
        outsideC = outsideC.or(other.outsideC),
        doors = doors.or(other.doors),
        handbrake = handbrake.or(other.handbrake),
        lights = lights.or(other.lights),
        alerts = alerts.or(other.alerts),
    )

    val hasSimulatedData
        get() = listOf(speedKmh, rpm, coolantC, fuelPercent, rangeKm, instantL100, averageL100,
            doors, handbrake, lights).any { it.simulated }
}

private fun <T> Reading<T>.or(other: Reading<T>): Reading<T> = if (available) this else other

/** Fiche technique fixe du véhicule (affichage). */
object PoloSpecs {
    const val MAKE = "Volkswagen"
    const val MODEL = "Polo V"
    const val GENERATION = "6R / 6C"
    const val BODY = "5 portes"
    const val ENGINE = "1.2 essence"
    const val POWER = "44 kW / 60 ch"
    const val PERIOD = "06/2009 – 05/2014"
    const val GEARBOX = "Manuelle"
    const val DRIVE = "Conduite à gauche"
    const val TANK_LITERS = 45f        // réservoir Polo 6R
    const val DISPLACEMENT_L = 1.198f  // 3 cylindres 1.2 MPI

    val rows = listOf(
        "Constructeur" to MAKE, "Modèle" to MODEL, "Génération" to GENERATION,
        "Carrosserie" to BODY, "Motorisation" to ENGINE, "Puissance" to POWER,
        "Période" to PERIOD, "Boîte" to GEARBOX, "Configuration" to DRIVE,
        "Réservoir" to "45 L",
    )
}
