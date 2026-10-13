package com.xtjm.fusionstatusbar

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Outline
import android.view.View
import android.view.ViewOutlineProvider
import android.view.ViewTreeObserver
import android.util.Log
import android.widget.FrameLayout
import kotlin.math.ceil
import kotlin.math.roundToInt

/** Capturing the common ancestor must never include the foreground or yesterday's blur. */
internal class NavigationBackdropHost(context: Context) : FrameLayout(context) {
    private var capturing = false
    private val backdrop = NavigationBackdropView(context, { source, canvas ->
        capturing = true
        try { source.draw(canvas) } finally { capturing = false }
    })

    init {
        clipChildren = false
        clipToPadding = false
        addView(backdrop, LayoutParams(0, 0))
    }

    override fun draw(canvas: Canvas) { if (!capturing) super.draw(canvas) }
    override fun dispatchDraw(canvas: Canvas) { if (!capturing) super.dispatchDraw(canvas) }
    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        backdrop.layout(paddingLeft, paddingTop, width - paddingRight, height - paddingBottom)
    }
    fun setBackdropSource(source: View) { backdrop.setSource(source) }
    fun setNightMode(night: Boolean) { backdrop.setNightMode(night) }
    fun setFloating(enabled: Boolean) { backdrop.setEnabledBlur(enabled) }
}

/** Small live page captures keep View and Compose content in one blur without blurring controls. */
internal class NavigationBackdropView(context: Context, private val captureSource: (View, Canvas) -> Unit) : View(context) {
    private var source: View? = null
    private var observer: ViewTreeObserver? = null
    private var enabledBlur = false
    private var night = false
    private var snapshot: Bitmap? = null
    private var blurred: Bitmap? = null
    private var pixels = IntArray(0)
    private var previousPixels = IntArray(0)
    private var work = IntArray(0)
    private var lastNight = false
    private var hasCapture = false
    private var captureFailed = false
    private val sourceLocation = IntArray(2)
    private val ownLocation = IntArray(2)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val clip = Path()
    private val bounds = RectF()
    private val imageBounds = RectF()
    private val density get() = resources.displayMetrics.density
    private val capturePadding get() = (16f * density).roundToInt()
    private val preDraw = ViewTreeObserver.OnPreDrawListener { capture(); true }

