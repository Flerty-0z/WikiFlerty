package com.flerty.wikiflerty.reader

import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.view.KeyEvent
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.ComposeView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.fragment.app.commitNow
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.compose.runtime.collectAsState
import com.flerty.wikiflerty.App
import com.flerty.wikiflerty.R
import com.flerty.wikiflerty.ui.WikiFlertyTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.readium.r2.navigator.epub.EpubNavigatorFactory
import org.readium.r2.navigator.epub.EpubNavigatorFragment
import org.readium.r2.navigator.epub.css.FontStyle
import org.readium.r2.navigator.epub.css.FontWeight
import org.readium.r2.navigator.input.InputListener
import org.readium.r2.navigator.input.TapEvent
import org.readium.r2.navigator.util.DirectionalNavigationAdapter
import org.readium.r2.shared.publication.Link
import org.readium.r2.shared.publication.services.positions
import org.readium.r2.shared.util.AbsoluteUrl

/**
 * The reading screen: the book (Readium) with our panels drawn on top.
 * Tap the middle of the page to show or hide the panels, the edges to turn pages.
 */
class ReaderActivity : AppCompatActivity(), EpubNavigatorFragment.Listener {

    private val app: App get() = application as App
    private val ui = ReaderUiState()
    private lateinit var navigator: EpubNavigatorFragment
    private lateinit var fonts: List<ReaderFont>

    private var edgeAdapter: DirectionalNavigationAdapter? = null
    private var appliedEdgeTaps: Boolean? = null
    private var appliedAnimated: Boolean? = null

    /** First tap while panels are open just closes them. */
    private val closePanelsListener = object : InputListener {
        override fun onTap(event: TapEvent): Boolean {
            if (!ui.panelsVisible && ui.sheet == null) return false
            ui.hideAll()
            return true
        }
    }

