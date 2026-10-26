package com.xtjm.fusionstatusbar

import android.util.LruCache
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.*
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.extra.SuperArrow
import top.yukonga.miuix.kmp.extra.WindowBottomSheet
import top.yukonga.miuix.kmp.extra.SuperSwitch
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.basic.Check
import top.yukonga.miuix.kmp.icon.basic.Search
import top.yukonga.miuix.kmp.icon.basic.SearchCleanup
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.LocalContentColor
import top.yukonga.miuix.kmp.theme.darkColorScheme
import top.yukonga.miuix.kmp.theme.lightColorScheme
import java.util.Locale

/** Compose desktop settings; all persisted values still use the existing launcher protocol. */
class DesktopSettingsPage(private val activity: ComponentActivity) : AutoCloseable {
    private enum class Panel { HIDDEN, EDITOR, ACTIONS, APPS, ACTIVITY_APPS, ACTIVITIES, SHORTCUTS, TOGGLES,
        BACK_STYLES, BACK_BACKGROUND, BACK_ARROW, BACK_DISTANCE, BACK_DELAY }
    private data class Shortcut(val target: String, val label: String, val packageName: String)

    private var config by mutableStateOf(DesktopConfig.read(activity))
    private var draft by mutableStateOf(config)
    private var gesture by mutableIntStateOf(0)
    private var panel by mutableStateOf<Panel?>(null)
    private val showPanel = mutableStateOf(false)
    private var query by mutableStateOf("")
    private var colorHex by mutableStateOf("")
    private var colorError by mutableStateOf(false)
    private var thresholdInput by mutableStateOf("")
    private var thresholdSystem by mutableStateOf(false)
    private var thresholdError by mutableStateOf(false)
    private var hiddenDraft by mutableStateOf<Set<String>>(emptySet())
    private var apps by mutableStateOf<List<DesktopAppCatalog.App>>(emptyList())
    private var activities by mutableStateOf<List<DesktopAppCatalog.ActivityEntry>>(emptyList())
    private var shortcuts by mutableStateOf<List<Shortcut>>(emptyList())
    private var loading by mutableStateOf(false)
    private val actionIds = activity.resources.getIntArray(R.array.desktop_gesture_action_values)
    private val actionLabels = activity.resources.getStringArray(R.array.desktop_gesture_actions)
    private val toggleIds = activity.resources.getIntArray(R.array.desktop_toggle_values)
    private val toggleLabels = activity.resources.getStringArray(R.array.desktop_toggle_actions)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val iconCache = LruCache<String, ImageBitmap>(128)
    private var loadingJob: Job? = null
    private var desktopView: ComposeView? = null
    private var popupView: ComposeView? = null
    private var closed = false
    private var navigationPadding by mutableIntStateOf(108)

    fun setNavigationHeight(height: Int) {
        navigationPadding = (height / activity.resources.displayMetrics.density).toInt()
    }

    fun installInto(page: LinearLayout) {
        page.removeAllViews()
        val viewport = (page.parent as View).parent.parent as FrameLayout
        desktopView = composeView { DesktopContent() }.also {
            it.id = R.id.desktop_miuix_page
            it.contentDescription = "桌面"
            viewport.addView(it, FrameLayout.LayoutParams(-1, -1))
        }
        val root = activity.findViewById<FrameLayout>(android.R.id.content)
        popupView = composeView { PopupContent() }.also {
            it.id = R.id.desktop_miuix_popups
            it.visibility = View.GONE
            root.addView(it, FrameLayout.LayoutParams(-1, -1))
        }
    }

    private fun composeView(content: @Composable () -> Unit) = ComposeView(activity).apply {
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        setContent {
            MiuixTheme(colors = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()) {
                CompositionLocalProvider(LocalContentColor provides MiuixTheme.colorScheme.onSurface) {
                    content()
                }
            }
        }
    }

    fun setVisible(visible: Boolean) {
        desktopView?.visibility = if (visible) View.VISIBLE else View.GONE
        if (!visible && panel != null) dismissAll()
    }

    fun refresh() {
        if (!closed) config = DesktopConfig.read(activity)
    }

