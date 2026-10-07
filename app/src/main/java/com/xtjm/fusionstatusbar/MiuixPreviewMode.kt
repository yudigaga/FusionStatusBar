package com.xtjm.fusionstatusbar

import android.widget.RadioButton
import android.widget.RadioGroup
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.theme.MiuixTheme

class MiuixPreviewMode(private val group: RadioGroup) {
    private var revision by mutableIntStateOf(0)
    val view = MiuixControls.view(group.context) {
        @Suppress("UNUSED_VARIABLE") val current = revision
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            repeat(group.childCount) { index ->
                val option = group.getChildAt(index) as? RadioButton ?: return@repeat
                TextButton(option.text.toString(), onClick = { group.check(option.id); invalidate() },
                    modifier = Modifier.weight(1f).testTag("preview_mode_$index"),
                    colors = if (option.id == group.checkedRadioButtonId) ButtonDefaults.textButtonColors(
                        color = MiuixTheme.colorScheme.primary, textColor = MiuixTheme.colorScheme.onPrimary)
                        else ButtonDefaults.textButtonColors())
            }
        }
    }
    fun invalidate() { revision++ }
}
