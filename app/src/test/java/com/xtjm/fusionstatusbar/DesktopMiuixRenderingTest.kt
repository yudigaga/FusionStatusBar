package com.xtjm.fusionstatusbar

import android.content.ComponentName
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowDialog
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w320dp-h640dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DesktopMiuixRenderingTest {
    @get:Rule(order = 0) val configuration = FusionTestConfigRule()
    @get:Rule(order = 1) val fontScale = object : ExternalResource() {
        override fun before() { RuntimeEnvironment.setFontScale(1.5f) }
        override fun after() { RuntimeEnvironment.setFontScale(1f) }
    }
    @get:Rule(order = 2) val compose = createAndroidComposeRule<MainActivity>()

    @Test fun lightSmallScreenHasScrollableIconChoices() = render("light")
    @Test @Config(qualifiers = "w320dp-h640dp-night-mdpi")
    fun darkSmallScreenHasScrollableIconChoices() = render("dark")

    private fun render(theme: String) {
        compose.waitUntil(10_000) { compose.activity.settingsLoaded() }
        assertEquals(1.5f, compose.activity.resources.configuration.fontScale, .001f)
        val titleBounds = compose.onNodeWithTag("desktop_heading").fetchSemanticsNode().boundsInRoot
        val folder = File("build/reports/desktop-miuix").apply { mkdirs() }
        capture(compose.activity.window.decorView, File(folder, "desktop-$theme-320.png")) { bitmap ->
            val pageLocation = IntArray(2)
            val windowLocation = IntArray(2)
            compose.activity.findViewById<View>(R.id.desktop_miuix_page).getLocationInWindow(pageLocation)
            compose.activity.window.decorView.getLocationInWindow(windowLocation)
            val left = (titleBounds.left + pageLocation[0] - windowLocation[0]).toInt()
            val top = (titleBounds.top + pageLocation[1] - windowLocation[1]).toInt()
            var readablePixels = 0
            for (y in top until top + titleBounds.height.toInt()) {
                for (x in left until left + titleBounds.width.toInt()) {
                    val pixel = bitmap.getPixel(x, y)
                    val brightness = (android.graphics.Color.red(pixel) + android.graphics.Color.green(pixel) +
                        android.graphics.Color.blue(pixel)) / (3f * 255f)
                    if (if (theme == "dark") brightness > .7f else brightness < .3f) readablePixels++
                }
            }
            assertTrue("The desktop title must remain readable in $theme mode", readablePixels > 20)
        }
        compose.runOnIdle {
            val manager = Shadows.shadowOf(compose.activity.packageManager)
            for (i in 0..29) {
                val packageName = "example.application$i"
                val app = ApplicationInfo().apply {
                    this.packageName = packageName
                    enabled = i != 0
                    flags = ApplicationInfo.FLAG_INSTALLED
                    nonLocalizedLabel = if (i == 0) "带有较长名称的冻结应用" else "应用 $i"
                }
                manager.installPackage(PackageInfo().apply { this.packageName = packageName; applicationInfo = app })
                val target = ActivityInfo().apply {
                    this.packageName = packageName
                    name = "$packageName.Main"
                    applicationInfo = app
                    enabled = true
                    exported = true
                }
                manager.addOrUpdateActivity(target)
                manager.addIntentFilterForActivity(ComponentName(packageName, target.name),
                    IntentFilter(Intent.ACTION_MAIN).apply { addCategory(Intent.CATEGORY_LAUNCHER) })
            }
        }
        compose.onNodeWithTag("desktop_hidden_apps").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("desktop_app_list").fetchSemanticsNodes().isNotEmpty() }
        compose.mainClock.advanceTimeBy(1000)
        val dialog = ShadowDialog.getLatestDialog()
        assertNotNull(dialog)
        compose.onNodeWithTag("desktop_app_search").assertIsDisplayed()
        compose.onNodeWithTag("desktop_app_search").assertIsNotFocused()
        compose.onNodeWithTag("desktop_sheet_confirm").assertIsDisplayed()
        capture(dialog.window!!.decorView, File(folder, "applications-$theme-320.png"))
        compose.onNodeWithTag("desktop_app_list").performScrollToNode(hasTestTag("desktop_app_example.application29"))
        compose.onNodeWithTag("desktop_app_example.application29").assertIsDisplayed()
        compose.onNodeWithTag("desktop_app_search").performTextInput("application0")
        compose.onNodeWithTag("desktop_icon_example.application0", useUnmergedTree = true).assertExists()
        capture(dialog.window!!.decorView, File(folder, "applications-$theme-filtered.png"))
        val header = compose.onNodeWithTag("desktop_sheet_confirm").fetchSemanticsNode().boundsInRoot
        val search = compose.onNodeWithTag("desktop_app_search").fetchSemanticsNode().boundsInRoot
        val app = compose.onNodeWithTag("desktop_app_example.application0").fetchSemanticsNode().boundsInRoot
        assertTrue(header.bottom <= search.top)
        assertTrue(search.bottom <= app.top)
        assertTrue(app.bottom <= dialog.window!!.decorView.height)
    }

    private fun capture(view: View, file: File, check: ((Bitmap) -> Unit)? = null) {
        compose.runOnIdle {
            assertTrue(view.width > 0 && view.height > 0)
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
            val colors = mutableSetOf<Int>()
            for (y in 0 until bitmap.height step 8) for (x in 0 until bitmap.width step 8) colors.add(bitmap.getPixel(x, y))
            assertTrue("The rendered Compose window must contain actual UI pixels", colors.size > 8)
            file.outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
            check?.invoke(bitmap)
            bitmap.recycle()
        }
    }
}
