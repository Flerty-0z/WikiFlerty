package com.flerty.wikiflerty.pet

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import androidx.core.graphics.PathParser
import kotlin.math.sin

class PetShape private constructor(
    val tag: String,
    val ellipse: FloatArray?,
    val pathData: String?,
    val fill: Int,
    val stroke: Int,
    val strokeWidth: Float,
) {
    val path: Path? by lazy { pathData?.let { PathParser.createPathFromPathData(it) } }

    companion object {
        fun e(tag: String, cx: Float, cy: Float, rx: Float, ry: Float, fill: Long, rot: Float, stroke: Long, sw: Float) =
            PetShape(tag, floatArrayOf(cx, cy, rx, ry, rot), null, fill.toInt(), stroke.toInt(), sw)

        fun p(tag: String, d: String, fill: Long, stroke: Long, sw: Float) = PetShape(tag, null, d, fill.toInt(), stroke.toInt(), sw)
    }
}

enum class PetLook { Happy, Sad, Sleep, Tired }

object PetArt {
    private val shapes: Map<String, List<PetShape>> = mapOf(
    "cat" to listOf(
        PetShape.p("body", "M68 82 Q90 82 86 62 Q84 54 90 50", 0L, 0xFFF2A65AL, 7.0f),
        PetShape.p("body", "M87 55 Q88 51 90 50", 0L, 0xFFD9823AL, 7.0f),
        PetShape.e("body", 50.0f, 73.0f, 21.0f, 17.0f, 0xFFF2A65AL, 0.0f, 0L, 0.0f),
        PetShape.e("body", 50.0f, 77.0f, 12.5f, 10.5f, 0xFFFFE3C4L, 0.0f, 0L, 0.0f),
        PetShape.e("body", 39.0f, 89.0f, 7.0f, 4.0f, 0xFFD9823AL, 0.0f, 0L, 0.0f),
        PetShape.e("body", 61.0f, 89.0f, 7.0f, 4.0f, 0xFFD9823AL, 0.0f, 0L, 0.0f),
        PetShape.e("body", 30.0f, 74.0f, 5.0f, 8.0f, 0xFFF2A65AL, 20.0f, 0L, 0.0f),
        PetShape.e("body", 70.0f, 74.0f, 5.0f, 8.0f, 0xFFF2A65AL, -20.0f, 0L, 0.0f),
        PetShape.p("head", "M28 30 L31 10 L45 23 Z", 0xFFF2A65AL, 0L, 0.0f),
        PetShape.p("head", "M31 26 L33 15 L41 23 Z", 0xFFFFC2C8L, 0L, 0.0f),
        PetShape.p("head", "M72 30 L69 10 L55 23 Z", 0xFFF2A65AL, 0L, 0.0f),
        PetShape.p("head", "M69 26 L67 15 L59 23 Z", 0xFFFFC2C8L, 0L, 0.0f),
        PetShape.e("head", 50.0f, 42.0f, 26.0f, 22.5f, 0xFFF2A65AL, 0.0f, 0L, 0.0f),
        PetShape.p("head", "M44 21 Q46 25 45 28", 0L, 0xFFD9823AL, 2.2f),
        PetShape.p("head", "M50 20 L50 27", 0L, 0xFFD9823AL, 2.2f),
        PetShape.p("head", "M56 21 Q54 25 55 28", 0L, 0xFFD9823AL, 2.2f),
        PetShape.p("head", "M30 49 L20 47 M30 52 L20 53 M70 49 L80 47 M70 52 L80 53", 0L, 0xAAD9823AL, 1.2f),
        PetShape.e("eye", 40.0f, 42.0f, 4.2f, 5.2f, 0xFF2B2320L, 0.0f, 0L, 0.0f),
        PetShape.e("shine", 41.4f, 40.0f, 1.4f, 1.6f, 0xFFFFFFFFL, 0.0f, 0L, 0.0f),
        PetShape.e("eye", 60.0f, 42.0f, 4.2f, 5.2f, 0xFF2B2320L, 0.0f, 0L, 0.0f),
        PetShape.e("shine", 61.4f, 40.0f, 1.4f, 1.6f, 0xFFFFFFFFL, 0.0f, 0L, 0.0f),
        PetShape.p("head", "M47.2 47.4 Q50 46.2 52.8 47.4 Q52.4 50 50 50.8 Q47.6 50 47.2 47.4 Z", 0xFF2B2320L, 0L, 0.0f),
        PetShape.e("cheek", 33.0f, 51.0f, 4.6f, 2.8f, 0x99FF9AA8L, 0.0f, 0L, 0.0f),
        PetShape.e("cheek", 67.0f, 51.0f, 4.6f, 2.8f, 0x99FF9AA8L, 0.0f, 0L, 0.0f),
    ),
    "dog" to listOf(
        PetShape.p("body", "M68 76 Q84 70 82 56", 0L, 0xFFD9A066L, 6.0f),
        PetShape.e("body", 50.0f, 73.0f, 21.0f, 17.0f, 0xFFD9A066L, 0.0f, 0L, 0.0f),
        PetShape.e("body", 50.0f, 77.0f, 12.5f, 10.5f, 0xFFFFF1DFL, 0.0f, 0L, 0.0f),
        PetShape.e("body", 39.0f, 89.0f, 7.0f, 4.0f, 0xFFA8713EL, 0.0f, 0L, 0.0f),
        PetShape.e("body", 61.0f, 89.0f, 7.0f, 4.0f, 0xFFA8713EL, 0.0f, 0L, 0.0f),
        PetShape.e("body", 30.0f, 74.0f, 5.0f, 8.0f, 0xFFD9A066L, 20.0f, 0L, 0.0f),
        PetShape.e("body", 70.0f, 74.0f, 5.0f, 8.0f, 0xFFD9A066L, -20.0f, 0L, 0.0f),
        PetShape.e("head", 50.0f, 42.0f, 26.0f, 22.5f, 0xFFD9A066L, 0.0f, 0L, 0.0f),
        PetShape.e("head", 50.0f, 50.0f, 11.0f, 8.0f, 0xFFFFF1DFL, 0.0f, 0L, 0.0f),
        PetShape.p("head", "M26 30 Q16 34 19 54 Q22 62 29 56 Q30 44 33 34 Z", 0xFFA8713EL, 0L, 0.0f),
        PetShape.p("head", "M74 30 Q84 34 81 54 Q78 62 71 56 Q70 44 67 34 Z", 0xFFA8713EL, 0L, 0.0f),
        PetShape.e("head", 60.0f, 36.0f, 7.0f, 6.0f, 0xFFFFF1DFL, 0.0f, 0L, 0.0f),
        PetShape.e("eye", 40.0f, 42.0f, 4.2f, 5.2f, 0xFF2B2320L, 0.0f, 0L, 0.0f),
        PetShape.e("shine", 41.4f, 40.0f, 1.4f, 1.6f, 0xFFFFFFFFL, 0.0f, 0L, 0.0f),
        PetShape.e("eye", 60.0f, 42.0f, 4.2f, 5.2f, 0xFF2B2320L, 0.0f, 0L, 0.0f),
        PetShape.e("shine", 61.4f, 40.0f, 1.4f, 1.6f, 0xFFFFFFFFL, 0.0f, 0L, 0.0f),
        PetShape.p("head", "M47.2 47.4 Q50 46.2 52.8 47.4 Q52.4 50 50 50.8 Q47.6 50 47.2 47.4 Z", 0xFF2B2320L, 0L, 0.0f),
        PetShape.e("cheek", 33.0f, 51.0f, 4.6f, 2.8f, 0x99FF9AA8L, 0.0f, 0L, 0.0f),
        PetShape.e("cheek", 67.0f, 51.0f, 4.6f, 2.8f, 0x99FF9AA8L, 0.0f, 0L, 0.0f),
    ),
    "owl" to listOf(
        PetShape.e("body", 50.0f, 62.0f, 27.0f, 30.0f, 0xFF9B7653L, 0.0f, 0L, 0.0f),
        PetShape.e("body", 50.0f, 70.0f, 17.0f, 19.0f, 0xFFF1DEC0L, 0.0f, 0L, 0.0f),
        PetShape.p("body", "M42 66 q2 2 4 0 M54 66 q2 2 4 0 M46 74 q2 2 4 0 M50 80 q2 2 4 0 M42 81 q2 2 4 0", 0L, 0xAA9B7653L, 1.4f),
        PetShape.e("body", 24.0f, 64.0f, 7.0f, 15.0f, 0xFF6E4F35L, 12.0f, 0L, 0.0f),
        PetShape.e("body", 76.0f, 64.0f, 7.0f, 15.0f, 0xFF6E4F35L, -12.0f, 0L, 0.0f),
        PetShape.e("body", 42.0f, 91.0f, 5.0f, 2.6f, 0xFFF2A23AL, 0.0f, 0L, 0.0f),
        PetShape.e("body", 58.0f, 91.0f, 5.0f, 2.6f, 0xFFF2A23AL, 0.0f, 0L, 0.0f),
        PetShape.p("head", "M27 30 L28 14 L39 26 Z", 0xFF6E4F35L, 0L, 0.0f),
        PetShape.p("head", "M73 30 L72 14 L61 26 Z", 0xFF6E4F35L, 0L, 0.0f),
        PetShape.e("head", 39.0f, 41.0f, 11.0f, 11.0f, 0xFFFFF6E6L, 0.0f, 0L, 0.0f),
        PetShape.e("head", 61.0f, 41.0f, 11.0f, 11.0f, 0xFFFFF6E6L, 0.0f, 0L, 0.0f),
        PetShape.e("head", 39.0f, 41.0f, 11.0f, 11.0f, 0L, 0.0f, 0xFF6E4F35L, 1.6f),
        PetShape.e("head", 61.0f, 41.0f, 11.0f, 11.0f, 0L, 0.0f, 0xFF6E4F35L, 1.6f),
        PetShape.e("eye", 39.0f, 41.0f, 5.0f, 5.6f, 0xFF2B2320L, 0.0f, 0L, 0.0f),
        PetShape.e("shine", 40.4f, 39.0f, 1.4f, 1.6f, 0xFFFFFFFFL, 0.0f, 0L, 0.0f),
        PetShape.e("eye", 61.0f, 41.0f, 5.0f, 5.6f, 0xFF2B2320L, 0.0f, 0L, 0.0f),
        PetShape.e("shine", 62.4f, 39.0f, 1.4f, 1.6f, 0xFFFFFFFFL, 0.0f, 0L, 0.0f),
        PetShape.p("head", "M46.5 47 L53.5 47 L50 54 Z", 0xFFF2A23AL, 0L, 0.0f),
        PetShape.e("cheek", 30.0f, 53.0f, 4.6f, 2.8f, 0x99FF9AA8L, 0.0f, 0L, 0.0f),
        PetShape.e("cheek", 70.0f, 53.0f, 4.6f, 2.8f, 0x99FF9AA8L, 0.0f, 0L, 0.0f),
    ),
    "parrot" to listOf(
        PetShape.p("body", "M44 84 L40 98 L48 98 L50 86 Z", 0xFF3B7DD8L, 0L, 0.0f),
        PetShape.p("body", "M56 84 L60 98 L52 98 L50 86 Z", 0xFFE8473CL, 0L, 0.0f),
        PetShape.e("body", 50.0f, 64.0f, 23.0f, 26.0f, 0xFF5CC46BL, 0.0f, 0L, 0.0f),
        PetShape.e("body", 50.0f, 72.0f, 14.0f, 15.0f, 0xFFBFF0B0L, 0.0f, 0L, 0.0f),
        PetShape.e("body", 27.0f, 66.0f, 7.0f, 15.0f, 0xFF3B7DD8L, 14.0f, 0L, 0.0f),
        PetShape.e("body", 73.0f, 66.0f, 7.0f, 15.0f, 0xFF3B7DD8L, -14.0f, 0L, 0.0f),
        PetShape.e("body", 26.0f, 76.0f, 5.0f, 6.0f, 0xFFE8473CL, 14.0f, 0L, 0.0f),
        PetShape.e("body", 74.0f, 76.0f, 5.0f, 6.0f, 0xFFE8473CL, -14.0f, 0L, 0.0f),
        PetShape.e("body", 43.0f, 90.0f, 4.5f, 2.6f, 0xFF8A8F98L, 0.0f, 0L, 0.0f),
        PetShape.e("body", 57.0f, 90.0f, 4.5f, 2.6f, 0xFF8A8F98L, 0.0f, 0L, 0.0f),
        PetShape.p("head", "M44 22 Q42 8 50 6 Q46 14 50 20 Z", 0xFFE8473CL, 0L, 0.0f),
        PetShape.p("head", "M50 20 Q52 6 60 8 Q54 14 54 21 Z", 0xFFFFC94DL, 0L, 0.0f),
        PetShape.e("head", 50.0f, 40.0f, 22.0f, 20.0f, 0xFF5CC46BL, 0.0f, 0L, 0.0f),
        PetShape.e("head", 40.0f, 40.0f, 7.5f, 7.5f, 0xFFFFFFFFL, 0.0f, 0L, 0.0f),
        PetShape.e("head", 60.0f, 40.0f, 7.5f, 7.5f, 0xFFFFFFFFL, 0.0f, 0L, 0.0f),
        PetShape.e("eye", 40.0f, 40.0f, 3.8f, 4.6f, 0xFF2B2320L, 0.0f, 0L, 0.0f),
        PetShape.e("shine", 41.4f, 38.0f, 1.4f, 1.6f, 0xFFFFFFFFL, 0.0f, 0L, 0.0f),
        PetShape.e("eye", 60.0f, 40.0f, 3.8f, 4.6f, 0xFF2B2320L, 0.0f, 0L, 0.0f),
        PetShape.e("shine", 61.4f, 38.0f, 1.4f, 1.6f, 0xFFFFFFFFL, 0.0f, 0L, 0.0f),
        PetShape.p("head", "M44 47 Q50 43 56 47 Q57 54 50 58 Q49 53 46 52 Q44 50 44 47 Z", 0xFFF5D06BL, 0L, 0.0f),
        PetShape.p("head", "M47 52 Q50 57 50 58", 0L, 0xFFB8932EL, 1.2f),
        PetShape.e("cheek", 33.0f, 49.0f, 4.6f, 2.8f, 0x99FF9AA8L, 0.0f, 0L, 0.0f),
        PetShape.e("cheek", 67.0f, 49.0f, 4.6f, 2.8f, 0x99FF9AA8L, 0.0f, 0L, 0.0f),
    ),
    "raccoon" to listOf(
        PetShape.p("body", "M66 84 Q88 86 88 64", 0L, 0xFF9AA0A8L, 8.0f),
        PetShape.p("body", "M77 82.5 L81 76.5 M84 75 L87 69", 0L, 0xFF4A4F57L, 8.4f),
        PetShape.e("body", 50.0f, 73.0f, 21.0f, 17.0f, 0xFF9AA0A8L, 0.0f, 0L, 0.0f),
        PetShape.e("body", 50.0f, 77.0f, 12.5f, 10.5f, 0xFFEDEFF2L, 0.0f, 0L, 0.0f),
        PetShape.e("body", 39.0f, 89.0f, 7.0f, 4.0f, 0xFF4A4F57L, 0.0f, 0L, 0.0f),
        PetShape.e("body", 61.0f, 89.0f, 7.0f, 4.0f, 0xFF4A4F57L, 0.0f, 0L, 0.0f),
        PetShape.e("body", 30.0f, 74.0f, 5.0f, 8.0f, 0xFF9AA0A8L, 20.0f, 0L, 0.0f),
        PetShape.e("body", 70.0f, 74.0f, 5.0f, 8.0f, 0xFF9AA0A8L, -20.0f, 0L, 0.0f),
        PetShape.e("head", 31.0f, 24.0f, 7.5f, 8.5f, 0xFF9AA0A8L, -20.0f, 0L, 0.0f),
        PetShape.e("head", 31.0f, 25.0f, 4.0f, 5.0f, 0xFFEDEFF2L, -20.0f, 0L, 0.0f),
        PetShape.e("head", 69.0f, 24.0f, 7.5f, 8.5f, 0xFF9AA0A8L, 20.0f, 0L, 0.0f),
        PetShape.e("head", 69.0f, 25.0f, 4.0f, 5.0f, 0xFFEDEFF2L, 20.0f, 0L, 0.0f),
        PetShape.e("head", 50.0f, 42.0f, 26.0f, 22.5f, 0xFF9AA0A8L, 0.0f, 0L, 0.0f),
        PetShape.e("head", 50.0f, 50.0f, 11.0f, 8.0f, 0xFFEDEFF2L, 0.0f, 0L, 0.0f),
        PetShape.p("head", "M27 44 Q30 34 41 37 Q48 39 50 42 Q52 39 59 37 Q70 34 73 44 Q70 50 60 48 Q52 46 50 47 Q48 46 40 48 Q30 50 27 44 Z", 0xFF4A4F57L, 0L, 0.0f),
        PetShape.e("head", 50.0f, 34.0f, 3.0f, 9.0f, 0xFFEDEFF2L, 0.0f, 0L, 0.0f),
        PetShape.e("eye", 40.0f, 42.0f, 4.2f, 5.2f, 0xFF1A1614L, 0.0f, 0L, 0.0f),
        PetShape.e("shine", 41.4f, 40.0f, 1.4f, 1.6f, 0xFFFFFFFFL, 0.0f, 0L, 0.0f),
        PetShape.e("eye", 60.0f, 42.0f, 4.2f, 5.2f, 0xFF1A1614L, 0.0f, 0L, 0.0f),
        PetShape.e("shine", 61.4f, 40.0f, 1.4f, 1.6f, 0xFFFFFFFFL, 0.0f, 0L, 0.0f),
        PetShape.p("head", "M47.2 47.4 Q50 46.2 52.8 47.4 Q52.4 50 50 50.8 Q47.6 50 47.2 47.4 Z", 0xFF2B2320L, 0L, 0.0f),
        PetShape.e("cheek", 33.0f, 51.0f, 4.6f, 2.8f, 0x99FF9AA8L, 0.0f, 0L, 0.0f),
        PetShape.e("cheek", 67.0f, 51.0f, 4.6f, 2.8f, 0x99FF9AA8L, 0.0f, 0L, 0.0f),
    ),
    "bear" to listOf(
        PetShape.e("body", 50.0f, 73.0f, 21.0f, 17.0f, 0xFFA8744AL, 0.0f, 0L, 0.0f),
        PetShape.e("body", 50.0f, 77.0f, 12.5f, 10.5f, 0xFFE8C9A5L, 0.0f, 0L, 0.0f),
        PetShape.e("body", 39.0f, 89.0f, 7.0f, 4.0f, 0xFF7A5232L, 0.0f, 0L, 0.0f),
        PetShape.e("body", 61.0f, 89.0f, 7.0f, 4.0f, 0xFF7A5232L, 0.0f, 0L, 0.0f),
        PetShape.e("body", 30.0f, 74.0f, 5.0f, 8.0f, 0xFFA8744AL, 20.0f, 0L, 0.0f),
        PetShape.e("body", 70.0f, 74.0f, 5.0f, 8.0f, 0xFFA8744AL, -20.0f, 0L, 0.0f),
        PetShape.e("head", 30.0f, 24.0f, 8.0f, 8.0f, 0xFFA8744AL, 0.0f, 0L, 0.0f),
        PetShape.e("head", 30.0f, 24.5f, 4.2f, 4.2f, 0xFFE8C9A5L, 0.0f, 0L, 0.0f),
        PetShape.e("head", 70.0f, 24.0f, 8.0f, 8.0f, 0xFFA8744AL, 0.0f, 0L, 0.0f),
        PetShape.e("head", 70.0f, 24.5f, 4.2f, 4.2f, 0xFFE8C9A5L, 0.0f, 0L, 0.0f),
        PetShape.e("head", 50.0f, 42.0f, 26.0f, 22.5f, 0xFFA8744AL, 0.0f, 0L, 0.0f),
        PetShape.e("head", 50.0f, 50.0f, 11.0f, 8.0f, 0xFFE8C9A5L, 0.0f, 0L, 0.0f),
        PetShape.e("eye", 40.0f, 42.0f, 4.2f, 5.2f, 0xFF2B2320L, 0.0f, 0L, 0.0f),
        PetShape.e("shine", 41.4f, 40.0f, 1.4f, 1.6f, 0xFFFFFFFFL, 0.0f, 0L, 0.0f),
        PetShape.e("eye", 60.0f, 42.0f, 4.2f, 5.2f, 0xFF2B2320L, 0.0f, 0L, 0.0f),
        PetShape.e("shine", 61.4f, 40.0f, 1.4f, 1.6f, 0xFFFFFFFFL, 0.0f, 0L, 0.0f),
        PetShape.p("head", "M47.2 47.4 Q50 46.2 52.8 47.4 Q52.4 50 50 50.8 Q47.6 50 47.2 47.4 Z", 0xFF2B2320L, 0L, 0.0f),
        PetShape.e("cheek", 33.0f, 51.0f, 4.6f, 2.8f, 0x99FF9AA8L, 0.0f, 0L, 0.0f),
        PetShape.e("cheek", 67.0f, 51.0f, 4.6f, 2.8f, 0x99FF9AA8L, 0.0f, 0L, 0.0f),
    ),

    )

