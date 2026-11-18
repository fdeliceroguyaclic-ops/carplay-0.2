package fr.polo.cockpit.vehicle

/**
 * Associations « diffusion du fabricant → donnée véhicule », saisies par l'utilisateur à partir
 * de ce que la capture guidée a réellement observé. Rien n'est supposé : sans association,
 * aucune donnée CanBox n'est affichée.
 *
 * Format d'une ligne :  ACTION;EXTRA;CHAMP[;FACTEUR | bit:N]
 * Exemple :             com.exemple.CAR_INFO;speed;vitesse;1
 *                       com.exemple.DOOR;state;porte_avg;bit:0
 */
data class CanMapping(val action: String, val extra: String, val field: String, val factor: Double = 1.0, val bit: Int? = null) {

    /** Convertit la valeur brute d'un extra en nombre (null si non numérique). */
    fun numeric(raw: Any?): Double? {
        val v: Double = when (raw) {
            is Boolean -> if (raw) 1.0 else 0.0
            is Number -> raw.toDouble()
            is String -> raw.trim().replace(',', '.').toDoubleOrNull() ?: return null
            else -> return null
        }
        return if (bit != null) (((v.toLong() shr bit) and 1L).toDouble()) else v * factor
    }

    companion object {
        val NUMERIC_FIELDS = setOf("vitesse", "regime", "temp_moteur", "carburant", "autonomie", "temp_ext", "batterie")
        val BOOLEAN_FIELDS = setOf("frein_main", "porte_avg", "porte_avd", "porte_arg", "porte_ard", "coffre",
            "feux_croisement", "feux_route", "clignotant_g", "clignotant_d")
        val FIELDS = NUMERIC_FIELDS + BOOLEAN_FIELDS

        /** Analyse le texte saisi ; renvoie les associations valides et les erreurs ligne par ligne. */
        fun parse(text: String): Pair<List<CanMapping>, List<String>> {
            val ok = mutableListOf<CanMapping>()
            val errors = mutableListOf<String>()
            text.lines().forEachIndexed { i, rawLine ->
                val line = rawLine.trim()
                if (line.isEmpty() || line.startsWith("#")) return@forEachIndexed
                val p = line.split(';').map { it.trim() }
                if (p.size !in 3..4 || p[0].isEmpty() || p[1].isEmpty()) {
                    errors += "Ligne ${i + 1} : format attendu ACTION;EXTRA;CHAMP[;FACTEUR]"; return@forEachIndexed
                }
                if (p[2] !in FIELDS) {
                    errors += "Ligne ${i + 1} : champ inconnu « ${p[2]} »"; return@forEachIndexed
                }
                var factor = 1.0
                var bit: Int? = null
                if (p.size == 4 && p[3].isNotEmpty()) {
                    if (p[3].startsWith("bit:")) {
                        bit = p[3].removePrefix("bit:").toIntOrNull()
                        if (bit == null || bit !in 0..62) { errors += "Ligne ${i + 1} : bit invalide"; return@forEachIndexed }
                    } else {
                        factor = p[3].replace(',', '.').toDoubleOrNull()
                            ?: run { errors += "Ligne ${i + 1} : facteur invalide"; return@forEachIndexed }
                    }
                }
                ok += CanMapping(p[0], p[1], p[2], factor, bit)
            }
            return ok to errors
        }
    }
}

/** Repère, parmi les chaînes extraites du code du fabricant, celles qui ressemblent à des actions de diffusion. */
object ActionCandidates {
    private val SHAPE = Regex("^[a-zA-Z][a-zA-Z0-9_]*(\\.[a-zA-Z0-9_]+){2,}$")
    private val HINTS = listOf("can", "car", "mcu", "door", "radar", "fuel", "speed", "light", "brake", "vehicle", "canbox", "info")

    fun isCandidate(s: String): Boolean {
        if (s.length !in 8..120 || !SHAPE.matches(s)) return false
        val last = s.substringAfterLast('.')
        val lower = s.lowercase()
        // une action est en général une constante en majuscules ou contient « action »
        val looksLikeAction = last.any { it.isUpperCase() } && last == last.uppercase() || "action" in lower
        if (!looksLikeAction) return false
        if (lower.startsWith("android.") || lower.startsWith("java.") || lower.startsWith("kotlin.")) return false
        return HINTS.any { it in lower }
    }
}
