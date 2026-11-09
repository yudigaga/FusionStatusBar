package com.xtjm.fusionstatusbar;

import android.util.Log;
import android.os.Handler;
import android.os.Looper;
import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicBoolean;
import io.github.libxposed.api.XposedModule;
import static com.xtjm.fusionstatusbar.SecurityCenterFeature.*;

/** Game and video feature gates verified against phone manager 13.8.0. */
final class SecurityCenterGameVideoHooks {
    private static final AtomicBoolean INSTALLED = new AtomicBoolean();
    private static final String KING = "com.tencent.tmgp.sgame";
    private static final String GAME = "com.miui.gamebooster.utils.o0";
    private static final String VISION = "com.miui.gamebooster.utils.GameBoxVisionEnhanceUtils";
    private static final String DISPLAY = "com.miui.gamebooster.ui.performancetab.a";
    private static final SecurityCenterFeature[] VIDEO_FEATURES = {
            VIDEO_MORE, CINEMA_MODE, SMART_REFRESH_RATE, IMAGE_ENHANCEMENT};
    private static final SecurityCenterFeature[] DISPLAY_FEATURES = {
            INDEPENDENT_DISPLAY_UI, SUPER_RESOLUTION, FRAME_INTERPOLATION, GAME_HDR};

    private SecurityCenterGameVideoHooks() { }

    static int install(XposedModule module, ClassLoader loader) {
        if (!INSTALLED.compareAndSet(false, true)) return 0;
        int count = 0;
        count += packageGate(module, loader, "t9.l", "m", GAME_FILL_DISPLAY);
        count += SecurityCenterHookSupport.forceBoolean(module, loader, GAME_FILL_DISPLAY, "t9.l", "o");
        count += SecurityCenterHookSupport.forceBooleanAny(module, loader, VIDEO_FEATURES, "lb.v", "f");
        count += SecurityCenterHookSupport.forceBooleanAny(module, loader, VIDEO_FEATURES, "lb.v", "g");
        count += SecurityCenterHookSupport.forceBoolean(module, loader, VIDEO_MORE, "lb.x", "A");
        count += SecurityCenterHookSupport.forceBoolean(module, loader, VIDEO_MORE, "lb.x", "H");
        count += packageGate(module, loader, "lb.x", "x", VIDEO_MORE);
        count += packageGate(module, loader, "lb.x", "z", VIDEO_MORE);
        count += SecurityCenterHookSupport.forceBooleanAny(module, loader,
                new SecurityCenterFeature[] {VIDEO_MORE, IMAGE_ENHANCEMENT}, "lb.x", "I");
        count += packageGate(module, loader, "lb.x", "w", VIDEO_MORE, IMAGE_ENHANCEMENT);
        count += SecurityCenterHookSupport.forceBooleanAny(module, loader,
                new SecurityCenterFeature[] {VIDEO_MORE, IMAGE_ENHANCEMENT}, "lb.g", "l");
        count += SecurityCenterHookSupport.forceBooleanAny(module, loader,
                new SecurityCenterFeature[] {VIDEO_MORE, IMAGE_ENHANCEMENT}, "lb.g", "p");
        count += SecurityCenterHookSupport.forceValue(module, loader, VIDEO_TEMPERATURE_LIMIT,
                "hb.m", "o", boolean.class, false);
        count += SecurityCenterHookSupport.forceValue(module, loader, VIDEO_TEMPERATURE_LIMIT,
                "hb.m", "t", void.class, null);
        count += SecurityCenterHookSupport.forceBoolean(module, loader, CINEMA_MODE, "lb.q", "g");
        count += packageGate(module, loader, "lb.q", "f", CINEMA_MODE);
        count += SecurityCenterHookSupport.forceBoolean(module, loader, SMART_REFRESH_RATE, GAME, "B");
        count += SecurityCenterHookSupport.forceBoolean(module, loader, AIM_ASSIST, GAME, "h");
        count += packageGate(module, loader, "b9.a", "l", AIM_ASSIST);
        count += SecurityCenterHookSupport.forceBoolean(module, loader, GYROSCOPE_ACCELERATION,
                "com.miui.gamebooster.utils.b2", "m");
        count += packageGate(module, loader, "com.miui.gamebooster.utils.b2", "i", GYROSCOPE_ACCELERATION);
        count += SecurityCenterHookSupport.forceBoolean(module, loader, COOLING_FAN_UI, "h9.e", "n");
        count += SecurityCenterHookSupport.forceBoolean(module, loader, GAME_SOUND_UI, "s9.a", "c");
        count += SecurityCenterHookSupport.forceBooleanAny(module, loader, DISPLAY_FEATURES, GAME, "K");
        count += SecurityCenterHookSupport.forceBooleanAny(module, loader, DISPLAY_FEATURES, VISION, "l0");
        count += SecurityCenterHookSupport.forceBooleanAny(module, loader, DISPLAY_FEATURES, VISION, "O");
        count += SecurityCenterHookSupport.forceBooleanAny(module, loader, DISPLAY_FEATURES, VISION, "V");
        count += SecurityCenterHookSupport.forceBooleanAny(module, loader, DISPLAY_FEATURES, DISPLAY, "m");
        count += SecurityCenterHookSupport.forceBoolean(module, loader, INDEPENDENT_DISPLAY_UI, VISION, "Y");
        count += SecurityCenterHookSupport.forceBoolean(module, loader, SUPER_RESOLUTION, VISION, "e0");
        count += SecurityCenterHookSupport.forceBoolean(module, loader, SUPER_RESOLUTION, VISION, "f0");
        count += SecurityCenterHookSupport.forceBoolean(module, loader, SUPER_RESOLUTION, VISION, "S");
        count += SecurityCenterHookSupport.forceBoolean(module, loader, SUPER_RESOLUTION, DISPLAY, "g");
        count += SecurityCenterHookSupport.forceBoolean(module, loader, FRAME_INTERPOLATION, VISION, "Q");
        count += SecurityCenterHookSupport.forceBoolean(module, loader, FRAME_INTERPOLATION, VISION, "c0");
        count += SecurityCenterHookSupport.forceBoolean(module, loader, FRAME_INTERPOLATION, VISION, "T");
        count += SecurityCenterHookSupport.forceBoolean(module, loader, FRAME_INTERPOLATION, DISPLAY, "f");
        count += SecurityCenterHookSupport.forceBoolean(module, loader, GAME_HDR, GAME, "n");
        count += SecurityCenterHookSupport.forceBoolean(module, loader, GAME_HDR, "com.miui.gamebooster.utils.t", "b");
        count += SecurityCenterHookSupport.forceBoolean(module, loader, GAME_HDR, "com.miui.gamebooster.utils.t", "c");
        count += SecurityCenterHookSupport.forceBoolean(module, loader, GAME_HDR, DISPLAY, "l");
        count += installGameLaunch(module, loader);
        installTemperatureRestoration(module, loader);
        return count;
    }

