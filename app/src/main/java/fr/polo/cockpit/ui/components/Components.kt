package fr.polo.cockpit.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.polo.cockpit.ui.theme.Cockpit
import fr.polo.cockpit.ui.theme.LocalAccent
import fr.polo.cockpit.vehicle.DataOrigin
import fr.polo.cockpit.vehicle.Reading

/** Panneau « verre fumé » avec liseré discret. */
@Composable
fun GlassCard(modifier: Modifier = Modifier, glow: Boolean = false, content: @Composable () -> Unit) {
    val accent = LocalAccent.current
    Box(
        modifier
            .clip(RoundedCornerShape(18.dp))
            .background(Brush.verticalGradient(listOf(Cockpit.PanelHi.copy(alpha = 0.92f), Cockpit.Panel.copy(alpha = 0.92f))))
            .border(1.dp, if (glow) accent.copy(alpha = 0.45f) else Cockpit.Stroke, RoundedCornerShape(18.dp))
            .padding(14.dp)
    ) { content() }
}

/** Bouton tactile avec halo lumineux à l'appui. */
@Composable
fun CockpitButton(
    label: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    compact: Boolean = false,
    onClick: () -> Unit,
) {
    val accent = LocalAccent.current
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.95f else 1f, tween(120), label = "scale")
    val border by animateColorAsState(
        if (pressed || selected) accent else Cockpit.Stroke, tween(180), label = "border")
    Column(
        modifier
            .scale(scale)
            .clip(RoundedCornerShape(16.dp))
            .background(
                if (pressed || selected) Brush.verticalGradient(listOf(accent.copy(alpha = 0.28f), Cockpit.Panel))
                else Brush.verticalGradient(listOf(Cockpit.PanelHi, Cockpit.Panel))
            )
            .border(1.dp, border, RoundedCornerShape(16.dp))
            .clickable(interactionSource = source, indication = null, onClick = onClick)
            .padding(vertical = if (compact) 8.dp else 12.dp, horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(icon, null, tint = if (selected) accent else Cockpit.Text, modifier = Modifier.size(if (compact) 22.dp else 28.dp))
        Spacer(Modifier.height(4.dp))
        Text(label, fontSize = if (compact) 11.sp else 13.sp, color = Cockpit.Text, textAlign = TextAlign.Center, maxLines = 1)
    }
}

/** Étiquette d'origine de la donnée : SIMULATION / OBD2 / Estimé / Indisponible. */
@Composable
fun OriginBadge(origin: DataOrigin) {
    val color = when (origin) {
        DataOrigin.DEMO -> Cockpit.Sim
        DataOrigin.CANBOX, DataOrigin.OBD -> Cockpit.Ok
        DataOrigin.ESTIMATED -> Color(0xFF7FB2FF)
        DataOrigin.UNAVAILABLE -> Cockpit.TextDim
    }
    Box(
        Modifier.clip(RoundedCornerShape(6.dp)).background(color.copy(alpha = 0.15f))
            .border(1.dp, color.copy(alpha = 0.6f), RoundedCornerShape(6.dp))
            .padding(horizontal = 6.dp, vertical = 1.dp)
    ) { Text(origin.label, fontSize = 9.sp, color = color, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp) }
}

/** Tuile valeur + unité + origine. */
@Composable
fun <T> DataTile(
    title: String,
    reading: Reading<T>,
    unit: String,
    modifier: Modifier = Modifier,
    format: (T) -> String = { it.toString() },
    alert: Boolean = false,
) {
    GlassCard(modifier, glow = alert) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title.uppercase(), style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1f))
                OriginBadge(reading.origin)
            }
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                val v = reading.value
                Text(if (v != null && reading.available) format(v) else "—", fontSize = 28.sp, fontWeight = FontWeight.Light,
                    color = if (alert) Cockpit.Warn else Cockpit.Text)
                Spacer(Modifier.size(4.dp))
                Text(unit, fontSize = 13.sp, color = Cockpit.TextDim, modifier = Modifier.padding(bottom = 5.dp))
            }
        }
    }
}

/** Compteur circulaire (vitesse / régime). */
@Composable
fun ArcGauge(
    value: Float?,
    max: Float,
    label: String,
    unit: String,
    origin: DataOrigin,
    modifier: Modifier = Modifier,
    size: Dp = 220.dp,
    redFrom: Float? = null,
) {
    val accent = LocalAccent.current
    val animated by animateFloatAsState((value ?: 0f).coerceIn(0f, max), tween(250), label = "gauge")
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize().padding(8.dp)) {
            val stroke = 12.dp.toPx()
            val start = 135f; val sweep = 270f
            val arcSize = Size(this.size.width - stroke, this.size.height - stroke)
            val tl = Offset(stroke / 2, stroke / 2)
            drawArc(Cockpit.Stroke, start, sweep, false, tl, arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
            redFrom?.let {
                val s = start + sweep * (it / max)
                drawArc(Cockpit.Danger.copy(alpha = 0.35f), s, start + sweep - s, false, tl, arcSize, style = Stroke(stroke, cap = StrokeCap.Butt))
            }
            if (value != null) {
                drawArc(
                    Brush.sweepGradient(listOf(accent.copy(alpha = 0.3f), accent, accent)),
                    start, sweep * (animated / max), false, tl, arcSize, style = Stroke(stroke, cap = StrokeCap.Round),
                )
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(if (value != null) animated.toInt().toString() else "—", fontSize = 52.sp, fontWeight = FontWeight.Thin)
            Text(unit, fontSize = 13.sp, color = Cockpit.TextDim)
            Spacer(Modifier.height(6.dp))
            Text(label.uppercase(), style = MaterialTheme.typography.labelSmall)
            Spacer(Modifier.height(4.dp))
            OriginBadge(origin)
        }
    }
}

@Composable
fun Dot(color: Color, size: Dp = 8.dp) = Box(Modifier.size(size).clip(CircleShape).background(color))

/** Bandeau signalant les données simulées. */
@Composable
fun SimulationBanner(modifier: Modifier = Modifier) {
    Row(
        modifier.clip(RoundedCornerShape(10.dp)).background(Cockpit.Sim.copy(alpha = 0.12f))
            .border(1.dp, Cockpit.Sim.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Dot(Cockpit.Sim)
        Spacer(Modifier.size(8.dp))
        Text("MODE DÉMONSTRATION — certaines valeurs sont simulées", fontSize = 12.sp, color = Cockpit.Sim, fontWeight = FontWeight.Medium)
    }
}
