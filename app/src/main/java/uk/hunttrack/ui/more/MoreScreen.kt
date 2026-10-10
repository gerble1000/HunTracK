package uk.hunttrack.ui.more

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import uk.hunttrack.core.designsystem.components.ClickableGlassCard
import uk.hunttrack.core.designsystem.components.RegisterLazyListScrollToTop
import uk.hunttrack.core.designsystem.components.ScreenBackground
import uk.hunttrack.core.designsystem.components.SectionLabel
import uk.hunttrack.core.designsystem.theme.HuntColors

@Composable
fun MoreScreen(
    onOpenCompass: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenAbout: () -> Unit,
    onOpenSpecies: () -> Unit,
    onOpenCalls: () -> Unit,
    onOpenPermissions: () -> Unit,
    onOpenEmergency: () -> Unit,
    onOpenCalendar: () -> Unit,
    onOpenCulls: () -> Unit = {},
    onOpenComingSoon: (String) -> Unit,
) {
    ScreenBackground(Modifier.fillMaxSize()) {
        val listState = rememberLazyListState()
        RegisterLazyListScrollToTop(listState)
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text("More", style = MaterialTheme.typography.headlineLarge, color = HuntColors.Cream)
                Text("Tap any row to open", style = MaterialTheme.typography.bodyMedium, color = HuntColors.Mist)
            }
            item {
                SectionLabel("Navigation")
                MoreItem("Compass", "Magnetic, true & GPS heading", hint = "Open compass ›", onClick = onOpenCompass)
            }
            item {
                SectionLabel("Field knowledge")
                MoreItem("Species", "UK quarry ID & legal seasons", hint = "Browse species ›", onClick = onOpenSpecies)
                MoreItem("Calls", "Library, recorder & journal", hint = "Open calls ›", onClick = onOpenCalls)
            }
            item {
                SectionLabel("Planning")
                MoreItem(
                    "Calendar",
                    "Day, month & year field diary",
                    hint = "Open calendar ›",
                    onClick = onOpenCalendar,
                )
                MoreItem(
                    "Cull log",
                    "Bag table, OS Grid & CSV export",
                    hint = "Open cull log ›",
                    onClick = onOpenCulls,
                )
            }
            item {
                SectionLabel("Safety & access")
                MoreItem("Permissions", "Wallet & boundary areas", hint = "Open permissions ›", onClick = onOpenPermissions)
                MoreItem("Emergency", "Public medical profile", hint = "Open emergency ›", onClick = onOpenEmergency)
            }
            item {
                SectionLabel("App")
                MoreItem("Settings", "Units, theme, sound, maps", hint = "Open settings ›", onClick = onOpenSettings)
                MoreItem(
                    "About",
                    "App story, developer & helping hand",
                    hint = "Open about ›",
                    onClick = onOpenAbout,
                )
            }
        }
    }
}

@Composable
private fun MoreItem(
    title: String,
    subtitle: String,
    enabled: Boolean = true,
    hint: String = "Tap to open ›",
    onClick: () -> Unit,
) {
    ClickableGlassCard(
        onClick = onClick,
        enabled = enabled,
        hint = hint,
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp),
    ) {
        Text(
            title,
            style = MaterialTheme.typography.titleLarge,
            color = if (enabled) HuntColors.Cream else HuntColors.Mist,
        )
        Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = HuntColors.Mist)
    }
}