    private val eggColors = mapOf(
        "cat" to 0xFFF2A65A.toInt(), "dog" to 0xFFD9A066.toInt(), "owl" to 0xFF9B7653.toInt(),
        "parrot" to 0xFF5CC46B.toInt(), "raccoon" to 0xFF9AA0A8.toInt(), "bear" to 0xFFA8744A.toInt(),
    )
    private val mouthY = mapOf("cat" to 55f, "dog" to 56f, "raccoon" to 55f, "bear" to 56f)

    private val egg by lazy { PathParser.createPathFromPathData("M50 18 C68 18 78 50 78 66 C78 84 66 94 50 94 C34 94 22 84 22 66 C22 50 32 18 50 18 Z") }
    private val crack by lazy { PathParser.createPathFromPathData("M34 50 L40 46 L44 52 L50 47 L55 53 L61 48 L66 52") }
    private val crown by lazy { PathParser.createPathFromPathData("M38 20 L40 9 L45 15 L50 6 L55 15 L60 9 L62 20 Z") }

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val oval = RectF()
    private val tmp = Path()

    private fun scales(stage: PetStage): Triple<Float, Float, Float> = when (stage) {
        PetStage.Baby -> Triple(0.72f, 1.14f, 0.88f)
        PetStage.Teen -> Triple(0.86f, 1.06f, 1f)
        else -> Triple(1f, 1f, 1f)
    }

