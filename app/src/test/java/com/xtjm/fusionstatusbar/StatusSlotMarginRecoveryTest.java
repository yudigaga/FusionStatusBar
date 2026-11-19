package com.xtjm.fusionstatusbar;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import java.lang.reflect.Constructor;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

/** Real Android measurement of the production slot, not a replay of device feedback. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, manifest = Config.NONE)
public class StatusSlotMarginRecoveryTest {
    @Test public void freshWideSlotRetainsConfiguredMargins() throws Exception {
        Fixture f = new Fixture(80);
        f.measure();
        f.assertRestored();
    }
    @Test public void nativeContentShrinksAtSameRootWidthRestoresMargins() throws Exception {
        Fixture f = new Fixture(320);
        f.measure();
        assertEquals(0, f.readout.getMeasuredWidth());
        assertEquals(0, f.params.leftMargin + f.params.rightMargin);
        f.nativeView.getLayoutParams().width = 80;
        f.nativeView.requestLayout();
        f.measure();
        f.assertRestored();
    }
    @Test public void repeatedConstraintCyclesDoNotConsumeConfiguredMargins() throws Exception {
        Fixture f = new Fixture(80);
        for (int cycle=0; cycle<3; cycle++) {
            f.nativeView.getLayoutParams().width=320;
            f.nativeView.requestLayout(); f.measure();
            assertEquals(0,f.readout.getMeasuredWidth());
            f.nativeView.getLayoutParams().width=80;
            f.nativeView.requestLayout(); f.measure(); f.assertRestored();
        }
    }
    @Test public void newLayoutConfigurationReplacesSavedMarginsIncludingZero() throws Exception {
        Fixture f = new Fixture(320); f.measure();
        f.params.leftMargin=4; f.params.rightMargin=6;
        f.readout.setLayoutParams(f.params);
        f.measure();
        f.nativeView.getLayoutParams().width=80;
        f.nativeView.requestLayout(); f.measure();
        assertEquals(4,f.params.leftMargin); assertEquals(6,f.params.rightMargin);
        f.params.leftMargin=0; f.params.rightMargin=0;
        f.readout.setLayoutParams(f.params); f.measure();
        assertEquals(0,f.params.leftMargin); assertEquals(0,f.params.rightMargin);
        assertEquals(160,f.readout.getMeasuredWidth());
    }
    private static class Fixture {
        final LinearLayout slot;
        final StatusPairView readout;
        final View nativeView;
        final LinearLayout.LayoutParams params;
        Fixture(int nativeWidth) throws Exception {
            Context c = RuntimeEnvironment.getApplication();
            Constructor<?> ctor = Class.forName(DualRowStatusBarLayout.class.getName()+"$StatusSlotLayout")
                    .getDeclaredConstructor(Context.class, int.class);
            ctor.setAccessible(true);
            slot=(LinearLayout)ctor.newInstance(c, Gravity.END);
            readout=new StatusPairView(c);readout.setLines("1KB/s");readout.setPreferredWidth(160);
            params=new LinearLayout.LayoutParams(0,48);params.leftMargin=10;params.rightMargin=12;
            slot.addView(readout,params);
            nativeView=new View(c);nativeView.setBackgroundColor(0xffffffff);
            slot.addView(nativeView,new LinearLayout.LayoutParams(nativeWidth,48));
        }
        void measure() {
            slot.requestLayout();
            slot.measure(View.MeasureSpec.makeMeasureSpec(320,View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(48,View.MeasureSpec.EXACTLY));
            slot.layout(0,0,320,48);
        }
        void assertRestored() {
            assertEquals("Same configured left margin after native width changes",10,params.leftMargin);
            assertEquals("Same configured right margin after native width changes",12,params.rightMargin);
            assertEquals(160,readout.getMeasuredWidth());
        }
    }
}
