package com.flerty.wikiflerty.reader

import android.os.Parcel
import android.os.Parcelable
import org.readium.r2.navigator.Decoration
import org.readium.r2.navigator.html.HtmlDecorationTemplate
import org.readium.r2.navigator.html.toCss

data class TtsMark(override val tint: Int, val strength: Float, override val isActive: Boolean) :
    Decoration.Style, Decoration.Style.Tinted, Decoration.Style.Activable {
    override fun describeContents(): Int = 0

    override fun writeToParcel(dest: Parcel, flags: Int) {
        dest.writeInt(tint)
        dest.writeFloat(strength)
        dest.writeInt(if (isActive) 1 else 0)
    }

    companion object {
        @JvmField
        val CREATOR: Parcelable.Creator<TtsMark> = object : Parcelable.Creator<TtsMark> {
            override fun createFromParcel(source: Parcel) = TtsMark(source.readInt(), source.readFloat(), source.readInt() == 1)
            override fun newArray(size: Int): Array<TtsMark?> = arrayOfNulls(size)
        }

        val template = HtmlDecorationTemplate(
            layout = HtmlDecorationTemplate.Layout.BOXES,
            element = { d ->
                val mark = d.style as? TtsMark
                val tint = mark?.tint ?: 0
                val alpha = (mark?.strength ?: 0.3f).toDouble().coerceIn(0.05, 0.95)
                val line = if (mark?.isActive == true) "--wf-mark-line: ${tint.toCss()};" else "--wf-mark-line: transparent;"
                """<div class="wf-tts-mark" style="background-color: ${tint.toCss(alpha = alpha)} !important; $line"/>"""
            },
            stylesheet = """
                .wf-tts-mark { margin: 0 -1px 0 0; padding: 0 2px 0 0; border-radius: 3px; box-sizing: border-box; border: 0 solid var(--wf-mark-line); }
                [data-writing-mode="horizontal-tb"].wf-tts-mark { border-bottom-width: 2px; }
                [data-writing-mode="vertical-rl"].wf-tts-mark, [data-writing-mode="sideways-rl"].wf-tts-mark { border-left-width: 2px; }
                [data-writing-mode="vertical-lr"].wf-tts-mark, [data-writing-mode="sideways-lr"].wf-tts-mark { border-right-width: 2px; }
            """.trimIndent(),
        )
    }
}
