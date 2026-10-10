package uk.hunttrack.core.designsystem.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import uk.hunttrack.core.designsystem.theme.HuntColors

/**
 * Tracks whether the active page can scroll further down / up,
 * so the scaffold can show a shared “more below” affordance.
 */
class ScrollMoreHintState {
    var canScrollForward by mutableStateOf(false)
        internal set
    var canScrollBackward by mutableStateOf(false)
        internal set
    internal var nudgeDown: (suspend () -> Unit)? = null
    internal var scrollToTop: (suspend () -> Unit)? = null

    suspend fun nudge() {
        nudgeDown?.invoke()
    }

    suspend fun toTop() {
        scrollToTop?.invoke()
    }
}

val LocalScrollMoreHintState = staticCompositionLocalOf { ScrollMoreHintState() }

@Composable
fun rememberScrollMoreHintState(): ScrollMoreHintState = remember { ScrollMoreHintState() }

@Composable
fun BindLazyListScrollHints(listState: LazyListState) {
    val hint = LocalScrollMoreHintState.current
    SideEffect {
        hint.canScrollForward = listState.canScrollForward
        hint.canScrollBackward = listState.canScrollBackward
    }
    LaunchedEffect(listState, hint) {
        hint.nudgeDown = { listState.animateScrollBy(220f) }
        hint.scrollToTop = { listState.animateScrollToItem(0) }
        try {
            snapshotFlow { listState.canScrollForward to listState.canScrollBackward }
                .collect { (forward, backward) ->
                    hint.canScrollForward = forward
                    hint.canScrollBackward = backward
                }
        } finally {
            hint.canScrollForward = false
            hint.canScrollBackward = false
            hint.nudgeDown = null
            hint.scrollToTop = null
        }
    }
}

@Composable
fun BindScrollStateHints(scrollState: ScrollState) {
    val hint = LocalScrollMoreHintState.current
    SideEffect {
        hint.canScrollForward = scrollState.canScrollForward
        hint.canScrollBackward = scrollState.canScrollBackward
    }
    LaunchedEffect(scrollState, hint) {
        hint.nudgeDown = {
            val target = (scrollState.value + 220).coerceAtMost(scrollState.maxValue)
            scrollState.animateScrollTo(target)
        }
        hint.scrollToTop = { scrollState.animateScrollTo(0) }
        try {
            snapshotFlow { scrollState.canScrollForward to scrollState.canScrollBackward }
                .collect { (forward, backward) ->
                    hint.canScrollForward = forward
                    hint.canScrollBackward = backward
                }
        } finally {
            hint.canScrollForward = false
            hint.canScrollBackward = false
            hint.nudgeDown = null
            hint.scrollToTop = null
        }
    }
}

/**
 * Bottom-edge cue: mist fade + bouncing chevron stack. Tap nudges the page down.
 */
@Composable
fun MoreBelowHint(
    visible: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = fadeIn(tween(220)) + slideInVertically { it / 3 },
        exit = fadeOut(tween(180)) + slideOutVertically { it / 4 },
    ) {
        val pulse = rememberInfiniteTransition(label = "moreBelowPulse")
        val bob by pulse.animateFloat(
            initialValue = 0f,
            targetValue = 8f,
            animationSpec = infiniteRepeatable(
                animation = tween(750, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "bob",
        )
        val glow by pulse.animateFloat(
            initialValue = 0.55f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(750, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "glow",
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onClick,
                ),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                HuntColors.Charcoal.copy(alpha = 0.55f),
                                HuntColors.Charcoal.copy(alpha = 0.88f),
                            ),
                        ),
                    ),
                contentAlignment = Alignment.BottomCenter,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy((-10).dp),
                    modifier = Modifier
                        .padding(bottom = 6.dp)
                        .graphicsLayer { translationY = bob },
                ) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = null,
                        tint = HuntColors.BronzeBright.copy(alpha = glow * 0.45f),
                        modifier = Modifier.size(22.dp),
                    )
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = null,
                        tint = HuntColors.BronzeBright.copy(alpha = glow * 0.75f),
                        modifier = Modifier.size(26.dp),
                    )
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .background(HuntColors.ForestDeep.copy(alpha = 0.72f))
                            .padding(horizontal = 12.dp, vertical = 4.dp),
                    ) {
                        Text(
                            text = "More below",
                            style = MaterialTheme.typography.labelSmall,
                            color = HuntColors.BronzeBright.copy(alpha = glow),
                        )
                    }
                }
            }
        }
    }
}

/** One-shot peek: gently scrolls down then back when a page first overflows. */
@Composable
fun ScrollDownPeekOnce(listState: LazyListState) {
    var didPeek by remember { mutableStateOf(false) }
    LaunchedEffect(listState) {
        if (didPeek) return@LaunchedEffect
        snapshotFlow { listState.canScrollForward }.first { it }
        didPeek = true
        delay(500)
        if (listState.canScrollForward) {
            listState.animateScrollBy(64f)
            delay(320)
            listState.animateScrollToItem(0)
        }
    }
}
