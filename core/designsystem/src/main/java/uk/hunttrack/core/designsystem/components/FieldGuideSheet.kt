package uk.hunttrack.core.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.HelpOutline
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import uk.hunttrack.core.designsystem.theme.HuntColors

data class FieldGuideSection(
    val title: String,
    val body: String,
)

/**
 * In-app training / help sheet — title, short sections, Close.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FieldGuideSheet(
    visible: Boolean,
    title: String,
    subtitle: String,
    sections: List<FieldGuideSection>,
    onDismiss: () -> Unit,
    confirmLabel: String = "Close",
) {
    if (!visible) return
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = HuntColors.ForestMid,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(title, style = MaterialTheme.typography.headlineMedium, color = HuntColors.Cream)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = HuntColors.Mist)
            sections.forEach { section ->
                GlassPanel(Modifier.fillMaxWidth()) {
                    Text(section.title, style = MaterialTheme.typography.titleMedium, color = HuntColors.BronzeBright)
                    Spacer(Modifier.height(4.dp))
                    Text(section.body, style = MaterialTheme.typography.bodyMedium, color = HuntColors.Cream)
                }
            }
            HuntButton(
                onClick = onDismiss,
                text = confirmLabel,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
fun FieldGuideHelpButton(
    onClick: () -> Unit,
    contentDescription: String = "Help and training",
) {
    IconButton(onClick = onClick) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(HuntColors.ForestDeep)
                .border(1.dp, HuntColors.Bronze.copy(alpha = 0.75f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Outlined.HelpOutline,
                contentDescription = contentDescription,
                tint = HuntColors.Cream,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
fun FieldGuideTitleRow(
    title: String,
    subtitle: String,
    onHelp: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.headlineLarge, color = HuntColors.Cream)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = HuntColors.Mist)
        }
        FieldGuideHelpButton(onClick = onHelp)
    }
}

object FieldGuides {
    val wind = listOf(
        FieldGuideSection(
            title = "What this screen does",
            body = "Shows which way the wind is blowing, and which way your scent (smell) will travel. Animals usually smell you from downwind.",
        ),
        FieldGuideSection(
            title = "Wind FROM vs scent",
            body = "Weather reports wind FROM a direction (e.g. west wind = air coming from the west). Your scent drifts the opposite way — downwind.",
        ),
        FieldGuideSection(
            title = "What “check this spot” means",
            body = "Pick one of your map markers (a hide, high seat, deer sighting, etc.). HunTracK asks: if I stand here now, does my scent blow toward that marker? Use this to decide if deer at that spot might smell you.",
        ),
        FieldGuideSection(
            title = "WARNING vs Clear",
            body = "WARNING = scent is heading roughly toward the marker (within ~40°). Clear = scent path is off to the side or away. No marker selected = nothing to compare yet.",
        ),
        FieldGuideSection(
            title = "Approach into the wind",
            body = "After you choose a marker, open Map. HunTracK draws an orange approach line from downwind of the spot toward it — walk that line so wind hits the front of you and your scent blows behind.",
        ),
        FieldGuideSection(
            title = "Need markers first",
            body = "Drop deer waypoints with a long-press on the Map tab (or Mark GPS / the + pin). Then return here and tap a marker to check scent risk and show the approach route.",
        ),
    )

    val map = listOf(
        FieldGuideSection(
            title = "Live map basics",
            body = "You are the person icon. Soft light magenta smoke shows your scent drifting downwind — " +
                "calm wind = very slow drift; stronger wind = faster smoke (up to extremely fast in gale).",
        ),
        FieldGuideSection(
            title = "Tool bar — swipe for more",
            body = "The chip row at the top (Home, North up, Walk fence, Layers…) scrolls sideways. " +
                "Swipe left/right across the chips to reach more tools (Delete, Offline, Overlays, fences). " +
                "When you open the map it briefly peeks to show there is more along the row.",
        ),
        FieldGuideSection(
            title = "Home (retrieve / downed prey)",
            body = "Place a red pin where prey went down (tap Home then the map, or Home here). Walk toward it — distance, turn hints, and an alert when you are within 5 m. At 1 m the panel shows At target.",
        ),
        FieldGuideSection(
            title = "Mark & trail",
            body = "Tap an empty spot on the map to open the Mark location form and add any marker type at that exact spot. With “Mark anywhere on map” off in Settings, taps instead drop a quick deer waypoint. Long-press does the same as a tap. Tap an existing pin to select it. Mark GPS uses your phone position. Clear trail removes placed markers. Back closes the last panel you opened.",
        ),
        FieldGuideSection(
            title = "Layers & offline",
            body = "Layers switches Streets / Topo / Satellite. Offline downloads a map pack for weak signal areas.",
        ),
    )
}
