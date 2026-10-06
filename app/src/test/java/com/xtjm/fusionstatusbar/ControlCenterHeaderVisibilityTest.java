package com.xtjm.fusionstatusbar;

import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import static org.junit.Assert.assertEquals;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, manifest = Config.NONE)
public class ControlCenterHeaderVisibilityTest {
    @After public void clearControllers() { ControlCenterHeaderVisibility.clearForTests(); }

    @Test public void dateAndCarrierOverridesPreserveNativeAndPrivacyVisibility() {
        HeaderFixture header = new HeaderFixture();
        ControlCenterConfig visible = ControlCenterConfig.defaults();
        ControlCenterConfig hideBoth = visible.withHideDate(true).withHideCarrier(true);

        header.updateDateVisibility();
        ControlCenterHeaderVisibility.afterDateUpdate(header, hideBoth);
        header.updateCarrierAndPrivacyVisible(true, false);
        ControlCenterHeaderVisibility.afterCarrierPrivacyUpdate(header,
                new Object[] {true, false}, hideBoth);

        assertEquals(View.GONE, header.dateView.getVisibility());
        assertEquals(View.GONE, header.carrierText.getVisibility());
        assertEquals(View.GONE, header.privacyIcon.getVisibility());

        assertEquals(0, ControlCenterHeaderVisibility.refresh(visible));

        assertEquals(View.VISIBLE, header.dateView.getVisibility());
        assertEquals(View.VISIBLE, header.carrierText.getVisibility());
        assertEquals(View.GONE, header.privacyIcon.getVisibility());

        header.updateCarrierAndPrivacyVisible(false, true);
        ControlCenterHeaderVisibility.afterCarrierPrivacyUpdate(header,
                new Object[] {false, true}, hideBoth);
        assertEquals(View.GONE, header.carrierText.getVisibility());
        assertEquals(View.VISIBLE, header.privacyIcon.getVisibility());

        assertEquals(0, ControlCenterHeaderVisibility.refresh(visible));
        assertEquals(View.GONE, header.carrierText.getVisibility());
        assertEquals(View.VISIBLE, header.privacyIcon.getVisibility());
    }

    @Test public void carrierContainerToggleHidesAndRestoresBothCarrierLabels() {
        CarrierGroupFixture header = new CarrierGroupFixture();
        ControlCenterConfig hide = ControlCenterConfig.defaults().withHideCarrier(true);

        header.updateCarrierAndPrivacyVisible(true, true);
        ControlCenterHeaderVisibility.afterCarrierPrivacyUpdate(header,
                new Object[] {true, true}, hide);
        assertEquals(View.GONE, header.carrierLayout.getVisibility());

        assertEquals(0, ControlCenterHeaderVisibility.refresh(
                ControlCenterConfig.defaults()));
        assertEquals(View.VISIBLE, header.carrierLayout.getVisibility());
        assertEquals(View.VISIBLE, header.firstCarrier.getVisibility());
        assertEquals(View.VISIBLE, header.secondCarrier.getVisibility());

        header.updateCarrierAndPrivacyVisible(false, true);
        ControlCenterHeaderVisibility.afterCarrierPrivacyUpdate(header,
                new Object[] {false, true}, hide);
        assertEquals(View.GONE, header.carrierLayout.getVisibility());
        assertEquals(0, ControlCenterHeaderVisibility.refresh(
                ControlCenterConfig.defaults()));
        assertEquals(View.GONE, header.carrierLayout.getVisibility());
        header.carrierLayout.removeView(header.firstCarrier);
        header.carrierLayout.removeView(header.secondCarrier);
    }

    static final class CarrierGroupFixture {
        final LinearLayout carrierLayout = new LinearLayout(RuntimeEnvironment.getApplication());
        final TextView firstCarrier = new TextView(RuntimeEnvironment.getApplication());
        final TextView secondCarrier = new TextView(RuntimeEnvironment.getApplication());
        CarrierGroupFixture() {
            carrierLayout.addView(firstCarrier);
            carrierLayout.addView(secondCarrier);
        }
        void updateCarrierAndPrivacyVisible(boolean carrierVisible, boolean ignored) {
            carrierLayout.setVisibility(carrierVisible ? View.VISIBLE : View.GONE);
        }
    }

    static final class HeaderFixture {
        final TextView dateView = new TextView(RuntimeEnvironment.getApplication());
        final TextView carrierText = new TextView(RuntimeEnvironment.getApplication());
        final TextView privacyIcon = new TextView(RuntimeEnvironment.getApplication());

        void updateDateVisibility() { dateView.setVisibility(View.VISIBLE); }

        void updateCarrierAndPrivacyVisible(boolean carrierVisible, boolean privacyVisible) {
            carrierText.setVisibility(carrierVisible ? View.VISIBLE : View.GONE);
            privacyIcon.setVisibility(privacyVisible ? View.VISIBLE : View.GONE);
        }
    }
}
