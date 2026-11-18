package fr.polo.cockpit.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.polo.cockpit.BuildConfig
import fr.polo.cockpit.apps.InstalledApp
import fr.polo.cockpit.data.ACCENT_PRESETS
import fr.polo.cockpit.data.BODY_PRESETS
import fr.polo.cockpit.data.ClockStyle
import fr.polo.cockpit.data.CockpitSettings
import fr.polo.cockpit.ui.CockpitViewModel
import fr.polo.cockpit.ui.components.GlassCard
import fr.polo.cockpit.ui.theme.Cockpit
import fr.polo.cockpit.ui.theme.LocalAccent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val TABS = listOf("Apparence", "Véhicule", "Écran d'accueil", "Raccourcis", "Données véhicule", "Système")

@Composable
fun SettingsScreen(vm: CockpitViewModel, s: CockpitSettings) {
    var tab by remember { mutableIntStateOf(0) }
    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.width(190.dp).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            TABS.forEachIndexed { i, t -> Chip(t, tab == i) { tab = i } }
        }
        Column(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp)) {
            when (tab) {
                0 -> AppearanceTab(vm, s)
                1 -> VehicleTab(vm, s)
                2 -> HomeTab(vm, s)
                3 -> ShortcutsTab(vm, s)
                4 -> DataTab(vm, s)
                5 -> SystemTab(vm)
            }
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    GlassCard(Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title.uppercase(), style = MaterialTheme.typography.labelSmall)
            content()
        }
    }
}

@Composable
private fun Toggle(label: String, checked: Boolean, detail: String? = null, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label, fontSize = 14.sp)
            detail?.let { Text(it, fontSize = 11.sp, color = Cockpit.TextDim) }
        }
        Switch(checked, onChange, colors = SwitchDefaults.colors(checkedTrackColor = LocalAccent.current))
    }
}

@Composable
private fun Field(label: String, value: String, onChange: (String) -> Unit) {
    var text by remember(label) { mutableStateOf(value) }
    OutlinedTextField(text, { text = it; onChange(it) }, label = { Text(label) }, singleLine = true, modifier = Modifier.fillMaxWidth())
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AppearanceTab(vm: CockpitViewModel, s: CockpitSettings) {
    val wallpaperPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { vm.setWallpaper(it) }
    }
    Section("Couleur de l'interface") {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ACCENT_PRESETS.forEach { (name, c) -> Swatch(Color(c), s.accent == c, name) { vm.update { it.copy(accent = c) } } }
        }
        Field("Couleur personnalisée (ex. #FF6A00)", "") { hex ->
            parseHex(hex)?.let { c -> vm.update { it.copy(accent = c) } }
        }
    }
    Section("Horloge") {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            ClockStyle.entries.forEach { cs -> Chip(cs.label, s.clockStyle == cs) { vm.update { it.copy(clockStyle = cs) } } }
        }
        Toggle("Afficher les secondes", s.showSeconds) { v -> vm.update { it.copy(showSeconds = v) } }
    }
    Section("Animations et luminosité") {
        Toggle("Animations", s.animations, "Désactiver si l'autoradio manque de fluidité") { v -> vm.update { it.copy(animations = v) } }
        Toggle("Animation de démarrage", s.splash) { v -> vm.update { it.copy(splash = v) } }
        Text("Atténuation de l'interface (conduite de nuit) : ${(s.dim * 100).toInt()} %", fontSize = 13.sp)
        Slider(s.dim, { v -> vm.update { it.copy(dim = v) } }, valueRange = 0f..0.7f,
            colors = SliderDefaults.colors(thumbColor = LocalAccent.current, activeTrackColor = LocalAccent.current))
    }
    Section("Fond d'écran") {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Chip("Choisir une image", false) { wallpaperPicker.launch(arrayOf("image/*")) }
            if (s.wallpaperUri != null) Chip("Fond par défaut", false) { vm.setWallpaper(null) }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun VehicleTab(vm: CockpitViewModel, s: CockpitSettings) {
    Section("Identité") {
        Field("Nom affiché du véhicule", s.vehicleName) { v -> vm.update { it.copy(vehicleName = v.ifBlank { "POLO V" }) } }
        Field("Immatriculation affichée", s.plate) { v -> vm.update { it.copy(plate = v) } }
    }
    Section("Couleur du véhicule 3D") {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            BODY_PRESETS.forEach { (name, c) -> Swatch(Color(c), s.bodyColor == c, name) { vm.update { it.copy(bodyColor = c) } } }
        }
        Field("Teinte personnalisée (ex. #C0C4C8)", "") { hex -> parseHex(hex)?.let { c -> vm.update { it.copy(bodyColor = c) } } }
    }
}

