package fr.polo.cockpit.ui

import android.graphics.BitmapFactory
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import fr.polo.cockpit.ui.components.CockpitButton
import fr.polo.cockpit.ui.screens.AppsScreen
import fr.polo.cockpit.ui.screens.Car3DScreen
import fr.polo.cockpit.ui.screens.HomeScreen
import fr.polo.cockpit.ui.screens.SettingsScreen
import fr.polo.cockpit.ui.screens.SplashScreen
import fr.polo.cockpit.ui.screens.VehicleScreen
import fr.polo.cockpit.ui.theme.Cockpit
import fr.polo.cockpit.ui.theme.CockpitTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun CockpitRoot(vm: CockpitViewModel) {
    val settings by vm.settings.collectAsState()
    val s = settings ?: return  // réglages en cours de chargement (quelques ms)
    val accent = Color(s.accent)
    var splashDone by rememberSaveable { mutableStateOf(!s.splash) }
    val context = LocalContext.current
    val message by vm.message.collectAsState()
    LaunchedEffect(message) {
        message?.let { Toast.makeText(context, it, Toast.LENGTH_SHORT).show(); vm.message.value = null }
    }

    CockpitTheme(accent = accent, animations = s.animations) {
        Box(Modifier.fillMaxSize().background(Cockpit.Bg)) {
            Wallpaper(s.wallpaperUri, accent)

            if (!splashDone) {
                SplashScreen(vehicleName = s.vehicleName, onDone = { splashDone = true })
            } else {
                val screen by vm.screen.collectAsState()
                Row(Modifier.fillMaxSize()) {
                    NavRail(screen, onSelect = vm::go, onOriginal = { vm.launcher.openOriginalLauncher() })
                    AnimatedContent(
                        targetState = screen,
                        transitionSpec = {
                            if (s.animations) (fadeIn(tween(260)) + scaleIn(tween(260), initialScale = 0.98f)) togetherWith fadeOut(tween(160))
                            else fadeIn(tween(0)) togetherWith fadeOut(tween(0))
                        },
                        modifier = Modifier.fillMaxSize(),
                        label = "screens",
                    ) { target ->
                        Box(Modifier.fillMaxSize().padding(start = 4.dp, top = 10.dp, end = 12.dp, bottom = 10.dp)) {
                            when (target) {
                                Screen.HOME -> HomeScreen(vm, s)
                                Screen.VEHICLE -> VehicleScreen(vm, s)
                                Screen.CAR3D -> Car3DScreen(vm, s)
                                Screen.APPS -> AppsScreen(vm, s)
                                Screen.SETTINGS -> SettingsScreen(vm, s)
                            }
                        }
                    }
                }
            }
            // Atténuation de la luminosité de l'interface (nuit)
            if (s.dim > 0f) Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = s.dim)))
        }
    }
}

@Composable
private fun NavRail(current: Screen, onSelect: (Screen) -> Unit, onOriginal: () -> Unit) {
    val icons = mapOf(
        Screen.HOME to Icons.Filled.Home, Screen.VEHICLE to Icons.Filled.Speed, Screen.CAR3D to Icons.Filled.DirectionsCar,
        Screen.APPS to Icons.Filled.Apps, Screen.SETTINGS to Icons.Filled.Settings,
    )
    Column(
        Modifier.fillMaxHeight().width(92.dp).padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Screen.entries.forEach { sc ->
            CockpitButton(sc.label, icons.getValue(sc), Modifier.fillMaxWidth().weight(1f),
                selected = sc == current, compact = true) { onSelect(sc) }
        }
        Spacer(Modifier.height(4.dp))
        CockpitButton("Origine", Icons.Filled.SwapHoriz, Modifier.fillMaxWidth().weight(0.8f), compact = true, onClick = onOriginal)
    }
}

@Composable
private fun Wallpaper(uri: String?, accent: Color) {
    val context = LocalContext.current
    val bmp by produceState<ImageBitmap?>(null, uri) {
        value = if (uri == null) null else withContext(Dispatchers.IO) {
            runCatching {
                val u = Uri.parse(uri)
                val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                context.contentResolver.openInputStream(u)?.use { BitmapFactory.decodeStream(it, null, opts) }
                var sample = 1
                while (opts.outWidth / (sample * 2) >= 1024) sample *= 2   // limite la RAM
                context.contentResolver.openInputStream(u)?.use {
                    BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
                }?.asImageBitmap()
            }.getOrNull()
        }
    }
    val img = bmp
    if (img != null) {
        Image(img, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop, alpha = 0.55f)
    } else {
        // Fond par défaut : dégradé sombre + halo de la couleur d'accent
        Box(Modifier.fillMaxSize().background(
            Brush.radialGradient(listOf(accent.copy(alpha = 0.16f), Color.Transparent), radius = 900f)))
    }
    Box(Modifier.fillMaxSize().background(
        Brush.verticalGradient(listOf(Color.Transparent, Cockpit.Bg.copy(alpha = 0.85f)))))
}
