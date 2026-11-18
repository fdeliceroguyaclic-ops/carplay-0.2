package fr.polo.cockpit.vehicle

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/**
 * Agrège les sources par ordre de priorité : CanBox (réel) > OBD2 (réel) > démonstration.
 * Une valeur simulée ne remplace jamais une valeur réelle : elle ne comble que les champs vides,
 * et reste étiquetée « SIMULATION ».
 */
class VehicleRepository(context: Context, private val scope: CoroutineScope) {
    val canbox = CanBoxBroadcastSource(context)
    val obd = ObdElm327Source(context)
    val demo = DemoSource()
    val probe = CanBoxProbe(context)

    val state: StateFlow<VehicleState> =
        combine(canbox.state, obd.state, demo.state) { c, o, d -> c fillWith o fillWith d }
            .stateIn(scope, SharingStarted.Eagerly, VehicleState())

    fun setDemo(enabled: Boolean) = if (enabled) demo.start(scope) else demo.stop()

    fun setObd(enabled: Boolean, cfg: ObdConfig) {
        obd.stop()
        obd.config = cfg
        if (enabled) obd.start(scope)
    }

    /** Applique les associations ; renvoie les erreurs de saisie éventuelles. */
    fun setCanMappings(text: String): List<String> {
        val (maps, errors) = CanMapping.parse(text)
        canbox.mappings = maps
        canbox.start(scope)
        return errors
    }
}
