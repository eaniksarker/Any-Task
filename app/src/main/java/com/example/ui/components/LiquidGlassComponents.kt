package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.domain.model.TaskPriority
import com.example.ui.theme.LocalLiquidGlassPalette
import com.example.ui.theme.accentColor
import com.example.ui.theme.glassTintColor

/**
 * Full-bleed atmospheric Liquid Glass ambient mesh backdrop with softly drifting luminous orbs.
 */
@Composable
fun LiquidGlassBackground(
    modifier: Modifier = Modifier,
    isDark: Boolean = LocalLiquidGlassPalette.current.isDark,
    content: @Composable BoxScope.() -> Unit
) {
    val palette = LocalLiquidGlassPalette.current
    val infiniteTransition = rememberInfiniteTransition(label = "liquid_mesh")
    val drift by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 12000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "mesh_drift"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(palette.canvasBase, palette.canvasSecondary, palette.canvasBase)
                )
            )
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height

            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(palette.orbPrimary, Color.Transparent),
                    center = Offset(w * (0.18f + 0.08f * drift), h * (0.12f + 0.05f * drift)),
                    radius = w * 0.68f
                ),
                center = Offset(w * (0.18f + 0.08f * drift), h * (0.12f + 0.05f * drift)),
                radius = w * 0.68f
            )

            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(palette.orbSecondary, Color.Transparent),
                    center = Offset(w * (0.86f - 0.1f * drift), h * (0.42f + 0.06f * drift)),
                    radius = w * 0.62f
                ),
                center = Offset(w * (0.86f - 0.1f * drift), h * (0.42f + 0.06f * drift)),
                radius = w * 0.62f
            )

            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(palette.orbTertiary, Color.Transparent),
                    center = Offset(w * (0.28f + 0.07f * drift), h * (0.86f - 0.05f * drift)),
                    radius = w * 0.58f
                ),
                center = Offset(w * (0.28f + 0.07f * drift), h * (0.86f - 0.05f * drift)),
                radius = w * 0.58f
            )
        }
        CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onBackground) {
            content()
        }
    }
}

/**
 * Translucent frosted-glass surface with specular rim border highlight, soft depth shadow,
 * and proper [LocalContentColor] contrast for Light & Dark Glass.
 */
@Composable
fun LiquidGlassSurface(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(24.dp),
    elevated: Boolean = false,
    tintColor: Color? = null,
    borderHighlightColor: Color? = null,
    elevation: Dp = if (elevated) 14.dp else 6.dp,
    content: @Composable BoxScope.() -> Unit
) {
    val palette = LocalLiquidGlassPalette.current
    val topColor = tintColor ?: if (elevated) palette.glassSurfaceElevated else palette.glassSurfaceTop
    val bottomColor = tintColor?.copy(alpha = (tintColor.alpha * 0.6f)) ?: palette.glassSurfaceBottom
    val rimStart = borderHighlightColor ?: palette.rimStart
    val rimEnd = borderHighlightColor?.copy(alpha = 0.28f) ?: palette.rimEnd

    Box(
        modifier = modifier
            .shadow(
                elevation = elevation,
                shape = shape,
                ambientColor = if (palette.isDark) Color(0x66000000) else Color(0x280F172A),
                spotColor = if (palette.isDark) Color(0x66000000) else Color(0x280F172A)
            )
            .clip(shape)
            .background(
                brush = Brush.linearGradient(
                    colors = listOf(topColor, bottomColor)
                )
            )
            .border(
                width = 1.dp,
                brush = Brush.linearGradient(
                    colors = listOf(rimStart, rimEnd, rimStart.copy(alpha = 0.35f))
                ),
                shape = shape
            )
    ) {
        CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
            content()
        }
    }
}

/**
 * Official 4-color Google "G" emblem rendered cleanly in Canvas for crisp scaling.
 */
