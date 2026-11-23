package uk.hunttrack.core.designsystem.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import java.util.Locale
import uk.hunttrack.core.designsystem.R
import uk.hunttrack.core.designsystem.theme.HuntColors

fun locationShareText(
    latitude: Double,
    longitude: Double,
    gridRef: String?,
    gridLine: (String) -> String,
    coordinatesLine: (String) -> String,
): String {
    val coordinates = String.format(Locale.UK, "%.5f, %.5f", latitude, longitude)
    return buildString {
        if (!gridRef.isNullOrBlank()) {
            append(gridLine(gridRef))
            append('\n')
        }
        append(coordinatesLine(coordinates))
    }
}

/** Grid reference or lat/lon text. A tap opens Copy, Share, and Open in Maps. */
@Composable
fun TappableCoordinates(
    text: String,
    latitude: Double,
    longitude: Double,
    modifier: Modifier = Modifier,
    gridRef: String? = null,
    style: TextStyle = MaterialTheme.typography.bodyMedium,
    color: Color = HuntColors.Cream,
) {
    var open by remember { mutableStateOf(false) }
    Text(
        text = text,
        style = style,
        color = color,
        modifier = modifier.clickable { open = true },
    )
    if (open) {
        LocationActionsDialog(
            latitude = latitude,
            longitude = longitude,
            gridRef = gridRef,
            onDismiss = { open = false },
        )
    }
}

@Composable
fun LocationActionsDialog(
    latitude: Double,
    longitude: Double,
    gridRef: String?,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val summary = locationShareText(
        latitude = latitude,
        longitude = longitude,
        gridRef = gridRef,
        gridLine = { context.getString(R.string.location_grid_line, it) },
        coordinatesLine = { context.getString(R.string.location_coordinates_line, it) },
    )
    var copied by remember { mutableStateOf(false) }
    val copyLabel = stringResource(if (copied) R.string.location_copied else R.string.location_action_copy)
    val shareTitle = stringResource(R.string.location_share_chooser)
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = HuntColors.ForestMid,
        titleContentColor = HuntColors.Cream,
        textContentColor = HuntColors.Mist,
        title = { Text(stringResource(R.string.location_actions_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(summary, style = MaterialTheme.typography.bodyMedium, color = HuntColors.Cream)
            }
        },
        confirmButton = {
            Column(Modifier.fillMaxWidth()) {
                TextButton(
                    onClick = {
                        val clipboard = context.getSystemService(ClipboardManager::class.java)
                        clipboard?.setPrimaryClip(ClipData.newPlainText("location", summary))
                        copied = true
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(copyLabel) }
                TextButton(
                    onClick = {
                        val send = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, summary)
                        }
                        context.startActivity(Intent.createChooser(send, shareTitle))
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.location_action_share)) }
                TextButton(
                    onClick = {
                        val uri = Uri.parse("geo:$latitude,$longitude?q=$latitude,$longitude")
                        val view = Intent(Intent.ACTION_VIEW, uri)
                        runCatching { context.startActivity(view) }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.location_action_open_maps)) }
            }
        },
    )
}
