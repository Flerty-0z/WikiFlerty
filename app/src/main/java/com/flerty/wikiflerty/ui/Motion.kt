package com.flerty.wikiflerty.ui

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.TweenSpec
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import kotlin.math.roundToInt

enum class MotionSpeed(val id: String, val title: String, val hint: String, val factor: Float) {
    Fast("fast", "Быстро", "Короткие и резвые переходы", 0.65f),
    Normal("normal", "Обычно", "Сбалансированная скорость", 1f),
    Smooth("smooth", "Плавно", "Медленные, мягкие переходы", 1.45f);

    companion object {
        fun of(id: String?) = entries.firstOrNull { it.id == id } ?: Normal
    }
}

enum class MotionStyle(val id: String, val title: String, val hint: String) {
    Soft("soft", "Мягкий", "Плавное ускорение и торможение"),
    Bouncy("bouncy", "Пружинистый", "С лёгким отскоком в конце"),
    Strict("strict", "Строгий", "Чёткие движения без отскока");

    companion object {
        fun of(id: String?) = entries.firstOrNull { it.id == id } ?: Soft
    }
}

enum class PressStrength(val id: String, val title: String, val hint: String, val factor: Float) {
    Off("off", "Выключен", "Кнопки не сжимаются", 0f),
    Light("light", "Слабый", "Едва заметное сжатие", 0.55f),
    Normal("normal", "Обычный", "Заметное сжатие с пружинкой", 1f),
    Strong("strong", "Сильный", "Глубокое сжатие", 1.8f);

    companion object {
        fun of(id: String?) = entries.firstOrNull { it.id == id } ?: Normal
    }
}

enum class ReadingSound(val id: String, val title: String, val hint: String, val factor: Float) {
    Normal("normal", "Как в меню", "Звук фона не меняется во время чтения", 1f),
    Quiet("quiet", "Тише", "Во время чтения звук плавно стихает", 0.3f),
    Off("off", "Выключить", "Во время чтения звук плавно выключается", 0f);

    companion object {
        fun of(id: String?) = entries.firstOrNull { it.id == id } ?: Normal
    }
}

object Motion {
    private val style get() = AppStyle.current

    val enabled: Boolean get() = style.animations
    val speed: MotionSpeed get() = MotionSpeed.of(style.motionSpeed)
    val kind: MotionStyle get() = MotionStyle.of(style.motionStyle)

    private val bouncy = CubicBezierEasing(0.34f, 1.36f, 0.64f, 1f)
    private val strict = CubicBezierEasing(0.2f, 0f, 0f, 1f)

    val easing: Easing
        get() = when (kind) {
            MotionStyle.Soft -> FastOutSlowInEasing
            MotionStyle.Bouncy -> bouncy
            MotionStyle.Strict -> strict
        }

    fun ms(base: Int): Int = if (!enabled) 0 else (base * speed.factor).roundToInt()

    fun <T> tween(durationMillis: Int, delayMillis: Int = 0, easing: Easing? = null): TweenSpec<T> =
        androidx.compose.animation.core.tween(ms(durationMillis), ms(delayMillis), easing ?: this.easing)

    fun <T> fade(durationMillis: Int): TweenSpec<T> =
        androidx.compose.animation.core.tween(ms(durationMillis), 0, FastOutSlowInEasing)

    fun <T> spring(): FiniteAnimationSpec<T> = if (!enabled) {
        snap()
    } else {
        spring(
            dampingRatio = when (kind) {
                MotionStyle.Soft -> 0.75f
                MotionStyle.Bouncy -> 0.45f
                MotionStyle.Strict -> 1f
            },
            stiffness = when (speed) {
                MotionSpeed.Fast -> Spring.StiffnessMedium
                MotionSpeed.Normal -> 500f
                MotionSpeed.Smooth -> Spring.StiffnessLow
            },
        )
    }

    fun pressScale(base: Float): Float {
        if (!enabled) return 1f
        val factor = PressStrength.of(style.press).factor
        return (1f - (1f - base) * factor).coerceIn(0.8f, 1f)
    }
}

fun androidx.compose.foundation.lazy.LazyItemScope.motionItem(): androidx.compose.ui.Modifier =
    androidx.compose.ui.Modifier.animateItem(
        fadeInSpec = if (Motion.enabled) Motion.fade(260) else null,
        placementSpec = if (Motion.enabled) Motion.tween(320) else null,
        fadeOutSpec = if (Motion.enabled) Motion.fade(180) else null,
    )

fun androidx.compose.foundation.lazy.grid.LazyGridItemScope.motionItem(): androidx.compose.ui.Modifier =
    androidx.compose.ui.Modifier.animateItem(
        fadeInSpec = if (Motion.enabled) Motion.fade(260) else null,
        placementSpec = if (Motion.enabled) Motion.tween(320) else null,
        fadeOutSpec = if (Motion.enabled) Motion.fade(180) else null,
    )
