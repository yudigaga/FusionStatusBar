package com.xtjm.fusionstatusbar;

import android.content.Context;
import android.view.ViewGroup;
import android.view.WindowManager;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, manifest = Config.NONE)
public class StatusBarWindowHeightControllerTest {
    @Test public void configuredTotalHeightUpdatesTheStatusBarWindowLayoutParams() {
        Context context = RuntimeEnvironment.getApplication();
        WindowFixture controller = new WindowFixture(context);
        FusionConfig config = FusionConfig.defaults().withStatusBarHeight(76);

        assertTrue(StatusBarWindowHeightController.apply(controller, config));

        int expected = StatusBarWindowHeightController.heightPx(context, 76);
        assertEquals(expected, controller.mBarHeight);
        assertEquals(expected, controller.mLpChanged.height);
    }

    @Test public void launchAnimationRetainsFullHeightWindowWhileUpdatingConfiguredBarHeight() {
        Context context = RuntimeEnvironment.getApplication();
        WindowFixture controller = new WindowFixture(context);
        controller.mCurrentState.mIsLaunchAnimationRunning = true;

        assertTrue(StatusBarWindowHeightController.apply(controller,
                FusionConfig.defaults().withStatusBarHeight(70)));

        assertEquals(StatusBarWindowHeightController.heightPx(context, 70), controller.mBarHeight);
        assertEquals(ViewGroup.LayoutParams.MATCH_PARENT, controller.mLpChanged.height);
    }

    public static final class WindowFixture {
        public final Context mContext;
        public int mBarHeight;
        public final WindowManager.LayoutParams mLpChanged = new WindowManager.LayoutParams();
        public WindowManager.LayoutParams mLp;
        public Object mStatusBarWindowView;
        public boolean mIsAttached;
        public final State mCurrentState = new State();
        WindowFixture(Context context) { mContext = context; }
        public static final class State { public boolean mIsLaunchAnimationRunning; }
    }
}