    /** A tap that was not on a page edge shows the panels. */
    private val showPanelsListener = object : InputListener {
        override fun onTap(event: TapEvent): Boolean {
            ui.panelsVisible = true
            return true
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        val session = app.readerSession

        if (session == null) {
            // Android closed the app in the background and the open book was lost.
            // Go back to the library; the reading position is already saved.
            supportFragmentManager.fragmentFactory = EpubNavigatorFragment.createDummyFactory()
            super.onCreate(savedInstanceState)
            finish()
            return
        }

        fonts = ReaderFont.available(this)
        val settings = app.readerSettings.settings.value

        // IMPORTANT: the fragment factory must be set before super.onCreate().
        supportFragmentManager.fragmentFactory = EpubNavigatorFactory(session.publication)
            .createFragmentFactory(
                initialLocator = session.initialLocator,
                initialPreferences = settings.toEpubPreferences(fonts),
                listener = this,
                configuration = navigatorConfiguration(),
            )

        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_reader)
        hideSystemBars()
        applyPageBackground(settings)

        if (savedInstanceState == null) {
            supportFragmentManager.commitNow {
                add(R.id.navigator_container, EpubNavigatorFragment::class.java, Bundle(), NAVIGATOR_TAG)
            }
        }
        navigator = supportFragmentManager.findFragmentByTag(NAVIGATOR_TAG) as EpubNavigatorFragment
        applyInputListeners(settings)

        val book = app.library.books.value.firstOrNull { it.id == session.bookId }
        val title = book?.title ?: session.publication.metadata.title.orEmpty()
        val toc = flattenToc(session.publication.tableOfContents)
        var cover by mutableStateOf<ImageBitmap?>(null)

        findViewById<ComposeView>(R.id.reader_overlay).setContent {
            WikiFlertyTheme {
                val current by app.readerSettings.settings.collectAsState()
                ReaderOverlay(
                    state = ui,
                    settings = current,
                    fonts = fonts,
                    title = title,
                    cover = cover,
                    toc = toc,
                    onSettingsChange = { transform -> app.readerSettings.update(transform) },
                    onTocSelect = { item -> navigator.go(item.link, animated = false) },
                    onBack = { finish() },
                    onNotes = {
                        Toast.makeText(this, "Заметки появятся в следующем обновлении", Toast.LENGTH_SHORT).show()
                    },
                )
            }
        }

        lifecycleScope.launch {
            book?.let { app.library.coverFile(it) }?.takeIf { it.exists() }?.let { file ->
                cover = withContext(Dispatchers.IO) {
                    BitmapFactory.decodeFile(file.absolutePath)?.asImageBitmap()
                }
            }
        }

        lifecycleScope.launch {
            ui.totalPositions = withContext(Dispatchers.Default) {
                runCatching { session.publication.positions().size }.getOrDefault(0)
            }
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    navigator.currentLocator.collect { locator ->
                        ui.progression = locator.locations.totalProgression ?: ui.progression
                        ui.position = locator.locations.position
                        ui.chapter = locator.title
                        app.library.saveProgress(session.bookId, locator)
                    }
                }
                launch {
                    app.readerSettings.settings.collect { s ->
                        navigator.submitPreferences(s.toEpubPreferences(fonts))
                        applyPageBackground(s)
                        applyInputListeners(s)
                    }
                }
            }
        }
    }

    /** Lets the book use the fonts bundled in assets/fonts. */
    private fun navigatorConfiguration() = EpubNavigatorFragment.Configuration {
        servedAssets += "fonts/.*"
        if (ReaderFont.Literata in fonts) {
            addFontFamilyDeclaration(ReaderFont.Literata.family!!) {
                addFontFace {
                    addSource("fonts/Literata.ttf", preload = true)
                    setFontStyle(FontStyle.NORMAL)
                    setFontWeight(200..900)
                }
                addFontFace {
                    addSource("fonts/Literata-Italic.ttf")
                    setFontStyle(FontStyle.ITALIC)
                    setFontWeight(200..900)
                }
            }
        }
        if (ReaderFont.PtSerif in fonts) {
            addFontFamilyDeclaration(ReaderFont.PtSerif.family!!) {
                addFontFace {
                    addSource("fonts/PTSerif-Regular.ttf", preload = true)
                    setFontStyle(FontStyle.NORMAL)
                    setFontWeight(FontWeight.NORMAL)
                }
                addFontFace {
                    addSource("fonts/PTSerif-Bold.ttf")
                    setFontStyle(FontStyle.NORMAL)
                    setFontWeight(FontWeight.BOLD)
                }
                addFontFace {
                    addSource("fonts/PTSerif-Italic.ttf")
                    setFontStyle(FontStyle.ITALIC)
                    setFontWeight(FontWeight.NORMAL)
                }
            }
        }
    }

    /** Re-adds the tap handlers when "tap the edges" or the animation setting changes. */
    private fun applyInputListeners(settings: ReaderSettings) {
        if (appliedEdgeTaps == settings.edgeTaps && appliedAnimated == settings.animatedTurns) return
        if (appliedEdgeTaps != null) {
            navigator.removeInputListener(closePanelsListener)
            edgeAdapter?.let { navigator.removeInputListener(it) }
            navigator.removeInputListener(showPanelsListener)
        }
        navigator.addInputListener(closePanelsListener)
        edgeAdapter = if (settings.edgeTaps) {
            DirectionalNavigationAdapter(navigator, animatedTransition = settings.animatedTurns)
                .also { navigator.addInputListener(it) }
        } else {
            null
        }
        navigator.addInputListener(showPanelsListener)
        appliedEdgeTaps = settings.edgeTaps
        appliedAnimated = settings.animatedTurns
    }

    private fun applyPageBackground(settings: ReaderSettings) {
        val color = settings.pageTheme.background.toInt()
        findViewById<android.view.View>(R.id.reader_root)?.setBackgroundColor(color)
        window.decorView.setBackgroundColor(color)
    }

    /** Volume keys turn pages when that option is on. */
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val settings = app.readerSettings.settings.value
        if (settings.volumeKeys && ::navigator.isInitialized &&
            (event.keyCode == KeyEvent.KEYCODE_VOLUME_DOWN || event.keyCode == KeyEvent.KEYCODE_VOLUME_UP)
        ) {
            if (event.action == KeyEvent.ACTION_DOWN) {
                if (event.keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
                    navigator.goForward(animated = settings.animatedTurns)
                } else {
                    navigator.goBackward(animated = settings.animatedTurns)
                }
            }
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    private fun flattenToc(links: List<Link>, depth: Int = 0): List<TocItem> =
        links.flatMap { link ->
            val title = link.title?.trim().orEmpty()
            val self = if (title.isNotEmpty()) listOf(TocItem(title, depth, link)) else emptyList()
            self + flattenToc(link.children, depth + 1)
        }

    private fun hideSystemBars() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowCompat.getInsetsController(window, window.decorView).apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.systemBars())
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isFinishing) app.closeReader()
    }

    override fun onExternalLinkActivated(url: AbsoluteUrl) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url.toString())))
        } catch (_: ActivityNotFoundException) {
        }
    }

    private companion object {
        const val NAVIGATOR_TAG = "navigator"
    }
}
