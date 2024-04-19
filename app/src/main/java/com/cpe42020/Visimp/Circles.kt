package com.cpe42020.Visimp

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.graphics.Typeface
import android.view.View
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import android.graphics.RectF

class CircleTextView(context: Context, attrs: AttributeSet?) : View(context, attrs) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val hourAngles = listOf(30, 60, 90, 120, 150, 180, 210, 240, 270, 300, 330, 0)
    private val labelIndices =
        listOf("BR", "BR", "MB", "BL", "BL", "ML", "TL", "TL", "TM", "TR", "TR", "MR")
    private val centerX: Float
    private val centerY: Float
    private val radius: Float

    init {

        paint.textSize = 50f
        paint.typeface =
            Typeface.create(Typeface.DEFAULT, Typeface.BOLD)// Adjust text size as needed

        val displayMetrics = resources.displayMetrics
        val screenWidth = displayMetrics.widthPixels
        val screenHeight = displayMetrics.heightPixels
        centerX = (screenWidth / 2.15f).toFloat()
        centerY = (screenHeight / 2f).toFloat()
        radius = min(centerX, centerY) * 0.9f  // Adjust as needed


    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)


        // Draw text around the circle
        for ((i, angle) in hourAngles.withIndex()) {
            val x = centerX + radius * cos(Math.toRadians(angle.toDouble())).toFloat()
            val y = centerY + radius * 1.5f * sin(Math.toRadians(angle.toDouble())).toFloat()
            canvas.drawText(labelIndices[i], x, y, paint)
            val pointRadius = 15f
            canvas.drawCircle(centerX, centerY, pointRadius, paint)


            paint.color = Color.GREEN
            canvas.drawText(labelIndices[i], x, y, paint)
        }

    }
}
