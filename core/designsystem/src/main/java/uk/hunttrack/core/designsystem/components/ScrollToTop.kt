package uk.hunttrack.core.designsystem.components

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import uk.hunttrack.core.designsystem.theme.HuntColors

class ScrollToTopController internal constructor() {
    private val handlers = mutableListOf<suspend () -> Unit>()

    internal fun register(action: suspend () -> Unit): () -> Unit {
        handlers.add(action)
        return {
            handlers.remove(action)
        }
    }

    suspend fun scrollToTop() {
        handlers.lastOrNull()?.invoke()
    }
}

val LocalScrollToTopController = staticCompositionLocalOf { ScrollToTopController() }

@Composable
fun rememberScrollToTopController(): ScrollToTopController = remember { ScrollToTopController() }

@Composable
fun RegisterLazyListScrollToTop(listState: LazyListState) {
    val controller = LocalScrollToTopController.current
    DisposableEffect(listState, controller) {
        val unregister = controller.register {
            listState.animateScrollToItem(0)
        }
        onDispose { unregister() }
    }
    BindLazyListScrollHints(listState)
    ScrollDownPeekOnce(listState)
}

@Composable
fun RegisterScrollStateToTop(scrollState: ScrollState) {
    val controller = LocalScrollToTopController.current
    DisposableEffect(scrollState, controller) {
        val unregister = controller.register {
            scrollState.animateScrollTo(0)
        }
        onDispose { unregister() }
    }
    BindScrollStateHints(scrollState)
}

@Composable
fun BackToTopButton(modifier: Modifier = Modifier) {
    val controller = LocalScrollToTopController.current
    val moreHint = LocalScrollMoreHintState.current
    val scope = rememberCoroutineScope()
    TextButton(
        onClick = {
            scope.launch {
                moreHint.toTop()
                controller.scrollToTop()
            }
        },
        modifier = modifier,
        shape = RoundedCornerShape(10.dp),
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
    ) {
        Text(
            text = "Back to Top",
            style = MaterialTheme.typography.labelSmall,
            color = HuntColors.BronzeBright,
        )
    }
}
