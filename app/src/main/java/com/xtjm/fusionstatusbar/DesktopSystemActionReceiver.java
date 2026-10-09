package com.xtjm.fusionstatusbar;

import android.bluetooth.BluetoothAdapter;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.location.LocationManager;
import android.media.AudioManager;
import android.net.wifi.WifiManager;
import android.nfc.NfcAdapter;
import android.os.Handler;
import android.os.Looper;
import android.os.Build;
import android.os.PowerManager;
import android.os.SystemClock;
import android.os.UserHandle;
import android.provider.Settings;
import android.telephony.TelephonyManager;
import android.app.NotificationManager;
import android.app.UiModeManager;
import android.util.Log;

import java.lang.reflect.Method;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.concurrent.Executor;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/** Executes authenticated launcher action broadcasts in SystemUI's permission context. */
final class DesktopSystemActionReceiver {
    private static final String TAG = "FusionStatusBar";
    private static final AtomicBoolean REGISTERED = new AtomicBoolean();
    private static final AtomicBoolean TORCH_CALLBACK_REGISTERED = new AtomicBoolean();
    private static final ConcurrentHashMap<String, Boolean> TORCH_STATES = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, Boolean> PENDING_TORCH_TOGGLES =
            new ConcurrentHashMap<>();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private DesktopSystemActionReceiver() { }

    static void register(Context source) {
        if (source == null || !REGISTERED.compareAndSet(false, true)) return;
        Context context = source.getApplicationContext();
        if (!"com.android.systemui".equals(context.getPackageName())) {
            try { context = context.createPackageContext("com.android.systemui", 0); }
            catch (PackageManager.NameNotFoundException error) {
                REGISTERED.set(false);
                Log.w(TAG, "SystemUI action receiver context unavailable", error);
                return;
            }
        }
        try {
            final Context systemUiContext = context;
            context.registerReceiver(new BroadcastReceiver() {
                @Override public void onReceive(Context ignored, Intent intent) {
                    int senderUid = Build.VERSION.SDK_INT >= 34 ? getSentFromUid() : -1;
                    if (!receiveLauncherAction(systemUiContext, intent, senderUid)) {
                        Log.w(TAG, "ignored untrusted desktop action broadcast");
                    }
                }
            }, new IntentFilter(DesktopActionQueue.ACTION_DISPATCH), Context.RECEIVER_EXPORTED);
            Log.i(TAG, "desktop action broadcast bridge registered");
        } catch (Throwable error) {
            REGISTERED.set(false);
            Log.w(TAG, "desktop action bridge registration failed", error);
        }
    }

    static boolean receiveLauncherAction(Context context, Intent intent, int senderUid) {
        if (context == null || intent == null
                || !DesktopActionQueue.ACTION_DISPATCH.equals(intent.getAction())
                || !"com.android.systemui".equals(intent.getPackage())) return false;
        int action = intent.getIntExtra(DesktopActionQueue.KEY_ACTION,
                DesktopConfig.ACTION_NATIVE);
        String target = intent.getStringExtra(DesktopActionQueue.KEY_TARGET);
        int toggle = intent.getIntExtra(DesktopActionQueue.KEY_TOGGLE, 1);
        if (!DesktopActionQueue.isValidCommand(action, target, toggle)) return false;
        long timestamp = intent.getLongExtra(DesktopActionQueue.KEY_TIMESTAMP, 0L);
        String nonce = intent.getStringExtra(DesktopActionQueue.KEY_NONCE);
        String signature = intent.getStringExtra(DesktopActionQueue.KEY_SIGNATURE);
        if (!DesktopActionBridgeToken.verify(context, action, target, toggle, timestamp,
                    nonce, signature)
                || Build.VERSION.SDK_INT >= 34 && !isTrustedLauncherUid(context, senderUid)) {
            return false;
        }
        execute(context, action, target, toggle);
        return true;
    }

    static boolean isTrustedLauncherUid(Context context, int uid) {
        if (context == null || uid < 0) return false;
        return isTrustedLauncherUid(uid, context.getPackageManager().getPackagesForUid(uid));
    }

    static boolean isTrustedLauncherUid(int uid, String[] packages) {
        if (uid < 0 || packages == null) return false;
        for (String packageName : packages) {
            if ("com.miui.home".equals(packageName)) return true;
        }
        return false;
    }