    init {
        visibility = GONE
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                outline.setRoundRect(0, 0, view.width, view.height, view.height / 2f)
                outline.alpha = 0.24f
            }
        }
    }

    fun setSource(value: View) {
        unregister()
        source = value
        hasCapture = false
        captureFailed = false
        register()
        invalidate()
    }

    fun setNightMode(value: Boolean) {
        if (night == value) return
        night = value
        hasCapture = false
        invalidate()
    }

    fun setEnabledBlur(value: Boolean) {
        enabledBlur = value
        captureFailed = false
        visibility = if (value) VISIBLE else GONE
        elevation = if (value) 6f * density else 0f
        if (value) { register(); invalidate() } else { unregister(); release() }
    }

    override fun onSizeChanged(width: Int, height: Int, oldWidth: Int, oldHeight: Int) {
        super.onSizeChanged(width, height, oldWidth, oldHeight)
        invalidateOutline()
    }

    override fun onAttachedToWindow() { super.onAttachedToWindow(); register() }
    override fun onDetachedFromWindow() { unregister(); release(); super.onDetachedFromWindow() }

    private fun register() {
        if (!enabledBlur || !isAttachedToWindow || observer != null) return
        source?.viewTreeObserver?.takeIf { it.isAlive }?.let {
            observer = it
            it.addOnPreDrawListener(preDraw)
        }
    }

    private fun unregister() {
        observer?.takeIf { it.isAlive }?.removeOnPreDrawListener(preDraw)
        observer = null
    }

    private fun release() {
        snapshot?.recycle(); snapshot = null
        blurred?.recycle(); blurred = null
        pixels = IntArray(0); previousPixels = IntArray(0); work = IntArray(0)
        hasCapture = false
    }

    internal fun capture() {
        val page = source ?: return
        if (!enabledBlur || captureFailed || width <= 0 || height <= 0 || page.width <= 0 || page.height <= 0) return
        val padding = capturePadding
        val scaledWidth = ceil((width + padding * 2) / 4f).toInt()
        val scaledHeight = ceil((height + padding * 2) / 4f).toInt()
        if (snapshot?.width != scaledWidth || snapshot?.height != scaledHeight) {
            release()
            snapshot = Bitmap.createBitmap(scaledWidth, scaledHeight, Bitmap.Config.ARGB_8888)
            blurred = Bitmap.createBitmap(scaledWidth, scaledHeight, Bitmap.Config.ARGB_8888)
            pixels = IntArray(scaledWidth * scaledHeight)
            previousPixels = IntArray(pixels.size)
            work = IntArray(pixels.size)
        }
        val bitmap = snapshot!!
        bitmap.eraseColor(if (night) Color.rgb(24, 24, 26) else Color.rgb(247, 247, 249))
        page.getLocationOnScreen(sourceLocation)
        getLocationOnScreen(ownLocation)
        val canvas = Canvas(bitmap)
        canvas.scale(0.25f, 0.25f)
        canvas.translate((sourceLocation[0] - ownLocation[0] + padding).toFloat(),
            (sourceLocation[1] - ownLocation[1] + padding).toFloat())
        try {
            captureSource(page, canvas)
        } catch (error: RuntimeException) {
            release()
            captureFailed = true
            invalidate()
            Log.w("FusionStatusBar", "Navigation background capture unavailable", error)
            return
        }
        bitmap.getPixels(pixels, 0, scaledWidth, 0, 0, scaledWidth, scaledHeight)
        if (hasCapture && night == lastNight && pixels.contentEquals(previousPixels)) return
        pixels.copyInto(previousPixels)
        // Three separable box passes approximate a Gaussian at a quarter of screen resolution.
        val radius = (2f * density).roundToInt().coerceAtLeast(1)
        repeat(3) {
            NavigationBlur.horizontal(pixels, work, scaledWidth, scaledHeight, radius)
            NavigationBlur.vertical(work, pixels, scaledWidth, scaledHeight, radius)
        }
        blurred!!.setPixels(pixels, 0, scaledWidth, 0, 0, scaledWidth, scaledHeight)
        lastNight = night
        hasCapture = true
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        bounds.set(0f, 0f, width.toFloat(), height.toFloat())
        clip.reset()
        clip.addRoundRect(bounds, height / 2f, height / 2f, Path.Direction.CW)
        val save = canvas.save()
        canvas.clipPath(clip)
        blurred?.let {
            val padding = capturePadding.toFloat()
            imageBounds.set(-padding, -padding, it.width * 4f - padding, it.height * 4f - padding)
            paint.color = Color.WHITE
            canvas.drawBitmap(it, null, imageBounds, paint)
        }
        paint.color = if (night) Color.argb(170, 29, 29, 32) else Color.argb(162, 255, 255, 255)
        canvas.drawRect(bounds, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = density
        paint.color = if (night) Color.argb(65, 255, 255, 255) else Color.argb(210, 255, 255, 255)
        bounds.inset(density / 2f, density / 2f)
        canvas.drawRoundRect(bounds, height / 2f, height / 2f, paint)
        paint.style = Paint.Style.FILL
        canvas.restoreToCount(save)
    }
}

internal object NavigationBlur {
    fun horizontal(input: IntArray, output: IntArray, width: Int, height: Int, radius: Int) =
        pass(input, output, width, height, radius, true)

    fun vertical(input: IntArray, output: IntArray, width: Int, height: Int, radius: Int) =
        pass(input, output, width, height, radius, false)

    private fun pass(input: IntArray, output: IntArray, width: Int, height: Int, radius: Int, horizontal: Boolean) {
        val rows = if (horizontal) height else width
        val columns = if (horizontal) width else height
        val divisor = radius * 2 + 1
        for (row in 0 until rows) {
            fun pixel(column: Int): Int {
                val bounded = column.coerceIn(0, columns - 1)
                return input[if (horizontal) row * width + bounded else bounded * width + row]
            }
            var red = 0; var green = 0; var blue = 0
            for (offset in -radius..radius) {
                val color = pixel(offset)
                red += Color.red(color); green += Color.green(color); blue += Color.blue(color)
            }
            for (column in 0 until columns) {
                output[if (horizontal) row * width + column else column * width + row] =
                    Color.rgb(red / divisor, green / divisor, blue / divisor)
                val leaving = pixel(column - radius)
                val entering = pixel(column + radius + 1)
                red += Color.red(entering) - Color.red(leaving)
                green += Color.green(entering) - Color.green(leaving)
                blue += Color.blue(entering) - Color.blue(leaving)
            }
        }
    }
}
