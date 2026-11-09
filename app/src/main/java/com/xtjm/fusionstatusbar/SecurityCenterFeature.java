package com.xtjm.fusionstatusbar;

import java.util.Locale;

/** Independently stored options for the verified phone-manager host. */
public enum SecurityCenterFeature {
    SMART_PRIVACY(R.string.ui_shared_4971bad28a, R.string.ui_shared_06d23d6323, R.string.ui_shared_4c17aca43f),
    CAR_CRASH_DETECTION(R.string.ui_shared_4971bad28a, R.string.ui_shared_f20e26a53c, R.string.ui_shared_21fccda712),
    MOTION_SICKNESS(R.string.ui_shared_6b3ab7cdc5, R.string.ui_shared_3e5f0f83ba, R.string.ui_shared_d2d3e3ce2e),
    SCORE_666(R.string.ui_shared_abac55f77e, R.string.ui_shared_620e48697b, R.string.ui_shared_3b248194b7),
    SKIP_TEN_SECONDS(R.string.ui_shared_abac55f77e, R.string.ui_shared_1ed667a339, R.string.ui_shared_2611edc14d),
    GAME_THREE_SECOND_LAUNCH(R.string.ui_shared_0d4eb90dda, R.string.ui_shared_ee63545459, R.string.ui_shared_74e2750b90),
    GAME_FILL_DISPLAY(R.string.ui_shared_0d4eb90dda, R.string.ui_shared_6ded4ea71c, R.string.ui_shared_3afac8982a),
    AIM_ASSIST(R.string.ui_shared_0d4eb90dda, R.string.ui_shared_0e4521bebe, R.string.ui_shared_1ff083ea9c),
    GYROSCOPE_ACCELERATION(R.string.ui_shared_0d4eb90dda, R.string.ui_shared_a311282778, R.string.ui_shared_9e7826b729),
    COOLING_FAN_UI(R.string.ui_shared_0d4eb90dda, R.string.ui_shared_19d7acdcd6, R.string.ui_shared_d42438d005),
    GAME_SOUND_UI(R.string.ui_shared_0d4eb90dda, R.string.ui_shared_3e789f84de, R.string.ui_shared_7764d15031),
    INDEPENDENT_DISPLAY_UI(R.string.ui_shared_0d4eb90dda, R.string.ui_shared_5acf8ee13d, R.string.ui_shared_0dad51dfeb),
    SUPER_RESOLUTION(R.string.ui_shared_0d4eb90dda, R.string.ui_shared_bf481fe8e3, R.string.ui_shared_3b7f03a9cb),
    FRAME_INTERPOLATION(R.string.ui_shared_0d4eb90dda, R.string.ui_shared_9cbd7e000c, R.string.ui_shared_446a8a8694),
    GAME_HDR(R.string.ui_shared_0d4eb90dda, R.string.ui_shared_5ceae040b5, R.string.ui_shared_cb42d4c338),
    VIDEO_MORE(R.string.ui_shared_7e4ce1087c, R.string.ui_shared_62048f24bc, R.string.ui_shared_5ada2a3451),
    VIDEO_TEMPERATURE_LIMIT(R.string.ui_shared_7e4ce1087c, R.string.ui_shared_8e40f6458b, R.string.ui_shared_afe8b9922d),
    CINEMA_MODE(R.string.ui_shared_7e4ce1087c, R.string.ui_shared_8007bed79c, R.string.ui_shared_a4841cadcb),
    SMART_REFRESH_RATE(R.string.ui_shared_7e4ce1087c, R.string.ui_shared_b759b63956, R.string.ui_shared_488a5835a1),
    IMAGE_ENHANCEMENT(R.string.ui_shared_7e4ce1087c, R.string.ui_shared_3dbd1a4c8c, R.string.ui_shared_ebcf9eab4c),
    BATTERY_DETAILS(R.string.ui_shared_72979fd337, R.string.ui_shared_68f060613b, R.string.ui_shared_4d557baa9b),
    CHARGE_FEATURES(R.string.ui_shared_72979fd337, R.string.ui_shared_1aabcab8a6, R.string.ui_shared_cdf423572b),
    SMART_CHARGING(R.string.ui_shared_72979fd337, R.string.ui_shared_6e8e5179da, R.string.ui_shared_276df9c7d4),
    BYPASS_CHARGING_PLUS(R.string.ui_shared_72979fd337, R.string.ui_shared_76e92ed58d, R.string.ui_shared_5b1f357c2f),
    SKIP_CHARGE_DIALOGS(R.string.ui_shared_72979fd337, R.string.ui_shared_5fc775d098, R.string.ui_shared_8f21b5c949),
    SCREEN_ON_TIME(R.string.ui_shared_72979fd337, R.string.ui_shared_b52c815f90, R.string.ui_shared_4bb0bcd111);

    final int section;
    final int title;
    final int summary;

    SecurityCenterFeature(int section, int title, int summary) {
        this.section = section; this.title = title; this.summary = summary;
    }

    String key() { return "security_center_" + name().toLowerCase(Locale.ROOT); }
}
