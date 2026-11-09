package com.xtjm.fusionstatusbar;

import android.app.LocaleManager;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.res.XmlResourceParser;
import android.os.LocaleList;
import java.util.ArrayList;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.xmlpull.v1.XmlPullParser;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33)
public class AppLanguageSettingsTest {
    private static final String[] TAGS = {"", "zh-Hans", "zh-Hant", "en", "ja", "ko", "ru", "es"};
    private Context context;
    private LocaleManager manager;

    @Before public void setup() {
        context = RuntimeEnvironment.getApplication();
        manager = context.getSystemService(LocaleManager.class);
        manager.setApplicationLocales(LocaleList.getEmptyLocaleList());
    }

    @Test public void theEightOptionsExposeStableTagsWithoutSharingTheirMutableArray() {
        assertArrayEquals(TAGS, AppLanguageSettings.supportedTags());
        String[] copy = AppLanguageSettings.supportedTags();
        copy[1] = "corrupted";
        assertArrayEquals(TAGS, AppLanguageSettings.supportedTags());
    }

    @Test public void labelsUseEachLanguagesOwnNameAndLocalizeOnlyFollowSystem() {
        assertArrayEquals(new String[] {context.getString(R.string.app_language_follow_system),
                "简体中文", "繁體中文", "English", "日本語", "한국어", "Русский", "Español"},
                AppLanguageSettings.labels(context));
        String[] labels = AppLanguageSettings.labels(context);
        labels[1] = "corrupted";
        assertEquals("简体中文", AppLanguageSettings.labels(context)[1]);
    }

    @Test public void selectingALanguageUsesTheNativeStoreAndSurvivesANewContextWrapper() {
        for (int index = 1; index < TAGS.length; index++) {
            assertTrue(AppLanguageSettings.select(context, index));
            assertEquals(TAGS[index], manager.getApplicationLocales().toLanguageTags());
            assertEquals(index, AppLanguageSettings.selectedIndex(new ContextWrapper(context)));
            assertTrue(AppLanguageSettings.select(context, index));
        }
    }

    @Test public void followSystemClearsTheApplicationOverride() {
        manager.setApplicationLocales(LocaleList.forLanguageTags("ja"));
        assertTrue(AppLanguageSettings.select(context, 0));
        assertTrue(manager.getApplicationLocales().isEmpty());
        assertEquals(0, AppLanguageSettings.selectedIndex(context));
        assertTrue(AppLanguageSettings.select(context, 0));
    }

    @Test public void externallySelectedRegionTagsStillIdentifyTheCorrectLanguage() {
        manager.setApplicationLocales(LocaleList.forLanguageTags("en-US"));
        assertEquals(3, AppLanguageSettings.selectedIndex(context));
        manager.setApplicationLocales(LocaleList.forLanguageTags("zh-TW"));
        assertEquals(2, AppLanguageSettings.selectedIndex(context));
        manager.setApplicationLocales(LocaleList.forLanguageTags("zh-HK"));
        assertEquals(2, AppLanguageSettings.selectedIndex(context));
        manager.setApplicationLocales(LocaleList.forLanguageTags("zh-CN"));
        assertEquals(1, AppLanguageSettings.selectedIndex(context));
    }

    @Test public void hostContextsCannotReadOrChangeAnInjectedProcessesLanguage() {
        manager.setApplicationLocales(LocaleList.forLanguageTags("ru"));
        Context host = new ContextWrapper(context) {
            @Override public String getPackageName() { return "com.miui.securitycenter"; }
            @Override public Object getSystemService(String name) {
                throw new AssertionError("a host context must not reach its LocaleManager");
            }
        };
        assertFalse(AppLanguageSettings.select(host, 3));
        assertEquals(0, AppLanguageSettings.selectedIndex(host));
        assertEquals("ru", manager.getApplicationLocales().toLanguageTags());
    }

    @Test public void invalidSelectionsAndMissingServicesLeaveTheCurrentChoiceUntouched() {
        manager.setApplicationLocales(LocaleList.forLanguageTags("ru"));
        assertFalse(AppLanguageSettings.select(context, -1));
        assertFalse(AppLanguageSettings.select(context, 8));
        assertFalse(AppLanguageSettings.select(null, 3));
        assertEquals(0, AppLanguageSettings.selectedIndex(null));
        Context missing = new ContextWrapper(context) {
            @Override public Object getSystemService(String name) {
                return Context.LOCALE_SERVICE.equals(name) ? null : super.getSystemService(name);
            }
        };
        assertFalse(AppLanguageSettings.select(missing, 3));
        assertEquals(0, AppLanguageSettings.selectedIndex(missing));
        Context failing = new ContextWrapper(context) {
            @Override public Object getSystemService(String name) {
                if (Context.LOCALE_SERVICE.equals(name)) throw new IllegalStateException("unavailable");
                return super.getSystemService(name);
            }
        };
        assertFalse(AppLanguageSettings.select(failing, 3));
        assertEquals(0, AppLanguageSettings.selectedIndex(failing));
        assertEquals("ru", manager.getApplicationLocales().toLanguageTags());
    }

    @Test public void theSystemLanguageListContainsOnlyTheSevenTranslatedLanguages() throws Exception {
        List<String> declared = new ArrayList<>();
        try (XmlResourceParser xml = context.getResources().getXml(R.xml.locales_config)) {
            for (int event = xml.getEventType(); event != XmlPullParser.END_DOCUMENT; event = xml.next()) {
                if (event == XmlPullParser.START_TAG && "locale".equals(xml.getName()))
                    declared.add(xml.getAttributeValue("http://schemas.android.com/apk/res/android", "name"));
            }
        }
        assertEquals(List.of("zh-Hans", "zh-Hant", "en", "ja", "ko", "ru", "es"), declared);
    }
}
