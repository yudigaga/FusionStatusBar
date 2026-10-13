package com.xtjm.fusionstatusbar

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w360dp-h800dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MiscSettingsUiTest {
    @get:Rule(order = 0) val configuration = FusionTestConfigRule()
    @get:Rule(order = 1) val compose = createAndroidComposeRule<MainActivity>()

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
