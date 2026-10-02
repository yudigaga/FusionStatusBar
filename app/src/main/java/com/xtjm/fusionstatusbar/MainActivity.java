package com.xtjm.fusionstatusbar;

import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.Toast;

/* JADX INFO: loaded from: classes2.dex */
public final class MainActivity extends android.app.Activity {
    private static final java.lang.String KEY_SELECTED_PAGE = "selected_page";
    private static final java.lang.String PROJECT_REPOSITORY_URL = "https://github.com/yudigaga/FusionStatusBar";
    private static final java.lang.String MIUIX_REPOSITORY_URL = "https://github.com/compose-miuix-ui/miuix";
    private static final int REQUEST_EXPORT_LOGS = 1027;
    private static final int REQUEST_PHONE_PERMISSION = 1028;
    private android.view.View activationIndicator;
    private android.database.ContentObserver activationObserver;
    private android.widget.TextView activationSummary;
    private android.widget.TextView activationTitle;
    private android.widget.TextView clockSettingsSummary;
    private com.xtjm.fusionstatusbar.FusionConfig config;
    private android.widget.LinearLayout controlCenterActivePreview;
    private android.widget.LinearLayout controlCenterActualPanel;
    private com.xtjm.fusionstatusbar.ControlCenterActualEditor controlCenterActualPreview;
    private android.widget.TextView controlCenterActualSummary;
    private android.widget.LinearLayout controlCenterAvailablePreview;
    private boolean controlCenterCompactMode;
    private android.widget.Button controlCenterCompactModeButton;
    private com.xtjm.fusionstatusbar.ControlCenterConfig controlCenterDraft;
    private boolean controlCenterDraftDirty;
    private com.xtjm.fusionstatusbar.ControlCenterGridEditor controlCenterGridEditor;
    private android.widget.LinearLayout controlCenterGridPanel;
    private ControlCenterWallpaperWorkspace controlCenterWallpaperWorkspace;
    private android.widget.LinearLayout controlCenterPreview;
    private int controlCenterPreviewPolls;
    private long controlCenterPreviewRequest;
    private android.view.View controlCenterPreviewTitle;
    private android.widget.Button controlCenterPushButton;
    private android.widget.Button controlCenterRegularModeButton;
    private android.widget.TextView controlCenterSummary;
    private android.widget.Switch controlCenterEnabledSwitch;
    private android.widget.Button controlCenterUndoButton;
    private android.widget.Button controlCenterRedoButton;
    private ControlCenterEditorSession editorSession;
    private ControlCenterPageCoordinator editorPage;
    private android.widget.TextView controlCenterDraftHint;
    private android.widget.Button controlCenterDraftRetryButton;
    private ControlCenterPreviewLoader previewLoader;
    private FusionConfigRepository.Session configSession;
    private boolean settingsLoaded;
    private boolean controlCenterSaving;
    private boolean configWriteInFlight;
    private boolean configRestoring;
    private boolean controlCenterSaveFailed;
    private long controlCenterSavedRevision;
    private boolean suppressEditorSelection;
    private boolean resumed;
    private android.view.View startupOverlay;
    private SettingsDialogController settingsDialogs;
    private SettingsPageController settingsPages;
    private ConfigurationBackupController backupController;
    private LayoutPresetController presetController;
    private SettingsStartupCoordinator startupCoordinator;
    private android.widget.TextView controlCenterCanvasSummary;
    private android.widget.TextView controlCenterPublishHint;
    private android.widget.TextView controlCenterReferenceToggle;
    private boolean controlCenterReferenceExpanded;
    private android.widget.TextView fusionSettingsSummary;
    private android.widget.TextView layoutSettingsSummary;
    private android.widget.TextView notificationClockSummary;
    private android.widget.TextView notificationListPreview;
    private android.widget.TextView notificationListSummary;
    private android.widget.LinearLayout notificationPreview;
    private android.widget.TextView notificationTimePreview;
    private android.widget.ScrollView pageScroll;
    private boolean pendingSettingsSave;
    private android.widget.TextView phonePermissionSummary;
    private com.xtjm.fusionstatusbar.FusionIconView preview;
    private com.xtjm.fusionstatusbar.FusionPreviewBackdrop previewBackdrop;
    private int previewSliderIndex;
    private int selectedPage;
    private android.widget.TextView telemetrySettingsSummary;
    private boolean updating;
    private static final int[] TELEMETRY_TITLES = {com.xtjm.fusionstatusbar.R.string.telemetry_temperatures, com.xtjm.fusionstatusbar.R.string.telemetry_power_current, com.xtjm.fusionstatusbar.R.string.telemetry_net_speed};
    private static final java.lang.String[] CONTROL_TILE_SPECS = {"cell", "wifi", "bt", "airplane", "mute", "flashlight", "screenshot", "batterysaver", "rotation", "controls", "custom(com.miui.screenrecorder/.service.QuickService)", "hotspot", "satellite", "autobrightness", "screenlock", "night", "quietmode", "gps", "nfc", "sync", "vibrate", "edit", "settings", "wallet", "internet"};
    private static final java.lang.String[] CONTROL_TILE_LABELS = {"移动网络", "Wi-Fi", "蓝牙", "飞行模式", "静音", "手电筒", "截屏", "省电模式", "自动旋转", "设备控制", "录屏", "个人热点", "卫星通信", "自动亮度", "旋转锁定", "夜间显示", "勿扰模式", "位置", "NFC", "同步", "振动", "编辑", "设置", "电子钱包", "互联网"};
    private final android.widget.Switch[] telemetrySwitches = new android.widget.Switch[3];
    private final SettingsChoiceSelector[] telemetryPositions = new SettingsChoiceSelector[3];
    private final android.widget.LinearLayout[] pages = new android.widget.LinearLayout[5];
    private final android.widget.LinearLayout[] navigationItems = new android.widget.LinearLayout[5];
    private long lastControlCenterPreviewFailure = -1;
    private final java.util.ArrayList<java.lang.String> controlCenterCapturedSpecs = new java.util.ArrayList<>();
    private final java.util.ArrayList<java.lang.String> controlCenterNativeCatalog = new java.util.ArrayList<>();
    private java.util.List<ControlCenterAppTiles.Entry> controlCenterAppTiles = java.util.Collections.emptyList();
    private boolean controlCenterAppTilesLoading;
    private Runnable controlCenterAppTilesReady;
    private boolean previewWifi = true;
    private final android.os.Handler saveHandler = new android.os.Handler(android.os.Looper.getMainLooper());
    private final java.lang.Runnable controlCenterPreviewPoll = new java.lang.Runnable() { // from class: com.xtjm.fusionstatusbar.MainActivity.1
        @Override // java.lang.Runnable
        public void run() {
            com.xtjm.fusionstatusbar.MainActivity.this.pollControlCenterPreview();
        }
    };
    private final java.lang.Runnable saveSettings = new java.lang.Runnable() { // from class: com.xtjm.fusionstatusbar.MainActivity.2
        @Override // java.lang.Runnable
        public final void run() {
            com.xtjm.fusionstatusbar.MainActivity.this.lambda$new$0();
        }
    };

    interface BooleanUpdate {
        void apply(boolean z);
    }

    interface DialogBuilder {
        void build(android.widget.LinearLayout linearLayout);
    }

    interface IntegerUpdate {
        void apply(int i);
    }

    interface ScaleUpdate {
        com.xtjm.fusionstatusbar.FusionConfig apply(int i);
    }

    public void lambda$new$0() {
        if (controlCenterSaving || configRestoring) return;
        this.pendingSettingsSave = false;
        if (configSession == null || config == null) return;
        FusionConfig submitted = config;
        configWriteInFlight = true;
        configSession.write(submitted, result -> {
            configWriteInFlight = false;
            if (!result.success) {
                pendingSettingsSave = true;
                Toast.makeText(this, R.string.editor_save_failed, Toast.LENGTH_LONG).show();
            } else if (config == submitted) {
                config = result.config;
                controlCenterSavedRevision = result.revision;
                editorPage.published(result.revision);
                updateSettingsSummaries();
            }
        });
    }

    @Override // android.app.Activity
    protected void onCreate(android.os.Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(com.xtjm.fusionstatusbar.R.layout.activity_main);
        installStartupOverlay();
        editorPage = new ControlCenterPageCoordinator(new ControlCenterDraftStore(this), this::updateSettingsSummaries);
        previewLoader = new ControlCenterPreviewLoader(this);
        configSession = FusionConfigRepository.forApplication(this).newSession();
        startupCoordinator = new SettingsStartupCoordinator(configSession, editorPage,
                new SettingsStartupCoordinator.Host() {
                    @Override public boolean isAlive() { return !isDestroyed() && !isFinishing(); }

                    @Override public void onSettingsReady(SettingsStartupCoordinator.Snapshot loaded,
                            ControlCenterEditorSession restoredSession, android.os.Bundle state) {
                        config = loaded.config;
                        controlCenterSavedRevision = loaded.revision;
                        editorSession = restoredSession;
                        syncEditorState();
                        initializeSettingsViews(state);
                        settingsLoaded = true;
                        if (editorSession.isDirty())
                            Toast.makeText(MainActivity.this, R.string.editor_draft_restored, Toast.LENGTH_SHORT).show();
                        refreshOverview();
                        ((android.view.ViewGroup) startupOverlay.getParent()).removeView(startupOverlay);
                        startupOverlay = null;
                    }

                    @Override public void onDeferredActivityResult(int requestCode, int resultCode,
                            android.content.Intent data) {
                        backupController().onActivityResult(requestCode, resultCode, data);
                    }
                });
        startupCoordinator.start(savedInstanceState);
    }

    private void installStartupOverlay() {
        android.widget.FrameLayout root = findViewById(android.R.id.content);
        android.widget.FrameLayout overlay = new android.widget.FrameLayout(this);
        overlay.setBackgroundColor(pageColor());
        overlay.setClickable(true);
        android.widget.LinearLayout loading = new android.widget.LinearLayout(this);
        loading.setOrientation(LinearLayout.VERTICAL);
        loading.setGravity(android.view.Gravity.CENTER);
        android.widget.ProgressBar progress = new android.widget.ProgressBar(this);
        loading.addView(progress, new android.widget.LinearLayout.LayoutParams(dp(40), dp(40)));
        android.widget.TextView label = labelView(getString(R.string.starting_settings), 15, false);
        label.setContentDescription(getString(R.string.starting_settings));
        label.setTextColor(textSecondaryColor());
        android.widget.LinearLayout.LayoutParams labelParams = matchWrap();
        labelParams.topMargin = dp(16);
        loading.addView(label, labelParams);
        overlay.addView(loading, new android.widget.FrameLayout.LayoutParams(-2, -2, android.view.Gravity.CENTER));
        root.addView(overlay, new android.widget.FrameLayout.LayoutParams(-1, -1));
        startupOverlay = overlay;
    }

    private void initializeSettingsViews(android.os.Bundle savedInstanceState) {
        final android.widget.Button restartSystemUi = (android.widget.Button) findViewById(com.xtjm.fusionstatusbar.R.id.restart_systemui_button);
        restartSystemUi.setOnClickListener(new android.view.View.OnClickListener() { // from class: com.xtjm.fusionstatusbar.MainActivity.3
            @Override // android.view.View.OnClickListener
            public final void onClick(android.view.View view) {
                com.xtjm.fusionstatusbar.MainActivity.this.lambda$onCreate$1(restartSystemUi, view);
            }
        });
        findViewById(com.xtjm.fusionstatusbar.R.id.export_logs_button).setOnClickListener(new android.view.View.OnClickListener() { // from class: com.xtjm.fusionstatusbar.MainActivity.4
            @Override // android.view.View.OnClickListener
            public final void onClick(android.view.View view) {
                com.xtjm.fusionstatusbar.MainActivity.this.lambda$onCreate$2(view);
            }
        });
        this.preview = (com.xtjm.fusionstatusbar.FusionIconView) findViewById(com.xtjm.fusionstatusbar.R.id.preview);
        this.previewBackdrop = (com.xtjm.fusionstatusbar.FusionPreviewBackdrop) findViewById(com.xtjm.fusionstatusbar.R.id.preview_backdrop);
        refreshPreview();
        this.preview.setPreviewConfig(this.config);
        android.widget.RadioGroup previewMode = (android.widget.RadioGroup) findViewById(com.xtjm.fusionstatusbar.R.id.preview_mode);
        previewMode.setOnCheckedChangeListener(new android.widget.RadioGroup.OnCheckedChangeListener() { // from class: com.xtjm.fusionstatusbar.MainActivity.5
            @Override // android.widget.RadioGroup.OnCheckedChangeListener
            public final void onCheckedChanged(android.widget.RadioGroup radioGroup, int i) {
                com.xtjm.fusionstatusbar.MainActivity.this.lambda$onCreate$3(radioGroup, i);
            }
        });
        bindScale(com.xtjm.fusionstatusbar.R.id.icon_size_seek, com.xtjm.fusionstatusbar.R.id.icon_size_value, new com.xtjm.fusionstatusbar.MainActivity.ScaleUpdate() { // from class: com.xtjm.fusionstatusbar.MainActivity.6
            @Override // com.xtjm.fusionstatusbar.MainActivity.ScaleUpdate
            public final com.xtjm.fusionstatusbar.FusionConfig apply(int i) {
                com.xtjm.fusionstatusbar.FusionConfig lambda$onCreate$4 = com.xtjm.fusionstatusbar.MainActivity.this.lambda$onCreate$4(i);
                return lambda$onCreate$4;
            }
        });
        bindScale(com.xtjm.fusionstatusbar.R.id.stroke_size_seek, com.xtjm.fusionstatusbar.R.id.stroke_size_value, new com.xtjm.fusionstatusbar.MainActivity.ScaleUpdate() { // from class: com.xtjm.fusionstatusbar.MainActivity.7
            @Override // com.xtjm.fusionstatusbar.MainActivity.ScaleUpdate
            public final com.xtjm.fusionstatusbar.FusionConfig apply(int i) {
                com.xtjm.fusionstatusbar.FusionConfig lambda$onCreate$5 = com.xtjm.fusionstatusbar.MainActivity.this.lambda$onCreate$5(i);
                return lambda$onCreate$5;
            }
        });
        ((android.widget.Switch) findViewById(com.xtjm.fusionstatusbar.R.id.custom_clock_switch)).setOnCheckedChangeListener(new android.widget.CompoundButton.OnCheckedChangeListener() { // from class: com.xtjm.fusionstatusbar.MainActivity.8
            @Override // android.widget.CompoundButton.OnCheckedChangeListener
            public final void onCheckedChanged(android.widget.CompoundButton compoundButton, boolean z) {
                com.xtjm.fusionstatusbar.MainActivity.this.lambda$onCreate$6(compoundButton, z);
            }
        });
        ((android.widget.Switch) findViewById(com.xtjm.fusionstatusbar.R.id.show_weather_switch)).setOnCheckedChangeListener(new android.widget.CompoundButton.OnCheckedChangeListener() { // from class: com.xtjm.fusionstatusbar.MainActivity.9
            @Override // android.widget.CompoundButton.OnCheckedChangeListener
            public final void onCheckedChanged(android.widget.CompoundButton compoundButton, boolean z) {
                com.xtjm.fusionstatusbar.MainActivity.this.lambda$onCreate$7(compoundButton, z);
            }
        });
        android.widget.EditText clockFormatInput = (android.widget.EditText) findViewById(com.xtjm.fusionstatusbar.R.id.clock_format_input);
        final android.widget.TextView clockFormatError = (android.widget.TextView) findViewById(com.xtjm.fusionstatusbar.R.id.clock_format_error);
        clockFormatInput.addTextChangedListener(new android.text.TextWatcher() { // from class: com.xtjm.fusionstatusbar.MainActivity.10
            @Override // android.text.TextWatcher
            public void beforeTextChanged(java.lang.CharSequence text, int start, int count, int after) {
            }

            @Override // android.text.TextWatcher
            public void onTextChanged(java.lang.CharSequence text, int start, int before, int count) {
            }

            @Override // android.text.TextWatcher
            public void afterTextChanged(android.text.Editable text) {
                if (com.xtjm.fusionstatusbar.MainActivity.this.updating) {
                    return;
                }
                java.lang.String pattern = text.toString();
                boolean valid = com.xtjm.fusionstatusbar.ClockTextFormatter.isValidPattern(pattern);
                clockFormatError.setVisibility(valid ? View.GONE : View.VISIBLE);
                if (valid) {
                    com.xtjm.fusionstatusbar.MainActivity.this.config = com.xtjm.fusionstatusbar.MainActivity.this.config.withClockPattern(pattern);
                    com.xtjm.fusionstatusbar.MainActivity.this.updatePreviewElements();
                    com.xtjm.fusionstatusbar.MainActivity.this.scheduleSettingsSave();
                }
            }
        });
        bindSpanOffset(com.xtjm.fusionstatusbar.R.id.span_horizontal_seek, com.xtjm.fusionstatusbar.R.id.span_horizontal_value, true);
        bindSpanOffset(com.xtjm.fusionstatusbar.R.id.span_vertical_seek, com.xtjm.fusionstatusbar.R.id.span_vertical_value, false);
        bindTelemetryControls();
        android.widget.Switch doubleRow = (android.widget.Switch) findViewById(com.xtjm.fusionstatusbar.R.id.double_row_switch);
        doubleRow.setOnCheckedChangeListener(new android.widget.CompoundButton.OnCheckedChangeListener() { // from class: com.xtjm.fusionstatusbar.MainActivity.11
            @Override // android.widget.CompoundButton.OnCheckedChangeListener
            public final void onCheckedChanged(android.widget.CompoundButton compoundButton, boolean z) {
                com.xtjm.fusionstatusbar.MainActivity.this.lambda$onCreate$8(compoundButton, z);
            }
        });
        android.widget.Switch spanRows = (android.widget.Switch) findViewById(com.xtjm.fusionstatusbar.R.id.span_rows_switch);
        spanRows.setOnCheckedChangeListener(new android.widget.CompoundButton.OnCheckedChangeListener() { // from class: com.xtjm.fusionstatusbar.MainActivity.12
            @Override // android.widget.CompoundButton.OnCheckedChangeListener
            public final void onCheckedChanged(android.widget.CompoundButton compoundButton, boolean z) {
                com.xtjm.fusionstatusbar.MainActivity.this.lambda$onCreate$9(compoundButton, z);
            }
        });
        android.widget.Switch wifiIcon = (android.widget.Switch) findViewById(com.xtjm.fusionstatusbar.R.id.wifi_icon_switch);
        wifiIcon.setOnCheckedChangeListener(new android.widget.CompoundButton.OnCheckedChangeListener() { // from class: com.xtjm.fusionstatusbar.MainActivity.13
            @Override // android.widget.CompoundButton.OnCheckedChangeListener
            public final void onCheckedChanged(android.widget.CompoundButton compoundButton, boolean z) {
                com.xtjm.fusionstatusbar.MainActivity.this.lambda$onCreate$10(compoundButton, z);
            }
        });
        bindPosition(com.xtjm.fusionstatusbar.R.id.clock_side, com.xtjm.fusionstatusbar.R.id.clock_row, 0);
        bindPosition(com.xtjm.fusionstatusbar.R.id.notification_side, com.xtjm.fusionstatusbar.R.id.notification_row, 1);
        bindPosition(com.xtjm.fusionstatusbar.R.id.system_side, com.xtjm.fusionstatusbar.R.id.system_row, 2);
        android.widget.SeekBar statusHeight = (android.widget.SeekBar) findViewById(com.xtjm.fusionstatusbar.R.id.statusbar_height_seek);
        final android.widget.TextView statusHeightLabel = (android.widget.TextView) findViewById(com.xtjm.fusionstatusbar.R.id.statusbar_height_label);
        statusHeight.setOnSeekBarChangeListener(new android.widget.SeekBar.OnSeekBarChangeListener() { // from class: com.xtjm.fusionstatusbar.MainActivity.14
            @Override // android.widget.SeekBar.OnSeekBarChangeListener
            public void onProgressChanged(android.widget.SeekBar bar, int progress, boolean fromUser) {
                int height = progress + 32;
                statusHeightLabel.setText(com.xtjm.fusionstatusbar.MainActivity.this.getString(com.xtjm.fusionstatusbar.R.string.statusbar_height_value, new java.lang.Object[]{java.lang.Integer.valueOf(height)}));
                if (fromUser && !com.xtjm.fusionstatusbar.MainActivity.this.updating) {
                    com.xtjm.fusionstatusbar.MainActivity.this.update(com.xtjm.fusionstatusbar.MainActivity.this.config.withStatusBarHeight(height));
                }
            }

            @Override // android.widget.SeekBar.OnSeekBarChangeListener
            public void onStartTrackingTouch(android.widget.SeekBar bar) {
            }

            @Override // android.widget.SeekBar.OnSeekBarChangeListener
            public void onStopTrackingTouch(android.widget.SeekBar bar) {
            }
        });
        installMiuixSettingsUi();
        applyControls();
        if (savedInstanceState != null) {
            selectPage(savedInstanceState.getInt(KEY_SELECTED_PAGE, 0));
        }
    }

    public void lambda$onCreate$1(android.widget.Button restartSystemUi, android.view.View view) {
        restartSystemUi(restartSystemUi);
    }

    public void lambda$onCreate$2(android.view.View view) {
        chooseLogDestination();
    }

    public void lambda$onCreate$3(android.widget.RadioGroup group, int checkedId) {
        this.previewWifi = checkedId == com.xtjm.fusionstatusbar.R.id.preview_wifi;
        refreshPreview();
    }

    public com.xtjm.fusionstatusbar.FusionConfig lambda$onCreate$4(int value) {
        return this.config.withIconScale(value);
    }

    public com.xtjm.fusionstatusbar.FusionConfig lambda$onCreate$5(int value) {
        return this.config.withStrokeScale(value);
    }

    public void lambda$onCreate$6(android.widget.CompoundButton button, boolean checked) {
        if (!this.updating) {
            update(this.config.withCustomClock(checked));
        }
    }

    public void lambda$onCreate$7(android.widget.CompoundButton button, boolean checked) {
        if (!this.updating) {
            update(this.config.withShowWeather(checked));
        }
    }

    public void lambda$onCreate$8(android.widget.CompoundButton button, boolean checked) {
        if (!this.updating) {
            update(this.config.withDoubleRow(checked));
        }
    }

    public void lambda$onCreate$9(android.widget.CompoundButton button, boolean checked) {
        if (!this.updating) {
            update(this.config.withSpanRows(checked));
        }
    }

    public void lambda$onCreate$10(android.widget.CompoundButton button, boolean checked) {
        if (!this.updating) {
            update(this.config.withWifiIcon(checked));
        }
    }

    @Override // android.app.Activity
    protected void onSaveInstanceState(android.os.Bundle outState) {
        boolean previous = suppressEditorSelection;
        suppressEditorSelection = true;
        if (controlCenterWallpaperWorkspace != null) controlCenterWallpaperWorkspace.close();
        if (isChangingConfigurations()) outState.putInt(KEY_SELECTED_PAGE, this.selectedPage);
        if (editorPage != null) editorPage.saveState(outState);
        super.onSaveInstanceState(outState);
        suppressEditorSelection = previous;
    }

    @Override // android.app.Activity
    protected void onResume() {
        super.onResume();
        resumed = true;
        suppressEditorSelection = false;
        if (editorSession != null && controlCenterGridEditor != null) controlCenterGridEditor.restoreSelection(editorSession.selection());
        refreshControlCenterAppTiles();
        if (this.activationObserver == null) {
            this.activationObserver = new android.database.ContentObserver(new android.os.Handler(android.os.Looper.getMainLooper())) { // from class: com.xtjm.fusionstatusbar.MainActivity.15
                @Override // android.database.ContentObserver
                public void onChange(boolean selfChange) {
                    com.xtjm.fusionstatusbar.MainActivity.this.refreshOverview();
                    updateSettingsSummaries();
                }
            };
            getContentResolver().registerContentObserver(com.xtjm.fusionstatusbar.FusionActivationStatus.contentUri(), false, this.activationObserver);
        }
        refreshOverview();
        if (this.controlCenterActualPreview != null) {
            loadLatestControlCenterPreview();
            if (this.config != null && controlCenterPreviewRequest > 0) {
                this.controlCenterPreviewPolls = 0;
                this.saveHandler.removeCallbacks(this.controlCenterPreviewPoll);
                this.saveHandler.post(this.controlCenterPreviewPoll);
            }
        }
    }

