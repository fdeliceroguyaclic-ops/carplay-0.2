package fr.polo.cockpit.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import fr.polo.cockpit.PoloApp
import fr.polo.cockpit.data.CockpitSettings
import fr.polo.cockpit.render.CarRenderer
import fr.polo.cockpit.weather.Weather
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class Screen(val label: String) { HOME("Accueil"), VEHICLE("Véhicule"), CAR3D("Ma Polo"), APPS("Applis"), SETTINGS("Réglages") }

class CockpitViewModel(app: Application) : AndroidViewModel(app) {
    private val poloApp = app as PoloApp
    val launcher = poloApp.launcher
    val vehicleRepo = poloApp.vehicle
    val vehicle = vehicleRepo.state

    val settings: StateFlow<CockpitSettings?> =
        poloApp.settings.settings.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val screen = MutableStateFlow(Screen.HOME)
    val weather = MutableStateFlow<Weather?>(null)
    /** Incrémenté à chaque import de modèle pour recharger la vue 3D. */
    val modelKey = MutableStateFlow(0)
    val hasModel = MutableStateFlow(CarRenderer.importedModel(app).exists() || assetModelExists())
    val message = MutableStateFlow<String?>(null)
    /** Déclaré avant init : utilisé par un collecteur lancé dans init. */
    val canMappingErrors = MutableStateFlow<List<String>>(emptyList())

    init {
        // Applique les réglages des sources de données à chaque changement
        viewModelScope.launch {
            poloApp.settings.settings.map { it.demoMode }.distinctUntilChanged().collect { vehicleRepo.setDemo(it) }
        }
        viewModelScope.launch {
            // La lecture OBD ne démarre que si la configuration actuelle a été testée avec succès.
            poloApp.settings.settings.map { (it.obdEnabled && it.obdConfigVerified) to it.obdConfig }
                .distinctUntilChanged()
                .collect { (enabled, cfg) -> vehicleRepo.setObd(enabled, cfg) }
        }
        viewModelScope.launch {
            poloApp.settings.settings.map { it.canMappings }.distinctUntilChanged().collect {
                canMappingErrors.value = vehicleRepo.setCanMappings(it)
            }
        }
        viewModelScope.launch {
            poloApp.settings.settings.map { it.listenActions }.distinctUntilChanged().collect {
                vehicleRepo.probe.startListening(it.lines())
            }
        }
        // Météo toutes les 20 min (silencieux si hors ligne)
        viewModelScope.launch {
            while (true) {
                settings.value?.let { s ->
                    if (s.widgetWeather) weather.value = poloApp.weather.fetch(s.weatherLat, s.weatherLon, s.weatherCity) ?: weather.value
                }
                delay(if (weather.value == null) 60_000L else 20 * 60_000L)
            }
        }
    }

    private fun assetModelExists() =
        runCatching { getApplication<Application>().assets.open(CarRenderer.MODEL_FILE).close(); true }.getOrDefault(false)

    fun update(transform: (CockpitSettings) -> CockpitSettings) {
        viewModelScope.launch { poloApp.settings.update(transform) }
    }

    fun go(s: Screen) { screen.value = s }

    fun launch(key: String) = launcher.launch(key, settings.value?.overrides ?: emptyMap())