@Composable
private fun HomeTab(vm: CockpitViewModel, s: CockpitSettings) {
    Section("Widgets affichés") {
        Toggle("Météo", s.widgetWeather, "Nécessite Internet (Open-Meteo, gratuit, sans compte)") { v -> vm.update { it.copy(widgetWeather = v) } }
        Toggle("Carburant / autonomie / moteur", s.widgetVehicle) { v -> vm.update { it.copy(widgetVehicle = v) } }
        Toggle("Consommation", s.widgetConsumption) { v -> vm.update { it.copy(widgetConsumption = v) } }
    }
    Section("Lieu pour la météo") {
        Field("Ville affichée", s.weatherCity) { v -> vm.update { it.copy(weatherCity = v) } }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(Modifier.weight(1f)) { Field("Latitude", s.weatherLat.toString()) { v -> v.toFloatOrNull()?.let { f -> vm.update { it.copy(weatherLat = f) } } } }
            Column(Modifier.weight(1f)) { Field("Longitude", s.weatherLon.toString()) { v -> v.toFloatOrNull()?.let { f -> vm.update { it.copy(weatherLon = f) } } } }
        }
    }
}

@Composable
private fun ShortcutsTab(vm: CockpitViewModel, s: CockpitSettings) {
    val apps by produceState(emptyList<InstalledApp>()) { value = withContext(Dispatchers.IO) { vm.launcher.installedApps() } }
    var picking by remember { mutableStateOf<String?>(null) }
    Section("Applications associées aux raccourcis") {
        Text("« Auto » = détection automatique. Choisissez une appli si la détection se trompe (ex. votre appli CarPlay).",
            fontSize = 12.sp, color = Cockpit.TextDim)
        vm.launcher.shortcuts.forEach { sc ->
            val resolved = remember(s.shortcutOverrides, apps) { vm.launcher.resolve(sc, s.overrides) }
            val label = apps.firstOrNull { it.packageName == resolved }?.label ?: resolved ?: "Aucune (action système)"
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(sc.label, fontSize = 14.sp, modifier = Modifier.width(150.dp))
                Text((if (s.overrides.containsKey(sc.key)) "" else "Auto · ") + label, fontSize = 12.sp,
                    color = Cockpit.TextDim, modifier = Modifier.weight(1f))
                Chip("Choisir", false) { picking = sc.key }
                Spacer(Modifier.width(6.dp))
                if (s.overrides.containsKey(sc.key)) Chip("Auto", false) {
                    vm.update { it.copy(shortcutOverrides = (it.overrides - sc.key).entries.joinToString(";") { e -> "${e.key}=${e.value}" }) }
                }
            }
        }
    }
    picking?.let { key ->
        AlertDialog(
            onDismissRequest = { picking = null },
            confirmButton = { TextButton({ picking = null }) { Text("Annuler") } },
            title = { Text("Choisir l'application") },
            text = {
                LazyColumn(Modifier.heightIn(max = 380.dp)) {
                    items(apps) { a ->
                        Text("${a.label}  ·  ${a.packageName}", fontSize = 13.sp, modifier = Modifier.fillMaxWidth().clickable {
                            vm.update { it.copy(shortcutOverrides = (it.overrides + (key to a.packageName)).entries.joinToString(";") { e -> "${e.key}=${e.value}" }) }
                            picking = null
                        }.padding(vertical = 10.dp))
                    }
                }
            },
        )
    }
}

