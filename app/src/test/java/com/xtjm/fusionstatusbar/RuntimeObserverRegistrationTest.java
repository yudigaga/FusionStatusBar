package com.xtjm.fusionstatusbar;

import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, manifest = Config.NONE)
public class RuntimeObserverRegistrationTest {
    @Test public void retriesOnlyFailedSubscriptions() {
        List<Runnable> retries = new ArrayList<>();
        int[] calls = new int[3];
        RuntimeObserverRegistration registration = new RuntimeObserverRegistration((task, delay) -> retries.add(task),
                () -> calls[0]++, () -> { if (++calls[1] == 1) throw new SecurityException("locked"); },
                () -> calls[2]++);
        registration.ensureRegistered();
        assertArrayEquals(new int[] {1, 1, 1}, calls);
        assertEquals(1, retries.size());
        retries.remove(0).run();
        registration.ensureRegistered();
        assertArrayEquals(new int[] {1, 2, 1}, calls);
        assertTrue(retries.isEmpty());
    }

    @Test public void persistentFailureHasBoundedScheduledRetries() {
        List<Runnable> retries = new ArrayList<>();
        int[] calls = {0};
        RuntimeObserverRegistration registration = new RuntimeObserverRegistration((task, delay) -> retries.add(task),
                () -> { calls[0]++; throw new IllegalStateException("unavailable"); });
        registration.ensureRegistered();
        while (!retries.isEmpty()) retries.remove(0).run();
        assertEquals(7, calls[0]);
    }
}
