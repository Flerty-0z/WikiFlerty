package com.flerty.wikiflerty.ui

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.CompositionLocalProvider
import com.flerty.wikiflerty.App

class MainActivity : ComponentActivity() {
    private val libraryViewModel: LibraryViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null && !isTaskRoot && forwardToOwnTask(intent)) {
            finish()
            return
        }
        HighRefresh.apply(window)
        enableEdgeToEdge()
        if (savedInstanceState == null) holdFirstFrame()
        setContent {
            CompositionLocalProvider(LocalFeedback provides (application as App).feedback) {
                WikiFlertyTheme {
                    ThemeSwapHost { MainScreen(libraryViewModel) }
                }
            }
        }
        if (savedInstanceState == null) handleIncomingBook(intent)
    }

    private fun holdFirstFrame() {
        val warm = (application as App).coversWarm
        if (warm.isCompleted) return
        val until = android.os.SystemClock.uptimeMillis() + 700
        val content = findViewById<android.view.View>(android.R.id.content)
        content.viewTreeObserver.addOnPreDrawListener(object : android.view.ViewTreeObserver.OnPreDrawListener {
            override fun onPreDraw(): Boolean {
                val ready = warm.isCompleted || android.os.SystemClock.uptimeMillis() >= until
                if (ready) {
                    content.viewTreeObserver.removeOnPreDrawListener(this)
                } else {
                    content.postDelayed({ content.invalidate() }, 16)
                }
                return ready
            }
        })
    }

    private fun forwardToOwnTask(source: Intent?): Boolean {
        val action = source?.action ?: return false
        if (action != Intent.ACTION_VIEW && action != Intent.ACTION_SEND) return false
        val forward = Intent(source).setClass(this, MainActivity::class.java)
            .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        return runCatching { startActivity(forward) }.isSuccess
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIncomingBook(intent)
    }

    private fun handleIncomingBook(intent: Intent?) {
        if (intent != null && intent.action == com.flerty.wikiflerty.widget.QuoteWidget.ACTION_OPEN_NOTE) {
            val id = intent.getStringExtra(com.flerty.wikiflerty.widget.QuoteWidget.EXTRA_NOTE_ID)
            val app = applicationContext as com.flerty.wikiflerty.App
            app.notes.notes.value.firstOrNull { it.id == id }?.let { libraryViewModel.openNote(it) }
            intent.setAction(null)
            return
        }
        if (intent != null && intent.action == com.flerty.wikiflerty.widget.ReadingWidget.ACTION_OPEN_BOOK) {
            intent.getStringExtra(com.flerty.wikiflerty.widget.ReadingWidget.EXTRA_BOOK_ID)?.let { libraryViewModel.openById(it) }
            intent.setAction(null)
            return
        }
        if (intent?.action == Intent.ACTION_SEND_MULTIPLE) {
            val many: List<Uri> = if (Build.VERSION.SDK_INT >= 33) {
                intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java).orEmpty()
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM).orEmpty()
            }
            if (many.isNotEmpty()) libraryViewModel.importAll(many)
            intent.setAction(null)
            return
        }
        val uri: Uri? = when (intent?.action) {
            Intent.ACTION_VIEW -> intent.data
            Intent.ACTION_SEND -> if (Build.VERSION.SDK_INT >= 33) {
                intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(Intent.EXTRA_STREAM)
            }
            else -> null
        }
        if (uri != null) {
            val open = (applicationContext as com.flerty.wikiflerty.App).libraryPrefs.prefs.value.openAfterAdd
            libraryViewModel.import(uri, openAfterImport = open)
            intent.setAction(null)
        }
    }
}
