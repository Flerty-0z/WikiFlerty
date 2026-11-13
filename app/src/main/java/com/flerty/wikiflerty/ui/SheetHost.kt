package com.flerty.wikiflerty.ui

import android.os.Build
import android.view.ViewGroup
import android.view.WindowManager
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import kotlinx.coroutines.launch

private val SheetEase = androidx.compose.animation.core.CubicBezierEasing(0.16f, 1f, 0.3f, 1f)
private val SheetExit = androidx.compose.animation.core.CubicBezierEasing(0.3f, 0.35f, 0.55f, 1f)

class SheetControl internal constructor(val close: () -> Unit, private val shown: () -> Float) {
    val progress: Float get() = shown()
}

@Composable
private fun DialogWindowSetup() {
    val view = LocalView.current
    val light = AppStyle.light
    DisposableEffect(view, light) {
        var parent: android.view.ViewParent? = view.parent
        var window: android.view.Window? = null
        while (parent != null) {
            if (parent is DialogWindowProvider) {
                window = parent.window
                break
            }
            parent = parent.parent
        }
        window?.let { w ->
            w.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            w.setDimAmount(0f)
            w.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            w.setWindowAnimations(0)
            @Suppress("DEPRECATION")
            w.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
            WindowCompat.setDecorFitsSystemWindows(w, false)
            @Suppress("DEPRECATION")
            w.statusBarColor = android.graphics.Color.TRANSPARENT
            @Suppress("DEPRECATION")
            w.navigationBarColor = android.graphics.Color.TRANSPARENT
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                w.isNavigationBarContrastEnforced = false
                w.isStatusBarContrastEnforced = false
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                w.attributes = w.attributes.apply { layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES }
            }
            WindowCompat.getInsetsController(w, w.decorView).apply {
                isAppearanceLightStatusBars = light
                isAppearanceLightNavigationBars = light
            }
        }
        onDispose { }
    }
}

@Composable
fun SheetWindow(
    onDismiss: () -> Unit,
    onClosing: () -> Unit = {},
    onBack: (() -> Boolean)? = null,
    content: @Composable BoxScope.(SheetControl) -> Unit,
) {
    val backHook by rememberUpdatedState(onBack)
    val latest by rememberUpdatedState(onDismiss)
    val closingHook by rememberUpdatedState(onClosing)
    val shown = remember { Animatable(if (Motion.enabled) 0f else 1f) }
    val scope = rememberCoroutineScope()
    val closing = remember { booleanArrayOf(false) }
    val close: () -> Unit = remember {
        {
            if (!closing[0]) {
                closing[0] = true
                closingHook()
                scope.launch {
                    if (Motion.enabled) runCatching { shown.animateTo(0f, tween(Motion.ms(260), easing = SheetExit)) }
                    latest()
                }
            }
        }
    }
    val control = remember { SheetControl(close) { shown.value } }
    val back: () -> Unit = remember { { if (backHook?.invoke() != true) close() } }
    Dialog(
        onDismissRequest = back,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        DialogWindowSetup()
        KeepHostBars()
        LaunchedEffect(Unit) {
            if (Motion.enabled) {
                androidx.compose.runtime.withFrameNanos { }
                androidx.compose.runtime.withFrameNanos { }
                shown.animateTo(1f, tween(Motion.ms(380), easing = SheetEase))
            }
        }
        Box(Modifier.fillMaxSize()) {
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = shown.value }
                    .background(Color.Black.copy(alpha = if (AppStyle.light) 0.32f else 0.48f))
                    .pointerInput(Unit) { detectTapGestures { back() } },
            )
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .imePadding()
                    .graphicsLayer { translationY = (1f - shown.value) * (size.height + 24f * density) }
                    .pointerInput(Unit) { detectTapGestures { } },
            ) { content(control) }
        }
    }
}
