package fr.polo.cockpit.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.polo.cockpit.data.BODY_PRESETS
import fr.polo.cockpit.data.CockpitSettings
import fr.polo.cockpit.render.CarRenderer
import fr.polo.cockpit.render.CarView
import fr.polo.cockpit.ui.CockpitViewModel
import fr.polo.cockpit.ui.components.Car3DView
import fr.polo.cockpit.ui.components.CarSilhouette
import fr.polo.cockpit.ui.components.CockpitButton
import fr.polo.cockpit.ui.components.GlassCard
import fr.polo.cockpit.ui.theme.Cockpit
import fr.polo.cockpit.ui.theme.LocalAccent
import fr.polo.cockpit.vehicle.PoloSpecs

private val DOOR_KEYS = listOf("door", "porte")
private val TRUNK_KEYS = listOf("trunk", "boot", "coffre", "hayon", "tailgate")

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun Car3DScreen(vm: CockpitViewModel, s: CockpitSettings) {
    val accent = LocalAccent.current
    val hasModel by vm.hasModel.collectAsState()
    val modelKey by vm.modelKey.collectAsState()
    val v by vm.vehicle.collectAsState()
    var renderer by remember { mutableStateOf<CarRenderer?>(null) }
    var info by remember { mutableStateOf<CarRenderer.ModelInfo?>(null) }
    var view by remember { mutableStateOf(CarView.THREE_QUARTER) }
    var doors by remember { mutableStateOf(false) }
    var trunk by remember { mutableStateOf(false) }
    var head by remember { mutableStateOf(false) }
    var blinkL by remember { mutableStateOf(false) }
    var blinkR by remember { mutableStateOf(false) }
    var sync by remember { mutableStateOf(false) }
    val animStates = remember { mutableStateOf(setOf<Int>()) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { vm.importModel(it) }
    }

    // Reflète l'état réel (ou simulé, signalé) du véhicule sur le modèle
    LaunchedEffect(sync, v.doors, v.lights, renderer) {
        val r = renderer ?: return@LaunchedEffect
        if (!sync) return@LaunchedEffect
        v.doors.value?.let { d ->
            r.toggleAnimations(listOf("door_fl", "porte_avg"), d.frontLeft)
            r.toggleAnimations(listOf("door_fr", "porte_avd"), d.frontRight)
            r.toggleAnimations(listOf("door_rl", "porte_arg"), d.rearLeft)
            r.toggleAnimations(listOf("door_rr", "porte_ard"), d.rearRight)
            r.toggleAnimations(TRUNK_KEYS, d.trunk)
        }
        v.lights.value?.let { l ->
            r.setHeadlights(l.lowBeam || l.highBeam)
            r.setBlinkers(l.leftIndicator, l.rightIndicator)
        }
    }

    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.weight(1f).fillMaxHeight()) {
            if (hasModel) {
                Car3DView(Modifier.fillMaxSize(), s.bodyColor, modelKey, autoRotate = false,
                    onReady = { renderer = it }, onInfo = { info = it })
            } else {
                Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center) {
                    CarSilhouette(Modifier.fillMaxWidth().height(240.dp), Color(s.bodyColor), accent,
                        lights = v.lights.value, doors = v.doors.value)
                    GlassCard(Modifier.fillMaxWidth().padding(top = 12.dp)) {
                        Column {
                            Text("Aucun modèle 3D importé", fontSize = 16.sp)
                            Text("Importez un fichier GLB de Polo V (6R) 5 portes via le bouton « Importer un modèle ». " +
                                "Aucun modèle de Polo 6R libre de droits n'est fourni avec l'appli : le guide docs/MODELE_3D.md " +
                                "explique où en trouver un (licence compatible) et comment le préparer dans Blender " +
                                "(portières, coffre, phares, clignotants).",
                                fontSize = 13.sp, color = Cockpit.TextDim)
                        }
                    }
                }
            }
            info?.let {
                Text(it.message, fontSize = 11.sp, color = Cockpit.TextDim, modifier = Modifier.align(Alignment.BottomStart))
            }
        }

        Column(Modifier.width(300.dp).fillMaxHeight().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp)) {
            GlassCard(Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("VUE", style = MaterialTheme.typography.labelSmall)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        CarView.entries.forEach { cv ->
                            Chip(cv.label, view == cv) { view = cv; renderer?.setView(cv) }
                        }
                    }
                }
            }
            GlassCard(Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("ANIMATIONS", style = MaterialTheme.typography.labelSmall)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Chip("Portières", doors) {
                            doors = !doors
                            if (renderer?.toggleAnimations(DOOR_KEYS, doors) == false) vm.message.value = "Ce modèle n'a pas d'animation de portières"
                        }
                        Chip("Coffre", trunk) {
                            trunk = !trunk
                            if (renderer?.toggleAnimations(TRUNK_KEYS, trunk) == false) vm.message.value = "Ce modèle n'a pas d'animation de coffre"
                        }
                        Chip("Phares", head) { head = !head; renderer?.setHeadlights(head) }
                        Chip("◀ Clignot.", blinkL) { blinkL = !blinkL; renderer?.setBlinkers(blinkL, blinkR) }
                        Chip("Clignot. ▶", blinkR) { blinkR = !blinkR; renderer?.setBlinkers(blinkL, blinkR) }
                        Chip("Warning", blinkL && blinkR) {
                            val on = !(blinkL && blinkR); blinkL = on; blinkR = on; renderer?.setBlinkers(on, on)
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Refléter l'état du véhicule", fontSize = 13.sp, modifier = Modifier.weight(1f))
                        Switch(sync, { sync = it }, colors = SwitchDefaults.colors(checkedTrackColor = accent))
                    }
                    // Animations supplémentaires trouvées dans le modèle
                    info?.animations?.takeIf { it.isNotEmpty() }?.let { names ->
                        Text("Dans le modèle :", fontSize = 11.sp, color = Cockpit.TextDim)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            names.forEachIndexed { i, n ->
                                val on = i in animStates.value
                                Chip(n, on) {
                                    animStates.value = if (on) animStates.value - i else animStates.value + i
                                    renderer?.toggleAnimation(i, !on)
                                }
                            }
                        }
                    }
                }
            }
            GlassCard(Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("COULEUR DE CARROSSERIE", style = MaterialTheme.typography.labelSmall)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        BODY_PRESETS.forEach { (name, c) ->
                            Swatch(Color(c), s.bodyColor == c, name) { vm.update { it.copy(bodyColor = c) } }
                        }
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CockpitButton("Importer un modèle", Icons.Filled.FileUpload, Modifier.weight(1f), compact = true) {
                    picker.launch(arrayOf("*/*"))
                }
                if (hasModel) CockpitButton("Supprimer", Icons.Filled.Delete, Modifier.weight(0.6f), compact = true) { vm.deleteModel() }
            }
            GlassCard(Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text("CARACTÉRISTIQUES", style = MaterialTheme.typography.labelSmall)
                    PoloSpecs.rows.forEach { (k, value) ->
                        Row { Text(k, fontSize = 12.sp, color = Cockpit.TextDim, modifier = Modifier.width(110.dp)); Text(value, fontSize = 12.sp) }
                    }
                    Row { Text("Immatriculation", fontSize = 12.sp, color = Cockpit.TextDim, modifier = Modifier.width(110.dp)); Text(s.plate, fontSize = 12.sp) }
                }
            }
        }
    }
}

@Composable
fun Chip(label: String, selected: Boolean, onClick: () -> Unit) {
    val accent = LocalAccent.current
    Box(
        Modifier.clip(RoundedCornerShape(10.dp))
            .background(if (selected) accent.copy(alpha = 0.25f) else Cockpit.PanelHi)
            .border(1.dp, if (selected) accent else Cockpit.Stroke, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) { Text(label, fontSize = 13.sp, color = if (selected) Color.White else Cockpit.Text) }
}

@Composable
fun Swatch(color: Color, selected: Boolean, name: String, onClick: () -> Unit) {
    val accent = LocalAccent.current
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(60.dp).clickable(onClick = onClick)) {
        Box(Modifier.size(34.dp).clip(CircleShape).background(color)
            .border(if (selected) 3.dp else 1.dp, if (selected) accent else Cockpit.Stroke, CircleShape))
        Spacer(Modifier.height(2.dp))
        Text(name, fontSize = 9.sp, color = Cockpit.TextDim, maxLines = 1)
    }
}
