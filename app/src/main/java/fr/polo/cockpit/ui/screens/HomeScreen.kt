package fr.polo.cockpit.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.PhoneIphone
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.polo.cockpit.data.ClockStyle
import fr.polo.cockpit.data.CockpitSettings
import fr.polo.cockpit.ui.CockpitViewModel
import fr.polo.cockpit.ui.Screen
import fr.polo.cockpit.ui.components.CarSilhouette
import fr.polo.cockpit.ui.components.Car3DView
import fr.polo.cockpit.ui.components.CockpitButton
import fr.polo.cockpit.ui.components.DataTile
import fr.polo.cockpit.ui.components.GlassCard
import fr.polo.cockpit.ui.components.SimulationBanner
import fr.polo.cockpit.ui.theme.Cockpit
import fr.polo.cockpit.ui.theme.LocalAccent
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HomeScreen(vm: CockpitViewModel, s: CockpitSettings) {
    val accent = LocalAccent.current
    val v by vm.vehicle.collectAsState()
    val weather by vm.weather.collectAsState()
    val hasModel by vm.hasModel.collectAsState()
    val modelKey by vm.modelKey.collectAsState()

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.weight(1f).fillMaxWidth()) {
            // ---- Colonne gauche : heure, date, météo
            Column(Modifier.width(250.dp).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Clock(s)
                if (s.widgetWeather) {
                    GlassCard(Modifier.fillMaxWidth()) {
                        val w = weather
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Cloud, null, tint = accent)
                            Spacer(Modifier.width(10.dp))
                            if (w != null) Column {
                                Text("${w.tempC}°C  ${w.city}", fontSize = 18.sp)
                                Text(w.description, fontSize = 12.sp, color = Cockpit.TextDim)
                            } else Text("Météo indisponible (hors ligne)", fontSize = 12.sp, color = Cockpit.TextDim)
                        }
                    }
                }
                Spacer(Modifier.weight(1f))
                if (v.hasSimulatedData) SimulationBanner()
            }

            // ---- Centre : la Polo
            Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
                Canvas(Modifier.fillMaxSize()) {  // halo au sol
                    drawOval(Brush.radialGradient(listOf(accent.copy(alpha = 0.35f), Color.Transparent),
                        center = Offset(size.width / 2, size.height * 0.78f), radius = size.width * 0.42f),
                        topLeft = Offset(size.width * 0.08f, size.height * 0.70f), size = Size(size.width * 0.84f, size.height * 0.16f))
                }
                if (hasModel) {
                    Car3DView(Modifier.fillMaxSize(), s.bodyColor, modelKey, autoRotate = s.animations)
                } else {
                    CarSilhouette(Modifier.fillMaxWidth().height(220.dp).padding(horizontal = 12.dp),
                        Color(s.bodyColor), accent, v.doors.value, v.lights.value)
                }
                Column(Modifier.align(Alignment.TopCenter).padding(top = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(s.vehicleName, style = MaterialTheme.typography.headlineMedium)
                    Text(s.plate, fontSize = 11.sp, letterSpacing = 3.sp, color = Cockpit.TextDim)
                }
            }

            // ---- Colonne droite : widgets véhicule
            Column(Modifier.width(220.dp).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (s.widgetVehicle) {
                    DataTile("Carburant", v.fuelPercent, "%", Modifier.fillMaxWidth(),
                        alert = (v.fuelPercent.value ?: 100) < 12)
                    DataTile("Autonomie", v.rangeKm, "km", Modifier.fillMaxWidth())
                    DataTile("Moteur", v.coolantC, "°C", Modifier.fillMaxWidth(),
                        alert = (v.coolantC.value ?: 0) > 110)
                }
                if (s.widgetConsumption) {
                    DataTile("Conso. moyenne", v.averageL100, "L/100", Modifier.fillMaxWidth(), format = { "%.1f".format(it) })
                }
            }
        }

        // ---- Raccourcis
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth().height(86.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            val m = Modifier.weight(1f).fillMaxHeight()
            CockpitButton("Navigation", Icons.Filled.Navigation, m) { vm.launch("gps") }
            CockpitButton("Musique", Icons.Filled.MusicNote, m) { vm.launch("music") }
            CockpitButton("Téléphone", Icons.Filled.Phone, m) { vm.launch("phone") }
            CockpitButton("Bluetooth", Icons.Filled.Bluetooth, m) { vm.launch("bluetooth") }
            CockpitButton("CarPlay", Icons.Filled.PhoneIphone, m) { vm.launch("carplay") }
            CockpitButton("Android Auto", Icons.Filled.Smartphone, m) { vm.launch("androidauto") }
            CockpitButton("Véhicule", Icons.Filled.Speed, m) { vm.go(Screen.VEHICLE) }
            CockpitButton("Paramètres", Icons.Filled.Settings, m) { vm.launch("settings") }
        }
    }
}

@Composable
private fun Clock(s: CockpitSettings) {
    val now = remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(s.showSeconds) {
        while (true) { now.longValue = System.currentTimeMillis(); delay(if (s.showSeconds) 1000L else 5000L) }
    }
    val fr = Locale.FRANCE
    val time = SimpleDateFormat(if (s.showSeconds) "HH:mm:ss" else "HH:mm", fr).format(Date(now.longValue))
    val date = SimpleDateFormat("EEEE d MMMM", fr).format(Date(now.longValue)).replaceFirstChar { it.uppercase() }
    when (s.clockStyle) {
        ClockStyle.LARGE -> Column {
            Text(time, style = MaterialTheme.typography.displayLarge.copy(fontSize = if (s.showSeconds) 64.sp else 96.sp))
            Text(date, fontSize = 18.sp, color = Cockpit.TextDim)
        }
        ClockStyle.DIGITAL -> GlassCard(Modifier.fillMaxWidth()) {
            Column {
                Text(time, fontSize = if (s.showSeconds) 46.sp else 60.sp, fontWeight = FontWeight.Thin)
                Text(date, fontSize = 15.sp, color = Cockpit.TextDim)
            }
        }
        ClockStyle.MINIMAL -> Column {
            Text(time, fontSize = 40.sp, fontWeight = FontWeight.Light, color = LocalAccent.current)
            Text(date, fontSize = 13.sp, color = Cockpit.TextDim)
        }
    }
}