    @Synchronized
    fun draw(
        canvas: Canvas,
        species: String,
        stage: PetStage,
        look: PetLook,
        size: Float,
        blink: Float = 1f,
        breathe: Float = 0f,
        hatching: Boolean = false,
    ) {
        canvas.save()
        canvas.scale(size / 100f, size / 100f)
        if (stage == PetStage.Egg) {
            drawEgg(canvas, species, hatching, breathe)
            canvas.restore()
            return
        }
        val (whole, head, body) = scales(stage)
        if (stage == PetStage.Legend) {
            fill.shader = RadialGradient(50f, 55f, 52f, intArrayOf(0x88FFD76A.toInt(), 0x00FFD76A), null, Shader.TileMode.CLAMP)
            canvas.drawRect(-10f, -10f, 110f, 110f, fill)
            fill.shader = null
        }
        canvas.save()
        canvas.scale(whole, whole * (1f + 0.018f * breathe), 50f, 95f)
        val sleep = look == PetLook.Sleep
        val tired = look == PetLook.Tired
        val sad = look == PetLook.Sad
        for (s in shapes[species] ?: shapes.getValue("cat")) {
            canvas.save()
            when (s.tag) {
                "head", "eye", "shine", "cheek" -> canvas.scale(head, head, 50f, 60f)
                "body" -> canvas.scale(body, body, 50f, 92f)
            }
            val e = s.ellipse
            if (s.tag == "eye" && e != null && (sleep || tired)) {
                val (cx, cy, rx, ry) = listOf(e[0], e[1], e[2], e[3])
                if (tired) {
                    fill.color = s.fill
                    oval.set(cx - rx, cy + ry * 0.45f - ry * 0.5f, cx + rx, cy + ry * 0.45f + ry * 0.5f)
                    canvas.drawOval(oval, fill)
                }
                line.color = 0xFF2B2320.toInt()
                line.strokeWidth = 1.8f
                tmp.reset()
                tmp.moveTo(cx - rx, cy)
                tmp.quadTo(cx, cy + if (sleep) 3.5f else -1f, cx + rx, cy)
                canvas.drawPath(tmp, line)
            } else if (s.tag == "shine" && (sleep || tired)) {
                Unit
            } else {
                if ((s.tag == "eye" || s.tag == "shine") && e != null && blink < 1f) canvas.scale(1f, blink.coerceAtLeast(0.08f), e[0], e[1])
                drawShape(canvas, s)
            }
            if (s.tag == "eye" && sad && e != null) {
                val sgn = if (e[0] < 50f) 1f else -1f
                line.color = 0xFF2B2320.toInt()
                line.strokeWidth = 1.6f
                canvas.drawLine(e[0] - 5f * sgn, e[1] - 10f, e[0] + 4f * sgn, e[1] - 7.5f, line)
            }
            canvas.restore()
        }
        mouthY[species]?.let { m ->
            canvas.save()
            canvas.scale(head, head, 50f, 60f)
            line.color = 0xFF2B2320.toInt()
            line.strokeWidth = 1.6f
            tmp.reset()
            when {
                sad -> { tmp.moveTo(46f, m + 3f); tmp.quadTo(50f, m - 0.5f, 54f, m + 3f) }
                sleep -> tmp.addOval(RectF(48.4f, m + 0.3f, 51.6f, m + 2.7f), Path.Direction.CW)
                else -> { tmp.moveTo(45.5f, m); tmp.quadTo(47.8f, m + 3.2f, 50f, m); tmp.quadTo(52.2f, m + 3.2f, 54.5f, m) }
            }
            canvas.drawPath(tmp, line)
            canvas.restore()
        }
        canvas.restore()
        if (stage == PetStage.Legend) {
            fill.color = 0xFFFFC93C.toInt()
            canvas.drawPath(crown, fill)
            line.color = 0xFFC98A12.toInt()
            line.strokeWidth = 1f
            canvas.drawPath(crown, line)
            fill.color = 0xFFFF5E7A.toInt()
            canvas.drawCircle(50f, 15f, 1.8f, fill)
            fill.color = 0xFFFFE7A0.toInt()
            listOf(14f to 30f, 86f to 24f, 90f to 70f, 10f to 76f).forEachIndexed { i, (a, b) ->
                val k = 0.7f + 0.3f * sin(breathe * 3f + i)
                tmp.reset()
                tmp.moveTo(a, b - 4f * k); tmp.lineTo(a + 1.2f, b - 1.2f); tmp.lineTo(a + 4f * k, b); tmp.lineTo(a + 1.2f, b + 1.2f)
                tmp.lineTo(a, b + 4f * k); tmp.lineTo(a - 1.2f, b + 1.2f); tmp.lineTo(a - 4f * k, b); tmp.lineTo(a - 1.2f, b - 1.2f); tmp.close()
                canvas.drawPath(tmp, fill)
            }
        }
        if (sleep) {
            fill.color = 0xFFBFD3FF.toInt()
            fill.textSize = 10f
            fill.isFakeBoldText = true
            canvas.drawText("z", 76f, 26f, fill)
            fill.textSize = 14f
            canvas.drawText("Z", 82f, 16f, fill)
            fill.isFakeBoldText = false
        }
        canvas.restore()
    }

