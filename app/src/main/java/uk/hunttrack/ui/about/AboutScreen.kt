package uk.hunttrack.ui.about

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import uk.hunttrack.core.designsystem.components.GlassPanel
import uk.hunttrack.core.designsystem.components.MetricRow
import uk.hunttrack.core.designsystem.components.RegisterLazyListScrollToTop
import uk.hunttrack.core.designsystem.components.ScreenBackground
import uk.hunttrack.core.designsystem.components.SectionLabel
import uk.hunttrack.core.designsystem.theme.HuntColors

@Composable
fun AboutScreen() {
    val uriHandler = LocalUriHandler.current
    val context = LocalContext.current
    val versionName = try {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "unknown"
    } catch (_: Exception) { "unknown" }

    ScreenBackground(Modifier.fillMaxSize()) {
        val listState = rememberLazyListState()
        RegisterLazyListScrollToTop(listState)
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                Text("About", style = MaterialTheme.typography.headlineLarge, color = HuntColors.Cream)
                Text(
                    "HunTracK",
                    style = MaterialTheme.typography.titleLarge,
                    color = HuntColors.BronzeBright,
                )
            }

            item {
                GlassPanel(Modifier.fillMaxWidth()) {
                    SectionLabel("The app")
                    Text(
                        "HunTracK is a field companion for UK hunters and stalkers. " +
                            "It brings your map, wind & scent awareness, compass, weather, " +
                            "species seasons, calls, and safety tools into one pocket kit — " +
                            "built for use outside, not at a desk.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = HuntColors.Cream,
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "Know where you are, which way the wind sends your scent, " +
                            "range a shot, mark downed prey, and keep permissions and emergency " +
                            "details ready when you need them.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = HuntColors.Mist,
                    )
                    Spacer(Modifier.height(12.dp))
                    MetricRow("Version", versionName)
                }
            }

            item {
                GlassPanel(Modifier.fillMaxWidth()) {
                    SectionLabel("Developer")
                    Text(
                        "GER8LE",
                        style = MaterialTheme.typography.headlineMedium,
                        color = HuntColors.BronzeBright,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Built and engineered HunTracK — app architecture, map tools, " +
                            "field features, and the polish you use each outing.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = HuntColors.Cream,
                    )
                }
            }

            item {
                GlassPanel(Modifier.fillMaxWidth()) {
                    SectionLabel("Helping hand")
                    Text(
                        "Greg Willing",
                        style = MaterialTheme.typography.headlineMedium,
                        color = HuntColors.BronzeBright,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Design guidance and field-feature direction from someone who knows " +
                            "the ground. Greg helped shape how HunTracK feels in the hand and " +
                            "what matters when you are out on permission.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = HuntColors.Cream,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Thank you for the helping hand.",
                        style = MaterialTheme.typography.titleMedium,
                        color = HuntColors.Gold,
                    )
                }
            }

            item {
                GlassPanel(Modifier.fillMaxWidth()) {
                    SectionLabel("Support & Community")
                    Text(
                        "Join our Facebook group for tips, updates, and community support:",
                        style = MaterialTheme.typography.bodyMedium,
                        color = HuntColors.Cream,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "HunTracK Facebook Group",
                        style = MaterialTheme.typography.titleMedium,
                        color = HuntColors.BronzeBright,
                        modifier = Modifier.clickable {
                            uriHandler.openUri("https://www.facebook.com/share/g/19MMYKx2fa")
                        },
                    )
                }
            }

            item {
                GlassPanel(Modifier.fillMaxWidth()) {
                    SectionLabel("Please remember")
                    Text(
                        "Weather, scent checks, seasons, and activity guidance are indicative only. " +
                            "Always verify current laws, land permission, and safe practice before you hunt.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = HuntColors.Mist,
                    )
                }
            }
        }
    }
}
