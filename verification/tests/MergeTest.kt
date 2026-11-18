import fr.polo.cockpit.vehicle.*
var fails = 0
fun check(n: String, c: Boolean) { if (!c) { fails++; println("ÉCHEC : $n") } else println("ok : $n") }
fun main() {
    val canbox = VehicleState(doors = Reading(DoorState(frontLeft = true), DataOrigin.CANBOX))
    val obd = VehicleState(speedKmh = Reading(42, DataOrigin.OBD))
    val demo = VehicleState(speedKmh = Reading(80, DataOrigin.DEMO), fuelPercent = Reading(60, DataOrigin.DEMO),
        doors = Reading(DoorState(), DataOrigin.DEMO))
    val m = canbox fillWith obd fillWith demo      // même ordre que VehicleRepository
    check("vitesse réelle OBD conservée (42, pas 80)", m.speedKmh.value == 42 && m.speedKmh.origin == DataOrigin.OBD)
    check("portière réelle CanBox conservée", m.doors.value?.frontLeft == true && m.doors.origin == DataOrigin.CANBOX)
    check("carburant absent comblé par la démo ET étiqueté SIMULATION", m.fuelPercent.origin == DataOrigin.DEMO && m.fuelPercent.simulated)
    check("bandeau simulation affiché", m.hasSimulatedData)
    val noDemo = canbox fillWith obd fillWith VehicleState()
    check("sans démo : carburant indisponible (rien d'inventé)", !noDemo.fuelPercent.available && noDemo.fuelPercent.origin == DataOrigin.UNAVAILABLE)
    check("sans démo : pas de bandeau simulation", !noDemo.hasSimulatedData)
    println(if (fails == 0) "TOUS LES TESTS PASSENT" else "$fails ÉCHEC(S)")
}
