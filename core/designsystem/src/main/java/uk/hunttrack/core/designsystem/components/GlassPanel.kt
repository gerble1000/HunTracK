package uk.hunttrack.core.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import uk.hunttrack.core.designsystem.theme.HuntColors

@Composable
fun GlassPanel(
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(if (compact) 12.dp else 20.dp)
    Column(
        modifier = modifier
            .shadow(if (compact) 6.dp else 12.dp, shape, spotColor = HuntColors.ForestDeep, ambientColor = HuntColors.Charcoal)
            .clip(shape)
            .background(
                Brush.verticalGradient(
                    listOf(
                        HuntColors.Glass.copy(alpha = 0.92f),
                        HuntColors.ForestMid.copy(alpha = 0.88f),
                    ),
                ),
            )
            .border(1.dp, HuntColors.GlassBorder, shape)
            .padding(if (compact) 8.dp else 16.dp),
        verticalArrangement = if (compact) Arrangement.spacedBy(2.dp) else Arrangement.Top,
        content = content,
    )
}

/**
 * Glass card that looks tappable — bronze border, chevron, optional hint line.
 */
@Composable
fun ClickableGlassCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    selected: Boolean = false,
    hint: String? = "Tap for more ›",
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(20.dp)
    val borderColor = when {
        selected -> HuntColors.BronzeBright
        enabled -> HuntColors.Bronze.copy(alpha = 0.65f)
        else -> HuntColors.GlassBorder.copy(alpha = 0.4f)
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .shadow(10.dp, shape, spotColor = HuntColors.ForestDeep, ambientColor = HuntColors.Charcoal)
            .clip(shape)
            .background(
                Brush.verticalGradient(
                    listOf(
                        if (selected) HuntColors.ForestGreen.copy(alpha = 0.95f) else HuntColors.ForestDeep,
                        HuntColors.ForestMid,
                    ),
                ),
            )
            .border(1.5.dp, borderColor, shape)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Column(Modifier.weight(1f)) {
            content()
            if (hint != null) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = hint,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (enabled) HuntColors.Gold else HuntColors.Mist,
                )
            }
        }
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = if (enabled) HuntColors.BronzeBright else HuntColors.Mist,
        )
    }
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = HuntColors.Bronze,
        modifier = modifier.padding(bottom = 8.dp),
    )
}

@Composable
fun MetricRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = HuntColors.Mist)
        Text(value, style = MaterialTheme.typography.titleMedium, color = HuntColors.Cream)
    }
}

@Composable
fun ScreenBackground(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(
        modifier = modifier.background(
            Brush.verticalGradient(
                colors = listOf(
                    HuntColors.ForestDeep,
                    HuntColors.Charcoal,
                    HuntColors.ForestMid,
                ),
            ),
        ),
    ) {
        content()
    }
}