    private void installMiuixSettingsUi() {
        int systemUi;
        android.view.View previewModeView = findViewById(com.xtjm.fusionstatusbar.R.id.preview_mode);
        if (!(previewModeView.getParent() instanceof android.widget.LinearLayout)) {
            return;
        }
        android.widget.LinearLayout root = (android.widget.LinearLayout) previewModeView.getParent();
        root.setPadding(dp(18), dp(12), dp(18), dp(116));
        root.setBackgroundColor(pageColor());
        android.view.Window window = getWindow();
        window.setStatusBarColor(pageColor());
        window.setNavigationBarColor(pageColor());
        int systemUi2 = window.getDecorView().getSystemUiVisibility();
        if (isNightMode()) {
            systemUi = systemUi2 & (-8209);
        } else {
            systemUi = systemUi2 | 8208;
        }
        window.getDecorView().setSystemUiVisibility(systemUi);
        final android.view.View restart = findViewById(com.xtjm.fusionstatusbar.R.id.restart_systemui_button);
        final android.view.View export = findViewById(com.xtjm.fusionstatusbar.R.id.export_logs_button);
        android.widget.TextView previewTitle = (android.widget.TextView) root.getChildAt(4);
        android.widget.FrameLayout previewFrame = (android.widget.FrameLayout) findViewById(com.xtjm.fusionstatusbar.R.id.preview_frame);
        root.removeView(previewTitle);
        root.removeView(previewFrame);
        root.removeView(previewModeView);
        for (int index = 0; index < root.getChildCount(); index++) {
            root.getChildAt(index).setVisibility(View.GONE);
        }
        this.pageScroll = (android.widget.ScrollView) root.getParent();
        android.view.ViewGroup viewGroup = (android.view.ViewGroup) this.pageScroll.getParent();
        android.view.ViewGroup.LayoutParams originalParams = this.pageScroll.getLayoutParams();
        viewGroup.removeView(this.pageScroll);
        android.widget.FrameLayout frameLayout = new android.widget.FrameLayout(this);
        frameLayout.setClipChildren(false);
        frameLayout.setClipToPadding(false);
        frameLayout.setBackgroundColor(pageColor());
        viewGroup.addView(frameLayout, originalParams);
        frameLayout.addView(this.pageScroll, new android.widget.FrameLayout.LayoutParams(-1, -1));
        android.widget.LinearLayout linearLayoutNewPage = newPage(root, 0, "概述");
        linearLayoutNewPage.addView(sectionTitle("模块状态"));
        android.widget.LinearLayout linearLayoutCard = card();
        linearLayoutNewPage.addView(linearLayoutCard, matchWrap());
        android.widget.LinearLayout activationRow = new android.widget.LinearLayout(this);
        activationRow.setGravity(16);
        activationRow.setPadding(dp(20), dp(18), dp(18), dp(18));
        this.activationIndicator = new android.view.View(this);
        activationRow.addView(this.activationIndicator, new android.widget.LinearLayout.LayoutParams(dp(12), dp(12)));
        android.widget.LinearLayout activationText = new android.widget.LinearLayout(this);
        activationText.setOrientation(LinearLayout.VERTICAL);
        android.widget.LinearLayout.LayoutParams activationTextParams = new android.widget.LinearLayout.LayoutParams(0, -2, 1.0f);
        activationTextParams.leftMargin = dp(14);
        this.activationTitle = labelView("正在检查", 19.0f, true);
        activationText.addView(this.activationTitle, matchWrap());
        this.activationSummary = labelView("", 13.0f, false);
        this.activationSummary.setTextColor(textSecondaryColor());
        activationText.addView(this.activationSummary, matchWrap());
        activationRow.addView(activationText, activationTextParams);
        linearLayoutCard.addView(activationRow, matchWrap());
        linearLayoutNewPage.addView(sectionTitle("设备信息"));
        android.widget.LinearLayout deviceCard = card();
        linearLayoutNewPage.addView(deviceCard, matchWrap());
        addDetail(deviceCard, "设备", android.os.Build.MANUFACTURER + "  " + android.os.Build.MODEL);
        addDivider(deviceCard);
        addDetail(deviceCard, "系统", "Android " + android.os.Build.VERSION.RELEASE + "  ·  API " + android.os.Build.VERSION.SDK_INT);
        linearLayoutNewPage.addView(sectionTitle("关于"));
        android.widget.LinearLayout aboutCard = card();
        linearLayoutNewPage.addView(aboutCard, matchWrap());
        addDetail(aboutCard, "FusionStatusBar", getString(R.string.about_project_version,
                BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE));
        addDivider(aboutCard);
        addPreference(aboutCard, getString(R.string.about_project_repository),
                PROJECT_REPOSITORY_URL, view -> openRepositoryUrl(PROJECT_REPOSITORY_URL));
        addDivider(aboutCard);
        addPreference(aboutCard, getString(R.string.about_miuix_credit),
                getString(R.string.about_miuix_summary), view -> openRepositoryUrl(MIUIX_REPOSITORY_URL));
        android.widget.LinearLayout statusPage = newPage(root, 1, "状态栏");
        previewTitle.setText("预览");
        previewTitle.setTextSize(15.0f);
        previewTitle.setTextColor(textSecondaryColor());
        android.widget.LinearLayout.LayoutParams previewHeadingParams = matchWrap();
        previewHeadingParams.topMargin = dp(16);
        previewHeadingParams.bottomMargin = dp(8);
        statusPage.addView(previewTitle, previewHeadingParams);
        previewFrame.setBackground(rounded(android.graphics.Color.parseColor("#FF1C242C"), dp(8)));
        previewFrame.setClipToOutline(true);
        statusPage.addView(previewFrame, new android.widget.LinearLayout.LayoutParams(-1, dp(200)));
        statusPage.addView(previewModeView, matchWrap());
        statusPage.addView(sectionTitle("显示与布局"));
        android.widget.LinearLayout personalizationCard = card();
        statusPage.addView(personalizationCard, matchWrap());
        android.widget.LinearLayout fusionRow = addPreference(personalizationCard, "融合图标", "无线网络、移动网络和电量融合显示", new android.view.View.OnClickListener() { // from class: com.xtjm.fusionstatusbar.MainActivity.16
            @Override // android.view.View.OnClickListener
            public final void onClick(android.view.View view) {
                com.xtjm.fusionstatusbar.MainActivity.this.lambda$installMiuixSettingsUi$11(view);
            }
        });
        this.fusionSettingsSummary = (android.widget.TextView) fusionRow.getTag();
        addDivider(personalizationCard);
        android.widget.LinearLayout layoutRow = addPreference(personalizationCard, "状态栏布局", "双排、元素位置和状态栏高度", new android.view.View.OnClickListener() { // from class: com.xtjm.fusionstatusbar.MainActivity.17
            @Override // android.view.View.OnClickListener
            public final void onClick(android.view.View view) {
                com.xtjm.fusionstatusbar.MainActivity.this.lambda$installMiuixSettingsUi$12(view);
            }
        });
        this.layoutSettingsSummary = (android.widget.TextView) layoutRow.getTag();
        addDivider(personalizationCard);
        android.widget.LinearLayout clockRow = addPreference(personalizationCard, "时间与天气", "状态栏时间、农历、星期和天气", new android.view.View.OnClickListener() { // from class: com.xtjm.fusionstatusbar.MainActivity.18
            @Override // android.view.View.OnClickListener
            public final void onClick(android.view.View view) {
                com.xtjm.fusionstatusbar.MainActivity.this.lambda$installMiuixSettingsUi$13(view);
            }
        });
        this.clockSettingsSummary = (android.widget.TextView) clockRow.getTag();
        addDivider(personalizationCard);
        android.widget.LinearLayout telemetryRow = addPreference(personalizationCard, "状态栏监测", "温度、功率、电流和实时网速", new android.view.View.OnClickListener() { // from class: com.xtjm.fusionstatusbar.MainActivity.19
            @Override // android.view.View.OnClickListener
            public final void onClick(android.view.View view) {
                com.xtjm.fusionstatusbar.MainActivity.this.lambda$installMiuixSettingsUi$14(view);
            }
        });
        this.telemetrySettingsSummary = (android.widget.TextView) telemetryRow.getTag();
        android.widget.LinearLayout notificationPage = newPage(root, 2, "通知栏");
        notificationPage.addView(sectionTitle("预览"));
        this.notificationPreview = new android.widget.LinearLayout(this);
        this.notificationPreview.setOrientation(LinearLayout.VERTICAL);
        this.notificationPreview.setGravity(17);
        this.notificationPreview.setBackground(rounded(android.graphics.Color.parseColor("#FF1C242C"), dp(8)));
        this.notificationTimePreview = labelView("14:37", 52.0f, true);
        this.notificationTimePreview.setTextColor(-1);
        this.notificationTimePreview.setGravity(17);
        this.notificationTimePreview.setPadding(dp(12), 0, dp(12), 0);
        this.notificationTimePreview.setSingleLine(false);
        this.notificationTimePreview.setMaxLines(2);
        this.notificationPreview.addView(this.notificationTimePreview, new android.widget.LinearLayout.LayoutParams(-1, 0, 1.0f));
        this.notificationListPreview = labelView("应用通知", 14.0f, false);
        this.notificationListPreview.setTextColor(-1);
        this.notificationListPreview.setGravity(16);
        this.notificationListPreview.setPadding(dp(16), 0, dp(16), 0);
        this.notificationPreview.addView(this.notificationListPreview, new android.widget.LinearLayout.LayoutParams(-1, dp(44)));
        notificationPage.addView(this.notificationPreview, new android.widget.LinearLayout.LayoutParams(-1, dp(212)));
        notificationPage.addView(sectionTitle("通知中心"));
        android.widget.LinearLayout notificationCard = card();
        notificationPage.addView(notificationCard, matchWrap());
        android.widget.LinearLayout notificationRow = addPreference(notificationCard, "通知中心时间", "使用系统时间", new android.view.View.OnClickListener() { // from class: com.xtjm.fusionstatusbar.MainActivity.20
            @Override // android.view.View.OnClickListener
            public final void onClick(android.view.View view) {
                com.xtjm.fusionstatusbar.MainActivity.this.lambda$installMiuixSettingsUi$15(view);
            }
        });
        this.notificationClockSummary = (android.widget.TextView) notificationRow.getTag();
        addDivider(notificationCard);
        android.widget.LinearLayout listRow = addPreference(notificationCard, "通知列表", "下移 0 dp", new android.view.View.OnClickListener() { // from class: com.xtjm.fusionstatusbar.MainActivity.21
            @Override // android.view.View.OnClickListener
            public final void onClick(android.view.View view) {
                com.xtjm.fusionstatusbar.MainActivity.this.lambda$installMiuixSettingsUi$17(view);
            }
        });
        this.notificationListSummary = (android.widget.TextView) listRow.getTag();
        android.widget.LinearLayout controlCenterPage = newPage(root, 3, "控制中心");
        installControlCenterPage(controlCenterPage);
        android.widget.LinearLayout settingsPage = newPage(root, 4, "设置");
        settingsPage.addView(sectionTitle("权限"));
        android.widget.LinearLayout permissionCard = card();
        settingsPage.addView(permissionCard, matchWrap());
        android.widget.LinearLayout phoneRow = addPreference(permissionCard, "电话状态权限", "检查中", new android.view.View.OnClickListener() { // from class: com.xtjm.fusionstatusbar.MainActivity.22
            @Override // android.view.View.OnClickListener
            public final void onClick(android.view.View view) {
                com.xtjm.fusionstatusbar.MainActivity.this.lambda$installMiuixSettingsUi$18(view);
            }
        });
        this.phonePermissionSummary = (android.widget.TextView) phoneRow.getTag();
        addDivider(permissionCard);
        addPreference(permissionCard, "应用权限管理", "打开系统权限页面", new android.view.View.OnClickListener() { // from class: com.xtjm.fusionstatusbar.MainActivity.23
            @Override // android.view.View.OnClickListener
            public final void onClick(android.view.View view) {
                com.xtjm.fusionstatusbar.MainActivity.this.lambda$installMiuixSettingsUi$19(view);
            }
        });
        settingsPage.addView(sectionTitle("模块维护"));
        android.widget.LinearLayout maintenanceCard = card();
        settingsPage.addView(maintenanceCard, matchWrap());
        addPreference(maintenanceCard, getString(R.string.editor_backup_export),
                getString(R.string.editor_backup_export_summary), view -> backupController().exportConfiguration());
        addDivider(maintenanceCard);
        addPreference(maintenanceCard, getString(R.string.editor_backup_import),
                getString(R.string.editor_backup_import_summary), view -> backupController().importConfiguration());
        addDivider(maintenanceCard);
        addPreference(maintenanceCard, getString(R.string.editor_backup_undo),
                getString(R.string.editor_backup_undo_summary), view -> backupController().undoRestore());
        addDivider(maintenanceCard);
        addPreference(maintenanceCard, "重启系统界面", "配置修改后让系统界面重新加载", new android.view.View.OnClickListener() { // from class: com.xtjm.fusionstatusbar.MainActivity.24
            @Override // android.view.View.OnClickListener
            public final void onClick(android.view.View view) {
                restart.performClick();
            }
        });
        addDivider(maintenanceCard);
        addPreference(maintenanceCard, "导出诊断日志", "导出最近的模块日志用于排查问题", new android.view.View.OnClickListener() { // from class: com.xtjm.fusionstatusbar.MainActivity.25
            @Override // android.view.View.OnClickListener
            public final void onClick(android.view.View view) {
                export.performClick();
            }
        });
        addDivider(maintenanceCard);
        addPreference(maintenanceCard, "恢复默认设置", "清除状态栏和通知栏的自定义参数", new android.view.View.OnClickListener() { // from class: com.xtjm.fusionstatusbar.MainActivity.26
            @Override // android.view.View.OnClickListener
            public final void onClick(android.view.View view) {
                com.xtjm.fusionstatusbar.MainActivity.this.lambda$installMiuixSettingsUi$22(view);
            }
        });
        android.widget.LinearLayout navigation = new android.widget.LinearLayout(this);
        navigation.setGravity(17);
        navigation.setPadding(dp(6), dp(4), dp(6), dp(4));
        android.graphics.drawable.GradientDrawable navigationBackground = rounded(surfaceColor(), dp(36));
        navigationBackground.setStroke(dp(1), android.graphics.Color.parseColor(isNightMode() ? "#FF3A3A3D" : "#FFE6E8EB"));
        navigation.setBackground(navigationBackground);
        navigation.setElevation(dp(12));
        int[] icons = {android.R.drawable.ic_menu_info_details, android.R.drawable.ic_menu_edit, android.R.drawable.ic_popup_reminder, android.R.drawable.ic_menu_view, android.R.drawable.ic_menu_manage};
        java.lang.String[] labels = {"概述", "状态栏", "通知栏", "控制中心", "设置"};
        int i = 0;
        while (i < this.pages.length) {
            final int page = i;
            android.widget.LinearLayout item = new android.widget.LinearLayout(this);
            item.setOrientation(LinearLayout.VERTICAL);
            item.setGravity(17);
            item.setContentDescription(labels[i]);
            android.widget.ImageView icon = new android.widget.ImageView(this);
            icon.setImageResource(icons[i]);
            item.addView(icon, new android.widget.LinearLayout.LayoutParams(dp(23), dp(23)));
            android.widget.TextView label = labelView(labels[i], 11.0f, false);
            label.setGravity(17);
            android.widget.LinearLayout.LayoutParams labelParams = matchWrap();
            labelParams.topMargin = dp(5);
            item.addView(label, labelParams);
            item.setOnClickListener(new android.view.View.OnClickListener() { // from class: com.xtjm.fusionstatusbar.MainActivity.27
                @Override // android.view.View.OnClickListener
                public final void onClick(android.view.View view) {
                    com.xtjm.fusionstatusbar.MainActivity.this.lambda$installMiuixSettingsUi$23(page, view);
                }
            });
            this.navigationItems[i] = item;
            navigation.addView(item, new android.widget.LinearLayout.LayoutParams(0, -1, 1.0f));
            i++;
            icons = icons;
            labels = labels;
        }
        android.widget.FrameLayout.LayoutParams navigationParams = new android.widget.FrameLayout.LayoutParams(-1, dp(68), 80);
        navigationParams.leftMargin = dp(18);
        navigationParams.rightMargin = dp(18);
        navigationParams.bottomMargin = dp(16);
        frameLayout.addView(navigation, navigationParams);
        selectPage(0);
        refreshOverview();
    }

    public void lambda$installMiuixSettingsUi$11(android.view.View v) {
        showFusionDialog();
    }

    public void lambda$installMiuixSettingsUi$12(android.view.View v) {
        showLayoutDialog();
    }

    public void lambda$installMiuixSettingsUi$13(android.view.View v) {
        showClockDialog();
    }

    public void lambda$installMiuixSettingsUi$14(android.view.View v) {
        showTelemetryDialog();
    }

    public void lambda$installMiuixSettingsUi$15(android.view.View v) {
        showNotificationClockDialog();
    }

    public void lambda$installMiuixSettingsUi$17(android.view.View v) {
        showNotificationListDialog();
    }

    public void lambda$installMiuixSettingsUi$18(android.view.View v) {
        requestPhonePermission();
    }

    public void lambda$installMiuixSettingsUi$19(android.view.View v) {
        openAppPermissions();
    }

    public void lambda$installMiuixSettingsUi$22(android.view.View v) {
        confirmResetSettings();
    }

    public void lambda$installMiuixSettingsUi$23(int page, android.view.View v) {
        selectPage(page);
    }

    private android.widget.LinearLayout newPage(android.widget.LinearLayout root, int index, java.lang.String heading) {
        android.widget.LinearLayout page = new android.widget.LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        android.widget.TextView title = labelView(heading, 28.0f, true);
        title.setPadding(0, dp(12), 0, dp(6));
        page.addView(title, matchWrap());
        root.addView(page, matchWrap());
        this.pages[index] = page;
        return page;
    }

    private void installControlCenterPage(android.widget.LinearLayout page) {
        LinearLayout settingsCard = controlCenterCard();
        controlCenterEnabledSwitch = addSwitch(settingsCard, "自定义控制中心",
                "开启后编辑布局，推送后应用到手机", controlCenterSettings().enabled,
                value -> updateControlCenterDraft(controlCenterSettings().withEnabled(value)));
        View switchRow = settingsCard.getChildAt(0);
        View switchTexts = ((LinearLayout) switchRow).getChildAt(0);
        switchTexts.getLayoutParams().height = -2;
        addDivider(settingsCard);
        LinearLayout settingsRow = addPreference(settingsCard, "布局与外观",
                "画布列数、内容缩放、默认圆角与间距", view -> showControlCenterSettingsDialog());
        settingsRow.setPadding(0, dp(10), 0, 0);
        settingsRow.getChildAt(0).getLayoutParams().height = -2;
        controlCenterSummary = (android.widget.TextView) settingsRow.getTag();
        page.addView(settingsCard, controlCenterSectionParams());

        controlCenterPreviewTitle = sectionTitle("布局画布");
        page.addView(controlCenterPreviewTitle);
        controlCenterGridPanel = controlCenterCard();
        LinearLayout modeBar = new LinearLayout(this);
        modeBar.setPadding(dp(4), dp(4), dp(4), dp(4));
        modeBar.setBackground(rounded(inputColor(), dp(14)));
        controlCenterRegularModeButton = controlCenterButton("标准布局", false,
                view -> selectControlCenterMode(false));
        controlCenterCompactModeButton = controlCenterButton("紧凑布局", false,
                view -> selectControlCenterMode(true));
        addControlCenterAction(modeBar, controlCenterRegularModeButton, false);
        addControlCenterAction(modeBar, controlCenterCompactModeButton, true);
        controlCenterGridPanel.addView(modeBar, matchWrap());
        controlCenterCanvasSummary = labelView("", 12f, false);
        controlCenterCanvasSummary.setTextColor(textSecondaryColor());
        controlCenterCanvasSummary.setPadding(0, dp(12), 0, dp(10));
        controlCenterGridPanel.addView(controlCenterCanvasSummary, matchWrap());
        controlCenterGridEditor = new ControlCenterGridEditor(this, new ControlCenterGridEditor.Listener() {
            @Override public void onItemClicked(String id) { showControlGridItemDialog(id); }
            @Override public void onItemMoved(String id, int x, int y) { commitControlGridGesture(id, x, y, -1, -1); }
            @Override public void onItemResized(String id, int width, int height) {
                commitControlGridGesture(id, -1, -1, width, height);
            }
            @Override public void onItemAction(String id, ControlCenterGridEditor.EditAction action) {
                applyControlGridOverlayAction(id, action);
            }
            @Override public void onSelectionChanged(String id) {
                if (editorSession != null && !suppressEditorSelection) { editorSession.select(id); persistEditorSession(); }
            }
        });
        controlCenterGridPanel.addView(controlCenterGridEditor, matchWrap());
        android.widget.TextView gestureHint = labelView("点击查看属性 · 长按编辑与移动 · 状态为示意", 12f, false);
        gestureHint.setTextColor(textSecondaryColor());
        gestureHint.setPadding(0, dp(10), 0, dp(12));
        controlCenterGridPanel.addView(gestureHint, matchWrap());
        controlCenterGridPanel.addView(controlCenterButton("壁纸画布", false, view -> {
            if (controlCenterWallpaperWorkspace == null) {
                controlCenterWallpaperWorkspace = new ControlCenterWallpaperWorkspace(this, controlCenterGridEditor);
            }
            controlCenterWallpaperWorkspace.open(controlCenterCompactMode ? "紧凑布局 · 壁纸画布" : "标准布局 · 壁纸画布",
                    this::undoControlGridChange);
            controlCenterWallpaperWorkspace.setBackgroundStrength(controlCenterSettings().backgroundBlur);
        }), matchWrap());
        LinearLayout tools = new LinearLayout(this);
        android.widget.Button addItem = controlCenterButton(getString(R.string.editor_add), false,
                view -> showAddControlItemDialog());
        addItem.setContentDescription(getString(R.string.editor_add_description));
        addItem.setTooltipText(getString(R.string.editor_add_description));
        addControlCenterAction(tools, addItem, false);
        controlCenterUndoButton = controlCenterButton(getString(R.string.editor_undo), false,
                view -> undoControlGridChange());
        addControlCenterAction(tools, controlCenterUndoButton, true);
        controlCenterRedoButton = controlCenterButton(getString(R.string.editor_redo), false,
                view -> redoControlGridChange());
        addControlCenterAction(tools, controlCenterRedoButton, true);
        addControlCenterAction(tools, controlCenterButton("更多 ···", false,
                this::showControlCenterMoreMenu), true);
        controlCenterGridPanel.addView(tools, matchWrap());
        page.addView(controlCenterGridPanel, controlCenterSectionParams());

        LinearLayout reference = controlCenterCard();
        controlCenterActualPanel = reference;
        controlCenterReferenceToggle = labelView("真实布局参考", 16f, true);
        controlCenterReferenceToggle.setMinimumHeight(dp(48));
        controlCenterReferenceToggle.setGravity(android.view.Gravity.CENTER_VERTICAL);
        controlCenterReferenceToggle.setEnabled(false);
        controlCenterReferenceToggle.setOnClickListener(view -> {
            controlCenterReferenceExpanded = !controlCenterReferenceExpanded;
            updateControlCenterReferenceVisibility();
        });
        reference.addView(controlCenterReferenceToggle, matchWrap());
        android.widget.TextView referenceHint = labelView("拉取手机当前布局作为参考，也可导入画布继续编辑。", 12f, false);
        referenceHint.setTextColor(textSecondaryColor());
        referenceHint.setPadding(0, dp(6), 0, dp(12));
        reference.addView(referenceHint, matchWrap());
        controlCenterActualPreview = new ControlCenterActualEditor(this, new ControlCenterActualEditor.Listener() {
            @Override public void onTileClicked(String spec) {}
            @Override public void onTileMoved(String source, String destination) {}
            @Override public void onTileMovedToEnd(String source) {}
            @Override public void onTileMovedToPosition(String source, float x, float y) {}
        });
        controlCenterActualPreview.setBackground(rounded(android.graphics.Color.rgb(15, 16, 18), dp(12)));
        controlCenterActualPreview.setEditingEnabled(false);
        controlCenterActualPreview.setVisibility(View.GONE);
        reference.addView(controlCenterActualPreview, matchWrap());
        controlCenterActualSummary = labelView("尚未拉取布局", 12f, false);
        controlCenterActualSummary.setTextColor(textSecondaryColor());
        controlCenterActualSummary.setPadding(0, dp(8), 0, dp(12));
        reference.addView(controlCenterActualSummary, matchWrap());
        LinearLayout referenceActions = new LinearLayout(this);
        addControlCenterAction(referenceActions, controlCenterButton("拉取布局", false,
                view -> requestControlCenterPreview()), false);
        addControlCenterAction(referenceActions, controlCenterButton("导入画布", false,
                view -> importActualControlCenterDraft()), true);
        reference.addView(referenceActions, matchWrap());
        page.addView(reference, controlCenterSectionParams());

        controlCenterPreview = null;
        controlCenterActivePreview = null;
        controlCenterAvailablePreview = null;
        controlCenterPublishHint = labelView("", 12f, false);
        controlCenterPublishHint.setTextColor(textSecondaryColor());
        controlCenterPublishHint.setPadding(dp(4), dp(6), dp(4), dp(10));
        page.addView(controlCenterPublishHint, matchWrap());
        controlCenterDraftHint = labelView("", 12f, false);
        controlCenterDraftHint.setTextColor(textSecondaryColor());
        page.addView(controlCenterDraftHint, matchWrap());
        controlCenterDraftRetryButton = controlCenterButton(getString(R.string.editor_draft_retry), false,
                view -> editorPage.retryDraft());
        page.addView(controlCenterDraftRetryButton, controlCenterSectionParams());
        controlCenterPushButton = controlCenterButton("推送修改", true,
                view -> pushControlCenterDraft());
        controlCenterPushButton.setContentDescription("推送控制中心修改到手机");
        page.addView(controlCenterPushButton, controlCenterSectionParams());
        loadLatestControlCenterPreview();
    }

    private LinearLayout controlCenterCard() {
        LinearLayout result = card();
        result.setPadding(dp(16), dp(16), dp(16), dp(16));
        return result;
    }

    private LinearLayout.LayoutParams controlCenterSectionParams() {
        LinearLayout.LayoutParams params = matchWrap();
        params.topMargin = dp(10);
        params.bottomMargin = dp(4);
        return params;
    }

    private android.widget.Button controlCenterButton(String text, boolean primary,
            View.OnClickListener action) {
        android.widget.Button button = new android.widget.Button(this);
        button.setText(text);
        button.setAllCaps(false);
        button.setTextSize(14f);
        button.setMinHeight(0);
        button.setMinimumHeight(dp(48));
        button.setMinWidth(0);
        button.setMinimumWidth(0);
        button.setPadding(dp(8), dp(8), dp(8), dp(8));
        button.setMaxLines(2);
        button.setGravity(android.view.Gravity.CENTER);
        styleControlCenterButton(button, primary);
        button.setOnClickListener(action);
        return button;
    }

    private void styleControlCenterButton(android.widget.Button button, boolean primary) {
        button.setBackgroundTintList(null);
        int background = primary ? accentColor() : inputColor();
        int foreground = primary ? (isNightMode() ? android.graphics.Color.rgb(16, 30, 48)
                : android.graphics.Color.WHITE) : textPrimaryColor();
        android.graphics.drawable.GradientDrawable fill = rounded(background, dp(12));
        android.graphics.drawable.GradientDrawable mask = rounded(android.graphics.Color.WHITE, dp(12));
        button.setBackground(new android.graphics.drawable.RippleDrawable(
                android.content.res.ColorStateList.valueOf(android.graphics.Color.argb(40, 127, 127, 127)),
                fill, mask));
        button.setTextColor(foreground);
        button.setElevation(0f);
        button.setStateListAnimator(null);
    }

