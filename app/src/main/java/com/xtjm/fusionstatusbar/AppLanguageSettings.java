package com.xtjm.fusionstatusbar;

import android.app.LocaleManager;
import android.content.Context;
import android.os.LocaleList;
import java.util.Locale;

/** Language selection for the module's own settings interface. */
final class AppLanguageSettings {
    private static final String[] TAGS = {"", "zh-Hans", "zh-Hant", "en", "ja", "ko", "ru", "es"};

    private AppLanguageSettings() { }

    static int selectedIndex(Context context) {
        try {
            if (!ownContext(context)) return 0;
            LocaleManager manager = context.getSystemService(LocaleManager.class);
            if (manager == null) return 0;
            LocaleList locales = manager.getApplicationLocales();
            if (locales.isEmpty()) return 0;
            Locale locale = locales.get(0);
            if ("zh".equals(locale.getLanguage())) {
                if ("Hant".equals(locale.getScript())) return 2;
                if ("Hans".equals(locale.getScript())) return 1;
                String region = locale.getCountry();
                return "TW".equals(region) || "HK".equals(region) || "MO".equals(region) ? 2 : 1;
            }
            for (int index = 3; index < TAGS.length; index++)
                if (TAGS[index].equals(locale.getLanguage())) return index;
        } catch (RuntimeException unavailable) { }
        return 0;
    }

    static boolean select(Context context, int index) {
        if (index < 0 || index >= TAGS.length) return false;
        try {
            if (!ownContext(context)) return false;
            LocaleManager manager = context.getSystemService(LocaleManager.class);
            if (manager == null) return false;
            LocaleList requested = index == 0 ? LocaleList.getEmptyLocaleList()
                    : LocaleList.forLanguageTags(TAGS[index]);
            if (!requested.equals(manager.getApplicationLocales())) manager.setApplicationLocales(requested);
            return true;
        } catch (RuntimeException unavailable) { return false; }
    }

    static String[] labels(Context context) {
        return context.getResources().getStringArray(R.array.app_language_names);
    }

    static String[] supportedTags() { return TAGS.clone(); }

    private static boolean ownContext(Context context) {
        return context != null && BuildConfig.APPLICATION_ID.equals(context.getPackageName());
    }
}
