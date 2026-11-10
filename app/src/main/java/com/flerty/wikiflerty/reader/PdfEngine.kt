package com.flerty.wikiflerty.reader

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.Point
import android.graphics.RectF
import android.graphics.pdf.PdfRenderer
import android.graphics.pdf.PdfRendererPreV
import android.graphics.pdf.RenderParams
import android.graphics.pdf.models.selection.SelectionBoundary
import android.os.Build
import android.os.ParcelFileDescriptor
import android.os.ext.SdkExtensions
import androidx.annotation.RequiresApi
import androidx.annotation.RequiresExtension
import java.io.File

class PdfSelectionResult(val text: String, val rects: List<RectF>)

class PdfLink(val rects: List<RectF>, val page: Int?, val uri: String?)

interface PdfPageApi {
    val width: Int
    val height: Int
    fun render(bitmap: Bitmap)
    fun text(): String? = null
    fun search(query: String): List<List<RectF>> = emptyList()
    fun select(start: Point, stop: Point): PdfSelectionResult? = null
    fun links(): List<PdfLink> = emptyList()
    fun close()
}

interface PdfEngine {
    val count: Int
    val textSupported: Boolean
    fun page(index: Int): PdfPageApi
    fun close()

    companion object {
        @SuppressLint("NewApi")
        fun open(file: File): PdfEngine {
            if (Build.VERSION.SDK_INT >= 35) {
                val fd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
                return try {
                    ModernEngine(PdfRenderer(fd), fd)
                } catch (e: Throwable) {
                    runCatching { fd.close() }
                    throw e
                }
            }
            if (Build.VERSION.SDK_INT >= 31 && SdkExtensions.getExtensionVersion(Build.VERSION_CODES.S) >= 13) {
                val fd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
                val engine = runCatching { PreVEngine(PdfRendererPreV(fd), fd) }.getOrNull()
                if (engine != null) return engine
                runCatching { fd.close() }
            }
            val fd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            return try {
                LegacyEngine(PdfRenderer(fd), fd)
            } catch (e: Throwable) {
                runCatching { fd.close() }
                throw e
            }
        }
    }
}

private class LegacyEngine(private val renderer: PdfRenderer, private val fd: ParcelFileDescriptor) : PdfEngine {
    override val count: Int get() = renderer.pageCount
    override val textSupported: Boolean = false

    override fun page(index: Int): PdfPageApi {
        val p = renderer.openPage(index)
        return object : PdfPageApi {
            override val width = p.width
            override val height = p.height
            override fun render(bitmap: Bitmap) = p.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            override fun close() = p.close()
        }
    }

    override fun close() {
        runCatching { renderer.close() }
        runCatching { fd.close() }
    }
}

private fun joinText(parts: List<String>): String = parts.joinToString("") { it }.replace("\r\n", "\n")

@RequiresApi(35)
private class ModernEngine(private val renderer: PdfRenderer, private val fd: ParcelFileDescriptor) : PdfEngine {
    override val count: Int get() = renderer.pageCount
    override val textSupported: Boolean = true

    override fun page(index: Int): PdfPageApi {
        val p = renderer.openPage(index)
        return object : PdfPageApi {
            override val width = p.width
            override val height = p.height
            override fun render(bitmap: Bitmap) = p.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            override fun text(): String = joinText(p.textContents.map { it.text })
            override fun search(query: String): List<List<RectF>> = p.searchText(query).map { it.bounds }
            override fun select(start: Point, stop: Point): PdfSelectionResult? {
                val sel = p.selectContent(SelectionBoundary(start), SelectionBoundary(stop)) ?: return null
                val contents = sel.selectedTextContents
                val text = joinText(contents.map { it.text }).trim()
                if (text.isEmpty()) return null
                return PdfSelectionResult(text, contents.flatMap { it.bounds })
            }
            override fun links(): List<PdfLink> =
                p.gotoLinks.map { PdfLink(it.bounds, it.destination.pageNumber, null) } +
                    p.linkContents.map { PdfLink(it.bounds, null, it.uri.toString()) }
            override fun close() = p.close()
        }
    }

    override fun close() {
        runCatching { renderer.close() }
        runCatching { fd.close() }
    }
}

@RequiresApi(31)
@RequiresExtension(extension = Build.VERSION_CODES.S, version = 13)
private class PreVEngine(private val renderer: PdfRendererPreV, private val fd: ParcelFileDescriptor) : PdfEngine {
    override val count: Int get() = renderer.pageCount
    override val textSupported: Boolean = true
    private val params = RenderParams.Builder(RenderParams.RENDER_MODE_FOR_DISPLAY).build()

    override fun page(index: Int): PdfPageApi {
        val p = renderer.openPage(index)
        return object : PdfPageApi {
            override val width = p.width
            override val height = p.height
            override fun render(bitmap: Bitmap) = p.render(bitmap, null, null, params)
            override fun text(): String = joinText(p.textContents.map { it.text })
            override fun search(query: String): List<List<RectF>> = p.searchText(query).map { it.bounds }
            override fun select(start: Point, stop: Point): PdfSelectionResult? {
                val sel = p.selectContent(SelectionBoundary(start), SelectionBoundary(stop)) ?: return null
                val contents = sel.selectedTextContents
                val text = joinText(contents.map { it.text }).trim()
                if (text.isEmpty()) return null
                return PdfSelectionResult(text, contents.flatMap { it.bounds })
            }
            override fun links(): List<PdfLink> =
                p.gotoLinks.map { PdfLink(it.bounds, it.destination.pageNumber, null) } +
                    p.linkContents.map { PdfLink(it.bounds, null, it.uri.toString()) }
            override fun close() = p.close()
        }
    }

    override fun close() {
        runCatching { renderer.close() }
        runCatching { fd.close() }
    }
}
