package com.xtjm.fusionstatusbar

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.semantics.SemanticsActions
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
import org.robolectric.shadows.ShadowDialog
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w320dp-h640dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MiuixAllPagesRenderingTest {
    @get:Rule(order = 0) val configuration = FusionTestConfigRule()
    @get:Rule(order = 1) val fonts = object : ExternalResource() {
        override fun before() { RuntimeEnvironment.setFontScale(1.5f) }
        override fun after() { RuntimeEnvironment.setFontScale(1f) }
    }
    @get:Rule(order = 2) val compose = createAndroidComposeRule<MainActivity>()

    @Test fun lightPagesAndSharedSheetsRender() = render("light")
    @Test @Config(qualifiers = "w320dp-h640dp-night-mdpi")
    fun darkPagesAndSharedSheetsRender() = render("dark")

    private fun render(theme: String) {
        compose.waitUntil(10_000) { compose.activity.settingsLoaded() }
        val folder = File("build/reports/miuix-all-pages").apply { mkdirs() }
        for (index in 1..5) {
            compose.onNodeWithTag("navigation_$index").performClick()
            compose.onNodeWithTag("navigation_$index").assertIsSelected()
            val layouts = mutableListOf<TextLayoutResult>()
            val label = compose.onNode(hasText("控制\n中心") and hasAnyAncestor(hasTestTag("navigation_3")),
                useUnmergedTree = true)
            label.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
            assertTrue(layouts.isNotEmpty())
            val labelBounds = label.fetchSemanticsNode().boundsInRoot
            val tabBounds = compose.onNodeWithTag("navigation_3").fetchSemanticsNode().boundsInRoot
            val layout = layouts.single()
            assertEquals(2, layout.lineCount)
            assertFalse("The navigation label must fit at large font sizes", layout.didOverflowHeight)
            for (line in 0 until layout.lineCount) {
                assertTrue(layout.getLineRight(line) - layout.getLineLeft(line) <= tabBounds.width)
            }
            assertTrue(labelBounds.bottom <= tabBounds.bottom)
            capture(compose.activity.window.decorView, File(folder, "page-$index-$theme.png"))
        }
        compose.onNodeWithTag("navigation_1").performClick()
        compose.onNodeWithTag("preference_时间与天气").performClick()
        compose.onNodeWithTag("setting_自定义时间格式").assertIsDisplayed()
        compose.onNodeWithTag("miuix_sheet_confirm").assertIsDisplayed()
        compose.mainClock.advanceTimeBy(1000)
        capture(ShadowDialog.getLatestDialog().window!!.decorView, File(folder, "clock-$theme.png"))
        compose.onNodeWithTag("miuix_sheet_confirm").performClick()
        compose.mainClock.advanceTimeBy(1000)
        compose.onNodeWithTag("navigation_3").performClick()
        compose.onNodeWithTag("preference_布局与外观").performClick()
        compose.onNodeWithTag("setting_接管电源菜单").assertExists()
        compose.onNodeWithTag("miuix_sheet_confirm").assertIsDisplayed()
        compose.mainClock.advanceTimeBy(1000)
        capture(ShadowDialog.getLatestDialog().window!!.decorView, File(folder, "control-settings-$theme.png"))
    }

    private fun capture(view: View, file: File) {
        compose.waitForIdle()
        compose.runOnIdle {
            assertTrue(view.width > 0 && view.height > 0)
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
            val colors = mutableSetOf<Int>()
            for (y in 0 until bitmap.height step 8) for (x in 0 until bitmap.width step 8) colors.add(bitmap.getPixel(x, y))
            assertTrue(colors.size > 8)
            file.outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
            bitmap.recycle()
        }
    }
}
