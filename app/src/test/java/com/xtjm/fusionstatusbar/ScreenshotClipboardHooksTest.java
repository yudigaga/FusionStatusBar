package com.xtjm.fusionstatusbar;

import io.github.libxposed.api.XposedInterface;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.Test;
import static org.junit.Assert.*;

public class ScreenshotClipboardHooksTest {
    private static final String KEY = "mi_screen_shots_write_clipboard_enable";
    // Distinct host operation values catch accidental dependence on AOSP's numeric constants.
    private static final int[] OPS = {17, 19, 23, 29};

    @Test public void onlyKnownBooleanMutationSignaturesAreSelected() {
        var methods = ScreenshotClipboardHooks.supportedMethods(Provider.class);
        assertEquals(2, methods.size());
        assertTrue(methods.stream().allMatch(method -> method.getName().equals("mutateSecureSetting")
                && method.getReturnType() == boolean.class));
        assertEquals(List.of(8, 9), methods.stream().map(method -> method.getParameterCount())
                .sorted().toList());
        assertTrue(ScreenshotClipboardHooks.supportedMethods(WrongReturnProvider.class).isEmpty());
    }

    @Test public void enabledInsertUpdateAndDeleteKeepOneWhilePreservingEveryOtherArgument() throws Throwable {
        for (int count : new int[] {8, 9}) {
            for (int operation : new int[] {17, 19, 23}) {
                Object[] input = arguments(KEY, "0", operation, count);
                Object[] unchanged = input.clone();
                NativeMutation nativeCall = new NativeMutation(input);
                Object result = ScreenshotClipboardHooks.interceptMutation(nativeCall.chain(), true,
                        OPS, () -> fail("individual guarded writes must not trigger a bulk repair"));
                assertEquals(Boolean.TRUE, result);
                Object[] expected = count == 8
                        ? new Object[] {KEY, "1", "audit-tag", true, 10, 17, true, 41}
                        : new Object[] {KEY, "1", "audit-tag", true, 10, 17, true, 41, true};
                assertArrayEquals(expected, nativeCall.received);
                assertArrayEquals("interception must not mutate the caller's argument list", unchanged, input);
                assertEquals(1, nativeCall.calls);
            }
        }
    }

    @Test public void nullWritesAreGuardedLikeOtherInsertValues() throws Throwable {
        NativeMutation nativeCall = new NativeMutation(arguments(KEY, null, 17, 9));
        ScreenshotClipboardHooks.interceptMutation(nativeCall.chain(), true, OPS,
                () -> fail("a guarded insertion needs no bulk repair"));
        assertArrayEquals(new Object[] {KEY, "1", "audit-tag", true, 10, 17, true, 41, true},
                nativeCall.received);
    }

    @Test public void disablingPreservesNativeOperationsAndResultsWithoutRepairing() throws Throwable {
        for (int operation : new int[] {17, 19, 23, 29}) {
            Object[] input = arguments(KEY, "0", operation, 8);
            NativeMutation nativeCall = new NativeMutation(input);
            nativeCall.result = false;
            assertEquals(Boolean.FALSE, ScreenshotClipboardHooks.interceptMutation(nativeCall.chain(),
                    false, OPS, () -> fail("disabled protection must not repair settings")));
            assertArrayEquals(input, nativeCall.received);
            assertEquals(1, nativeCall.calls);
        }
    }

    @Test public void otherSettingsAndUnknownOperationsRemainUnchanged() throws Throwable {
        for (Object[] input : new Object[][] {
                arguments("unrelated_secure_setting", "0", 17, 8),
                arguments("unrelated_secure_setting", null, 19, 9),
                arguments(KEY, "0", 999, 9)}) {
            NativeMutation nativeCall = new NativeMutation(input);
            assertEquals(Boolean.TRUE, ScreenshotClipboardHooks.interceptMutation(nativeCall.chain(),
                    true, OPS, () -> fail("unrelated operations must not request repair")));
            assertArrayEquals(input, nativeCall.received);
            assertEquals(1, nativeCall.calls);
        }
    }

    @Test public void successfulBulkAndNamedResetsRunUnchangedBeforeRepair() throws Throwable {
        for (String name : new String[] {null, KEY}) {
            for (int count : new int[] {8, 9}) {
                Object[] input = arguments(name, null, 29, count);
                NativeMutation nativeCall = new NativeMutation(input);
                assertEquals(Boolean.TRUE, ScreenshotClipboardHooks.interceptMutation(nativeCall.chain(),
                        true, OPS, () -> nativeCall.events.add("repair")));
                assertArrayEquals("the original reset must still cover its full scope", input,
                        nativeCall.received);
                assertEquals(List.of("original", "repair"), nativeCall.events);
                assertEquals(1, nativeCall.calls);
            }
        }
    }

