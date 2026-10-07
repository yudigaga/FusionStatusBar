package com.xtjm.fusionstatusbar

import android.app.Dialog
import android.graphics.drawable.Drawable
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.FrameLayout
import android.widget.ListAdapter
import android.widget.ListView
import androidx.activity.ComponentActivity
import androidx.compose.foundation.focusable
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.DialogWindowProvider
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.extra.WindowBottomSheet
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.basic.Check
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.LocalContentColor
import top.yukonga.miuix.kmp.theme.darkColorScheme
import top.yukonga.miuix.kmp.theme.lightColorScheme
import androidx.compose.foundation.isSystemInDarkTheme
import java.util.function.BooleanSupplier
import java.util.function.IntConsumer
import java.util.function.Consumer
import java.util.function.IntFunction
import androidx.core.graphics.drawable.toBitmap

/** Window ownership shared by Compose pages and the existing Java controller callbacks. */
class MiuixSheetHost(private val activity: ComponentActivity) : AutoCloseable {
    private val sheets = mutableStateListOf<Sheet>()
    private var host: ComposeView? = null
    private var closed = false

    private class Sheet(val title: String, val label: String, val confirm: (() -> Boolean)?,
                        val content: @Composable () -> Unit) {
        var visible by mutableStateOf(true)
        var window: Window? = null
        var dismissible = true
        lateinit var handle: Dialog
    }

    private inner class Handle(private val sheet: Sheet) : Dialog(activity) {
        override fun isShowing(): Boolean = sheet.visible && !closed
        override fun show() { if (!closed) sheet.visible = true }
        override fun dismiss() { sheet.visible = false }
        override fun cancel() { dismiss() }
        override fun getWindow(): Window? = sheet.window ?: super.getWindow()
    }

    internal fun show(title: String, label: String = activity.getString(R.string.editor_done),
                      confirm: (() -> Boolean)? = null, content: @Composable () -> Unit): Dialog {
        val sheet = Sheet(title, label, confirm, content)
        sheet.handle = Handle(sheet)
        if (closed || activity.isDestroyed || activity.isFinishing) return sheet.handle
        ensureHost()
        sheets.add(sheet)
        host?.apply { visibility = View.VISIBLE; bringToFront() }
        return sheet.handle
    }

    fun showNative(title: String, content: View, label: String, confirm: BooleanSupplier?): Dialog =
        show(title, label, confirm?.let { { it.asBoolean } }) {
            val limit = (LocalConfiguration.current.screenHeightDp * .65f).dp
            Box(Modifier.heightIn(max = limit).verticalScroll(rememberScrollState())) {
                AndroidView(factory = {
                    (content.parent as? ViewGroup)?.removeView(content)
                    content
                }, modifier = Modifier.fillMaxWidth())
            }
        }

    fun showChoices(title: String, labels: Array<String>, selection: IntConsumer,
                    selected: Int, icons: IntFunction<Drawable?>?): Dialog {
        lateinit var handle: Dialog
        handle = show(title, activity.getString(R.string.backup_cancel)) {
            LazyColumn(Modifier.fillMaxWidth().heightIn(max =
                (LocalConfiguration.current.screenHeightDp * .62f).dp).testTag("miuix_choice_list")) {
                itemsIndexed(labels.toList()) { index, label ->
                    val icon = remember(index) { icons?.apply(index)?.toBitmap(84, 84)?.asImageBitmap() }
                    BasicComponent(title = label,
                        modifier = Modifier.testTag("miuix_choice_$index").semantics { this.selected = index == selected },
                        insideMargin = PaddingValues(vertical = 12.dp),
                        startAction = icon?.let { { Image(it, null, Modifier.padding(end = 12.dp).size(28.dp)) } },
                        endActions = { Box(Modifier.size(24.dp)) {
                            if (index == selected) Icon(MiuixIcons.Basic.Check, null,
                                tint = MiuixTheme.colorScheme.primary)
                        } }, onClick = { handle.dismiss(); selection.accept(index) })
                }
            }
        }
        return handle
    }

    fun showChoices(title: String, labels: Array<String>, selection: IntConsumer,
                    selected: Int): Dialog = showChoices(title, labels, selection, selected, null)

    fun showAdapter(title: String, adapter: ListAdapter, selection: IntConsumer): Dialog {
        val model = ListView(activity).apply { id = android.R.id.list; this.adapter = adapter }
        lateinit var handle: Dialog
        model.setOnItemClickListener { _, _, position, _ ->
            if (handle.isShowing && adapter.isEnabled(position)) {
                handle.dismiss()
                selection.accept(position)
            }
        }
        handle = show(title, activity.getString(R.string.backup_cancel)) {
            // The Java adapter remains the source of row identity and callbacks.
            AndroidView(factory = { model }, modifier = Modifier.size(0.dp))
            LazyColumn(Modifier.fillMaxWidth().heightIn(max =
                (LocalConfiguration.current.screenHeightDp * .62f).dp).testTag("miuix_choice_list")) {
                itemsIndexed((0 until adapter.count).toList()) { _, index ->
                    val bitmap = remember(index) {
                        (adapter as? SettingsListAdapter)?.iconAt(index)?.toBitmap(84, 84)?.asImageBitmap()
                    }
                    BasicComponent(title = adapter.getItem(index).toString(), enabled = adapter.isEnabled(index),
                        modifier = Modifier.testTag("miuix_choice_$index").semantics {
                            if (!adapter.isEnabled(index)) disabled()
                        }, insideMargin = PaddingValues(vertical = 12.dp),
                        startAction = bitmap?.let { { Image(it, null, Modifier.padding(end = 12.dp).size(28.dp)) } },
                        onClick = { model.performItemClick(null, index, adapter.getItemId(index)) })
                }
            }
        }
        return handle
    }

