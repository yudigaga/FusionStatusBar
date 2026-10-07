package com.xtjm.fusionstatusbar

import android.content.Context
import android.text.Editable
import android.text.TextWatcher
import android.graphics.drawable.Drawable
import android.view.View
import android.widget.*
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.basic.Text as MiuixText
import top.yukonga.miuix.kmp.basic.TextField as MiuixTextField
import top.yukonga.miuix.kmp.extra.SuperSwitch
import top.yukonga.miuix.kmp.extra.SuperArrow
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.LocalContentColor
import top.yukonga.miuix.kmp.theme.darkColorScheme
import top.yukonga.miuix.kmp.theme.lightColorScheme
import java.util.function.Consumer
import java.util.function.IntConsumer
import kotlin.math.roundToInt
import androidx.core.graphics.drawable.toBitmap

/** Keeps the Java editor's typed control callbacks while Compose owns the visible controls. */
object MiuixControls {
    class Toggle(context: Context) : android.widget.Switch(context) {
        private var state: MutableState<Boolean>? = null
        private var available: MutableState<Boolean>? = null
        init { state = mutableStateOf(isChecked); available = mutableStateOf(isEnabled) }
        val value get() = state!!.value
        val active get() = available!!.value
        override fun setChecked(checked: Boolean) { super.setChecked(checked); state?.value = isChecked }
        override fun setEnabled(enabled: Boolean) { super.setEnabled(enabled); available?.value = enabled }
    }

    class Range(context: Context) : SeekBar(context) {
        private var state: MutableState<Int>? = null
        private var available: MutableState<Boolean>? = null
        private var callback: OnSeekBarChangeListener? = null
        init { state = mutableStateOf(progress); available = mutableStateOf(isEnabled) }
        val value get() = state!!.value
        val active get() = available!!.value
        override fun setProgress(progress: Int) { super.setProgress(progress); state?.value = getProgress() }
        override fun setEnabled(enabled: Boolean) { super.setEnabled(enabled); available?.value = enabled }
        override fun setOnSeekBarChangeListener(listener: OnSeekBarChangeListener?) {
            callback = listener
            super.setOnSeekBarChangeListener(listener)
        }
        fun changeFromUi(progress: Int) {
            if (!isEnabled) return
            setProgress(progress)
            callback?.onProgressChanged(this, getProgress(), true)
        }
        fun finishFromUi() { callback?.onStopTrackingTouch(this) }
    }

    class Input(context: Context) : EditText(context) {
        private var state: MutableState<String>? = null
        private var available: MutableState<Boolean>? = null
        private var errorMessage: MutableState<String?>? = null
        init { state = mutableStateOf(text.toString()); available = mutableStateOf(isEnabled); errorMessage = mutableStateOf(null) }
        val value get() = state!!.value
        val active get() = available!!.value
        val problem get() = errorMessage!!.value
        override fun setText(text: CharSequence?, type: TextView.BufferType?) {
            super.setText(text, type)
            state?.value = getText().toString()
        }
        override fun setEnabled(enabled: Boolean) { super.setEnabled(enabled); available?.value = enabled }
        override fun setError(error: CharSequence?, icon: Drawable?) {
            super.setError(error, icon)
            errorMessage?.value = error?.toString()
        }
    }

    class Label(context: Context) : TextView(context) {
        private var state: MutableState<String>? = null
        init { state = mutableStateOf(text.toString()) }
        val value get() = state!!.value
        override fun setText(text: CharSequence?, type: BufferType?) {
            super.setText(text, type)
            state?.value = getText().toString()
        }
    }

    class Command(context: Context) : android.widget.Button(context) {
        private var state: MutableState<String>? = null
        private var available: MutableState<Boolean>? = null
        private var prominent: MutableState<Boolean?>? = null
        private var opacity: MutableState<Float>? = null
        init { state = mutableStateOf(text.toString()); available = mutableStateOf(isEnabled); prominent = mutableStateOf(null); opacity = mutableStateOf(alpha) }
        val label get() = state!!.value
        val active get() = available!!.value
        val emphasized get() = prominent!!.value
        val opacityValue get() = opacity!!.value
        fun primary(value: Boolean) { prominent?.value = value }
        override fun setText(text: CharSequence?, type: TextView.BufferType?) {
            super.setText(text, type)
            state?.value = getText().toString()
        }
        override fun setEnabled(enabled: Boolean) { super.setEnabled(enabled); available?.value = enabled }
        override fun setAlpha(alpha: Float) { super.setAlpha(alpha); opacity?.value = alpha }
    }