    /** Copie le fichier GLB choisi dans le stockage interne de l'appli. */
    fun importModel(uri: Uri) {
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    val app = getApplication<Application>()
                    val dest = CarRenderer.importedModel(app)
                    dest.parentFile?.mkdirs()
                    app.contentResolver.openInputStream(uri)!!.use { input ->
                        val head = ByteArray(4); input.read(head)
                        require(String(head) == "glTF") { "Ce fichier n'est pas un GLB (glTF binaire)" }
                        dest.outputStream().use { it.write(head); input.copyTo(it) }
                    }
                }
            }
            ok.onSuccess {
                hasModel.value = true; modelKey.value++
                message.value = "Modèle 3D importé"
            }.onFailure { message.value = "Import impossible : ${it.message}" }
        }
    }

    fun deleteModel() {
        CarRenderer.importedModel(getApplication()).delete()
        hasModel.value = assetModelExists(); modelKey.value++
        message.value = "Modèle 3D supprimé"
    }

    fun setWallpaper(uri: Uri?) {
        if (uri != null) runCatching {
            getApplication<Application>().contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        update { it.copy(wallpaperUri = uri?.toString()) }
    }

    // ---------------------------------------------------------------- rapport de compatibilité
    val reportStatus = MutableStateFlow<String?>(null)
    /** Dernier rapport généré (affiché à l'écran et enregistrable). */
    val reportText = MutableStateFlow<String?>(null)
    val reportBusy = MutableStateFlow(false)
    fun buildReport() {
        if (reportBusy.value) return
        reportBusy.value = true
        viewModelScope.launch {
            runCatching { vehicleRepo.probe.buildReport { reportStatus.value = it } }
                .onSuccess { reportText.value = it; reportStatus.value = "Rapport généré (${it.lines().size} lignes). Touchez « Enregistrer » pour choisir où le sauvegarder." }
                .onFailure { reportStatus.value = "Échec de la génération : ${it.javaClass.simpleName} — ${it.message}" }
            reportBusy.value = false
        }
    }

    /** Enregistrement à l'emplacement choisi par l'utilisateur (ACTION_CREATE_DOCUMENT). */
    fun saveReportTo(uri: Uri) {
        val text = reportText.value ?: run { reportStatus.value = "Générez d'abord le rapport."; return }
        viewModelScope.launch {
            val r = withContext(Dispatchers.IO) { runCatching { fr.polo.cockpit.vehicle.ReportExporter.writeToUri(getApplication(), uri, text) } }
            reportStatus.value = r.getOrElse { "Échec de l'enregistrement : ${it.message}. Essayez « Enregistrer dans le dossier de l'appli »." }
        }
    }

    /** Secours si l'autoradio n'a pas de sélecteur de fichiers. */
    fun saveReportFallback() {
        val text = reportText.value ?: run { reportStatus.value = "Générez d'abord le rapport."; return }
        viewModelScope.launch {
            val r = withContext(Dispatchers.IO) { runCatching { fr.polo.cockpit.vehicle.ReportExporter.writeFallback(getApplication(), text) } }
            reportStatus.value = r.getOrElse { "Échec de l'enregistrement : ${it.message}" }
        }
    }

    fun exportVendorCode(uri: Uri) {
        viewModelScope.launch {
            reportStatus.value = "Export des applis du fabricant en cours…"
            val r = withContext(Dispatchers.IO) { runCatching { vehicleRepo.probe.exportVendorCode(uri) } }
            reportStatus.value = r.getOrElse { "Échec de l'export : ${it.message}" }
        }
    }

    fun startCapture() {
        viewModelScope.launch {
            runCatching { vehicleRepo.probe.guidedCapture(settings.value?.listenActions?.lines().orEmpty()) }
                .onFailure { message.value = "Capture impossible : ${it.message}" }
        }
    }

    // ---------------------------------------------------------------- OBD2
    val obdTest = MutableStateFlow<fr.polo.cockpit.vehicle.ObdTestResult?>(null)
    val obdTesting = MutableStateFlow(false)
    val btDiag = MutableStateFlow<fr.polo.cockpit.vehicle.BtDiagnostic?>(null)

    fun refreshBluetooth() {
        viewModelScope.launch {
            btDiag.value = withContext(Dispatchers.IO) {
                runCatching { fr.polo.cockpit.vehicle.ObdLinks.diagnose(getApplication()) }.getOrElse {
                    fr.polo.cockpit.vehicle.BtDiagnostic(false, false, false, emptyList(), "Erreur Bluetooth : ${it.message}")
                }
            }
        }
    }

    fun testObd() {
        val cfg = settings.value?.obdConfig ?: return
        if (obdTesting.value) return
        obdTesting.value = true
        obdTest.value = null
        viewModelScope.launch {
            val r = vehicleRepo.obd.testConnection(cfg)
            obdTest.value = r
            // Mémorise la configuration vérifiée (adaptateur qui répond). Le calculateur peut exiger le contact.
            update { it.copy(obdVerified = if (r.adapterOk) cfg.key else null, obdEnabled = if (r.adapterOk) it.obdEnabled else false) }
            // Le test a interrompu une lecture en cours : on la relance si elle reste autorisée.
            if (r.adapterOk && settings.value?.obdEnabled == true) vehicleRepo.setObd(true, cfg)
            obdTesting.value = false
        }
    }
}