    private void addControlCenterAction(LinearLayout row, View action, boolean spaced) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, -2, 1f);
        if (spaced) params.leftMargin = dp(8);
        row.addView(action, params);
    }

    private void showControlCenterMoreMenu(View anchor) {
        String[] labels = {"全局磁贴开关", "清空当前画布", "恢复原生布局", getString(R.string.editor_layout_presets)};
        int[] icons = {R.drawable.cc_preview_settings, android.R.drawable.ic_menu_delete,
                android.R.drawable.ic_menu_revert, android.R.drawable.ic_menu_save};
        showControlChoiceSheet("更多操作", labels, index ->
                ControlCenterIcons.load(this, icons[index], textPrimaryColor()), index -> {
            switch (index) {
                case 0: showControlCenterTilesDialog(); break;
                case 1: clearControlGridMode(); break;
                case 2: confirmResetControlCenter(); break;
                case 3: presetController().show(); break;
                default: break;
            }
        });
    }

    private void selectControlCenterMode(boolean compact) {
        if (configRestoring) return;
        if (controlCenterGridEditor != null) controlCenterGridEditor.finishEditing();
        editorSession.selectMode(compact);
        syncEditorState();
        persistEditorSession();
        updateSettingsSummaries();
    }

    /* JADX INFO: Access modifiers changed from: private */
    public com.xtjm.fusionstatusbar.ControlCenterConfig controlCenterSettings() {
        if (this.controlCenterDraft == null && this.config != null) {
            this.controlCenterDraft = this.config.controlCenter;
        }
        if (this.controlCenterDraft != null) {
            return this.controlCenterDraft;
        }
        return this.config == null ? com.xtjm.fusionstatusbar.ControlCenterConfig.defaults() : this.config.controlCenter;
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void updateControlCenterDraft(com.xtjm.fusionstatusbar.ControlCenterConfig next) {
        if (next == null || this.config == null || configRestoring) {
            return;
        }
        editorSession.edit(next);
        controlCenterSaveFailed = false;
        syncEditorState();
        persistEditorSession();
        updateSettingsSummaries();
    }

    private boolean hasPendingControlCenterDraft() {
        return editorSession != null && editorSession.isDirty();
    }

    private void pushControlCenterDraft() {
        if (controlCenterSaving || configRestoring) return;
        if (!hasPendingControlCenterDraft()) {
            if (editorPage.canRetryApplication()) {
                commitControlCenterDraft(config.controlCenter, "");
                return;
            }
            android.widget.Toast.makeText(this, "没有待推送的控制中心修改", Toast.LENGTH_SHORT).show();
            return;
        }
        com.xtjm.fusionstatusbar.ControlCenterConfig draft = controlCenterSettings();
        com.xtjm.fusionstatusbar.ControlCenterLayoutPlan plan = com.xtjm.fusionstatusbar.ControlCenterLayoutPlan.decode(draft.layoutPlan);
        if (plan != null && (!plan.regular.validate().isEmpty() || !plan.compact.validate().isEmpty())) {
            android.widget.Toast.makeText(this, "布局存在重叠、越界或重复项目，无法推送", Toast.LENGTH_LONG).show();
        } else {
            if (plan != null) {
                plan = plan.forPublication(this.controlCenterCompactMode);
                draft = draft.withLayoutPlan(plan.encode());
                android.util.Log.i("FusionStatusBar", "control-center publish canvas="
                        + plan.runtimeLayout + " columns="
                        + plan.runtimeMode(false).columns + " items="
                        + plan.runtimeMode(false).items.size());
            }
            commitControlCenterDraft(draft, "布局已保存，请拉取系统实际布局核对");
        }
    }

    private void commitControlCenterDraft(com.xtjm.fusionstatusbar.ControlCenterConfig next, java.lang.String message) {
        ControlCenterConfig submittedDraft = editorSession.draft();
        saveHandler.removeCallbacks(saveSettings);
        pendingSettingsSave = false;
        controlCenterSaving = true;
        controlCenterSaveFailed = false;
        FusionConfig submitted = config.withControlCenter(next);
        updateSettingsSummaries();
        configSession.write(submitted, result -> {
            controlCenterSaving = false;
            configWriteInFlight = false;
            controlCenterSaveFailed = !result.success;
            if (result.success) {
                config = config.withControlCenter(result.config.controlCenter).withRevision(result.revision);
                controlCenterSavedRevision = result.revision;
                editorPage.published(result.revision);
                editorSession.saved(submittedDraft, result.config.controlCenter);
                Toast.makeText(this, R.string.editor_saved_waiting, Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, R.string.editor_save_failed, Toast.LENGTH_LONG).show();
            }
            syncEditorState();
            persistEditorSession();
            updateSettingsSummaries();
            if (pendingSettingsSave) flushSettingsSave();
        });
    }

    ControlCenterEditorSession editorSession() { return editorSession; }
    boolean settingsLoaded() { return settingsLoaded; }
    boolean savingControlCenter() { return controlCenterSaving; }

    private void syncEditorState() {
        controlCenterDraft = editorSession.draft();
        controlCenterDraftDirty = editorSession.isDirty();
        controlCenterCompactMode = editorSession.compact();
    }

    private void persistEditorSession() {
        if (editorPage != null) editorPage.persist();
    }

    private ConfigurationBackupController backupController() {
        if (backupController == null) backupController = new ConfigurationBackupController(this,
                new ConfigurationBackupController.Host() {
                    @Override public boolean hasDraft() { return hasPendingControlCenterDraft(); }
                    @Override public boolean beginRestore() {
                        if (!settingsLoaded || controlCenterSaving || configRestoring || configWriteInFlight || pendingSettingsSave) {
                            flushSettingsSave();
                            Toast.makeText(MainActivity.this, R.string.editor_backup_busy, Toast.LENGTH_SHORT).show();
                            return false;
                        }
                        configRestoring = true;
                        if (controlCenterGridEditor != null) controlCenterGridEditor.finishEditing();
                        if (settingsDialogs != null) settingsDialogs.close();
                        persistEditorSession();
                        setChildrenEnabled((android.view.ViewGroup) findViewById(android.R.id.content), false);
                        return true;
                    }

                    @Override public void finishRestore(FusionConfigStore.WriteResult result) {
                        configRestoring = false;
                        setChildrenEnabled((android.view.ViewGroup) findViewById(android.R.id.content), true);
                        if (result.success) {
                            config = result.config;
                            controlCenterSavedRevision = result.revision;
                            editorPage.replacePublished(config.controlCenter, result.revision);
                            editorSession = editorPage.session();
                            syncEditorState();
                            controlCenterSaveFailed = false;
                            if (editorSession.isDirty()) Toast.makeText(MainActivity.this,
                                    R.string.editor_backup_draft_kept, Toast.LENGTH_LONG).show();
                            preview.setPreviewConfig(config);
                            applyControls();
                        } else updateSettingsSummaries();
                    }
                });
        return backupController;
    }

    private LayoutPresetController presetController() {
        if (presetController == null) presetController = new LayoutPresetController(this,
                new LayoutPresetController.Host() {
                    @Override public ControlCenterConfig draft() { return editorSession.draft(); }
                    @Override public boolean compact() { return editorSession.compact(); }
                    @Override public boolean canEdit() {
                        return settingsLoaded && editorSession != null && !controlCenterSaving
                                && !configRestoring && !configWriteInFlight && !pendingSettingsSave;
                    }
                    @Override public void loadPreset(ControlCenterConfig next, boolean compact) {
                        if (!canEdit()) return;
                        if (controlCenterGridEditor != null) controlCenterGridEditor.finishEditing();
                        editorSession.loadPreset(next, compact);
                        controlCenterSaveFailed = false;
                        renderEditorSession();
                    }
                }, (title, labels, selected) -> showControlChoiceSheet(title, labels, null, selected));
        return presetController;
    }

    private void requestControlCenterPreview() {
        if (this.config == null) {
            android.widget.Toast.makeText(this, "配置尚未加载，请重试", Toast.LENGTH_SHORT).show();
            return;
        }
        saveHandler.removeCallbacks(controlCenterPreviewPoll);
        if (controlCenterActualPreview != null) controlCenterActualPreview.clearPreview();
        previewLoader.clear();
        previewLoader.request(this::onControlCenterPreviewRequested);
    }

    private void onControlCenterPreviewRequested(long request) {
        if (request <= 0) {
            android.widget.Toast.makeText(this, "无法向 SystemUI 请求实际预览", Toast.LENGTH_SHORT).show();
            return;
        }
        this.controlCenterPreviewRequest = request;
        this.controlCenterPreviewPolls = 0;
        this.lastControlCenterPreviewFailure = -1L;
        if (this.controlCenterActualPreview != null) {
            this.controlCenterActualPreview.clearPreview();
            previewLoader.clear();
            controlCenterReferenceExpanded = false;
            updateControlCenterReferenceVisibility();
        }
        if (this.controlCenterActualSummary != null) {
            this.controlCenterActualSummary.setText("正在拉取，请展开手机控制中心一次后返回");
        }
        this.saveHandler.removeCallbacks(this.controlCenterPreviewPoll);
        this.saveHandler.post(this.controlCenterPreviewPoll);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void pollControlCenterPreview() {
        loadLatestControlCenterPreview();
        int i = this.controlCenterPreviewPolls;
        this.controlCenterPreviewPolls = i + 1;
        if (i >= 120) {
            if (this.controlCenterActualSummary != null) {
                this.controlCenterActualSummary.setText("未检测到可读取的布局，请展开控制中心后重新拉取");
                return;
            }
            return;
        }
        this.saveHandler.postDelayed(this.controlCenterPreviewPoll, 500L);
    }

    private boolean loadLatestControlCenterPreview() {
        if (config == null || controlCenterActualPreview == null) return false;
        int width = controlCenterActualPreview.getWidth();
        if (width <= 0) width = getResources().getDisplayMetrics().widthPixels;
        previewLoader.load(controlCenterPreviewRequest, width, new ControlCenterPreviewLoader.Listener() {
            @Override public void loaded(long generation, android.graphics.Bitmap bitmap, String layout) {
                if (isDestroyed() || isFinishing()) return;
                controlCenterPreviewRequest = generation;
                saveHandler.removeCallbacks(controlCenterPreviewPoll);
                controlCenterActualPreview.setPreview(bitmap, layout);
                controlCenterActualPanel.setVisibility(View.VISIBLE);
                updateControlCenterReferenceVisibility();
                controlCenterCapturedSpecs.clear();
                controlCenterCapturedSpecs.addAll(controlCenterActualPreview.capturedSpecs());
                controlCenterNativeCatalog.clear();
                for (String spec : previewLoader.nativeTileCatalog().split(",")) {
                    String normalized = ControlCenterConfig.canonicalSpec(
                            spec.trim().toLowerCase(java.util.Locale.ROOT));
                    if (!normalized.isEmpty() && !controlCenterNativeCatalog.contains(normalized))
                        controlCenterNativeCatalog.add(normalized);
                }
                refreshControlCenterPreview();
                controlCenterActualSummary.setText(R.string.editor_preview_ready);
            }
            @Override public void failed(long generation, Throwable error) {
                reportControlCenterPreviewFailure(generation, getString(R.string.editor_preview_failed), error);
            }
            @Override public void waiting(long generation) {
                if (controlCenterPreviewRequest != generation) {
                    controlCenterPreviewRequest = generation;
                    controlCenterPreviewPolls = 0;
                    saveHandler.removeCallbacks(controlCenterPreviewPoll);
                    if (resumed) saveHandler.postDelayed(controlCenterPreviewPoll, 500);
                }
            }
        });
        return false;
    }

    private void updateControlCenterReferenceVisibility() {
        boolean available = controlCenterActualPreview != null
                && controlCenterActualPreview.hasTileLayout();
        if (controlCenterReferenceToggle != null) {
            controlCenterReferenceToggle.setEnabled(available);
            controlCenterReferenceToggle.setText(available
                    ? "真实布局参考  " + (controlCenterReferenceExpanded ? "∧" : "∨")
                    : "真实布局参考");
            controlCenterReferenceToggle.setContentDescription(available
                    ? (controlCenterReferenceExpanded ? "收起真实布局参考图" : "展开真实布局参考图")
                    : "真实布局参考，请先拉取布局");
        }
        if (controlCenterActualPreview != null) {
            controlCenterActualPreview.setVisibility(available && controlCenterReferenceExpanded
                    ? View.VISIBLE : View.GONE);
        }
    }

    private void reportControlCenterPreviewFailure(long generation, java.lang.String message, java.lang.Throwable error) {
        if (this.controlCenterActualSummary != null) {
            this.controlCenterActualSummary.setText(message);
        }
        if (this.lastControlCenterPreviewFailure != generation) {
            this.lastControlCenterPreviewFailure = generation;
            android.util.Log.w("FusionStatusBar", "control-center preview load failed request=" + generation + " reason=" + message, error);
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void showControlCenterSettingsDialog() {
        showSettingsDialog("布局与外观", new com.xtjm.fusionstatusbar.MainActivity.DialogBuilder() { // from class: com.xtjm.fusionstatusbar.MainActivity.33
            @Override // com.xtjm.fusionstatusbar.MainActivity.DialogBuilder
            public void build(android.widget.LinearLayout content) {
                com.xtjm.fusionstatusbar.ControlCenterConfig settings = com.xtjm.fusionstatusbar.MainActivity.this.controlCenterSettings();
                addSectionLabel(content, "当前画布");
                final java.lang.String modeLabel = controlCenterCompactMode ? "紧凑布局" : "标准布局";
                com.xtjm.fusionstatusbar.MainActivity.this.addSlider(content, "画布列数", "仅调整当前" + modeLabel + "的网格列数", 3, 6, currentControlGridPlan().mode(controlCenterCompactMode).columns, " 列", new com.xtjm.fusionstatusbar.MainActivity.IntegerUpdate() { // from class: com.xtjm.fusionstatusbar.MainActivity.33.2
                    @Override // com.xtjm.fusionstatusbar.MainActivity.IntegerUpdate
                    public void apply(int value) {
                        ControlCenterLayoutPlan plan = currentControlGridPlan();
                        applyControlGridPlan(plan.withMode(controlCenterCompactMode,
                                plan.mode(controlCenterCompactMode).withColumns(value)));
                    }
                });
                addSectionLabel(content, "全局外观");
                addHint(content, "以下设置同时应用于标准布局和紧凑布局。项目尺寸、形状和独立圆角请在画布中点击项目调整。");
                com.xtjm.fusionstatusbar.MainActivity.this.addSlider(content, "内容缩放", "缩放项目内部图标和内容；项目宽高由画布网格决定", 70, 130, settings.tileScale, "%", new com.xtjm.fusionstatusbar.MainActivity.IntegerUpdate() { // from class: com.xtjm.fusionstatusbar.MainActivity.33.3
                    @Override // com.xtjm.fusionstatusbar.MainActivity.IntegerUpdate
                    public void apply(int value) {
                        com.xtjm.fusionstatusbar.MainActivity.this.updateControlCenterDraft(com.xtjm.fusionstatusbar.MainActivity.this.controlCenterSettings().withTileScale(value));
                    }
                });
                com.xtjm.fusionstatusbar.MainActivity.this.addSlider(content, "默认圆角", "用于矩形项目；单项目设置的独立圆角优先，圆角为 0 时使用此默认值", 0, 32, settings.cornerRadius, " dp", new com.xtjm.fusionstatusbar.MainActivity.IntegerUpdate() { // from class: com.xtjm.fusionstatusbar.MainActivity.33.4
                    @Override // com.xtjm.fusionstatusbar.MainActivity.IntegerUpdate
                    public void apply(int value) {
                        com.xtjm.fusionstatusbar.MainActivity.this.updateControlCenterDraft(com.xtjm.fusionstatusbar.MainActivity.this.controlCenterSettings().withCornerRadius(value));
                    }
                });
                com.xtjm.fusionstatusbar.MainActivity.this.addSlider(content, "项目间距", "调整画布中相邻项目之间的留白", 0, 24, settings.spacing, " dp", new com.xtjm.fusionstatusbar.MainActivity.IntegerUpdate() { // from class: com.xtjm.fusionstatusbar.MainActivity.33.5
                    @Override // com.xtjm.fusionstatusbar.MainActivity.IntegerUpdate
                    public void apply(int value) {
                        com.xtjm.fusionstatusbar.MainActivity.this.updateControlCenterDraft(com.xtjm.fusionstatusbar.MainActivity.this.controlCenterSettings().withSpacing(value));
                    }
                });
                addBlurSlider(content, "背景模糊", settings.backgroundBlur, value -> {
                    ControlCenterConfig current = controlCenterSettings();
                    updateControlCenterDraft(current.withBlur(value, current.cardBlur, current.tileBlur));
                });
                addBlurSlider(content, "卡片模糊", settings.cardBlur, value -> {
                    ControlCenterConfig current = controlCenterSettings();
                    updateControlCenterDraft(current.withBlur(current.backgroundBlur, value, current.tileBlur));
                });
                addBlurSlider(content, "磁贴模糊", settings.tileBlur, value -> {
                    ControlCenterConfig current = controlCenterSettings();
                    updateControlCenterDraft(current.withBlur(current.backgroundBlur, current.cardBlur, value));
                });
            }
        });
    }

    private void confirmResetControlCenter() {
        new android.app.AlertDialog.Builder(this).setTitle("恢复原生布局").setMessage("清除本模块的控制中心布局和外观修改，保留当前磁贴选择与顺序。").setNegativeButton("取消", (android.content.DialogInterface.OnClickListener) null).setPositiveButton("恢复", new android.content.DialogInterface.OnClickListener() { // from class: com.xtjm.fusionstatusbar.MainActivity$$ExternalSyntheticLambda5
            @Override // android.content.DialogInterface.OnClickListener
            public final void onClick(android.content.DialogInterface dialogInterface, int i) {
                MainActivity.this.lambda$confirmResetControlCenter$10(dialogInterface, i);
            }
        }).show();
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$confirmResetControlCenter$10(android.content.DialogInterface dialog, int which) {
        restoreControlCenter(false);
    }

    private void restoreControlCenter(boolean resetSystemTiles) {
        if (this.controlCenterActualPreview != null) {
            this.controlCenterActualPreview.clearPreview();
            previewLoader.clear();
        }
        this.controlCenterCapturedSpecs.clear();
        this.controlCenterNativeCatalog.clear();
        previewLoader.clearRemote();
        com.xtjm.fusionstatusbar.ControlCenterConfig cleared = controlCenterSettings().clearCustomization();
        updateControlCenterDraft(cleared);
        commitControlCenterDraft(cleared, "已提交恢复请求，请核对系统实际布局");
        if (this.controlCenterActualSummary != null) {
            this.controlCenterActualSummary.setText("恢复请求已保存；展开控制中心后拉取实际布局核对");
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void showControlCenterTilesDialog() {
        if (controlCenterAppTilesLoading) {
            controlCenterAppTilesReady = this::showControlCenterTilesDialog;
            Toast.makeText(this, "正在读取可用磁贴", Toast.LENGTH_SHORT).show();
            return;
        }
        java.util.ArrayList<String> specs = knownControlGridSpecs();
        specs.sort(java.util.Comparator.comparingInt(spec -> spec.startsWith("custom(") ? 1 : 0));
        showSettingsDialog("全局磁贴开关", content -> {
            addHint(content, "管理所有已发现的系统与应用快捷磁贴，同时影响标准、紧凑两种布局。关闭后不再显示，也不会出现在添加列表；已关闭的项目仍保留在此，可随时重新启用。仅修改编辑草稿，推送后才应用到控制中心；不会直接切换 Wi-Fi、蓝牙等功能。");
            String section = "";
            for (String spec : specs) {
                String nextSection = spec.startsWith("custom(") ? "应用快捷磁贴" : "系统与其他磁贴";
                if (!section.equals(nextSection)) { addSectionLabel(content, nextSection); section = nextSection; }
                String title = tileLabel(spec);
                String hint = spec.startsWith("custom(") && ControlCenterAppTiles.find(controlCenterAppTiles, spec) == null
                        ? "当前未发现此服务，保留设置供恢复" : "适用于标准与紧凑布局";
                android.widget.Switch toggle = addSwitch(content, title, hint,
                        !controlCenterSettings().isHidden(spec), enabled ->
                                updateControlCenterDraft(updateHiddenTile(controlCenterSettings(), spec, !enabled)));
                toggle.setContentDescription("全局可用：" + title);
                LinearLayout row = (LinearLayout) toggle.getParent();
                row.setPadding(0, dp(8), 0, dp(8));
                LinearLayout texts = (LinearLayout) row.getChildAt(0);
                texts.getLayoutParams().height = android.view.ViewGroup.LayoutParams.WRAP_CONTENT;
                android.widget.TextView label = (android.widget.TextView) texts.getChildAt(0);
                label.setMaxLines(2);
                label.setEllipsize(android.text.TextUtils.TruncateAt.END);
                label.setCompoundDrawablePadding(dp(10));
                label.setCompoundDrawablesRelative(addControlItemIcon(spec), null, null, null);
            }
        });
    }

    /* JADX INFO: Access modifiers changed from: private */
    public com.xtjm.fusionstatusbar.ControlCenterConfig updateHiddenTile(com.xtjm.fusionstatusbar.ControlCenterConfig settings, java.lang.String spec, boolean hidden) {
        spec = ControlCenterConfig.canonicalSpec(spec);
        java.util.LinkedHashSet<java.lang.String> values = new java.util.LinkedHashSet<>();
        if (!settings.hidden.isEmpty()) {
            values.addAll(java.util.Arrays.asList(settings.hidden.split(",")));
        }
        if (hidden) {
            values.add(spec);
        } else {
            values.remove(spec);
        }
        return settings.withHidden(java.lang.String.join(",", values));
    }

    private void refreshControlCenterAppTiles() {
        if (controlCenterAppTilesLoading) return;
        controlCenterAppTilesLoading = true;
        android.content.Context app = getApplicationContext();
        new Thread(() -> {
            java.util.List<ControlCenterAppTiles.Entry> tiles;
            try { tiles = ControlCenterAppTiles.query(app, true); }
            catch (RuntimeException error) {
                android.util.Log.w("FusionStatusBar", "Application tile discovery failed", error);
                tiles = java.util.Collections.emptyList();
            }
            java.util.List<ControlCenterAppTiles.Entry> result = tiles;
            saveHandler.post(() -> {
                controlCenterAppTilesLoading = false;
                if (isFinishing() || isDestroyed()) return;
                controlCenterAppTiles = result;
                refreshControlCenterGridEditor();
                Runnable ready = controlCenterAppTilesReady;
                controlCenterAppTilesReady = null;
                if (ready != null) ready.run();
            });
        }, "fusion-app-tiles").start();
    }

    private void addBlurSlider(LinearLayout content, String title, int value, IntegerUpdate update) {
        LinearLayout row = (LinearLayout) addSlider(content, title, "", 0, 100, value, "%", update);
        row.getChildAt(1).setVisibility(View.GONE);
        LinearLayout endpoints = new LinearLayout(this);
        android.widget.TextView transparent = labelView("透明", 12f, false);
        android.widget.TextView blurred = labelView("模糊", 12f, false);
        transparent.setTextColor(textSecondaryColor()); blurred.setTextColor(textSecondaryColor());
        blurred.setGravity(android.view.Gravity.END);
        endpoints.addView(transparent, new LinearLayout.LayoutParams(0, -2, 1));
        endpoints.addView(blurred, new LinearLayout.LayoutParams(0, -2, 1));
        row.addView(endpoints, new LinearLayout.LayoutParams(-1, -2));
    }

    private ControlCenterCapturedStyle controlTileStyle(String spec) {
        ControlCenterCapturedStyle captured = controlCenterActualPreview == null ? null : controlCenterActualPreview.capturedStyle(spec);
        ControlCenterAppTiles.Entry entry = ControlCenterAppTiles.find(controlCenterAppTiles, spec);
        if (captured != null) return entry == null ? captured : captured.withFallbackIcon(entry.icon);
        return entry == null ? null : new ControlCenterCapturedStyle(ControlCenterLayoutPlan.Shape.CIRCLE, 0,
                -1, entry.label, "未读取状态", -1, entry.icon);
    }

    private java.util.ArrayList<java.lang.String> knownControlGridSpecs() {
        java.util.LinkedHashSet<java.lang.String> specs = new java.util.LinkedHashSet<>(editableControlCenterOrder());
        for (String spec : CONTROL_TILE_SPECS) specs.add(ControlCenterConfig.canonicalSpec(
                spec.toLowerCase(java.util.Locale.ROOT)));
        for (String spec : this.controlCenterCapturedSpecs) specs.add(ControlCenterConfig.canonicalSpec(
                spec.toLowerCase(java.util.Locale.ROOT)));
        specs.addAll(this.controlCenterNativeCatalog);
        if (this.controlCenterActualPreview != null) {
            for (String spec : this.controlCenterActualPreview.capturedSpecs())
                specs.add(ControlCenterConfig.canonicalSpec(spec.toLowerCase(java.util.Locale.ROOT)));
            specs.addAll(this.controlCenterActualPreview.capturedComponents());
        }
        for (ControlCenterAppTiles.Entry tile : controlCenterAppTiles) {
            if (specs.stream().noneMatch(spec -> ControlCenterAppTiles.find(java.util.Collections.singletonList(tile), spec) != null)) specs.add(tile.key);
        }
        ControlCenterConfig settings = controlCenterSettings();
        specs.addAll(settings.orderedSpecs());
        specs.addAll(java.util.Arrays.asList(settings.hidden.split(",")));
        ControlCenterLayoutPlan stored = ControlCenterLayoutPlan.decode(settings.layoutPlan);
        if (stored != null) {
            for (ControlCenterLayoutPlan.Mode mode : new ControlCenterLayoutPlan.Mode[] {stored.regular, stored.compact}) {
                for (ControlCenterLayoutPlan.Item item : mode.items) specs.addAll(item.specs());
            }
        }
        java.util.LinkedHashSet<String> canonical = new java.util.LinkedHashSet<>();
        for (String spec : specs) {
            String key = ControlCenterConfig.canonicalSpec(spec);
            if (!key.isEmpty() && !ControlCenterComponentSpec.isSpecial(key)) canonical.add(key);
        }
        return new java.util.ArrayList<>(canonical);
    }

    private java.util.ArrayList<java.lang.String> availableControlGridSpecs() {
        ControlCenterConfig settings = controlCenterSettings();
        java.util.ArrayList<String> specs = knownControlGridSpecs();
        specs.removeIf(settings::isHidden);
        for (String spec : new String[] {
                ControlCenterComponentSpec.MEDIA,
                ControlCenterComponentSpec.BRIGHTNESS,
                ControlCenterComponentSpec.VOLUME,
                ControlCenterComponentSpec.DEVICE_CENTER,
                ControlCenterComponentSpec.DEVICE_CONTROLS,
                ControlCenterComponentSpec.QS_CARD}) {
            if (!settings.isHidden(spec) && !specs.contains(spec)) specs.add(spec);
        }
        return new java.util.ArrayList<>(specs);
    }

    private void addTileToBlankGrid(java.lang.String spec) {
        com.xtjm.fusionstatusbar.ControlCenterLayoutPlan.Item item;
        if (spec == null || spec.isEmpty()) {
            return;
        }
        controlCenterSettings();
        com.xtjm.fusionstatusbar.ControlCenterLayoutPlan plan = currentControlGridPlan();
        com.xtjm.fusionstatusbar.ControlCenterLayoutPlan.Mode mode = plan.mode(this.controlCenterCompactMode);
        for (com.xtjm.fusionstatusbar.ControlCenterLayoutPlan.Item item2 : mode.items) {
            if (item2.containsSpec(spec)) {
                return;
            }
        }
        if (com.xtjm.fusionstatusbar.ControlCenterComponentSpec.isSpecial(spec)) {
            item = com.xtjm.fusionstatusbar.ControlCenterLayoutPlan.Item.component(spec, -1, -1, java.lang.Math.min(mode.columns, com.xtjm.fusionstatusbar.ControlCenterComponentSpec.defaultSpan(spec)), com.xtjm.fusionstatusbar.ControlCenterComponentSpec.defaultRows(spec));
        } else {
            item = com.xtjm.fusionstatusbar.ControlCenterLayoutPlan.Item.tile(spec, -1, -1, 1, 1);
        }
        applyControlGridPlan(plan.withItem(this.controlCenterCompactMode, item));
    }

    private com.xtjm.fusionstatusbar.ControlCenterLayoutPlan currentControlGridPlan() {
        return com.xtjm.fusionstatusbar.ControlCenterLayoutPlan.fromConfig(
                controlCenterSettings(), availableControlGridSpecs());
    }

    private void refreshControlCenterGridEditor() {
        if (this.controlCenterGridEditor == null) {
            return;
        }
        if (this.controlCenterRegularModeButton != null) {
            styleControlCenterButton(this.controlCenterRegularModeButton, !this.controlCenterCompactMode);
            this.controlCenterRegularModeButton.setSelected(!this.controlCenterCompactMode);
        }
        if (this.controlCenterCompactModeButton != null) {
            styleControlCenterButton(this.controlCenterCompactModeButton, this.controlCenterCompactMode);
            this.controlCenterCompactModeButton.setSelected(this.controlCenterCompactMode);
        }
        com.xtjm.fusionstatusbar.ControlCenterConfig settings = controlCenterSettings();
        this.controlCenterGridEditor.setGeometry(settings.spacing, settings.cornerRadius);
        this.controlCenterGridEditor.setMaterialStrength(settings.cardBlur, settings.tileBlur);
        if (controlCenterWallpaperWorkspace != null) controlCenterWallpaperWorkspace.setBackgroundStrength(settings.backgroundBlur);
        com.xtjm.fusionstatusbar.ControlCenterLayoutPlan.Resolved resolved = currentControlGridPlan().resolve(this.controlCenterCompactMode, availableControlGridSpecs());
        java.util.ArrayList<com.xtjm.fusionstatusbar.ControlCenterGridEditor.Cell> cells = new java.util.ArrayList<>();
        for (com.xtjm.fusionstatusbar.ControlCenterLayoutPlan.Item item : resolved.items) {
        if (item.type == ControlCenterLayoutPlan.Type.GROUP) {
            java.util.Map<String, ControlCenterCapturedStyle> styles = new java.util.HashMap<>();
            java.util.Map<String, String> labels = new java.util.HashMap<>();
            for (String spec : item.specs()) {
                labels.put(spec, tileLabel(spec));
                styles.put(spec, controlTileStyle(spec));
            }
            cells.add(new ControlCenterGridEditor.Cell(item.id, "", "", "组合卡片", null, item.x, item.y,
                    item.width, item.height, false, null, null, item.shape, item.cornerRadius,
                    item.locked, item.hidden, item.zIndex, settings.tileScale).withGroup(item, styles, labels));
            continue;
        }
        java.lang.String first = com.xtjm.fusionstatusbar.ControlCenterComponentSpec.isSpecial(item.firstSpec)
                ? com.xtjm.fusionstatusbar.ControlCenterComponentSpec.label(item.firstSpec)
                : tileLabel(item.firstSpec);
        java.lang.String second = item.type == com.xtjm.fusionstatusbar.ControlCenterLayoutPlan.Type.PAIR ? tileLabel(item.secondSpec) : null;
        // Captures remain in the reference panel; editable cards must re-render their shape.
        cells.add(new com.xtjm.fusionstatusbar.ControlCenterGridEditor.Cell(item.id,
                item.firstSpec, item.secondSpec, first, second,
                item.x, item.y, item.width, item.height,
                item.direction == com.xtjm.fusionstatusbar.ControlCenterLayoutPlan.Direction.HORIZONTAL,
                null, null, item.shape, item.cornerRadius,
                item.locked, item.hidden, item.zIndex, settings.tileScale).withCapturedStyles(
                        controlTileStyle(item.firstSpec), controlTileStyle(item.secondSpec)));
        }
        cells.sort(java.util.Comparator.comparingInt(
                (com.xtjm.fusionstatusbar.ControlCenterGridEditor.Cell cell) -> cell.zIndex));
        String selection = editorSession == null ? "" : editorSession.selection();
        this.controlCenterGridEditor.setCells(resolved.mode.columns, cells);
        this.controlCenterGridEditor.restoreSelection(selection);
    }

    private void syncGridFromActualLayout() {
        if (this.controlCenterActualPreview == null || !this.controlCenterActualPreview.hasTileLayout()) {
            return;
        }
        java.util.ArrayList<java.lang.String> captured = new java.util.ArrayList<>(this.controlCenterActualPreview.capturedSpecs());
        if (captured.isEmpty()) {
            return;
        }
        com.xtjm.fusionstatusbar.ControlCenterConfig settings = controlCenterSettings();
        com.xtjm.fusionstatusbar.ControlCenterLayoutPlan plan = com.xtjm.fusionstatusbar.ControlCenterLayoutPlan.fromConfig(settings, captured);
        updateControlCenterDraft(settings.withLayoutPlan(plan.encode()));
    }

    private void importActualControlCenterDraft() {
        if (this.controlCenterActualPreview == null
                || !this.controlCenterActualPreview.hasTileLayout()) {
            Toast.makeText(this, "请先拉取真实布局", Toast.LENGTH_SHORT).show();
            return;
        }
        java.util.ArrayList<String> captured = new java.util.ArrayList<>(
                this.controlCenterActualPreview.capturedSpecs());
        captured.addAll(this.controlCenterActualPreview.capturedComponents());
        if (captured.isEmpty() && this.controlCenterActualPreview.capturedLayout().editableMode == null) {
            Toast.makeText(this, "真实布局中没有可导入项目", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            ControlCenterCaptureImport imported = ControlCenterCaptureImport.from(
                    controlCenterActualPreview.capturedLayout(), currentControlGridPlan().mode(controlCenterCompactMode).columns,
                    getResources().getDisplayMetrics().density);
            applyControlGridPlan(currentControlGridPlan().withMode(controlCenterCompactMode, imported.mode), imported.spacingDp);
            android.util.Log.i("FusionStatusBar", "control-center imported geometry columns=" + imported.mode.columns
                    + " spacingDp=" + imported.spacingDp + " items=" + imported.mode.items.size());
        } catch (IllegalArgumentException error) {
            android.util.Log.w("FusionStatusBar", "Native layout import rejected", error);
            Toast.makeText(this, "布局坐标不完整，请展开控制中心后重新拉取", Toast.LENGTH_SHORT).show();
            return;
        }
        Toast.makeText(this, "已导入编辑草稿，点击推送后才会应用", Toast.LENGTH_SHORT).show();
    }

    private void applyControlGridPlan(com.xtjm.fusionstatusbar.ControlCenterLayoutPlan next) {
        applyControlGridPlan(next, null);
    }

    private void applyControlGridPlan(ControlCenterLayoutPlan next, Integer spacing) {
        com.xtjm.fusionstatusbar.ControlCenterConfig settings = controlCenterSettings();
        java.lang.String encoded = next.encode();
        if (encoded.equals(settings.layoutPlan) && (spacing == null || spacing == settings.spacing)) {
            return;
        }
        updateControlCenterDraft((spacing == null ? settings : settings.withSpacing(spacing)).withLayoutPlan(encoded));
    }

    private void undoControlGridChange() {
        if (configRestoring) return;
        if (!editorSession.undo()) Toast.makeText(this, R.string.editor_nothing_to_undo, Toast.LENGTH_SHORT).show();
        else renderEditorSession();
    }

    private void redoControlGridChange() {
        if (configRestoring) return;
        if (!editorSession.redo()) Toast.makeText(this, R.string.editor_nothing_to_redo, Toast.LENGTH_SHORT).show();
        else renderEditorSession();
    }

    private void renderEditorSession() {
        syncEditorState();
        persistEditorSession();
        updateSettingsSummaries();
    }

    private void clearControlGridMode() {
        ControlCenterLayoutPlan plan = currentControlGridPlan();
        ControlCenterLayoutPlan.Mode mode = plan.mode(this.controlCenterCompactMode);
        if (mode.items.isEmpty()) {
            Toast.makeText(this, "当前网格已经为空", Toast.LENGTH_SHORT).show();
            return;
        }
        applyControlGridPlan(plan.withMode(this.controlCenterCompactMode,
                new ControlCenterLayoutPlan.Mode(mode.columns,
                        java.util.Collections.emptyList())));
    }

    private com.xtjm.fusionstatusbar.ControlCenterLayoutPlan.Item findControlGridItem(java.lang.String id) {
        com.xtjm.fusionstatusbar.ControlCenterLayoutPlan.Resolved resolved = currentControlGridPlan().resolve(this.controlCenterCompactMode, availableControlGridSpecs());
        for (com.xtjm.fusionstatusbar.ControlCenterLayoutPlan.Item item : resolved.items) {
            if (item.id.equals(id)) {
                return item;
            }
        }
        return null;
    }

    private void changeControlGridItem(java.lang.String id, java.util.function.UnaryOperator<com.xtjm.fusionstatusbar.ControlCenterLayoutPlan.Item> change) {
        changeControlGridItem(id, change, false);
    }

    private void changeControlGridItem(java.lang.String id,
            java.util.function.UnaryOperator<com.xtjm.fusionstatusbar.ControlCenterLayoutPlan.Item> change,
            boolean allowLocked) {
        com.xtjm.fusionstatusbar.ControlCenterLayoutPlan.Item current = findControlGridItem(id);
        if (current == null) {
            return;
        }
        if (current.locked && !allowLocked) {
            Toast.makeText(this, "项目已锁定，请先解锁", Toast.LENGTH_SHORT).show();
            return;
        }
        com.xtjm.fusionstatusbar.ControlCenterLayoutPlan.Item changed = (com.xtjm.fusionstatusbar.ControlCenterLayoutPlan.Item) change.apply(current);
        com.xtjm.fusionstatusbar.ControlCenterLayoutPlan plan = currentControlGridPlan();
        com.xtjm.fusionstatusbar.ControlCenterLayoutPlan.Mode mode = plan.mode(this.controlCenterCompactMode);
        if (!allowLocked) {
            ControlCenterLayoutPlan.Mode placed = ControlCenterGridPlacement.place(mode, changed);
            if (placed == null) {
                Toast.makeText(this, "目标位置超出画布或被锁定项目占用", Toast.LENGTH_SHORT).show();
                return;
            }
            applyControlGridPlan(plan.withMode(controlCenterCompactMode, placed));
            return;
        }
        java.util.ArrayList<com.xtjm.fusionstatusbar.ControlCenterLayoutPlan.Item> items = new java.util.ArrayList<>();
        items.add(changed);
        for (com.xtjm.fusionstatusbar.ControlCenterLayoutPlan.Item item : mode.items) {
            if (!item.id.equals(id)) {
                items.add(item);
            }
        }
        applyControlGridPlan(plan.withMode(this.controlCenterCompactMode, new com.xtjm.fusionstatusbar.ControlCenterLayoutPlan.Mode(mode.columns, items)));
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ com.xtjm.fusionstatusbar.ControlCenterLayoutPlan.Item lambda$moveControlGridItem$12(int x, int y, com.xtjm.fusionstatusbar.ControlCenterLayoutPlan.Item item) {
        return item.withPosition(java.lang.Math.max(0, java.lang.Math.min(x, currentControlGridPlan().mode(this.controlCenterCompactMode).columns - item.width)), java.lang.Math.max(0, java.lang.Math.min(y, 128 - item.height)));
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void moveControlGridItem(java.lang.String id, final int x, final int y) {
        changeControlGridItem(id, new java.util.function.UnaryOperator() { // from class: com.xtjm.fusionstatusbar.MainActivity$$ExternalSyntheticLambda28
            @Override // java.util.function.Function
            public final java.lang.Object apply(java.lang.Object obj) {
                return MainActivity.this.lambda$moveControlGridItem$12(x, y, (com.xtjm.fusionstatusbar.ControlCenterLayoutPlan.Item) obj);
            }
        });
    }

    /** A completed gesture is one validated, undoable draft transaction, never a save. */
    private void commitControlGridGesture(String id, int x, int y, int width, int height) {
        ControlCenterLayoutPlan.Item item = findControlGridItem(id);
        if (item == null || item.locked) return;
        int nextX = x < 0 ? item.x : x;
        int nextY = y < 0 ? item.y : y;
        int nextWidth = width < 0 ? item.width : width;
        int nextHeight = height < 0 ? item.height : height;
        if (nextX == item.x && nextY == item.y && nextWidth == item.width && nextHeight == item.height) return;
        changeControlGridItem(id, current -> current.withPosition(nextX, nextY).withSize(nextWidth, nextHeight));
    }

    private void applyControlGridOverlayAction(String id, ControlCenterGridEditor.EditAction action) {
        ControlCenterLayoutPlan.Item item = findControlGridItem(id);
        if (item == null || (item.locked && action != ControlCenterGridEditor.EditAction.TOGGLE_LOCK)) return;
        ControlCenterLayoutPlan plan = currentControlGridPlan();
        ControlCenterLayoutPlan next;
        switch (action) {
            case REMOVE:
                next = plan.removeItem(controlCenterCompactMode, id);
                break;
            case TOGGLE_LOCK:
                next = plan.withItem(controlCenterCompactMode, item.withLocked(!item.locked));
                break;
            case TOGGLE_HIDDEN:
                next = plan.withItem(controlCenterCompactMode, item.withHidden(!item.hidden));
                break;
            case UNPAIR:
                if (item.type != ControlCenterLayoutPlan.Type.PAIR && item.type != ControlCenterLayoutPlan.Type.GROUP) return;
                next = plan.unpair(controlCenterCompactMode, id);
                break;
            case TOGGLE_DIRECTION:
                if (item.type != ControlCenterLayoutPlan.Type.PAIR) return;
                boolean horizontal = item.direction != ControlCenterLayoutPlan.Direction.HORIZONTAL;
                int columns = plan.mode(controlCenterCompactMode).columns;
                int nextWidth = Math.min(columns, Math.max(horizontal ? 2 : 1, item.height));
                int nextHeight = Math.min(ControlCenterLayoutPlan.MAX_HEIGHT,
                        Math.max(horizontal ? 1 : 2, item.width));
                ControlCenterLayoutPlan.Item rotated = item.withDirection(horizontal
                        ? ControlCenterLayoutPlan.Direction.HORIZONTAL : ControlCenterLayoutPlan.Direction.VERTICAL)
                        .withSize(nextWidth, nextHeight)
                        .withPosition(Math.min(item.x, columns - nextWidth),
                                Math.min(item.y, ControlCenterLayoutPlan.MAX_ROWS - nextHeight));
                next = plan.withItem(controlCenterCompactMode, rotated);
                break;
            default: return;
        }
        applyValidatedControlGridEdit(next);
    }

    private void applyValidatedControlGridEdit(ControlCenterLayoutPlan next) {
        if (!next.mode(controlCenterCompactMode).validate().isEmpty()) {
            Toast.makeText(this, "当前位置空间不足，布局未修改", Toast.LENGTH_SHORT).show();
            return;
        }
        applyControlGridPlan(next);
    }

    private void resizeControlGridItem(java.lang.String id, final int width, final int height) {
        changeControlGridItem(id, new java.util.function.UnaryOperator() {
            @Override
            public java.lang.Object apply(java.lang.Object value) {
                com.xtjm.fusionstatusbar.ControlCenterLayoutPlan.Item item =
                        (com.xtjm.fusionstatusbar.ControlCenterLayoutPlan.Item) value;
                int columns = currentControlGridPlan().mode(controlCenterCompactMode).columns;
                int nextWidth = Math.max(1, Math.min(width, columns - item.x));
                int nextHeight = Math.max(1, Math.min(
                        com.xtjm.fusionstatusbar.ControlCenterLayoutPlan.MAX_HEIGHT, height));
                if (item.type == com.xtjm.fusionstatusbar.ControlCenterLayoutPlan.Type.PAIR) {
                    if (item.direction
                            == com.xtjm.fusionstatusbar.ControlCenterLayoutPlan.Direction.HORIZONTAL) {
                        nextWidth = Math.max(2, nextWidth);
                    } else {
                        nextHeight = Math.max(2, nextHeight);
                    }
                }
                return item.withSize(nextWidth, nextHeight);
            }
        });
    }

    private void changeControlGroup(String id, ControlCenterGroupData data) {
        ControlCenterLayoutPlan.Item current = findControlGridItem(id);
        if (current == null || current.locked || current.type != ControlCenterLayoutPlan.Type.GROUP) return;
        ControlCenterLayoutPlan plan = currentControlGridPlan();
        for (String spec : current.withGroup(data).specs()) {
            for (ControlCenterLayoutPlan.Item other : plan.mode(controlCenterCompactMode).items) {
                if (!other.id.equals(id) && other.containsSpec(spec)
                        && (other.locked || other.type != ControlCenterLayoutPlan.Type.TILE)) return;
            }
        }
        applyValidatedControlGridEdit(plan.withItem(controlCenterCompactMode, current.withGroup(data)));
    }

    private void showAddControlPairDialog() {
        java.util.ArrayList<String> specs = availableControlGridSpecs();
        specs.removeIf(ControlCenterComponentSpec::isSpecial);
        if (specs.size() < 2) {
            Toast.makeText(this, "至少需要两个可用磁贴", Toast.LENGTH_SHORT).show();
            return;
        }
        showControlTileChoiceSheet("选择第一个磁贴", specs, firstIndex -> {
            String first = specs.get(firstIndex);
            java.util.ArrayList<String> others = new java.util.ArrayList<>(specs);
            others.remove(first);
            showControlTileChoiceSheet("选择第二个磁贴", others, secondIndex ->
                    applyControlGridPlan(currentControlGridPlan().withPair(controlCenterCompactMode,
                            first, others.get(secondIndex), ControlCenterLayoutPlan.Direction.HORIZONTAL,
                            -1, -1, 2, 1)));
        });
    }

    private void showAddControlItemDialog() {
        if (controlCenterAppTilesLoading) {
            controlCenterAppTilesReady = this::showAddControlItemDialog;
            Toast.makeText(this, "正在读取应用快捷开关", Toast.LENGTH_SHORT).show();
            return;
        }
        java.util.ArrayList<String> specs = availableControlGridSpecs();
        ControlCenterLayoutPlan.Mode mode = currentControlGridPlan().mode(controlCenterCompactMode);
        specs.removeIf(spec -> mode.items.stream().anyMatch(item -> item.containsSpec(spec)));
        specs.sort(java.util.Comparator.comparingInt(spec ->
                ControlCenterComponentSpec.isSpecial(spec) ? 2 : spec.startsWith("custom(") ? 1 : 0));
        String[] labels = new String[specs.size() + 2];
        boolean hasCapture = controlCenterActualPreview != null && controlCenterActualPreview.hasTileLayout();
        java.util.List<String> capturedComponents = hasCapture ? controlCenterActualPreview.capturedComponents()
                : java.util.Collections.emptyList();
        labels[0] = "组合卡片 · 将两个磁贴合并";
        labels[1] = "空白组合卡";
        for (int index = 0; index < specs.size(); index++) {
            labels[index + 2] = addControlItemLabel(specs.get(index), capturedComponents, hasCapture);
        }
        showControlChoiceSheet("添加项目", labels,
                index -> addControlItemIcon(index < 2 ? "" : specs.get(index - 2)), index -> {
            if (index == 0) showAddControlPairDialog();
            else if (index == 1) {
                String id = "group:" + java.util.UUID.randomUUID();
                applyControlGridPlan(currentControlGridPlan().withItem(controlCenterCompactMode,
                        ControlCenterLayoutPlan.Item.group(id, ControlCenterGroupData.empty(), -1, -1, 2, 2)));
                showControlGridItemDialog(id);
            } else addTileToBlankGrid(specs.get(index - 2));
        });
    }

    private void showControlTileChoiceSheet(String title, java.util.List<String> specs,
            java.util.function.IntConsumer selected) {
        showControlChoiceSheet(title, specs.stream().map(this::tileLabel).toArray(String[]::new),
                index -> addControlItemIcon(specs.get(index)), selected);
    }

    private android.app.Dialog showControlChoiceSheet(String title, String[] labels,
            java.util.function.IntFunction<android.graphics.drawable.Drawable> icons,
            java.util.function.IntConsumer selected) {
        if (settingsDialogs == null) settingsDialogs = new SettingsDialogController(this);
        return settingsDialogs.showList(title, new SettingsListAdapter(this, labels, surfaceColor(),
                textPrimaryColor(), accentColor(), icons), selected, surfaceColor(), textPrimaryColor(), accentColor());
    }

    private String addControlItemLabel(String spec, java.util.List<String> capturedComponents, boolean hasCapture) {
        if (!ControlCenterComponentSpec.isSpecial(spec)) return tileLabel(spec);
        String label = ControlCenterComponentSpec.label(spec);
        return "组件 · " + label + (capturedComponents.contains(spec) ? "" : hasCapture ? " · 本次未检测到" : " · 未验证");
    }

    private android.graphics.drawable.Drawable addControlItemIcon(String spec) {
        int size = dp(28);
        android.graphics.drawable.Drawable icon;
        if (spec == null || spec.isEmpty()) {
            icon = getDrawable(android.R.drawable.ic_menu_add).mutate();
            icon.setTint(textPrimaryColor());
        } else if (ControlCenterComponentSpec.isSpecial(spec)) {
            icon = getDrawable(android.R.drawable.ic_menu_manage).mutate();
            icon.setTint(textPrimaryColor());
        } else {
            ControlCenterCapturedStyle style = controlTileStyle(spec);
            ControlCenterIcons.Artwork artwork = new ControlCenterIcons.Artwork(this, spec, false,
                    style == null ? null : style.icon);
            artwork.setBackdrop(surfaceColor());
            icon = artwork.drawable;
            if (!artwork.captured) icon.setTint(textPrimaryColor());
        }
        icon.setBounds(0, 0, size, size);
        return icon;
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void showControlGridItemDialog(final java.lang.String id) {
        final com.xtjm.fusionstatusbar.ControlCenterLayoutPlan.Item selected = findControlGridItem(id);
        if (selected == null) {
            return;
        }
        showSettingsDialog(selected.type == ControlCenterLayoutPlan.Type.PAIR || selected.type == ControlCenterLayoutPlan.Type.GROUP ? "组合卡片" : tileLabel(selected.firstSpec), new com.xtjm.fusionstatusbar.MainActivity.DialogBuilder() { // from class: com.xtjm.fusionstatusbar.MainActivity$$ExternalSyntheticLambda36
            @Override // com.xtjm.fusionstatusbar.MainActivity.DialogBuilder
            public final void build(android.widget.LinearLayout linearLayout) {
                MainActivity.this.lambda$showControlGridItemDialog$29(selected, id, linearLayout);
            }
        });
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$showControlGridItemDialog$29(com.xtjm.fusionstatusbar.ControlCenterLayoutPlan.Item selected, final java.lang.String id, android.widget.LinearLayout content) {
        ControlCenterGroupEditor groupEditor = selected.type == ControlCenterLayoutPlan.Type.GROUP
                ? new ControlCenterGroupEditor(this, () -> findControlGridItem(id), () -> {
                    java.util.List<String> available = availableControlGridSpecs();
                    available.removeIf(spec -> ControlCenterComponentSpec.isSpecial(spec)
                            || currentControlGridPlan().mode(controlCenterCompactMode).items.stream().anyMatch(item ->
                                    item.containsSpec(spec) && (item.type != ControlCenterLayoutPlan.Type.TILE || item.locked)));
                    return available;
                }, group -> changeControlGroup(id, group), this::tileLabel,
                () -> {
                    applyControlGridOverlayAction(id, ControlCenterGridEditor.EditAction.UNPAIR);
                    if (findControlGridItem(id) == null) content.removeAllViews();
                }, textPrimaryColor(), (specs, selectedIndex) ->
                        showControlTileChoiceSheet("添加成员", specs, selectedIndex)) : null;
        if (groupEditor != null) content.addView(groupEditor, matchWrap());
        int columns = currentControlGridPlan().mode(this.controlCenterCompactMode).columns;
        addSlider(content, "宽度", "占用网格列数", 1, columns, selected.width, " 列", new com.xtjm.fusionstatusbar.MainActivity.IntegerUpdate() { // from class: com.xtjm.fusionstatusbar.MainActivity$$ExternalSyntheticLambda19
            @Override // com.xtjm.fusionstatusbar.MainActivity.IntegerUpdate
            public final void apply(int i) {
                MainActivity.this.lambda$showControlGridItemDialog$18(id, i);
            }
        });
        addSlider(content, "高度", "占用网格行数", 1, 6, selected.height, " 行", new com.xtjm.fusionstatusbar.MainActivity.IntegerUpdate() { // from class: com.xtjm.fusionstatusbar.MainActivity$$ExternalSyntheticLambda20
            @Override // com.xtjm.fusionstatusbar.MainActivity.IntegerUpdate
            public final void apply(int i) {
                MainActivity.this.lambda$showControlGridItemDialog$20(id, i);
            }
        });
        ControlCenterCardStylePicker stylePicker = new ControlCenterCardStylePicker(this, selected,
                textPrimaryColor(), surfaceColor(), proposed -> {
            changeControlGridItem(id, item -> item.withShape(proposed.shape, proposed.cornerRadius));
            return findControlGridItem(id);
        });
        content.addView(stylePicker, matchWrap());
        android.widget.Button remove = new android.widget.Button(this);
        remove.setEnabled(!selected.locked);
        android.widget.Button state = new android.widget.Button(this);
        state.setText(selected.locked ? "解锁此项目" : "锁定此项目");
        state.setAllCaps(false);
        state.setOnClickListener(new android.view.View.OnClickListener() {
            @Override
            public void onClick(android.view.View view) {
                changeControlGridItem(id, new java.util.function.UnaryOperator() {
                    @Override
                    public java.lang.Object apply(java.lang.Object raw) {
                        com.xtjm.fusionstatusbar.ControlCenterLayoutPlan.Item item =
                                (com.xtjm.fusionstatusbar.ControlCenterLayoutPlan.Item) raw;
                        return item.withLocked(!item.locked);
                    }
                }, true);
                state.setText(findControlGridItem(id).locked ? "解锁此项目" : "锁定此项目");
                stylePicker.bind(findControlGridItem(id));
                remove.setEnabled(!findControlGridItem(id).locked);
                if (groupEditor != null) groupEditor.refresh();
            }
        });
        content.addView(state, matchWrap());
        android.widget.Button visibility = new android.widget.Button(this);
        visibility.setText(selected.hidden ? "显示此项目" : "隐藏此项目");
        visibility.setAllCaps(false);
        visibility.setOnClickListener(new android.view.View.OnClickListener() {
            @Override
            public void onClick(android.view.View view) {
                changeControlGridItem(id, new java.util.function.UnaryOperator() {
                    @Override
                    public java.lang.Object apply(java.lang.Object raw) {
                        com.xtjm.fusionstatusbar.ControlCenterLayoutPlan.Item item =
                                (com.xtjm.fusionstatusbar.ControlCenterLayoutPlan.Item) raw;
                        return item.withHidden(!item.hidden);
                    }
                }, true);
                visibility.setText(findControlGridItem(id).hidden ? "显示此项目" : "隐藏此项目");
            }
        });
        content.addView(visibility, matchWrap());
        addSlider(content, "层级", "重叠时的绘制顺序", -20, 20, selected.zIndex, "",
                new com.xtjm.fusionstatusbar.MainActivity.IntegerUpdate() {
                    @Override
                    public void apply(int value) {
                        changeControlGridItem(id, new java.util.function.UnaryOperator() {
                            @Override
                            public java.lang.Object apply(java.lang.Object raw) {
                                return ((com.xtjm.fusionstatusbar.ControlCenterLayoutPlan.Item) raw)
                                        .withZIndex(value);
                            }
                        }, true);
                    }
                });
        if (columns > selected.width) {
            addSlider(content, "横向位置", "网格起始列", 0, columns - selected.width, selected.x, " 列", new com.xtjm.fusionstatusbar.MainActivity.IntegerUpdate() { // from class: com.xtjm.fusionstatusbar.MainActivity$$ExternalSyntheticLambda21
                @Override // com.xtjm.fusionstatusbar.MainActivity.IntegerUpdate
                public final void apply(int i) {
                    MainActivity.this.lambda$showControlGridItemDialog$22(id, i);
                }
            });
        }
        addSlider(content, "纵向位置", "网格起始行", 0, java.lang.Math.min(128 - selected.height, java.lang.Math.max(24, selected.y + 4)), selected.y, " 行", new com.xtjm.fusionstatusbar.MainActivity.IntegerUpdate() { // from class: com.xtjm.fusionstatusbar.MainActivity$$ExternalSyntheticLambda23
            @Override // com.xtjm.fusionstatusbar.MainActivity.IntegerUpdate
            public final void apply(int i) {
                MainActivity.this.lambda$showControlGridItemDialog$24(id, i);
            }
        });
        remove.setText("从网格移除");
        remove.setAllCaps(false);
        remove.setOnClickListener(new android.view.View.OnClickListener() {
            @Override
            public void onClick(android.view.View view) {
                ControlCenterLayoutPlan.Item current = findControlGridItem(id);
                if (current == null || current.locked) return;
                applyControlGridPlan(currentControlGridPlan().removeItem(
                        MainActivity.this.controlCenterCompactMode, id));
                if (selected.type == ControlCenterLayoutPlan.Type.GROUP) content.removeAllViews();
            }
        });
        content.addView(remove, matchWrap());
        if (selected.type == com.xtjm.fusionstatusbar.ControlCenterLayoutPlan.Type.PAIR) {
            final android.widget.Button direction = new android.widget.Button(this);
            direction.setText(selected.direction == com.xtjm.fusionstatusbar.ControlCenterLayoutPlan.Direction.HORIZONTAL ? "改为上下排列" : "改为左右排列");
            direction.setAllCaps(false);
            direction.setOnClickListener(new android.view.View.OnClickListener() { // from class: com.xtjm.fusionstatusbar.MainActivity$$ExternalSyntheticLambda24
                @Override // android.view.View.OnClickListener
                public final void onClick(android.view.View view) {
                    MainActivity.this.lambda$showControlGridItemDialog$26(id, direction, view);
                }
            });
            content.addView(direction, matchWrap());
            android.widget.Button unpair = new android.widget.Button(this);
            unpair.setText("拆分卡片");
            unpair.setAllCaps(false);
            unpair.setOnClickListener(new android.view.View.OnClickListener() { // from class: com.xtjm.fusionstatusbar.MainActivity$$ExternalSyntheticLambda25
                @Override // android.view.View.OnClickListener
                public final void onClick(android.view.View view) {
                    MainActivity.this.lambda$showControlGridItemDialog$27(id, view);
                }
            });
            content.addView(unpair, matchWrap());
            return;
        }
        if (selected.type == com.xtjm.fusionstatusbar.ControlCenterLayoutPlan.Type.TILE) {
            android.widget.Button pair = new android.widget.Button(this);
            pair.setText("与另一磁贴组成卡片");
            pair.setAllCaps(false);
            pair.setOnClickListener(new android.view.View.OnClickListener() { // from class: com.xtjm.fusionstatusbar.MainActivity$$ExternalSyntheticLambda26
                @Override // android.view.View.OnClickListener
                public final void onClick(android.view.View view) {
                    MainActivity.this.lambda$showControlGridItemDialog$28(view);
                }
            });
            content.addView(pair, matchWrap());
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$showControlGridItemDialog$18(java.lang.String id, final int value) {
        changeControlGridItem(id, new java.util.function.UnaryOperator() { // from class: com.xtjm.fusionstatusbar.MainActivity$$ExternalSyntheticLambda6
            @Override // java.util.function.Function
            public final java.lang.Object apply(java.lang.Object obj) {
                com.xtjm.fusionstatusbar.ControlCenterLayoutPlan.Item item = (com.xtjm.fusionstatusbar.ControlCenterLayoutPlan.Item) obj;
                return item.withSize(value, item.height);
            }
        });
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$showControlGridItemDialog$20(java.lang.String id, final int value) {
        changeControlGridItem(id, new java.util.function.UnaryOperator() { // from class: com.xtjm.fusionstatusbar.MainActivity$$ExternalSyntheticLambda34
            @Override // java.util.function.Function
            public final java.lang.Object apply(java.lang.Object obj) {
                com.xtjm.fusionstatusbar.ControlCenterLayoutPlan.Item item = (com.xtjm.fusionstatusbar.ControlCenterLayoutPlan.Item) obj;
                return item.withSize(item.width, value);
            }
        });
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$showControlGridItemDialog$22(java.lang.String id, final int value) {
        changeControlGridItem(id, new java.util.function.UnaryOperator() { // from class: com.xtjm.fusionstatusbar.MainActivity$$ExternalSyntheticLambda32
            @Override // java.util.function.Function
            public final java.lang.Object apply(java.lang.Object obj) {
                com.xtjm.fusionstatusbar.ControlCenterLayoutPlan.Item item = (com.xtjm.fusionstatusbar.ControlCenterLayoutPlan.Item) obj;
                return item.withPosition(value, item.y);
            }
        });
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$showControlGridItemDialog$24(java.lang.String id, final int value) {
        changeControlGridItem(id, new java.util.function.UnaryOperator() { // from class: com.xtjm.fusionstatusbar.MainActivity$$ExternalSyntheticLambda3
            @Override // java.util.function.Function
            public final java.lang.Object apply(java.lang.Object obj) {
                com.xtjm.fusionstatusbar.ControlCenterLayoutPlan.Item item = (com.xtjm.fusionstatusbar.ControlCenterLayoutPlan.Item) obj;
                return item.withPosition(item.x, value);
            }
        });
    }

    static /* synthetic */ com.xtjm.fusionstatusbar.ControlCenterLayoutPlan.Item lambda$showControlGridItemDialog$25(com.xtjm.fusionstatusbar.ControlCenterLayoutPlan.Item item) {
        com.xtjm.fusionstatusbar.ControlCenterLayoutPlan.Direction direction;
        if (item.direction == com.xtjm.fusionstatusbar.ControlCenterLayoutPlan.Direction.HORIZONTAL) {
            direction = com.xtjm.fusionstatusbar.ControlCenterLayoutPlan.Direction.VERTICAL;
        } else {
            direction = com.xtjm.fusionstatusbar.ControlCenterLayoutPlan.Direction.HORIZONTAL;
        }
        return item.withDirection(direction);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$showControlGridItemDialog$26(java.lang.String id, android.widget.Button direction, android.view.View view) {
        changeControlGridItem(id, new java.util.function.UnaryOperator() { // from class: com.xtjm.fusionstatusbar.MainActivity$$ExternalSyntheticLambda29
            @Override // java.util.function.Function
            public final java.lang.Object apply(java.lang.Object obj) {
                return com.xtjm.fusionstatusbar.MainActivity.lambda$showControlGridItemDialog$25((com.xtjm.fusionstatusbar.ControlCenterLayoutPlan.Item) obj);
            }
        });
        direction.setText(findControlGridItem(id).direction == com.xtjm.fusionstatusbar.ControlCenterLayoutPlan.Direction.HORIZONTAL ? "改为上下排列" : "改为左右排列");
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$showControlGridItemDialog$27(java.lang.String id, android.view.View view) {
        applyControlGridPlan(currentControlGridPlan().unpair(this.controlCenterCompactMode, id));
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$showControlGridItemDialog$28(android.view.View view) {
        showAddControlPairDialog();
    }

    private void refreshControlCenterPreview() {
        if (this.config == null) {
            return;
        }
        refreshControlCenterGridEditor();
    }

    private void applyControlCenterPreviewVisibility() {
        boolean visible = this.config != null && controlCenterSettings().enabled;
        int visibility = visible ? View.VISIBLE : View.GONE;
        if (this.controlCenterPreviewTitle != null) {
            this.controlCenterPreviewTitle.setVisibility(visibility);
        }
        if (this.controlCenterActualPanel != null) {
            this.controlCenterActualPanel.setVisibility(visibility);
        }
        if (this.controlCenterGridPanel != null) {
            this.controlCenterGridPanel.setVisibility(visibility);
        }
        if (this.controlCenterPreview != null) {
            this.controlCenterPreview.setVisibility(View.GONE);
        }
        if (!visible && this.controlCenterActualPreview != null) {
            this.controlCenterActualPreview.setVisibility(View.GONE);
        }
    }

    private void addActualControlCenterPreview(android.widget.LinearLayout linearLayout, java.util.List<java.lang.String> active) {
        linearLayout.removeAllViews();
        this.previewSliderIndex = 0;
        linearLayout.setPadding(dp(4), dp(4), dp(4), dp(8));
        com.xtjm.fusionstatusbar.ControlCenterConfig settings = controlCenterSettings();
        java.lang.String wifi = firstActive(active, "wifi");
        java.lang.String mobile = firstActive(active, "cell", "internet");
        if (wifi != null || mobile != null) {
            android.widget.LinearLayout network = new android.widget.LinearLayout(this);
            network.setOrientation(LinearLayout.HORIZONTAL);
            int networkRows = wifi != null ? java.lang.Math.max(1, settings.tileHeight(wifi)) : 1;
            if (mobile != null) {
                networkRows = java.lang.Math.max(networkRows, settings.tileHeight(mobile));
            }
            int networkHeight = dp(networkRows * 78);
            if (wifi != null) {
                com.xtjm.fusionstatusbar.ControlCenterPreviewTile wifiTile = actualTile(wifi, "ble-5-1-503", "已连接", true);
                android.widget.LinearLayout.LayoutParams params = new android.widget.LinearLayout.LayoutParams(0, networkHeight, settings.tileWidth(wifi));
                params.setMargins(0, 0, dp(4), dp(5));
                network.addView(wifiTile, params);
            }
            if (mobile != null) {
                com.xtjm.fusionstatusbar.ControlCenterPreviewTile mobileTile = actualTile(mobile, "移动数据", "不可用", false);
                android.widget.LinearLayout.LayoutParams params2 = new android.widget.LinearLayout.LayoutParams(0, networkHeight, settings.tileWidth(mobile));
                params2.setMargins(dp(4), 0, 0, dp(5));
                network.addView(mobileTile, params2);
            }
            linearLayout.addView(network, matchWrap());
        }
        android.widget.LinearLayout controls = new android.widget.LinearLayout(this);
        controls.setOrientation(LinearLayout.HORIZONTAL);
        controls.addView(mediaPreview(), new android.widget.LinearLayout.LayoutParams(0, dp(142), 2.0f));
        controls.addView(verticalSlider("☼", android.graphics.Color.rgb(255, 168, 0)), new android.widget.LinearLayout.LayoutParams(0, dp(142), 0.82f));
        controls.addView(verticalSlider("≋", android.graphics.Color.rgb(215, 216, 218)), new android.widget.LinearLayout.LayoutParams(0, dp(142), 0.82f));
        linearLayout.addView(controls, matchWrap());
        android.widget.LinearLayout linearLayout2 = new android.widget.LinearLayout(this);
        linearLayout2.setOrientation(LinearLayout.HORIZONTAL);
        linearLayout2.setGravity(17);
        linearLayout2.setPadding(dp(8), dp(5), dp(8), dp(5));
        linearLayout2.setBackground(rounded(android.graphics.Color.rgb(36, 39, 43), dp(24)));
        android.widget.LinearLayout deviceDots = new android.widget.LinearLayout(this);
        deviceDots.setGravity(16);
        int[] deviceColors = {android.graphics.Color.rgb(151, 113, 255), android.graphics.Color.rgb(108, 222, 136), android.graphics.Color.rgb(255, 139, 147)};
        int length = deviceColors.length;
        int i = 0;
        while (i < length) {
            int color = deviceColors[i];
            android.view.View dot = new android.view.View(this);
            dot.setBackground(rounded(color, dp(100)));
            android.widget.LinearLayout.LayoutParams dotParams = new android.widget.LinearLayout.LayoutParams(dp(18), dp(18));
            dotParams.setMargins(dp(2), 0, dp(2), 0);
            deviceDots.addView(dot, dotParams);
            i++;
            deviceColors = deviceColors;
            controls = controls;
        }
        linearLayout2.addView(deviceDots, new android.widget.LinearLayout.LayoutParams(-2, dp(48)));
        android.widget.TextView deviceTitle = labelView("融合设备中心", 16.0f, true);
        deviceTitle.setTextColor(android.graphics.Color.rgb(225, 226, 228));
        android.widget.LinearLayout.LayoutParams deviceTitleParams = new android.widget.LinearLayout.LayoutParams(-2, dp(48));
        deviceTitleParams.leftMargin = dp(10);
        linearLayout2.addView(deviceTitle, deviceTitleParams);
        bindControlTileDrag(linearLayout2, "control:device-center");
        android.widget.LinearLayout.LayoutParams deviceParams = matchWrap();
        deviceParams.topMargin = dp(5);
        linearLayout.addView(linearLayout2, deviceParams);
        java.util.ArrayList<java.lang.String> grid = new java.util.ArrayList<>(active);
        if (wifi != null) {
            grid.remove(wifi);
        }
        if (mobile != null) {
            grid.remove(mobile);
        }
        addCircularTileGrid(linearLayout, grid, settings);
        android.widget.TextView edit = labelView("编辑", 13.0f, false);
        edit.setTextColor(-1);
        edit.setGravity(17);
        edit.setBackground(rounded(android.graphics.Color.rgb(145, 146, 148), dp(22)));
        android.widget.LinearLayout.LayoutParams editParams = new android.widget.LinearLayout.LayoutParams(dp(72), dp(38));
        editParams.gravity = 1;
        editParams.topMargin = dp(6);
        linearLayout.addView(edit, editParams);
    }

    private java.lang.String firstActive(java.util.List<java.lang.String> active, java.lang.String... candidates) {
        for (java.lang.String candidate : candidates) {
            for (java.lang.String spec : active) {
                if (candidate.equalsIgnoreCase(spec)) {
                    return spec;
                }
            }
        }
        return null;
    }

    private com.xtjm.fusionstatusbar.ControlCenterPreviewTile actualTile(java.lang.String spec, java.lang.String title, java.lang.String subtitle, boolean on) {
        com.xtjm.fusionstatusbar.ControlCenterPreviewTile tile = new com.xtjm.fusionstatusbar.ControlCenterPreviewTile(this, spec, title, subtitle, 2, on, controlCenterSettings().cornerRadius, controlCenterSettings().tileScale);
        bindControlTileDrag(tile, spec);
        return tile;
    }

    private void addCircularTileGrid(android.widget.LinearLayout container, java.util.List<java.lang.String> specs, com.xtjm.fusionstatusbar.ControlCenterConfig settings) {
        android.widget.LinearLayout row;
        int used;
        int mode;
        int used2 = 0;
        android.widget.LinearLayout row2 = null;
        int index = 0;
        while (index < index) {
            java.lang.String spec = specs.get(index);
            int span = settings.tileWidth(spec);
            int rows = settings.tileHeight(spec);
            if (row2 == null || used2 + span > settings.columns) {
                android.widget.LinearLayout row3 = new android.widget.LinearLayout(this);
                row3.setGravity(16);
                container.addView(row3, matchWrap());
                row = row3;
                used = 0;
            } else {
                used = used2;
                row = row2;
            }
            if (span > 1) {
                mode = 2;
            } else {
                mode = 0;
            }
            android.widget.LinearLayout row4 = row;
            com.xtjm.fusionstatusbar.ControlCenterPreviewTile tile = new com.xtjm.fusionstatusbar.ControlCenterPreviewTile(this, spec, tileLabel(spec), "", mode, previewTileOn(spec), settings.cornerRadius, settings.tileScale);
            bindControlTileDrag(tile, spec);
            android.widget.LinearLayout.LayoutParams params = new android.widget.LinearLayout.LayoutParams(0, dp(rows * 66), span);
            int margin = dp(java.lang.Math.max(1, settings.spacing / 2));
            params.setMargins(margin, dp(4), margin, dp(4));
            row4.addView(tile, params);
            index++;
            row2 = row4;
            used2 = used + span;
        }
    }

    private boolean previewTileOn(java.lang.String spec) {
        return "wifi".equalsIgnoreCase(spec) || "bt".equalsIgnoreCase(spec) || "gps".equalsIgnoreCase(spec) || "location".equalsIgnoreCase(spec) || "mute".equalsIgnoreCase(spec) || "batterysaver".equalsIgnoreCase(spec);
    }

    private android.view.View mediaPreview() {
        android.widget.LinearLayout media = new android.widget.LinearLayout(this);
        media.setOrientation(LinearLayout.VERTICAL);
        media.setGravity(1);
        media.setPadding(dp(8), dp(8), dp(8), dp(4));
        media.setBackground(rounded(android.graphics.Color.rgb(30, 33, 37), dp(24)));
        android.widget.TextView cast = labelView("⌁", 20.0f, false);
        cast.setTextColor(-3355444);
        cast.setGravity(8388613);
        media.addView(cast, new android.widget.LinearLayout.LayoutParams(-1, dp(28)));
        android.widget.TextView empty = labelView("暂无播放", 16.0f, true);
        empty.setTextColor(android.graphics.Color.rgb(170, 171, 173));
        empty.setGravity(17);
        media.addView(empty, new android.widget.LinearLayout.LayoutParams(-1, 0, 1.0f));
        android.widget.TextView controls = labelView("|◀        ▶        ▶|", 19.0f, false);
        controls.setTextColor(android.graphics.Color.rgb(225, 226, 228));
        controls.setGravity(17);
        media.addView(controls, new android.widget.LinearLayout.LayoutParams(-1, dp(34)));
        bindControlTileDrag(media, "control:media");
        return media;
    }

    private android.view.View verticalSlider(java.lang.String symbol, int symbolColor) {
        int i = this.previewSliderIndex;
        this.previewSliderIndex = i + 1;
        java.lang.String spec = i == 0 ? "control:brightness" : "control:volume";
        android.widget.FrameLayout slider = new android.widget.FrameLayout(this);
        slider.setPadding(dp(4), dp(4), dp(4), dp(4));
        slider.setBackground(rounded(android.graphics.Color.rgb(36, 39, 43), dp(24)));
        android.widget.TextView icon = labelView(symbol, 24.0f, true);
        icon.setTextColor(symbolColor);
        icon.setGravity(17);
        slider.addView(icon, new android.widget.FrameLayout.LayoutParams(-1, dp(42), 80));
        bindControlTileDrag(slider, spec);
        return slider;
    }

    private android.view.View deviceCircle(java.lang.String spec) {
        com.xtjm.fusionstatusbar.ControlCenterPreviewTile tile = new com.xtjm.fusionstatusbar.ControlCenterPreviewTile(this, spec, "", "", 0, false, controlCenterSettings().cornerRadius, controlCenterSettings().tileScale);
        return tile;
    }

    private void addControlTileGrid(android.widget.LinearLayout linearLayout, java.util.List<java.lang.String> specs, boolean active) {
        com.xtjm.fusionstatusbar.ControlCenterConfig settings = controlCenterSettings();
        int i = 0;
        if (specs.isEmpty()) {
            android.widget.TextView empty = labelView(active ? "暂无磁贴" : "没有可添加的磁贴", 13.0f, false);
            empty.setTextColor(textSecondaryColor());
            empty.setGravity(17);
            linearLayout.addView(empty, new android.widget.LinearLayout.LayoutParams(-1, dp(48)));
            return;
        }
        int index = 0;
        int used = 0;
        android.widget.LinearLayout row = null;
        while (index < specs.size()) {
            java.lang.String spec = specs.get(index);
            int span = settings.tileWidth(spec);
            int rows = settings.tileHeight(spec);
            if (row == null || used + span > settings.columns) {
                row = new android.widget.LinearLayout(this);
                row.setGravity(16);
                linearLayout.addView(row, matchWrap());
                used = 0;
            }
            com.xtjm.fusionstatusbar.ControlCenterPreviewTile tile = createControlTilePreview(spec, active);
            android.widget.LinearLayout.LayoutParams params = new android.widget.LinearLayout.LayoutParams(i, dp(rows * 46), span);
            int margin = dp(java.lang.Math.max(1, settings.spacing / 2));
            params.setMargins(margin, dp(3), margin, dp(3));
            row.addView(tile, params);
            used += span;
            index++;
            i = 0;
        }
    }

    private com.xtjm.fusionstatusbar.ControlCenterPreviewTile createControlTilePreview(java.lang.String spec, boolean active) {
        com.xtjm.fusionstatusbar.ControlCenterConfig settings = controlCenterSettings();
        ControlCenterAppTiles.Entry app = ControlCenterAppTiles.find(controlCenterAppTiles, spec);
        com.xtjm.fusionstatusbar.ControlCenterPreviewTile tile = new com.xtjm.fusionstatusbar.ControlCenterPreviewTile(this, spec, tileLabel(spec), "", 1, active && previewTileOn(spec), settings.cornerRadius, settings.tileScale, true, app == null ? null : app.icon);
        bindControlTileDrag(tile, spec);
        return tile;
    }

    private java.lang.String tileLabel(java.lang.String spec) {
        ControlCenterAppTiles.Entry applicationTile = ControlCenterAppTiles.find(controlCenterAppTiles, spec);
        if (applicationTile != null) return applicationTile.label + (applicationTile.application.equals(applicationTile.label)
                ? "" : " · " + applicationTile.application);
        java.lang.String componentLabel = com.xtjm.fusionstatusbar.ControlCenterComponentSpec.label(spec);
        if (!componentLabel.isEmpty()) {
            return componentLabel;
        }
        int index = tileIndex(spec);
        return index < 0 ? spec : CONTROL_TILE_LABELS[index];
    }

    private void bindControlTileDrag(android.view.View tile, final java.lang.String spec) {
        tile.setTag(spec);
        tile.setOnClickListener(new android.view.View.OnClickListener() { // from class: com.xtjm.fusionstatusbar.MainActivity$$ExternalSyntheticLambda22
            @Override // android.view.View.OnClickListener
            public final void onClick(android.view.View view) {
                MainActivity.this.lambda$bindControlTileDrag$30(spec, view);
            }
        });
        tile.setOnLongClickListener(new android.view.View.OnLongClickListener() { // from class: com.xtjm.fusionstatusbar.MainActivity$$ExternalSyntheticLambda33
            @Override // android.view.View.OnLongClickListener
            public final boolean onLongClick(android.view.View view) {
                return com.xtjm.fusionstatusbar.MainActivity.lambda$bindControlTileDrag$31(spec, view);
            }
        });
        tile.setOnDragListener(new android.view.View.OnDragListener() { // from class: com.xtjm.fusionstatusbar.MainActivity$$ExternalSyntheticLambda37
            @Override // android.view.View.OnDragListener
            public final boolean onDrag(android.view.View view, android.view.DragEvent dragEvent) {
                return MainActivity.this.onControlCenterTileDrag(view, dragEvent);
            }
        });
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$bindControlTileDrag$30(java.lang.String spec, android.view.View view) {
        showControlTileShapeDialog(spec);
    }

    static /* synthetic */ boolean lambda$bindControlTileDrag$31(java.lang.String spec, android.view.View view) {
        android.content.ClipData data = android.content.ClipData.newPlainText("control_tile", spec);
        return view.startDragAndDrop(data, new android.view.View.DragShadowBuilder(view), view, 768);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void showControlTileShapeDialog(final java.lang.String spec) {
        if (spec == null || this.config == null) {
            return;
        }
        if (com.xtjm.fusionstatusbar.ControlCenterComponentSpec.isSpecial(spec)) {
            showControlComponentShapeDialog(spec);
        } else {
            showSettingsDialog("磁贴形状 · " + tileLabel(spec), new com.xtjm.fusionstatusbar.MainActivity.DialogBuilder() { // from class: com.xtjm.fusionstatusbar.MainActivity.36
                @Override // com.xtjm.fusionstatusbar.MainActivity.DialogBuilder
                public void build(android.widget.LinearLayout linearLayout) {
                    final com.xtjm.fusionstatusbar.ControlCenterConfig settings = com.xtjm.fusionstatusbar.MainActivity.this.controlCenterSettings();
                    com.xtjm.fusionstatusbar.MainActivity.this.addHint(linearLayout, "形状会按控制中心网格组合，点击完成后重绘预览并应用到系统界面。");
                    java.lang.String[] options = {"标准 1×1", "横向 2×1", "横向 3×1", "整行 " + settings.columns + "×1", "大块 2×2"};
                    int selected = com.xtjm.fusionstatusbar.MainActivity.this.shapeIndex(settings, spec);
                    com.xtjm.fusionstatusbar.SettingsChoiceSelector shape = new com.xtjm.fusionstatusbar.SettingsChoiceSelector(
                            com.xtjm.fusionstatusbar.MainActivity.this, "磁贴形状", options, selected,
                            com.xtjm.fusionstatusbar.MainActivity.this.inputColor(),
                            com.xtjm.fusionstatusbar.MainActivity.this.textPrimaryColor(),
                            com.xtjm.fusionstatusbar.MainActivity.this.textSecondaryColor(),
                            com.xtjm.fusionstatusbar.MainActivity.this.accentColor(), position -> {
                            int width;
                            if (position == 0) {
                                width = 1;
                            } else if (position == 1) {
                                width = 2;
                            } else {
                                width = 3;
                                if (position != 2) {
                                    width = position == 3 ? settings.columns : 2;
                                }
                            }
                            int height = position == 4 ? 2 : 1;
                            com.xtjm.fusionstatusbar.MainActivity.this.updateControlCenterDraft(com.xtjm.fusionstatusbar.MainActivity.this.controlCenterSettings().withTileShape(spec, width, height));
                        });
                    linearLayout.addView(shape, com.xtjm.fusionstatusbar.MainActivity.this.matchWrap());
                }
            });
        }
    }

    private void showControlComponentShapeDialog(final java.lang.String spec) {
        showSettingsDialog("组件形状 · " + tileLabel(spec), new com.xtjm.fusionstatusbar.MainActivity.DialogBuilder() { // from class: com.xtjm.fusionstatusbar.MainActivity$$ExternalSyntheticLambda4
            @Override // com.xtjm.fusionstatusbar.MainActivity.DialogBuilder
            public final void build(android.widget.LinearLayout linearLayout) {
                MainActivity.this.lambda$showControlComponentShapeDialog$39(spec, linearLayout);
            }
        });
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$showControlComponentShapeDialog$39(final java.lang.String spec, android.widget.LinearLayout linearLayout) {
        int iCapturedComponentSpan;
        int iCapturedComponentRows;
        com.xtjm.fusionstatusbar.ControlCenterConfig settings = controlCenterSettings();
        android.widget.LinearLayout placement = new android.widget.LinearLayout(this);
        placement.setOrientation(LinearLayout.HORIZONTAL);
        final android.widget.Button left = new android.widget.Button(this);
        left.setText("左侧");
        left.setAllCaps(false);
        left.setAlpha("left".equals(settings.componentSide(spec)) ? 1.0f : 0.56f);
        placement.addView(left, new android.widget.LinearLayout.LayoutParams(0, dp(44), 1.0f));
        final android.widget.Button right = new android.widget.Button(this);
        right.setText("右侧");
        right.setAllCaps(false);
        right.setAlpha("right".equals(settings.componentSide(spec)) ? 1.0f : 0.56f);
        left.setOnClickListener(new android.view.View.OnClickListener() { // from class: com.xtjm.fusionstatusbar.MainActivity$$ExternalSyntheticLambda38
            @Override // android.view.View.OnClickListener
            public final void onClick(android.view.View view) {
                MainActivity.this.lambda$showControlComponentShapeDialog$32(spec, left, right, view);
            }
        });
        right.setOnClickListener(new android.view.View.OnClickListener() { // from class: com.xtjm.fusionstatusbar.MainActivity$$ExternalSyntheticLambda39
            @Override // android.view.View.OnClickListener
            public final void onClick(android.view.View view) {
                MainActivity.this.lambda$showControlComponentShapeDialog$33(spec, right, left, view);
            }
        });
        placement.addView(right, new android.widget.LinearLayout.LayoutParams(0, dp(44), 1.0f));
        linearLayout.addView(placement, matchWrap());
        android.widget.LinearLayout ordering = new android.widget.LinearLayout(this);
        ordering.setOrientation(LinearLayout.HORIZONTAL);
        android.widget.Button earlier = new android.widget.Button(this);
        earlier.setText("上移");
        earlier.setAllCaps(false);
        earlier.setOnClickListener(new android.view.View.OnClickListener() { // from class: com.xtjm.fusionstatusbar.MainActivity$$ExternalSyntheticLambda40
            @Override // android.view.View.OnClickListener
            public final void onClick(android.view.View view) {
                MainActivity.this.lambda$showControlComponentShapeDialog$34(spec, view);
            }
        });
        ordering.addView(earlier, new android.widget.LinearLayout.LayoutParams(0, dp(44), 1.0f));
        android.widget.Button later = new android.widget.Button(this);
        later.setText("下移");
        later.setAllCaps(false);
        later.setOnClickListener(new android.view.View.OnClickListener() { // from class: com.xtjm.fusionstatusbar.MainActivity$$ExternalSyntheticLambda41
            @Override // android.view.View.OnClickListener
            public final void onClick(android.view.View view) {
                MainActivity.this.lambda$showControlComponentShapeDialog$35(spec, view);
            }
        });
        ordering.addView(later, new android.widget.LinearLayout.LayoutParams(0, dp(44), 1.0f));
        linearLayout.addView(ordering, matchWrap());
        boolean customized = com.xtjm.fusionstatusbar.ControlCenterTileLayout.hasShape(settings.layout, spec);
        int i = settings.columns;
        if (this.controlCenterActualPreview == null) {
            iCapturedComponentSpan = com.xtjm.fusionstatusbar.ControlCenterComponentSpec.defaultSpan(spec);
        } else {
            iCapturedComponentSpan = this.controlCenterActualPreview.capturedComponentSpan(spec);
        }
        final int nativeWidth = java.lang.Math.max(1, java.lang.Math.min(i, iCapturedComponentSpan));
        if (this.controlCenterActualPreview == null) {
            iCapturedComponentRows = com.xtjm.fusionstatusbar.ControlCenterComponentSpec.defaultRows(spec);
        } else {
            iCapturedComponentRows = this.controlCenterActualPreview.capturedComponentRows(spec);
        }
        int nativeRows = iCapturedComponentRows;
        int width = customized ? settings.tileWidth(spec) : nativeWidth;
        int height = customized ? settings.tileHeight(spec) : nativeRows;
        addHint(linearLayout, "横向和纵向分别调整组件占用的网格；恢复原始尺寸可撤销本组件修改。");
        addSlider(linearLayout, "横向宽度", "占用的网格列数", 1, settings.columns, width, " 列", new com.xtjm.fusionstatusbar.MainActivity.IntegerUpdate() { // from class: com.xtjm.fusionstatusbar.MainActivity$$ExternalSyntheticLambda42
            @Override // com.xtjm.fusionstatusbar.MainActivity.IntegerUpdate
            public final void apply(int i2) {
                MainActivity.this.lambda$showControlComponentShapeDialog$36(spec, i2);
            }
        });
        addSlider(linearLayout, "纵向高度", "使用系统控制中心的行高档位", 1, 4, height, " 行", new com.xtjm.fusionstatusbar.MainActivity.IntegerUpdate() { // from class: com.xtjm.fusionstatusbar.MainActivity$$ExternalSyntheticLambda1
            @Override // com.xtjm.fusionstatusbar.MainActivity.IntegerUpdate
            public final void apply(int i2) {
                MainActivity.this.lambda$showControlComponentShapeDialog$37(spec, nativeWidth, i2);
            }
        });
        android.widget.Button reset = new android.widget.Button(this);
        reset.setText("恢复系统原始尺寸");
        reset.setAllCaps(false);
        reset.setOnClickListener(new android.view.View.OnClickListener() { // from class: com.xtjm.fusionstatusbar.MainActivity$$ExternalSyntheticLambda2
            @Override // android.view.View.OnClickListener
            public final void onClick(android.view.View view) {
                MainActivity.this.lambda$showControlComponentShapeDialog$38(spec, view);
            }
        });
        android.widget.LinearLayout.LayoutParams resetParams = new android.widget.LinearLayout.LayoutParams(-1, dp(46));
        resetParams.topMargin = dp(8);
        linearLayout.addView(reset, resetParams);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$showControlComponentShapeDialog$32(java.lang.String spec, android.widget.Button left, android.widget.Button right, android.view.View view) {
        updateControlCenterDraft(controlCenterSettings().withComponentSide(spec, "left"));
        left.setAlpha(1.0f);
        right.setAlpha(0.56f);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$showControlComponentShapeDialog$33(java.lang.String spec, android.widget.Button right, android.widget.Button left, android.view.View view) {
        updateControlCenterDraft(controlCenterSettings().withComponentSide(spec, "right"));
        right.setAlpha(1.0f);
        left.setAlpha(0.56f);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$showControlComponentShapeDialog$34(java.lang.String spec, android.view.View view) {
        moveControlComponentInOrder(spec, -1);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$showControlComponentShapeDialog$35(java.lang.String spec, android.view.View view) {
        moveControlComponentInOrder(spec, 1);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$showControlComponentShapeDialog$36(java.lang.String spec, int value) {
        com.xtjm.fusionstatusbar.ControlCenterConfig current = controlCenterSettings();
        int currentHeight = current.tileHeight(spec);
        updateControlCenterDraft(current.withTileShape(spec, value, currentHeight));
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$showControlComponentShapeDialog$37(java.lang.String spec, int nativeWidth, int value) {
        com.xtjm.fusionstatusbar.ControlCenterConfig current = controlCenterSettings();
        int currentWidth = current.tileWidth(spec);
        if (!com.xtjm.fusionstatusbar.ControlCenterTileLayout.hasShape(current.layout, spec)) {
            currentWidth = nativeWidth;
        }
        updateControlCenterDraft(current.withTileShape(spec, currentWidth, value));
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$showControlComponentShapeDialog$38(java.lang.String spec, android.view.View view) {
        updateControlCenterDraft(controlCenterSettings().withoutTileShape(spec));
    }

    private void moveControlComponentInOrder(java.lang.String spec, int delta) {
        java.util.LinkedHashSet<java.lang.String> known = new java.util.LinkedHashSet<>();
        java.lang.String configured = controlCenterSettings().componentOrder;
        if (!configured.isEmpty()) {
            known.addAll(java.util.Arrays.asList(configured.split(",")));
        }
        if (this.controlCenterActualPreview != null) {
            known.addAll(this.controlCenterActualPreview.capturedComponents());
        }
        known.add(spec);
        java.util.ArrayList<java.lang.String> order = new java.util.ArrayList<>(known);
        int index = order.indexOf(spec);
        int target = index + delta;
        if (index < 0 || target < 0 || target >= order.size()) {
            return;
        }
        java.util.Collections.swap(order, index, target);
        updateControlCenterDraft(controlCenterSettings().withComponentOrder(java.lang.String.join(",", order)));
    }

    /* JADX INFO: Access modifiers changed from: private */
    public int shapeIndex(com.xtjm.fusionstatusbar.ControlCenterConfig settings, java.lang.String spec) {
        int width = settings.tileWidth(spec);
        int height = settings.tileHeight(spec);
        if (height == 2 && width == 2) {
            return 4;
        }
        if (width == settings.columns) {
            return 3;
        }
        if (width == 3) {
            return 2;
        }
        return width == 2 ? 1 : 0;
    }

    /* JADX INFO: Access modifiers changed from: private */
    public boolean onControlCenterTileDrag(android.view.View target, android.view.DragEvent event) {
        switch (event.getAction()) {
            case com.xtjm.fusionstatusbar.FusionConfig.SIDE_RIGHT /* 1 */:
                return event.getClipDescription() != null && event.getClipDescription().hasMimeType("text/plain");
            case 2:
            default:
                return true;
            case 3:
                target.setAlpha(1.0f);
                java.lang.String source = controlTileSpec(event);
                java.lang.String destination = target.getTag() instanceof java.lang.String ? (java.lang.String) target.getTag() : null;
                if (source == null || destination == null || source.equals(destination)) {
                    return false;
                }
                if (com.xtjm.fusionstatusbar.ControlCenterComponentSpec.isSpecial(source) || com.xtjm.fusionstatusbar.ControlCenterComponentSpec.isSpecial(destination)) {
                    if (com.xtjm.fusionstatusbar.ControlCenterComponentSpec.isSpecial(source) && com.xtjm.fusionstatusbar.ControlCenterComponentSpec.isSpecial(destination)) {
                        moveControlComponent(source, destination);
                    }
                    return true;
                }
                if (isControlTileActive(destination)) {
                    if (isControlTileActive(source)) {
                        moveControlTile(source, destination);
                    } else {
                        addControlTileBefore(source, destination);
                    }
                } else if (isControlTileActive(source)) {
                    hideControlTile(source);
                }
                return true;
            case 4:
            case 6:
                target.setAlpha(1.0f);
                return true;
            case 5:
                target.setAlpha(0.55f);
                return true;
        }
    }

    private boolean onControlCenterAreaDrag(android.view.View target, android.view.DragEvent event) {
        switch (event.getAction()) {
            case com.xtjm.fusionstatusbar.FusionConfig.SIDE_RIGHT /* 1 */:
                return event.getClipDescription() != null && event.getClipDescription().hasMimeType("text/plain");
            case 2:
            default:
                return true;
            case 3:
                java.lang.String source = controlTileSpec(event);
                if (source == null) {
                    return false;
                }
                if (target == this.controlCenterActivePreview) {
                    if (isControlTileActive(source)) {
                        moveControlTileToEnd(source);
                    } else {
                        addControlTileBefore(source, null);
                    }
                } else if (target == this.controlCenterAvailablePreview && isControlTileActive(source)) {
                    hideControlTile(source);
                }
                return true;
        }
    }

    private java.lang.String controlTileSpec(android.view.DragEvent event) {
        java.lang.Object localState = event.getLocalState();
        if (localState instanceof android.view.View) {
            android.view.View view = (android.view.View) localState;
            if (view.getTag() instanceof java.lang.String) {
                return (java.lang.String) view.getTag();
            }
        }
        android.content.ClipData data = event.getClipData();
        if (data != null && data.getItemCount() > 0) {
            return data.getItemAt(0).coerceToText(this).toString();
        }
        return null;
    }

    private boolean isControlTileActive(java.lang.String spec) {
        return (spec == null || !editableControlCenterOrder().contains(spec.toLowerCase(java.util.Locale.ROOT)) || controlCenterSettings().isHidden(spec)) ? false : true;
    }

    private java.util.ArrayList<java.lang.String> editableControlCenterOrder() {
        java.util.ArrayList<java.lang.String> configured;
        com.xtjm.fusionstatusbar.ControlCenterConfig settings = controlCenterSettings();
        java.util.ArrayList<java.lang.String> captured = new java.util.ArrayList<>(this.controlCenterCapturedSpecs);
        if (settings.order.isEmpty()) {
            configured = new java.util.ArrayList<>();
        } else {
            configured = new java.util.ArrayList<>(settings.orderedSpecs());
        }
        return new java.util.ArrayList<>(com.xtjm.fusionstatusbar.ControlCenterPreviewDraft.visibleSpecs(captured, configured, settings));
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void moveControlTile(java.lang.String source, java.lang.String destination) {
        if (source == null || destination == null || source.equals(destination)) {
            return;
        }
        java.lang.String source2 = source.toLowerCase(java.util.Locale.ROOT);
        java.lang.String destination2 = destination.toLowerCase(java.util.Locale.ROOT);
        java.util.ArrayList<java.lang.String> order = editableControlCenterOrder();
        if (!order.contains(source2)) {
            order.add(source2);
        }
        order.remove(source2);
        int targetIndex = order.indexOf(destination2);
        if (targetIndex < 0) {
            order.add(source2);
        } else {
            order.add(targetIndex, source2);
        }
        updateControlCenterDraft(controlCenterSettings().withOrder(java.lang.String.join(",", order)));
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void moveControlComponent(java.lang.String source, java.lang.String destination) {
        if (!com.xtjm.fusionstatusbar.ControlCenterComponentSpec.isSpecial(source) || source.equals(destination) || this.controlCenterActualPreview == null) {
            return;
        }
        boolean right = destination != null && this.controlCenterActualPreview.isRightOf(destination);
        java.lang.String target = com.xtjm.fusionstatusbar.ControlCenterComponentSpec.isSpecial(destination) ? destination : null;
        moveControlComponent(source, target, right);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void moveControlComponentToPosition(java.lang.String source, float x, float y) {
        if (!com.xtjm.fusionstatusbar.ControlCenterComponentSpec.isSpecial(source) || this.controlCenterActualPreview == null) {
            return;
        }
        java.lang.String destination = this.controlCenterActualPreview.componentDropTarget(source, x, y);
        boolean right = this.controlCenterActualPreview.isRightPosition(x);
        if (destination != null) {
            right = this.controlCenterActualPreview.isRightOf(destination);
        }
        moveControlComponent(source, destination, right);
    }

    private void moveControlComponent(java.lang.String source, java.lang.String destination, boolean right) {
        if (!com.xtjm.fusionstatusbar.ControlCenterComponentSpec.isSpecial(source) || source.equals(destination) || this.controlCenterActualPreview == null) {
            return;
        }
        java.util.LinkedHashSet<java.lang.String> components = new java.util.LinkedHashSet<>();
        java.lang.String configured = controlCenterSettings().componentOrder;
        if (!configured.isEmpty()) {
            components.addAll(java.util.Arrays.asList(configured.split(",")));
        }
        components.addAll(this.controlCenterActualPreview.capturedComponents());
        java.util.ArrayList<java.lang.String> order = new java.util.ArrayList<>(components);
        java.lang.String source2 = source.toLowerCase(java.util.Locale.ROOT);
        if (destination != null) {
            destination = destination.toLowerCase(java.util.Locale.ROOT);
        }
        order.remove(source2);
        int destinationIndex = destination == null ? -1 : order.indexOf(destination);
        if (destinationIndex < 0) {
            order.add(source2);
        } else {
            order.add(destinationIndex, source2);
        }
        com.xtjm.fusionstatusbar.ControlCenterConfig settings = controlCenterSettings().withComponentOrder(java.lang.String.join(",", order)).withComponentSide(source2, right ? "right" : "left");
        updateControlCenterDraft(settings);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void moveControlTileToEnd(java.lang.String source) {
        if (source == null) {
            return;
        }
        java.lang.String source2 = source.toLowerCase(java.util.Locale.ROOT);
        java.util.ArrayList<java.lang.String> order = editableControlCenterOrder();
        if (order.remove(source2)) {
            order.add(source2);
            updateControlCenterDraft(controlCenterSettings().withOrder(java.lang.String.join(",", order)));
        }
    }

    private void addControlTileBefore(java.lang.String source, java.lang.String destination) {
        int targetIndex;
        if (source == null) {
            return;
        }
        java.lang.String source2 = source.toLowerCase(java.util.Locale.ROOT);
        if (destination != null) {
            destination = destination.toLowerCase(java.util.Locale.ROOT);
        }
        com.xtjm.fusionstatusbar.ControlCenterConfig settings = updateHiddenTile(controlCenterSettings(), source2, false);
        java.util.ArrayList<java.lang.String> order = editableControlCenterOrder();
        order.remove(source2);
        if (destination != null && (targetIndex = order.indexOf(destination)) >= 0) {
            order.add(targetIndex, source2);
        } else {
            order.add(source2);
        }
        updateControlCenterDraft(settings.withOrder(java.lang.String.join(",", order)));
    }

    private void hideControlTile(java.lang.String source) {
        updateControlCenterDraft(updateHiddenTile(controlCenterSettings(), source, true));
    }

    private void focusControlCenterPreview() {
        if (this.controlCenterGridPanel == null) {
            return;
        }
        this.controlCenterGridPanel.setFocusable(true);
        this.controlCenterGridPanel.requestFocus();
        if (this.pageScroll != null) {
            this.pageScroll.post(new java.lang.Runnable() { // from class: com.xtjm.fusionstatusbar.MainActivity$$ExternalSyntheticLambda27
                @Override // java.lang.Runnable
                public final void run() {
                    MainActivity.this.lambda$focusControlCenterPreview$40();
                }
            });
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$focusControlCenterPreview$40() {
        this.pageScroll.smoothScrollTo(0, this.controlCenterGridPanel.getTop());
    }

    private int tileIndex(java.lang.String spec) {
        for (int i = 0; i < CONTROL_TILE_SPECS.length; i++) {
            if (CONTROL_TILE_SPECS[i].equalsIgnoreCase(spec)) {
                return i;
            }
        }
        return -1;
    }

    private android.widget.TextView addDetail(android.widget.LinearLayout container, java.lang.String title, java.lang.String description) {
        android.widget.LinearLayout row = new android.widget.LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(dp(18), dp(14), dp(18), dp(14));
        row.addView(labelView(title, 14.0f, true), matchWrap());
        android.widget.TextView value = labelView(description, 13.0f, false);
        value.setTextColor(textSecondaryColor());
        row.addView(value, matchWrap());
        container.addView(row, matchWrap());
        return value;
    }

    private void selectPage(int index) {
        android.graphics.drawable.GradientDrawable gradientDrawable;
        if (index < 0 || index >= this.pages.length || this.pages[index] == null) {
            return;
        }
        this.selectedPage = index;
        int i = 0;
        while (i < this.pages.length) {
            this.pages[i].setVisibility(i == index ? View.VISIBLE : View.GONE);
            this.navigationItems[i].setSelected(i == index);
            android.widget.ImageView icon = (android.widget.ImageView) this.navigationItems[i].getChildAt(0);
            android.widget.TextView label = (android.widget.TextView) this.navigationItems[i].getChildAt(1);
            int color = i == index ? accentColor() : textSecondaryColor();
            icon.setColorFilter(color);
            label.setTextColor(color);
            android.widget.LinearLayout linearLayout = this.navigationItems[i];
            if (i == index) {
                gradientDrawable = rounded(android.graphics.Color.parseColor(isNightMode() ? "#FF263343" : "#FFEAF2FF"), dp(28));
            } else {
                gradientDrawable = null;
            }
            linearLayout.setBackground(gradientDrawable);
            i++;
        }
        this.pageScroll.post(new java.lang.Runnable() { // from class: com.xtjm.fusionstatusbar.MainActivity.37
            @Override // java.lang.Runnable
            public final void run() {
                com.xtjm.fusionstatusbar.MainActivity.this.lambda$selectPage$24();
            }
        });
        if (index == 0 || index == 4) {
            refreshOverview();
        }
    }

    public void lambda$selectPage$24() {
        this.pageScroll.scrollTo(0, 0);
    }

    public void refreshOverview() {
        java.lang.String str;
        if (this.activationSummary != null) {
            boolean loaded = com.xtjm.fusionstatusbar.FusionActivationStatus.isLoadedThisBoot(this);
            this.activationTitle.setText(loaded ? "已激活" : "未检测到激活");
            long reportedAt = com.xtjm.fusionstatusbar.FusionActivationStatus.lastReportTime(this);
            android.widget.TextView textView = this.activationSummary;
            if (loaded) {
                str = "系统界面已连接" + (reportedAt > 0 ? " · " + new java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(new java.util.Date(reportedAt)) : "");
            } else {
                str = "本次开机尚未收到系统界面连接记录";
            }
            textView.setText(str);
            this.activationIndicator.setBackground(rounded(android.graphics.Color.parseColor(loaded ? "#FF228663" : "#FFC48733"), dp(6)));
        }
        if (this.phonePermissionSummary != null) {
            this.phonePermissionSummary.setText(checkSelfPermission("android.permission.READ_PHONE_STATE") == PackageManager.PERMISSION_GRANTED ? "已授权" : "未授权 · 点击请求");
        }
    }

    private void requestPhonePermission() {
        if (checkSelfPermission("android.permission.READ_PHONE_STATE") != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new java.lang.String[]{"android.permission.READ_PHONE_STATE"}, REQUEST_PHONE_PERMISSION);
        } else {
            openAppPermissions();
        }
    }

    private void openRepositoryUrl(String url) {
        try {
            startActivity(new android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url)));
        } catch (android.content.ActivityNotFoundException e) {
            Toast.makeText(this, R.string.about_link_unavailable, Toast.LENGTH_SHORT).show();
        }
    }

    private void openAppPermissions() {
        try {
            startActivity(new android.content.Intent("android.settings.APPLICATION_DETAILS_SETTINGS", android.net.Uri.parse("package:" + getPackageName())));
        } catch (java.lang.RuntimeException e) {
            android.widget.Toast.makeText(this, "无法打开权限设置", Toast.LENGTH_SHORT).show();
        }
    }

    @Override // android.app.Activity
    public void onRequestPermissionsResult(int requestCode, java.lang.String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_PHONE_PERMISSION) {
            refreshOverview();
        }
    }

    private SettingsPageController settingsPages() {
        if (settingsDialogs == null) settingsDialogs = new SettingsDialogController(this);
        if (settingsPages == null) settingsPages = new SettingsPageController(this,
                new SettingsPageController.Model() {
                    @Override public FusionConfig current() { return config; }
                    @Override public void update(FusionConfig next) { MainActivity.this.update(next); }
                }, settingsDialogs);
        return settingsPages;
    }

    private void showFusionDialog() { settingsPages().showFusion(); }
    private void showClockDialog() { settingsPages().showClock(); }
    private void showNotificationClockDialog() { settingsPages().showNotificationClock(); }
    private void showNotificationDateDialog() { settingsPages().showNotificationDate(); }
    private void showNotificationListDialog() { settingsPages().showNotificationList(); }

    private void showTelemetryDialog() { settingsPages().showTelemetry(); }
    private void showLayoutDialog() { settingsPages().showLayout(); }

    private void showSettingsDialog(java.lang.String title, com.xtjm.fusionstatusbar.MainActivity.DialogBuilder builder) {
        if (settingsDialogs == null) settingsDialogs = new SettingsDialogController(this);
        settingsDialogs.show(title, builder::build, surfaceColor(), textPrimaryColor(), accentColor());
    }

    /* JADX INFO: Access modifiers changed from: private */
    public android.widget.Switch addSwitch(android.widget.LinearLayout linearLayout, java.lang.String title, java.lang.String summary, boolean checked, final com.xtjm.fusionstatusbar.MainActivity.BooleanUpdate update) {
        android.widget.LinearLayout row = new android.widget.LinearLayout(this);
        row.setGravity(16);
        row.setMinimumHeight(dp(58));
        android.widget.LinearLayout texts = new android.widget.LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        texts.setGravity(16);
        texts.addView(labelView(title, 16.0f, false), matchWrap());
        android.widget.TextView hint = labelView(summary, 12.0f, false);
        hint.setTextColor(textSecondaryColor());
        texts.addView(hint, matchWrap());
        row.addView(texts, new android.widget.LinearLayout.LayoutParams(0, dp(58), 1.0f));
        android.widget.Switch toggle = new android.widget.Switch(this);
        toggle.setChecked(checked);
        row.addView(toggle, new android.widget.LinearLayout.LayoutParams(-2, -2));
        linearLayout.addView(row, matchWrap());
        toggle.setOnCheckedChangeListener(new android.widget.CompoundButton.OnCheckedChangeListener() { // from class: com.xtjm.fusionstatusbar.MainActivity.103
            @Override // android.widget.CompoundButton.OnCheckedChangeListener
            public final void onCheckedChanged(android.widget.CompoundButton compoundButton, boolean z) {
                com.xtjm.fusionstatusbar.MainActivity.this.lambda$addSwitch$80(update, compoundButton, z);
            }
        });
        return toggle;
    }

    public void lambda$addSwitch$80(com.xtjm.fusionstatusbar.MainActivity.BooleanUpdate update, android.widget.CompoundButton button, boolean value) {
        if (!this.updating) {
            update.apply(value);
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public android.view.View addSlider(android.widget.LinearLayout linearLayout, java.lang.String title, java.lang.String summary, final int minimum, int maximum, int value, final java.lang.String suffix, final com.xtjm.fusionstatusbar.MainActivity.IntegerUpdate update) {
        android.widget.LinearLayout row = new android.widget.LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(0, dp(8), 0, dp(4));
        android.widget.LinearLayout heading = new android.widget.LinearLayout(this);
        heading.setGravity(16);
        heading.addView(labelView(title, 16.0f, false), new android.widget.LinearLayout.LayoutParams(0, -2, 1.0f));
        final android.widget.TextView valueLabel = labelView(value + suffix, 13.0f, false);
        valueLabel.setTextColor(accentColor());
        heading.addView(valueLabel, new android.widget.LinearLayout.LayoutParams(-2, -2));
        row.addView(heading, matchWrap());
        android.widget.TextView hint = labelView(summary, 12.0f, false);
        hint.setTextColor(textSecondaryColor());
        row.addView(hint, matchWrap());
        android.widget.SeekBar seek = new android.widget.SeekBar(this);
        seek.setMax(java.lang.Math.max(1, maximum - minimum));
        seek.setProgress(java.lang.Math.max(0, java.lang.Math.min(seek.getMax(), value - minimum)));
        android.widget.LinearLayout.LayoutParams seekParams = matchWrap();
        seekParams.topMargin = dp(2);
        row.addView(seek, seekParams);
        linearLayout.addView(row, matchWrap());
        seek.setOnSeekBarChangeListener(new android.widget.SeekBar.OnSeekBarChangeListener() { // from class: com.xtjm.fusionstatusbar.MainActivity.104
            @Override // android.widget.SeekBar.OnSeekBarChangeListener
            public void onProgressChanged(android.widget.SeekBar bar, int progress, boolean fromUser) {
                int selected = minimum + progress;
                valueLabel.setText(selected + suffix);
                if (fromUser && !com.xtjm.fusionstatusbar.MainActivity.this.updating) {
                    update.apply(selected);
                }
            }

            @Override // android.widget.SeekBar.OnSeekBarChangeListener
            public void onStartTrackingTouch(android.widget.SeekBar bar) {
            }

            @Override // android.widget.SeekBar.OnSeekBarChangeListener
            public void onStopTrackingTouch(android.widget.SeekBar bar) {
                com.xtjm.fusionstatusbar.MainActivity.this.flushSettingsSave();
            }
        });
        return row;
    }


    /* JADX INFO: Access modifiers changed from: private */
    public void addSectionLabel(android.widget.LinearLayout content, java.lang.String title) {
        android.widget.TextView label = labelView(title, 13.0f, true);
        label.setTextColor(accentColor());
        android.widget.LinearLayout.LayoutParams params = matchWrap();
        params.topMargin = dp(18);
        params.bottomMargin = dp(4);
        content.addView(label, params);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void addHint(android.widget.LinearLayout content, java.lang.String text) {
        android.widget.TextView hint = hintView(text);
        android.widget.LinearLayout.LayoutParams params = matchWrap();
        params.topMargin = dp(8);
        content.addView(hint, params);
    }

    private android.widget.TextView hintView(java.lang.String text) {
        android.widget.TextView view = labelView(text, 12.0f, false);
        view.setTextColor(textSecondaryColor());
        view.setLineSpacing(dp(2), 1.0f);
        return view;
    }

    private android.widget.TextView labelView(java.lang.String str, float f, boolean z) {
        android.widget.TextView textView = new android.widget.TextView(this);
        textView.setText(str);
        textView.setTextSize(f);
        textView.setTextColor(textPrimaryColor());
        textView.setTypeface(android.graphics.Typeface.DEFAULT, z ? Typeface.BOLD : Typeface.NORMAL);
        return textView;
    }

    private android.widget.LinearLayout card() {
        android.widget.LinearLayout card = new android.widget.LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(rounded(surfaceColor(), dp(18)));
        card.setElevation(dp(1));
        return card;
    }

    private android.widget.LinearLayout addPreference(android.widget.LinearLayout linearLayout, java.lang.String title, java.lang.String summary, android.view.View.OnClickListener listener) {
        android.widget.LinearLayout row = new android.widget.LinearLayout(this);
        row.setGravity(16);
        row.setMinimumHeight(dp(70));
        row.setPaddingRelative(dp(18), dp(8), dp(12), dp(8));
        android.widget.LinearLayout texts = new android.widget.LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        texts.setGravity(16);
        texts.addView(labelView(title, 16.0f, false), matchWrap());
        android.widget.TextView summaryView = labelView(summary, 12.0f, false);
        summaryView.setTextColor(textSecondaryColor());
        texts.addView(summaryView, matchWrap());
        row.addView(texts, new android.widget.LinearLayout.LayoutParams(0, dp(54), 1.0f));
        android.widget.TextView arrow = labelView("›", 28.0f, false);
        arrow.setTextColor(textSecondaryColor());
        arrow.setGravity(17);
        row.addView(arrow, new android.widget.LinearLayout.LayoutParams(dp(28), dp(54)));
        row.setContentDescription(title);
        row.setOnClickListener(listener);
        row.setTag(summaryView);
        linearLayout.addView(row, matchWrap());
        return row;
    }

    private void addDivider(android.widget.LinearLayout card) {
        android.view.View divider = new android.view.View(this);
        divider.setBackgroundColor(android.graphics.Color.parseColor(isNightMode() ? "#303034" : "#E9E9ED"));
        android.widget.LinearLayout.LayoutParams params = new android.widget.LinearLayout.LayoutParams(-1, 1);
        params.leftMargin = dp(18);
        params.rightMargin = dp(18);
        card.addView(divider, params);
    }

    private android.widget.TextView sectionTitle(java.lang.String text) {
        android.widget.TextView title = labelView(text, 14.0f, true);
        title.setTextColor(textSecondaryColor());
        title.setAllCaps(false);
        android.widget.LinearLayout.LayoutParams params = matchWrap();
        params.topMargin = dp(26);
        params.bottomMargin = dp(8);
        title.setLayoutParams(params);
        return title;
    }

    /* JADX INFO: Access modifiers changed from: private */
    public android.widget.LinearLayout.LayoutParams matchWrap() {
        return new android.widget.LinearLayout.LayoutParams(-1, -2);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public android.graphics.drawable.GradientDrawable rounded(int color, int radius) {
        android.graphics.drawable.GradientDrawable drawable = new android.graphics.drawable.GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(radius);
        return drawable;
    }

    private int pageColor() {
        if (isNightMode()) {
            return -16777216;
        }
        return android.graphics.Color.parseColor("#FFF5F5F7");
    }

    private int surfaceColor() {
        if (isNightMode()) {
            return android.graphics.Color.parseColor("#FF1C1C1E");
        }
        return -1;
    }

    /* JADX INFO: Access modifiers changed from: private */
    public int inputColor() {
        return android.graphics.Color.parseColor(isNightMode() ? "#FF2C2C2E" : "#FFF0F0F2");
    }

    /* JADX INFO: Access modifiers changed from: private */
    public int textPrimaryColor() {
        return android.graphics.Color.parseColor(isNightMode() ? "#FFF5F5F7" : "#FF202124");
    }

    /* JADX INFO: Access modifiers changed from: private */
    public int textSecondaryColor() {
        return android.graphics.Color.parseColor(isNightMode() ? "#FFA7A7AD" : "#FF77777D");
    }

    private int accentColor() {
        return android.graphics.Color.parseColor(isNightMode() ? "#FF72A7FF" : "#FF3482FF");
    }

    private boolean isNightMode() {
        return (getResources().getConfiguration().uiMode & 48) == 32;
    }

    private String controlCenterApplicationSummary() {
        return getString(editorPage.applicationMessage(FusionActivationStatus.read(this)));
    }

    private void updateSettingsSummaries() {
        java.lang.String value;
        java.lang.String summary;
        if (this.fusionSettingsSummary == null || this.config == null) {
            return;
        }
        this.fusionSettingsSummary.setText("大小 " + this.config.iconScale + "% · 粗细 " + this.config.strokeScale + "% · " + (this.config.wifiIcon ? "显示 Wi-Fi" : "仅移动网络"));
        this.layoutSettingsSummary.setText((this.config.doubleRow ? "双排" : "单排") + " · 融合" + shortPosition(this.config.fusionSide, this.config.fusionRow) + " · 系统" + shortPosition(this.config.systemSide, this.config.systemRow) + " · 高度 " + this.config.statusBarHeight + " dp");
        this.clockSettingsSummary.setText((this.config.customClock ? "自定义格式" : "系统时间") + (this.config.showWeather ? " · 显示天气" : ""));
        int enabled = 0;
        for (int metric = 0; metric < 3; metric++) {
            if (this.config.telemetry.enabled(metric)) {
                enabled++;
            }
        }
        this.telemetrySettingsSummary.setText(enabled + " 项启用 · 可分别调整字号、对齐和间距");
        if (this.controlCenterSummary != null) {
            ControlCenterConfig control = controlCenterSettings();
            ControlCenterLayoutPlan.Mode mode = currentControlGridPlan().mode(controlCenterCompactMode);
            String modeName = controlCenterCompactMode ? "紧凑布局" : "标准布局";
            int hidden = 0;
            for (ControlCenterLayoutPlan.Item item : mode.items) {
                if (item.hidden || control.isHidden(item.firstSpec)) hidden++;
            }
            boolean pending = hasPendingControlCenterDraft();
            String applicationSummary = controlCenterApplicationSummary();
            boolean retryApplication = !pending && editorPage.canRetryApplication();
            controlCenterSummary.setText(mode.columns + " 列 · 内容 " + control.tileScale
                    + "% · 圆角 " + control.cornerRadius + " dp · 间距 " + control.spacing + " dp");
            controlCenterCanvasSummary.setText(modeName + " · " + mode.columns + " 列 · "
                    + (mode.items.size() - hidden) + " 项显示"
                    + (hidden > 0 ? " · " + hidden + " 项隐藏" : ""));
            controlCenterPublishHint.setText(controlCenterSaving ? getString(R.string.editor_saving)
                    : controlCenterSaveFailed ? getString(R.string.editor_save_failed)
                    : pending && !control.enabled
                    ? "自定义已关闭；推送后使用原生布局。开启开关可应用画布设置。"
                    : pending ? "有待推送的修改 · 将应用当前的" + modeName
                            : controlCenterSavedRevision > 0 ? applicationSummary
                            : "设置已保存 · 编辑后推送到手机");
            controlCenterDraftHint.setVisibility(editorPage.draftFailed() ? View.VISIBLE : View.GONE);
            controlCenterDraftHint.setText(editorPage.draftRetrying()
                    ? R.string.editor_draft_retrying : R.string.editor_draft_failed);
            controlCenterDraftRetryButton.setVisibility(editorPage.draftFailed() && !editorPage.draftRetrying()
                    ? View.VISIBLE : View.GONE);
            boolean previousUpdating = updating;
            updating = true;
            controlCenterEnabledSwitch.setChecked(control.enabled);
            updating = previousUpdating;
            controlCenterUndoButton.setEnabled(editorSession.canUndo());
            controlCenterUndoButton.setAlpha(controlCenterUndoButton.isEnabled() ? 1f : 0.45f);
            controlCenterRedoButton.setEnabled(editorSession.canRedo());
            controlCenterRedoButton.setAlpha(controlCenterRedoButton.isEnabled() ? 1f : 0.45f);
            if (this.controlCenterPushButton != null) {
                this.controlCenterPushButton.setText(controlCenterSaving ? getString(R.string.editor_saving)
                        : pending ? "推送修改" : retryApplication ? getString(R.string.editor_apply_retry) : "暂无待推送修改");
                this.controlCenterPushButton.setEnabled((pending || retryApplication) && !controlCenterSaving);
                this.controlCenterPushButton.setAlpha((pending || retryApplication) && !controlCenterSaving ? 1f : 0.5f);
            }
        }
        refreshControlCenterPreview();
        if (this.notificationClockSummary != null) {
            this.notificationClockSummary.setText(this.config.notificationClock.enabled ? "自定义格式 · " + this.config.notificationClock.combinedPattern().replace('\n', ' ') : "使用系统时间与日期");
        }
        if (this.notificationListSummary != null) {
            this.notificationListSummary.setText("下移 " + this.config.notificationClock.listOffsetYDp + " dp");
        }
        if (this.notificationTimePreview != null) {
            com.xtjm.fusionstatusbar.NotificationClockConfig setting = this.config.notificationClock;
            int previewTimeSp = (!setting.enabled || setting.sizeSp == 0) ? 52 : setting.sizeSp;
            java.lang.String pattern = setting.combinedPattern();
            if (setting.enabled) {
                java.util.Date now = new java.util.Date();
                com.xtjm.fusionstatusbar.ClockTextFormatter.LunarFields lunar = com.xtjm.fusionstatusbar.ClockTextFormatter.needsLunar(pattern) ? com.xtjm.fusionstatusbar.ChineseClockFields.forDate(now) : com.xtjm.fusionstatusbar.ClockTextFormatter.LunarFields.EMPTY;
                value = com.xtjm.fusionstatusbar.ClockTextFormatter.format(now, java.util.Locale.getDefault(), pattern, false, "", lunar);
            } else {
                value = new java.text.SimpleDateFormat("HH:mm\nMM/dd E", java.util.Locale.CHINA).format(new java.util.Date());
            }
            java.lang.String previewText = value == null ? "--:--" : value;
            this.notificationTimePreview.setText(styleNotificationPreview(previewText, setting.dateSizeSp, setting.timeCentered, setting.dateCentered));
            this.notificationTimePreview.setSingleLine(false);
            this.notificationTimePreview.setMaxLines(2);
            this.notificationTimePreview.setGravity(17);
            this.notificationTimePreview.setAutoSizeTextTypeUniformWithConfiguration(18, previewTimeSp, 1, 2);
            this.notificationTimePreview.setTranslationY(dp(setting.enabled ? setting.offsetYDp : 0));
            int timeOffset = setting.enabled ? java.lang.Math.max(0, setting.offsetYDp) : 0;
            android.view.ViewGroup.LayoutParams previewParams = this.notificationPreview.getLayoutParams();
            android.widget.LinearLayout.LayoutParams listParams = (android.widget.LinearLayout.LayoutParams) this.notificationListPreview.getLayoutParams();
            int listMargin = dp(setting.listOffsetYDp);
            if (listParams.topMargin != listMargin) {
                listParams.topMargin = listMargin;
                this.notificationListPreview.setLayoutParams(listParams);
            }
            int lines = pattern.indexOf(10) < 0 ? 1 : 2;
            int textHeight = java.lang.Math.round(previewTimeSp * getResources().getDisplayMetrics().scaledDensity * 1.35f * lines);
            int height2 = java.lang.Math.max(dp(212), dp((timeOffset * 2) + 60) + textHeight) + listMargin;
            if (previewParams.height != height2) {
                previewParams.height = height2;
                this.notificationPreview.setLayoutParams(previewParams);
            }
        }
    }

    private java.lang.String shortPosition(int side, int row) {
        return (side == 0 ? "左" : "右") + (row == 0 ? "上" : "下");
    }

    private java.lang.CharSequence styleNotificationPreview(java.lang.String text, int dateSizeSp, boolean timeCentered, boolean dateCentered) {
        int newline = text.indexOf(10);
        android.text.SpannableString styled = new android.text.SpannableString(text);
        int firstEnd = newline < 0 ? text.length() : newline + 1;
        styled.setSpan(new android.text.style.AlignmentSpan.Standard(timeCentered ? android.text.Layout.Alignment.ALIGN_CENTER : android.text.Layout.Alignment.ALIGN_NORMAL), 0, firstEnd, 33);
        if (newline >= 0 && newline + 1 < text.length()) {
            styled.setSpan(new android.text.style.AlignmentSpan.Standard(dateCentered ? android.text.Layout.Alignment.ALIGN_CENTER : android.text.Layout.Alignment.ALIGN_NORMAL), newline + 1, text.length(), 33);
            int size = dateSizeSp > 0 ? dateSizeSp : 16;
            int pixels = java.lang.Math.max(1, java.lang.Math.round(size * getResources().getDisplayMetrics().scaledDensity));
            styled.setSpan(new android.text.style.AbsoluteSizeSpan(pixels, false), newline + 1, text.length(), 33);
        }
        return styled;
    }

    private void chooseLogDestination() {
        android.content.Intent intent = new android.content.Intent("android.intent.action.CREATE_DOCUMENT");
        intent.addCategory("android.intent.category.OPENABLE");
        intent.setType("application/zip");
        java.lang.String timestamp = new java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.US).format(new java.util.Date());
        intent.putExtra("android.intent.extra.TITLE", "fusion-statusbar-logs-" + timestamp + ".zip");
        try {
            startActivityForResult(intent, REQUEST_EXPORT_LOGS);
        } catch (java.lang.RuntimeException error) {
            android.util.Log.e("FusionStatusBar", "Could not open log export picker", error);
            android.widget.Toast.makeText(this, com.xtjm.fusionstatusbar.R.string.export_logs_save_failed, Toast.LENGTH_LONG).show();
        }
    }

    @Override // android.app.Activity
    protected void onActivityResult(int requestCode, int resultCode, android.content.Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (!settingsLoaded && requestCode != REQUEST_EXPORT_LOGS && startupCoordinator != null
                && startupCoordinator.deferActivityResult(requestCode, resultCode, data)) return;
        if (backupController().onActivityResult(requestCode, resultCode, data)) return;
        if (requestCode != REQUEST_EXPORT_LOGS || resultCode != -1 || data == null || data.getData() == null) {
            return;
        }
        final android.net.Uri destination = data.getData();
        final android.widget.Button button = (android.widget.Button) findViewById(com.xtjm.fusionstatusbar.R.id.export_logs_button);
        button.setEnabled(false);
        button.setText(com.xtjm.fusionstatusbar.R.string.exporting_logs);
        new java.lang.Thread(new java.lang.Runnable() { // from class: com.xtjm.fusionstatusbar.MainActivity.107
            @Override // java.lang.Runnable
            public final void run() {
                com.xtjm.fusionstatusbar.MainActivity.this.lambda$onActivityResult$82(destination, button);
            }
        }, "fusion-export-logs").start();
    }

    public void lambda$onActivityResult$82(android.net.Uri destination, final android.widget.Button button) {
        int result = com.xtjm.fusionstatusbar.R.string.export_logs_success;
        try {
            com.xtjm.fusionstatusbar.DiagnosticLogExporter.export(getApplicationContext(), destination);
        } catch (com.xtjm.fusionstatusbar.DiagnosticLogExporter.NoLogsException error) {
            result = com.xtjm.fusionstatusbar.R.string.export_logs_empty;
            android.util.Log.w("FusionStatusBar", "No matching logs to export", error);
        } catch (com.xtjm.fusionstatusbar.DiagnosticLogExporter.RootUnavailableException error2) {
            result = com.xtjm.fusionstatusbar.R.string.export_logs_root_failed;
            android.util.Log.e("FusionStatusBar", "Root log export failed", error2);
        } catch (java.io.IOException | java.lang.SecurityException error3) {
            result = com.xtjm.fusionstatusbar.R.string.export_logs_save_failed;
            android.util.Log.e("FusionStatusBar", "Could not save diagnostic logs", error3);
        }
        if (result != com.xtjm.fusionstatusbar.R.string.export_logs_success) {
            try {
                android.provider.DocumentsContract.deleteDocument(getContentResolver(), destination);
            } catch (java.lang.Exception e) {
            }
        }
        final int message = result;
        runOnUiThread(new java.lang.Runnable() { // from class: com.xtjm.fusionstatusbar.MainActivity.108
            @Override // java.lang.Runnable
            public final void run() {
                com.xtjm.fusionstatusbar.MainActivity.this.lambda$onActivityResult$81(button, message);
            }
        });
    }

    public void lambda$onActivityResult$81(android.widget.Button button, int message) {
        button.setEnabled(true);
        button.setText(com.xtjm.fusionstatusbar.R.string.export_logs);
        android.widget.Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }

    private void bindScale(int seekId, int valueId, final com.xtjm.fusionstatusbar.MainActivity.ScaleUpdate update) {
        android.widget.SeekBar seek = (android.widget.SeekBar) findViewById(seekId);
        final android.widget.TextView value = (android.widget.TextView) findViewById(valueId);
        seek.setOnSeekBarChangeListener(new android.widget.SeekBar.OnSeekBarChangeListener() { // from class: com.xtjm.fusionstatusbar.MainActivity.109
            @Override // android.widget.SeekBar.OnSeekBarChangeListener
            public void onProgressChanged(android.widget.SeekBar bar, int progress, boolean fromUser) {
                int scale = progress + 50;
                value.setText(scale + "%");
                if (fromUser && !com.xtjm.fusionstatusbar.MainActivity.this.updating) {
                    com.xtjm.fusionstatusbar.MainActivity.this.update(update.apply(scale));
                }
            }

            @Override // android.widget.SeekBar.OnSeekBarChangeListener
            public void onStartTrackingTouch(android.widget.SeekBar bar) {
            }

            @Override // android.widget.SeekBar.OnSeekBarChangeListener
            public void onStopTrackingTouch(android.widget.SeekBar bar) {
            }
        });
    }

    private void bindSpanOffset(int seekId, int valueId, final boolean horizontal) {
        android.widget.SeekBar seek = (android.widget.SeekBar) findViewById(seekId);
        final android.widget.TextView value = (android.widget.TextView) findViewById(valueId);
        seek.setOnSeekBarChangeListener(new android.widget.SeekBar.OnSeekBarChangeListener() { // from class: com.xtjm.fusionstatusbar.MainActivity.110
            @Override // android.widget.SeekBar.OnSeekBarChangeListener
            public void onProgressChanged(android.widget.SeekBar bar, int progress, boolean fromUser) {
                int offset = progress - (bar.getMax() / 2);
                value.setText(com.xtjm.fusionstatusbar.MainActivity.this.spanOffsetLabel(horizontal, offset));
                if (fromUser && !com.xtjm.fusionstatusbar.MainActivity.this.updating) {
                    com.xtjm.fusionstatusbar.MainActivity.this.config = horizontal ? com.xtjm.fusionstatusbar.MainActivity.this.config.withSpanOffsetX(offset) : com.xtjm.fusionstatusbar.MainActivity.this.config.withSpanOffsetY(offset);
                    com.xtjm.fusionstatusbar.MainActivity.this.updatePreviewLayout();
                    com.xtjm.fusionstatusbar.MainActivity.this.scheduleSettingsSave();
                }
            }

            @Override // android.widget.SeekBar.OnSeekBarChangeListener
            public void onStartTrackingTouch(android.widget.SeekBar bar) {
            }

            @Override // android.widget.SeekBar.OnSeekBarChangeListener
            public void onStopTrackingTouch(android.widget.SeekBar bar) {
                com.xtjm.fusionstatusbar.MainActivity.this.flushSettingsSave();
            }
        });
    }

    private void bindTelemetryControls() {
        android.widget.LinearLayout linearLayout = (android.widget.LinearLayout) findViewById(com.xtjm.fusionstatusbar.R.id.telemetry_controls);
        java.lang.String[] positions = getResources().getStringArray(com.xtjm.fusionstatusbar.R.array.telemetry_positions);
        for (int metric = 0; metric < 3; metric++) {
            final int index = metric;
            android.widget.LinearLayout row = new android.widget.LinearLayout(this);
            row.setGravity(16);
            row.setMinimumHeight(dp(48));
            android.widget.Switch toggle = new android.widget.Switch(this);
            toggle.setText(TELEMETRY_TITLES[metric]);
            row.addView(toggle, new android.widget.LinearLayout.LayoutParams(0, -2, 1.0f));
            final SettingsChoiceSelector position = new SettingsChoiceSelector(this,
                    getString(com.xtjm.fusionstatusbar.R.string.telemetry_position_description,
                            new java.lang.Object[]{getString(TELEMETRY_TITLES[metric])}), positions,
                    config.telemetry.position(metric), inputColor(), textPrimaryColor(), textSecondaryColor(),
                    accentColor(), selected -> {
                        if (!MainActivity.this.updating && selected != MainActivity.this.config.telemetry.position(index)) {
                            MainActivity.this.update(MainActivity.this.config.withTelemetry(
                                    MainActivity.this.config.telemetry.withPosition(index, selected)));
                        }
                    });
            row.addView(position, new android.widget.LinearLayout.LayoutParams(0, -2, 1.0f));
            linearLayout.addView(row);
            this.telemetrySwitches[metric] = toggle;
            this.telemetryPositions[metric] = position;
            toggle.setOnCheckedChangeListener(new android.widget.CompoundButton.OnCheckedChangeListener() { // from class: com.xtjm.fusionstatusbar.MainActivity.111
                @Override // android.widget.CompoundButton.OnCheckedChangeListener
                public final void onCheckedChanged(android.widget.CompoundButton compoundButton, boolean z) {
                    com.xtjm.fusionstatusbar.MainActivity.this.lambda$bindTelemetryControls$83(index, compoundButton, z);
                }
            });
        }
        int metric2 = com.xtjm.fusionstatusbar.R.id.dual_speed_switch;
        ((android.widget.Switch) findViewById(metric2)).setOnCheckedChangeListener(new android.widget.CompoundButton.OnCheckedChangeListener() { // from class: com.xtjm.fusionstatusbar.MainActivity.113
            @Override // android.widget.CompoundButton.OnCheckedChangeListener
            public final void onCheckedChanged(android.widget.CompoundButton compoundButton, boolean z) {
                com.xtjm.fusionstatusbar.MainActivity.this.lambda$bindTelemetryControls$84(compoundButton, z);
            }
        });
        ((android.widget.Switch) findViewById(com.xtjm.fusionstatusbar.R.id.current_ma_switch)).setOnCheckedChangeListener(new android.widget.CompoundButton.OnCheckedChangeListener() { // from class: com.xtjm.fusionstatusbar.MainActivity.114
            @Override // android.widget.CompoundButton.OnCheckedChangeListener
            public final void onCheckedChanged(android.widget.CompoundButton compoundButton, boolean z) {
                com.xtjm.fusionstatusbar.MainActivity.this.lambda$bindTelemetryControls$85(compoundButton, z);
            }
        });
        ((android.widget.Switch) findViewById(com.xtjm.fusionstatusbar.R.id.current_positive_switch)).setOnCheckedChangeListener(new android.widget.CompoundButton.OnCheckedChangeListener() { // from class: com.xtjm.fusionstatusbar.MainActivity.115
            @Override // android.widget.CompoundButton.OnCheckedChangeListener
            public final void onCheckedChanged(android.widget.CompoundButton compoundButton, boolean z) {
                com.xtjm.fusionstatusbar.MainActivity.this.lambda$bindTelemetryControls$86(compoundButton, z);
            }
        });
        android.widget.SeekBar textSize = (android.widget.SeekBar) findViewById(com.xtjm.fusionstatusbar.R.id.telemetry_size_seek);
        textSize.setOnSeekBarChangeListener(new android.widget.SeekBar.OnSeekBarChangeListener() { // from class: com.xtjm.fusionstatusbar.MainActivity.116
            @Override // android.widget.SeekBar.OnSeekBarChangeListener
            public void onProgressChanged(android.widget.SeekBar bar, int progress, boolean fromUser) {
                int size = progress + 5;
                ((android.widget.TextView) com.xtjm.fusionstatusbar.MainActivity.this.findViewById(com.xtjm.fusionstatusbar.R.id.telemetry_size_label)).setText(com.xtjm.fusionstatusbar.MainActivity.this.getString(com.xtjm.fusionstatusbar.R.string.telemetry_size_value, new java.lang.Object[]{java.lang.Integer.valueOf(size)}));
                if (fromUser && !com.xtjm.fusionstatusbar.MainActivity.this.updating) {
                    com.xtjm.fusionstatusbar.MainActivity.this.update(com.xtjm.fusionstatusbar.MainActivity.this.config.withTelemetry(com.xtjm.fusionstatusbar.MainActivity.this.config.telemetry.withTextSize(size)));
                }
            }

            @Override // android.widget.SeekBar.OnSeekBarChangeListener
            public void onStartTrackingTouch(android.widget.SeekBar bar) {
            }

            @Override // android.widget.SeekBar.OnSeekBarChangeListener
            public void onStopTrackingTouch(android.widget.SeekBar bar) {
            }
        });
    }

    public void lambda$bindTelemetryControls$83(int index, android.widget.CompoundButton button, boolean checked) {
        if (!this.updating) {
            update(this.config.withTelemetry(this.config.telemetry.withEnabled(index, checked)));
        }
    }

    public void lambda$bindTelemetryControls$84(android.widget.CompoundButton button, boolean checked) {
        if (!this.updating) {
            update(this.config.withTelemetry(this.config.telemetry.withDualSpeed(checked)));
        }
    }

    public void lambda$bindTelemetryControls$85(android.widget.CompoundButton button, boolean checked) {
        if (!this.updating) {
            update(this.config.withTelemetry(this.config.telemetry.withCurrentInMilliamps(checked)));
        }
    }

    public void lambda$bindTelemetryControls$86(android.widget.CompoundButton button, boolean checked) {
        if (!this.updating) {
            update(this.config.withTelemetry(this.config.telemetry.withCurrentPositive(checked)));
        }
    }

    @Override // android.app.Activity
    protected void onPause() {
        resumed = false;
        controlCenterAppTilesReady = null;
        persistEditorSession();
        suppressEditorSelection = true;
        if (controlCenterWallpaperWorkspace != null) controlCenterWallpaperWorkspace.close();
        flushSettingsSave();
        this.saveHandler.removeCallbacks(this.controlCenterPreviewPoll);
        if (this.activationObserver != null) {
            getContentResolver().unregisterContentObserver(this.activationObserver);
            this.activationObserver = null;
        }
        super.onPause();
    }

    @Override protected void onDestroy() {
        suppressEditorSelection = true;
        if (settingsDialogs != null) settingsDialogs.close();
        if (backupController != null) backupController.close();
        if (presetController != null) presetController.close();
        if (startupCoordinator != null) startupCoordinator.close();
        persistEditorSession();
        if (configSession != null) configSession.close();
        if (editorPage != null) editorPage.close();
        if (controlCenterActualPreview != null) controlCenterActualPreview.clearPreview();
        if (previewLoader != null) previewLoader.close();
        saveHandler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    @Override public void onBackPressed() {
        if (controlCenterWallpaperWorkspace != null && controlCenterWallpaperWorkspace.close()) return;
        super.onBackPressed();
    }

    public void scheduleSettingsSave() {
        this.pendingSettingsSave = true;
        this.saveHandler.removeCallbacks(this.saveSettings);
        this.saveHandler.postDelayed(this.saveSettings, 350L);
    }

    public void flushSettingsSave() {
        if (this.pendingSettingsSave) {
            this.saveHandler.removeCallbacks(this.saveSettings);
            this.saveSettings.run();
        }
    }

    public java.lang.String spanOffsetLabel(boolean horizontal, int offset) {
        int direction;
        if (offset == 0) {
            return getString(com.xtjm.fusionstatusbar.R.string.span_offset_center);
        }
        if (horizontal) {
            direction = offset < 0 ? com.xtjm.fusionstatusbar.R.string.span_offset_left : com.xtjm.fusionstatusbar.R.string.span_offset_right;
        } else {
            direction = offset < 0 ? com.xtjm.fusionstatusbar.R.string.span_offset_up : com.xtjm.fusionstatusbar.R.string.span_offset_down;
        }
        return getString(direction, new java.lang.Object[]{java.lang.Integer.valueOf(java.lang.Math.abs(offset))});
    }

    private void bindPosition(int sideGroupId, int rowGroupId, final int element) {
        android.widget.RadioGroup side = (android.widget.RadioGroup) findViewById(sideGroupId);
        android.widget.RadioGroup row = (android.widget.RadioGroup) findViewById(rowGroupId);
        side.setOnCheckedChangeListener(new android.widget.RadioGroup.OnCheckedChangeListener() { // from class: com.xtjm.fusionstatusbar.MainActivity.117
            @Override // android.widget.RadioGroup.OnCheckedChangeListener
            public final void onCheckedChanged(android.widget.RadioGroup radioGroup, int i) {
                com.xtjm.fusionstatusbar.MainActivity.this.lambda$bindPosition$87(element, radioGroup, i);
            }
        });
        row.setOnCheckedChangeListener(new android.widget.RadioGroup.OnCheckedChangeListener() { // from class: com.xtjm.fusionstatusbar.MainActivity.118
            @Override // android.widget.RadioGroup.OnCheckedChangeListener
            public final void onCheckedChanged(android.widget.RadioGroup radioGroup, int i) {
                com.xtjm.fusionstatusbar.MainActivity.this.lambda$bindPosition$88(element, radioGroup, i);
            }
        });
    }

    public void lambda$bindPosition$87(int element, android.widget.RadioGroup group, int checkedId) {
        if (!this.updating) {
            com.xtjm.fusionstatusbar.FusionConfig next = this.config.withElementPosition(element, checkedId == leftId(element) ? 0 : 1, getPositionRow(this.config, element));
            update(next);
        }
    }

    public void lambda$bindPosition$88(int element, android.widget.RadioGroup group, int checkedId) {
        if (!this.updating) {
            com.xtjm.fusionstatusbar.FusionConfig next = this.config.withElementPosition(element, getPositionSide(this.config, element), checkedId == topId(element) ? 0 : 1);
            update(next);
        }
    }

    public void update(com.xtjm.fusionstatusbar.FusionConfig next) {
        if (configRestoring) return;
        this.saveHandler.removeCallbacks(this.saveSettings);
        this.pendingSettingsSave = false;
        this.config = next;
        scheduleSettingsSave();
        this.preview.setPreviewConfig(this.config);
        if (this.previewBackdrop != null) {
            this.previewBackdrop.setDisplayConfig(this.config);
        }
        applyControls();
    }

    private void confirmResetSettings() {
        new android.app.AlertDialog.Builder(this).setTitle("恢复默认设置").setMessage("将恢复状态栏和通知栏的全部自定义参数，是否继续？").setNegativeButton("取消", (android.content.DialogInterface.OnClickListener) null).setPositiveButton("恢复", new android.content.DialogInterface.OnClickListener() { // from class: com.xtjm.fusionstatusbar.MainActivity.119
            @Override // android.content.DialogInterface.OnClickListener
            public final void onClick(android.content.DialogInterface dialogInterface, int i) {
                com.xtjm.fusionstatusbar.MainActivity.this.lambda$confirmResetSettings$89(dialogInterface, i);
            }
        }).show();
    }

    public void lambda$confirmResetSettings$89(android.content.DialogInterface dialog, int which) {
        com.xtjm.fusionstatusbar.FusionConfig defaults = com.xtjm.fusionstatusbar.FusionConfig.defaults();
        update(defaults.withControlCenter(config.controlCenter));
    }

    private void refreshPreview() {
        if (this.preview == null) {
            return;
        }
        this.preview.showSample(91, this.previewWifi, this.previewWifi ? "" : "5G", 4, 3, -1);
        if (this.config != null) {
            this.preview.setPreviewConfig(this.config);
        }
    }

    private void applyControls() {
        this.updating = true;
        setScale(com.xtjm.fusionstatusbar.R.id.icon_size_seek, com.xtjm.fusionstatusbar.R.id.icon_size_value, this.config.iconScale);
        setScale(com.xtjm.fusionstatusbar.R.id.stroke_size_seek, com.xtjm.fusionstatusbar.R.id.stroke_size_value, this.config.strokeScale);
        ((android.widget.Switch) findViewById(com.xtjm.fusionstatusbar.R.id.custom_clock_switch)).setChecked(this.config.customClock);
        ((android.widget.Switch) findViewById(com.xtjm.fusionstatusbar.R.id.show_weather_switch)).setChecked(this.config.showWeather);
        android.widget.EditText clockFormatInput = (android.widget.EditText) findViewById(com.xtjm.fusionstatusbar.R.id.clock_format_input);
        if (!clockFormatInput.getText().toString().equals(this.config.clockPattern)) {
            clockFormatInput.setText(this.config.clockPattern);
        }
        clockFormatInput.setEnabled(this.config.customClock);
        findViewById(com.xtjm.fusionstatusbar.R.id.clock_format_label).setEnabled(this.config.customClock);
        findViewById(com.xtjm.fusionstatusbar.R.id.clock_format_error).setVisibility(View.GONE);
        for (int metric = 0; metric < 3; metric++) {
            this.telemetrySwitches[metric].setChecked(this.config.telemetry.enabled(metric));
            this.telemetryPositions[metric].setSelection(this.config.telemetry.position(metric));
            this.telemetryPositions[metric].setEnabled(this.config.telemetry.enabled(metric));
        }
        int metric2 = com.xtjm.fusionstatusbar.R.id.dual_speed_switch;
        ((android.widget.Switch) findViewById(metric2)).setChecked(this.config.telemetry.dualSpeed);
        findViewById(com.xtjm.fusionstatusbar.R.id.dual_speed_switch).setEnabled(this.config.telemetry.enabled(2));
        ((android.widget.Switch) findViewById(com.xtjm.fusionstatusbar.R.id.current_ma_switch)).setChecked(this.config.telemetry.currentInMilliamps);
        ((android.widget.Switch) findViewById(com.xtjm.fusionstatusbar.R.id.current_positive_switch)).setChecked(this.config.telemetry.currentPositive);
        findViewById(com.xtjm.fusionstatusbar.R.id.current_ma_switch).setEnabled(this.config.telemetry.enabled(1));
        findViewById(com.xtjm.fusionstatusbar.R.id.current_positive_switch).setEnabled(this.config.telemetry.enabled(1));
        ((android.widget.SeekBar) findViewById(com.xtjm.fusionstatusbar.R.id.telemetry_size_seek)).setProgress(this.config.telemetry.textSizeSp - 5);
        ((android.widget.TextView) findViewById(com.xtjm.fusionstatusbar.R.id.telemetry_size_label)).setText(getString(com.xtjm.fusionstatusbar.R.string.telemetry_size_value, new java.lang.Object[]{java.lang.Integer.valueOf(this.config.telemetry.textSizeSp)}));
        setSpanOffset(com.xtjm.fusionstatusbar.R.id.span_horizontal_seek, com.xtjm.fusionstatusbar.R.id.span_horizontal_value, this.config.spanOffsetX, 24, true);
        setSpanOffset(com.xtjm.fusionstatusbar.R.id.span_vertical_seek, com.xtjm.fusionstatusbar.R.id.span_vertical_value, this.config.spanOffsetY, 24, false);
        ((android.widget.Switch) findViewById(com.xtjm.fusionstatusbar.R.id.double_row_switch)).setChecked(this.config.doubleRow);
        ((android.widget.Switch) findViewById(com.xtjm.fusionstatusbar.R.id.span_rows_switch)).setChecked(this.config.spanRows);
        ((android.widget.Switch) findViewById(com.xtjm.fusionstatusbar.R.id.wifi_icon_switch)).setChecked(this.config.wifiIcon);
        findViewById(com.xtjm.fusionstatusbar.R.id.span_rows_switch).setEnabled(this.config.doubleRow);
        setChildrenEnabled((android.view.ViewGroup) findViewById(com.xtjm.fusionstatusbar.R.id.span_position_controls), this.config.doubleRow && this.config.spanRows);
        findViewById(com.xtjm.fusionstatusbar.R.id.wifi_icon_switch).setEnabled(true);
        ((android.widget.RadioGroup) findViewById(com.xtjm.fusionstatusbar.R.id.clock_side)).check(this.config.clockSide == 0 ? com.xtjm.fusionstatusbar.R.id.clock_left : com.xtjm.fusionstatusbar.R.id.clock_right);
        ((android.widget.RadioGroup) findViewById(com.xtjm.fusionstatusbar.R.id.clock_row)).check(this.config.clockRow == 0 ? com.xtjm.fusionstatusbar.R.id.clock_row_top : com.xtjm.fusionstatusbar.R.id.clock_row_bottom);
        ((android.widget.RadioGroup) findViewById(com.xtjm.fusionstatusbar.R.id.notification_side)).check(this.config.notificationSide == 0 ? com.xtjm.fusionstatusbar.R.id.notification_left : com.xtjm.fusionstatusbar.R.id.notification_right);
        ((android.widget.RadioGroup) findViewById(com.xtjm.fusionstatusbar.R.id.notification_row)).check(this.config.notificationRow == 0 ? com.xtjm.fusionstatusbar.R.id.notification_row_top : com.xtjm.fusionstatusbar.R.id.notification_row_bottom);
        ((android.widget.RadioGroup) findViewById(com.xtjm.fusionstatusbar.R.id.system_side)).check(this.config.systemSide == 0 ? com.xtjm.fusionstatusbar.R.id.system_left : com.xtjm.fusionstatusbar.R.id.system_right);
        ((android.widget.RadioGroup) findViewById(com.xtjm.fusionstatusbar.R.id.system_row)).check(this.config.systemRow == 0 ? com.xtjm.fusionstatusbar.R.id.system_row_top : com.xtjm.fusionstatusbar.R.id.system_row_bottom);
        ((android.widget.SeekBar) findViewById(com.xtjm.fusionstatusbar.R.id.statusbar_height_seek)).setProgress(this.config.statusBarHeight - 32);
        ((android.widget.TextView) findViewById(com.xtjm.fusionstatusbar.R.id.statusbar_height_label)).setText(getString(com.xtjm.fusionstatusbar.R.string.statusbar_height_value, new java.lang.Object[]{java.lang.Integer.valueOf(this.config.statusBarHeight)}));
        findViewById(com.xtjm.fusionstatusbar.R.id.element_position_controls).setEnabled(this.config.doubleRow);
        setChildrenEnabled((android.view.ViewGroup) findViewById(com.xtjm.fusionstatusbar.R.id.element_position_controls), this.config.doubleRow);
        this.updating = false;
        if (this.previewBackdrop != null) {
            this.previewBackdrop.setDisplayConfig(this.config);
        }
        updatePreviewLayout();
        updatePreviewElements();
        updateSettingsSummaries();
    }

    private void setScale(int seekId, int valueId, int scale) {
        ((android.widget.SeekBar) findViewById(seekId)).setProgress(scale - 50);
        ((android.widget.TextView) findViewById(valueId)).setText(scale + "%");
    }

    private void setSpanOffset(int seekId, int valueId, int offset, int limit, boolean horizontal) {
        android.widget.SeekBar seek = (android.widget.SeekBar) findViewById(seekId);
        seek.setMax(limit * 2);
        int shown = java.lang.Math.max(-limit, java.lang.Math.min(limit, offset));
        seek.setProgress(shown + limit);
        ((android.widget.TextView) findViewById(valueId)).setText(spanOffsetLabel(horizontal, shown));
    }

    public void updatePreviewLayout() {
        android.widget.FrameLayout.LayoutParams iconParams;
        int i;
        if (this.preview == null) {
            return;
        }
        com.xtjm.fusionstatusbar.StatusBarLayoutPlan plan = com.xtjm.fusionstatusbar.StatusBarLayoutPlan.from(this.config);
        android.view.View frame = findViewById(com.xtjm.fusionstatusbar.R.id.preview_frame);
        android.view.ViewGroup.LayoutParams frameParams = frame.getLayoutParams();
        int previewHeight = this.config.doubleRow ? java.lang.Math.max(180, java.lang.Math.min(this.config.statusBarHeight + 20, 280)) : 180;
        frameParams.height = dp(previewHeight);
        frame.setLayoutParams(frameParams);
        if (this.preview.getLayoutParams() instanceof android.widget.FrameLayout.LayoutParams) {
            iconParams = (android.widget.FrameLayout.LayoutParams) this.preview.getLayoutParams();
        } else {
            iconParams = new android.widget.FrameLayout.LayoutParams(-2, -2);
        }
        if (this.config.doubleRow) {
            int rowHeight = java.lang.Math.max(1, this.config.statusBarHeight / 2);
            int available = plan.spanFusion ? this.config.statusBarHeight : rowHeight;
            int iconHeight = com.xtjm.fusionstatusbar.FusionIconPlacement.iconHeight(available, this.config.iconScale);
            iconParams.height = dp(iconHeight);
            iconParams.gravity = (plan.fusionSide == 0 ? 8388611 : 8388613) | 48;
            if (plan.spanFusion) {
                i = (this.config.statusBarHeight - iconHeight) / 2;
            } else {
                int i2 = plan.fusionRow;
                i = (i2 * rowHeight) + ((rowHeight - iconHeight) / 2);
            }
            iconParams.topMargin = dp(i);
            iconParams.leftMargin = dp(8);
            iconParams.rightMargin = dp(8);
        } else {
            iconParams.height = dp(java.lang.Math.round((this.config.iconScale * 120.0f) / 100.0f));
            iconParams.gravity = 17;
            iconParams.topMargin = 0;
            iconParams.leftMargin = 0;
            iconParams.rightMargin = 0;
        }
        this.preview.setLayoutParams(iconParams);
        boolean spanning = plan.spanFusion;
        this.preview.setTranslationX(spanning ? dp(this.config.spanOffsetX) : 0.0f);
        this.preview.setTranslationY(spanning ? dp(this.config.spanOffsetY) : 0.0f);
    }

    public void updatePreviewElements() {
        android.view.View view;
        java.lang.String appendWeather;
        java.lang.String str;
        android.view.View view2;
        android.view.View view3;
        boolean z;
        int[][] iArr13;
        int[] iArr2;
        android.view.View view4;
        int[] iArr3;
        int i;
        int i15;
        int[] iArr4;
        java.lang.String str2;
        android.view.View view5;
        int dp4;
        java.lang.String str3;
        int i6;
        android.widget.FrameLayout frameLayout;
        int naturalWidth;
        int i2;
        com.xtjm.fusionstatusbar.MainActivity mainActivity = this;
        android.widget.FrameLayout frameLayout2 = (android.widget.FrameLayout) mainActivity.findViewById(com.xtjm.fusionstatusbar.R.id.preview_frame);
        if (frameLayout2 == null) {
            return;
        }
        com.xtjm.fusionstatusbar.StatusBarLayoutPlan from = com.xtjm.fusionstatusbar.StatusBarLayoutPlan.from(mainActivity.config);
        android.view.View findViewWithTag = frameLayout2.findViewWithTag("preview-clock");
        android.view.View findViewWithTag2 = frameLayout2.findViewWithTag("preview-notifications");
        android.view.View findViewWithTag3 = frameLayout2.findViewWithTag("preview-system");
        if (findViewWithTag == null) {
            android.widget.TextView textView = new android.widget.TextView(mainActivity);
            textView.setTag("preview-clock");
            textView.setTextColor(-1);
            textView.setTextSize(12.0f);
            frameLayout2.addView(textView);
            view = textView;
        } else {
            view = findViewWithTag;
        }
        java.util.Date date = new java.util.Date();
        java.lang.String format = android.text.format.DateFormat.getTimeFormat(this).format(date);
        java.lang.String string = mainActivity.getString(com.xtjm.fusionstatusbar.R.string.preview_weather_sample);
        com.xtjm.fusionstatusbar.ClockTextFormatter.LunarFields forDate = (mainActivity.config.customClock && com.xtjm.fusionstatusbar.ClockTextFormatter.needsLunar(mainActivity.config.clockPattern)) ? com.xtjm.fusionstatusbar.ChineseClockFields.forDate(date) : com.xtjm.fusionstatusbar.ClockTextFormatter.LunarFields.EMPTY;
        if (mainActivity.config.customClock) {
            java.lang.String appendWeather2 = com.xtjm.fusionstatusbar.ClockTextFormatter.format(date, java.util.Locale.getDefault(), mainActivity.config.clockPattern, mainActivity.config.showWeather, string, forDate);
            appendWeather = appendWeather2;
            str = string;
        } else {
            appendWeather = com.xtjm.fusionstatusbar.ClockTextFormatter.appendWeather(format, mainActivity.config.showWeather, string);
            str = string;
        }
        ((android.widget.TextView) view).setText(appendWeather == null ? format : appendWeather);
        if (findViewWithTag2 == null) {
            android.widget.TextView textView2 = new android.widget.TextView(mainActivity);
            textView2.setTag("preview-notifications");
            textView2.setText("●  ●  ●");
            textView2.setTextColor(-1);
            textView2.setTextSize(9.0f);
            frameLayout2.addView(textView2);
            view2 = textView2;
        } else {
            view2 = findViewWithTag2;
        }
        ((android.widget.TextView) view2).setTextSize((mainActivity.config.notificationIconScale * 9.0f) / 100.0f);
        if (findViewWithTag3 == null) {
            android.widget.TextView textView3 = new android.widget.TextView(mainActivity);
            textView3.setTag("preview-system");
            textView3.setText("◉  ◉");
            textView3.setTextColor(-1);
            textView3.setTextSize(10.0f);
            frameLayout2.addView(textView3);
            view3 = textView3;
        } else {
            view3 = findViewWithTag3;
        }
        ((android.widget.TextView) view3).setTextSize((mainActivity.config.systemIconScale * 10.0f) / 100.0f);
        int width = frameLayout2.getWidth() > 0 ? frameLayout2.getWidth() : getResources().getDisplayMetrics().widthPixels - mainActivity.dp(48);
        int max = java.lang.Math.max(mainActivity.dp(80), (width / 2) - mainActivity.dp(12));
        int[][] iArr5 = (int[][]) java.lang.reflect.Array.newInstance((java.lang.Class<?>) java.lang.Integer.TYPE, 2, 2);
        if (!from.doubleRow) {
            z = true;
        } else {
            if (from.spanFusion) {
                i2 = mainActivity.config.statusBarHeight;
            } else {
                i2 = mainActivity.config.statusBarHeight / 2;
            }
            int dp = mainActivity.dp(java.lang.Math.max(1, java.lang.Math.round((com.xtjm.fusionstatusbar.FusionIconPlacement.iconHeight(i2, mainActivity.config.iconScale) * 271.0f) / 282.0f)));
            int dp2 = from.spanFusion ? mainActivity.dp(mainActivity.config.spanOffsetX) : 0;
            int dp3 = dp + mainActivity.dp(8) + (from.fusionSide == 0 ? java.lang.Math.max(0, dp2) : java.lang.Math.max(0, -dp2));
            int[] iArr6 = iArr5[from.fusionSide];
            int i3 = from.spanFusion ? 0 : from.fusionRow;
            iArr6[i3] = iArr6[i3] + dp3;
            if (!from.spanFusion) {
                z = true;
            } else {
                int[] iArr7 = iArr5[from.fusionSide];
                z = true;
                iArr7[1] = iArr7[1] + dp3;
            }
        }
        java.lang.String str4 = format;
        applyPreviewPosition(view, from.clockSide, from.clockRow, max, iArr5, false);
        applyPreviewPosition(view2, from.notificationSide, from.notificationRow, max, iArr5, false);
        applyPreviewPosition(view3, from.systemSide, from.systemRow, max, iArr5, false);
        com.xtjm.fusionstatusbar.StatusPairView[] statusPairViewArr = new com.xtjm.fusionstatusbar.StatusPairView[3];
        int[] iArr8 = new int[3];
        int[] iArr9 = new int[3];
        int[] iArr10 = new int[3];
        int[] iArr11 = new int[3];
        int i4 = 0;
        android.widget.FrameLayout frameLayout3 = frameLayout2;
        int i5 = 3;
        while (i4 < i5) {
            android.widget.FrameLayout frameLayout4 = frameLayout2;
            java.lang.String str5 = "preview-telemetry-" + i4;
            com.xtjm.fusionstatusbar.StatusPairView statusPairView = (com.xtjm.fusionstatusbar.StatusPairView) frameLayout3.findViewWithTag(str5);
            java.lang.String str6 = appendWeather;
            android.view.View findViewWithTag4 = findViewWithTag2;
            if (!mainActivity.config.telemetry.enabled(i4)) {
                if (statusPairView != null) {
                    statusPairView.setVisibility(View.GONE);
                }
                frameLayout = frameLayout3;
            } else {
                if (statusPairView == null) {
                    statusPairView = new com.xtjm.fusionstatusbar.StatusPairView(mainActivity);
                    statusPairView.setTag(str5);
                    frameLayout3.addView(statusPairView);
                }
                android.widget.FrameLayout frameLayout5 = frameLayout3;
                frameLayout = frameLayout5;
                statusPairView.setLines(com.xtjm.fusionstatusbar.TelemetryReadings.SAMPLE.textFor(i4, mainActivity.config.telemetry, java.util.Locale.getDefault()));
                statusPairView.setSize(mainActivity.config.telemetry.textSizeFor(i4, mainActivity.config.statusBarHeight, mainActivity.config.doubleRow));
                statusPairView.setBold(mainActivity.config.telemetry.bold(i4));
                statusPairView.setLineSpacing(mainActivity.config.telemetry.lineSpacing(i4));
                int position = mainActivity.config.telemetry.position(i4);
                int side = com.xtjm.fusionstatusbar.TelemetryConfig.side(position);
                statusPairView.setAlignment(com.xtjm.fusionstatusbar.TelemetryConfig.resolvedAlignment(mainActivity.config.telemetry.alignment(i4), side));
                int telemetryRow = from.telemetryRow(position);
                if (mainActivity.config.telemetry.fixedWidth(i4) > 0) {
                    naturalWidth = mainActivity.dp(mainActivity.config.telemetry.fixedWidth(i4));
                } else {
                    naturalWidth = statusPairView.naturalWidth();
                }
                statusPairView.setPreferredWidth(naturalWidth);
                statusPairViewArr[i4] = statusPairView;
                iArr8[i4] = side;
                iArr9[i4] = telemetryRow;
                iArr10[i4] = mainActivity.dp(mainActivity.config.telemetry.leftMargin(i4)) + naturalWidth + mainActivity.dp(mainActivity.config.telemetry.rightMargin(i4));
            }
            i4++;
            appendWeather = str6;
            frameLayout3 = frameLayout;
            i5 = 3;
            frameLayout2 = frameLayout4;
            findViewWithTag2 = findViewWithTag4;
        }
        int dp5 = mainActivity.dp(6);
        int i7 = 0;
        android.view.View view6 = view2;
        while (i7 < 2) {
            int[] iArr12 = iArr8;
            int i8 = i4;
            int i9 = i7;
            android.view.View findViewWithTag5 = findViewWithTag3;
            android.view.View view7 = view;
            java.lang.String appendWeather3 = appendWeather;
            int i10 = 0;
            android.view.View view8 = view6;
            int i11 = 2;
            while (i10 < i11) {
                int i12 = 0;
                int i13 = 0;
                while (true) {
                    str2 = str;
                    if (i13 >= 3) {
                        break;
                    }
                    int dp6 = dp5;
                    int i14 = i11;
                    int i16 = i9;
                    if (statusPairViewArr[i13] != null && iArr12[i13] == i16 && iArr9[i13] == i10) {
                        i12++;
                    }
                    i13++;
                    str = str2;
                    i9 = i16;
                    dp5 = dp6;
                    i11 = i14;
                }
                if (i12 == 0) {
                    str3 = str4;
                    dp4 = dp5;
                    view5 = view8;
                } else {
                    int[] iArr14 = new int[i12];
                    int[] iArr15 = new int[i12];
                    int i17 = 0;
                    java.lang.String str7 = str4;
                    android.view.View view9 = view8;
                    int i18 = 0;
                    while (true) {
                        view5 = view9;
                        int i19 = i11;
                        if (i18 >= 3) {
                            break;
                        }
                        int dp7 = dp5;
                        if (statusPairViewArr[i18] != null) {
                            i6 = i9;
                            if (iArr12[i18] == i6 && iArr9[i18] == i10) {
                                iArr14[i17] = iArr10[i18];
                                iArr15[i17] = i18;
                                i17++;
                            }
                        } else {
                            i6 = i9;
                        }
                        i18++;
                        view9 = view5;
                        i9 = i6;
                        dp5 = dp7;
                        i11 = i19;
                    }
                    dp4 = dp5;
                    int[] allocate = com.xtjm.fusionstatusbar.StatusBarWidthAllocator.allocate(java.lang.Math.max(0, (max - iArr5[i9][i10]) - dp5), iArr14);
                    for (int i110 = 0; i110 < i12; i110++) {
                        iArr11[iArr15[i110]] = allocate[i110];
                    }
                    str3 = str7;
                }
                i10++;
                str = str2;
                str4 = str3;
                view8 = view5;
                i11 = 2;
                dp5 = dp4;
            }
            int i20 = i9;
            i7 = i20 + 1;
            view6 = view8;
            mainActivity = this;
            i4 = i8;
            findViewWithTag3 = findViewWithTag5;
            view = view7;
            appendWeather = appendWeather3;
            iArr8 = iArr12;
        }
        int[][] iArr16 = (int[][]) java.lang.reflect.Array.newInstance((java.lang.Class<?>) java.lang.Integer.TYPE, 2, 2);
        int i111 = 0;
        while (i111 < 3) {
            com.xtjm.fusionstatusbar.StatusPairView statusPairView2 = statusPairViewArr[i111];
            if (statusPairView2 == null) {
                i15 = dp5;
                iArr4 = iArr9;
                int[] iArr = iArr10;
                int[] iArr17 = iArr11;
                iArr8 = iArr8;
                iArr3 = iArr17;
                view4 = view3;
                iArr2 = iArr;
            } else {
                int i21 = iArr8[i111];
                int i112 = iArr9[i111];
                int i113 = iArr11[i111];
                iArr2 = iArr10;
                int[] fitMargins = mainActivity.fitMargins(i113, mainActivity.dp(mainActivity.config.telemetry.leftMargin(i111)), mainActivity.dp(mainActivity.config.telemetry.rightMargin(i111)));
                android.view.View view10 = view3;
                view4 = view10;
                int max2 = java.lang.Math.max(0, (i113 - fitMargins[0]) - fitMargins[1]);
                iArr3 = iArr11;
                int max3 = java.lang.Math.max(1, mainActivity.dp(mainActivity.config.statusBarHeight) / (mainActivity.config.doubleRow ? 2 : 1));
                int[] iArr18 = iArr9;
                android.widget.FrameLayout.LayoutParams layoutParams = new android.widget.FrameLayout.LayoutParams(max2, max3, (i21 == 0 ? 8388611 : 8388613) | (mainActivity.config.doubleRow ? 48 : 16));
                int i114 = iArr5[i21][i112] + dp5 + iArr16[i21][i112];
                if (i21 == 0) {
                    int max4 = i114 + fitMargins[0];
                    layoutParams.leftMargin = max4;
                    layoutParams.rightMargin = fitMargins[1];
                    i = dp5;
                } else {
                    i = dp5;
                    layoutParams.leftMargin = fitMargins[0];
                    layoutParams.rightMargin = fitMargins[1] + i114;
                }
                if (mainActivity.config.doubleRow) {
                    layoutParams.topMargin = i112 * max3;
                }
                statusPairView2.setVisibility(View.VISIBLE);
                statusPairView2.setLayoutParams(layoutParams);
                statusPairView2.setTranslationX(0.0f);
                statusPairView2.setTranslationY(mainActivity.dp(mainActivity.config.telemetry.verticalOffset(i111)));
                int[] iArr19 = iArr16[i21];
                iArr19[i112] = iArr19[i112] + i113;
                i15 = i;
                iArr4 = iArr18;
            }
            i111++;
            iArr10 = iArr2;
            iArr11 = iArr3;
            view3 = view4;
            iArr9 = iArr4;
            dp5 = i15;
            view = view;
            appendWeather = appendWeather;
            str = str;
            i7 = i7;
            view6 = view6;
            iArr8 = iArr8;
        }
        int[] iArr20 = iArr8;
        int[] iArr21 = iArr9;
        int[] iArr110 = iArr11;
        boolean nativeElementAt = from.nativeElementAt(1, 1);
        if (from.spanFusion && from.fusionSide == 1 && !nativeElementAt) {
            int i115 = -1;
            for (int i22 = 0; i22 < 3; i22++) {
                if (statusPairViewArr[i22] != null && iArr20[i22] == 1 && iArr21[i22] == 1 && iArr110[i22] > 0) {
                    i115 = java.lang.Math.max(i115, width - ((android.widget.FrameLayout.LayoutParams) statusPairViewArr[i22].getLayoutParams()).rightMargin);
                }
            }
            if (i115 >= 0) {
                int dp8 = mainActivity.dp(java.lang.Math.round((com.xtjm.fusionstatusbar.FusionIconPlacement.iconHeight(mainActivity.config.statusBarHeight, mainActivity.config.iconScale) * 271.0f) / 282.0f));
                int readoutShiftTowardFusion = com.xtjm.fusionstatusbar.DualRowStatusBarLayout.readoutShiftTowardFusion(((width - mainActivity.dp(8)) - dp8) + mainActivity.dp(mainActivity.config.spanOffsetX) + java.lang.Math.round(dp8 * 0.08f), i115, mainActivity.dp(4), mainActivity.dp(32));
                int i23 = 0;
                while (i23 < 3) {
                    if (statusPairViewArr[i23] != null) {
                        iArr13 = iArr16;
                        if (iArr20[i23] == 1 && iArr21[i23] == 1) {
                            statusPairViewArr[i23].setTranslationX(readoutShiftTowardFusion);
                        }
                    } else {
                        iArr13 = iArr16;
                    }
                    i23++;
                    iArr16 = iArr13;
                }
            }
        }
    }

    private void applyPreviewPosition(android.view.View view, int side, int row, int sideWidth, int[][] usedWidth, boolean multiline) {
        int actualSide = side == 0 ? 0 : 1;
        int actualRow = this.config.doubleRow ? row : 0;
        int remaining = java.lang.Math.max(1, sideWidth - usedWidth[actualSide][actualRow]);
        view.setVisibility(View.VISIBLE);
        if (view instanceof android.widget.TextView) {
            android.widget.TextView textView = (android.widget.TextView) view;
            textView.setSingleLine(!multiline);
            textView.setMaxLines(multiline ? 2 : 1);
            textView.setMaxWidth(remaining);
            textView.setEllipsize(android.text.TextUtils.TruncateAt.END);
        }
        view.measure(android.view.View.MeasureSpec.makeMeasureSpec(remaining,
                android.view.View.MeasureSpec.AT_MOST),
                android.view.View.MeasureSpec.makeMeasureSpec(0,
                        android.view.View.MeasureSpec.UNSPECIFIED));
        int measuredWidth = java.lang.Math.min(remaining, view.getMeasuredWidth());
        android.widget.FrameLayout.LayoutParams params = new android.widget.FrameLayout.LayoutParams(measuredWidth, -2);
        int horizontal = actualSide == 0 ? 8388611 : 8388613;
        int vertical = this.config.doubleRow ? 48 : 16;
        params.gravity = horizontal | vertical;
        int sideMargin = dp(6) + usedWidth[actualSide][actualRow];
        params.leftMargin = actualSide == 0 ? sideMargin : 0;
        params.rightMargin = actualSide == 1 ? sideMargin : 0;
        if (this.config.doubleRow) {
            int rowHeight = dp(this.config.statusBarHeight) / 2;
            params.topMargin = (actualRow * rowHeight) + java.lang.Math.max(0, (rowHeight - view.getMeasuredHeight()) / 2);
        }
        view.setLayoutParams(params);
        int[] iArr = usedWidth[actualSide];
        iArr[actualRow] = iArr[actualRow] + dp(6) + measuredWidth;
    }

    /* JADX INFO: Access modifiers changed from: private */
    public int dp(int value) {
        return java.lang.Math.round(value * getResources().getDisplayMetrics().density);
    }

    private int[] fitMargins(int allocated, int left, int right) {
        int available = java.lang.Math.max(0, allocated);
        int fittedLeft = java.lang.Math.min(java.lang.Math.max(0, left), available);
        int fittedRight = java.lang.Math.min(java.lang.Math.max(0, right), java.lang.Math.max(0, available - fittedLeft));
        return new int[]{fittedLeft, fittedRight};
    }

    private static int leftId(int element) {
        if (element == 0) {
            return com.xtjm.fusionstatusbar.R.id.clock_left;
        }
        return element == 1 ? com.xtjm.fusionstatusbar.R.id.notification_left : com.xtjm.fusionstatusbar.R.id.system_left;
    }

    private static int topId(int element) {
        if (element == 0) {
            return com.xtjm.fusionstatusbar.R.id.clock_row_top;
        }
        return element == 1 ? com.xtjm.fusionstatusbar.R.id.notification_row_top : com.xtjm.fusionstatusbar.R.id.system_row_top;
    }

    private static int getPositionSide(com.xtjm.fusionstatusbar.FusionConfig config, int element) {
        if (element == 0) {
            return config.clockSide;
        }
        return element == 1 ? config.notificationSide : config.systemSide;
    }

    private static int getPositionRow(com.xtjm.fusionstatusbar.FusionConfig config, int element) {
        if (element == 0) {
            return config.clockRow;
        }
        return element == 1 ? config.notificationRow : config.systemRow;
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void setChildrenEnabled(android.view.ViewGroup group, boolean enabled) {
        for (int i = 0; i < group.getChildCount(); i++) {
            android.view.View child = group.getChildAt(i);
            child.setEnabled(enabled);
            if (child instanceof android.view.ViewGroup) {
                android.view.ViewGroup nested = (android.view.ViewGroup) child;
                setChildrenEnabled(nested, enabled);
            }
        }
    }

    private void restartSystemUi(final android.widget.Button button) {
        button.setEnabled(false);
        button.setText(com.xtjm.fusionstatusbar.R.string.restarting_systemui);
        new java.lang.Thread(new java.lang.Runnable() { // from class: com.xtjm.fusionstatusbar.MainActivity.120
            @Override // java.lang.Runnable
            public final void run() {
                com.xtjm.fusionstatusbar.MainActivity.this.lambda$restartSystemUi$91(button);
            }
        }, "fusion-restart-systemui").start();
    }

    public void lambda$restartSystemUi$91(final android.widget.Button button) {
        boolean success = runRootCommand("killall -9 com.android.systemui");
        if (!success) {
            success = runRootCommand("am force-stop com.android.systemui");
        }
        final boolean result = success;
        runOnUiThread(new java.lang.Runnable() { // from class: com.xtjm.fusionstatusbar.MainActivity.121
            @Override // java.lang.Runnable
            public final void run() {
                com.xtjm.fusionstatusbar.MainActivity.this.lambda$restartSystemUi$90(button, result);
            }
        });
    }

    public void lambda$restartSystemUi$90(android.widget.Button button, boolean result) {
        button.setEnabled(true);
        button.setText(com.xtjm.fusionstatusbar.R.string.restart_systemui);
        android.widget.Toast.makeText(this, result ? com.xtjm.fusionstatusbar.R.string.restart_systemui_success : com.xtjm.fusionstatusbar.R.string.restart_systemui_failed, Toast.LENGTH_LONG).show();
    }

    private static boolean runRootCommand(java.lang.String command) {
        try {
            java.lang.Process process2 = new java.lang.ProcessBuilder("su", "-c", command).redirectErrorStream(true).start();
            if (process2.waitFor(8L, java.util.concurrent.TimeUnit.SECONDS)) {
                return process2.exitValue() == 0;
            }
            process2.destroyForcibly();
            return false;
        } catch (java.lang.Throwable th) {
            return false;
        }
    }
}
