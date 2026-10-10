package uk.hunttrack.core.designsystem.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import uk.hunttrack.core.designsystem.theme.HuntColors
import kotlin.math.roundToInt

/**
 * Horizontal option/chip row with a right-edge “slides further” affordance.
 *
 * @param prominentHint stronger double peek (map toolbar). Decorative chevron only — no overlay button.
 */
@Composable
fun HintingOptionRow(
    modifier: Modifier = Modifier,
    horizontalArrangement: Arrangement.Horizontal = Arrangement.spacedBy(8.dp),
    verticalAlignment: Alignment.Vertical = Alignment.CenterVertically,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    prominentHint: Boolean = false,
    content: @Composable RowScope.() -> Unit,
) {
    val scrollState = rememberScrollState()
    HintingScrollChrome(
        modifier = modifier,
        canScrollForward = scrollState.canScrollForward,
        canScrollBackward = scrollState.canScrollBackward,
        prominentHint = prominentHint,
        onPeek = { peekScrollState(scrollState, prominent = prominentHint) },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(scrollState)
                .padding(contentPadding),
            horizontalArrangement = horizontalArrangement,
            verticalAlignment = verticalAlignment,
            content = content,
        )
    }
}

/**
 * Lazy variant for long chip lists (species groups, marker pickers, etc.).
 */
@Composable
fun HintingLazyOptionRow(
    modifier: Modifier = Modifier,
    state: LazyListState = rememberLazyListState(),
    contentPadding: PaddingValues = PaddingValues(0.dp),
    horizontalArrangement: Arrangement.Horizontal = Arrangement.spacedBy(8.dp),
    verticalAlignment: Alignment.Vertical = Alignment.CenterVertically,
    prominentHint: Boolean = false,
    content: LazyListScope.() -> Unit,
) {
    HintingScrollChrome(
        modifier = modifier,
        canScrollForward = state.canScrollForward,
        canScrollBackward = state.canScrollBackward,
        prominentHint = prominentHint,
        onPeek = { peekLazyList(state, prominent = prominentHint) },
    ) {
        LazyRow(
            state = state,
            modifier = Modifier.fillMaxWidth(),
            contentPadding = contentPadding,
            horizontalArrangement = horizontalArrangement,
            verticalAlignment = verticalAlignment,
            content = content,
        )
    }
}

/**
 * Press feedback that nudges a control slightly to the right, reinforcing the slide gesture.
 */
@Composable
fun Modifier.slideRightOnPress(
    interactionSource: MutableInteractionSource,
    distanceDp: Float = 5f,
): Modifier {
    val pressed by interactionSource.collectIsPressedAsState()
    val offsetX by animateFloatAsState(
        targetValue = if (pressed) distanceDp else 0f,
        animationSpec = tween(durationMillis = 110, easing = FastOutSlowInEasing),
        label = "chipSlideRight",
    )
    val density = LocalDensity.current
    return this.offset {
        IntOffset(with(density) { offsetX.dp.roundToPx() }, 0)
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun HintingScrollChrome(
    modifier: Modifier,
    canScrollForward: Boolean,
    canScrollBackward: Boolean,
    prominentHint: Boolean,
    onPeek: suspend () -> Unit,
    content: @Composable () -> Unit,
) {
    var didPeek by remember { mutableStateOf(false) }

    LaunchedEffect(canScrollForward) {
        if (canScrollForward && !didPeek) {
            didPeek = true
            delay(if (prominentHint) 280 else 420)
            onPeek()
        }
    }

    // Height comes only from the chip row. Overlays use matchParentSize so they
    // cannot expand under unbounded Column max-height (which blocked map touches).
    Box(modifier = modifier.fillMaxWidth()) {
        content()

        if (canScrollBackward || canScrollForward) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .pointerInteropFilter { false },
            ) {
                if (canScrollBackward) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .fillMaxHeight()
                            .width(if (prominentHint) 28.dp else 18.dp)
                            .background(
                                Brush.horizontalGradient(
                                    listOf(
                                        HuntColors.ForestDeep.copy(
                                            alpha = if (prominentHint) 0.85f else 0.55f,
                                        ),
                                        Color.Transparent,
                                    ),
                                ),
                            ),
                    )
                }
                if (canScrollForward) {
                    val pulse = rememberInfiniteTransition(label = "scrollHintPulse")
                    val pulseAlpha by pulse.animateFloat(
                        initialValue = 0.55f,
                        targetValue = 1f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(
                                if (prominentHint) 700 else 900,
                                easing = FastOutSlowInEasing,
                            ),
                            repeatMode = RepeatMode.Reverse,
                        ),
                        label = "chevronAlpha",
                    )
                    val nudgeX by pulse.animateFloat(
                        initialValue = 0f,
                        targetValue = if (prominentHint) 7f else 5f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(
                                if (prominentHint) 700 else 900,
                                easing = FastOutSlowInEasing,
                            ),
                            repeatMode = RepeatMode.Reverse,
                        ),
                        label = "chevronNudge",
                    )

                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .fillMaxHeight()
                            .width(if (prominentHint) 44.dp else 36.dp)
                            .background(
                                Brush.horizontalGradient(
                                    listOf(
                                        Color.Transparent,
                                        HuntColors.ForestDeep.copy(
                                            alpha = if (prominentHint) 0.88f else 0.72f,
                                        ),
                                    ),
                                ),
                            ),
                        contentAlignment = Alignment.CenterEnd,
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = null,
                            tint = HuntColors.BronzeBright.copy(alpha = pulseAlpha),
                            modifier = Modifier
                                .padding(end = 2.dp)
                                .size(if (prominentHint) 26.dp else 22.dp)
                                .graphicsLayer { translationX = nudgeX },
                        )
                    }
                }
            }
        }
    }
}

private suspend fun peekScrollState(scrollState: ScrollState, prominent: Boolean) {
    val peek = if (prominent) {
        140.coerceAtMost((scrollState.maxValue * 0.35f).roundToInt().coerceAtLeast(96))
    } else {
        72.coerceAtMost((scrollState.maxValue * 0.18f).roundToInt().coerceAtLeast(48))
    }
    if (peek <= 0 || scrollState.maxValue <= 0) return
    scrollState.animateScrollTo(peek, tween(420, easing = FastOutSlowInEasing))
    delay(if (prominent) 420 else 280)
    scrollState.animateScrollTo(0, tween(480, easing = FastOutSlowInEasing))
    if (prominent) {
        delay(500)
        scrollState.animateScrollTo(peek, tween(380, easing = FastOutSlowInEasing))
        delay(360)
        scrollState.animateScrollTo(0, tween(440, easing = FastOutSlowInEasing))
    }
}

private suspend fun peekLazyList(state: LazyListState, prominent: Boolean) {
    if (!state.canScrollForward) return
    val distance = if (prominent) 120f else 72f
    state.animateScrollBy(distance, tween(420, easing = FastOutSlowInEasing))
    delay(if (prominent) 420 else 280)
    state.animateScrollToItem(0)
    if (prominent) {
        delay(500)
        state.animateScrollBy(distance, tween(380, easing = FastOutSlowInEasing))
        delay(360)
        state.animateScrollToItem(0)
    }
}
