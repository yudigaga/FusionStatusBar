package com.xtjm.fusionstatusbar

import android.view.View
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import top.yukonga.miuix.kmp.basic.NavigationBarDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.darkColorScheme
import top.yukonga.miuix.kmp.theme.lightColorScheme
import java.util.function.IntConsumer
import kotlin.math.roundToInt

class MiuixNavigation(activity: ComponentActivity, callback: IntConsumer) {
    private var selection by mutableIntStateOf(0)
    private var floatingEnabled by mutableStateOf(false)
    private var bottomInset = 0
    private var heightListener: IntConsumer? = null
    private val host = NavigationBackdropHost(activity)
    val view: FrameLayout get() = host

    init {
        host.id = R.id.miuix_navigation
        val content = ComposeView(activity).apply {
            elevation = dp(8).toFloat()
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                val night = isSystemInDarkTheme()
                MiuixTheme(colors = if (night) darkColorScheme() else lightColorScheme()) {
                    SideEffect { host.setNightMode(night) }
                    val icons = listOf(Icons.Default.Home, Icons.Default.Edit, Icons.Default.Notifications,
                        Icons.Default.Menu, Icons.Default.MoreVert, Icons.Default.Settings)
                    val labels = listOf("桌面", "状态栏", "通知栏", "控制中心", "杂项", "设置")
                    val largeText = LocalConfiguration.current.fontScale > 1.3f
                    BoxWithConstraints {
                        val density = LocalDensity.current
                        val measurer = rememberTextMeasurer()
                        val labelWidth = with(density) {
                            ((maxWidth - 8.dp) / labels.size - 6.dp).roundToPx().coerceAtLeast(1)
                        }
                        val baseStyle = MiuixTheme.textStyles.main.copy(
                            fontSize = NavigationBarDefaults.LabelFontSize, fontWeight = FontWeight.Bold,
                            letterSpacing = 0.sp)
                        val controlWidth = measurer.measure(labels[3], style = baseStyle).size.width
                        val displayLabels = labels.mapIndexed { index, label ->
                            if (index == 3 && (largeText || controlWidth > labelWidth)) "控制\n中心" else label
                        }
                        var fontSize = NavigationBarDefaults.LabelFontSize.value
                        while (fontSize > 8f && displayLabels.any { label ->
                                label.lineSequence().any {
                                    measurer.measure(it, style = baseStyle.copy(fontSize = fontSize.sp))
                                        .size.width > labelWidth
                                }
                            }) fontSize -= 0.5f
                        val textHeight = displayLabels.maxOf { label ->
                            measurer.measure(label, style = baseStyle.copy(fontSize = fontSize.sp),
                                constraints = Constraints(maxWidth = labelWidth)).size.height
                        }
                        val itemHeight = maxOf(NavigationBarDefaults.ItemHeight,
                            with(density) { textHeight.toDp() } + NavigationBarDefaults.IconSize + 20.dp)
                        Row(Modifier.fillMaxWidth()
                            .background(if (floatingEnabled) Color.Transparent else MiuixTheme.colorScheme.surface)
                            .padding(horizontal = 4.dp)) {
                            labels.forEachIndexed { index, label ->
                                val selected = selection == index
                                val tint = if (floatingEnabled && selected) Color(0xFF3478F6)
                                    else MiuixTheme.colorScheme.onSurfaceContainer.copy(
                                        alpha = if (selected || floatingEnabled) 1f else 0.4f)
                                val selectedFill = if (floatingEnabled && selected)
                                    if (night) Color(0x33FFFFFF) else Color(0x22747480) else Color.Transparent
                                Box(Modifier.weight(1f).height(itemHeight)
                                    .padding(horizontal = 2.dp, vertical = if (floatingEnabled) 4.dp else 0.dp)
                                    .background(selectedFill, RoundedCornerShape(50))
                                    .testTag("navigation_$index")
                                    .selectable(selected = selected, role = Role.Tab,
                                        onClick = { callback.accept(index) })
                                    .semantics { contentDescription = label }, contentAlignment = Alignment.Center) {
                                    Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Center) {
                                        Image(imageVector = icons[index], contentDescription = null,
                                            colorFilter = ColorFilter.tint(tint),
                                            modifier = Modifier.size(NavigationBarDefaults.IconSize))
                                        Spacer(Modifier.height(2.dp))
                                        Text(displayLabels[index], modifier = Modifier.fillMaxWidth(), color = tint, textAlign = TextAlign.Center,
                                            fontSize = fontSize.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                            style = baseStyle.copy(fontSize = fontSize.sp),
                                            letterSpacing = 0.sp, maxLines = 2, softWrap = false)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        host.addView(content, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.WRAP_CONTENT))
        ViewCompat.setOnApplyWindowInsetsListener(host) { _, insets ->
            val safeInsets = insets.getInsets(WindowInsetsCompat.Type.navigationBars() or
                WindowInsetsCompat.Type.displayCutout())
            bottomInset = safeInsets.bottom
            updatePadding(safeInsets.left, safeInsets.right)
            insets
        }
        host.addOnLayoutChangeListener { _, _, _, _, bottom, _, _, _, oldBottom ->
            if (bottom != oldBottom || heightListener != null) notifyHeight()
        }
    }

    fun select(index: Int) { selection = index.coerceIn(0, 5) }

    fun setFloating(enabled: Boolean) {
        if (floatingEnabled == enabled) return
        floatingEnabled = enabled
        host.setFloating(enabled)
        val safeInsets = ViewCompat.getRootWindowInsets(host)?.getInsets(
            WindowInsetsCompat.Type.navigationBars() or WindowInsetsCompat.Type.displayCutout())
        bottomInset = safeInsets?.bottom ?: bottomInset
        updatePadding(safeInsets?.left ?: 0, safeInsets?.right ?: 0)
    }

    fun isFloating(): Boolean = floatingEnabled

    /** Pass the common page container; the navigation host excludes itself during capture. */
    fun setBackdropSource(source: View) { host.setBackdropSource(source) }

    fun setOnReservedHeightChanged(listener: IntConsumer) {
        heightListener = listener
        if (host.height > 0) notifyHeight()
    }

    fun setHeightListener(listener: IntConsumer) { setOnReservedHeightChanged(listener) }

    fun getReservedHeight(): Int = host.height + dp(8)

    private fun updatePadding(leftInset: Int, rightInset: Int) {
        val side = if (floatingEnabled) dp(12) else 0
        host.setPadding(leftInset + side, if (floatingEnabled) dp(6) else 0, rightInset + side,
            bottomInset + if (floatingEnabled) dp(12) else 0)
        host.requestLayout()
    }

    private fun notifyHeight() { heightListener?.accept(getReservedHeight()) }
    private fun dp(value: Int): Int = (value * host.resources.displayMetrics.density).roundToInt()
}
