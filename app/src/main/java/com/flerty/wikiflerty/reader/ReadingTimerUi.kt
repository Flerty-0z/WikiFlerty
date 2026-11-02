package com.flerty.wikiflerty.reader

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.flerty.wikiflerty.App
import com.flerty.wikiflerty.ui.AppColors
import com.flerty.wikiflerty.ui.AppIcons
import com.flerty.wikiflerty.ui.FeedbackSound
import com.flerty.wikiflerty.ui.LocalFeedback
import com.flerty.wikiflerty.ui.accentContent
import com.flerty.wikiflerty.ui.accentHighlight
import com.flerty.wikiflerty.ui.appCorner
import com.flerty.wikiflerty.ui.liveClick
import com.flerty.wikiflerty.ui.panelSurface
import kotlinx.coroutines.delay

@Composable
fun ReadingTimerChip(modifier: Modifier = Modifier) {
    val app = LocalContext.current.applicationContext as App
    val state by app.readingTimer.state.collectAsStateWithLifecycle()
    val s = state ?: return
    if (s.finished) return
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(s) {
        while (true) {
            now = System.currentTimeMillis()
            if (now >= s.endsAt) {
                app.readingTimer.finish()
                break
            }
            delay(1000)
        }
    }
    val left = ((s.endsAt - now) / 1000).coerceAtLeast(0)
    Row(
        modifier
            .panelSurface(CircleShape, elevation = 6.dp)
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Icon(AppIcons.Clock, null, tint = AppColors.Accent, modifier = Modifier.size(13.dp))
        Text("%d:%02d".format(left / 60, left % 60), color = AppColors.Text, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun ReadingTimerDone(modifier: Modifier = Modifier) {
    val app = LocalContext.current.applicationContext as App
    val state by app.readingTimer.state.collectAsStateWithLifecycle()
    val feedback = LocalFeedback.current
    val view = LocalView.current
    val done = state?.finished == true
    LaunchedEffect(done) { if (done) feedback?.play(FeedbackSound.Open, view) }
    AnimatedVisibility(
        visible = done,
        enter = fadeIn() + scaleIn(initialScale = 0.9f),
        exit = fadeOut(),
        modifier = modifier,
    ) {
        val minutes = state?.minutes ?: 0
        val shape = appCorner(24)
        Column(
            Modifier.fillMaxWidth().panelSurface(shape, elevation = 16.dp).border(1.dp, AppColors.Accent.copy(alpha = 0.4f), shape).padding(18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("⏰", fontSize = 34.sp)
            Text("Время вышло!", color = AppColors.Text, fontSize = 19.sp, fontWeight = FontWeight.ExtraBold)
            Text("Ты читал $minutes мин. Отличная работа 🎉", color = AppColors.Muted, fontSize = 13.5.sp)
            Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val b = appCorner(16)
                Box(
                    Modifier.weight(1f).height(48.dp).liveClick(b, sound = FeedbackSound.Toggle) { app.readingTimer.extend(10) }.accentHighlight(b),
                    contentAlignment = Alignment.Center,
                ) { Text("Ещё 10 мин", color = accentContent, fontSize = 14.sp, fontWeight = FontWeight.Bold) }
                Box(
                    Modifier.weight(1f).height(48.dp).liveClick(b, sound = FeedbackSound.Close) { app.readingTimer.stop() }.border(1.dp, AppColors.Line, b),
                    contentAlignment = Alignment.Center,
                ) { Text("Хватит", color = AppColors.Text, fontSize = 14.sp, fontWeight = FontWeight.Bold) }
            }
        }
    }
}
