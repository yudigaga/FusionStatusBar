package com.xtjm.fusionstatusbar;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.provider.Settings;
import android.widget.TextView;
import io.github.libxposed.api.XposedInterface;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowSensor;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class SecurityCenterPrivacyHooksTest {
    private Context context;
    private SensorManager sensors;

    @Before public void clearHardwareAndPreferences() {
        context = RuntimeEnvironment.getApplication();
        sensors = context.getSystemService(SensorManager.class);
        for (Sensor sensor : new ArrayList<>(sensors.getSensorList(Sensor.TYPE_ALL)))
            Shadows.shadowOf(sensors).removeSensor(sensor);
        Settings.System.putString(context.getContentResolver(), "motion_sickness_relief_remind_time", null);
        Settings.System.putString(context.getContentResolver(), "settings_car_sickness_mode", null);
    }

    @Test public void onlyScoreModeNumericModeAndItsTextRendererAreSelected() {
        var methods = SecurityCenterPrivacyHooks.scoreMethods(ScoreView.class);
        assertEquals(3, methods.size());
        assertTrue(methods.stream().anyMatch(method -> method.getName().equals("setScore")));
        assertTrue(methods.stream().anyMatch(method -> method.getName().equals("setNumber")));
        assertTrue(methods.stream().anyMatch(method -> method.getName().equals("d")));
        assertTrue(methods.stream().noneMatch(method -> method.getName().equals("setFlipScore")));
    }

    @Test public void lockingOnlyTheRenderedTextPreservesTheNativeScoreAndInput() throws Throwable {
        ScoreView view = new ScoreView();
        Setter original = new Setter(view, 42, true);
        assertEquals("rendered", SecurityCenterPrivacyHooks.interceptScore(original.chain(), true));
        assertArrayEquals(new Object[] {42}, original.received);
        assertArrayEquals(new Object[] {42}, original.input);
        assertEquals(42, view.getTextScore());
        assertEquals("666", view.getText().toString());
        assertEquals(1, original.calls);
    }

    @Test public void numberModeAndItsAnimationFramesRemainNative() throws Throwable {
        ScoreView view = new ScoreView();
        SecurityCenterPrivacyHooks.interceptScore(new Setter(view, 42, true).chain(), true);
        assertEquals("666", view.getText().toString());
        Setter number = new Setter(view, 72, true);
        assertEquals("rendered", SecurityCenterPrivacyHooks.interceptNumber(number.chain()));
        assertArrayEquals(new Object[] {72}, number.received);
        assertEquals(72, view.getTextScore());
        assertEquals("72", view.getText().toString());
        SecurityCenterPrivacyHooks.interceptRender(new Renderer(view, "73").chain(), true);
        assertEquals("73", view.getText().toString());
    }

    @Test public void disablingPreservesNativeDisplayValuesAndErrors() throws Throwable {
        ScoreView view = new ScoreView();
        Setter original = new Setter(view, 72, false);
        assertEquals("rendered", SecurityCenterPrivacyHooks.interceptScore(original.chain(), false));
        assertArrayEquals(new Object[] {72}, original.received);
        assertEquals(72, view.getTextScore());
        assertEquals("72", view.getText().toString());
        IllegalStateException failure = new IllegalStateException("native renderer unavailable");
        original.failure = failure;
        assertSame(failure, assertThrows(IllegalStateException.class,
                () -> SecurityCenterPrivacyHooks.interceptScore(original.chain(), true)));
    }

    @Test public void disablingRestoresTheCurrentNativeFrameWithoutAnotherScoreUpdate() throws Throwable {
        ScoreView view = new ScoreView();
        SecurityCenterPrivacyHooks.interceptScore(new Setter(view, 42, true).chain(), true);
        SecurityCenterPrivacyHooks.interceptRender(new Renderer(view, "43").chain(), true);
        assertEquals("666", view.getText().toString());
        assertEquals(42, view.getTextScore());
        SecurityCenterPrivacyHooks.refreshScores(false);
        assertEquals("43", view.getText().toString());
        SecurityCenterPrivacyHooks.refreshScores(true);
        assertEquals("666", view.getText().toString());
        assertEquals(42, view.getTextScore());
    }

    @Test public void motionUnlockStillRequiresBothRealMotionSensors() {
        assertFalse(SecurityCenterPrivacyHooks.motionSensorsAvailable(context));
        Shadows.shadowOf(sensors).addSensor(ShadowSensor.newInstance(Sensor.TYPE_LINEAR_ACCELERATION));
        assertFalse(SecurityCenterPrivacyHooks.motionSensorsAvailable(context));
        Shadows.shadowOf(sensors).addSensor(ShadowSensor.newInstance(Sensor.TYPE_GYROSCOPE));
        assertTrue(SecurityCenterPrivacyHooks.motionSensorsAvailable(context));
        assertFalse(SecurityCenterPrivacyHooks.motionSensorsAvailable(null));
    }

    @Test public void carCrashUnlockDoesNotInventTheSpecializedDetectionSensor() {
        Shadows.shadowOf(sensors).addSensor(ShadowSensor.newInstance(Sensor.TYPE_GYROSCOPE));
        assertFalse(SecurityCenterPrivacyHooks.crashSensorAvailable(context));
        Shadows.shadowOf(sensors).addSensor(ShadowSensor.newInstance(33171623));
        assertTrue(SecurityCenterPrivacyHooks.crashSensorAvailable(context));
        assertFalse(SecurityCenterPrivacyHooks.crashSensorAvailable(null));
    }

    @Test public void reminderChoiceUsesTheSameStoredSettingAsTheNativeDropdown() {
        Settings.System.putInt(context.getContentResolver(), "motion_sickness_relief_remind_time", 0);
        assertEquals(0, SecurityCenterPrivacyHooks.reminderTime(context));
        Settings.System.putInt(context.getContentResolver(), "motion_sickness_relief_remind_time", 1);
        assertEquals(1, SecurityCenterPrivacyHooks.reminderTime(context));
    }

    @Test public void motionEnableStateStillFollowsTheUsersRealSystemSwitch() {
        assertFalse(SecurityCenterPrivacyHooks.motionEnabled(context));
        assertFalse(SecurityCenterPrivacyHooks.motionEnabled(null));
        Settings.System.putInt(context.getContentResolver(), "settings_car_sickness_mode", 0);
        assertFalse(SecurityCenterPrivacyHooks.motionEnabled(context));
        Settings.System.putInt(context.getContentResolver(), "settings_car_sickness_mode", 1);
        assertTrue(SecurityCenterPrivacyHooks.motionEnabled(context));
        Settings.System.putInt(context.getContentResolver(), "settings_car_sickness_mode", 2);
        assertFalse(SecurityCenterPrivacyHooks.motionEnabled(context));
    }

    @Test public void invalidOrMissingReminderModesUseAlwaysWithoutOverwritingTheSetting() {
        assertEquals(1, SecurityCenterPrivacyHooks.reminderTime(context));
        assertEquals(1, SecurityCenterPrivacyHooks.reminderTime(null));
        Settings.System.putInt(context.getContentResolver(), "motion_sickness_relief_remind_time", -7);
        assertEquals(1, SecurityCenterPrivacyHooks.reminderTime(context));
        assertEquals(-7, Settings.System.getInt(context.getContentResolver(),
                "motion_sickness_relief_remind_time", 0));
    }

    private static XposedInterface.Chain chain(Object receiver, Object[] input, NativeCall nativeCall) {
        return (XposedInterface.Chain) Proxy.newProxyInstance(SecurityCenterPrivacyHooksTest.class.getClassLoader(),
                new Class<?>[] {XposedInterface.Chain.class}, (proxy, method, args) -> {
                    if (method.getName().equals("getArgs"))
                        return Collections.unmodifiableList(Arrays.asList(input));
                    if (method.getName().equals("getThisObject")) return receiver;
                    if (method.getName().equals("proceed")) return nativeCall.proceed(
                            args == null || args.length == 0 ? input.clone() : ((Object[]) args[0]).clone());
                    throw new UnsupportedOperationException(method.getName());
                });
    }

    private interface NativeCall { Object proceed(Object[] args) throws Throwable; }

    private static final class Setter {
        final ScoreView view;
        final Object[] input;
        final boolean renderingEnabled;
        Object[] received;
        int calls;
        RuntimeException failure;
        Setter(ScoreView view, int value, boolean enabled) {
            this.view = view; input = new Object[] {value}; renderingEnabled = enabled;
        }
        XposedInterface.Chain chain() {
            return SecurityCenterPrivacyHooksTest.chain(view, input, args -> {
                calls++; received = args;
                if (failure != null) throw failure;
                view.nativeScore = (Integer) args[0];
                SecurityCenterPrivacyHooks.interceptRender(new Renderer(view,
                        Integer.toString(view.nativeScore)).chain(), renderingEnabled);
                return "rendered";
            });
        }
    }

    private static final class Renderer {
        final ScoreView view;
        final Object[] input;
        Renderer(ScoreView view, String text) { this.view = view; input = new Object[] {text}; }
        XposedInterface.Chain chain() {
            return SecurityCenterPrivacyHooksTest.chain(view, input, args -> {
                view.setText((CharSequence) args[0]);
                return null;
            });
        }
    }

    private static class ScoreView extends TextView {
        int nativeScore;
        ScoreView() { super(RuntimeEnvironment.getApplication()); }
        int getTextScore() { return nativeScore; }
        void setScore(int value) { }
        void setFlipScore(int value) { }
        void setNumber(int value) { }
        private void d(CharSequence text) { setText(text); }
        void setScore(float value) { }
        int setNumber(long value) { return 0; }
    }
}
