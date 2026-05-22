package com.phonedoctor

import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class ScreenDiagnosticActivity : AppCompatActivity() {

    private val touchPoints = mutableSetOf<Pair<Int, Int>>()
    private val colors = listOf(Color.WHITE, Color.BLACK, Color.RED, Color.GREEN, Color.BLUE, Color.YELLOW)
    private var colorIndex = 0
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var mainView: FrameLayout
    private lateinit var infoText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN)

        mainView = FrameLayout(this)
        mainView.setBackgroundColor(colors[0])

        infoText = TextView(this)
        infoText.text = "🎨 COLOR TEST — Watch for dead pixels or burn-in"
        infoText.setTextColor(Color.GRAY)
        infoText.textSize = 14f
        infoText.setPadding(30, 60, 30, 0)

        mainView.addView(infoText)
        setContentView(mainView)
        startColorCycle()
    }

    private fun startColorCycle() {
        handler.postDelayed({
            colorIndex = (colorIndex + 1) % colors.size
            mainView.setBackgroundColor(colors[colorIndex])
            if (colorIndex < colors.size - 1) {
                startColorCycle()
            } else {
                startTouchTest()
            }
        }, 800)
    }

    private fun startTouchTest() {
        mainView.setBackgroundColor(Color.parseColor("#1A1A2E"))
        infoText.text = "👆 TOUCH TEST — Swipe all over screen\nCoverage: 0%\n\nTap anywhere to start"
        infoText.setTextColor(Color.WHITE)

        mainView.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_MOVE || event.action == MotionEvent.ACTION_DOWN) {
                val x = (event.x / mainView.width * 10).toInt().coerceIn(0, 9)
                val y = (event.y / mainView.height * 10).toInt().coerceIn(0, 9)
                touchPoints.add(Pair(x, y))
                drawTouchPoint(event.x, event.y)
                val coverage = (touchPoints.size.toFloat() / 100f * 100).coerceAtMost(100f)
                infoText.text = "👆 TOUCH TEST — Swipe all over screen\nCoverage: ${"%.0f".format(coverage)}%\n${if (coverage >= 70) "✅ Tap to finish!" else "Keep swiping..."}"
                if (touchPoints.size >= 70) finishTest()
            }
            true
        }
    }

    private fun drawTouchPoint(x: Float, y: Float) {
        val dot = View(this)
        dot.setBackgroundColor(Color.parseColor("#00FF88"))
        val params = FrameLayout.LayoutParams(25, 25)
        params.leftMargin = x.toInt() - 12
        params.topMargin = y.toInt() - 12
        mainView.addView(dot, params)
    }

    private fun finishTest() {
        val coverage = (touchPoints.size.toFloat() / 100f * 100).coerceAtMost(100f)
        mainView.removeAllViews()
        mainView.setBackgroundColor(Color.parseColor("#0D0D0D"))
        val result = TextView(this)
        result.text = "✅ SCREEN TEST DONE!\n\nTouch Coverage: ${"%.0f".format(coverage)}%\n\n" +
            if (coverage >= 70) "Screen appears HEALTHY ✅" else "⚠️ Dead zones detected! Coverage low."
        result.setTextColor(Color.WHITE)
        result.textSize = 18f
        result.setPadding(40, 100, 40, 0)
        mainView.addView(result)
        handler.postDelayed({ finish() }, 3000)
    }
}