    @Test public void nativeFalseResultsDoNotCausePrivilegedRepair() throws Throwable {
        for (int operation : new int[] {17, 29}) {
            NativeMutation nativeCall = new NativeMutation(arguments(KEY, "0", operation, 9));
            nativeCall.result = false;
            assertEquals(Boolean.FALSE, ScreenshotClipboardHooks.interceptMutation(nativeCall.chain(),
                    true, OPS, () -> fail("failed native mutations must not schedule repair")));
            assertEquals(List.of("original"), nativeCall.events);
        }
    }

    @Test public void nativePermissionFailuresPropagateWithoutRepair() {
        for (int operation : new int[] {17, 29}) {
            NativeMutation nativeCall = new NativeMutation(arguments(KEY, "0", operation, 8));
            SecurityException failure = new SecurityException("permission denied by provider");
            nativeCall.failure = failure;
            assertSame(failure, assertThrows(SecurityException.class,
                    () -> ScreenshotClipboardHooks.interceptMutation(nativeCall.chain(), true, OPS,
                            () -> fail("a rejected caller must not cause privileged repair"))));
            assertEquals(List.of("original"), nativeCall.events);
        }
    }

    @Test public void aSettingChangeDuringAnInflightMutationReconcilesTheLatestChoice() throws Throwable {
        for (boolean initiallyEnabled : new boolean[] {true, false}) {
            AtomicBoolean enabled = new AtomicBoolean(initiallyEnabled);
            NativeMutation nativeCall = new NativeMutation(arguments(KEY, "0", 17, 9));
            nativeCall.onProceed = () -> enabled.set(!initiallyEnabled);
            int[] reconciliations = {0};
            assertEquals(Boolean.TRUE, ScreenshotClipboardHooks.interceptCurrentMutation(nativeCall.chain(),
                    OPS, enabled::get, () -> reconciliations[0]++));
            assertEquals("an in-flight write must not leave the previous switch state behind", 1,
                    reconciliations[0]);
        }
    }

    private static Object[] arguments(String name, String value, int operation, int count) {
        return count == 8
                ? new Object[] {name, value, "audit-tag", true, 10, operation, true, 41}
                : new Object[] {name, value, "audit-tag", true, 10, operation, true, 41, true};
    }

    private static final class NativeMutation {
        final Object[] input;
        final List<String> events = new ArrayList<>();
        Object[] received;
        int calls;
        boolean result = true;
        Throwable failure;
        Runnable onProceed = () -> { };

        NativeMutation(Object[] input) { this.input = input; }

        XposedInterface.Chain chain() {
            return (XposedInterface.Chain) Proxy.newProxyInstance(getClass().getClassLoader(),
                    new Class<?>[] {XposedInterface.Chain.class}, (proxy, method, parameters) -> {
                        if (method.getName().equals("getArgs"))
                            return Collections.unmodifiableList(Arrays.asList(input));
                        if (method.getName().equals("proceed")) {
                            calls++;
                            events.add("original");
                            received = parameters == null || parameters.length == 0
                                    ? input.clone() : ((Object[]) parameters[0]).clone();
                            if (failure != null) throw failure;
                            onProceed.run();
                            return result;
                        }
                        throw new UnsupportedOperationException(method.getName());
                    });
        }
    }

    private static class Provider {
        boolean mutateSecureSetting(String name, String value, String tag, boolean makeDefault,
                int user, int operation, boolean forceNotify, int mode) { return false; }
        boolean mutateSecureSetting(String name, String value, String tag, boolean makeDefault,
                int user, int operation, boolean forceNotify, int mode, boolean restore) { return false; }
        boolean mutateSecureSetting(String name, String value, String tag, boolean makeDefault,
                int user, long operation, boolean forceNotify, int mode) { return false; }
        boolean mutateSecureSetting(String name, String value, String tag, boolean makeDefault,
                int user, int operation, boolean forceNotify, int mode, String restore) { return false; }
        boolean updateOtherSetting(String name, String value, String tag, boolean makeDefault,
                int user, int operation, boolean forceNotify, int mode) { return false; }
    }

    private static class WrongReturnProvider {
        int mutateSecureSetting(String name, String value, String tag, boolean makeDefault,
                int user, int operation, boolean forceNotify, int mode) { return 0; }
    }
}