@Composable
private fun MultiField(label: String, value: String, onChange: (String) -> Unit) {
    var text by remember(label) { mutableStateOf(value) }
    OutlinedTextField(text, { text = it; onChange(it) }, label = { Text(label) }, minLines = 3, maxLines = 8,
        textStyle = androidx.compose.ui.text.TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = Cockpit.Text),
        modifier = Modifier.fillMaxWidth())
}

@Composable
private fun Info(text: String, color: Color = Cockpit.TextDim) = Text(text, fontSize = 12.sp, color = color)

/** Lance un sélecteur « Enregistrer sous » ; renvoie false si l'autoradio n'en a pas. */
private fun safeLaunch(block: () -> Unit): Boolean =
    try { block(); true } catch (e: android.content.ActivityNotFoundException) { false } catch (e: SecurityException) { false }

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DataTab(vm: CockpitViewModel, s: CockpitSettings) {
    val context = LocalContext.current
    val accent = LocalAccent.current
    val report by vm.reportStatus.collectAsState()
    val reportText by vm.reportText.collectAsState()
    val busy by vm.reportBusy.collectAsState()
    val journal by vm.vehicleRepo.probe.journal.collectAsState()
    val capture by vm.vehicleRepo.probe.captureStatus.collectAsState()
    val canStatus by vm.vehicleRepo.canbox.status.collectAsState()
    val mapErrors by vm.canMappingErrors.collectAsState()

    val saveReport = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        if (uri != null) vm.saveReportTo(uri)
    }
    val saveZip = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null) vm.exportVendorCode(uri)
    }

    Section("Mode démonstration") {
        Toggle("Activer la simulation", s.demoMode,
            "Remplit uniquement les valeurs non disponibles, toujours marquées « SIMULATION ». Une valeur réelle n'est jamais remplacée.") { v -> vm.update { it.copy(demoMode = v) } }
    }

    Section("1. Rapport de compatibilité CanBox / MCU") {
        Info("Analyse passive : applis et services du fabricant, fournisseurs de données, code des applis CAN, journaux, Bluetooth. " +
            "Rien n'est envoyé à la MCU ni au bus CAN.")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Chip(if (busy) "Génération en cours…" else "Générer le rapport", false) { vm.buildReport() }
            if (reportText != null) {
                Chip("Enregistrer…", false) {
                    if (!safeLaunch { saveReport.launch(fr.polo.cockpit.vehicle.ReportExporter.suggestedName()) }) {
                        vm.reportStatus.value = "Pas de sélecteur de fichiers sur cet autoradio : enregistrement dans le dossier de l'appli."
                        vm.saveReportFallback()
                    }
                }
                Chip("Enregistrer dans le dossier de l'appli", false) { vm.saveReportFallback() }
            }
            Chip("Exporter les applis CAN (.zip)", false) {
                if (!safeLaunch { saveZip.launch(fr.polo.cockpit.vehicle.ReportExporter.suggestedName("polo_applis_fabricant", "zip")) })
                    vm.reportStatus.value = "Pas de sélecteur de fichiers sur cet autoradio : export ZIP impossible."
            }
        }
        report?.let { Info(it, accent) }
        reportText?.let { t ->
            androidx.compose.foundation.layout.Box(Modifier.fillMaxWidth().heightIn(max = 240.dp).verticalScroll(rememberScrollState())) {
                Text(t.take(20_000) + if (t.length > 20_000) "\n… (suite dans le fichier enregistré)" else "",
                    fontSize = 10.sp, fontFamily = FontFamily.Monospace, color = Cockpit.TextDim)
            }
        }
    }

    Section("2. Capture guidée des données de la CanBox") {
        Info("Pendant 60 s, l'appli écoute les diffusions candidates trouvées dans le code du fabricant. " +
            "Pendant ce temps : ouvrez et fermez une portière, le coffre, allumez et éteignez les feux, serrez et desserrez le frein à main. " +
            "Ensuite, régénérez le rapport : seules les diffusions réellement reçues y sont listées.")
        Chip("Lancer la capture (60 s)", false) { vm.startCapture() }
        capture?.let { Info(it, accent) }
        Field("Actions supplémentaires à écouter (séparées par des espaces)", s.listenActions.replace("\n", " ")) { v ->
            vm.update { it.copy(listenActions = v.trim().split(Regex("\\s+")).filter { a -> a.isNotEmpty() }.joinToString("\n")) }
        }
        if (journal.isNotEmpty()) Text(journal.take(12).joinToString("\n"), fontSize = 10.sp, fontFamily = FontFamily.Monospace, color = Cockpit.TextDim)
    }

    Section("3. Intégration CanBox (lecture seule)") {
        Info("Associez une diffusion réellement observée à une donnée. Format : ACTION;EXTRA;CHAMP[;FACTEUR ou bit:N]. " +
            "Champs : ${fr.polo.cockpit.vehicle.CanMapping.FIELDS.sorted().joinToString()}.")
        MultiField("Associations (une par ligne)", s.canMappings) { v -> vm.update { it.copy(canMappings = v) } }
        mapErrors.forEach { Info(it, Cockpit.Warn) }
        Info("État : ${canStatus.message}", if (canStatus.connected) Cockpit.Ok else Cockpit.TextDim)
    }

    ObdSection(vm, s)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ObdSection(vm: CockpitViewModel, s: CockpitSettings) {
    val context = LocalContext.current
    val accent = LocalAccent.current
    val diag by vm.btDiag.collectAsState()
    val test by vm.obdTest.collectAsState()
    val testing by vm.obdTesting.collectAsState()
    val obdStatus by vm.vehicleRepo.obd.status.collectAsState()
    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { vm.refreshBluetooth() }
    androidx.compose.runtime.LaunchedEffect(Unit) { vm.refreshBluetooth() }

    Section("4. Adaptateur OBD2 ELM327 (facultatif, lecture seule)") {
        Info("Requêtes de lecture uniquement (mode 01) : vitesse, régime, température moteur, et carburant si le calculateur le fournit.")
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Chip("Bluetooth", s.obdTransport == "bt") { vm.update { it.copy(obdTransport = "bt") } }
            Chip("Wi-Fi", s.obdTransport == "wifi") { vm.update { it.copy(obdTransport = "wifi") } }
        }
        if (s.obdTransport == "bt") {
            val d = diag
            Info("Bluetooth Android : " + when {
                d == null -> "vérification…"
                !d.adapterPresent -> "absent"
                !d.permissionOk -> "autorisation manquante"
                !d.enabled -> "désactivé"
                else -> "actif"
            })
            d?.let { Info(it.message, if (it.devices.isEmpty()) Cockpit.Warn else Cockpit.TextDim) }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Chip("Actualiser", false) { vm.refreshBluetooth() }
                if (d != null && !d.permissionOk && android.os.Build.VERSION.SDK_INT >= 31)
                    Chip("Autoriser le Bluetooth", false) { permLauncher.launch("android.permission.BLUETOOTH_CONNECT") }
                if (d != null && d.adapterPresent && d.permissionOk && !d.enabled)
                    Chip("Activer le Bluetooth", false) {
                        safeLaunch { context.startActivity(android.content.Intent(android.bluetooth.BluetoothAdapter.ACTION_REQUEST_ENABLE)) }
                    }
                Chip("Paramètres Bluetooth (associer)", false) {
                    safeLaunch { context.startActivity(android.content.Intent(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)) }
                }
            }
            d?.devices?.forEach { dev ->
                Chip("${dev.name} · ${dev.address} · ${dev.kind}${if (fr.polo.cockpit.vehicle.ObdLinks.looksLikeObd(dev.name)) " · OBD probable" else ""}",
                    s.obdAddress == dev.address) { vm.update { it.copy(obdAddress = dev.address) } }
            }
        } else {
            Info("Connectez d'abord l'autoradio au réseau Wi-Fi de l'adaptateur (souvent « WiFi_OBDII »).")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Column(Modifier.weight(2f)) { Field("Adresse IP", s.obdWifiHost) { v -> vm.update { it.copy(obdWifiHost = v.trim()) } } }
                Column(Modifier.weight(1f)) { Field("Port", s.obdWifiPort.toString()) { v -> v.toIntOrNull()?.let { p -> vm.update { it.copy(obdWifiPort = p) } } } }
            }
        }

        Chip(if (testing) "Test en cours…" else "Tester la connexion", false) { vm.testObd() }
        test?.let { r ->
            Info(r.summary, when { r.ecuOk -> Cockpit.Ok; r.adapterOk -> Cockpit.Warn; else -> Cockpit.Danger })
            Text(r.steps.joinToString("\n"), fontSize = 10.sp, fontFamily = FontFamily.Monospace, color = Cockpit.TextDim)
        }
        val verified = s.obdConfigVerified
        Toggle("Activer la lecture OBD2", s.obdEnabled && verified,
            if (!verified) "Faites d'abord un test de connexion réussi avec cet adaptateur." else obdStatus.message) { v ->
            if (verified) vm.update { it.copy(obdEnabled = v) } else vm.message.value = "Testez d'abord la connexion."
        }
    }
}

