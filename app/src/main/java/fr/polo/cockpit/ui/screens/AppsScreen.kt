package fr.polo.cockpit.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.PhoneIphone
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import fr.polo.cockpit.apps.InstalledApp
import fr.polo.cockpit.data.CockpitSettings
import fr.polo.cockpit.ui.CockpitViewModel
import fr.polo.cockpit.ui.components.CockpitButton
import fr.polo.cockpit.ui.components.GlassCard
import fr.polo.cockpit.ui.theme.Cockpit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun AppsScreen(vm: CockpitViewModel, s: CockpitSettings) {
    val apps by produceState(emptyList<InstalledApp>()) { value = withContext(Dispatchers.IO) { vm.launcher.installedApps() } }
    val projection by produceState(emptyList<InstalledApp>()) { value = withContext(Dispatchers.IO) { vm.launcher.detectProjectionApps() } }

    LazyVerticalGrid(GridCells.Adaptive(104.dp), Modifier.fillMaxSize(),
        horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {

        item(span = { GridItemSpan(maxLineSpan) }) {
            GlassCard(Modifier.fillMaxWidth(), glow = true) {
                Column {
                    Text("PROJECTION SMARTPHONE", style = MaterialTheme.typography.labelSmall)
                    Spacer(Modifier.height(4.dp))
                    if (projection.isEmpty()) {
                        Text("Aucune appli CarPlay / Android Auto détectée (ZLink, TLink, AutoKit…). " +
                            "Choisissez-la dans Réglages › Raccourcis.", fontSize = 13.sp, color = Cockpit.TextDim)
                    } else {
                        Text("Détectée : " + projection.joinToString { "${it.label} (${it.packageName})" }, fontSize = 13.sp)
                        Text("CarPlay et Android Auto restent gérés par cette appli d'origine : le cockpit se contente de l'ouvrir.",
                            fontSize = 11.sp, color = Cockpit.TextDim)
                    }
                }
            }
        }

        val quick = listOf(
            Triple("CarPlay", Icons.Filled.PhoneIphone, "carplay"),
            Triple("Android Auto", Icons.Filled.Smartphone, "androidauto"),
            Triple("Google Maps", Icons.Filled.Map, "maps"),
            Triple("Waze", Icons.Filled.Navigation, "waze"),
            Triple("Spotify", Icons.Filled.MusicNote, "spotify"),
            Triple("YouTube Music", Icons.Filled.PlayCircle, "ytmusic"),
            Triple("Téléphone", Icons.Filled.Phone, "phone"),
        )
        items(quick) { (label, icon, key) ->
            CockpitButton(label, icon, Modifier.height(86.dp)) { vm.launch(key) }
        }

        item(span = { GridItemSpan(maxLineSpan) }) {
            Text("TOUTES LES APPLICATIONS (${apps.size})", style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 8.dp))
        }
        items(apps, key = { it.packageName }) { app -> AppTile(app) { vm.launcher.launchPackage(app.packageName) } }
    }
}

@Composable
fun AppTile(app: InstalledApp, onClick: () -> Unit) {
    val bmp = remember(app.packageName) { app.icon?.let { runCatching { it.toBitmap(96, 96).asImageBitmap() }.getOrNull() } }
    Column(Modifier.clickable(onClick = onClick).padding(6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        if (bmp != null) Image(bmp, null, Modifier.size(52.dp)) else Spacer(Modifier.size(52.dp))
        Spacer(Modifier.height(4.dp))
        Row { Text(app.label, fontSize = 12.sp, maxLines = 1, modifier = Modifier.width(96.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center) }
    }
}
