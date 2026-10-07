package com.xtjm.fusionstatusbar

import android.view.ViewGroup
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.extra.SuperArrow
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.basic.Check
import top.yukonga.miuix.kmp.theme.MiuixTheme

internal class MiuixChoiceBinding(private val selector: SettingsChoiceSelector) {
    private var revision by mutableIntStateOf(0)
    val view = MiuixControls.view(selector.context) {
        @Suppress("UNUSED_VARIABLE") val current = revision
        val title = selector.contentDescription.toString()
        val labels = selector.choiceLabels()
        Column(Modifier.fillMaxWidth().testTag("choice_$title")) {
            SuperArrow(title = title, summary = labels[selector.selectedIndex()],
                enabled = selector.isEnabled,
                modifier = Modifier.semantics { contentDescription = "当前选择" },
                onClick = { selector.performClick() })
            if (selector.expanded()) {
                labels.forEachIndexed { index, label ->
                    BasicComponent(title = label, enabled = selector.optionEnabled(index),
                        modifier = Modifier.testTag("choice_${title}_$index").semantics {
                            selected = index == selector.selectedIndex()
                            if (!selector.optionEnabled(index)) disabled()
                        }, startAction = selector.decoration(index)?.let { decoration -> {
                            AndroidView(factory = {
                                (decoration.parent as? ViewGroup)?.removeView(decoration)
                                decoration
                            }, modifier = Modifier.size(40.dp, 28.dp))
                        } }, endActions = {
                            Box(Modifier.size(24.dp)) {
                                if (index == selector.selectedIndex()) Icon(MiuixIcons.Basic.Check, null,
                                    tint = MiuixTheme.colorScheme.primary)
                            }
                        }, onClick = { selector.selectOption(index) })
                }
            }
        }
    }
    fun invalidate() { revision++ }
}
