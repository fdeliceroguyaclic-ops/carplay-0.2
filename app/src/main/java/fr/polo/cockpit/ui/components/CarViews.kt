package fr.polo.cockpit.ui.components

import android.view.TextureView
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.polo.cockpit.ui.theme.LocalAccent
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import fr.polo.cockpit.render.CarRenderer
import fr.polo.cockpit.ui.theme.Cockpit
import fr.polo.cockpit.vehicle.DoorState
import fr.polo.cockpit.vehicle.LightState

/**
 * Vue 3D (Filament) intégrée dans Compose.
 * [modelKey] : changer la valeur force le rechargement (après import d'un nouveau modèle).
 */
@Composable
fun Car3DView(
    modifier: Modifier,
    bodyColor: Long,
    modelKey: Int,
    autoRotate: Boolean,
    onReady: (CarRenderer) -> Unit = {},
    onInfo: (CarRenderer.ModelInfo) -> Unit = {},
) {
    val context = LocalContext.current
    key(modelKey) {  // nouveau modèle => nouvelle vue + nouveau moteur
    // Si le moteur 3D ne peut pas démarrer (OpenGL ES 3 absent, bibliothèque native refusée…),
    // on affiche la silhouette 2D au lieu de planter.
    val created = remember { runCatching { CarRenderer(context) } }
    var failure by remember { mutableStateOf(created.exceptionOrNull()?.let { "${it.javaClass.simpleName}: ${it.message}" }) }
    val renderer = created.getOrNull()
    if (renderer == null || failure != null) {
        Box(modifier, contentAlignment = Alignment.Center) {
            CarSilhouette(Modifier.fillMaxWidth().height(200.dp), Color(bodyColor), LocalAccent.current)
            Text("Vue 3D indisponible sur cet appareil (${failure ?: "erreur inconnue"})", fontSize = 10.sp,
                color = Cockpit.TextDim, modifier = Modifier.align(Alignment.BottomCenter))
        }
    } else {
    DisposableEffect(renderer) { onDispose { runCatching { renderer.destroy() } } }
    LaunchedEffect(bodyColor, renderer) { runCatching { renderer.setBodyColor(bodyColor) } }
    LaunchedEffect(autoRotate, renderer) { renderer.autoRotate = autoRotate }
    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            TextureView(ctx).also { tv ->
                tv.isOpaque = false
                try {
                    renderer.onModelInfo = onInfo
                    renderer.attach(tv)
                    renderer.loadModel()
                    renderer.setBodyColor(bodyColor)
                    renderer.autoRotate = autoRotate
                    onReady(renderer)
                } catch (e: Throwable) {
                    failure = "${e.javaClass.simpleName}: ${e.message}"
                }
            }
        },
    )
    }
    }
}

/**
 * Silhouette 2D (dessin original, profil compacte 5 portes) utilisée tant qu'aucun modèle 3D
 * n'est importé, et pour l'état des ouvrants sur l'écran Véhicule.
 */