    fun confirm(title: String, message: String, label: String, action: BooleanSupplier): Dialog =
        show(title, label, { action.asBoolean }) {
            Text(message, modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                color = MiuixTheme.colorScheme.onSurfaceSecondary)
        }

    fun input(title: String, hint: String, initial: String, maxLength: Int,
              label: String, submit: Consumer<String>): Dialog {
        var value by mutableStateOf(initial)
        var invalid by mutableStateOf(false)
        return show(title, label, {
            if (value.trim().isEmpty()) { invalid = true; false }
            else { submit.accept(value.trim()); true }
        }) {
            Column(Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
                TextField(value = value, onValueChange = {
                    value = it.take(maxLength)
                    invalid = false
                }, singleLine = true, label = hint, useLabelAsPlaceholder = true,
                    textStyle = MiuixTheme.textStyles.main.copy(color = MiuixTheme.colorScheme.onSurface),
                    modifier = Modifier.fillMaxWidth().testTag("miuix_sheet_input"))
                if (invalid) Text(activity.getString(R.string.preset_invalid_name),
                    color = Color(0xffc93434), modifier = Modifier.testTag("miuix_input_error"))
            }
        }
    }

    fun options(title: String, message: String, labels: Array<String>, checked: BooleanArray,
                label: String, action: BooleanSupplier): Dialog {
        val state = checked.map { mutableStateOf(it) }
        return show(title, label, { action.asBoolean }) {
            Column(Modifier.fillMaxWidth()) {
                Text(message, color = MiuixTheme.colorScheme.onSurfaceSecondary,
                    modifier = Modifier.padding(vertical = 12.dp))
                labels.forEachIndexed { index, title ->
                    top.yukonga.miuix.kmp.extra.SuperCheckbox(title = title, checked = state[index].value,
                        onCheckedChange = { state[index].value = it; checked[index] = it },
                        modifier = Modifier.testTag("miuix_option_$index"))
                }
            }
        }
    }

    fun progress(title: String, message: String, cancelLabel: String, cancel: Runnable): Dialog {
        val handle = show(title, cancelLabel, { cancel.run(); true }) {
            Column(Modifier.fillMaxWidth().padding(vertical = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator()
                Text(message, color = MiuixTheme.colorScheme.onSurfaceSecondary,
                    modifier = Modifier.padding(top = 16.dp))
            }
        }
        sheets.firstOrNull { it.handle === handle }?.dismissible = false
        return handle
    }

    private fun ensureHost() {
        if (host != null) return
        host = ComposeView(activity).apply {
            id = View.generateViewId()
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                MiuixTheme(colors = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()) {
                    CompositionLocalProvider(LocalContentColor provides MiuixTheme.colorScheme.onSurface) {
                        Box(Modifier.fillMaxSize()) {
                            sheets.toList().forEach { sheet -> key(sheet) { RenderSheet(sheet) } }
                        }
                    }
                }
            }
            activity.findViewById<FrameLayout>(android.R.id.content).addView(this, FrameLayout.LayoutParams(-1, -1))
        }
    }

    @Composable private fun RenderSheet(sheet: Sheet) {
        WindowBottomSheet(show = sheet.visible, cornerRadius = 28.dp, sheetMaxWidth = 420.dp,
            allowDismiss = sheet.dismissible,
            insideMargin = DpSize(22.dp, 18.dp), onDismissRequest = { sheet.handle.dismiss() },
            onDismissFinished = {
                sheets.remove(sheet)
                if (sheets.isEmpty()) host?.visibility = View.GONE
            }) {
            var current: Any? = LocalView.current
            while (current is View && current !is DialogWindowProvider) current = current.parent
            (current as? DialogWindowProvider)?.let { sheet.window = it.window }
            val focus = remember { FocusRequester() }
            val keyboard = LocalSoftwareKeyboardController.current
            LaunchedEffect(sheet) { focus.requestFocus(); keyboard?.hide() }
            Row(Modifier.fillMaxWidth().focusRequester(focus).focusable(),
                verticalAlignment = Alignment.CenterVertically) {
                Text(sheet.title, modifier = Modifier.weight(1f), fontSize = 22.sp,
                    fontWeight = FontWeight.Bold)
                TextButton(sheet.label,
                    onClick = { if (sheet.confirm?.invoke() != false) sheet.handle.dismiss() },
                    modifier = Modifier.widthIn(min = 64.dp).testTag("miuix_sheet_confirm"),
                    colors = ButtonDefaults.textButtonColors(color = Color.Transparent,
                        textColor = MiuixTheme.colorScheme.primary))
            }
            sheet.content()
        }
    }

    override fun close() {
        closed = true
        sheets.forEach { it.visible = false }
        sheets.clear()
        host?.disposeComposition()
        (host?.parent as? ViewGroup)?.removeView(host)
        host = null
    }
}
