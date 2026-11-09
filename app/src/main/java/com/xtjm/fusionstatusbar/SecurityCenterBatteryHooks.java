package com.xtjm.fusionstatusbar;

import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.View;
import java.lang.ref.WeakReference;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Locale;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import io.github.libxposed.api.XposedModule;

final class SecurityCenterBatteryHooks {
    private static final String GROUP = "preference_key_category_battery_info";
    private static final String FRAGMENT = "com.miui.powercenter.nightcharge.ChargeProtectFragment";
    private static final String DESIGN = "fusion_battery_design_capacity";
    private static final String FULL = "fusion_battery_full_capacity";
    private static final String SCREEN = "fusion_screen_on_time";
    private static final String[] NATIVE_FIELDS = {"d", "e", "f", "g", "h", "i"};
    private static final Set<String> INFO_KEYS = Set.of("reference_battery_health", "reference_current_temp",
            "reference_toady_charge_time", "reference_cycle_count", "reference_production_date",
            "reference_first_use_date");
    private static final Map<Object, Set<String>> BLOCKED_REMOVALS = new WeakHashMap<>();
    private static final Map<Object, ChargeOptionsState> CHARGE_OPTIONS = new WeakHashMap<>();
    private static final Map<Object, Long> REFERENCE_GENERATIONS = new WeakHashMap<>();
    private static final ExecutorService READER = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "FusionBatteryInfo");
        thread.setDaemon(true);
        return thread;
    });

    private SecurityCenterBatteryHooks() { }

    static int install(XposedModule module, ClassLoader loader) {
        int count = SecurityCenterHookSupport.forceBooleanAny(module, loader,
                new SecurityCenterFeature[] {SecurityCenterFeature.SMART_CHARGING,
                        SecurityCenterFeature.BYPASS_CHARGING_PLUS}, "xh.u", "r");
        count += SecurityCenterHookSupport.forceBooleanAny(module, loader,
                new SecurityCenterFeature[] {SecurityCenterFeature.CHARGE_FEATURES,
                        SecurityCenterFeature.SMART_CHARGING, SecurityCenterFeature.BYPASS_CHARGING_PLUS},
                "xh.u", "E");
        count += SecurityCenterHookSupport.forceBoolean(module, loader, SecurityCenterFeature.SMART_CHARGING,
                "com.miui.gamebooster.utils.o0", "X");
        count += SecurityCenterHookSupport.forceBoolean(module, loader, SecurityCenterFeature.BYPASS_CHARGING_PLUS,
                "com.miui.gamebooster.utils.o0", "Y");
        try {
            Class<?> charge = Class.forName("com.miui.powercenter.charge.ChargeFeatureFragment", false, loader);
            Method layout = charge.getDeclaredMethod("L0");
            count += SecurityCenterHookSupport.hook(module, layout, "battery:charge-options", chain -> {
                Object target = chain.getThisObject();
                prepareChargeOptions(target, chargeOptions(target), enabled(SecurityCenterFeature.CHARGE_FEATURES));
                return chain.proceed();
            });
            Method create = charge.getDeclaredMethod("onCreatePreferences", Bundle.class, String.class);
            count += SecurityCenterHookSupport.hook(module, create, "battery:charge-options-initialize", chain -> {
                Object target = chain.getThisObject();
                ChargeOptionsState state = chargeOptions(target);
                if (state.requiresRefresh(false)) clearChargeIndices(target);
                state.resetForNativeInitialization();
                return chain.proceed();
            });
            Method resume = charge.getMethod("onResume");
            count += SecurityCenterHookSupport.hook(module, resume, "battery:charge-options-refresh", chain -> {
                Object result = chain.proceed();
                Object target = chain.getThisObject();
                if (charge.isInstance(target) && call(target, "getContext") != null
                        && field(target, "a").get(target) != null
                        && chargeOptions(target).requiresRefresh(enabled(SecurityCenterFeature.CHARGE_FEATURES)))
                    layout.invoke(target);
                return result;
            });
        } catch (ReflectiveOperationException error) { unavailable(module, "charge layout", error); }
        count += installInformation(module, loader);
        count += installDialogs(module, loader);
        return count;
    }

    private static int installInformation(XposedModule module, ClassLoader loader) {
        int count = 0;
        try {
            Class<?> preference = Class.forName("androidx.preference.Preference", false, loader);
            Class<?> group = Class.forName("androidx.preference.PreferenceGroup", false, loader);
            Method remove = group.getDeclaredMethod("removePreference", preference);
            count += SecurityCenterHookSupport.hook(module, remove, "battery:retain-information", chain -> {
                Object row = chain.getArgs().get(0);
                if (enabled(SecurityCenterFeature.BATTERY_DETAILS) && row != null) {
                    String key = (String) call(row, "getKey");
                    if (keepBatteryPreference((String) call(chain.getThisObject(), "getKey"), key)) {
                        rememberRemoval(chain.getThisObject(), key);
                        return false;
                    }
                }
                return chain.proceed();
            });
            Class<?> fragment = Class.forName(FRAGMENT, false, loader);
            Method create = fragment.getDeclaredMethod("onCreatePreferences", Bundle.class, String.class);
            count += SecurityCenterHookSupport.hook(module, create, "battery:information-layout", chain -> {
                nextReferenceGeneration(chain.getThisObject());
                Object result = chain.proceed();
                updateRows(chain.getThisObject());
                return result;
            });
            Method resume = fragment.getMethod("onResume");
            count += SecurityCenterHookSupport.hook(module, resume, "battery:information-refresh", chain -> {
                Object result = chain.proceed();
                if (fragment.isInstance(chain.getThisObject())) {
                    updateRows(chain.getThisObject());
                    refreshInformation(chain.getThisObject(), loader);
                }
                return result;
            });
        } catch (ReflectiveOperationException error) { unavailable(module, "battery information", error); }
        return count;
    }

    private static int installDialogs(XposedModule module, ClassLoader loader) {
        int count = 0;
        try {
            Class<?> slow = Class.forName("com.miui.gamebooster.utils.e4", false, loader);
            Method show = slow.getDeclaredMethod("R", View.class);
            Method accept = slow.getDeclaredMethod("S", View.class, DialogInterface.class, int.class);
            accept.setAccessible(true);
            count += SecurityCenterHookSupport.hook(module, show, "battery:smart-charge-confirmation", chain -> {
                if (!enabled(SecurityCenterFeature.SKIP_CHARGE_DIALOGS)) return chain.proceed();
                accept.invoke(null, chain.getArgs().get(0), null, DialogInterface.BUTTON_POSITIVE);
                return null;
            });
        } catch (ReflectiveOperationException error) { unavailable(module, "smart charge confirmation", error); }
        for (String owner : new String[] {"com.miui.powercenter.nightcharge.NightChargeDialogActivity",
                "com.miui.powercenter.superwirelesscharge.SuperWirelessChargeDialog"}) {
            try {
                Class<?> type = Class.forName(owner, false, loader);
                Method create = type.getDeclaredMethod("onCreate", Bundle.class);
                count += SecurityCenterHookSupport.hook(module, create, "battery:charge-hint:" + owner, chain -> {
                    Object result = chain.proceed();
                    if (enabled(SecurityCenterFeature.SKIP_CHARGE_DIALOGS)) {
                        Activity activity = (Activity) chain.getThisObject();
                        if (owner.endsWith("NightChargeDialogActivity")) activity.finish();
                        else if (keepWirelessCharge(field(activity, "b").get(activity))) activity.finish();
                    }
                    return result;
                });
            } catch (ReflectiveOperationException error) { unavailable(module, owner, error); }
        }
        return count;
    }

    private static void updateRows(Object fragment) throws ReflectiveOperationException {
        Object group = field(fragment, "c").get(fragment);
        Context context = (Context) call(fragment, "getContext");
        if (group == null || context == null) return;
        boolean details = enabled(SecurityCenterFeature.BATTERY_DETAILS);
        if (details) {
            for (String name : NATIVE_FIELDS) {
                Object row = field(fragment, name).get(fragment);
                if (row == null) continue;
                String key = (String) call(row, "getKey");
                if (call(group, "findPreference", key) == null) {
                    rememberRemoval(group, key);
                    call(group, "addPreference", row);
                }
                Object current = call(row, "getText");
                if (current == null || current.toString().isBlank()) call(row, "setText", "--");
            }
        } else {
            Set<String> originalRemovals;
            synchronized (BLOCKED_REMOVALS) { originalRemovals = BLOCKED_REMOVALS.remove(group); }
            for (String key : originalRemovals == null ? Set.<String>of() : originalRemovals) {
                Object row = call(group, "findPreference", key);
                if (row != null) call(group, "removePreference", row);
            }
        }
        ensureRow(group, context, DESIGN, "电池设计容量（参考）", details, 1000);
        ensureRow(group, context, FULL, "当前满充容量（参考）", details, 1001);
        ensureRow(group, context, SCREEN, "亮屏时间（参考，自上次充满）",
                enabled(SecurityCenterFeature.SCREEN_ON_TIME), 1002);
    }

    private static void ensureRow(Object group, Context context, String key, String title,
            boolean visible, int order) throws ReflectiveOperationException {
        Object row = call(group, "findPreference", key);
        if (!visible) {
            if (row != null) call(group, "removePreference", row);
            return;
        }
        if (row != null) return;
        Class<?> type = Class.forName("miuix.preference.TextPreference", false, group.getClass().getClassLoader());
        Constructor<?> constructor = type.getConstructor(Context.class);
        row = constructor.newInstance(context);
        call(row, "setKey", key);
        call(row, "setTitle", title);
        call(row, "setText", "--");
        call(row, "setClickable", false);
        call(row, "setOrder", order);
        call(group, "addPreference", row);
    }

    private static void refreshInformation(Object fragment, ClassLoader loader) throws ReflectiveOperationException {
        long generation = nextReferenceGeneration(fragment);
        MiscConfig config = SecurityCenterConfigRuntime.current();
        boolean details = config.isEnabled(SecurityCenterFeature.BATTERY_DETAILS);
        boolean screen = config.isEnabled(SecurityCenterFeature.SCREEN_ON_TIME);
        Context context = (Context) call(fragment, "getContext");
        Object group = field(fragment, "c").get(fragment);
        if ((!details && !screen) || context == null || group == null) return;
        Context app = context.getApplicationContext();
        int precision = config.screenOnTimePrecisionMinutes;
        ReferenceRequest request = new ReferenceRequest(fragment, group, generation, precision);
        READER.execute(() -> {
            String[] values = new String[7];
            if (details) {
                values[0] = temperatureText(app);
                values[1] = countText(readHost(loader, "ph.c", "g"));
                values[2] = countText(readHost(loader, "nh.g", "d"));
                Object production = readHost(loader, "xh.o", "e");
                Object firstUse = readHost(loader, "xh.o", "m");
                values[3] = batteryDate(production instanceof String text ? text : null);
                values[4] = batteryDate(firstUse instanceof String text ? text : null);
                values[5] = capacityText(readHost(loader, "xh.i", "j"));
                values[6] = capacityText(readHost(loader, "xh.i", "i"));
            }
            String time = screen ? screenOnText(screenOnMinutes(readScreenOnMicros(loader), precision)) : "--";
            new Handler(Looper.getMainLooper()).post(() -> {
                Object target = request.owner.get();
                if (target == null) return;
                try {
                    if (call(target, "getContext") == null) return;
                    Object currentGroup = field(target, "c").get(target);
                    MiscConfig current = SecurityCenterConfigRuntime.current();
                    if (!request.accepts(target, currentGroup, referenceGeneration(target),
                            current.screenOnTimePrecisionMinutes)) return;
                    if (current.isEnabled(SecurityCenterFeature.BATTERY_DETAILS) && details) {
                        String[] fields = {"e", "f", "g", "h", "i"};
                        for (int i = 0; i < fields.length; i++)
                            call(field(target, fields[i]).get(target), "setText", values[i]);
                        call(call(currentGroup, "findPreference", DESIGN), "setText", values[5]);
                        call(call(currentGroup, "findPreference", FULL), "setText", values[6]);
                    }
                    if (current.isEnabled(SecurityCenterFeature.SCREEN_ON_TIME) && screen)
                        call(call(currentGroup, "findPreference", SCREEN), "setText", time);
                } catch (ReflectiveOperationException | RuntimeException ignored) { }
            });
        });
    }

    private static long readScreenOnMicros(ClassLoader loader) {
        try {
            Object stats = Class.forName("miui.securitycenter.powercenter.BatteryStatsUtils", false, loader)
                    .getDeclaredMethod("getBatteryStats").invoke(null);
            int which = Class.forName("android.os.BatteryStats", false, loader)
                    .getField("STATS_SINCE_CHARGED").getInt(null);
            Object value = call(stats, "getScreenOnTime", SystemClock.elapsedRealtime() * 1000L, which);
            return value instanceof Number number && number.longValue() >= 0 ? number.longValue() : -1;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError | OutOfMemoryError ignored) {
            return -1;
        }
    }

    private static String temperatureText(Context app) {
        try {
            Intent battery = app.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
            int temperature = battery == null ? Integer.MIN_VALUE
                    : battery.getIntExtra("temperature", Integer.MIN_VALUE);
            return temperature == Integer.MIN_VALUE ? "--"
                    : String.format(Locale.getDefault(), "%.1f °C", temperature / 10f);
        } catch (RuntimeException unavailable) { return "--"; }
    }

    private static void rememberRemoval(Object group, String key) {
        synchronized (BLOCKED_REMOVALS) {
            BLOCKED_REMOVALS.computeIfAbsent(group, ignored -> new HashSet<>()).add(key);
        }
    }

    static long screenOnMinutes(long micros, int precisionMinutes) {
        if (micros < 0) return -1;
        int precision = Math.max(1, Math.min(60, precisionMinutes));
        long minutes = micros / 60_000_000L;
        return minutes / precision * precision;
    }

    static String screenOnText(long minutes) {
        if (minutes < 0) return "--";
        return minutes < 60 ? minutes + "分钟" : minutes / 60 + "小时" + minutes % 60 + "分钟";
    }

    static boolean keepBatteryPreference(String category, String key) {
        return GROUP.equals(category) && key != null && INFO_KEYS.contains(key);
    }

    static boolean keepWirelessCharge(Object dialog) {
        if (dialog == null) return false;
        try {
            Object button = call(dialog, "getButton", DialogInterface.BUTTON_NEGATIVE);
            return button != null && Boolean.TRUE.equals(call(button, "performClick"));
        } catch (ReflectiveOperationException | RuntimeException error) { return false; }
    }

    static String batteryDate(String raw) {
        if (raw == null || !raw.matches("[0-9]{8}")) return "--";
        try { return LocalDate.parse(raw, DateTimeFormatter.BASIC_ISO_DATE).toString(); }
        catch (DateTimeParseException invalid) { return "--"; }
    }

    static String capacityText(Object raw) {
        return raw instanceof Number value && value.longValue() >= 1000
                ? value.longValue() / 1000 + " mAh" : "--";
    }

    static final class ChargeOptionsState {
        private boolean owned;
        private boolean originalFastCharge;
        private boolean originalLowBatteryCharge;

        synchronized boolean[] apply(boolean enabled, boolean fastCharge, boolean lowBatteryCharge) {
            if (enabled) {
                if (!owned) {
                    originalFastCharge = fastCharge;
                    originalLowBatteryCharge = lowBatteryCharge;
                    owned = true;
                }
                return new boolean[] {true, true};
            }
            if (!owned) return new boolean[] {fastCharge, lowBatteryCharge};
            owned = false;
            return new boolean[] {originalFastCharge, originalLowBatteryCharge};
        }

        synchronized boolean requiresRefresh(boolean enabled) { return owned != enabled; }

        synchronized void resetForNativeInitialization() { owned = false; }
    }

    static void prepareChargeOptions(Object fragment, ChargeOptionsState state, boolean enabled)
            throws ReflectiveOperationException {
        boolean changed = state.requiresRefresh(enabled);
        if (!enabled && !changed) return;
        Field fast = field(fragment, "p");
        Field low = field(fragment, "q");
        boolean[] flags = state.apply(enabled, fast.getBoolean(fragment), low.getBoolean(fragment));
        fast.setBoolean(fragment, flags[0]);
        low.setBoolean(fragment, flags[1]);
        if (changed) {
            clearChargeIndices(fragment);
            if (enabled) {
                for (String key : new String[] {"pref_key_charge_protection_wired_charge_category",
                        "pref_key_charge_protection_wired_charge_mode"}) {
                    Object row = call(fragment, "findPreference", key);
                    if (row != null) call(row, "setVisible", true);
                }
            }
        }
    }

    private static ChargeOptionsState chargeOptions(Object fragment) {
        synchronized (CHARGE_OPTIONS) {
            return CHARGE_OPTIONS.computeIfAbsent(fragment, ignored -> new ChargeOptionsState());
        }
    }

    private static void clearChargeIndices(Object fragment) throws ReflectiveOperationException {
        Object indices = field(fragment, "B").get(fragment);
        if (indices instanceof Map<?, ?> map) map.clear();
    }

    static final class ReferenceRequest {
        private final WeakReference<Object> owner;
        private final WeakReference<Object> group;
        private final long generation;
        private final int precisionMinutes;

        ReferenceRequest(Object owner, Object group, long generation, int precisionMinutes) {
            this.owner = new WeakReference<>(owner);
            this.group = new WeakReference<>(group);
            this.generation = generation;
            this.precisionMinutes = precisionMinutes;
        }

        boolean accepts(Object currentOwner, Object currentGroup, long latestGeneration, int currentPrecision) {
            return currentOwner != null && currentGroup != null && owner.get() == currentOwner
                    && group.get() == currentGroup && generation == latestGeneration
                    && precisionMinutes == currentPrecision;
        }
    }

    private static long nextReferenceGeneration(Object fragment) {
        synchronized (REFERENCE_GENERATIONS) {
            long generation = REFERENCE_GENERATIONS.getOrDefault(fragment, 0L) + 1;
            REFERENCE_GENERATIONS.put(fragment, generation);
            return generation;
        }
    }

    private static long referenceGeneration(Object fragment) {
        synchronized (REFERENCE_GENERATIONS) { return REFERENCE_GENERATIONS.getOrDefault(fragment, 0L); }
    }

    private static String countText(Object value) {
        return value instanceof Number number && number.longValue() >= 0 ? number.toString() : "--";
    }

    private static Object readHost(ClassLoader loader, String owner, String method) {
        try { return Class.forName(owner, false, loader).getDeclaredMethod(method).invoke(null); }
        catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) { return null; }
    }

    private static boolean enabled(SecurityCenterFeature feature) {
        return SecurityCenterConfigRuntime.current().isEnabled(feature);
    }

    private static Field field(Object owner, String name) throws ReflectiveOperationException {
        for (Class<?> type = owner.getClass(); type != null; type = type.getSuperclass()) {
            try {
                Field field = type.getDeclaredField(name);
                field.setAccessible(true);
                return field;
            } catch (NoSuchFieldException missing) { }
        }
        throw new NoSuchFieldException(name);
    }

    private static Object call(Object owner, String name, Object... args) throws ReflectiveOperationException {
        if (owner == null) throw new NoSuchMethodException(name);
        for (Class<?> type = owner.getClass(); type != null; type = type.getSuperclass()) {
            for (Method method : type.getDeclaredMethods()) {
                if (!method.getName().equals(name) || method.getParameterCount() != args.length) continue;
                Class<?>[] parameters = method.getParameterTypes();
                boolean matches = true;
                for (int i = 0; i < args.length; i++) {
                    if (args[i] == null) matches &= !parameters[i].isPrimitive();
                    else if (parameters[i].isPrimitive()) matches &= (parameters[i] == int.class && args[i] instanceof Integer)
                            || (parameters[i] == long.class && args[i] instanceof Long)
                            || (parameters[i] == boolean.class && args[i] instanceof Boolean);
                    else matches &= parameters[i].isInstance(args[i]);
                }
                if (matches) { method.setAccessible(true); return method.invoke(owner, args); }
            }
        }
        throw new NoSuchMethodException(owner.getClass().getName() + "." + name);
    }

    private static void unavailable(XposedModule module, String part, Exception error) {
        module.log(android.util.Log.WARN, FusionModule.TAG, "security center battery " + part + " unavailable", error);
    }
}
