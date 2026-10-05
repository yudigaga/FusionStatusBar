package com.xtjm.fusionstatusbar;

import org.junit.Test;
import static org.junit.Assert.*;

public class PowerMenuActionsTest {
    @Test public void bootModesUseExplicitReasonsAndTogglesNeverReboot() throws Exception {
        FakeOperations operations = new FakeOperations();
        PowerMenuActions.execute(PowerMenuActions.Action.BOOTLOADER, operations);
        assertEquals("bootloader", operations.reason);
        PowerMenuActions.execute(PowerMenuActions.Action.RECOVERY, operations);
        assertEquals("recovery", operations.reason);
        assertEquals(2, operations.reboots);
        PowerMenuActions.execute(PowerMenuActions.Action.AIRPLANE, operations);
        assertTrue(operations.airplane);
        PowerMenuActions.execute(PowerMenuActions.Action.AIRPLANE, operations);
        assertFalse(operations.airplane);
        PowerMenuActions.execute(PowerMenuActions.Action.SILENT, operations);
        assertTrue(operations.silent);
        PowerMenuActions.execute(PowerMenuActions.Action.SILENT, operations);
        assertFalse(operations.silent);
        assertEquals(2, operations.reboots);
    }

    private static final class FakeOperations implements PowerMenuActions.Operations {
        String reason;
        int reboots;
        boolean airplane, silent;
        @Override public void reboot(String reason) { this.reason = reason; reboots++; }
        @Override public boolean airplane() { return airplane; }
        @Override public void airplane(boolean value) { airplane = value; }
        @Override public boolean silent() { return silent; }
        @Override public void silent(boolean value) { silent = value; }
    }
}