    private static void execute(Context context, int action, String target, int toggle) {
        try {
            switch (action) {
                case DesktopConfig.ACTION_NONE:
                    return;
                case DesktopConfig.ACTION_NOTIFICATIONS:
                    callStatusBar(context, "expandNotificationsPanel");
                    return;
                case DesktopConfig.ACTION_QUICK_SETTINGS:
                    callStatusBar(context, "expandSettingsPanel", new Object[] {null});
                    return;
                case DesktopConfig.ACTION_LOCK_DEVICE:
                    sleep(context, 7);
                    callStaticService("android.view.WindowManagerGlobal", "getWindowManagerService",
                            "lockNow", new Class<?>[] {android.os.Bundle.class}, new Object[] {null});
                    return;
                case DesktopConfig.ACTION_SLEEP:
                    sleep(context, 4);
                    return;
                case DesktopConfig.ACTION_SCREENSHOT:
                    sendSystemAction(context, "SYSTEM_ACTION_TAKE_SCREENSHOT");
                    return;
                case DesktopConfig.ACTION_RECENTS:
                    sendSystemAction(context, "SYSTEM_ACTION_RECENTS");
                    return;
                case DesktopConfig.ACTION_POWER_MENU:
                    callStaticService("android.view.WindowManagerGlobal", "getWindowManagerService",
                            "showGlobalActions", new Class<?>[0], new Object[0]);
                    return;
                case DesktopConfig.ACTION_SIDEBAR:
                    context.sendBroadcast(new Intent(SidebarHooks.ACTION_SHOW_SIDEBAR)
                            .setPackage("com.miui.securitycenter"));
                    return;
                case DesktopConfig.ACTION_LAUNCH_APP:
                case DesktopConfig.ACTION_LAUNCH_ACTIVITY:
                    launchComponent(context, target,
                            action == DesktopConfig.ACTION_LAUNCH_APP);
                    return;
                case DesktopConfig.ACTION_LAUNCH_SHORTCUT:
                    launchShortcut(context, target);
                    return;
                case DesktopConfig.ACTION_TOGGLE:
                    toggle(context, toggle);
                    return;
                case DesktopConfig.ACTION_CLEAR_MEMORY:
                    Intent clearMemory = new Intent("com.android.systemui.taskmanager.Clear");
                    clearMemory.putExtra("show_toast", true);
                    context.sendBroadcast(clearMemory);
                    return;
                case DesktopConfig.ACTION_ONE_HANDED:
                    Settings.Secure.putInt(context.getContentResolver(),
                            "one_handed_mode_activated", 1);
                    return;
                case DesktopConfig.ACTION_CLEAR_NOTIFICATIONS:
                    Object notificationService = callStatic(NotificationManager.class, "getService");
                    if (notificationService != null) call(notificationService,
                            "cancelAllNotifications", new Class<?>[] {String.class, int.class},
                            null, 0);
                    return;
                default:
                    Log.w(TAG, "unsupported desktop action=" + action);
            }
        } catch (Throwable error) {
            Log.w(TAG, "desktop action failed action=" + action, error);
        }
    }

    private static void sendSystemAction(Context context, String action) {
        context.sendBroadcast(new Intent(action).setPackage("com.android.systemui"));
    }

    private static void sleep(Context context, int reason) throws Exception {
        call(context.getSystemService(Context.POWER_SERVICE), "goToSleep",
                new Class<?>[] {long.class, int.class, int.class},
                SystemClock.uptimeMillis(), reason, 0);
    }

    private static void callStatusBar(Context context, String method, Object... args)
            throws Exception {
        Object service = context.getSystemService(android.app.StatusBarManager.class);
        Class<?>[] parameterTypes = args.length == 0 ? new Class<?>[0]
                : new Class<?>[] {String.class};
        try {
            call(service, method, parameterTypes, args);
        } catch (NoSuchMethodException missing) {
            if (args.length != 0) throw missing;
            call(service, method, new Class<?>[] {String.class}, "");
        }
    }

    private static void callStaticService(String ownerName, String getter, String method,
            Class<?>[] parameterTypes, Object[] args) throws Exception {
        Class<?> owner = Class.forName(ownerName);
        Object service = callStatic(owner, getter);
        call(service, method, parameterTypes, args);
    }

