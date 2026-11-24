package com.flerty.wikiflerty.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

object AccountAvatar {
    private val _version = MutableStateFlow(0L)
    val version = _version.asStateFlow()

    fun file(context: Context) = File(context.filesDir, "account_avatar.jpg")

    fun set(context: Context, uri: Uri): Boolean = runCatching {
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (minOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= 320) sample *= 2
        val bitmap = resolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: return@runCatching false
        val side = minOf(bitmap.width, bitmap.height)
        val square = Bitmap.createBitmap(bitmap, (bitmap.width - side) / 2, (bitmap.height - side) / 2, side, side)
        val scaled = if (side > 320) Bitmap.createScaledBitmap(square, 320, 320, true) else square
        val target = file(context)
        val temp = File(target.path + ".tmp")
        temp.outputStream().use { scaled.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        temp.renameTo(target)
        _version.value = System.currentTimeMillis()
        true
    }.getOrDefault(false)

    fun clear(context: Context) {
        file(context).delete()
        _version.value = System.currentTimeMillis()
    }
}

@Composable
fun AccountAvatarBox(size: Dp, onClick: () -> Unit, onLongClick: (() -> Unit)? = null) {
    val context = LocalContext.current
    val version by AccountAvatar.version.collectAsStateWithLifecycle()
    var image by remember { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(version) {
        image = withContext(Dispatchers.IO) {
            AccountAvatar.file(context).takeIf { it.exists() }?.let { f ->
                runCatching { BitmapFactory.decodeFile(f.path)?.asImageBitmap() }.getOrNull()
            }
        }
    }
    Box(
        Modifier
            .size(size)
            .liveClick(CircleShape, sound = FeedbackSound.Photo, pressedScale = 0.92f, onLongClick = onLongClick, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Crossfade(image, animationSpec = Motion.fade(260), label = "account-avatar") { bmp ->
            if (bmp != null) {
                Image(
                    bmp,
                    null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(CircleShape)
                        .border(1.5.dp, AppColors.Accent.copy(alpha = 0.45f), CircleShape),
                )
            } else {
                SettingIcon(AppIcons.Person, size)
            }
        }
    }
}

@Composable
fun AvatarEditMarker(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val version by AccountAvatar.version.collectAsStateWithLifecycle()
    val has = remember(version) { AccountAvatar.file(context).exists() }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) scope.launch {
            val ok = withContext(Dispatchers.IO) { AccountAvatar.set(context, uri) }
            if (!ok) Toast.makeText(context, "Не удалось открыть картинку", Toast.LENGTH_SHORT).show()
        }
    }
    Box(
        modifier
            .size(34.dp)
            .liveClick(appCorner(11), sound = FeedbackSound.Photo, pressedScale = 0.9f, onLongClick = if (has) {
                {
                    AccountAvatar.clear(context)
                    Toast.makeText(context, "Аватар убран", Toast.LENGTH_SHORT).show()
                }
            } else null) { pick.launch("image/*") }
            .background(AppColors.AccentSoft, appCorner(11))
            .border(1.dp, AppColors.Accent.copy(alpha = 0.3f), appCorner(11)),
        contentAlignment = Alignment.Center,
    ) { Icon(AppIcons.Pencil, "Сменить аватар", tint = AppColors.Accent, modifier = Modifier.size(17.dp)) }
}
