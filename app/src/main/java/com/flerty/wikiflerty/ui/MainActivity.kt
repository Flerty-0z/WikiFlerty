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
        enableEdgeToEdge()
        setContent {
            CompositionLocalProvider(LocalFeedback provides (application as App).feedback) {
                WikiFlertyTheme {
                    MainScreen(libraryViewModel)
                }
            }
        }
        if (savedInstanceState == null) handleIncomingBook(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIncomingBook(intent)
    }

    private fun handleIncomingBook(intent: Intent?) {
        if (intent != null && intent.action == com.flerty.wikiflerty.widget.ReadingWidget.ACTION_OPEN_BOOK) {
            intent.getStringExtra(com.flerty.wikiflerty.widget.ReadingWidget.EXTRA_BOOK_ID)?.let { libraryViewModel.openById(it) }
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
        if (uri != null) libraryViewModel.import(uri, openAfterImport = true)
    }
}
