package com.flerty.wikiflerty.reader

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.flerty.wikiflerty.App
import com.flerty.wikiflerty.ui.AppColors
import com.flerty.wikiflerty.ui.PetCanvas
import kotlinx.coroutines.delay

@Composable
fun PetPeek(modifier: Modifier = Modifier) {
    val app = LocalContext.current.applicationContext as App
    val pet by app.pet.pet.collectAsStateWithLifecycle()
    val show = remember { Animatable(0f) }
    var gained by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        app.pet.peek.collect { xp ->
            gained = xp
            show.animateTo(1f, spring(dampingRatio = 0.55f, stiffness = 260f))
            delay(2200)
            show.animateTo(0f, tween(320))
        }
    }
    val p = pet ?: return
    if (show.value <= 0.01f) return
    Column(
        modifier.graphicsLayer {
            translationY = (1f - show.value) * 140.dp.toPx()
            alpha = show.value.coerceIn(0f, 1f)
        },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.clip(CircleShape).background(AppColors.Panel).padding(horizontal = 10.dp, vertical = 4.dp),
        ) {
            Text("+$gained ✨", color = AppColors.Accent, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold)
        }
        PetCanvas(p.species.id, app.pet.look(app.pet.mood(p)), 76.dp)
    }
}
