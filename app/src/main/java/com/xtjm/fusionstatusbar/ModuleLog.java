package com.xtjm.fusionstatusbar;

import android.util.Log;
import java.util.ArrayDeque;

/** Process-local gate for logs explicitly written by Fusion. */
final class ModuleLog {
    private enum State { PENDING, OFF, ON }
    private static final int STARTUP_LIMIT = 48;
    private static final ArrayDeque<Runnable> startup = new ArrayDeque<>();
    private static volatile State state = State.ON;
    private static volatile boolean injected;

    private ModuleLog() { }

    static synchronized void awaitConfiguration() {
        startup.clear();
        state = State.PENDING;
    }

    static void startInjected() {
        awaitConfiguration();
        injected = true;
    }

    static boolean isInjected() { return injected; }

    static synchronized void configure(boolean enabled) {
        if (!enabled) {
            startup.clear();
            state = State.OFF;
            return;
        }
        while (!startup.isEmpty()) startup.removeFirst().run();
        state = State.ON;
    }

    static boolean acceptsDiagnostics() {
        return state != State.OFF;
    }

    static boolean enabled() {
        return state == State.ON;
    }

    static void write(Runnable output) {
        if (state == State.OFF) return;
        synchronized (ModuleLog.class) {
            if (state == State.ON) {
                output.run();
            } else if (state == State.PENDING && startup.size() < STARTUP_LIMIT) {
                startup.addLast(output);
            }
        }
    }

    static void d(String tag, String message, Throwable error) { write(() -> Log.d(tag, message, error)); }
    static void i(String tag, String message) { write(() -> Log.i(tag, message)); }
    static void w(String tag, String message) { write(() -> Log.w(tag, message)); }
    static void w(String tag, String message, Throwable error) { write(() -> Log.w(tag, message, error)); }
    static void e(String tag, String message) { write(() -> Log.e(tag, message)); }
    static void e(String tag, String message, Throwable error) { write(() -> Log.e(tag, message, error)); }
    static void println(int priority, String tag, String message) {
        write(() -> Log.println(priority, tag, message));
    }
}
