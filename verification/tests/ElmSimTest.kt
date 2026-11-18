import fr.polo.cockpit.vehicle.*
import java.io.*

/** ELM327 simulé : répond comme un vrai adaptateur, avec un calculateur Polo (CAN). */
class FakeElm(val ecuOn: Boolean) {
    val toElm = PipedOutputStream(); private val elmIn = PipedInputStream(toElm, 4096)
    private val elmOut = PipedOutputStream(); val fromElm = PipedInputStream(elmOut, 4096)
    val received = mutableListOf<String>()
    init {
        Thread {
            val sb = StringBuilder()
            while (true) {
                val c = elmIn.read(); if (c < 0) break
                if (c.toChar() != '\r') { sb.append(c.toChar()); continue }
                val cmd = sb.toString(); sb.setLength(0); received += cmd
                val r = when {
                    cmd == "ATZ" -> "\r\rELM327 v1.5"
                    cmd == "ATRV" -> "12.4V"
                    cmd.startsWith("AT") -> "OK"
                    !ecuOn -> "SEARCHING...\rUNABLE TO CONNECT"
                    cmd == "0100" -> "SEARCHING...\r41 00 BE 1F A8 13"   // 01-20 : 05,0C,0D,0F,10?...
                    cmd == "0120" -> "41 20 80 00 00 00"
                    cmd == "010D" -> "41 0D 32"
                    cmd == "010C" -> "41 0C 2E E0"
                    cmd == "0105" -> "41 05 82"
                    else -> "NO DATA"
                }
                elmOut.write("$r\r\r>".toByteArray()); elmOut.flush()
            }
        }.apply { isDaemon = true; start() }
    }
}

var fails = 0
fun check(n: String, c: Boolean) { if (!c) { fails++; println("ÉCHEC : $n") } else println("ok : $n") }

fun main() {
    val f = FakeElm(ecuOn = true)
    val s = ElmSession(ObdLink(f.fromElm, f.toElm, "simulé", Closeable { }))
    check("version ELM détectée", s.init() == "ELM327 v1.5")
    check("calculateur répond", s.readSupported() == null)
    check("PID vitesse/régime/temp supportés", 0x0D in s.supported && 0x0C in s.supported && 0x05 in s.supported)
    check("vitesse 50 km/h", s.pid(0x0D)?.let { ObdProtocol.speedKmh(it) } == 50)
    check("régime 3000 tr/min", s.pid(0x0C)?.let { ObdProtocol.rpm(it) } == 3000)
    check("temp moteur 90 °C", s.pid(0x05)?.let { ObdProtocol.tempC(it) } == 90)
    check("carburant non fourni -> null (pas de valeur inventée)", s.pid(0x2F) == null)
    check("tension 12.4 V", s.voltage() == 12.4f)
    var refused = false
    try { s.send("04") } catch (e: IllegalArgumentException) { refused = true }
    check("effacement des défauts (04) refusé avant envoi", refused && "04" !in f.received)
    check("seules des commandes autorisées ont été envoyées", f.received.all { ObdProtocol.isAllowed(it) })

    val off = FakeElm(ecuOn = false)
    val s2 = ElmSession(ObdLink(off.fromElm, off.toElm, "simulé", Closeable { }))
    check("contact coupé : adaptateur OK", s2.init() != null)
    val err = s2.readSupported()
    check("contact coupé : erreur explicite", err == "UNABLE TO CONNECT")
    check("contact coupé : message clair", ObdProtocol.explain(err).contains("mettez le contact"))
    println(if (fails == 0) "TOUS LES TESTS PASSENT" else "$fails ÉCHEC(S)")
}
