package com.flerty.wikiflerty.studio

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import com.flerty.wikiflerty.ui.AppColors
import com.flerty.wikiflerty.ui.AppIcons
import com.flerty.wikiflerty.ui.FeedbackSound
import com.flerty.wikiflerty.ui.WikiFlertyTheme
import com.flerty.wikiflerty.ui.accentContent
import com.flerty.wikiflerty.ui.accentHighlight
import com.flerty.wikiflerty.ui.appCorner
import com.flerty.wikiflerty.ui.liveClick
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class StudioActivity : ComponentActivity() {
    private var scenarios by mutableStateOf<List<Scenario>>(emptyList())
    private var status by mutableStateOf("")
    private var prepared by mutableStateOf(false)
    private var driverOn by mutableStateOf(false)
    private var record by mutableStateOf(true)
    private var showTouches by mutableStateOf(true)
    private var pending: Scenario? = null

    private val capture = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val scenario = pending ?: return@registerForActivityResult
        val data = result.data
        if (result.resultCode == RESULT_OK && data != null) {
            StudioRecorder.start(this, result.resultCode, data)
            lifecycleScope.launch {
                delay(1200)
                go(scenario)
            }
        } else {
            status = "Запись экрана не разрешена"
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = getSharedPreferences("studio", MODE_PRIVATE)
        prepared = prefs.getBoolean("prepared", false)
        record = prefs.getBoolean("record", true)
        showTouches = prefs.getBoolean("touches", true)
        loadBundled()
        refresh()
        setContent { WikiFlertyTheme { Screen() } }
    }

    override fun onResume() {
        super.onResume()
        driverOn = StudioDriver.enabled(this)
    }

    private fun loadBundled() {
        val list = runCatching { assets.list("studio/scenarios")?.toList() }.getOrNull().orEmpty().filter { it.endsWith(".json") && it != "index.json" }.sorted()
        val cached = File(filesDir, "scenarios").listFiles()?.associateBy { it.name }.orEmpty()
        val names = (list + cached.keys).distinct().sorted()
        scenarios = names.mapNotNull { name ->
            val raw = cached[name]?.readText() ?: runCatching { assets.open("studio/scenarios/$name").bufferedReader().readText() }.getOrNull() ?: return@mapNotNull null
            Scenario.parse(name.removeSuffix(".json"), raw)
        }
    }

    private fun refresh() {
        lifecycleScope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    val base = "https://raw.githubusercontent.com/Flerty-0z/WikiFlerty/main/tools/studio/scenarios/"
                    val index = org.json.JSONArray(java.net.URL(base + "index.json?t=" + System.currentTimeMillis()).readText())
                    val dir = File(filesDir, "scenarios").apply { mkdirs() }
                    for (i in 0 until index.length()) {
                        val name = index.getString(i)
                        val text = java.net.URL(base + name + "?t=" + System.currentTimeMillis()).readText()
                        if (Scenario.parse(name, text) != null) File(dir, name).writeText(text)
                    }
                    true
                }.getOrDefault(false)
            }
            if (ok) loadBundled()
        }
    }

    private fun prepare() {
        status = "Готовлю книги и файлы…"
        lifecycleScope.launch {
            val result = Studio.prepare(this@StudioActivity)
            getSharedPreferences("studio", MODE_PRIVATE).edit().putBoolean("prepared", true).apply()
            prepared = true
            status = "Готово: книг ${result.first}, файлов ${result.second} (в «Загрузки / WikiFlerty Studio»)"
        }
    }

    private fun quiet() = Studio.quiet(this)

    private fun start(scenario: Scenario) {
        quiet()
        if (!StudioDriver.enabled(this) || StudioDriver.instance == null) {
            status = "Сначала включи службу сценариев"
            openAccessibility()
            return
        }
        if (record) {
            pending = scenario
            val mpm = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            capture.launch(mpm.createScreenCaptureIntent())
        } else {
            go(scenario)
        }
    }

    private fun go(scenario: Scenario) {
        val driver = StudioDriver.instance ?: return
        val context = applicationContext
        driver.run(scenario, showTouches) { ok, message ->
            StudioRecorder.stop(context)
            StudioDriver.toast(context, if (ok) "Сценарий «${scenario.title}» пройден" else "Сценарий остановлен. $message")
        }
    }

    private fun openAccessibility() {
        runCatching { startActivity(Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
    }

    @Composable
    private fun Screen() {
        Column(
            Modifier
                .fillMaxSize()
                .background(AppColors.Background)
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Сценарии", color = AppColors.Text, fontSize = 26.sp, fontWeight = FontWeight.Bold)
            Tile(
                AppIcons.Gesture,
                "Служба сценариев",
                if (driverOn) "Включена" else "Выключена — нажми, чтобы включить",
                if (driverOn) null else "Включить",
            ) { openAccessibility() }
            Tile(
                AppIcons.Book,
                "Книги и файлы",
                if (prepared) "Подготовлено. Можно повторить" else "Классика на полки и файлы для импорта",
                "Подготовить",
            ) { prepare() }
            Toggle(AppIcons.Video, "Записывать экран", record) {
                record = it
                getSharedPreferences("studio", MODE_PRIVATE).edit().putBoolean("record", it).apply()
            }
            Toggle(AppIcons.Gesture, "Показывать касания", showTouches) {
                showTouches = it
                getSharedPreferences("studio", MODE_PRIVATE).edit().putBoolean("touches", it).apply()
            }
            if (status.isNotEmpty()) Text(status, color = AppColors.Muted, fontSize = 13.sp)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Ролики", color = AppColors.Text, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Text(
                    "Обновить",
                    color = AppColors.Accent,
                    fontSize = 14.sp,
                    modifier = Modifier.liveClick(appCorner(10), sound = FeedbackSound.Tap) { refresh() }.padding(8.dp),
                )
            }
            if (scenarios.isEmpty()) Text("Сценариев пока нет", color = AppColors.Muted, fontSize = 14.sp)
            scenarios.forEach { s ->
                Tile(AppIcons.Play, s.title, s.note.ifEmpty { "${s.steps.size} шагов" }, "Запустить") { start(s) }
            }
            Spacer(Modifier.size(24.dp))
        }
    }

    @Composable
    private fun Tile(icon: ImageVector, title: String, text: String, action: String?, onClick: () -> Unit) {
        val shape = appCorner(16)
        Row(
            Modifier
                .fillMaxWidth()
                .border(1.dp, AppColors.PanelBorder, shape)
                .background(AppColors.Field, shape)
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(Modifier.size(38.dp).background(AppColors.AccentTint, appCorner(10)), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = AppColors.Accent, modifier = Modifier.size(18.dp))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, color = AppColors.Text, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                Text(text, color = AppColors.Muted, fontSize = 12.sp)
            }
            if (action != null) {
                Box(
                    Modifier
                        .liveClick(appCorner(12), sound = FeedbackSound.Open, onClick = onClick)
                        .accentHighlight(appCorner(12))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                ) { Text(action, color = accentContent, fontSize = 13.sp, fontWeight = FontWeight.Bold) }
            }
        }
    }

    @Composable
    private fun Toggle(icon: ImageVector, title: String, value: Boolean, onChange: (Boolean) -> Unit) {
        val shape = appCorner(16)
        Row(
            Modifier
                .fillMaxWidth()
                .border(1.dp, AppColors.PanelBorder, shape)
                .background(AppColors.Field, shape)
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(Modifier.size(38.dp).background(AppColors.AccentTint, appCorner(10)), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = AppColors.Accent, modifier = Modifier.size(18.dp))
            }
            Text(title, color = AppColors.Text, fontSize = 15.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Switch(checked = value, onCheckedChange = onChange)
        }
    }

    companion object {
        fun saveDownload(context: Context, name: String): Boolean = runCatching {
            if (Build.VERSION.SDK_INT >= 29) {
                val resolver = context.contentResolver
                val rel = Environment.DIRECTORY_DOWNLOADS + "/WikiFlerty Studio"
                resolver.query(
                    MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                    arrayOf(MediaStore.Downloads._ID),
                    "${MediaStore.Downloads.DISPLAY_NAME}=? AND ${MediaStore.Downloads.RELATIVE_PATH} LIKE ?",
                    arrayOf(name, "$rel%"),
                    null,
                )?.use { if (it.moveToFirst()) return true }
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, name)
                    put(MediaStore.Downloads.RELATIVE_PATH, rel)
                    put(MediaStore.Downloads.IS_PENDING, 1)
                }
                val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return false
                resolver.openOutputStream(uri)?.use { out -> context.assets.open("studio/files/$name").use { it.copyTo(out) } }
                resolver.update(uri, ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) }, null, null)
                true
            } else {
                val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "WikiFlerty Studio").apply { mkdirs() }
                context.assets.open("studio/files/$name").use { input -> File(dir, name).outputStream().use { input.copyTo(it) } }
                true
            }
        }.getOrDefault(false)
    }
}
