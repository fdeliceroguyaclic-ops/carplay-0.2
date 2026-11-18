import fr.polo.cockpit.vehicle.*

var fails = 0
fun check(name: String, cond: Boolean) { if (!cond) { fails++; println("ÉCHEC : $name") } else println("ok : $name") }

fun main() {
    // Liste blanche
    check("0100 autorisé", ObdProtocol.isAllowed("0100"))
    check("010D autorisé", ObdProtocol.isAllowed("010D"))
    check("04 (effacement défauts) refusé", !ObdProtocol.isAllowed("04"))
    check("0902 refusé", !ObdProtocol.isAllowed("0902"))
    check("ATSH refusé", !ObdProtocol.isAllowed("ATSH7E0"))
    check("2E (écriture) refusé", !ObdProtocol.isAllowed("2E F1 90"))
    check("ATMA (moniteur bus) refusé", !ObdProtocol.isAllowed("ATMA"))
    // Analyse des réponses
    check("vitesse 50", ObdProtocol.parseMode01("41 0D 32", 0x0D)?.let { ObdProtocol.speedKmh(it) } == 50)
    check("régime 3000", ObdProtocol.parseMode01("SEARCHING...\r41 0C 2E E0\r", 0x0C)?.let { ObdProtocol.rpm(it) } == 3000)
    check("temp 90", ObdProtocol.parseMode01("410582", 0x05)?.let { ObdProtocol.tempC(it) } == 90)
    check("NO DATA -> null", ObdProtocol.parseMode01("NO DATA", 0x2F) == null)
    check("multi-calculateurs", ObdProtocol.parseMode01("7E8 03 41 0D 28\r41 0D 28", 0x0D)?.get(0) == 0x28)
    check("carburant 50%", ObdProtocol.parseMode01("41 2F 80", 0x2F)?.let { ObdProtocol.percent(it) } == 50)
    val sup = ObdProtocol.decodeSupported(0x00, intArrayOf(0xBE, 0x1F, 0xA8, 0x13))
    check("PID 0C/0D/05 supportés", 0x0C in sup && 0x0D in sup && 0x05 in sup)
    check("PID 20 supporté (suite)", 0x20 in sup)
    check("PID 02 non supporté", 0x02 !in sup)
    check("tension", ObdProtocol.voltage("12.4V") == 12.4f)
    check("version", ObdProtocol.elmVersion("\r\rELM327 v1.5\r") == "ELM327 v1.5")
    check("erreur UNABLE", ObdProtocol.errorToken("SEARCHING...\rUNABLE TO CONNECT") == "UNABLE TO CONNECT")
    check("message socket", ObdProtocol.explain("read failed, socket might closed or timeout, read ret: -1").startsWith("L'adaptateur ne répond pas"))
    check("message wifi", ObdProtocol.explain("connect failed: ENETUNREACH (Network is unreachable)").startsWith("Adaptateur Wi-Fi"))
    // Associations CanBox
    val (maps, errs) = CanMapping.parse("# commentaire\ncom.x.CAR_INFO;speed;vitesse;1\ncom.x.DOOR;state;porte_avg;bit:2\ncom.x.A;b;inconnu\nmauvais")
    check("2 associations valides", maps.size == 2)
    check("2 erreurs signalées", errs.size == 2)
    check("bit 2 de 4 = 1", maps[1].numeric(4) == 1.0)
    check("bit 2 de 3 = 0", maps[1].numeric(3) == 0.0)
    check("chaîne numérique", maps[0].numeric("88") == 88.0)
    check("non numérique -> null", maps[0].numeric(listOf(1)) == null)
    // Candidats d'actions
    check("action CAN reconnue", ActionCandidates.isCandidate("com.syu.canbus.ACTION_DOOR_STATE"))
    check("action car info", ActionCandidates.isCandidate("com.fyt.car.CARINFO"))
    check("classe Java ignorée", !ActionCandidates.isCandidate("com.example.canbus.DoorManager"))
    check("android.* ignoré", !ActionCandidates.isCandidate("android.intent.action.BOOT_COMPLETED"))
    println(if (fails == 0) "TOUS LES TESTS PASSENT" else "$fails ÉCHEC(S)")
}
