package com.xtjm.fusionstatusbar

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.semantics.SemanticsActions
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "zh-rCN-w360dp-h800dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MiscSettingsUiTest {
    @get:Rule(order = 0) val configuration = FusionTestConfigRule()
    @get:Rule(order = 1) val compose = createAndroidComposeRule<MainActivity>()

    @Test fun homeWarningTogglePersistsAndCanBeDisabledWithoutChangingTheScoreOption() {
        val title = "隐藏首页风险提醒"
        val key = "security_center_hide_home_warnings"
        compose.waitUntil(10_000) { compose.activity.settingsLoaded() }
        compose.onNodeWithTag("navigation_4").performClick()
        compose.runOnIdle {
            val values = MiscConfig.read(compose.activity).toBundle()
            assertTrue(values.containsKey(key))
            assertFalse(values.getBoolean(key, true))
        }
        scrollToSetting(title)
        compose.onNodeWithTag("setting_$title").assertIsDisplayed().performClick()
        scrollToSetting("锁定 666 分")
        compose.onNodeWithTag("setting_锁定 666 分").performClick()
        compose.runOnIdle {
            val values = MiscConfig.read(compose.activity).toBundle()
            assertTrue(values.getBoolean(key))
            assertTrue(values.getBoolean("security_center_score_666"))
            assertFalse(values.getBoolean("screenshot_clipboard_enabled"))
        }
        compose.activityRule.scenario.recreate()
        compose.waitUntil(10_000) { compose.activity.settingsLoaded() }
        compose.onNodeWithTag("navigation_4").performClick()
        compose.runOnIdle { assertTrue(MiscConfig.read(compose.activity).toBundle().getBoolean(key)) }
        scrollToSetting(title)
        compose.onNodeWithTag("setting_$title").performClick()
        compose.runOnIdle {
            val values = MiscConfig.read(compose.activity).toBundle()
            assertFalse(values.getBoolean(key, true))
            assertTrue(values.getBoolean("security_center_score_666"))
        }
    }

    @Test fun phoneManagerOptionsCanBeScrolledSavedAndRestoredIndependently() {
        compose.waitUntil(10_000) { compose.activity.settingsLoaded() }
        compose.onNodeWithTag("navigation_4").performClick()
        scrollToSetting("解锁智能防窥")
        compose.onNodeWithTag("setting_解锁智能防窥").assertIsDisplayed().performClick()
        scrollToSetting("恢复参考亮屏时间")
        compose.onNodeWithTag("setting_恢复参考亮屏时间").assertIsDisplayed().performClick()
        scrollToSetting("亮屏时间显示粒度")
        compose.onNodeWithTag("slider_亮屏时间显示粒度")
            .performSemanticsAction(SemanticsActions.SetProgress) { it(4f) }
        compose.runOnIdle {
            val config = MiscConfig.read(compose.activity).toBundle()
            assertTrue(config.getBoolean("security_center_smart_privacy"))
            assertTrue(config.getBoolean("security_center_screen_on_time"))
            assertFalse(config.getBoolean("security_center_cinema_mode"))
            assertEquals(5, config.getInt("security_center_screen_on_time_precision_minutes"))
            assertFalse(MiscConfig.read(compose.activity).screenshotClipboardEnabled)
        }
        compose.activityRule.scenario.recreate()
        compose.waitUntil(10_000) { compose.activity.settingsLoaded() }
        compose.runOnIdle {
            assertTrue(MiscConfig.read(compose.activity).toBundle().getBoolean("security_center_smart_privacy"))
            assertEquals(5, MiscConfig.read(compose.activity).toBundle()
                .getInt("security_center_screen_on_time_precision_minutes"))
        }
    }

    private fun scrollToSetting(title: String) {
        fun find(view: android.view.View): android.view.View? {
            if (title == view.contentDescription?.toString()) return view
            if (view is android.view.ViewGroup) for (index in 0 until view.childCount) {
                val result = find(view.getChildAt(index))
                if (result != null) return result
            }
            return null
        }
        compose.runOnIdle {
            val model = find(compose.activity.window.decorView)
            assertNotNull("setting exists: $title", model)
            val row = model!!.parent as android.view.View
            row.requestRectangleOnScreen(android.graphics.Rect(0, 0, row.width, row.height), true)
        }
        compose.waitForIdle()
    }

    @Test fun screenshotClipboardTogglePersistsThroughRecreationAndKeepsChainStartIndependent() {
        compose.waitUntil(10_000) { compose.activity.settingsLoaded() }
        compose.onNodeWithTag("navigation_4").performClick().assertIsSelected()
        compose.onNodeWithTag("setting_截屏自动写入剪贴板").assertIsDisplayed().performClick()
        compose.runOnIdle {
            assertTrue(MiscConfig.read(compose.activity).toBundle().getBoolean("screenshot_clipboard_enabled"))
            assertFalse(MiscConfig.read(compose.activity).removeChainStartDialog)
        }
        compose.onNodeWithTag("setting_移除链式启动弹窗").performClick()
        compose.runOnIdle {
            assertTrue(MiscConfig.read(compose.activity).toBundle().getBoolean("screenshot_clipboard_enabled"))
            assertTrue(MiscConfig.read(compose.activity).removeChainStartDialog)
        }
        compose.activityRule.scenario.recreate()
        compose.waitUntil(10_000) { compose.activity.settingsLoaded() }
        compose.onNodeWithTag("navigation_4").performClick()
        compose.onNodeWithTag("setting_截屏自动写入剪贴板").performClick()
        compose.runOnIdle {
            assertFalse(MiscConfig.read(compose.activity).toBundle().getBoolean("screenshot_clipboard_enabled"))
            assertTrue(MiscConfig.read(compose.activity).removeChainStartDialog)
        }
    }

    @Test fun miscellaneousTogglePersistsAndCanBeDisabled() {
        compose.waitUntil(10_000) { compose.activity.settingsLoaded() }
        compose.onNodeWithTag("navigation_4").performClick().assertIsSelected()
        compose.onNodeWithTag("setting_移除链式启动弹窗").assertIsDisplayed().performClick()
        compose.runOnIdle { assertTrue(MiscConfig.read(compose.activity).removeChainStartDialog) }
        compose.onNodeWithTag("navigation_1").performClick()
        compose.onNodeWithTag("navigation_4").performClick()
        compose.onNodeWithTag("setting_移除链式启动弹窗").performClick()
        compose.runOnIdle { assertFalse(MiscConfig.read(compose.activity).removeChainStartDialog) }
    }

    @Test fun appearanceToggleSwitchesImmediatelyAndSurvivesRecreation() {
        compose.waitUntil(10_000) { compose.activity.settingsLoaded() }
        compose.onNodeWithTag("navigation_5").performClick().assertIsSelected()
        compose.onNodeWithTag("setting_毛玻璃悬浮底栏").assertExists().performClick()
        compose.runOnIdle { assertFalse(AppAppearanceSettings.floatingNavigation(compose.activity)) }
        compose.activityRule.scenario.recreate()
        compose.waitUntil(10_000) { compose.activity.settingsLoaded() }
        compose.onNodeWithTag("navigation_5").performClick()
        compose.onNodeWithTag("setting_毛玻璃悬浮底栏").performClick()
        compose.runOnIdle { assertTrue(AppAppearanceSettings.floatingNavigation(compose.activity)) }
    }
}
