package com.xtjm.fusionstatusbar;

import android.view.View;
import java.util.HashSet;
import java.util.Set;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class ControlCenterGlassCapabilitiesTest {
    @Test public void legacyBlurMixAndBloomMethodsDoNotCountAsModernGlassApis() {
        ControlCenterGlassCapabilities.Snapshot snapshot = ControlCenterGlassCapabilities.inspect(LegacyApi.class);
        assertEquals(0, snapshot.available());
        assertEquals(Set.of("setMiGlass", "setMiGlassBlurRadius", "setMiGlassClip", "setMiViewMaterialType",
                "chooseBackgroundBlurContainer", "setPassWindowBlurEnabled"), new HashSet<>(snapshot.missing()));
    }

    @Test public void completeMethodInventoryNeverConstructsOrInvokesTheReportedNativeApi() {
        ModernApi.calls = 0;
        ControlCenterGlassCapabilities.Snapshot snapshot = ControlCenterGlassCapabilities.inspect(ModernApi.class);
        assertEquals(6, snapshot.available());
        assertTrue(snapshot.missing().isEmpty());
        assertEquals("Method discovery is a read-only inventory, not a compositor test or activation", 0, ModernApi.calls);
        assertSame("Repeated reports reuse the inventory instead of probing native rendering",
                snapshot, ControlCenterGlassCapabilities.inspect(ModernApi.class));
        assertEquals(0, ModernApi.calls);
    }

    @Test public void inventoryRequiresExactPrimitiveAndArraySignatures() {
        ControlCenterGlassCapabilities.Snapshot snapshot = ControlCenterGlassCapabilities.inspect(WrongSignatures.class);
        assertEquals("Only the exact four-float clip method matches", 1, snapshot.available());
        assertEquals(5, snapshot.missing().size());
        assertTrue(snapshot.missing().contains("setMiGlass"));
        assertTrue(snapshot.missing().contains("setPassWindowBlurEnabled"));
        assertFalse(snapshot.missing().contains("setMiGlassClip"));
    }

    public static final class LegacyApi {
        public void setMiViewBlurMode(int value) { throw new AssertionError("must not invoke"); }
        public void setMiBackgroundBlurMode(int value) { throw new AssertionError("must not invoke"); }
        public void setMiBackgroundBlurRadius(int value) { throw new AssertionError("must not invoke"); }
        public void setMixEffectEnabled(boolean value) { throw new AssertionError("must not invoke"); }
        public void setMiBloomStroke(float[] value) { throw new AssertionError("must not invoke"); }
    }

    public static final class ModernApi {
        static int calls;
        public ModernApi() { calls++; throw new AssertionError("must not construct"); }
        public void setMiGlass(float[] value) { calls++; throw new AssertionError("must not invoke"); }
        public void setMiGlassBlurRadius(int small, int large) { calls++; throw new AssertionError("must not invoke"); }
        public void setMiGlassClip(float left, float top, float right, float bottom) { calls++; throw new AssertionError("must not invoke"); }
        public void setMiViewMaterialType(int value) { calls++; throw new AssertionError("must not invoke"); }
        public void chooseBackgroundBlurContainer(View value) { calls++; throw new AssertionError("must not invoke"); }
        public void setPassWindowBlurEnabled(boolean value) { calls++; throw new AssertionError("must not invoke"); }
    }

    public static final class WrongSignatures {
        public void setMiGlass(Object[] value) { throw new AssertionError("must not invoke"); }
        public void setPassWindowBlurEnabled(Boolean value) { throw new AssertionError("must not invoke"); }
        public void setMiGlassClip(float left, float top, float right, float bottom) { throw new AssertionError("must not invoke"); }
    }
}
