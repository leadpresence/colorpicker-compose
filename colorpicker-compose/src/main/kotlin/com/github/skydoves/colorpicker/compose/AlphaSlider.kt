/*
 * Copyright (C) 2022 skydoves (Jaewoong Eum)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.github.skydoves.colorpicker.compose

import android.view.MotionEvent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageBitmapConfig
import androidx.compose.ui.graphics.LinearGradientShader
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.PaintingStyle
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp

/**
 * AlphaSlider allows you to adjust the alpha value of the selected color from color pickers.
 *
 * @param modifier [Modifier] to decorate the internal Canvas.
 * @param controller Allows you to control and interacts with color pickers and all relevant subcomponents.
 * @param borderRadius Radius of the border.
 * @param borderSize [Dp] size of the border.
 * @param borderColor [Color] of the border.
 * @param wheelImageBitmap [ImageBitmap] to draw the wheel.
 * @param wheelRadius Radius of the wheel.
 * @param wheelColor [Color] of th wheel.
 * @param wheelPaint [Paint] to draw the wheel.
 * @param tileOddColor Color of the odd tiles.
 * @param tileEvenColor Color of the even tiles.
 * @param tileSize DP size of tiles.
 */
@Composable
public fun AlphaSlider(
    modifier: Modifier,
    controller: ColorPickerController,
    borderRadius: Dp = 6.dp,
    borderSize: Dp = 5.dp,
    borderColor: Color = Color.LightGray,
    wheelImageBitmap: ImageBitmap? = null,
    wheelRadius: Dp = 30.dp,
    wheelColor: Color = Color.White,
    wheelPaint: Paint = Paint().apply { color = wheelColor },
    tileOddColor: Color = defaultTileOddColor,
    tileEvenColor: Color = defaultTileEvenColor,
    tileSize: Dp = 30.dp,
) {
    var backgroundBitmap: ImageBitmap? = null
    var bitmapSize = IntSize(0, 0)
    val borderPaint: Paint = Paint().apply {
        style = PaintingStyle.Stroke
        strokeWidth = borderSize.value
        color = borderColor
    }
    val colorPaint: Paint = Paint().apply {
        color = controller.pureSelectedColor.value
    }

    LaunchedEffect(controller) {
        controller.isAttachedAlphaSlider = true
    }

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(borderRadius))
            .onSizeChanged { newSize ->
                val size =
                    newSize.takeIf { it.width != 0 && it.height != 0 } ?: return@onSizeChanged
                val drawable = AlphaTileDrawable(tileSize, tileOddColor, tileEvenColor)
                backgroundBitmap
                    ?.asAndroidBitmap()
                    ?.recycle()
                backgroundBitmap =
                    ImageBitmap(size.width, size.height, ImageBitmapConfig.Argb8888).apply {
                        val backgroundCanvas = Canvas(this)
                        drawable.setBounds(
                            0,
                            0,
                            backgroundCanvas.nativeCanvas.width,
                            backgroundCanvas.nativeCanvas.height
                        )
                        drawable.draw(backgroundCanvas.nativeCanvas)
                        backgroundCanvas.drawRoundRect(
                            left = 0f,
                            top = 0f,
                            right = size.width.toFloat(),
                            bottom = size.height.toFloat(),
                            radiusX = borderRadius.value,
                            radiusY = borderRadius.value,
                            paint = borderPaint
                        )
                    }
                bitmapSize = size
            }
            .pointerInteropFilter { event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN,
                    MotionEvent.ACTION_MOVE,
                    MotionEvent.ACTION_UP -> {
                        // calculate wheel position.
                        val wheelPoint = event.x
                        val position: Float = if (wheelImageBitmap == null) {
                            val sliderWidth =
                                bitmapSize.width - (wheelRadius.value + borderSize.value) * 2
                            val point = wheelPoint.coerceIn(
                                minimumValue = wheelRadius.value + borderSize.value,
                                maximumValue = bitmapSize.width - wheelRadius.value - borderSize.value,
                            )
                            point / sliderWidth
                        } else {
                            val sliderWidth =
                                bitmapSize.width - (borderSize.value) * 2 - wheelImageBitmap.width
                            val point = wheelPoint.coerceIn(
                                minimumValue = borderSize.value,
                                maximumValue = bitmapSize.width.toFloat() - wheelImageBitmap.width - borderSize.value
                            )
                            point / sliderWidth
                        }
                        controller.setAlpha(position.coerceIn(0f, 1f), fromUser = true)
                        true
                    }
                    else -> false
                }
            }
    ) {
        drawIntoCanvas { canvas ->
            backgroundBitmap?.let {
                // draw background bitmap.
                canvas.drawImage(it, Offset.Zero, Paint())

                // draw a linear gradient color shader.
                val startColor = controller.pureSelectedColor.value.copy(alpha = 0f)
                val endColor = controller.pureSelectedColor.value.copy(alpha = 1f)
                val shader = LinearGradientShader(
                    colors = listOf(startColor, endColor),
                    from = Offset.Zero,
                    to = Offset(bitmapSize.width.toFloat(), bitmapSize.height.toFloat()),
                    tileMode = TileMode.Clamp
                )
                colorPaint.shader = shader
                canvas.drawRoundRect(
                    left = 0f,
                    top = 0f,
                    right = bitmapSize.width.toFloat(),
                    bottom = bitmapSize.height.toFloat(),
                    radiusX = borderRadius.value,
                    radiusY = borderRadius.value,
                    paint = colorPaint
                )

                // draw wheel bitmap on the canvas.
                if (wheelImageBitmap == null) {
                    val position = controller.alpha.value
                    val point = (position * bitmapSize.width).coerceIn(
                        minimumValue = wheelRadius.value + borderSize.value,
                        maximumValue = bitmapSize.width - wheelRadius.value - borderSize.value,
                    )
                    canvas.drawCircle(
                        Offset(x = point, y = bitmapSize.height / 2f),
                        wheelRadius.value,
                        wheelPaint
                    )
                } else {
                    val position = controller.alpha.value
                    val point = (position * bitmapSize.width).coerceIn(
                        minimumValue = borderSize.value,
                        maximumValue = bitmapSize.width.toFloat() - wheelImageBitmap.width - borderSize.value
                    )
                    canvas.drawImage(
                        wheelImageBitmap,
                        Offset(x = point, y = bitmapSize.height / 2f - wheelImageBitmap.height / 2),
                        Paint()
                    )
                }
            }
        }
    }
}
