package com.xtjm.fusionstatusbar

import android.content.ComponentName
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowDialog
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w360dp-h800dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DesktopComposeUiTest {
    @get:Rule(order = 0) val configuration = FusionTestConfigRule()
    @get:Rule(order = 1) val compose = createAndroidComposeRule<MainActivity>()

    @Before fun reset() {
        compose.waitUntil(10_000) { compose.activity.settingsLoaded() }
        compose.runOnIdle {
            DesktopConfig.save(compose.activity, DesktopConfig.defaults())
            page().refresh()
        }
    }

    @Test fun desktopUsesComposeAndMiuixControls() {
        compose.onNodeWithTag("desktop_page").assertIsDisplayed()
        compose.onNodeWithTag("desktop_hide_icons").performClick()
        compose.runOnIdle { assertTrue(DesktopConfig.read(compose.activity).hideIcons) }
        compose.onNodeWithTag("desktop_hidden_apps").assertExists()
        compose.onNodeWithTag("desktop_gesture_2").performScrollTo().assertExists()
    }

    @Test fun actionSelectionStaysDraftUntilDone() {
        gestureEditor()
        compose.onNodeWithTag("desktop_editor_action").performClick()
        compose.onNodeWithTag("desktop_action_2").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(DesktopConfig.ACTION_NATIVE,
            DesktopConfig.read(compose.activity).gestureAction(2)) }
        compose.onNodeWithTag("desktop_sheet_confirm").performClick()
        compose.runOnIdle { assertEquals(DesktopConfig.ACTION_NOTIFICATIONS,
            DesktopConfig.read(compose.activity).gestureAction(2)) }
    }

    @Test fun backHoldRowEditsAndRestoresNativeWithoutChangingOtherGestures() {
        compose.runOnIdle {
            DesktopConfig.save(compose.activity, DesktopConfig.defaults()
                .withGesture(DesktopConfig.GESTURE_SINGLE_UP, DesktopConfig.ACTION_NOTIFICATIONS))
            page().refresh()
        }
        compose.onNodeWithTag("desktop_gesture_10").performScrollTo().performClick()
        compose.onNodeWithTag("desktop_back_hold_summary").assertIsDisplayed()
        compose.onNodeWithTag("desktop_editor_action").performClick()
        compose.onNodeWithTag("desktop_action_0").assertExists()
        compose.onNodeWithTag("desktop_action_1").assertDoesNotExist()
        compose.onNodeWithTag("desktop_action_6").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(DesktopConfig.ACTION_NATIVE,
            DesktopConfig.read(compose.activity).gestureAction(DesktopConfig.GESTURE_BACK_HOLD)) }
        compose.onNodeWithTag("desktop_sheet_confirm").performClick()
        compose.runOnIdle {
            val saved = DesktopConfig.read(compose.activity)
            assertEquals(DesktopConfig.ACTION_SCREENSHOT, saved.gestureAction(DesktopConfig.GESTURE_BACK_HOLD))
            assertEquals(DesktopConfig.ACTION_NOTIFICATIONS, saved.gestureAction(DesktopConfig.GESTURE_SINGLE_UP))
        }

        compose.onNodeWithTag("desktop_gesture_10").performScrollTo().performClick()
        compose.onNodeWithTag("desktop_editor_action").performClick()
        compose.onNodeWithTag("desktop_action_0").performScrollTo().performClick()
        compose.onNodeWithTag("desktop_sheet_confirm").performClick()
        compose.runOnIdle {
            val saved = DesktopConfig.read(compose.activity)
            assertEquals(DesktopConfig.ACTION_NATIVE, saved.gestureAction(DesktopConfig.GESTURE_BACK_HOLD))
            assertEquals(DesktopConfig.ACTION_NOTIFICATIONS, saved.gestureAction(DesktopConfig.GESTURE_SINGLE_UP))
        }
    }

    @Test fun backHoldThresholdEditsStayDraftUntilDoneAndReopenWithSavedValues() {
        backHoldEditor()
        editBackHoldDistance("72")
        editBackHoldDelay("850")
        compose.onNodeWithTag("desktop_back_hold_summary").performScrollTo()
            .assertTextContains("72 dp", substring = true)
            .assertTextContains("850 毫秒", substring = true)
        compose.runOnIdle {
            val saved = DesktopConfig.read(compose.activity)
            assertEquals(DesktopConfig.BACK_HOLD_DISTANCE_SYSTEM, saved.backHoldDistanceDp)
            assertEquals(DesktopConfig.DEFAULT_BACK_HOLD_DELAY_MS, saved.backHoldDelayMs)
        }
        compose.onNodeWithTag("desktop_sheet_confirm").performClick()
        compose.runOnIdle {
            val saved = DesktopConfig.read(compose.activity)
            assertEquals(72, saved.backHoldDistanceDp)
            assertEquals(850, saved.backHoldDelayMs)
        }
        backHoldEditor()
        openBackHoldThreshold("desktop_back_hold_distance")
        compose.onNodeWithTag("desktop_back_hold_threshold_input").assertTextContains("72")
        compose.onNodeWithTag("desktop_sheet_confirm").performClick()
        openBackHoldThreshold("desktop_back_hold_delay")
        compose.onNodeWithTag("desktop_back_hold_threshold_input").assertTextContains("850")
    }

    @Test fun invalidBackHoldThresholdInputsCannotChangeDraftOrPersistentValues() {
        backHoldEditor()
        openBackHoldThreshold("desktop_back_hold_distance")
        compose.onNodeWithTag("desktop_back_hold_distance_system").performClick()
        for (invalid in listOf("", "abc", "15", "241", "-16", "16.5", "2147483648")) {
            compose.onNodeWithTag("desktop_back_hold_threshold_input").performTextReplacement(invalid)
            compose.onNodeWithTag("desktop_sheet_confirm").performClick()
            compose.onNodeWithTag("desktop_back_hold_threshold_error").performScrollTo().assertIsDisplayed()
        }
        compose.onNodeWithTag("desktop_sheet_cancel").performClick()
        openBackHoldThreshold("desktop_back_hold_delay")
        for (invalid in listOf("", "abc", "199", "3001", "-200", "200.5", "2147483648")) {
            compose.onNodeWithTag("desktop_back_hold_threshold_input").performTextReplacement(invalid)
            compose.onNodeWithTag("desktop_sheet_confirm").performClick()
            compose.onNodeWithTag("desktop_back_hold_threshold_error").performScrollTo().assertIsDisplayed()
        }
        compose.onNodeWithTag("desktop_sheet_cancel").performClick()
        compose.onNodeWithTag("desktop_sheet_confirm").performClick()
        compose.runOnIdle {
            val saved = DesktopConfig.read(compose.activity)
            assertEquals(DesktopConfig.BACK_HOLD_DISTANCE_SYSTEM, saved.backHoldDistanceDp)
            assertEquals(DesktopConfig.DEFAULT_BACK_HOLD_DELAY_MS, saved.backHoldDelayMs)
        }
    }

    @Test fun backHoldResetRestoresBothDefaultsOnlyAfterFinalConfirmation() {
        setBackHoldThresholds(88, 1100)
        backHoldEditor()
        compose.onNodeWithTag("desktop_back_hold_reset").performScrollTo().performClick()
        compose.onNodeWithTag("desktop_back_hold_summary").performScrollTo()
            .assertTextContains("跟随系统距离", substring = true)
            .assertTextContains("500 毫秒", substring = true)
        compose.runOnIdle {
            val saved = DesktopConfig.read(compose.activity)
            assertEquals(88, saved.backHoldDistanceDp)
            assertEquals(1100, saved.backHoldDelayMs)
        }
        compose.onNodeWithTag("desktop_sheet_confirm").performClick()
        compose.runOnIdle {
            val saved = DesktopConfig.read(compose.activity)
            assertEquals(DesktopConfig.BACK_HOLD_DISTANCE_SYSTEM, saved.backHoldDistanceDp)
            assertEquals(DesktopConfig.DEFAULT_BACK_HOLD_DELAY_MS, saved.backHoldDelayMs)
        }
    }

    @Test fun backHoldSubEditorsCanRestoreSystemDistanceAndDefaultDelay() {
        setBackHoldThresholds(80, 900)
        backHoldEditor()
        openBackHoldThreshold("desktop_back_hold_distance")
        compose.onNodeWithTag("desktop_back_hold_threshold_reset").performScrollTo().performClick()
        compose.onNodeWithTag("desktop_back_hold_threshold_input").assertDoesNotExist()
        compose.onNodeWithTag("desktop_sheet_confirm").performClick()
        openBackHoldThreshold("desktop_back_hold_delay")
        compose.onNodeWithTag("desktop_back_hold_threshold_reset").performScrollTo().performClick()
        compose.onNodeWithTag("desktop_back_hold_threshold_input").assertTextContains("500")
        compose.onNodeWithTag("desktop_sheet_confirm").performClick()
        compose.onNodeWithTag("desktop_sheet_confirm").performClick()
        compose.runOnIdle {
            val saved = DesktopConfig.read(compose.activity)
            assertEquals(DesktopConfig.BACK_HOLD_DISTANCE_SYSTEM, saved.backHoldDistanceDp)
            assertEquals(DesktopConfig.DEFAULT_BACK_HOLD_DELAY_MS, saved.backHoldDelayMs)
        }
    }

    @Test fun cancellingBackHoldEditorDiscardsBothThresholdDrafts() {
        backHoldEditor()
        editBackHoldDistance("96")
        editBackHoldDelay("1200")
        compose.onNodeWithTag("desktop_sheet_cancel").performClick()
        backHoldEditor()
        compose.onNodeWithTag("desktop_back_hold_summary")
            .assertTextContains("跟随系统距离", substring = true)
            .assertTextContains("500 毫秒", substring = true)
        compose.runOnIdle {
            val saved = DesktopConfig.read(compose.activity)
            assertEquals(DesktopConfig.BACK_HOLD_DISTANCE_SYSTEM, saved.backHoldDistanceDp)
            assertEquals(DesktopConfig.DEFAULT_BACK_HOLD_DELAY_MS, saved.backHoldDelayMs)
        }
    }

    @Test fun cancellingANumericSubEditorPreservesTheLastAcceptedDraftValue() {
        backHoldEditor()
        editBackHoldDistance("64")
        openBackHoldThreshold("desktop_back_hold_distance")
        compose.onNodeWithTag("desktop_back_hold_threshold_input").performTextReplacement("100")
        compose.onNodeWithTag("desktop_sheet_cancel").performClick()
        openBackHoldThreshold("desktop_back_hold_distance")
        compose.onNodeWithTag("desktop_back_hold_threshold_input").assertTextContains("64")
        compose.runOnIdle { assertEquals(DesktopConfig.BACK_HOLD_DISTANCE_SYSTEM,
            DesktopConfig.read(compose.activity).backHoldDistanceDp) }
    }

    @Test fun choosingActionAndApplicationPreservesBothThresholdDrafts() {
        install("test.threshold", "阈值测试应用")
        backHoldEditor()
        editBackHoldDistance("76")
        editBackHoldDelay("700")
        compose.onNodeWithTag("desktop_editor_action").performScrollTo().performClick()
        compose.onNodeWithTag("desktop_action_list").performScrollToNode(
            hasTestTag("desktop_action_${DesktopConfig.ACTION_LAUNCH_APP}"))
        compose.onNodeWithTag("desktop_action_${DesktopConfig.ACTION_LAUNCH_APP}").performClick()
        compose.onNodeWithTag("desktop_editor_app").performScrollTo().performClick()
        awaitApp("test.threshold")
        compose.onNodeWithTag("desktop_app_search").performTextInput("test.threshold")
        compose.onNodeWithTag("desktop_app_test.threshold").performClick()
        compose.onNodeWithTag("desktop_back_hold_summary").performScrollTo()
            .assertTextContains("76 dp", substring = true)
            .assertTextContains("700 毫秒", substring = true)
        compose.onNodeWithTag("desktop_sheet_confirm").performClick()
        compose.runOnIdle {
            val saved = DesktopConfig.read(compose.activity)
            assertEquals(76, saved.backHoldDistanceDp)
            assertEquals(700, saved.backHoldDelayMs)
            assertEquals("test.threshold|test.threshold.Main",
                saved.gestureTarget(DesktopConfig.GESTURE_BACK_HOLD))
        }
    }

    @Test fun otherGestureEditorsDoNotExposeBackHoldThresholds() {
        gestureEditor()
        compose.onNodeWithTag("desktop_back_hold_distance").assertDoesNotExist()
        compose.onNodeWithTag("desktop_back_hold_delay").assertDoesNotExist()
        compose.onNodeWithTag("desktop_back_hold_reset").assertDoesNotExist()
    }

    @Test fun backAppearancePresetAndArgbColorsSaveWithoutChangingHoldAction() {
        compose.runOnIdle {
            DesktopConfig.save(compose.activity, DesktopConfig.defaults()
                .withGesture(DesktopConfig.GESTURE_BACK_HOLD, DesktopConfig.ACTION_SCREENSHOT))
            page().refresh()
        }
        openAppearanceRow("desktop_back_style")
        chooseBackStyle(DesktopConfig.BACK_STYLE_NEON)
        compose.runOnIdle {
            assertEquals(DesktopConfig.BACK_STYLE_NEON, DesktopConfig.read(compose.activity).backGestureStyle)
        }
        openAppearanceRow("desktop_back_background")
        compose.onNodeWithTag("desktop_back_color_hex").performTextReplacement("#804488CC")
        compose.runOnIdle { assertEquals(DesktopConfig.backGesturePresetBackground(DesktopConfig.BACK_STYLE_NEON),
            DesktopConfig.read(compose.activity).backGestureBackgroundColor) }
        compose.onNodeWithTag("desktop_sheet_confirm").performClick()
        openAppearanceRow("desktop_back_arrow")
        compose.onNodeWithTag("desktop_back_color_hex").performTextReplacement("#12345")
        compose.onNodeWithTag("desktop_sheet_confirm").performClick()
        compose.onNodeWithTag("desktop_back_color_error").performScrollTo().assertIsDisplayed()
        compose.runOnIdle { assertEquals(DesktopConfig.backGesturePresetArrow(DesktopConfig.BACK_STYLE_NEON),
            DesktopConfig.read(compose.activity).backGestureArrowColor) }
        compose.onNodeWithTag("desktop_back_color_hex").performTextReplacement("44AACC")
        compose.onNodeWithTag("desktop_sheet_confirm").performClick()
        compose.runOnIdle {
            val saved = DesktopConfig.read(compose.activity)
            assertEquals(DesktopConfig.BACK_STYLE_NEON, saved.backGestureStyle)
            assertEquals(0x804488CC.toInt(), saved.backGestureBackgroundColor)
            assertEquals(0xFF44AACC.toInt(), saved.backGestureArrowColor)
            assertEquals(DesktopConfig.ACTION_SCREENSHOT, saved.gestureAction(DesktopConfig.GESTURE_BACK_HOLD))
        }
    }

    @Test fun nativeAppearanceColorEditActivatesClassicAndNativePresetRestoresDefaults() {
        openAppearanceRow("desktop_back_background")
        compose.onNodeWithTag("desktop_back_color_hex").performTextReplacement("#40223344")
        compose.onNodeWithTag("desktop_sheet_confirm").performClick()
        compose.runOnIdle {
            val saved = DesktopConfig.read(compose.activity)
            assertEquals(DesktopConfig.BACK_STYLE_CLASSIC, saved.backGestureStyle)
            assertEquals(0x40223344, saved.backGestureBackgroundColor)
        }
        openAppearanceRow("desktop_back_style")
        chooseBackStyle(DesktopConfig.BACK_STYLE_NATIVE)
        compose.runOnIdle { assertEquals(DesktopConfig.defaults(), DesktopConfig.read(compose.activity)) }
    }

    @Test fun rainbowPresetIsSelectableAsTheSixthChoiceAndKeepsHoldAction() {
        compose.runOnIdle {
            DesktopConfig.save(compose.activity, DesktopConfig.defaults()
                .withGesture(DesktopConfig.GESTURE_BACK_HOLD, DesktopConfig.ACTION_SCREENSHOT))
            page().refresh()
        }
        openAppearanceRow("desktop_back_style")
        chooseBackStyle(DesktopConfig.BACK_STYLE_RAINBOW)
        compose.runOnIdle {
            val saved = DesktopConfig.read(compose.activity)
            assertEquals(DesktopConfig.BACK_STYLE_RAINBOW, saved.backGestureStyle)
            assertEquals(0xD94F6BEA.toInt(), saved.backGestureBackgroundColor)
            assertEquals(0xFFFFFFFF.toInt(), saved.backGestureArrowColor)
            assertEquals(DesktopConfig.ACTION_SCREENSHOT,
                saved.gestureAction(DesktopConfig.GESTURE_BACK_HOLD))
        }
        openAppearanceRow("desktop_back_background")
        compose.onNodeWithTag("desktop_back_color_hex").performTextReplacement("#804477CC")
        compose.onNodeWithTag("desktop_sheet_confirm").performClick()
        compose.runOnIdle {
            val saved = DesktopConfig.read(compose.activity)
            assertEquals(DesktopConfig.BACK_STYLE_RAINBOW, saved.backGestureStyle)
            assertEquals(0x804477CC.toInt(), saved.backGestureBackgroundColor)
            assertEquals(DesktopConfig.ACTION_SCREENSHOT,
                saved.gestureAction(DesktopConfig.GESTURE_BACK_HOLD))
        }
        openAppearanceRow("desktop_back_style")
        compose.onNodeWithTag("desktop_back_style_list").performScrollToNode(
            hasTestTag("desktop_back_style_5"))
        compose.onNodeWithTag("desktop_back_style_5").assertIsSelected()
    }

    @Test fun appSearchKeepsFrozenApplicationsAndSavesTheFilteredTarget() {
        install("test.alpha", "普通应用")
        install("test.frozen", "冻结应用")
        compose.runOnIdle { compose.activity.packageManager.setApplicationEnabledSetting("test.frozen",
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER, 0) }
        chooseAction(DesktopConfig.ACTION_LAUNCH_APP)
        compose.onNodeWithTag("desktop_editor_app").performClick()
        awaitApp("test.frozen")
        compose.onNodeWithTag("desktop_app_search").performTextInput("  TEST.FROZEN  ")
        compose.onNodeWithTag("desktop_app_test.alpha").assertDoesNotExist()
        compose.onNodeWithTag("desktop_icon_test.frozen", useUnmergedTree = true).assertExists()
        compose.onNodeWithTag("desktop_app_test.frozen").performClick()
        compose.onNodeWithTag("desktop_sheet_confirm").performClick()
        compose.runOnIdle {
            assertEquals("test.frozen|test.frozen.Main", DesktopConfig.read(compose.activity).gestureTarget(2))
            assertEquals(PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER,
                compose.activity.packageManager.getApplicationEnabledSetting("test.frozen"))
        }
    }

    @Test fun hiddenChoicesSurviveSearchingAndClearing() {
        install("test.alpha", "甲应用")
        install("test.beta", "乙应用")
        compose.onNodeWithTag("desktop_hidden_apps").performClick()
        awaitApp("test.alpha")
        compose.onNodeWithTag("desktop_app_search").performTextInput("test.alpha")
        compose.onNodeWithTag("desktop_app_test.alpha").performClick()
        compose.onNodeWithTag("desktop_app_search").performTextReplacement("test.beta")
        compose.onNodeWithTag("desktop_app_test.beta").performClick()
        compose.onNodeWithTag("desktop_search_clear").performClick()
        compose.onNodeWithTag("desktop_app_search").performTextInput("test.alpha")
        compose.onNodeWithTag("desktop_app_test.alpha").assertIsSelected()
        compose.onNodeWithTag("desktop_sheet_confirm").performClick()
        compose.runOnIdle { assertEquals(setOf("test.alpha", "test.beta"),
            DesktopConfig.read(compose.activity).hiddenPackages()) }
    }

    @Test fun emptySearchCanBeClearedAndCancelDoesNotSave() {
        install("test.alpha", "甲应用")
        compose.onNodeWithTag("desktop_hidden_apps").performClick()
        awaitApp("test.alpha")
        compose.onNodeWithTag("desktop_app_search").performTextInput("missing.application")
        compose.onNodeWithTag("desktop_search_empty").assertExists()
        compose.onNodeWithTag("desktop_search_clear").performClick()
        compose.onNodeWithTag("desktop_app_search").performTextInput("test.alpha")
        compose.onNodeWithTag("desktop_app_test.alpha").performClick()
        compose.runOnIdle { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.runOnIdle { assertTrue(DesktopConfig.read(compose.activity).hiddenPackages().isEmpty()) }
    }

    @Test fun activitySelectionIncludesPackagesWithoutLauncherAndDisabledExportedActivities() {
        install("test.background", "后台工具", launcher = false, enabled = false)
        chooseAction(DesktopConfig.ACTION_LAUNCH_ACTIVITY)
        compose.onNodeWithTag("desktop_editor_activity_app").performClick()
        awaitApp("test.background")
        compose.onNodeWithTag("desktop_app_search").performTextInput("test.background")
        compose.onNodeWithTag("desktop_app_test.background").performClick()
        compose.onNodeWithTag("desktop_editor_activity").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("desktop_activity_test.background.Main")
            .fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("desktop_activity_test.background.Main").performClick()
        compose.onNodeWithTag("desktop_sheet_confirm").performClick()
        compose.runOnIdle { assertEquals("test.background|test.background.Main",
            DesktopConfig.read(compose.activity).gestureTarget(2)) }
    }

    @Test fun toggleDependencyPersistsDarkModeChoice() {
        chooseAction(DesktopConfig.ACTION_TOGGLE)
        compose.onNodeWithTag("desktop_editor_toggle").performClick()
        compose.onNodeWithTag("desktop_toggle_list").performScrollToNode(hasTestTag("desktop_toggle_12"))
        compose.onNodeWithTag("desktop_toggle_12").performClick()
        compose.onNodeWithTag("desktop_sheet_confirm").performClick()
        compose.runOnIdle { assertEquals(12, DesktopConfig.read(compose.activity).gestureToggle(2)) }
    }

    @Test fun incompleteAppTargetCannotBeSaved() {
        chooseAction(DesktopConfig.ACTION_LAUNCH_APP)
        compose.onNodeWithTag("desktop_sheet_confirm").performClick()
        compose.onNodeWithTag("desktop_editor_app").assertExists()
        compose.runOnIdle { assertEquals(DesktopConfig.ACTION_NATIVE,
            DesktopConfig.read(compose.activity).gestureAction(2)) }
    }

    @Test fun exportsMiuixDesktopAndApplicationSheet() {
        install("test.visible", "应用示例")
        val folder = File("build/reports/desktop-miuix").apply { mkdirs() }
        save("desktop.png", folder, captureView(compose.activity.window.decorView))
        compose.onNodeWithTag("desktop_hidden_apps").performClick()
        awaitApp("test.visible")
        compose.onNodeWithTag("desktop_app_search").performTextInput("test.visible")
        save("applications.png", folder, captureView(ShadowDialog.getLatestDialog().window!!.decorView))
    }

    private fun captureView(view: View): Bitmap {
        lateinit var bitmap: Bitmap
        compose.runOnIdle {
            assertTrue(view.width > 0 && view.height > 0)
            bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
        }
        return bitmap
    }

    private fun save(name: String, folder: File, bitmap: Bitmap) {
        File(folder, name).outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
    }
    private fun page(): DesktopSettingsPage = MainActivity::class.java.getDeclaredField("desktopSettingsPage")
        .apply { isAccessible = true }.get(compose.activity) as DesktopSettingsPage
    private fun gestureEditor() = compose.onNodeWithTag("desktop_gesture_2").performScrollTo().performClick()
    private fun backHoldEditor() = compose.onNodeWithTag("desktop_gesture_10").performScrollTo().performClick()
    private fun setBackHoldThresholds(distanceDp: Int, delayMs: Int) {
        compose.runOnIdle {
            DesktopConfig.save(compose.activity, DesktopConfig.defaults().withBackHoldThresholds(distanceDp, delayMs))
            page().refresh()
        }
    }
    private fun openBackHoldThreshold(tag: String) = compose.onNodeWithTag(tag).performScrollTo().performClick()
    private fun editBackHoldDistance(value: String) {
        openBackHoldThreshold("desktop_back_hold_distance")
        if (compose.onAllNodesWithTag("desktop_back_hold_threshold_input").fetchSemanticsNodes().isEmpty()) {
            compose.onNodeWithTag("desktop_back_hold_distance_system").performClick()
        }
        compose.onNodeWithTag("desktop_back_hold_threshold_input").performTextReplacement(value)
        compose.onNodeWithTag("desktop_sheet_confirm").performClick()
    }
    private fun editBackHoldDelay(value: String) {
        openBackHoldThreshold("desktop_back_hold_delay")
        compose.onNodeWithTag("desktop_back_hold_threshold_input").performTextReplacement(value)
        compose.onNodeWithTag("desktop_sheet_confirm").performClick()
    }
    private fun openAppearanceRow(tag: String) {
        compose.onNodeWithTag("desktop_page").performScrollToNode(hasTestTag(tag))
        compose.onNodeWithTag(tag).performScrollTo().performClick()
    }
    private fun chooseBackStyle(style: Int) {
        val tag = "desktop_back_style_$style"
        compose.onNodeWithTag("desktop_back_style_list").performScrollToNode(hasTestTag(tag))
        compose.onNodeWithTag(tag).performScrollTo().performClick()
    }
    private fun chooseAction(action: Int) {
        gestureEditor()
        compose.onNodeWithTag("desktop_editor_action").performClick()
        compose.onNodeWithTag("desktop_action_list").performScrollToNode(hasTestTag("desktop_action_$action"))
        compose.onNodeWithTag("desktop_action_$action").performClick()
    }
    private fun awaitApp(packageName: String) {
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("desktop_app_$packageName")
            .fetchSemanticsNodes().isNotEmpty() }
    }
    private fun install(packageName: String, label: String, launcher: Boolean = true, enabled: Boolean = true) {
        compose.runOnIdle {
            val app = ApplicationInfo().apply {
                this.packageName = packageName
                this.enabled = true
                flags = ApplicationInfo.FLAG_INSTALLED
                nonLocalizedLabel = label
            }
            val target = ActivityInfo().apply {
                this.packageName = packageName
                name = "$packageName.Main"
                applicationInfo = app
                nonLocalizedLabel = label
                this.enabled = enabled
                exported = true
            }
            val info = PackageInfo().apply { this.packageName = packageName; applicationInfo = app }
            val manager = Shadows.shadowOf(compose.activity.packageManager)
            manager.installPackage(info)
            manager.addOrUpdateActivity(target)
            if (launcher) manager.addIntentFilterForActivity(ComponentName(packageName, target.name),
                IntentFilter(Intent.ACTION_MAIN).apply { addCategory(Intent.CATEGORY_LAUNCHER) })
        }
    }
}