    private static void launchComponent(Context context, String target, boolean launcherApp) {
        String[] parts = target == null ? new String[0] : target.split("\\|", 2);
        Intent intent;
        if (launcherApp && parts.length == 1 && !parts[0].isEmpty()) {
            intent = context.getPackageManager().getLaunchIntentForPackage(parts[0]);
            if (intent == null && "com.android.settings".equals(parts[0])) {
                intent = new Intent(Settings.ACTION_SETTINGS).setPackage(parts[0]);
            }
            if (intent == null) return;
        } else {
            if (parts.length != 2 || parts[0].isEmpty() || parts[1].isEmpty()) return;
            intent = new Intent().setClassName(parts[0], parts[1]);
        }
        if (launcherApp && parts.length == 2) {
            intent.setAction(Intent.ACTION_MAIN);
            intent.addCategory(Intent.CATEGORY_LAUNCHER);
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        context.startActivity(intent);
    }

    private static void launchShortcut(Context context, String encodedIntent) throws Exception {
        if (encodedIntent == null || encodedIntent.isEmpty()) return;
        Intent intent = Intent.parseUri(encodedIntent, 0);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        UserLaunch.start(context, intent);
    }

    private static void toggle(Context context, int toggle) throws Exception {
        switch (toggle) {
            case 1: {
                WifiManager wifi = context.getSystemService(WifiManager.class);
                wifi.setWifiEnabled(!wifi.isWifiEnabled());
                return;
            }
            case 2: {
                BluetoothAdapter bluetooth = BluetoothAdapter.getDefaultAdapter();
                if (bluetooth != null) {
                    call(bluetooth, bluetooth.isEnabled() ? "disable" : "enable", new Class<?>[0]);
                }
                return;
            }
            case 3: {
                LocationManager locations = context.getSystemService(LocationManager.class);
                UserHandle user = currentUser();
                boolean enabled = Boolean.TRUE.equals(call(locations, "isLocationEnabledForUser",
                        new Class<?>[] {UserHandle.class}, user));
                call(locations, "setLocationEnabledForUser",
                        new Class<?>[] {boolean.class, UserHandle.class}, !enabled, user);
                return;
            }
            case 4: {
                NfcAdapter nfc = NfcAdapter.getDefaultAdapter(context);
                if (nfc != null) call(nfc, nfc.isEnabled() ? "disable" : "enable", new Class<?>[0]);
                return;
            }
            case 5: {
                AudioManager audio = context.getSystemService(AudioManager.class);
                int mode = audio.getRingerMode();
                audio.setRingerMode(nextRingerMode(mode));
                return;
            }
            case 6:
                Settings.System.putInt(context.getContentResolver(),
                        Settings.System.SCREEN_BRIGHTNESS_MODE,
                        Settings.System.getInt(context.getContentResolver(),
                                Settings.System.SCREEN_BRIGHTNESS_MODE, 0) == 0 ? 1 : 0);
                return;
            case 7:
                Settings.System.putInt(context.getContentResolver(),
                        Settings.System.ACCELEROMETER_ROTATION,
                        Settings.System.getInt(context.getContentResolver(),
                                Settings.System.ACCELEROMETER_ROTATION, 0) == 0 ? 1 : 0);
                return;
            case 8:
                toggleFlashlight(context);
                return;
            case 9: {
                TelephonyManager telephony = context.getSystemService(TelephonyManager.class);
                boolean dataEnabled = Boolean.TRUE.equals(call(telephony, "isDataEnabled",
                        new Class<?>[0]));
                call(telephony, "setDataEnabledForReason", new Class<?>[] {int.class, boolean.class},
                        0, !dataEnabled);
                return;
            }
            case 10: {
                toggleHotspot(context);
                return;
            }
            case 11: {
                NotificationManager notifications = context.getSystemService(NotificationManager.class);
                int filter = notifications.getCurrentInterruptionFilter();
                notifications.setInterruptionFilter(filter == NotificationManager.INTERRUPTION_FILTER_ALL
                        ? NotificationManager.INTERRUPTION_FILTER_NONE
                        : NotificationManager.INTERRUPTION_FILTER_ALL);
                return;
            }
            case 12: {
                UiModeManager uiMode = context.getSystemService(UiModeManager.class);
                boolean dark = (context.getResources().getConfiguration().uiMode
                        & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
                uiMode.setNightMode(dark
                        ? UiModeManager.MODE_NIGHT_NO : UiModeManager.MODE_NIGHT_YES);
                return;
            }
            default:
                Log.w(TAG, "unsupported desktop toggle=" + toggle);
        }
    }

    private static void toggleFlashlight(Context context) throws Exception {
        Object controller = dependency(context,
                "com.android.systemui.statusbar.policy.FlashlightController");
        if (controller == null) {
            try {
                Class<?> stubType = Class.forName("miui.stub.MiuiStub", false, context.getClassLoader());
                Object stub = readField(null, stubType, "INSTANCE");
                Object provider = readField(stub, stubType, "mSysUIProvider");
                Object lazy = readField(provider, provider.getClass(), "mFlashlightController");
                controller = call(lazy, "get", new Class<?>[0]);
            } catch (Exception error) {
                Log.d(TAG, "MIUI flashlight controller unavailable", error);
            }
        }
        if (controller != null) {
            boolean enabled = Boolean.TRUE.equals(call(controller, "isEnabled", new Class<?>[0]));
            call(controller, "setFlashlight", new Class<?>[] {boolean.class}, !enabled);
            return;
        }
        CameraManager cameras = context.getSystemService(CameraManager.class);
        String cameraId = flashlightCamera(cameras);
        if (cameraId == null) return;
        Boolean enabled = TORCH_STATES.get(cameraId);
        if (enabled != null) {
            cameras.setTorchMode(cameraId, !enabled);
            TORCH_STATES.put(cameraId, !enabled);
            return;
        }
        // The first callback supplies the actual torch state, including a torch
        // that was already enabled before this bridge started.
        PENDING_TORCH_TOGGLES.merge(cameraId, true, (previous, request) -> previous != request);
        if (TORCH_CALLBACK_REGISTERED.compareAndSet(false, true)) {
            try {
                cameras.registerTorchCallback(new CameraManager.TorchCallback() {
                    @Override public void onTorchModeChanged(String id, boolean current) {
                        TORCH_STATES.put(id, current);
                        if (Boolean.TRUE.equals(PENDING_TORCH_TOGGLES.remove(id))) {
                            try {
                                cameras.setTorchMode(id, !current);
                                TORCH_STATES.put(id, !current);
                            } catch (Exception error) {
                                Log.w(TAG, "queued flashlight toggle failed", error);
                            }
                        }
                    }

                    @Override public void onTorchModeUnavailable(String id) {
                        TORCH_STATES.remove(id);
                        PENDING_TORCH_TOGGLES.remove(id);
                    }
                }, MAIN);
            } catch (Exception error) {
                TORCH_CALLBACK_REGISTERED.set(false);
                PENDING_TORCH_TOGGLES.remove(cameraId);
                throw error;
            }
        }
    }

    private static String flashlightCamera(CameraManager cameras) throws Exception {
        if (cameras == null) return null;
        String fallback = null;
        for (String id : cameras.getCameraIdList()) {
            CameraCharacteristics characteristics = cameras.getCameraCharacteristics(id);
            if (Boolean.TRUE.equals(characteristics.get(CameraCharacteristics.FLASH_INFO_AVAILABLE))) {
                if (Integer.valueOf(CameraCharacteristics.LENS_FACING_BACK).equals(
                        characteristics.get(CameraCharacteristics.LENS_FACING))) return id;
                fallback = id;
            }
        }
        return fallback;
    }

    static int nextRingerMode(int current) {
        return current == AudioManager.RINGER_MODE_NORMAL
                ? AudioManager.RINGER_MODE_SILENT : current + 1;
    }

    private static UserHandle currentUser() throws Exception {
        Object id = callStatic(Class.forName("android.app.ActivityManager"), "getCurrentUser");
        if (!(id instanceof Integer userId)) throw new IllegalStateException("current user unavailable");
        return UserHandle.getUserHandleForUid(userId * 100000);
    }

    private static Object dependency(Context context, String className) {
        try {
            ClassLoader loader = context.getClassLoader();
            Class<?> dependency = Class.forName("com.android.systemui.Dependency", false, loader);
            Class<?> contract = Class.forName(className, false, loader);
            try {
                Method getter = dependency.getDeclaredMethod("get", Class.class);
                getter.setAccessible(true);
                return getter.invoke(null, contract);
            } catch (NoSuchMethodException missing) {
                Object instance = readField(null, dependency, "sDependency");
                return call(instance, "getDependencyInner", new Class<?>[] {Object.class}, contract);
            }
        } catch (Throwable error) {
            Log.d(TAG, "SystemUI dependency unavailable " + className, error);
            return null;
        }
    }

    private static void toggleHotspot(Context context) throws Exception {
        Object controller = dependency(context,
                "com.android.systemui.statusbar.policy.HotspotController");
        if (controller != null) {
            if (!Boolean.TRUE.equals(call(controller, "isHotspotSupported", new Class<?>[0]))) return;
            boolean enabled = Boolean.TRUE.equals(call(controller, "isHotspotEnabled", new Class<?>[0]));
            call(controller, "setHotspotEnabled", new Class<?>[] {boolean.class}, !enabled);
            return;
        }
        Object tethering = context.getSystemService(Class.forName("android.net.TetheringManager"));
        if (tethering == null) throw new IllegalStateException("tethering service unavailable");
        WifiManager wifi = context.getSystemService(WifiManager.class);
        boolean enabled = Boolean.TRUE.equals(call(wifi, "isWifiApEnabled", new Class<?>[0]));
        if (enabled) {
            call(tethering, "stopTethering", new Class<?>[] {int.class}, 0);
            return;
        }
        ClassLoader loader = tethering.getClass().getClassLoader();
        Class<?> requestType = Class.forName("android.net.TetheringManager$TetheringRequest", false, loader);
        Class<?> builderType = Class.forName("android.net.TetheringManager$TetheringRequest$Builder", false, loader);
        Class<?> callbackType = Class.forName("android.net.TetheringManager$StartTetheringCallback", false, loader);
        Object builder = builderType.getConstructor(int.class).newInstance(0);
        call(builder, "setShouldShowEntitlementUi", new Class<?>[] {boolean.class}, false);
        Object request = call(builder, "build", new Class<?>[0]);
        Object callback = Proxy.newProxyInstance(callbackType.getClassLoader(),
                new Class<?>[] {callbackType}, (proxy, method, args) -> {
                    if (method.getDeclaringClass() == Object.class) {
                        if ("hashCode".equals(method.getName())) return System.identityHashCode(proxy);
                        if ("equals".equals(method.getName())) return proxy == args[0];
                        return "FusionStatusBarTetheringCallback";
                    }
                    if ("onTetheringFailed".equals(method.getName())) {
                        Log.w(TAG, "hotspot start failed code=" + (args == null ? "unknown" : args[0]));
                    }
                    return null;
                });
        Executor executor = MAIN::post;
        call(tethering, "startTethering", new Class<?>[] {requestType, Executor.class, callbackType},
                request, executor, callback);
    }

    private static Object callStatic(Class<?> owner, String name) throws Exception {
        Method method = owner.getDeclaredMethod(name);
        method.setAccessible(true);
        return method.invoke(null);
    }

    private static Object readField(Object target, Class<?> owner, String name) throws Exception {
        for (Class<?> type = owner; type != null; type = type.getSuperclass()) {
            try {
                Field field = type.getDeclaredField(name);
                field.setAccessible(true);
                return field.get(target);
            } catch (NoSuchFieldException ignored) { }
        }
        throw new NoSuchFieldException(owner.getName() + "#" + name);
    }

    private static Object call(Object target, String name, Class<?>[] parameters, Object... args)
            throws Exception {
        if (target == null) return null;
        for (Class<?> type = target.getClass(); type != null; type = type.getSuperclass()) {
            try {
                Method method = type.getDeclaredMethod(name, parameters);
                method.setAccessible(true);
                return method.invoke(target, args);
            } catch (NoSuchMethodException ignored) { }
        }
        throw new NoSuchMethodException(target.getClass().getName() + "#" + name);
    }

    private static final class UserLaunch {
        static void start(Context context, Intent intent) {
            Object profile = intent.getParcelableExtra("profile");
            intent.removeExtra("profile");
            if (profile instanceof UserHandle user) {
                try {
                    Method method = Context.class.getMethod("startActivityAsUser", Intent.class,
                            UserHandle.class);
                    method.invoke(context, intent, user);
                    return;
                } catch (Throwable error) {
                    Log.w(TAG, "profile shortcut launch unavailable", error);
                }
            }
            context.startActivity(intent);
        }
    }
}
