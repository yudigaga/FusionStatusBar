package com.xtjm.fusionstatusbar

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w360dp-h800dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MiuixAllPagesUiTest {
    @get:Rule(order = 0) val configuration = FusionTestConfigRule()
    @get:Rule(order = 1) val compose = createAndroidComposeRule<MainActivity>()
    @Before fun ready() { compose.waitUntil(10_000) { compose.activity.settingsLoaded() } }

    @Test fun navigationAndStatusControlsUseMiuix() {
        compose.onNodeWithTag("navigation_1").performClick()
        compose.onNodeWithTag("preference_融合图标").performClick()
        compose.onNodeWithTag("setting_显示无线网络图标").performClick()
        compose.onNodeWithTag("slider_整体大小").performSemanticsAction(SemanticsActions.SetProgress) { it(60f) }
        compose.runOnIdle {
            val current = config()
            assertFalse(current.wifiIcon)
            assertEquals(110, current.iconScale)
        }
        compose.onNodeWithTag("miuix_sheet_confirm").performClick()
        compose.onNodeWithTag("navigation_2").performClick()
        compose.onNodeWithTag("preference_通知中心时间").assertExists()
        compose.onNodeWithTag("navigation_4").performClick()
        compose.onNodeWithTag("preference_重启系统桌面").assertExists()
    }

    @Test fun invalidClockFormatDoesNotReplaceSavedPattern() {
        compose.onNodeWithTag("navigation_1").performClick()
        compose.onNodeWithTag("preference_时间与天气").performClick()
        compose.onNodeWithTag("setting_自定义时间格式").performClick()
        val previous = compose.runOnIdle { config().clockPattern }
        compose.onNodeWithTag("setting_clock_pattern").performTextReplacement("HH:mm '")
        compose.onNodeWithText(compose.activity.getString(R.string.clock_format_invalid)).assertExists()
        compose.runOnIdle { assertEquals(previous, config().clockPattern) }
        compose.onNodeWithTag("setting_clock_pattern").performTextReplacement("HH:mm:ss")
        compose.runOnIdle { assertEquals("HH:mm:ss", config().clockPattern) }
    }

    @Test fun controlCenterChangesStayDraftUntilPush() {
        compose.onNodeWithTag("navigation_3").performClick()
        compose.onNodeWithTag("preference_布局与外观").performClick()
        compose.onNodeWithTag("setting_接管电源菜单").performClick()
        compose.runOnIdle {
            assertFalse(compose.activity.editorSession().draft().systemSurfaces.powerMenu)
            assertTrue(FusionConfigStore.read(compose.activity).controlCenter.systemSurfaces.powerMenu)
        }
        compose.onNodeWithTag("miuix_sheet_confirm").performClick()
        compose.mainClock.advanceTimeBy(1000)
        compose.waitForIdle()
        compose.runOnIdle {
            val scroll = MainActivity::class.java.getDeclaredField("pageScroll")
                .apply { isAccessible = true }.get(compose.activity) as android.widget.ScrollView
            scroll.isSmoothScrollingEnabled = false
            scroll.fullScroll(android.view.View.FOCUS_DOWN)
            val decor = compose.activity.window.decorView
            val bitmap = android.graphics.Bitmap.createBitmap(decor.width, decor.height,
                android.graphics.Bitmap.Config.ARGB_8888)
            decor.draw(android.graphics.Canvas(bitmap))
            bitmap.recycle()
        }
        compose.onNodeWithTag("command_推送控制中心修改到手机").assertIsDisplayed().performClick()
        compose.waitUntil(10_000) {
            !FusionConfigStore.read(compose.activity).controlCenter.systemSurfaces.powerMenu
        }
        compose.runOnIdle { assertFalse(FusionConfigStore.read(compose.activity).controlCenter.systemSurfaces.powerMenu) }
    }

    @Test fun notificationTimeUsesMiuixDependentControls() {
        compose.onNodeWithTag("navigation_2").performClick()
        compose.onNodeWithTag("preference_通知中心时间").performClick()
        compose.onNodeWithTag("setting_自定义通知中心时间").performClick()
        compose.onNodeWithTag("setting_clock_pattern").performTextReplacement("HH:mm\nMM/dd E")
        compose.runOnIdle {
            assertTrue(config().notificationClock.enabled)
            assertEquals("HH:mm\nMM/dd E", config().notificationClock.combinedPattern())
        }
    }

    private fun config(): FusionConfig = MainActivity::class.java.getDeclaredField("config")
        .apply { isAccessible = true }.get(compose.activity) as FusionConfig
}
