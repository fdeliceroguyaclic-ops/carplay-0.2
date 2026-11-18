package fr.polo.cockpit.ui.screens

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.polo.cockpit.data.CockpitSettings
import fr.polo.cockpit.ui.CockpitViewModel
import fr.polo.cockpit.ui.components.ArcGauge
import fr.polo.cockpit.ui.components.CarSilhouette
import fr.polo.cockpit.ui.components.DataTile
import fr.polo.cockpit.ui.components.Dot
import fr.polo.cockpit.ui.components.GlassCard
import fr.polo.cockpit.ui.components.OriginBadge
import fr.polo.cockpit.ui.components.SimulationBanner
import fr.polo.cockpit.ui.theme.Cockpit
import fr.polo.cockpit.ui.theme.LocalAccent
import fr.polo.cockpit.vehicle.Reading

@Composable
fun VehicleScreen(vm: CockpitViewModel, s: CockpitSettings) {
    val v by vm.vehicle.collectAsState()
    val obdStatus by vm.vehicleRepo.obd.status.collectAsState()
    val canStatus by vm.vehicleRepo.canbox.status.collectAsState()
    val accent = LocalAccent.current

    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        // Compteurs
        Column(Modifier.width(250.dp).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceEvenly) {
            ArcGauge(v.speedKmh.value?.toFloat(), 200f, "Vitesse", "km/h", v.speedKmh.origin, size = 230.dp)
            ArcGauge(v.rpm.value?.toFloat(), 7000f, "Régime", "tr/min", v.rpm.origin, size = 190.dp, redFrom = 6000f)
        }

        Column(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (v.hasSimulatedData) SimulationBanner()

            GlassCard(Modifier.fillMaxWidth()) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("OUVRANTS & FEUX", style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1f))
                        OriginBadge(v.doors.origin)
                    }
                    CarSilhouette(Modifier.fillMaxWidth().height(150.dp), Color(s.bodyColor), accent, v.doors.value, v.lights.value)
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        StatusItem("Portières", v.doors.value?.let { d ->
                            if (d.frontLeft || d.frontRight || d.rearLeft || d.rearRight) "Ouverte(s)" else "Fermées" }, v.doors)
                        StatusItem("Coffre", v.doors.value?.let { if (it.trunk) "Ouvert" else "Fermé" }, v.doors)
                        StatusItem("Frein à main", v.handbrake.value?.let { if (it) "Serré" else "Desserré" }, v.handbrake)
                        StatusItem("Feux", v.lights.value?.let {
                            when { it.highBeam -> "Route"; it.lowBeam -> "Croisement"; else -> "Éteints" } }, v.lights)
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                val m = Modifier.weight(1f)
                DataTile("Liquide refroid.", v.coolantC, "°C", m, alert = (v.coolantC.value ?: 0) > 110)
                DataTile("Carburant", v.fuelPercent, "%", m, alert = (v.fuelPercent.value ?: 100) < 12)
                DataTile("Autonomie", v.rangeKm, "km", m)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                val m = Modifier.weight(1f)
                DataTile("Conso. instant.", v.instantL100, "L/100", m, format = { "%.1f".format(it) })
                DataTile("Conso. moyenne", v.averageL100, "L/100", m, format = { "%.1f".format(it) })
                DataTile("Batterie", v.batteryV, "V", m, format = { "%.1f".format(it) })
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                val m = Modifier.weight(1f)
                DataTile("Air admission", v.intakeC, "°C", m)
                DataTile("Extérieur", v.outsideC, "°C", m)
                GlassCard(m) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("ALERTES", style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1f))
                            OriginBadge(v.alerts.origin)
                        }
                        Spacer(Modifier.height(6.dp))
                        val alerts = v.alerts.value
                        Text(when { alerts == null -> "—"; alerts.isEmpty() -> "Aucune"; else -> alerts.joinToString("\n") },
                            fontSize = 15.sp, color = if (alerts.isNullOrEmpty()) Cockpit.Text else Cockpit.Warn)
                    }
                }
            }

            GlassCard(Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("SOURCES DE DONNÉES", style = MaterialTheme.typography.labelSmall)
                    SourceLine("CanBox Raise (via MCU)", canStatus.connected, canStatus.message)
                    SourceLine("OBD2 ELM327", obdStatus.connected, obdStatus.message)
                    SourceLine("Mode démonstration", s.demoMode, if (s.demoMode) "Complète les valeurs absentes (étiquette SIMULATION)" else "Désactivé")
                    Text("Les valeurs « Estimé » sont calculées (ex. consommation à partir du débit d'air) et peuvent s'écarter de l'ordinateur de bord.",
                        fontSize = 11.sp, color = Cockpit.TextDim, modifier = Modifier.padding(top = 4.dp))
                }
            }
        }
    }
}

@Composable
private fun StatusItem(label: String, value: String?, reading: Reading<*>) {
    Column {
        Text(label, fontSize = 11.sp, color = Cockpit.TextDim)
        Text(value ?: "—", fontSize = 15.sp, color = if (reading.simulated) Cockpit.Sim else Cockpit.Text)
    }
}

@Composable
private fun SourceLine(name: String, active: Boolean, detail: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Dot(if (active) Cockpit.Ok else Cockpit.TextDim)
        Spacer(Modifier.width(8.dp))
        Box(Modifier.width(200.dp)) { Text(name, fontSize = 13.sp) }
        Text(detail, fontSize = 12.sp, color = Cockpit.TextDim)
    }
}
