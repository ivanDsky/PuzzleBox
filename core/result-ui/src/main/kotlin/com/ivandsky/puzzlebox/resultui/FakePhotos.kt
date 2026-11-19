package com.ivandsky.puzzlebox.resultui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalInspectionMode
import coil3.annotation.ExperimentalCoilApi
import coil3.asImage
import coil3.compose.AsyncImagePreviewHandler
import coil3.compose.LocalAsyncImagePreviewHandler

private const val PHOTO_PX = 96
private const val SKY = 0xFFF2B35C.toInt()
private const val GROUND = 0xFFC4577B.toInt()
private const val SUBJECT = 0xFF5B3A4A.toInt()

private val FakePhoto by lazy {
    val bitmap = Bitmap.createBitmap(PHOTO_PX, PHOTO_PX, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    paint.shader = LinearGradient(0f, 0f, PHOTO_PX.toFloat(), PHOTO_PX.toFloat(), SKY, GROUND, Shader.TileMode.CLAMP)
    canvas.drawRect(0f, 0f, PHOTO_PX.toFloat(), PHOTO_PX.toFloat(), paint)
    paint.shader = null
    paint.color = SUBJECT
    // A head over shoulders, so the stand-in reads as a portrait, not a flat colour.
    canvas.drawCircle(PHOTO_PX * 0.5f, PHOTO_PX * 0.38f, PHOTO_PX * 0.17f, paint)
    canvas.drawCircle(PHOTO_PX * 0.5f, PHOTO_PX * 1.02f, PHOTO_PX * 0.36f, paint)
    bitmap.asImage()
}

/**
 * For previews and screenshot tests: every Coil `AsyncImage` in [content] draws one fixed portrait instead of loading its
 * URL, so a photo's look is reviewable with no network or files.
 */
@OptIn(ExperimentalCoilApi::class)
@Composable
fun FakePhotos(content: @Composable () -> Unit) {
    CompositionLocalProvider(
        LocalInspectionMode provides true,
        LocalAsyncImagePreviewHandler provides AsyncImagePreviewHandler { FakePhoto },
        content = content,
    )
}
