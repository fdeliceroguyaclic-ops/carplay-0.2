package fr.polo.cockpit.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.polo.cockpit.ui.theme.Cockpit
import fr.polo.cockpit.ui.theme.LocalAccent
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Animation d'accueil : anneau lumineux qui se dessine, balayage de lumière, puis nom du véhicule.
 * (Le logo officiel Volkswagen est une marque déposée : il n'est pas redessiné ici.
 *  Un logo personnel pourra être ajouté via le fond d'écran.)
 * Touchez l'écran pour passer.
 */
@Composable
fun SplashScreen(vehicleName: String, onDone: () -> Unit) {
    val accent = LocalAccent.current
    val ring = remember { Animatable(0f) }
    val sweep = remember { Animatable(0f) }
    val text = remember { Animatable(0f) }
    val spacing = remember { Animatable(30f) }

    LaunchedEffect(Unit) {
        ring.animateTo(1f, tween(1100, easing = FastOutSlowInEasing))
        launch { sweep.animateTo(1f, tween(900, easing = LinearEasing)) }
        launch { spacing.animateTo(10f, tween(1000, easing = FastOutSlowInEasing)) }
        text.animateTo(1f, tween(700))
        delay(900)
        onDone()
    }

    Box(
        Modifier.fillMaxSize().clickable(remember { MutableInteractionSource() }, null) { onDone() },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(260.dp)) {
            val stroke = 3.dp.toPx()
            drawCircle(accent.copy(alpha = 0.08f * ring.value), size.minDimension / 2)
            drawArc(
                Brush.sweepGradient(listOf(Color.Transparent, accent, Color.White, accent)),
                -90f, 360f * ring.value, false,
                topLeft = Offset(stroke, stroke), size = Size(size.width - 2 * stroke, size.height - 2 * stroke),
                style = Stroke(stroke, cap = StrokeCap.Round),
            )
            // balayage lumineux horizontal
            val x = size.width * (sweep.value * 1.6f - 0.3f)
            drawRect(
                Brush.horizontalGradient(listOf(Color.Transparent, Color.White.copy(alpha = 0.25f), Color.Transparent), x - 60f, x + 60f),
                topLeft = Offset(0f, size.height * 0.45f), size = Size(size.width, size.height * 0.1f),
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.alpha(text.value)) {
            Text(vehicleName, fontSize = 40.sp, fontWeight = FontWeight.Light, letterSpacing = spacing.value.sp, color = Cockpit.Text)
            Spacer(Modifier.height(6.dp))
            Text("DIGITAL COCKPIT", fontSize = 12.sp, letterSpacing = 6.sp, color = accent)
        }
    }
}