@Composable
fun CarSilhouette(
    modifier: Modifier,
    bodyColor: Color,
    accent: Color,
    doors: DoorState? = null,
    lights: LightState? = null,
) {
    val blink by rememberInfiniteTransition(label = "blink").animateFloat(
        0f, 1f, infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "b")
    Canvas(modifier) {
        val w = size.width; val h = size.height
        val u = minOf(w / 10f, h / 4.2f)          // unité de dessin
        val ox = (w - 10f * u) / 2f; val oy = (h - 3.4f * u) / 2f
        fun p(x: Float, y: Float) = Offset(ox + x * u, oy + y * u)

        // ombre au sol
        drawOval(Brush.radialGradient(listOf(Color.Black.copy(alpha = 0.6f), Color.Transparent),
            center = p(5f, 3.15f), radius = 5f * u), topLeft = p(0.2f, 2.9f), size = Size(9.6f * u, 0.55f * u))

        val body = Path().apply {
            moveTo(p(0.35f, 2.55f).x, p(0.35f, 2.55f).y)
            lineTo(p(0.25f, 1.95f).x, p(0.25f, 1.95f).y)
            quadraticBezierTo(p(0.35f, 1.55f).x, p(0.35f, 1.55f).y, p(1.2f, 1.42f).x, p(1.2f, 1.42f).y)   // capot
            lineTo(p(3.0f, 1.25f).x, p(3.0f, 1.25f).y)
            quadraticBezierTo(p(3.9f, 0.45f).x, p(3.9f, 0.45f).y, p(4.8f, 0.38f).x, p(4.8f, 0.38f).y)     // pare-brise
            lineTo(p(7.6f, 0.38f).x, p(7.6f, 0.38f).y)                                                     // pavillon
            quadraticBezierTo(p(8.95f, 0.45f).x, p(8.95f, 0.45f).y, p(9.45f, 1.3f).x, p(9.45f, 1.3f).y)  // hayon
            lineTo(p(9.65f, 2.0f).x, p(9.65f, 2.0f).y)
            lineTo(p(9.55f, 2.55f).x, p(9.55f, 2.55f).y)
            close()
        }
        drawPath(body, Brush.verticalGradient(listOf(bodyColor, bodyColor.copy(alpha = 0.75f)), p(0f, 0.4f).y, p(0f, 2.6f).y))
        drawPath(body, accent.copy(alpha = 0.6f), style = Stroke(1.5f))

        // vitres
        val glass = Color(0xFF0B1626)
        val win = Path().apply {
            moveTo(p(3.35f, 1.28f).x, p(3.35f, 1.28f).y)
            quadraticBezierTo(p(4.0f, 0.62f).x, p(4.0f, 0.62f).y, p(4.85f, 0.56f).x, p(4.85f, 0.56f).y)
            lineTo(p(8.0f, 0.56f).x, p(8.0f, 0.56f).y)
            quadraticBezierTo(p(8.75f, 0.62f).x, p(8.75f, 0.62f).y, p(9.0f, 1.25f).x, p(9.0f, 1.25f).y)
            close()
        }
        drawPath(win, glass)
        drawLine(bodyColor.copy(alpha = 0.9f), p(5.75f, 0.56f), p(5.75f, 1.28f), strokeWidth = 0.12f * u)  // montant B
        drawLine(bodyColor.copy(alpha = 0.9f), p(7.55f, 0.56f), p(7.65f, 1.27f), strokeWidth = 0.10f * u)  // montant C

        // portes (avant / arrière) — surlignées si ouvertes
        fun door(x1: Float, x2: Float, open: Boolean) = drawRoundRect(
            if (open) Cockpit.Warn.copy(alpha = 0.35f + 0.3f * blink) else Color.Transparent,
            topLeft = p(x1, 1.3f), size = Size((x2 - x1) * u, 1.15f * u), cornerRadius = CornerRadius(0.12f * u),
        ).also {
            drawRoundRect(Color.Black.copy(alpha = 0.35f), p(x1, 1.3f), Size((x2 - x1) * u, 1.15f * u), CornerRadius(0.12f * u), style = Stroke(1.2f))
        }
        door(3.3f, 5.75f, doors?.frontLeft == true || doors?.frontRight == true)
        door(5.8f, 7.75f, doors?.rearLeft == true || doors?.rearRight == true)
        if (doors?.trunk == true) {
            drawLine(Cockpit.Warn.copy(alpha = 0.5f + 0.5f * blink), p(9.0f, 0.5f), p(9.6f, 1.9f), strokeWidth = 0.18f * u)
        }

        // roues
        for (cx in listOf(2.05f, 7.95f)) wheel(p(cx, 2.55f), 0.62f * u)

        // feux
        val headOn = lights?.lowBeam == true || lights?.highBeam == true
        drawRoundRect(if (headOn) Color(0xFFFFF4D6) else Color(0xFF9AA4B0), p(0.3f, 1.62f), Size(0.55f * u, 0.22f * u), CornerRadius(0.08f * u))
        if (headOn) drawPath(Path().apply {
            moveTo(p(0.3f, 1.66f).x, p(0.3f, 1.66f).y)
            lineTo(p(-1.6f, 1.2f).x, p(-1.6f, 1.2f).y); lineTo(p(-1.6f, 2.6f).x, p(-1.6f, 2.6f).y)
            lineTo(p(0.3f, 1.82f).x, p(0.3f, 1.82f).y); close()
        }, Brush.horizontalGradient(listOf(Color.Transparent, Color(0x55FFF4D6)), p(-1.6f, 0f).x, p(0.3f, 0f).x))
        drawRoundRect(Color(0xFFB0121B), p(9.35f, 1.35f), Size(0.28f * u, 0.32f * u), CornerRadius(0.06f * u))
        if (lights?.leftIndicator == true || lights?.rightIndicator == true) {
            val a = blink
            drawCircle(Color(0xFFFF9A1F).copy(alpha = a), 0.12f * u, p(1.05f, 1.75f))
            drawCircle(Color(0xFFFF9A1F).copy(alpha = a * 0.4f), 0.35f * u, p(1.05f, 1.75f))
        }
    }
}

private fun DrawScope.wheel(c: Offset, r: Float) {
    drawCircle(Color(0xFF07090C), r * 1.08f, c)
    drawCircle(Color(0xFF1B2026), r, c)
    drawCircle(Color(0xFF8A939E), r * 0.62f, c)
    drawCircle(Color(0xFF2A3038), r * 0.2f, c)
    for (i in 0 until 5) {
        val a = Math.toRadians(i * 72.0)
        drawLine(Color(0xFF2A3038), c, Offset(c.x + (r * 0.6f * Math.cos(a)).toFloat(), c.y + (r * 0.6f * Math.sin(a)).toFloat()), strokeWidth = r * 0.12f)
    }
}