    private fun drawEgg(canvas: Canvas, species: String, hatching: Boolean, wobble: Float) {
        canvas.save()
        canvas.rotate(if (hatching) 6f * sin(wobble * 6f) else 2f * sin(wobble), 50f, 94f)
        fill.color = 0xFFFFF4E2.toInt()
        canvas.drawPath(egg, fill)
        canvas.save()
        canvas.clipPath(egg)
        fill.color = eggColors[species] ?: 0xFFF2A65A.toInt()
        listOf(floatArrayOf(40f, 40f, 7f, 9f), floatArrayOf(62f, 52f, 9f, 7f), floatArrayOf(44f, 72f, 10f, 8f), floatArrayOf(64f, 80f, 6f, 5f), floatArrayOf(30f, 58f, 5f, 6f)).forEach { o ->
            oval.set(o[0] - o[2], o[1] - o[3], o[0] + o[2], o[1] + o[3])
            canvas.drawOval(oval, fill)
        }
        fill.color = 0x14000000
        oval.set(36f, 44f, 80f, 96f)
        canvas.drawOval(oval, fill)
        canvas.restore()
        line.color = 0x22000000
        line.strokeWidth = 1.5f
        canvas.drawPath(egg, line)
        if (hatching) {
            line.color = 0xFF5A4A3A.toInt()
            line.strokeWidth = 1.6f
            canvas.drawPath(crack, line)
        }
        canvas.restore()
    }

    private fun drawShape(canvas: Canvas, s: PetShape) {
        val e = s.ellipse
        if (e != null) {
            canvas.save()
            if (e[4] != 0f) canvas.rotate(e[4], e[0], e[1])
            oval.set(e[0] - e[2], e[1] - e[3], e[0] + e[2], e[1] + e[3])
            if (s.fill != 0) { fill.color = s.fill; canvas.drawOval(oval, fill) }
            if (s.stroke != 0) { line.color = s.stroke; line.strokeWidth = s.strokeWidth; canvas.drawOval(oval, line) }
            canvas.restore()
        } else {
            val p = s.path ?: return
            if (s.fill != 0) { fill.color = s.fill; canvas.drawPath(p, fill) }
            if (s.stroke != 0) { line.color = s.stroke; line.strokeWidth = s.strokeWidth; canvas.drawPath(p, line) }
        }
    }

    fun bitmap(species: String, stage: PetStage, look: PetLook, px: Int): android.graphics.Bitmap {
        val bmp = android.graphics.Bitmap.createBitmap(px, px, android.graphics.Bitmap.Config.ARGB_8888)
        draw(Canvas(bmp), species, stage, look, px.toFloat())
        return bmp
    }
}
