package com.xtjm.fusionstatusbar

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.text.TextLayoutResult
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w320dp-h640dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MiuixNavigationRenderingTest {
    @get:Rule(order = 0) val configuration = FusionTestConfigRule()
    @get:Rule(order = 1) val fonts = object : ExternalResource() {
        override fun before() { RuntimeEnvironment.setFontScale(1.5f) }
        override fun after() { RuntimeEnvironment.setFontScale(1f) }
    }
    @get:Rule(order = 2) val compose = createAndroidComposeRule<MainActivity>()

    @Test fun lightNavigationRendersBothModes() = render("light")
    @Test @Config(qualifiers = "w320dp-h640dp-night-mdpi")
    fun darkNavigationRendersBothModes() = render("dark")

    private fun render(theme: String) {
        compose.waitUntil(10_000) { compose.activity.settingsLoaded() }
        val navigation = MainActivity::class.java.getDeclaredField("miuixNavigation")
            .apply { isAccessible = true }.get(compose.activity) as MiuixNavigation
        val folder = File("build/reports/floating-navigation").apply { mkdirs() }
        var plainHeight = 0
        for (floating in listOf(false, true, false)) {
            compose.runOnIdle { navigation.setFloating(floating) }
            compose.waitForIdle()
            for (index in 0..5) {
                val tab = compose.onNodeWithTag("navigation_$index")
                tab.assertIsDisplayed()
                val label = compose.onNode(hasText(if (index == 3) "控制\n中心"
                    else listOf("桌面", "状态栏", "通知栏", "控制中心", "杂项", "设置")[index]) and
                    hasAnyAncestor(hasTestTag("navigation_$index")), useUnmergedTree = true)
                val layouts = mutableListOf<TextLayoutResult>()
                label.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
                val layout = layouts.single()
                assertFalse("Tab $index text width must fit: size=${layout.size}, text=${layout.layoutInput.text}, style=${layout.layoutInput.style}, right=${layout.getLineRight(0)}", layout.didOverflowWidth)
                assertFalse("Tab $index text height must fit", layout.didOverflowHeight)
                val bounds = tab.fetchSemanticsNode().boundsInRoot
                val labelBounds = label.fetchSemanticsNode().boundsInRoot
                assertTrue(labelBounds.left >= bounds.left && labelBounds.right <= bounds.right)
                assertTrue(labelBounds.top >= bounds.top && labelBounds.bottom <= bounds.bottom)
            }
            compose.onNodeWithTag("navigation_4").performClick().assertIsSelected()
            compose.onNodeWithTag("setting_移除链式启动弹窗").assertExists()
            compose.runOnIdle {
                val host = navigation.view
                assertTrue(host.height > 0 && host.height < 160)
                assertEquals(host.height + 8, navigation.getReservedHeight())
                if (!floating) {
                    if (plainHeight == 0) plainHeight = host.height else assertEquals(plainHeight, host.height)
                } else {
                    assertTrue(host.height > plainHeight)
                    assertEquals(12, host.paddingLeft)
                    assertTrue(host.paddingBottom >= 12)
                    (host.getChildAt(0) as NavigationBackdropView).capture()
                }
                val bitmap = Bitmap.createBitmap(host.width, host.height, Bitmap.Config.ARGB_8888)
                host.draw(Canvas(bitmap))
                val colors = mutableSetOf<Int>()
                for (y in 0 until bitmap.height step 2) for (x in 0 until bitmap.width step 2)
                    colors.add(bitmap.getPixel(x, y))
                assertTrue("Navigation must render icons and labels", colors.size > 12)
                File(folder, "${if (floating) "floating" else "plain"}-$theme.png").outputStream().use {
                    assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it))
                }
                bitmap.recycle()
            }
        }
    }
}
