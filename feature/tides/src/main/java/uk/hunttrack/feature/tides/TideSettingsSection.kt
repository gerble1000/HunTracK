package uk.hunttrack.feature.tides

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import uk.hunttrack.core.designsystem.components.GlassPanel
import uk.hunttrack.core.designsystem.components.HuntButton
import uk.hunttrack.core.designsystem.components.SectionLabel
import uk.hunttrack.core.designsystem.theme.HuntColors
import uk.hunttrack.domain.model.TideEventType

@Composable
fun TideSettingsSection(
    state: TidesUiState,
    onEnabled: (Boolean) -> Unit,
    onOpenMap: () -> Unit,
    onNearest: () -> Unit,
    onRefresh: () -> Unit,
    onClearCache: () -> Unit,
) {
    GlassPanel(Modifier.fillMaxWidth()) {
        SectionLabel("UK tide times")
        Text(
            "Live high/low water from TideTimes.org.uk — refreshed daily when online. " +
                "Cached for offline use; approximate on-device estimate only if no cache.",
            style = MaterialTheme.typography.bodySmall,
            color = HuntColors.Mist,
            modifier = Modifier.padding(bottom = 8.dp),
        )
        RowSwitch("Show UK tide times", state.prefs.tidesEnabled, onEnabled)

        if (state.prefs.tideStationName.isNotBlank()) {
            Text(
                "Station: ${state.prefs.tideStationName}",
                style = MaterialTheme.typography.titleMedium,
                color = HuntColors.Cream,
                modifier = Modifier.padding(top = 8.dp),
            )
        } else {
            Text(
                "No tide station selected",
                style = MaterialTheme.typography.bodyMedium,
                color = HuntColors.Mist,
                modifier = Modifier.padding(top = 8.dp),
            )
        }

        state.lastUpdatedLabel?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = HuntColors.Mist)
        }

        state.error?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = HuntColors.Danger)
        }

        if (state.loadingStations || state.loadingForecast) {
            Row(
                modifier = Modifier.padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                CircularProgressIndicator(modifier = Modifier.padding(4.dp))
                Text("Loading tide times…", color = HuntColors.Mist)
            }
        }

        HuntButton(
            onClick = onOpenMap,
            text = "Choose station on map",
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
        )
        HuntButton(
            onClick = onNearest,
            text = "Use nearest station (GPS)",
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
        )
        HuntButton(
            onClick = onRefresh,
            text = "Refresh tide times",
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
        )

        if (state.prefs.tidesEnabled && state.forecast != null) {
            TideEventsList(state)
        }

        HuntButton(
            onClick = onClearCache,
            text = "Clear tide cache",
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
        )
    }
}

@Composable
private fun TideEventsList(state: TidesUiState) {
    val events = state.forecast?.events.orEmpty().take(14)
    if (events.isEmpty()) return
    Column(modifier = Modifier.padding(top = 12.dp)) {
        SectionLabel("Next tides")
        events.forEach { event ->
            val label = when (event.type) {
                TideEventType.HIGH -> "High water"
                TideEventType.LOW -> "Low water"
                TideEventType.UNKNOWN -> "Tide"
            }
            val time = SimpleDateFormat("EEE d MMM HH:mm", Locale.UK).format(Date(event.epochMs))
            val height = String.format(Locale.UK, "%.2f m", event.heightMeters)
            val approx = if (event.isApproximate) " ~" else ""
            Text(
                "$label · $time · $height$approx",
                style = MaterialTheme.typography.bodyMedium,
                color = HuntColors.Cream,
                modifier = Modifier.padding(vertical = 2.dp),
            )
        }
    }
}

@Composable
private fun RowSwitch(label: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.titleMedium)
        Switch(checked = checked, onCheckedChange = onChecked)
    }
}
