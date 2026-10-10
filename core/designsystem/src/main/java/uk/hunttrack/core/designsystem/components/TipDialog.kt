package uk.hunttrack.core.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.HelpOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import uk.hunttrack.core.designsystem.theme.HuntColors

/**
 * Short how-to popup for a single control (opened from a ? button).
 */
@Composable
fun TipDialog(
    visible: Boolean,
    title: String,
    body: String,
    onDismiss: () -> Unit,
) {
    if (!visible) return
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = HuntColors.ForestMid,
        titleContentColor = HuntColors.Cream,
        textContentColor = HuntColors.Mist,
        title = { Text(title, style = MaterialTheme.typography.titleLarge) },
        text = {
            Text(body, style = MaterialTheme.typography.bodyMedium, color = HuntColors.Cream)
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Got it", color = HuntColors.BronzeBright)
            }
        },
    )
}

@Composable
fun TipHelpIcon(
    onClick: () -> Unit,
    contentDescription: String = "How to use",
) {
    IconButton(onClick = onClick, modifier = Modifier.padding(0.dp)) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(HuntColors.ForestDeep)
                .border(1.dp, HuntColors.Bronze.copy(alpha = 0.75f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Outlined.HelpOutline,
                contentDescription = contentDescription,
                tint = HuntColors.Cream,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

@Composable
fun LabeledTipRow(
    label: String,
    tipTitle: String,
    tipBody: String,
    tipVisible: Boolean,
    onShowTip: () -> Unit,
    onDismissTip: () -> Unit,
    content: @Composable () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.fillMaxWidth()) {
        content()
        TipDialog(visible = tipVisible, title = tipTitle, body = tipBody, onDismiss = onDismissTip)
    }
}

object MapTips {
    val sync = "Centres the map on your GPS at full zoom (~1 m scale bar)."
    val home = "Places a retrieve pin for downed prey. Walk toward it for live distance and turn hints; phone alerts when you are within 5 m. At 1 m the panel shows At target. Tap End home when finished."
    val homeHere = "Drops the retrieve pin at your current GPS position."
    val layers = "Switch Streets, Outdoor/Topo, or Satellite basemap."
    val offline = "Download a map pack around you for weak-signal areas."
    val track = "Records a GPS track line as you walk."
    val mark = "Long-press the map to drop a deer-head waypoint immediately. Mark GPS / + opens the full marker form at your position. Tap a waypoint to open it."
    val delete = "Trash: Delete last removes the newest marker. Delete all clears every field marker (not the Home pin — use End home for that)."
    val clearTrail = "Deletes all field markers and the connecting path."
    val smoke = "Light magenta scent smoke drifts downwind from you (uses a default west wind until weather loads). Speed matches wind bands from calm to gale."
    val overlays = "Turn map overlays on or off: scent smoke, GPS track, marker path."
}
