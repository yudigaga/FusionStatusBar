package com.xtjm.fusionstatusbar;

import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, manifest = Config.NONE)
public class ControlCenterNativeTileCatalogTest {
    private Object previousList;
    private Object previousCatalog;

    @Before public void rememberHost() throws Exception {
        previousList = field("controlCenterListRef").get(null);
        previousCatalog = field("CONTROL_CENTER_TILE_CATALOG_LAST").get(null);
    }

    @After public void restoreHost() throws Exception {
        field("controlCenterListRef").set(null, previousList);
        field("CONTROL_CENTER_TILE_CATALOG_LAST").set(null, previousCatalog);
    }

    @Test public void normalCaptureIncludesStockTilesBeforeTheNativeEditorHasQueriedThem() throws Exception {
        NativeList list = new NativeList();
        list.tileQueryHelper = new QueryHelper();

        assertEquals("wifi,aitranslate,aisubtitles,modes_dnd,custom(com.example/.tile)", catalog(list));
        assertTrue("Reading the catalog must leave the native editor inventory untouched", list.systemTiles.isEmpty());
        assertTrue(list.packageTiles.isEmpty());
        assertEquals(1, list.addedTiles.size());
    }

    @Test public void hostStockAccessorWorksWhenThePluginHelperIsUnavailable() throws Exception {
        NativeList list = new NativeList();
        list.host = new Host();

        assertEquals("wifi,aitranslate,aisubtitles,modes_dnd", catalog(list));
    }

    @Test public void staticStockFallbackSurvivesAnUnavailableHostAccessor() throws Exception {
        NativeList list = new NativeList();
        list.tileQueryHelper = new LegacyQueryHelper();
        list.host = new BrokenHost();

        assertEquals("wifi,aitranslate,aisubtitles", catalog(list));
    }

    @Test public void existingEditorRecordsRemainAvailableWhenNoStockAccessorExists() throws Exception {
        NativeList list = new NativeList();
        list.systemTiles = List.of(new Record("aitranslate"), new Record("aisubtitles"));
        list.packageTiles = List.of(new Record("custom(com.example/com.example.Tile)"));

        assertEquals("wifi,aitranslate,aisubtitles,custom(com.example/.tile)", catalog(list));
    }

    @Test public void modesAndBothDoNotDisturbSpecsRemainSeparateInTheNativeStockCatalog() throws Exception {
        NativeList list = new NativeList();
        list.tileQueryHelper = new ModesQueryHelper();

        assertEquals("wifi,dnd,quietmode,modes_dnd", catalog(list));
    }

    @Test public void systemUiFactoryDeclarationsExposeModesMissingFromTheOemStockList() throws Exception {
        NativeList list = new NativeList();
        list.tileQueryHelper = new LegacyQueryHelper();
        list.host = new AlternateFactoryHost();

        assertEquals("wifi,aitranslate,aisubtitles,dnd,modes_dnd", catalog(list));
    }

    private static String catalog(Object list) throws Exception {
        field("controlCenterListRef").set(null, new WeakReference<>(list));
        Method method = SystemUiHooks.class.getDeclaredMethod("nativeControlCenterTileCatalog");
        method.setAccessible(true);
        return (String) method.invoke(null);
    }

    private static Field field(String name) throws Exception {
        Field field = SystemUiHooks.class.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }

    public static final class NativeList {
        private final List<Record> addedTiles = List.of(new Record("wifi"));
        private List<Record> systemTiles = List.of();
        private List<Record> packageTiles = List.of();
        private Object tileQueryHelper;
        private Object host;
        public void startQuery(Object mode) { fail("Catalog reads must not enter native edit mode"); }
    }

    public static final class Record {
        private final String spec;
        Record(String spec) { this.spec = spec; }
        public String getSpec() { return spec; }
    }

    public static final class QueryHelper {
        private String getTilesStock() {
            return " wifi, aitranslate, aisubtitles, modes_dnd, custom(com.example/com.example.Tile), ,WIFI ";
        }
        public void queryTiles(Object host) { fail("Catalog reads must not create or observe native tiles"); }
    }

    public static final class Host {
        public String getStockTiles() { return "aitranslate,aisubtitles,modes_dnd"; }
        public Object createTile(String spec) { fail("Catalog reads must not create native tiles"); return null; }
    }

    public static final class LegacyQueryHelper {
        private final String staticStockTiles = "aitranslate,aisubtitles";
    }

    public static final class ModesQueryHelper {
        private String getTilesStock() { return "dnd,quietmode,modes_dnd"; }
    }

    public static final class AlternateFactoryHost {
        private final AlternateInteractor interactor = new AlternateInteractor();
    }

    public static final class AlternateInteractor {
        private final AlternateFactory tileFactory = new AlternateFactory();
    }

    public static final class AlternateFactory {
        private final java.util.Map<String, Object> mTileMap = new java.util.LinkedHashMap<>();
        AlternateFactory() {
            mTileMap.put("dnd", new Object());
            mTileMap.put("modes_dnd", new Object());
        }
        public Object createTile(String spec) { fail("Catalog reads must not initialize factory tiles"); return null; }
    }

    public static final class BrokenHost {
        public String getStockTiles() { throw new IllegalStateException("Host is not ready"); }
    }
}