    private static int packageGate(XposedModule module, ClassLoader loader, String owner, String name,
            SecurityCenterFeature... features) {
        try {
            Method method = Class.forName(owner, false, loader).getDeclaredMethod(name, String.class);
            if (method.getReturnType() != boolean.class) throw new NoSuchMethodException("package gate return changed");
            return SecurityCenterHookSupport.hook(module, method, "package:" + owner + "." + name, chain -> {
                if (unlockPackage(SecurityCenterHookSupport.anyEnabled(SecurityCenterConfigRuntime.current(), features),
                        (String) chain.getArgs().get(0))) return true;
                return chain.proceed();
            });
        } catch (ReflectiveOperationException | RuntimeException | LinkageError error) {
            module.log(Log.WARN, FusionModule.TAG, "package qualification gate unavailable: " + owner + "." + name, error);
            return 0;
        }
    }

    private static int installGameLaunch(XposedModule module, ClassLoader loader) {
        try {
            Class<?> owner = Class.forName("com.miui.gamebooster.service.n1", false, loader);
            Method boost = owner.getDeclaredMethod("v0", boolean.class);
            Method currentPackage = owner.getDeclaredMethod("F");
            Class<?> properties = Class.forName("com.miui.common.utils.f2", false, loader);
            Method read = properties.getDeclaredMethod("c", String.class, String.class);
            Method write = properties.getDeclaredMethod("e", String.class, String.class);
            if (boost.getReturnType() != void.class || currentPackage.getReturnType() != String.class
                    || read.getReturnType() != String.class || write.getReturnType() != void.class) return 0;
            currentPackage.setAccessible(true);
            read.setAccessible(true);
            write.setAccessible(true);
            LaunchBoost launch = new LaunchBoost(new BootProperty() {
                public String read() throws ReflectiveOperationException {
                    return (String) read.invoke(null, "debug.game.video.boot", "");
                }
                public void write(String value) throws ReflectiveOperationException {
                    write.invoke(null, "debug.game.video.boot", value);
                    if (!value.equals(read())) throw new ReflectiveOperationException("game launch property was not applied");
                }
            });
            SecurityCenterConfigRuntime.addListener(() -> {
                if (!SecurityCenterConfigRuntime.current().isEnabled(GAME_THREE_SECOND_LAUNCH)) {
                    try { launch.revoke(); }
                    catch (ReflectiveOperationException error) {
                        module.log(Log.WARN, FusionModule.TAG, "game launch cleanup failed", error);
                    }
                }
            });
            return SecurityCenterHookSupport.hook(module, boost, "game-launch-king", chain -> {
                String packageName = (String) currentPackage.invoke(chain.getThisObject());
                boolean handled = launch.intercept(SecurityCenterConfigRuntime.current().isEnabled(GAME_THREE_SECOND_LAUNCH),
                        packageName, (Boolean) chain.getArgs().get(0));
                if (!SecurityCenterConfigRuntime.current().isEnabled(GAME_THREE_SECOND_LAUNCH)) launch.revoke();
                return handled ? null : chain.proceed();
            });
        } catch (ReflectiveOperationException | RuntimeException error) {
            module.log(Log.WARN, FusionModule.TAG, "game launch gate unavailable", error);
            return 0;
        }
    }

