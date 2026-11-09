package com.xtjm.fusionstatusbar;

import android.content.Context;
import android.content.res.Configuration;
import android.os.LocaleList;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class AppLanguageResourcesTest {
    @Test public void simplifiedAndTraditionalChineseResolveIndependently() {
        assertEquals("应用语言", localized("zh-Hans").getString(R.string.app_language_title));
        assertNotEquals("应用语言", localized("zh-Hant").getString(R.string.app_language_title));
        assertTrue(localized("zh-Hant").getString(R.string.app_language_title).contains("語"));
    }

    @Test public void everyAdvertisedLanguageResolvesTranslatedSettingsAndFeatureDescriptions() {
        Context chinese = localized("zh-Hans");
        for (String language : new String[] {"en", "ja", "ko", "ru", "es"}) {
            Context context = localized(language);
            assertNotEquals(language, chinese.getString(R.string.app_language_title),
                    context.getString(R.string.app_language_title));
            for (SecurityCenterFeature feature : SecurityCenterFeature.values()) {
                assertFalse(language + feature, context.getString(feature.title).isBlank());
                assertFalse(language + feature, context.getString(feature.summary).isBlank());
            }
            assertTrue(context.getString(R.string.language_control_summary, 4, 100, 20, 8).contains("100%"));
        }
    }

    private Context localized(String language) {
        Context app = RuntimeEnvironment.getApplication();
        Configuration config = new Configuration(app.getResources().getConfiguration());
        config.setLocales(LocaleList.forLanguageTags(language));
        return app.createConfigurationContext(config);
    }
}
