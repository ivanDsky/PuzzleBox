package com.ivandsky.puzzlebox.gameui.hidden

import android.graphics.ColorMatrixColorFilter
import android.graphics.Shader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalDensity
import com.ivandsky.puzzlebox.designsystem.HiddenBoardTokens

/**
 * Draws what it wraps blurred past reading ([HiddenBoardTokens]): spread, washed out and a little larger than
 * its bounds, so the soft edge falls outside the slot that clips it. The wrapped content is never drawn sharp.
 *
 * On Android 12 and up the layer blurs itself. Below it, and wherever the layer is drawn without the GPU (a view
 * drawn into a bitmap ignores a layer's effect and would show the board as it is), the content is drawn into a
 * small picture and blurred there ([HiddenBoardPicture]). The layer keeps the result: the picture is made again
 * only when the content under it changes.
 */
@Composable
internal fun Modifier.hiddenBoardBlur(): Modifier {
    val sigmaPx = with(LocalDensity.current) { HiddenBoardTokens.Blur.toPx() }
    val wash = remember { washMatrix(HiddenBoardTokens.Saturation, HiddenBoardTokens.Contrast) }
    val effect = remember(sigmaPx, wash) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) HiddenBoardLayerBlur.effect(sigmaPx, wash) else null
    }
    val picture = remember { HiddenBoardPicture() }
    val washFilter = remember(wash) { ColorFilter.colorMatrix(wash) }
    return this
        .graphicsLayer {
            scaleX = HiddenBoardTokens.Scale
            scaleY = HiddenBoardTokens.Scale
            renderEffect = effect
        }
        .drawWithContent {
            if (effect != null && drawContext.canvas.nativeCanvas.isHardwareAccelerated) {
                drawContent()
            } else {
                with(picture) { drawBlurred(sigmaPx, washFilter) }
            }
        }
}

/**
 * CSS `saturate(s) contrast(c)` as one matrix: colour pulled toward grey, then every channel pulled toward mid grey.
 */
internal fun washMatrix(saturation: Float, contrast: Float): ColorMatrix {
    val matrix = ColorMatrix().apply { setToSaturation(saturation) }
    val lift = MID_GREY * (1f - contrast)
    for (row in 0..2) {
        for (column in 0..2) matrix[row, column] = matrix[row, column] * contrast
        matrix[row, 4] = lift
    }
    return matrix
}

private const val MID_GREY = 127.5f

/** The layer's own blur, which the platform has from Android 12. */
@RequiresApi(Build.VERSION_CODES.S)
private object HiddenBoardLayerBlur {
    // The platform turns the radius it is given into a standard deviation as 0.57735 * radius + 0.5.
    private const val SIGMA_PER_RADIUS = 0.57735f
    private const val SIGMA_BASE = 0.5f

    fun effect(sigmaPx: Float, wash: ColorMatrix): RenderEffect {
        val radius = ((sigmaPx - SIGMA_BASE) / SIGMA_PER_RADIUS).coerceAtLeast(0f)
        val blur = android.graphics.RenderEffect.createBlurEffect(radius, radius, Shader.TileMode.CLAMP)
        return android.graphics.RenderEffect
            .createColorFilterEffect(ColorMatrixColorFilter(wash.values), blur)
            .asComposeRenderEffect()
    }
}
