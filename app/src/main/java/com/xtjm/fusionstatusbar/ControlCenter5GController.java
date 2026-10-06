package com.xtjm.fusionstatusbar;

import android.annotation.SuppressLint;
import android.content.Context;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;

/** Reads and updates the NR bit in the active data subscription's user network mask. */
final class ControlCenter5GController {
    interface RadioAccess {
        long supportedNetworkTypes();
        long allowedNetworkTypes();
        void setAllowedNetworkTypes(long value);
    }

    static final class State {
        final boolean available;
        final boolean enabled;

        State(boolean available, boolean enabled) {
            this.available = available;
            this.enabled = enabled;
        }
    }

    private static final long NR = TelephonyManager.NETWORK_TYPE_BITMASK_NR;
    private final RadioAccess radio;

    ControlCenter5GController(Context context) {
        this(systemRadio(context));
    }

    ControlCenter5GController(RadioAccess radio) {
        this.radio = radio;
    }

    State read() { return read(radio); }

    State toggle() { return toggle(radio); }

    static State read(RadioAccess radio) {
        if (radio == null) return unavailable();
        try {
            long supported = radio.supportedNetworkTypes();
            if ((supported & NR) == 0) return unavailable();
            return new State(true, (radio.allowedNetworkTypes() & NR) != 0);
        } catch (RuntimeException deniedOrUnavailable) {
            return unavailable();
        }
    }

    static State toggle(RadioAccess radio) {
        State before = read(radio);
        if (!before.available) return before;
        try {
            long supported = radio.supportedNetworkTypes();
            long allowed = radio.allowedNetworkTypes();
            long next = before.enabled ? allowed & ~NR : allowed | NR;
            if (next == 0L) next = supported & ~NR;
            if (next == 0L) return unavailable();
            radio.setAllowedNetworkTypes(next);
            return read(radio);
        } catch (RuntimeException deniedOrUnavailable) {
            return unavailable();
        }
    }

    // The module tile is created with the SystemUI context, which owns the privileged
    // telephony access on supported ROMs. Calls still fail closed when the ROM denies them.
    @SuppressLint("MissingPermission")
    private static RadioAccess systemRadio(Context context) {
        if (context == null) return null;
        try {
            int subscriptionId = SubscriptionManager.getDefaultDataSubscriptionId();
            if (!SubscriptionManager.isValidSubscriptionId(subscriptionId)) return null;
            TelephonyManager manager = context.getSystemService(TelephonyManager.class);
            if (manager == null) return null;
            TelephonyManager subscription = manager.createForSubscriptionId(subscriptionId);
            if (subscription == null) return null;
            return new RadioAccess() {
                @Override public long supportedNetworkTypes() {
                    return subscription.getSupportedRadioAccessFamily();
                }
                @Override public long allowedNetworkTypes() {
                    return subscription.getAllowedNetworkTypesForReason(
                            TelephonyManager.ALLOWED_NETWORK_TYPES_REASON_USER);
                }
                @Override public void setAllowedNetworkTypes(long value) {
                    subscription.setAllowedNetworkTypesForReason(
                            TelephonyManager.ALLOWED_NETWORK_TYPES_REASON_USER, value);
                }
            };
        } catch (RuntimeException deniedOrUnavailable) {
            return null;
        }
    }

    private static State unavailable() { return new State(false, false); }
}