    private static void installTemperatureRestoration(XposedModule module, ClassLoader loader) {
        try {
            Class<?> owner = Class.forName("hb.m", false, loader);
            Method instance = owner.getDeclaredMethod("m");
            Method blocked = owner.getDeclaredMethod("o");
            Method disableEffects = owner.getDeclaredMethod("t");
            if (instance.getReturnType() != owner || blocked.getReturnType() != boolean.class
                    || disableEffects.getReturnType() != void.class) return;
            instance.setAccessible(true);
            blocked.setAccessible(true);
            disableEffects.setAccessible(true);
            Handler main = new Handler(Looper.getMainLooper());
            AtomicBoolean previous = new AtomicBoolean(SecurityCenterConfigRuntime.current().isEnabled(VIDEO_TEMPERATURE_LIMIT));
            SecurityCenterConfigRuntime.addListener(() -> {
                boolean enabled = SecurityCenterConfigRuntime.current().isEnabled(VIDEO_TEMPERATURE_LIMIT);
                if (!previous.getAndSet(enabled) || enabled) return;
                main.post(() -> {
                    if (SecurityCenterConfigRuntime.current().isEnabled(VIDEO_TEMPERATURE_LIMIT)) return;
                    try {
                        Object monitor = instance.invoke(null);
                        if (Boolean.TRUE.equals(blocked.invoke(monitor))) disableEffects.invoke(monitor);
                    } catch (ReflectiveOperationException | RuntimeException error) {
                        module.log(Log.WARN, FusionModule.TAG, "video temperature protection restore failed", error);
                    }
                });
            });
        } catch (ReflectiveOperationException | RuntimeException error) {
            module.log(Log.WARN, FusionModule.TAG, "video temperature restore gate unavailable", error);
        }
    }

    interface BootProperty {
        String read() throws ReflectiveOperationException;
        void write(String value) throws ReflectiveOperationException;
    }

    static boolean unlockPackage(boolean enabled, String packageName) {
        return enabled && packageName != null && !packageName.isBlank();
    }

    static final class LaunchBoost {
        private final BootProperty property;
        private boolean owned;
        private String previous;

        LaunchBoost(BootProperty property) { this.property = property; }

        synchronized boolean intercept(boolean enabled, String packageName, boolean start)
                throws ReflectiveOperationException {
            if (!start) {
                boolean handled = owned;
                revoke();
                return handled;
            }
            if (!enabled || !KING.equals(packageName)) {
                revoke();
                return false;
            }
            String current = property.read();
            if (!owned && "true".equals(current)) return true;
            if (!owned || !"true".equals(current)) {
                property.write("true");
                previous = current;
                owned = true;
            }
            return true;
        }

        synchronized void revoke() throws ReflectiveOperationException {
            if (!owned) return;
            if ("true".equals(property.read())) property.write(previous);
            owned = false;
            previous = null;
        }
    }
}
