package fr.polo.cockpit.vehicle

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow

/**
 * Une source de données véhicule (CanBox, OBD2, démo...).
 * Chaque source ne remplit QUE les champs qu'elle sait réellement lire ;
 * les autres restent [Reading.none]. Aucune source n'a le droit d'écrire sur le bus.
 */
interface VehicleSource {
    val id: String
    val label: String
    val state: StateFlow<VehicleState>
    val status: StateFlow<SourceStatus>
    fun start(scope: CoroutineScope)
    fun stop()
}

data class SourceStatus(
    val connected: Boolean = false,
    val message: String = "Arrêtée",
)