    @Composable
    private fun DesktopContent() {
        LazyColumn(
            modifier = Modifier.fillMaxSize().testTag("desktop_page"),
            contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 18.dp, bottom = navigationPadding.dp)
        ) {
            item { Text("桌面", fontSize = 28.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.testTag("desktop_heading")) }
            item {
                SmallTitle(text(R.string.desktop_icons_section), insideMargin = PaddingValues(0.dp, 18.dp))
                Card(cornerRadius = 18.dp) {
                    SuperSwitch(
                        title = text(R.string.desktop_hide_icons),
                        summary = text(R.string.desktop_hide_icons_summary),
                        checked = config.hideIcons,
                        modifier = Modifier.testTag("desktop_hide_icons"),
                        onCheckedChange = { save(config.withHideIcons(it)) }
                    )
                    HorizontalDivider()
                    SuperArrow(
                        title = text(R.string.desktop_choose_hidden_apps),
                        summary = if (config.hiddenPackages().isEmpty()) text(R.string.desktop_hidden_apps_none)
                            else activity.getString(R.string.desktop_hidden_apps_count, config.hiddenPackages().size),
                        modifier = Modifier.testTag("desktop_hidden_apps"),
                        onClick = {
                            hiddenDraft = config.hiddenPackages().toSet()
                            openApps(Panel.HIDDEN)
                        }
                    )
                }
            }
            item {
                SmallTitle(text(R.string.desktop_gestures_section), insideMargin = PaddingValues(0.dp, 18.dp))
                Card(cornerRadius = 18.dp) {
                    repeat(DesktopConfig.GESTURE_COUNT) { id ->
                        if (id > 0) HorizontalDivider()
                        SuperArrow(
                            title = text(gestureTitles[id]),
                            summary = gestureSummary(id),
                            modifier = Modifier.testTag("desktop_gesture_$id"),
                            onClick = {
                                gesture = id
                                draft = config
                                open(Panel.EDITOR)
                            }
                        )
                    }
                }
            }
            item {
                SmallTitle(text(R.string.desktop_back_appearance), insideMargin = PaddingValues(0.dp, 18.dp))
                Card(cornerRadius = 18.dp) {
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                        Text(text(R.string.desktop_back_appearance_summary), fontSize = 14.sp,
                            color = LocalContentColor.current.copy(alpha = .7f))
                        BackPreview(config)
                    }
                    SuperArrow(title = text(R.string.desktop_back_style), summary = backStyleName(config.backGestureStyle),
                        modifier = Modifier.testTag("desktop_back_style"), onClick = { open(Panel.BACK_STYLES) })
                    HorizontalDivider()
                    SuperArrow(title = text(R.string.desktop_back_background), summary = colorString(config.backGestureBackgroundColor),
                        modifier = Modifier.testTag("desktop_back_background"), onClick = { openColor(Panel.BACK_BACKGROUND) })
                    HorizontalDivider()
                    SuperArrow(title = text(R.string.desktop_back_arrow), summary = colorString(config.backGestureArrowColor),
                        modifier = Modifier.testTag("desktop_back_arrow"), onClick = { openColor(Panel.BACK_ARROW) })
                }
            }
        }
    }

    @Composable
    private fun BackPreview(value: DesktopConfig) {
        AndroidView(factory = { BackGesturePreviewView(it) }, update = { it.setConfig(value) },
            modifier = Modifier.fillMaxWidth().height(156.dp).testTag("desktop_back_preview"))
        Text(text(R.string.desktop_back_preview), fontSize = 12.sp,
            color = LocalContentColor.current.copy(alpha = .7f))
    }

    @Composable
    private fun PopupContent() {
        Scaffold(containerColor = Color.Transparent, contentWindowInsets = WindowInsets(0, 0, 0, 0)) {
            WindowBottomSheet(
                show = showPanel.value,
                insideMargin = DpSize(22.dp, 18.dp),
                cornerRadius = 28.dp,
                sheetMaxWidth = 420.dp,
                onDismissRequest = { goBack() },
                onDismissFinished = { if (!showPanel.value) { panel = null; popupView?.visibility = View.GONE } }
            ) {
                val active = panel ?: return@WindowBottomSheet
                val done = active == Panel.HIDDEN || active == Panel.EDITOR || active in colorPanels
                    || active in thresholdPanels
                val headerFocus = remember { FocusRequester() }
                val keyboard = LocalSoftwareKeyboardController.current
                LaunchedEffect(active) { headerFocus.requestFocus(); keyboard?.hide() }
                Row(Modifier.fillMaxWidth().focusRequester(headerFocus).focusable(),
                    verticalAlignment = Alignment.CenterVertically) {
                    Text(panelTitle(active), modifier = Modifier.weight(1f),
                        fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    if (active in thresholdPanels || active == Panel.EDITOR
                        && gesture == DesktopConfig.GESTURE_BACK_HOLD) TextButton(
                        text = text(R.string.backup_cancel),
                        modifier = Modifier.widthIn(min = 64.dp).testTag("desktop_sheet_cancel"),
                        colors = ButtonDefaults.textButtonColors(color = Color.Transparent,
                            textColor = MiuixTheme.colorScheme.primary),
                        onClick = { if (active == Panel.EDITOR) dismissAll() else goBack() })
                    TextButton(
                        text = text(if (done) R.string.editor_done else R.string.backup_cancel),
                        modifier = Modifier.widthIn(min = 64.dp).testTag("desktop_sheet_confirm"),
                        colors = ButtonDefaults.textButtonColors(color = Color.Transparent,
                            textColor = MiuixTheme.colorScheme.primary),
                        onClick = { if (done) confirm(active) else goBack() }
                    )
                }
                val configuration = LocalConfiguration.current
                val maxHeight = (configuration.screenHeightDp * .58f).dp.coerceAtMost(440.dp)
                if (active == Panel.EDITOR || active in colorPanels || active in thresholdPanels) {
                    Column(Modifier.heightIn(max = maxHeight).verticalScroll(rememberScrollState())) {
                        when {
                            active == Panel.EDITOR -> GestureEditor()
                            active in thresholdPanels -> BackThresholdEditor(active)
                            else -> BackColorEditor(active)
                        }
                    }
                } else {
                    if (active in searchablePanels) SearchField()
                    Box(Modifier.fillMaxWidth().heightIn(min = 80.dp, max = maxHeight)) {
                        if (loading) {
                            Box(Modifier.fillMaxWidth().height(80.dp), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator()
                            }
                        } else {
                            Choices(active)
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun BackColorEditor(active: Panel) {
        Text(text(R.string.desktop_back_color_help), modifier = Modifier.padding(vertical = 8.dp),
            fontSize = 14.sp, color = LocalContentColor.current.copy(alpha = .7f))
        TextField(value = colorHex, onValueChange = { colorHex = it.take(32); colorError = false },
            label = text(R.string.desktop_back_color_hex), singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag("desktop_back_color_hex"),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { confirm(active) }))
        if (colorError) Text(text(R.string.desktop_back_color_invalid),
            modifier = Modifier.padding(vertical = 8.dp).testTag("desktop_back_color_error"),
            color = Color(0xFFD32F2F), fontSize = 14.sp)
        Row(Modifier.fillMaxWidth().padding(vertical = 16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            val palette = if (active == Panel.BACK_BACKGROUND) backgroundPalette else arrowPalette
            palette.forEachIndexed { index, color ->
                val selected = parseBackColor(colorHex) == color
                Box(Modifier.size(40.dp).border(if (selected) 3.dp else 1.dp,
                    if (selected) MiuixTheme.colorScheme.primary else LocalContentColor.current.copy(alpha = .35f), CircleShape)
                    .padding(4.dp).background(Color(color), CircleShape).clickable {
                        colorHex = colorString(color); colorError = false
                    }.testTag("desktop_back_swatch_$index").semantics {
                        contentDescription = colorString(color)
                        this.selected = selected; role = Role.RadioButton
                    })
            }
        }
        val selected = parseBackColor(colorHex)
        BackPreview(if (selected == null) config else withEditedColor(config, active, selected))
    }

    @Composable
    private fun GestureEditor() {
        if (gesture == DesktopConfig.GESTURE_BACK_HOLD) {
            Text(backHoldThresholdSummary(draft) + "\n" + text(R.string.desktop_gesture_back_hold_summary),
                modifier = Modifier.padding(vertical = 8.dp).testTag("desktop_back_hold_summary"),
                fontSize = 14.sp, color = LocalContentColor.current.copy(alpha = .7f))
        }
        SmallTitle(text(R.string.desktop_gesture_action_section), insideMargin = PaddingValues(0.dp, 16.dp))
        SuperArrow(title = text(R.string.desktop_gesture_action), summary = actionName(draft.gestureAction(gesture)),
            modifier = Modifier.testTag("desktop_editor_action"), onClick = { open(Panel.ACTIONS) })
        when (draft.gestureAction(gesture)) {
            DesktopConfig.ACTION_LAUNCH_APP -> SuperArrow(
                title = text(R.string.desktop_gesture_app_target), summary = selectedTarget(),
                modifier = Modifier.testTag("desktop_editor_app"), onClick = { openApps(Panel.APPS) })
            DesktopConfig.ACTION_LAUNCH_SHORTCUT -> SuperArrow(
                title = text(R.string.desktop_gesture_shortcut_target), summary = selectedTarget(),
                modifier = Modifier.testTag("desktop_editor_shortcut"), onClick = { openShortcuts() })
            DesktopConfig.ACTION_LAUNCH_ACTIVITY -> {
                SuperArrow(title = text(R.string.desktop_gesture_activity_app), summary = selectedActivityApp(),
                    modifier = Modifier.testTag("desktop_editor_activity_app"),
                    onClick = { openApps(Panel.ACTIVITY_APPS) })
                if (draft.gesturePackage(gesture) != null) {
                    SuperArrow(title = text(R.string.desktop_gesture_activity_target), summary = selectedTarget(),
                        modifier = Modifier.testTag("desktop_editor_activity"), onClick = { openActivities() })
                }
            }
            DesktopConfig.ACTION_TOGGLE -> SuperArrow(
                title = text(R.string.desktop_gesture_toggle_target), summary = toggleName(draft.gestureToggle(gesture)),
                modifier = Modifier.testTag("desktop_editor_toggle"), onClick = { open(Panel.TOGGLES) })
        }
        if (gesture == DesktopConfig.GESTURE_BACK_HOLD) {
            SmallTitle(text(R.string.desktop_back_hold_thresholds), insideMargin = PaddingValues(0.dp, 16.dp))
            SuperArrow(title = text(R.string.desktop_back_hold_distance),
                summary = backHoldDistanceSummary(draft),
                modifier = Modifier.testTag("desktop_back_hold_distance"),
                onClick = { openThreshold(Panel.BACK_DISTANCE) })
            SuperArrow(title = text(R.string.desktop_back_hold_delay),
                summary = activity.getString(R.string.desktop_back_hold_delay_value, draft.backHoldDelayMs),
                modifier = Modifier.testTag("desktop_back_hold_delay"),
                onClick = { openThreshold(Panel.BACK_DELAY) })
            TextButton(text = text(R.string.desktop_back_hold_reset),
                modifier = Modifier.testTag("desktop_back_hold_reset"),
                onClick = { draft = draft.withBackHoldThresholds(DesktopConfig.BACK_HOLD_DISTANCE_SYSTEM,
                    DesktopConfig.DEFAULT_BACK_HOLD_DELAY_MS) })
        }
    }

    @Composable
    private fun BackThresholdEditor(active: Panel) {
        val distance = active == Panel.BACK_DISTANCE
        Text(text(if (distance) R.string.desktop_back_hold_distance_help else R.string.desktop_back_hold_delay_help),
            modifier = Modifier.padding(vertical = 8.dp), fontSize = 14.sp,
            color = LocalContentColor.current.copy(alpha = .7f))
        if (distance) SuperSwitch(title = text(R.string.desktop_back_hold_distance_system),
            checked = thresholdSystem, modifier = Modifier.testTag("desktop_back_hold_distance_system"),
            onCheckedChange = { thresholdSystem = it; thresholdError = false })
        if (!distance || !thresholdSystem) TextField(
            value = thresholdInput,
            onValueChange = { thresholdInput = it.take(16); thresholdError = false },
            label = text(if (distance) R.string.desktop_back_hold_distance_input else R.string.desktop_back_hold_delay_input),
            singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag("desktop_back_hold_threshold_input"),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { confirm(active) }))
        if (thresholdError) Text(text(if (distance) R.string.desktop_back_hold_distance_invalid
            else R.string.desktop_back_hold_delay_invalid),
            modifier = Modifier.padding(vertical = 8.dp).testTag("desktop_back_hold_threshold_error"),
            color = Color(0xFFD32F2F), fontSize = 14.sp)
        TextButton(text = text(R.string.desktop_back_hold_reset),
            modifier = Modifier.testTag("desktop_back_hold_threshold_reset"), onClick = {
                if (distance) thresholdSystem = true
                else thresholdInput = DesktopConfig.DEFAULT_BACK_HOLD_DELAY_MS.toString()
                thresholdError = false
            })
    }

    @Composable
    private fun SearchField() {
        val keyboard = LocalSoftwareKeyboardController.current
        TextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp).testTag("desktop_app_search"),
            label = text(R.string.desktop_search_apps), useLabelAsPlaceholder = true,
            textStyle = MiuixTheme.textStyles.main.copy(color = MiuixTheme.colorScheme.onSurface),
            singleLine = true, cornerRadius = 8.dp, insideMargin = DpSize(12.dp, 12.dp),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
            leadingIcon = { Icon(MiuixIcons.Basic.Search, null, modifier = Modifier.size(20.dp)) },
            trailingIcon = {
                Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                    if (query.isNotEmpty()) IconButton(
                        onClick = { query = "" }, modifier = Modifier.size(48.dp).testTag("desktop_search_clear")
                    ) { Icon(MiuixIcons.Basic.SearchCleanup, text(R.string.desktop_clear_search)) }
                }
            }
        )
    }

    @Composable
    private fun Choices(active: Panel) {
        when (active) {
            Panel.HIDDEN, Panel.APPS, Panel.ACTIVITY_APPS -> {
                val visible = apps.filter { matches(query, it.label, it.packageName) }
                if (visible.isEmpty()) EmptyChoices() else LazyColumn(Modifier.testTag("desktop_app_list")) {
                    items(visible, key = { it.packageName }) { app ->
                        val selected = if (active == Panel.HIDDEN) app.packageName in hiddenDraft
                            else app.packageName == draft.gesturePackage(gesture)
                        val action = {
                            if (active == Panel.HIDDEN) {
                                hiddenDraft = if (selected) hiddenDraft - app.packageName else hiddenDraft + app.packageName
                            } else {
                                val target = if (active == Panel.ACTIVITY_APPS) app.packageName + "|" else app.component()
                                draft = draft.withGestureTarget(gesture, target, app.label)
                                open(Panel.EDITOR)
                            }
                        }
                        BasicComponent(
                            title = app.label,
                            summary = app.packageName + app.stateLabel().takeIf { it != 0 }?.let { " · " + text(it) }.orEmpty(),
                            startAction = { AppIcon(app.packageName) },
                            endActions = { Selection(selected, active == Panel.HIDDEN, action) },
                            modifier = Modifier.testTag("desktop_app_${app.packageName}").semantics {
                                this.selected = selected
                                role = if (active == Panel.HIDDEN) Role.Checkbox else Role.RadioButton
                            },
                            insideMargin = PaddingValues(vertical = 10.dp), onClick = action
                        )
                    }
                }
            }
            Panel.ACTIONS -> LazyColumn(Modifier.testTag("desktop_action_list")) {
                val choices = if (gesture == DesktopConfig.GESTURE_BACK_HOLD)
                    listOf(DesktopConfig.ACTION_NATIVE) + actionIds.filter { it != DesktopConfig.ACTION_NONE }
                    else actionIds.toList()
                items(choices, key = { it }) { action ->
                    ChoiceRow(actionName(action), draft.gestureAction(gesture) == action
                        || gesture != DesktopConfig.GESTURE_BACK_HOLD && action == DesktopConfig.ACTION_NONE
                            && draft.gestureAction(gesture) == DesktopConfig.ACTION_NATIVE
                        || gesture == DesktopConfig.GESTURE_BACK_HOLD && action == DesktopConfig.ACTION_NATIVE
                            && draft.gestureAction(gesture) == DesktopConfig.ACTION_NONE,
                        "desktop_action_$action") {
                        draft = draft.withGesture(gesture, action)
                        open(Panel.EDITOR)
                    }
                }
            }
            Panel.TOGGLES -> LazyColumn(Modifier.testTag("desktop_toggle_list")) {
                items(toggleIds.toList(), key = { it }) { toggle ->
                    ChoiceRow(toggleName(toggle), draft.gestureToggle(gesture) == toggle,
                        "desktop_toggle_$toggle") {
                        draft = draft.withGestureToggle(gesture, toggle)
                        open(Panel.EDITOR)
                    }
                }
            }
            Panel.BACK_STYLES -> LazyColumn(Modifier.testTag("desktop_back_style_list")) {
                items((DesktopConfig.BACK_STYLE_NATIVE until DesktopConfig.BACK_STYLE_COUNT).toList(), key = { it }) { style ->
                    ChoiceRow(backStyleName(style), config.backGestureStyle == style,
                        "desktop_back_style_$style", text(backStyleDescriptions[style])) {
                        if (save(config.withBackGesturePreset(style))) dismissAll()
                    }
                }
            }
            Panel.ACTIVITIES -> {
                val visible = activities.filter { matches(query, it.label, it.className) }
                if (visible.isEmpty()) EmptyChoices() else LazyColumn {
                    items(visible, key = { it.component }) { entry ->
                        ChoiceRow(entry.label, draft.gestureTarget(gesture) == entry.component,
                            "desktop_activity_${entry.className}", entry.className
                                + entry.stateLabel.takeIf { it != 0 }?.let { " · " + text(it) }.orEmpty()) {
                            draft = draft.withGestureTarget(gesture, entry.component, entry.label)
                            open(Panel.EDITOR)
                        }
                    }
                }
            }
            Panel.SHORTCUTS -> {
                val visible = shortcuts.filter { matches(query, it.label, it.packageName) }
                if (visible.isEmpty()) EmptyChoices() else LazyColumn {
                    items(visible, key = { it.target }) { shortcut ->
                        ChoiceRow(shortcut.label, draft.gestureTarget(gesture) == shortcut.target,
                            "desktop_shortcut_${shortcut.target}", shortcut.packageName) {
                            draft = draft.withGestureTarget(gesture, shortcut.target, shortcut.label)
                            open(Panel.EDITOR)
                        }
                    }
                }
            }
            else -> Unit
        }
    }

    @Composable
    private fun Selection(selected: Boolean, multiple: Boolean, action: () -> Unit) {
        if (multiple) Checkbox(state = if (selected) ToggleableState.On else ToggleableState.Off,
            onClick = action)
        else Box(Modifier.size(24.dp)) {
            if (selected) Icon(MiuixIcons.Basic.Check, null, tint = MiuixTheme.colorScheme.primary)
        }
    }

    @Composable
    private fun ChoiceRow(title: String, selected: Boolean, tag: String, summary: String? = null,
                          action: () -> Unit) {
        BasicComponent(title = title, summary = summary, onClick = action,
            modifier = Modifier.testTag(tag).semantics { this.selected = selected; role = Role.RadioButton },
            endActions = { Selection(selected, false, action) }, insideMargin = PaddingValues(vertical = 10.dp))
    }

    @Composable
    private fun EmptyChoices() {
        Box(Modifier.fillMaxWidth().height(120.dp).testTag("desktop_search_empty"), contentAlignment = Alignment.Center) {
            Text(text(R.string.desktop_search_empty), color = MiuixTheme.colorScheme.onSurfaceSecondary)
        }
    }

    @Composable
    private fun AppIcon(packageName: String) {
        val fallback = remember { activity.packageManager.defaultActivityIcon.toBitmap(96, 96).asImageBitmap() }
        val icon by produceState(iconCache.get(packageName) ?: fallback, packageName) {
            val loaded = iconCache.get(packageName) ?: withContext(Dispatchers.IO) {
                runCatching { activity.packageManager.getApplicationIcon(packageName).toBitmap(96, 96).asImageBitmap() }
                    .getOrDefault(fallback).also { iconCache.put(packageName, it) }
            }
            value = loaded
        }
        Image(icon, null, Modifier.padding(end = 12.dp).size(36.dp).testTag("desktop_icon_$packageName"))
    }

    private fun open(next: Panel) {
        loadingJob?.cancel()
        loading = false
        query = ""
        panel = next
        showPanel.value = true
        popupView?.apply { visibility = View.VISIBLE; bringToFront() }
    }

    private fun openApps(next: Panel) {
        open(next)
        loading = true
        loadingJob = scope.launch {
            apps = withContext(Dispatchers.IO) { runCatching { DesktopAppCatalog.installed(activity) }.getOrDefault(emptyList()) }
                .filter { next == Panel.ACTIVITY_APPS || it.launcherActivity != null }
            loading = false
        }
    }

    private fun openColor(next: Panel) {
        colorHex = colorString(if (next == Panel.BACK_BACKGROUND)
            config.backGestureBackgroundColor else config.backGestureArrowColor)
        colorError = false
        open(next)
    }

    private fun openThreshold(next: Panel) {
        thresholdSystem = draft.backHoldDistanceDp == DesktopConfig.BACK_HOLD_DISTANCE_SYSTEM
        thresholdInput = if (next == Panel.BACK_DISTANCE)
            (if (thresholdSystem) 60 else draft.backHoldDistanceDp).toString()
        else draft.backHoldDelayMs.toString()
        thresholdError = false
        open(next)
    }

    private fun openActivities() {
        val packageName = draft.gesturePackage(gesture) ?: return
        open(Panel.ACTIVITIES)
        loading = true
        loadingJob = scope.launch {
            activities = withContext(Dispatchers.IO) { DesktopAppCatalog.activities(activity, packageName) }
            loading = false
        }
    }

    private fun openShortcuts() {
        open(Panel.SHORTCUTS)
        loading = true
        loadingJob = scope.launch {
            shortcuts = withContext(Dispatchers.IO) {
                DesktopShortcutCatalog.read(activity).map { Shortcut(it.target, it.label, it.packageName) }
            }
            loading = false
        }
    }

    private fun confirm(active: Panel) {
        if (loading) return
        if (active in thresholdPanels) {
            val distance = active == Panel.BACK_DISTANCE
            val selected = thresholdInput.trim().takeIf { it.isNotEmpty() && it.all(Char::isDigit) }?.toIntOrNull()
            val valid = if (distance) thresholdSystem || selected != null
                && selected in DesktopConfig.MIN_BACK_HOLD_DISTANCE_DP..DesktopConfig.MAX_BACK_HOLD_DISTANCE_DP
            else selected != null && selected in DesktopConfig.MIN_BACK_HOLD_DELAY_MS..DesktopConfig.MAX_BACK_HOLD_DELAY_MS
            if (!valid) { thresholdError = true; return }
            draft = if (distance) draft.withBackHoldThresholds(
                if (thresholdSystem) DesktopConfig.BACK_HOLD_DISTANCE_SYSTEM else selected!!, draft.backHoldDelayMs)
            else draft.withBackHoldThresholds(draft.backHoldDistanceDp, selected!!)
            open(Panel.EDITOR)
            return
        }
        if (active in colorPanels) {
            val selected = parseBackColor(colorHex)
            if (selected == null) { colorError = true; return }
            if (save(withEditedColor(config, active, selected))) dismissAll()
            return
        }
        val next = if (active == Panel.HIDDEN) config.withHiddenPackages(hiddenDraft) else draft
        if (active == Panel.EDITOR && !next.isGestureComplete(gesture)) {
            Toast.makeText(activity, R.string.desktop_gesture_target_required, Toast.LENGTH_SHORT).show()
            return
        }
        if (save(next)) dismissAll()
    }

    private fun save(next: DesktopConfig): Boolean {
        if (closed || activity.isDestroyed || activity.isFinishing) return false
        if (!DesktopConfig.save(activity, next)) {
            Toast.makeText(activity, R.string.desktop_save_failed, Toast.LENGTH_LONG).show()
            return false
        }
        config = next
        return true
    }

    private fun goBack() {
        if (panel != Panel.HIDDEN && panel != Panel.EDITOR && panel != Panel.BACK_STYLES
            && panel?.let { it in colorPanels } != true) open(Panel.EDITOR) else dismissAll()
    }

    private fun dismissAll() {
        loadingJob?.cancel()
        showPanel.value = false
    }

    private fun text(id: Int) = activity.getString(id)
    private fun backStyleName(style: Int) = text(backStyleTitles[style])
    private fun colorString(color: Int) = String.format(Locale.ROOT, "#%08X", color)
    private fun withEditedColor(source: DesktopConfig, active: Panel, color: Int): DesktopConfig {
        val styled = if (source.backGestureStyle == DesktopConfig.BACK_STYLE_NATIVE)
            source.withBackGestureStyle(DesktopConfig.BACK_STYLE_CLASSIC) else source
        return styled.withBackGestureColors(
            if (active == Panel.BACK_BACKGROUND) color else styled.backGestureBackgroundColor,
            if (active == Panel.BACK_ARROW) color else styled.backGestureArrowColor)
    }
    private fun actionName(action: Int, gestureId: Int = gesture) =
        if (gestureId == DesktopConfig.GESTURE_BACK_HOLD && action <= DesktopConfig.ACTION_NONE)
            text(R.string.desktop_action_native)
        else actionLabels.getOrNull(actionIds.indexOf(action)) ?: text(R.string.desktop_action_none)
    private fun toggleName(toggle: Int) = toggleLabels.getOrElse(toggleIds.indexOf(toggle)) { toggleLabels[0] }
    private fun selectedTarget() = draft.gestureTargetLabel(gesture) ?: draft.gestureTarget(gesture)
        ?: text(R.string.desktop_not_selected)
    private fun selectedActivityApp(): String {
        val packageName = draft.gesturePackage(gesture) ?: return text(R.string.desktop_not_selected)
        return apps.firstOrNull { it.packageName == packageName }?.label ?: packageName
    }
    private fun gestureSummary(id: Int): String {
        val action = config.gestureAction(id)
        val selected = when (action) {
            DesktopConfig.ACTION_LAUNCH_APP, DesktopConfig.ACTION_LAUNCH_ACTIVITY, DesktopConfig.ACTION_LAUNCH_SHORTCUT ->
                config.gestureTargetLabel(id) ?: actionName(action, id)
            DesktopConfig.ACTION_TOGGLE -> toggleName(config.gestureToggle(id))
            else -> actionName(action, id)
        }
        return if (id == DesktopConfig.GESTURE_BACK_HOLD)
            selected + "\n" + backHoldThresholdSummary(config) else selected
    }
    private fun backHoldDistanceSummary(value: DesktopConfig) =
        if (value.backHoldDistanceDp == DesktopConfig.BACK_HOLD_DISTANCE_SYSTEM)
            text(R.string.desktop_back_hold_distance_system)
        else activity.getString(R.string.desktop_back_hold_distance_value, value.backHoldDistanceDp)
    private fun backHoldThresholdSummary(value: DesktopConfig) = activity.getString(
        R.string.desktop_back_hold_threshold_summary, backHoldDistanceSummary(value), value.backHoldDelayMs)
    private fun panelTitle(active: Panel) = when (active) {
        Panel.HIDDEN -> text(R.string.desktop_choose_hidden_apps)
        Panel.EDITOR -> text(gestureDetailTitles[gesture])
        Panel.ACTIONS -> text(R.string.desktop_gesture_action)
        Panel.APPS -> text(R.string.desktop_choose_gesture_app)
        Panel.ACTIVITY_APPS -> text(R.string.desktop_gesture_activity_app)
        Panel.ACTIVITIES -> text(R.string.desktop_choose_gesture_activity)
        Panel.SHORTCUTS -> text(R.string.desktop_choose_gesture_shortcut)
        Panel.TOGGLES -> text(R.string.desktop_gesture_toggle_target)
        Panel.BACK_STYLES -> text(R.string.desktop_back_style)
        Panel.BACK_BACKGROUND -> text(R.string.desktop_back_background)
        Panel.BACK_ARROW -> text(R.string.desktop_back_arrow)
        Panel.BACK_DISTANCE -> text(R.string.desktop_back_hold_distance)
        Panel.BACK_DELAY -> text(R.string.desktop_back_hold_delay)
    }

    override fun close() {
        closed = true
        scope.cancel()
        showPanel.value = false
        listOf(desktopView, popupView).forEach { view ->
            view?.disposeComposition()
            (view?.parent as? ViewGroup)?.removeView(view)
        }
        desktopView = null
        popupView = null
        iconCache.evictAll()
    }

    companion object {
        private val colorPanels = setOf(Panel.BACK_BACKGROUND, Panel.BACK_ARROW)
        private val thresholdPanels = setOf(Panel.BACK_DISTANCE, Panel.BACK_DELAY)
        private val backStyleTitles = intArrayOf(R.string.desktop_action_native, R.string.desktop_back_style_classic,
            R.string.desktop_back_style_capsule, R.string.desktop_back_style_ripple, R.string.desktop_back_style_neon,
            R.string.desktop_back_style_rainbow)
        private val backStyleDescriptions = intArrayOf(R.string.desktop_back_style_native_summary,
            R.string.desktop_back_style_classic_summary, R.string.desktop_back_style_capsule_summary,
            R.string.desktop_back_style_ripple_summary, R.string.desktop_back_style_neon_summary,
            R.string.desktop_back_style_rainbow_summary)
        private val backgroundPalette = intArrayOf(0xCC202124.toInt(), 0xE63267D6.toInt(), 0xB34AAFA5.toInt(),
            0xCC17112D.toInt(), 0xCCB83D55.toInt(), 0xCCB97318.toInt())
        private val arrowPalette = intArrayOf(0xFFFFFFFF.toInt(), 0xFF202124.toInt(), 0xFFBAA2FF.toInt(),
            0xFF88E8DF.toInt(), 0xFFFFD178.toInt(), 0xFFFFA2B8.toInt())
        private fun parseBackColor(value: String): Int? {
            val hex = value.trim().removePrefix("#")
            if (hex.length != 6 && hex.length != 8 || hex.any { it.digitToIntOrNull(16) == null }) return null
            return hex.toLongOrNull(16)?.let { if (hex.length == 6) (it or 0xFF000000).toInt() else it.toInt() }
        }
        private val searchablePanels = setOf(Panel.HIDDEN, Panel.APPS, Panel.ACTIVITY_APPS, Panel.ACTIVITIES, Panel.SHORTCUTS)
        private val gestureTitles = intArrayOf(R.string.desktop_gesture_single_down, R.string.desktop_gesture_double_down,
            R.string.desktop_gesture_single_up, R.string.desktop_gesture_double_up, R.string.desktop_gesture_dock_right,
            R.string.desktop_gesture_dock_left, R.string.desktop_gesture_shake, R.string.desktop_gesture_double_tap,
            R.string.desktop_gesture_pinch, R.string.desktop_gesture_expand, R.string.desktop_gesture_back_hold)
        private val gestureDetailTitles = intArrayOf(R.string.desktop_gesture_detail_single_down, R.string.desktop_gesture_detail_double_down,
            R.string.desktop_gesture_detail_single_up, R.string.desktop_gesture_detail_double_up, R.string.desktop_gesture_detail_dock_right,
            R.string.desktop_gesture_detail_dock_left, R.string.desktop_gesture_detail_shake, R.string.desktop_gesture_detail_double_tap,
            R.string.desktop_gesture_detail_pinch, R.string.desktop_gesture_detail_expand, R.string.desktop_gesture_detail_back_hold)
        private fun matches(query: String, label: String, packageName: String): Boolean {
            val needle = query.trim().lowercase(Locale.ROOT)
            return needle.isEmpty() || label.lowercase(Locale.ROOT).contains(needle) || packageName.lowercase(Locale.ROOT).contains(needle)
        }
    }
}
