package uk.hunttrack.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import uk.hunttrack.core.designsystem.components.GlassPanel
import uk.hunttrack.core.designsystem.components.RegisterScrollStateToTop
import uk.hunttrack.core.designsystem.components.ScreenBackground
import uk.hunttrack.core.designsystem.theme.HuntColors

@Composable
fun ComingSoonScreen(feature: String) {
    ScreenBackground(Modifier.fillMaxSize()) {
        val scrollState = rememberScrollState()
        RegisterScrollStateToTop(scrollState)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            GlassPanel {
                Text(feature, style = MaterialTheme.typography.headlineLarge, color = HuntColors.Bronze)
                Text(
                    "This module is scaffolded for Phase 2+. Architecture hooks are ready in the app shell.",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
    }
}
