package com.flerty.wikiflerty.reader

import android.os.Parcel
import android.os.Parcelable
import org.readium.r2.navigator.Decoration
import org.readium.r2.navigator.html.HtmlDecorationTemplate
import org.readium.r2.navigator.html.toCss

data class TtsWordStyle(override val tint: Int) : Decoration.Style, Decoration.Style.Tinted {
    override fun describeContents(): Int = 0

    override fun writeToParcel(dest: Parcel, flags: Int) {
        dest.writeInt(tint)
    }

    companion object {
        @JvmField
        val CREATOR: Parcelable.Creator<TtsWordStyle> = object : Parcelable.Creator<TtsWordStyle> {
            override fun createFromParcel(source: Parcel): TtsWordStyle = TtsWordStyle(source.readInt())
            override fun newArray(size: Int): Array<TtsWordStyle?> = arrayOfNulls(size)
        }

        val template = HtmlDecorationTemplate(
            layout = HtmlDecorationTemplate.Layout.BOXES,
            element = { decoration ->
                val tint = (decoration.style as? Decoration.Style.Tinted)?.tint ?: 0
                """<div class="wf-tts-word" style="background-color: ${tint.toCss(alpha = 0.5)} !important; --wf-word-line: ${tint.toCss()};"/>"""
            },
            stylesheet = """
                .wf-tts-word {
                    margin: -1px -2px 0 0;
                    padding: 0 4px 3px 0;
                    border-radius: 4px;
                    box-sizing: border-box;
                    border-bottom: 2.5px solid var(--wf-word-line);
                    transition: opacity .12s ease;
                }
            """.trimIndent(),
        )
    }
}
