package com.xtjm.fusionstatusbar

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.text.TextLayoutResult
import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "en-rUS-w360dp-h800dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LanguageSettingsUiTest {
    @get:Rule(order = 0) val configuration = FusionTestConfigRule()
    @get:Rule(order = 1) val fontScale = object : ExternalResource() {
        override fun before() {
            val language = RuntimeEnvironment.getApplication().resources.configuration.locales[0].language
            RuntimeEnvironment.setFontScale(if (language == "ru") 1.3f else 1f)
        }
    }
    @get:Rule(order = 2) val compose = createAndroidComposeRule<MainActivity>()

    @Test fun languageChoiceUsesTheNativeStoreAndRecreationPreservesPageDraftAndMisc() {
        ready()
        compose.runOnIdle {
            MiscConfig.save(compose.activity, MiscConfig.defaults().withScreenshotClipboardEnabled(true))
            val draft = compose.activity.editorSession().draft().withSurface(ControlCenterSurface.SOFT_GLASS)
            compose.activity.updateControlCenterDraft(draft)
        }
        compose.onNodeWithTag("navigation_5").performClick()
        val title = compose.activity.getString(R.string.app_language_title)
        assertEquals("App language", title)
        expandLanguage()
        compose.onNodeWithTag("choice_${title}_3").performClick()
        compose.runOnIdle { assertEquals(3, AppLanguageSettings.selectedIndex(compose.activity)) }
        compose.activityRule.scenario.recreate()
        ready()
        compose.onNodeWithTag("navigation_5").assertIsSelected()
        compose.runOnIdle {
            assertTrue(MiscConfig.read(compose.activity).screenshotClipboardEnabled)
            assertTrue(compose.activity.editorSession().isDirty)
            assertEquals(ControlCenterSurface.SOFT_GLASS, compose.activity.editorSession().draft().surface)
        }
        expandLanguage()
        compose.onNodeWithTag("choice_${title}_0").performClick()
        compose.runOnIdle { assertEquals(0, AppLanguageSettings.selectedIndex(compose.activity)) }
    }

    @Test @Config(qualifiers = "ru-rRU-w320dp-h800dp-mdpi")
    fun longRussianSettingsAndNativeLanguageNamesRemainUsable() {
        ready()
        compose.onNodeWithTag("navigation_5").performClick()
        assertEquals("Язык приложения", compose.activity.getString(R.string.app_language_title))
        expandLanguage()
        compose.onNodeWithTag("choice_${compose.activity.getString(R.string.app_language_title)}_3")
            .assertExists()
        screenshot("ru-settings")
        for (index in 0..5) {
            val layouts = mutableListOf<TextLayoutResult>()
            compose.onNode(SemanticsMatcher.keyIsDefined(SemanticsActions.GetTextLayoutResult)
                and hasAnyAncestor(hasTestTag("navigation_$index")), useUnmergedTree = true)
                .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
            assertTrue(layouts.isNotEmpty())
            assertFalse("Russian navigation item $index clips its translated label", layouts.last().hasVisualOverflow)
        }
    }

    @Test @Config(qualifiers = "ja-rJP-w360dp-h800dp-mdpi")
    fun japaneseSettingsUseTranslatedControlsAndAllEightOptions() {
        ready()
        compose.onNodeWithTag("navigation_5").performClick()
        expandLanguage()
        compose.runOnIdle {
            assertEquals(8, AppLanguageSettings.labels(compose.activity).size)
            assertNotEquals("应用语言", compose.activity.getString(R.string.app_language_title))
        }
        screenshot("ja-settings")
    }

    private fun ready() { compose.waitUntil(10_000) { compose.activity.settingsLoaded() } }

    private fun expandLanguage() {
        val title = compose.activity.getString(R.string.app_language_title)
        scrollTo(title)
        compose.onNode(hasText(title) and hasClickAction()).performClick()
        scrollTo(title)
    }

    private fun scrollTo(title: String) {
        fun find(view: View): View? {
            if (view is SettingsChoiceSelector && view.contentDescription?.toString() == title) return view
            if (view is ViewGroup) for (i in 0 until view.childCount) find(view.getChildAt(i))?.let { return it }
            return null
        }
        compose.runOnIdle {
            val model = find(compose.activity.window.decorView)
            assertNotNull(model)
            model!!.requestRectangleOnScreen(Rect(0, 0, model.width, model.height), true)
        }
        compose.waitForIdle()
    }

    private fun screenshot(name: String) {
        compose.runOnIdle {
            val decor = compose.activity.window.decorView
            val bitmap = Bitmap.createBitmap(decor.width, decor.height, Bitmap.Config.ARGB_8888)
            decor.draw(Canvas(bitmap))
            val output = File("build/reports/languages/$name.png")
            output.parentFile!!.mkdirs()
            output.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        }
    }
}