@Composable
private fun SystemTab(vm: CockpitViewModel) {
    val context = LocalContext.current
    var home by remember { mutableStateOf(vm.launcher.isHomeMode()) }
    var crash by remember { mutableStateOf(fr.polo.cockpit.vehicle.CrashLog.read(context)) }
    Section("Écran d'accueil") {
        Toggle("Proposer Polo Cockpit comme écran d'accueil", home,
            "Android demandera quel écran d'accueil utiliser. L'interface d'origine reste disponible (bouton « Origine »).") { v ->
            vm.launcher.setHomeMode(v); home = v
            if (v) vm.launcher.openHomeChooser()
        }
        Chip("Ouvrir l'interface d'origine", false) { vm.launcher.openOriginalLauncher() }
    }
    Section("Diagnostic") {
        Text("Android réel : ${android.os.Build.VERSION.RELEASE} (API ${android.os.Build.VERSION.SDK_INT})", fontSize = 13.sp)
        if (android.os.Build.VERSION.SDK_INT < 31)
            Info("L'autoradio annonce Android 12, mais le système exécute l'API ${android.os.Build.VERSION.SDK_INT}. L'appli s'y adapte.")
        Text("Dernier plantage :", fontSize = 13.sp)
        Text(crash ?: "Aucun.", fontSize = 10.sp, fontFamily = FontFamily.Monospace, color = Cockpit.TextDim, maxLines = 25)
        if (crash != null) Chip("Effacer", false) { fr.polo.cockpit.vehicle.CrashLog.clear(context); crash = null }
    }
    Section("Désinstallation") {
        Text("La désinstallation supprime l'appli et ses réglages ; l'autoradio revient à son interface d'origine.",
            fontSize = 12.sp, color = Cockpit.TextDim)
        Chip("Désinstaller Polo Digital Cockpit", false) { vm.launcher.uninstallSelf() }
    }
    Section("À propos") {
        Text("Polo Digital Cockpit ${BuildConfig.VERSION_NAME}", fontSize = 13.sp)
        Text("Moteur 3D : Filament (Apache 2.0). Météo : Open-Meteo. Aucune modification du firmware, de la MCU ou du CAN.",
            fontSize = 11.sp, color = Cockpit.TextDim)
    }
}

private fun parseHex(hex: String): Long? {
    val h = hex.trim().removePrefix("#")
    if (h.length != 6) return null
    return h.toLongOrNull(16)?.let { 0xFF000000L or it }
}
