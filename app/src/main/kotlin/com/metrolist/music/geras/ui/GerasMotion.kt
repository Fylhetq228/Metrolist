package com.metrolist.music.geras.ui

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.navigation.NavBackStackEntry
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Springs tuned to feel like iOS: quick press, soft overshooting release. */
object GerasSprings {
    val press = spring<Float>(dampingRatio = 1f, stiffness = 1500f)
    val release = spring<Float>(dampingRatio = 0.42f, stiffness = 420f)
    val bouncy = spring<Float>(dampingRatio = 0.55f, stiffness = 500f)
    val smooth = spring<Float>(dampingRatio = 0.9f, stiffness = Spring.StiffnessMediumLow)
}

/**
 * Shrinks the element while a finger is on it and springs it back on release. Watches the
 * pointer without consuming it, so it works next to any clickable. Put it FIRST in the chain
 * so background, clip and shadow scale together.
 */
fun Modifier.gerasBounce(pressedScale: Float = 0.94f, dim: Boolean = true): Modifier = composed {
    var pressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (pressed) pressedScale else 1f,
        animationSpec = if (pressed) GerasSprings.press else GerasSprings.release,
        label = "bounce",
    )
    this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
            if (dim) alpha = 1f - (1f - scale) * 2.2f
        }
        .pointerInput(Unit) {
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                pressed = true
                // Final pass sees whether a scroll took over the gesture.
                waitForUpOrCancellation(PointerEventPass.Final)
                pressed = false
            }
        }
}

/**
 * Fade, lift and slight scale on first appearance, staggered by [index]. Runs once per
 * composition key, so scrolling back does not replay it.
 */
fun Modifier.gerasAppear(index: Int = 0, key: Any = Unit): Modifier = composed {
    var shown by rememberSaveable(key) { mutableStateOf(false) }
    val progress = remember(key) { Animatable(if (shown) 1f else 0f) }
    val lift = with(LocalDensity.current) { 22.dp.toPx() }
    LaunchedEffect(key) {
        if (!shown) {
            delay(45L * index.coerceAtMost(10))
            launch { progress.animateTo(1f, spring(dampingRatio = 0.8f, stiffness = 260f)) }
            shown = true
        }
    }
    graphicsLayer {
        val p = progress.value
        alpha = p.coerceIn(0f, 1f)
        translationY = (1f - p) * lift
        val s = 0.96f + 0.04f * p
        scaleX = s
        scaleY = s
    }
}

/** iOS-style navigation: tabs cross-fade with a hint of depth, pushed screens slide in over a parallax. */
object GerasTransitions {
    private val tabRoutes = setOf("home", "search_input", "library", "settings")

    private fun AnimatedContentTransitionScope<NavBackStackEntry>.isTabSwitch(): Boolean =
        initialState.destination.route in tabRoutes && targetState.destination.route in tabRoutes

    private val slide = spring(dampingRatio = 1f, stiffness = 380f, visibilityThreshold = androidx.compose.ui.unit.IntOffset(1, 1))

    val enter: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = {
        if (isTabSwitch()) {
            fadeIn(tween(260, delayMillis = 40)) + scaleIn(spring(dampingRatio = 0.85f, stiffness = 300f), initialScale = 0.965f)
        } else {
            slideInHorizontally(slide) { it / 3 } + fadeIn(tween(220))
        }
    }

    val exit: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = {
        if (isTabSwitch()) {
            fadeOut(tween(160)) + scaleOut(tween(220, easing = FastOutSlowInEasing), targetScale = 1.02f)
        } else {
            slideOutHorizontally(slide) { -it / 6 } + fadeOut(tween(200)) + scaleOut(tween(300), targetScale = 0.97f)
        }
    }

    val popEnter: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = {
        if (isTabSwitch()) {
            fadeIn(tween(260, delayMillis = 40)) + scaleIn(spring(dampingRatio = 0.85f, stiffness = 300f), initialScale = 0.965f)
        } else {
            slideInHorizontally(slide) { -it / 6 } + fadeIn(tween(240)) + scaleIn(tween(300), initialScale = 0.97f)
        }
    }

    val popExit: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = {
        if (isTabSwitch()) {
            fadeOut(tween(160))
        } else {
            slideOutHorizontally(slide) { it / 3 } + fadeOut(tween(180))
        }
    }
}

/** A springy "pop" whenever [active] turns on, e.g. a heart being liked. */
fun Modifier.gerasPop(active: Boolean): Modifier = composed {
    val scale = remember { Animatable(1f) }
    var first by remember { mutableStateOf(true) }
    LaunchedEffect(active) {
        if (first) {
            first = false
            return@LaunchedEffect
        }
        if (active) {
            scale.animateTo(1.32f, spring(dampingRatio = 1f, stiffness = 1800f))
            scale.animateTo(1f, spring(dampingRatio = 0.35f, stiffness = 380f))
        } else {
            scale.animateTo(0.82f, spring(dampingRatio = 1f, stiffness = 1800f))
            scale.animateTo(1f, spring(dampingRatio = 0.5f, stiffness = 420f))
        }
    }
    graphicsLayer {
        scaleX = scale.value
        scaleY = scale.value
    }
}
