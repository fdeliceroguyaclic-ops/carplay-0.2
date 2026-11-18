package fr.polo.cockpit.vehicle

/**
 * Logique pure du protocole ELM327 (sans dépendance Android, testée unitairement).
 * Lecture seule : seules les requêtes mode 01 et des commandes de configuration de l'adaptateur passent.
 */
object ObdProtocol {
    private val ALLOWED_AT = setOf("ATZ", "ATWS", "ATE0", "ATL0", "ATS0", "ATH0", "ATSP0", "ATRV", "ATDPN", "ATI", "AT@1", "ATAT1", "ATST96")
    private val MODE01 = Regex("^01[0-9A-F]{2}$")

    /** Liste blanche : aucune commande d'écriture, d'effacement de défauts ou de codage ne peut partir. */
    fun isAllowed(cmd: String): Boolean = cmd in ALLOWED_AT || MODE01.matches(cmd)

    /** Réponses de l'ELM indiquant un problème plutôt qu'une donnée. */
    private val ERROR_TOKENS = listOf("NO DATA", "UNABLE TO CONNECT", "CAN ERROR", "BUS INIT", "BUS ERROR",
        "STOPPED", "ERROR", "?", "FB ERROR", "DATA ERROR", "BUFFER FULL", "LV RESET")

    fun errorToken(raw: String): String? {
        val up = raw.uppercase()
        return ERROR_TOKENS.firstOrNull { up.contains(it) }
    }

    /** Extrait les octets de données d'une réponse mode 01 (ex. "41 0D 32" -> [0x32]). */
    fun parseMode01(raw: String, pid: Int): IntArray? {
        val hex = "%02X".format(pid)
        val clean = raw.uppercase().replace("SEARCHING...", "").replace(" ", "")
        for (line in clean.split('\r', '\n')) {
            val idx = line.indexOf("41$hex")
            if (idx < 0) continue
            val data = line.substring(idx + 4).takeWhile { it in '0'..'9' || it in 'A'..'F' }
            if (data.length < 2) continue
            return IntArray(data.length / 2) { data.substring(it * 2, it * 2 + 2).toInt(16) }
        }
        return null
    }

    /** Décode un masque « PID supportés » (réponse à 0100, 0120, 0140...). */
    fun decodeSupported(base: Int, bytes: IntArray): Set<Int> {
        val out = mutableSetOf<Int>()
        for (i in 0 until minOf(32, bytes.size * 8)) {
            if ((bytes[i / 8] shr (7 - i % 8)) and 1 == 1) out += base + i + 1
        }
        return out
    }

    fun speedKmh(b: IntArray) = b[0]
    fun rpm(b: IntArray) = if (b.size >= 2) (b[0] * 256 + b[1]) / 4 else null
    fun tempC(b: IntArray) = b[0] - 40
    fun percent(b: IntArray) = Math.round(b[0] * 100 / 255.0).toInt()
    fun mafGs(b: IntArray) = if (b.size >= 2) (b[0] * 256 + b[1]) / 100.0 else null

    /** Tension renvoyée par ATRV (ex. "12.4V"). */
    fun voltage(raw: String): Float? =
        Regex("(\\d{1,2}[.,]\\d)\\s*V", RegexOption.IGNORE_CASE).find(raw)?.groupValues?.get(1)?.replace(',', '.')?.toFloatOrNull()

    /** Version de l'adaptateur dans la réponse à ATZ/ATI (ex. "ELM327 v1.5"). */
    fun elmVersion(raw: String): String? = Regex("ELM327\\s*v?[0-9.]+", RegexOption.IGNORE_CASE).find(raw)?.value

    /** Message clair en français à partir d'une erreur technique. */
    fun explain(message: String?): String {
        val m = (message ?: "").lowercase()
        return when {
            "read failed" in m || "socket might closed" in m || "timeout" in m && "connect" in m ->
                "L'adaptateur ne répond pas. Vérifiez qu'il est branché sur la prise OBD, que le contact est mis et qu'il n'est pas déjà connecté à un autre appareil (téléphone)."
            "service discovery failed" in m ->
                "L'adaptateur refuse la connexion série (SPP). Désassociez-le puis réassociez-le (code 1234 ou 0000)."
            "connection refused" in m || "econnrefused" in m ->
                "Connexion refusée : vérifiez l'adresse IP et le port de l'adaptateur Wi-Fi (souvent 192.168.0.10:35000)."
            "network is unreachable" in m || "no route" in m || "enetunreach" in m || "ehostunreach" in m ->
                "Adaptateur Wi-Fi injoignable : connectez d'abord l'autoradio au réseau Wi-Fi de l'ELM327 (souvent « WiFi_OBDII »)."
            "unable to connect" in m || "no data" in m || "bus init" in m || "can error" in m ->
                "L'adaptateur répond mais le calculateur moteur non : mettez le contact (ou démarrez le moteur)."
            "permission" in m || "securityexception" in m ->
                "Autorisation Bluetooth refusée par le système."
            message.isNullOrBlank() -> "Erreur inconnue."
            else -> "Erreur : $message"
        }
    }
}