@Composable
fun GoogleGLogo(
    modifier: Modifier = Modifier,
    size: Dp = 20.dp
) {
    Canvas(modifier = modifier.size(size)) {
        val stroke = this.size.minDimension * 0.21f
        val inset = stroke / 2f
        val arcSize = Size(this.size.width - stroke, this.size.height - stroke)

        // Blue top-right & horizontal bar
        drawArc(
            color = Color(0xFF4285F4),
            startAngle = -10f,
            sweepAngle = 100f,
            useCenter = false,
            topLeft = Offset(inset, inset),
            size = arcSize,
            style = Stroke(width = stroke, cap = StrokeCap.Butt)
        )
        // Green bottom
        drawArc(
            color = Color(0xFF34A853),
            startAngle = 90f,
            sweepAngle = 90f,
            useCenter = false,
            topLeft = Offset(inset, inset),
            size = arcSize,
            style = Stroke(width = stroke, cap = StrokeCap.Butt)
        )
        // Yellow left
        drawArc(
            color = Color(0xFFFBBC05),
            startAngle = 180f,
            sweepAngle = 55f,
            useCenter = false,
            topLeft = Offset(inset, inset),
            size = arcSize,
            style = Stroke(width = stroke, cap = StrokeCap.Butt)
        )
        // Red top-left
        drawArc(
            color = Color(0xFFEA4335),
            startAngle = 235f,
            sweepAngle = 90f,
            useCenter = false,
            topLeft = Offset(inset, inset),
            size = arcSize,
            style = Stroke(width = stroke, cap = StrokeCap.Butt)
        )
        // Horizontal G crossbar
        val cy = this.size.height / 2f
        drawLine(
            color = Color(0xFF4285F4),
            start = Offset(this.size.width * 0.50f, cy),
            end = Offset(this.size.width * 0.92f, cy),
            strokeWidth = stroke,
            cap = StrokeCap.Round
        )
    }
}

/**
 * Google Cloud Sync hero emblem combining the 4-color Google "G" mark with a frosted Sync badge.
 */
@Composable
fun GoogleSyncLogo(
    modifier: Modifier = Modifier,
    size: Dp = 56.dp
) {
    Box(modifier = modifier.size(size)) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            shape = CircleShape,
            color = Color.White,
            shadowElevation = 6.dp
        ) {
            Box(contentAlignment = Alignment.Center) {
                GoogleGLogo(size = size * 0.54f)
            }
        }
        Surface(
            modifier = Modifier
                .size(size * 0.38f)
                .align(Alignment.BottomEnd),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primary,
            border = BorderStroke(2.dp, Color.White),
            shadowElevation = 3.dp
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Rounded.Sync,
                    contentDescription = "Google Sync",
                    tint = Color.White,
                    modifier = Modifier.size(size * 0.22f)
                )
            }
        }
    }
}

@Composable
fun PriorityBadge(
    priority: TaskPriority,
    modifier: Modifier = Modifier
) {
    val palette = LocalLiquidGlassPalette.current
    val accent = priority.accentColor()
    val tint = priority.glassTintColor(palette.isDark)

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(50),
        color = tint,
        border = BorderStroke(1.dp, accent.copy(alpha = 0.65f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(accent)
            )
            Text(
                text = priority.label,
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                color = if (palette.isDark) accent.copy(alpha = 0.95f) else accent,
                maxLines = 1,
                softWrap = false
            )
        }
    }
}

@Composable
fun LiquidGlassCheckCircle(
    checked: Boolean,
    priority: TaskPriority,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    val accent = priority.accentColor()
    val scale by animateFloatAsState(
        targetValue = if (checked) 1.08f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "check_scale"
    )
    val fill by animateColorAsState(
        targetValue = if (checked) accent else accent.copy(alpha = 0.10f),
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "check_fill"
    )

    Box(
        modifier = modifier
            .size(48.dp)
            .clickable(role = Role.Checkbox, onClick = onToggle),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .scale(scale)
                .clip(CircleShape)
                .background(fill)
                .border(width = 2.dp, color = accent, shape = CircleShape),
            contentAlignment = Alignment.Center
        ) {
            if (checked) {
                Icon(
                    imageVector = Icons.Rounded.Check,
                    contentDescription = "Completed",
                    tint = Color.White,
                    modifier = Modifier.size(15.dp)
                )
            }
        }
    }
}
