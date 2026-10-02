package com.xtjm.fusionstatusbar;

/** Maps telephony network constants to the short label drawn inside the icon. */
public final class NetworkLabel {
    private NetworkLabel() {
    }

    public static String fromDisplayInfo(int overrideNetworkType, int networkType) {
        if (overrideNetworkType == 1 || overrideNetworkType == 2) {
            return "4G+";
        }
        if (overrideNetworkType == 3 || overrideNetworkType == 4 || overrideNetworkType == 5) {
            return "5G";
        }
        return fromNetworkType(networkType);
    }

    public static String fromNetworkType(int networkType) {
        switch (networkType) {
            case 20:
                return "5G";
            case 19:
                return "4G+";
            case 13:
                return "4G";
            case 3:
            case 5:
            case 6:
            case 8:
            case 9:
            case 10:
            case 12:
            case 14:
            case 15:
            case 17:
                return "3G";
            case 1:
            case 2:
            case 4:
            case 7:
            case 11:
            case 16:
                return "2G";
            default:
                return "";
        }
    }
}
