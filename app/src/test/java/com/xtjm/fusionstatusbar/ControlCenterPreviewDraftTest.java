package com.xtjm.fusionstatusbar;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;

public class ControlCenterPreviewDraftTest {
    @Test
    public void capturedOrderRemainsSourceUntilExplicitEdit() {
        ControlCenterConfig settings = ControlCenterConfig.defaults().withEnabled(true)
                .clearCustomization();
        List<String> captured = List.of("wifi", "cell", "bt");

        assertEquals(captured, ControlCenterPreviewDraft.visibleSpecs(captured,
                List.of(), settings));
        assertEquals(captured, ControlCenterPreviewDraft.visibleSpecs(captured,
                List.of(), settings.withTileShape("wifi", 2, 1)));
        assertEquals(List.of("bt", "wifi", "cell"),
                ControlCenterPreviewDraft.visibleSpecs(captured, List.of(),
                        settings.withOrder("bt,wifi,cell")));
    }

    @Test
    public void uncapturedConfiguredTilesDoNotAppearUntilAddedLocally() {
        ControlCenterConfig settings = ControlCenterConfig.defaults().withEnabled(true)
                .withOrder("satellite,wifi,bt");
        List<String> captured = List.of("wifi", "bt");

        assertEquals(captured, ControlCenterPreviewDraft.visibleSpecs(captured,
                List.of(), settings));
        assertEquals(List.of("satellite", "wifi", "bt"),
                ControlCenterPreviewDraft.visibleSpecs(captured, List.of("satellite"),
                        settings));
        assertEquals(List.of("wifi", "bt"), ControlCenterPreviewDraft.visibleSpecs(captured,
                List.of("satellite"), settings.withHidden("satellite")));
    }
}
