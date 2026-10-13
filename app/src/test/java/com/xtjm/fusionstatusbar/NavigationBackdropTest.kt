package com.xtjm.fusionstatusbar

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w320dp-h640dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class NavigationBackdropTest {
    @Test fun backgroundIsBlurredWhileForegroundStaysSharpAndExcludedFromCapture() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().visible()
        val context = activity.get()
        val page = FrameLayout(context)
        val paint = Paint()
        val stripes = object : View(context) {
            override fun onDraw(canvas: Canvas) {
                paint.color = Color.RED
                canvas.drawRect(0f, 0f, 160f, height.toFloat(), paint)
                paint.color = Color.BLUE
                canvas.drawRect(160f, 0f, width.toFloat(), height.toFloat(), paint)
            }
        }
        page.addView(stripes, FrameLayout.LayoutParams(-1, -1))
        val host = NavigationBackdropHost(context)
        val foreground = object : View(context) {
            override fun onDraw(canvas: Canvas) {
                paint.color = Color.BLACK
                canvas.drawRect(132f, 16f, 140f, 48f, paint)
            }
        }.apply { elevation = 8f }
        host.addView(foreground, FrameLayout.LayoutParams(-1, 80))
        host.setPadding(12, 6, 12, 12)
        host.setBackdropSource(page)
        host.setFloating(true)
        page.addView(host, FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM))
        context.setContentView(page)
        page.measure(View.MeasureSpec.makeMeasureSpec(320, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(480, View.MeasureSpec.EXACTLY))
        page.layout(0, 0, 320, 480)
        assertEquals("A zero-sized background must not expand the wrap-content host", 98, host.height)
        val backdrop = host.getChildAt(0) as NavigationBackdropView
        backdrop.capture()
        val background = draw(backdrop)
        val folder = File("build/reports/floating-navigation").apply { mkdirs() }
        File(folder, "backdrop-boundary.png").outputStream().use {
            assertTrue(background.compress(Bitmap.CompressFormat.PNG, 100, it))
        }
        val midpoint = 160 - host.paddingLeft
        val left = background.getPixel(midpoint - 4, 30)
        val right = background.getPixel(midpoint + 4, 30)
        assertTrue("Blur mixes blue into the red side of the boundary", Color.blue(left) > 165)
        assertTrue("Blur mixes red into the blue side of the boundary", Color.red(right) > 165)
        assertTrue("Far-away red retains a different color from the boundary: far=${background.getPixel(80, 30)}, left=$left, right=$right",
            Color.blue(background.getPixel(80, 30)) < Color.blue(left))
        assertTrue("The foreground is absent from the background capture",
            Color.red(background.getPixel(135, 30)) > 240)
        assertEquals("The capsule corner stays transparent", 0, Color.alpha(background.getPixel(0, 0)))
        val complete = draw(host)
        assertEquals("Foreground edges and fill remain sharp", Color.BLACK,
            complete.getPixel(host.paddingLeft + 135, host.paddingTop + 30))
        File(folder, "foreground-sharp.png").outputStream().use {
            assertTrue(complete.compress(Bitmap.CompressFormat.PNG, 100, it))
        }
        background.recycle(); complete.recycle()
        host.setFloating(false)
        assertEquals(View.GONE, backdrop.visibility)
        assertNull(NavigationBackdropView::class.java.getDeclaredField("blurred")
            .apply { isAccessible = true }.get(backdrop))
        activity.pause().stop().destroy()
    }

    @Test fun changedPageAndThemeRefreshCapturedPixels() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().visible()
        val context = activity.get()
        val page = View(context).apply { setBackgroundColor(Color.RED); layout(0, 0, 320, 480) }
        val backdrop = NavigationBackdropView(context) { source, canvas -> source.draw(canvas) }
        backdrop.layout(0, 0, 300, 80)
        backdrop.setSource(page)
        backdrop.setEnabledBlur(true)
        backdrop.capture()
        val first = draw(backdrop)
        page.setBackgroundColor(Color.BLUE)
        backdrop.capture()
        val next = draw(backdrop)
        assertTrue(Color.red(first.getPixel(150, 40)) > Color.red(next.getPixel(150, 40)))
        assertTrue(Color.blue(next.getPixel(150, 40)) > Color.blue(first.getPixel(150, 40)))
        backdrop.setNightMode(true)
        backdrop.capture()
        val dark = draw(backdrop)
        assertTrue(Color.red(dark.getPixel(150, 40)) < Color.red(next.getPixel(150, 40)))
        first.recycle(); next.recycle(); dark.recycle()
        backdrop.setEnabledBlur(false)
        activity.pause().stop().destroy()
    }

    @Test fun unavailableSourceCaptureKeepsTheSurfaceUsableWithoutRepeatedAttempts() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().visible()
        val context = activity.get()
        val page = View(context).apply { layout(0, 0, 320, 480) }
        var attempts = 0
        val backdrop = NavigationBackdropView(context) { _, _ ->
            attempts++
            throw IllegalStateException("unsupported_canvas")
        }
        backdrop.layout(0, 0, 300, 80)
        backdrop.setSource(page)
        backdrop.setEnabledBlur(true)
        backdrop.capture()
        backdrop.capture()
        assertEquals(1, attempts)
        val surface = draw(backdrop)
        assertTrue(Color.alpha(surface.getPixel(150, 40)) > 0)
        assertEquals(0, Color.alpha(surface.getPixel(0, 0)))
        surface.recycle()
        activity.pause().stop().destroy()
    }

    private fun draw(view: View): Bitmap =
        Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888).also { view.draw(Canvas(it)) }
}
