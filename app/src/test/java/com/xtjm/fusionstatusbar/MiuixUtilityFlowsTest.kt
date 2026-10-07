package com.xtjm.fusionstatusbar

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.widget.ListView
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowDialog
import java.util.function.BooleanSupplier
import java.util.function.IntConsumer

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w360dp-h800dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MiuixUtilityFlowsTest {
    @get:Rule(order = 0) val configuration = FusionTestConfigRule()
    @get:Rule(order = 1) val compose = createAndroidComposeRule<MainActivity>()
    private lateinit var sheets: MiuixSheetHost
    @Before fun setup() {
        compose.waitUntil(10_000) { compose.activity.settingsLoaded() }
        compose.runOnIdle { sheets = MiuixSheetHost(compose.activity) }
    }
    @After fun close() { if (::sheets.isInitialized) compose.runOnIdle { sheets.close() } }

    @Test fun presetNameRejectsBlankAndSubmitsTrimmedNameOnlyOnConfirmation() {
        var name: String? = null
        compose.runOnIdle { sheets.input("保存预设", "预设名称", "", 40, "保存") { name = it } }
        settle()
        compose.onNodeWithTag("miuix_sheet_confirm").performClick()
        compose.onNodeWithTag("miuix_input_error").assertExists()
        assertNull(name)
        compose.onNodeWithTag("miuix_sheet_input").performTextInput("  我的布局  ")
        compose.onNodeWithTag("miuix_sheet_confirm").performClick()
        compose.runOnIdle { assertEquals("我的布局", name) }
    }

    @Test fun confirmationRejectsBusyActionAndBackDoesNotExecuteIt() {
        var calls = 0
        var allow = false
        compose.runOnIdle { sheets.confirm("导入配置", "配置预览", "恢复", BooleanSupplier { calls++; allow }) }
        settle()
        compose.onNodeWithTag("miuix_sheet_confirm").performClick()
        assertEquals(1, calls)
        compose.onNodeWithText("配置预览").assertExists()
        compose.runOnIdle { allow = true }
        compose.onNodeWithTag("miuix_sheet_confirm").performClick()
        compose.runOnIdle { assertEquals(2, calls) }
    }

    @Test fun optionsPreserveBooleanSelectionsAndProgressHasExplicitCancellation() {
        val selected = booleanArrayOf(false, false, false)
        var requests = 0
        compose.runOnIdle { sheets.options("诊断日志", "范围", arrayOf("完整日志", "内核日志", "模块日志"),
            selected, "导出", BooleanSupplier { requests++; true }) }
        settle()
        compose.onNodeWithTag("miuix_option_1").performClick()
        compose.onNodeWithTag("miuix_sheet_confirm").performClick()
        compose.runOnIdle { assertArrayEquals(booleanArrayOf(false, true, false), selected); assertEquals(1, requests) }
        compose.mainClock.advanceTimeBy(1000)
        var cancelled = false
        compose.runOnIdle { sheets.progress("正在导出", "可取消", "取消", Runnable { cancelled = true }) }
        settle()
        compose.onNodeWithTag("miuix_sheet_confirm").performClick()
        compose.runOnIdle { assertTrue(cancelled) }
    }

    @Test fun choiceAdapterRetainsIconsDisabledRowsAndCallsExactlyOnce() {
        var selections = 0
        val rows = SettingsListAdapter(compose.activity, arrayOf("允许", "禁用"),
            Color.WHITE, Color.BLACK, Color.BLUE, { ColorDrawable(Color.RED) })
        val adapter = object : android.widget.BaseAdapter() {
            override fun getCount(): Int = rows.count
            override fun getItem(position: Int): Any = rows.getItem(position)
            override fun getItemId(position: Int): Long = rows.getItemId(position)
            override fun getView(position: Int, convertView: android.view.View?, parent: android.view.ViewGroup?): android.view.View =
                rows.getView(position, convertView, parent)
            override fun isEnabled(position: Int): Boolean = position == 0
        }
        compose.runOnIdle { sheets.showAdapter("选择磁贴", adapter, IntConsumer { assertEquals(0, it); selections++ }) }
        settle()
        compose.onNodeWithTag("miuix_choice_1").assertIsNotEnabled()
        compose.onNodeWithTag("miuix_choice_0").performClick()
        compose.runOnIdle { assertEquals(1, selections) }
    }

    private fun settle() { compose.waitForIdle(); compose.mainClock.advanceTimeBy(1000); compose.waitForIdle() }
}