    @JvmStatic fun preference(context: Context, title: String, summary: String,
                              listener: View.OnClickListener): LinearLayout {
        val row = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        val value = Label(context).apply { text = summary }
        val labels = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            addView(TextView(context).apply { text = title })
            addView(value)
            visibility = View.GONE
        }
        row.addView(labels, LinearLayout.LayoutParams(-1, -2))
        row.tag = value
        row.contentDescription = title
        row.setOnClickListener(listener)
        row.addView(view(context) {
            SuperArrow(title = title, summary = value.value.takeIf { it.isNotEmpty() },
                modifier = Modifier.testTag("preference_$title"), onClick = { row.performClick() })
        }, LinearLayout.LayoutParams(-1, -2))
        return row
    }

    @JvmStatic fun attachButton(parent: LinearLayout, command: android.widget.Button,
                                primary: Boolean, params: LinearLayout.LayoutParams) {
        val holder = LinearLayout(parent.context).apply { orientation = LinearLayout.VERTICAL }
        command.visibility = View.GONE
        holder.addView(command, LinearLayout.LayoutParams(0, 0))
        holder.addView(view(parent.context) {
            val bound = command as? Command
            TextButton(text = bound?.label ?: command.text.toString(),
                enabled = bound?.active ?: command.isEnabled,
                modifier = Modifier.fillMaxWidth().alpha(bound?.opacityValue ?: command.alpha)
                    .testTag("command_${command.contentDescription ?: command.text}"),
                colors = if (bound?.emphasized ?: primary) ButtonDefaults.textButtonColors(color = MiuixTheme.colorScheme.primary,
                    textColor = MiuixTheme.colorScheme.onPrimary) else ButtonDefaults.textButtonColors(),
                onClick = { command.performClick() })
        }, LinearLayout.LayoutParams(-1, -2))
        parent.addView(holder, params)
    }

    @JvmStatic fun buttonVisibility(button: android.widget.Button, visibility: Int) {
        (button.parent as? LinearLayout)?.visibility = visibility
    }

    @JvmStatic fun hint(parent: LinearLayout, message: String) {
        parent.addView(view(parent.context) {
            MiuixText(message, fontSize = 12.sp, color = MiuixTheme.colorScheme.onSurfaceSecondary,
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp))
        }, LinearLayout.LayoutParams(-1, -2))
    }

    @JvmStatic fun startup(context: Context, message: String): View = view(context) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator()
                MiuixText(message, color = MiuixTheme.colorScheme.onSurfaceSecondary,
                    modifier = Modifier.padding(top = 16.dp).testTag("startup_loading"))
            }
        }
    }

    @JvmStatic fun icon(parent: LinearLayout, resource: Int, description: String,
                        enabled: Boolean, action: Runnable) {
        val model = ImageButton(parent.context).apply {
            contentDescription = description
            isEnabled = enabled
            visibility = View.GONE
            setOnClickListener { action.run() }
        }
        val holder = LinearLayout(parent.context).apply { orientation = LinearLayout.VERTICAL }
        holder.addView(model, LinearLayout.LayoutParams(0, 0))
        holder.addView(view(parent.context) {
            val drawable = remember(resource) { parent.context.getDrawable(resource) }
            val bitmap = remember(resource) { drawable?.toBitmap(48, 48) }
            IconButton(onClick = { model.performClick() }, enabled = enabled,
                modifier = Modifier.size(48.dp).testTag("tool_$description")) {
                bitmap?.let { Icon(it.asImageBitmap(), description,
                    tint = MiuixTheme.colorScheme.onSurface, modifier = Modifier.size(20.dp)) }
            }
        }, LinearLayout.LayoutParams(-1, -1))
        parent.addView(holder, LinearLayout.LayoutParams((48 * parent.resources.displayMetrics.density).roundToInt(),
            (48 * parent.resources.displayMetrics.density).roundToInt()))
    }

    @JvmStatic fun heading(context: Context, title: String): android.view.View = view(context) {
        MiuixText(title, fontSize = 28.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 6.dp))
    }

    @JvmStatic fun section(context: Context, title: String): android.view.View = view(context) {
        SmallTitle(title, insideMargin = PaddingValues(top = 24.dp, bottom = 8.dp))
    }

    @JvmStatic fun detail(parent: LinearLayout, title: String, description: String): TextView {
        val value = Label(parent.context).apply { text = description; visibility = View.GONE }
        val holder = LinearLayout(parent.context).apply { orientation = LinearLayout.VERTICAL }
        holder.addView(value, LinearLayout.LayoutParams(0, 0))
        holder.addView(view(parent.context) {
            BasicComponent(title = title, summary = value.value)
        }, LinearLayout.LayoutParams(-1, -2))
        parent.addView(holder, LinearLayout.LayoutParams(-1, -2))
        return value
    }

    @JvmStatic fun status(parent: LinearLayout, title: Label, summary: Label, indicator: View) {
        val holder = LinearLayout(parent.context).apply { orientation = LinearLayout.VERTICAL }
        title.visibility = View.GONE
        summary.visibility = View.GONE
        holder.addView(title, LinearLayout.LayoutParams(0, 0))
        holder.addView(summary, LinearLayout.LayoutParams(0, 0))
        holder.addView(view(parent.context) {
            BasicComponent(title = title.value, summary = summary.value, startAction = {
                androidx.compose.ui.viewinterop.AndroidView(factory = { indicator },
                    modifier = Modifier.padding(end = 14.dp).size(12.dp))
            })
        }, LinearLayout.LayoutParams(-1, -2))
        parent.addView(holder, LinearLayout.LayoutParams(-1, -2))
    }

    @JvmStatic fun toggle(content: LinearLayout, title: String, summary: String, checked: Boolean,
                          update: Consumer<Boolean>): android.widget.Switch {
        val context = content.context
        val row = LinearLayout(context)
        row.orientation = LinearLayout.VERTICAL
        val labels = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            addView(TextView(context).apply { text = title })
            addView(TextView(context).apply { text = summary })
            visibility = View.GONE
        }
        row.addView(labels, LinearLayout.LayoutParams(-1, -2))
        val model = Toggle(context).apply {
            contentDescription = title
            isChecked = checked
            visibility = View.GONE
            setOnCheckedChangeListener { _, value -> update.accept(value) }
        }
        row.addView(model, LinearLayout.LayoutParams(0, 0))
        row.addView(view(context) {
            SuperSwitch(title = title, summary = summary.takeIf { it.isNotEmpty() },
                checked = model.value, enabled = model.active,
                modifier = Modifier.testTag("setting_$title"),
                onCheckedChange = { model.isChecked = it })
        }, LinearLayout.LayoutParams(-1, -2))
        content.addView(row, LinearLayout.LayoutParams(-1, -2))
        return model
    }

    @JvmStatic fun slider(content: LinearLayout, title: String, summary: String, minimum: Int,
                          maximum: Int, value: Int, suffix: String, update: IntConsumer): LinearLayout {
        val context = content.context
        val block = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        val reading = Label(context).apply { text = "$value$suffix" }
        val labels = LinearLayout(context).apply {
            addView(TextView(context).apply { text = title })
            addView(reading)
            visibility = View.GONE
        }
        block.addView(labels, LinearLayout.LayoutParams(-1, -2))
        block.addView(TextView(context).apply { text = summary; visibility = View.GONE },
            LinearLayout.LayoutParams(-1, 0))
        val model = Range(context).apply {
            contentDescription = title
            max = (maximum - minimum).coerceAtLeast(1)
            progress = (value - minimum).coerceIn(0, max)
            visibility = View.GONE
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(bar: SeekBar, progress: Int, fromUser: Boolean) {
                    reading.text = "${minimum + progress}$suffix"
                    if (fromUser) update.accept(minimum + progress)
                }
                override fun onStartTrackingTouch(bar: SeekBar) { }
                override fun onStopTrackingTouch(bar: SeekBar) { }
            })
        }
        block.addView(model, LinearLayout.LayoutParams(0, 0))
        block.tag = model
        block.addView(view(context) {
            Column(Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        MiuixText(title, fontSize = 16.sp)
                        if (summary.isNotEmpty()) MiuixText(summary, fontSize = 12.sp,
                            color = MiuixTheme.colorScheme.onSurfaceSecondary)
                    }
                    MiuixText(reading.value, color = MiuixTheme.colorScheme.primary)
                }
                Slider(value = model.value.toFloat(), valueRange = 0f..model.max.toFloat(),
                    enabled = model.active && maximum > minimum, modifier = Modifier.testTag("slider_$title"),
                    onValueChange = { model.changeFromUi(it.roundToInt()) },
                    onValueChangeFinished = { model.finishFromUi() })
            }
        }, LinearLayout.LayoutParams(-1, -2))
        content.addView(block, LinearLayout.LayoutParams(-1, -2))
        return block
    }

    @JvmStatic fun pattern(content: LinearLayout, initial: String, enabled: Boolean, multiline: Boolean,
                           hint: String, update: Consumer<String>): EditText {
        val model = Input(content.context).apply {
            setText(initial)
            isEnabled = enabled
            contentDescription = "时间格式"
            visibility = View.GONE
        }
        content.addView(model, LinearLayout.LayoutParams(0, 0))
        var invalid by mutableStateOf(false)
        model.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(value: CharSequence?, start: Int, count: Int, after: Int) { }
            override fun onTextChanged(value: CharSequence?, start: Int, before: Int, count: Int) { }
            override fun afterTextChanged(value: Editable?) {
                if (!model.isEnabled) return
                invalid = !ClockTextFormatter.isValidPattern(value.toString())
                if (!invalid) update.accept(value.toString())
            }
        })
        content.addView(view(content.context) {
            Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                MiuixTextField(value = model.value, onValueChange = { model.setText(it) },
                    textStyle = MiuixTheme.textStyles.main.copy(color = MiuixTheme.colorScheme.onSurface),
                    enabled = model.active, singleLine = !multiline, minLines = if (multiline) 2 else 1,
                    maxLines = if (multiline) 2 else 1, label = hint, useLabelAsPlaceholder = true,
                    cornerRadius = 8.dp, insideMargin = DpSize(14.dp, 12.dp),
                    modifier = Modifier.testTag("setting_clock_pattern"))
                if (invalid) MiuixText(content.context.getString(R.string.clock_format_invalid),
                    color = androidx.compose.ui.graphics.Color(0xffc93434), fontSize = 12.sp)
            }
        }, LinearLayout.LayoutParams(-1, -2))
        return model
    }

    @JvmStatic fun numericInput(parent: LinearLayout, title: String, initial: Int): EditText {
        val model = Input(parent.context).apply {
            setText(initial.toString())
            contentDescription = title
            visibility = View.GONE
        }
        parent.addView(model, LinearLayout.LayoutParams(0, 0))
        parent.addView(view(parent.context) {
            Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                MiuixTextField(value = model.value, onValueChange = { model.setText(it) },
                    textStyle = MiuixTheme.textStyles.main.copy(color = MiuixTheme.colorScheme.onSurface),
                    singleLine = true, label = title, modifier = Modifier.fillMaxWidth().testTag("numeric_$title"),
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        keyboardType = androidx.compose.ui.text.input.KeyboardType.Number))
                model.problem?.let { MiuixText(it, fontSize = 12.sp,
                    color = androidx.compose.ui.graphics.Color(0xffc93434)) }
            }
        }, LinearLayout.LayoutParams(-1, -2))
        return model
    }

    internal fun view(context: Context, content: @Composable () -> Unit) = ComposeView(context).apply {
        id = View.generateViewId()
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindowOrReleasedFromPool)
        setContent {
            MiuixTheme(colors = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()) {
                CompositionLocalProvider(LocalContentColor provides MiuixTheme.colorScheme.onSurface) {
                    content()
                }
            }
        }
    }
}
