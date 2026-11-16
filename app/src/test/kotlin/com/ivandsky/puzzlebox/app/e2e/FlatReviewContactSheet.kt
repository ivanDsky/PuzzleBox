package com.ivandsky.puzzlebox.app.e2e

import java.awt.Color
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

/**
 * One contact sheet per screen, `<outDir>/sheets/<screen>.png`: a row per width and theme (phone light, phone
 * dark, tablet light, tablet dark), flat off on the left and flat on on the right, every row scaled to the same
 * height so a phone and a tablet sit on one sheet.
 */
internal object FlatReviewContactSheet {

    fun build(screen: String, outDir: File) {
        val rows = ReviewWidth.entries.flatMap { width ->
            listOf(false, true).map { dark ->
                listOf(false, true).map { flat ->
                    ImageIO.read(File(outDir, ReviewVariant(width, dark, flat).fileName(screen)))
                }
            }
        }
        val scaled = rows.map { pair -> pair.map(::toRowHeight) }
        val width = scaled.maxOf { (off, on) -> off.width + GAP + on.width } + 2 * MARGIN
        val height = scaled.size * ROW_HEIGHT + (scaled.size + 1) * MARGIN
        val sheet = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
        val canvas = sheet.createGraphics()
        canvas.color = BACKDROP
        canvas.fillRect(0, 0, width, height)
        scaled.forEachIndexed { index, (off, on) ->
            val y = MARGIN + index * (ROW_HEIGHT + MARGIN)
            canvas.drawImage(off, MARGIN, y, null)
            canvas.drawImage(on, MARGIN + off.width + GAP, y, null)
        }
        canvas.dispose()
        File(outDir, "sheets").apply { mkdirs() }.let { ImageIO.write(sheet, "png", File(it, "$screen.png")) }
    }

    private fun toRowHeight(image: BufferedImage): BufferedImage {
        val scaled = BufferedImage(image.width * ROW_HEIGHT / image.height, ROW_HEIGHT, BufferedImage.TYPE_INT_RGB)
        val canvas = scaled.createGraphics()
        canvas.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC)
        canvas.drawImage(image, 0, 0, scaled.width, scaled.height, null)
        canvas.dispose()
        return scaled
    }

    private const val ROW_HEIGHT = 1000
    private const val GAP = 40
    private const val MARGIN = 40
    private val BACKDROP = Color(0x80, 0x80, 0x80)
}
