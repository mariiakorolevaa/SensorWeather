package com.example.weather

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View

class GraphView(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {

    private var dataPoints: List<Pair<Float, Float>> = ArrayList()

    fun setData(points: List<Pair<Float, Float>>) {
        dataPoints = points
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val paint = Paint().apply {
            color = Color.BLUE
            strokeWidth = 5f
            isAntiAlias = true
        }

        val axisPaint = Paint().apply {
            color = Color.BLACK
            strokeWidth = 2f
            isAntiAlias = true
        }

        val textPaint = Paint().apply {
            color = Color.BLACK
            textSize = 30f
            isAntiAlias = true
        }

        if (dataPoints.isNotEmpty()) {
            val xMin = dataPoints.minOf { it.first }
            val xMax = dataPoints.maxOf { it.first }
            val yMin = dataPoints.minOf { it.second }
            val yMax = dataPoints.maxOf { it.second }

            val scaleX = width.toFloat() / (xMax - xMin)
            val scaleY = height.toFloat() / (yMax - yMin)

            // Рисуем оси X и Y
            canvas.drawLine(0f, height.toFloat(), width.toFloat(), height.toFloat(), axisPaint)
            canvas.drawLine(0f, 0f, 0f, height.toFloat(), axisPaint)

            // Подписи осей
            canvas.drawText("Время", width - 40f, height - 20f, textPaint)  // подпись оси X
            canvas.drawText("Температура (°C)", 20f, 17f, textPaint)  // подпись оси Y

            // Рисуем деления на оси X
            val xInterval = (xMax - xMin) / 5  // Разделим на 5 частей
            for (i in 0..5) {
                val xPos = (xMin + i * xInterval - xMin) * scaleX
                canvas.drawLine(xPos, height - 10f, xPos, height + 10f, axisPaint)  // рисуем деление
                canvas.drawText(
                    String.format("%.1f", xMin + i * xInterval),
                    xPos - 20f, height + 40f, textPaint
                )  // рисуем метку на оси X
            }

            // Рисуем деления на оси Y
            val yInterval = (yMax - yMin) / 5  // Разделим на 5 частей
            for (i in 0..5) {
                val yPos = (yMin + i * yInterval - yMin) * scaleY
                canvas.drawLine(-10f, height - yPos, 10f, height - yPos, axisPaint)  // рисуем деление
                canvas.drawText(
                    String.format("%.1f", yMin + i * yInterval),
                    20f, height - yPos + 10f, textPaint
                )  // рисуем метку на оси Y
            }

            // Рисуем линии для отображения данных
            for (i in 1 until dataPoints.size) {
                val prevPoint = dataPoints[i - 1]
                val currPoint = dataPoints[i]

                val prevX = (prevPoint.first - xMin) * scaleX
                val prevY = (prevPoint.second - yMin) * scaleY

                val currX = (currPoint.first - xMin) * scaleX
                val currY = (currPoint.second - yMin) * scaleY

                canvas.drawLine(prevX, height - prevY, currX, height - currY, paint)
            }

            // Легенда
            val legendText = "Температура (°C)"
            canvas.drawText(legendText, width / 2f - textPaint.measureText(legendText) / 2, 50f, textPaint)
        }
    }
}
