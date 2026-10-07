package com.xtjm.fusionstatusbar

import androidx.activity.ComponentActivity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.height
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.Role
import top.yukonga.miuix.kmp.basic.NavigationBar
import top.yukonga.miuix.kmp.basic.NavigationBarDefaults
import top.yukonga.miuix.kmp.basic.NavigationBarItem
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.darkColorScheme
import top.yukonga.miuix.kmp.theme.lightColorScheme
import java.util.function.IntConsumer

class MiuixNavigation(activity: ComponentActivity, callback: IntConsumer) {
    private var selection by mutableIntStateOf(0)
    val view = ComposeView(activity).apply {
        id = R.id.miuix_navigation
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        setContent {
            MiuixTheme(colors = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()) {
                val icons = listOf(Icons.Default.Home, Icons.Default.Edit, Icons.Default.Notifications,
                    Icons.Default.Menu, Icons.Default.Settings)
                val labels = listOf("桌面", "状态栏", "通知栏", "控制中心", "设置")
                val largeText = LocalConfiguration.current.fontScale > 1.3f
                val displayLabels = labels.mapIndexed { index, label ->
                    if (largeText && index == 3) "控制\n中心" else label
                }
                BoxWithConstraints {
                    val density = LocalDensity.current
                    val measurer = rememberTextMeasurer()
                    val textHeight = displayLabels.maxOf { label ->
                        measurer.measure(label, style = MiuixTheme.textStyles.main.copy(
                            fontSize = NavigationBarDefaults.LabelFontSize, fontWeight = FontWeight.Bold),
                            constraints = Constraints(maxWidth = with(density) {
                                (maxWidth / labels.size).roundToPx().coerceAtLeast(1)
                            })).size.height
                    }
                    val itemHeight = maxOf(NavigationBarDefaults.ItemHeight, with(density) { textHeight.toDp() } +
                        NavigationBarDefaults.IconTopPadding + NavigationBarDefaults.IconSize +
                        NavigationBarDefaults.BottomPadding + 2.dp)
                    NavigationBar {
                        labels.forEachIndexed { index, label ->
                            NavigationBarItem(selected = selection == index, onClick = { callback.accept(index) },
                                icon = icons[index], label = displayLabels[index],
                                modifier = Modifier.height(itemHeight)
                                    .testTag("navigation_$index").semantics {
                                    selected = selection == index
                                    contentDescription = label
                                    role = Role.Tab
                                    onClick(label) { callback.accept(index); true }
                                })
                        }
                    }
                }
            }
        }
    }
    fun select(index: Int) { selection = index }
}
