package com.xtjm.fusionstatusbar;

import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.Switch;

/** Owns the settings page's editable control-center session, independent of its view tree. */
final class ControlCenterSettingsPage {
    interface ContentBuilder { void build(LinearLayout content); }

    interface Host {
        android.content.Context pageContext();
        void showPageDialog(String title, ContentBuilder builder);
        ControlCenterLayoutPlan currentGridPlan();
        ControlCenterLayoutPlan.Mode resolvedGridMode(boolean compact);
        ControlCenterGridEditor createPageGridEditor(ControlCenterGridEditor.Listener listener);
        View addPageGroupEditor(LinearLayout content, String id);
        void refreshPageGroupEditor(View editor);
        ControlCenterCardStylePicker addPageCardStylePicker(LinearLayout content,
                ControlCenterLayoutPlan.Item selected,
                java.util.function.UnaryOperator<ControlCenterLayoutPlan.Item> update);
        Button addPageButton(LinearLayout content, String title, boolean enabled, Runnable action);
        void showPairCreation();
        String tileLabel(String spec);
        boolean suppressPageSelection();
        void persistPageSelection();
        void onPageDraftChanged();
        void rejectPageEdit(String reason);
        void addPageSectionLabel(LinearLayout content, String title);
        void addPageHint(LinearLayout content, String text);
        Switch addPageToggle(LinearLayout content, String title, String summary, boolean checked,
                java.util.function.Consumer<Boolean> update);
        View addPageSlider(LinearLayout content, String title, String summary, int minimum,
                int maximum, int value, String suffix, java.util.function.IntConsumer update);
        View addPageChoice(LinearLayout content, String title, String[] options, int selected,
                java.util.function.IntConsumer update);
        View addPageBlurSlider(LinearLayout content, String title, int value,
                java.util.function.IntConsumer update);
        void setPageSliderEnabled(View row, boolean enabled);
    }

    private ControlCenterEditorSession session;

    ControlCenterSettingsPage(ControlCenterEditorSession session) {
        replaceSession(session);
    }

    ControlCenterEditorSession session() { return session; }
    ControlCenterConfig draft() { return session.draft(); }
    String selection() { return session.selection(); }
    boolean compact() { return session.compact(); }
    boolean isDirty() { return session.isDirty(); }
    boolean canUndo() { return session.canUndo(); }
    boolean canRedo() { return session.canRedo(); }
    int undoCount() { return session.undoCount(); }

    void selectMode(boolean compact) { session.selectMode(compact); }
    void select(String id) { session.select(id); }
    boolean edit(ControlCenterConfig next) { return session.edit(next); }
    boolean undo() { return session.undo(); }
    boolean redo() { return session.redo(); }
    boolean loadPreset(ControlCenterConfig next, boolean compact) {
        return session.loadPreset(next, compact);
    }
    void saved(ControlCenterConfig submitted, ControlCenterConfig committed) {
        session.saved(submitted, committed);
    }

    void selectCanvas(boolean compact, Host host) {
        session.selectMode(compact);
        host.onPageDraftChanged();
    }

    boolean undoCanvas(Host host) {
        if (!session.undo()) return false;
        host.onPageDraftChanged();
        return true;
    }

    boolean redoCanvas(Host host) {
        if (!session.redo()) return false;
        host.onPageDraftChanged();
        return true;
    }

    boolean clearCanvas(Host host) {
        ControlCenterLayoutPlan plan = host.currentGridPlan();
        ControlCenterLayoutPlan.Mode mode = plan.mode(compact());
        if (mode.items.isEmpty()) return false;
        return publishPlan(plan.withMode(compact(),
                new ControlCenterLayoutPlan.Mode(mode.columns, java.util.Collections.emptyList())), host);
    }

    ControlCenterGridEditor createGridEditor(Host host) {
        return host.createPageGridEditor(new ControlCenterGridEditor.Listener() {
            @Override public void onItemClicked(String id) { showItemProperties(id, host); }
            @Override public void onItemMoved(String id, int x, int y) {
                commitGridGesture(id, x, y, -1, -1, host);
            }
            @Override public void onItemResized(String id, int width, int height) {
                commitGridGesture(id, -1, -1, width, height, host);
            }
            @Override public void onItemAction(String id, ControlCenterGridEditor.EditAction action) {
                applyGridAction(id, action, host);
            }
            @Override public void onSelectionChanged(String id) {
                if (host.suppressPageSelection()) return;
                select(id == null ? "" : id);
                host.persistPageSelection();
            }
        });
    }

    void showItemProperties(String id, Host host) {
        ControlCenterLayoutPlan.Item selected = findResolvedItem(id, compact(), host);
        if (selected == null) return;
        String title = selected.type == ControlCenterLayoutPlan.Type.PAIR
                || selected.type == ControlCenterLayoutPlan.Type.GROUP
                ? tr(host, R.string.ui_control_page_group_card) : host.tileLabel(selected.firstSpec);
        host.showPageDialog(title, content -> buildItemProperties(content, id, selected, host));
    }

    private void buildItemProperties(LinearLayout content, String id,
            ControlCenterLayoutPlan.Item selected, Host host) {
        View groupEditor = selected.type == ControlCenterLayoutPlan.Type.GROUP
                ? host.addPageGroupEditor(content, id) : null;
        boolean selectedCompact = compact();
        int columns = host.currentGridPlan().mode(selectedCompact).columns;
        host.addPageSlider(content, tr(host, R.string.ui_control_page_width), tr(host, R.string.ui_control_page_width_summary), 1, columns,
                selected.width, tr(host, R.string.ui_control_page_columns_suffix), value -> editGridItem(id, item -> item.withSize(value,
                        item.height), false, host));
        host.addPageSlider(content, tr(host, R.string.ui_control_page_height), tr(host, R.string.ui_control_page_height_summary), 1, 6,
                selected.height, tr(host, R.string.ui_control_page_rows_suffix), value -> editGridItem(id, item -> item.withSize(item.width,
                        value), false, host));
        ControlCenterCardStylePicker stylePicker = host.addPageCardStylePicker(content, selected,
                proposed -> {
                    editItemAppearance(id, proposed, host);
                    return findResolvedItem(id, compact(), host);
                });

        Button remove = host.addPageButton(content, tr(host, R.string.ui_control_page_remove_grid), !selected.locked, () -> {
            applyGridAction(id, ControlCenterGridEditor.EditAction.REMOVE, host);
            if (selected.type == ControlCenterLayoutPlan.Type.GROUP
                    && findResolvedItem(id, compact(), host) == null) content.removeAllViews();
        });
        Button[] lock = new Button[1];
        lock[0] = host.addPageButton(content,
                selected.locked ? tr(host, R.string.ui_control_page_unlock_item) : tr(host, R.string.ui_control_page_lock_item), true, () -> {
                    editGridItem(id, item -> item.withLocked(!item.locked), true, host);
                    ControlCenterLayoutPlan.Item current = findResolvedItem(id, compact(), host);
                    if (current == null) return;
                    lock[0].setText(current.locked ? tr(host, R.string.ui_control_page_unlock_item) : tr(host, R.string.ui_control_page_lock_item));
                    stylePicker.bind(current);
                    remove.setEnabled(!current.locked);
                    if (groupEditor != null) host.refreshPageGroupEditor(groupEditor);
                });
        Button[] visibility = new Button[1];
        visibility[0] = host.addPageButton(content,
                selected.hidden ? tr(host, R.string.ui_control_page_show_item) : tr(host, R.string.ui_control_page_hide_item), true, () -> {
            editGridItem(id, item -> item.withHidden(!item.hidden), true, host);
            ControlCenterLayoutPlan.Item current = findResolvedItem(id, compact(), host);
            if (current != null) visibility[0].setText(current.hidden ? tr(host, R.string.ui_control_page_show_item) : tr(host, R.string.ui_control_page_hide_item));
        });
        host.addPageSlider(content, tr(host, R.string.ui_control_page_layer), tr(host, R.string.ui_control_page_layer_summary), -20, 20,
                selected.zIndex, "", value -> editGridItem(id, item -> item.withZIndex(value),
                        true, host));
        if (columns > selected.width) {
            host.addPageSlider(content, tr(host, R.string.ui_control_page_horizontal_position), tr(host, R.string.ui_control_page_start_column), 0, columns - selected.width,
                    selected.x, tr(host, R.string.ui_control_page_columns_suffix), value -> editGridItem(id,
                            item -> item.withPosition(value, item.y), false, host));
        }
        host.addPageSlider(content, tr(host, R.string.ui_control_page_vertical_position), tr(host, R.string.ui_control_page_start_row), 0,
                Math.min(ControlCenterLayoutPlan.MAX_ROWS - selected.height,
                        Math.max(24, selected.y + 4)), selected.y, tr(host, R.string.ui_control_page_rows_suffix),
                value -> editGridItem(id, item -> item.withPosition(item.x, value), false, host));
        if (selected.type == ControlCenterLayoutPlan.Type.PAIR) {
            boolean isHorizontal = selected.direction == ControlCenterLayoutPlan.Direction.HORIZONTAL;
            Button[] direction = new Button[1];
            direction[0] = host.addPageButton(content,
                    isHorizontal ? tr(host, R.string.ui_control_page_pair_vertical) : tr(host, R.string.ui_control_page_pair_horizontal), true, () -> {
                applyGridAction(id, ControlCenterGridEditor.EditAction.TOGGLE_DIRECTION, host);
                ControlCenterLayoutPlan.Item current = findResolvedItem(id, compact(), host);
                if (current != null) direction[0].setText(current.direction
                        == ControlCenterLayoutPlan.Direction.HORIZONTAL ? tr(host, R.string.ui_control_page_pair_vertical) : tr(host, R.string.ui_control_page_pair_horizontal));
            });
            host.addPageButton(content, tr(host, R.string.ui_control_page_ungroup), true,
                    () -> applyGridAction(id, ControlCenterGridEditor.EditAction.UNPAIR, host));
        } else if (selected.type == ControlCenterLayoutPlan.Type.TILE) {
            host.addPageButton(content, tr(host, R.string.ui_control_page_pair_with_tile), true, host::showPairCreation);
        }
    }

    boolean editItemAppearance(String id, ControlCenterLayoutPlan.Item proposed, Host host) {
        boolean selectedCompact = compact();
        ControlCenterLayoutPlan plan = host.currentGridPlan();
        ControlCenterLayoutPlan.Mode mode = plan.mode(selectedCompact);
        GridEditTarget target = findGridEditTarget(id, selectedCompact, host);
        if (target == null || proposed == null) return false;
        ControlCenterLayoutPlan.Item current = target.stored;
        if (current.locked) {
            host.rejectPageEdit(tr(host, R.string.ui_control_page_locked_error));
            return false;
        }
        ControlCenterLayoutPlan.Mode placed = ControlCenterGridPlacement.place(mode,
                current.withShape(proposed.shape, proposed.cornerRadius));
        if (placed == null) {
            host.rejectPageEdit(tr(host, R.string.ui_control_page_placement_error));
            return false;
        }
        ControlCenterConfig next = draft().withLayoutPlan(plan.withMode(selectedCompact, placed).encode());
        if (!edit(next)) return false;
        host.onPageDraftChanged();
        return true;
    }

    boolean editGridItem(String id,
            java.util.function.UnaryOperator<ControlCenterLayoutPlan.Item> change,
            boolean allowLocked, Host host) {
        boolean selectedCompact = compact();
        ControlCenterLayoutPlan plan = host.currentGridPlan();
        ControlCenterLayoutPlan.Mode mode = plan.mode(selectedCompact);
        GridEditTarget target = findGridEditTarget(id, selectedCompact, host);
        if (target == null) return false;
        ControlCenterLayoutPlan.Item stored = target.stored;
        if (stored.locked && !allowLocked) {
            host.rejectPageEdit(tr(host, R.string.ui_control_page_locked_error));
            return false;
        }
        ControlCenterLayoutPlan.Item changed = change.apply(stored);
        if (changed == null) return false;
        changed = clampPairGeometry(changed, mode.columns);
        ControlCenterLayoutPlan.Mode nextMode;
        if (allowLocked) {
            java.util.ArrayList<ControlCenterLayoutPlan.Item> items = new java.util.ArrayList<>();
            items.add(changed);
            for (ControlCenterLayoutPlan.Item item : mode.items) {
                if (!item.id.equals(stored.id)) items.add(item);
            }
            nextMode = new ControlCenterLayoutPlan.Mode(mode.columns, items);
        } else {
            nextMode = ControlCenterGridPlacement.place(mode, changed);
            if (nextMode == null) {
                host.rejectPageEdit(tr(host, R.string.ui_control_page_placement_error));
                return false;
            }
        }
        return publishPlan(plan.withMode(selectedCompact, nextMode), host);
    }

    boolean commitGridGesture(String id, int x, int y, int width, int height, Host host) {
        ControlCenterLayoutPlan.Item item = findResolvedItem(id, compact(), host);
        if (item == null || item.locked) return false;
        int nextX = x < 0 ? item.x : x;
        int nextY = y < 0 ? item.y : y;
        int nextWidth = width < 0 ? item.width : width;
        int nextHeight = height < 0 ? item.height : height;
        if (nextX == item.x && nextY == item.y
                && nextWidth == item.width && nextHeight == item.height) return false;
        return editGridItem(id, current -> current.withPosition(nextX, nextY)
                .withSize(nextWidth, nextHeight), false, host);
    }

    boolean applyGridAction(String id, ControlCenterGridEditor.EditAction action, Host host) {
        boolean selectedCompact = compact();
        ControlCenterLayoutPlan plan = host.currentGridPlan();
        GridEditTarget target = findGridEditTarget(id, selectedCompact, host);
        if (target == null) return false;
        ControlCenterLayoutPlan.Item item = target.stored;
        if (item.locked && action != ControlCenterGridEditor.EditAction.TOGGLE_LOCK)
            return false;
        if (action == ControlCenterGridEditor.EditAction.TOGGLE_LOCK) {
            return editGridItem(item.id, current -> current.withLocked(!current.locked), true, host);
        }
        if (action == ControlCenterGridEditor.EditAction.TOGGLE_HIDDEN) {
            return editGridItem(item.id, current -> current.withHidden(!current.hidden), false, host);
        }
        ControlCenterLayoutPlan next;
        switch (action) {
            case REMOVE:
                next = plan.removeItem(selectedCompact, item.id);
                break;
            case UNPAIR:
                if (item.type != ControlCenterLayoutPlan.Type.PAIR
                        && item.type != ControlCenterLayoutPlan.Type.GROUP) return false;
                next = plan.unpair(selectedCompact, item.id);
                break;
            case TOGGLE_DIRECTION:
                if (item.type != ControlCenterLayoutPlan.Type.PAIR) return false;
                boolean horizontal = item.direction != ControlCenterLayoutPlan.Direction.HORIZONTAL;
                int columns = plan.mode(selectedCompact).columns;
                int width = Math.min(columns, Math.max(horizontal ? 2 : 1, item.height));
                int height = Math.min(ControlCenterLayoutPlan.MAX_HEIGHT,
                        Math.max(horizontal ? 1 : 2, item.width));
                ControlCenterLayoutPlan.Item rotated = item.withDirection(horizontal
                        ? ControlCenterLayoutPlan.Direction.HORIZONTAL
                        : ControlCenterLayoutPlan.Direction.VERTICAL)
                        .withSize(width, height)
                        .withPosition(Math.min(item.x, columns - width),
                                Math.min(item.y, ControlCenterLayoutPlan.MAX_ROWS - height));
                return editGridItem(item.id, ignored -> rotated, false, host);
            default:
                return false;
        }
        ControlCenterLayoutPlan.Mode nextMode = next.mode(selectedCompact);
        if (!nextMode.validate().isEmpty()) {
            host.rejectPageEdit(tr(host, R.string.ui_control_page_space_error));
            return false;
        }
        return publishPlan(next, host);
    }

    private ControlCenterLayoutPlan.Item findResolvedItem(String id, boolean selectedCompact, Host host) {
        return host.resolvedGridMode(selectedCompact).items.stream()
                .filter(item -> item.id.equals(id)).findFirst().orElse(null);
    }

    /** The runtime resolver may expose one survivor tile for a stored pair whose other member is
     * unavailable. All writes must still target that original pair. */
    private GridEditTarget findGridEditTarget(String id, boolean selectedCompact, Host host) {
        ControlCenterLayoutPlan plan = host.currentGridPlan();
        ControlCenterLayoutPlan.Mode storedMode = plan.mode(selectedCompact);
        ControlCenterLayoutPlan.Mode resolved = host.resolvedGridMode(selectedCompact);
        ControlCenterLayoutPlan.Item displayed = resolved.items.stream()
                .filter(item -> item.id.equals(id)).findFirst().orElse(null);
        ControlCenterLayoutPlan.Item stored = null;
        if (displayed != null) {
            final ControlCenterLayoutPlan.Item shown = displayed;
            stored = storedMode.items.stream().filter(item -> item.id.equals(shown.id))
                    .findFirst().orElse(null);
            if (stored == null && shown.type == ControlCenterLayoutPlan.Type.TILE) {
                stored = storedMode.items.stream().filter(item -> item.type == ControlCenterLayoutPlan.Type.PAIR
                        && item.containsSpec(shown.firstSpec)).findFirst().orElse(null);
            }
        }
        if (stored == null) {
            stored = storedMode.items.stream().filter(item -> item.id.equals(id)).findFirst().orElse(null);
            if (stored != null && displayed == null) {
                final ControlCenterLayoutPlan.Item source = stored;
                displayed = resolved.items.stream().filter(item -> item.id.equals(source.id)).findFirst()
                        .orElse(null);
                if (displayed == null && source.type == ControlCenterLayoutPlan.Type.PAIR) {
                    displayed = resolved.items.stream()
                            .filter(item -> item.type == ControlCenterLayoutPlan.Type.TILE
                                    && source.containsSpec(item.firstSpec))
                            .findFirst().orElse(null);
                }
            }
        }
        return displayed == null || stored == null ? null : new GridEditTarget(displayed, stored);
    }

    private static ControlCenterLayoutPlan.Item clampPairGeometry(
            ControlCenterLayoutPlan.Item item, int columns) {
        if (item.type != ControlCenterLayoutPlan.Type.PAIR) return item;
        int width = Math.min(columns, Math.max(
                item.direction == ControlCenterLayoutPlan.Direction.HORIZONTAL ? 2 : 1,
                item.width));
        int height = Math.min(ControlCenterLayoutPlan.MAX_HEIGHT,
                Math.max(item.direction == ControlCenterLayoutPlan.Direction.VERTICAL ? 2 : 1,
                        item.height));
        if (width == item.width && height == item.height) return item;
        return item.withSize(width, height);
    }

    private static final class GridEditTarget {
        final ControlCenterLayoutPlan.Item displayed;
        final ControlCenterLayoutPlan.Item stored;

        GridEditTarget(ControlCenterLayoutPlan.Item displayed,
                ControlCenterLayoutPlan.Item stored) {
            this.displayed = displayed;
            this.stored = stored;
        }
    }

    private boolean publishPlan(ControlCenterLayoutPlan plan, Host host) {
        ControlCenterConfig next = draft().withLayoutPlan(plan.encode());
        if (!edit(next)) return false;
        host.onPageDraftChanged();
        return true;
    }

    void showAppearanceDialog(Host host) {
        host.showPageDialog(tr(host, R.string.ui_control_page_appearance_title), content -> {
            ControlCenterConfig settings = draft();
            boolean selectedCompact = compact();
            String modeLabel = selectedCompact ? tr(host, R.string.ui_control_page_compact_mode) : tr(host, R.string.ui_control_page_regular_mode);
            host.addPageSectionLabel(content, tr(host, R.string.ui_control_page_current_canvas));
            ControlCenterLayoutPlan initialPlan = host.currentGridPlan();
            host.addPageSlider(content, tr(host, R.string.ui_control_page_canvas_columns),
                    tr(host, R.string.ui_control_page_canvas_columns_summary, modeLabel), 3, 6,
                    initialPlan.mode(selectedCompact).columns, tr(host, R.string.ui_control_page_columns_suffix), value -> {
                        ControlCenterConfig current = draft();
                        ControlCenterLayoutPlan plan = host.currentGridPlan();
                        update(host, current.withLayoutPlan(plan.withMode(selectedCompact,
                                plan.mode(selectedCompact).withColumns(value)).encode()));
                    });

            host.addPageSectionLabel(content, tr(host, R.string.ui_control_page_header_info));
            host.addPageToggle(content, tr(host, R.string.ui_control_page_hide_date), tr(host, R.string.ui_control_page_hide_date_summary), settings.hideDate,
                    value -> update(host, draft().withHideDate(value)));
            host.addPageToggle(content, tr(host, R.string.ui_control_page_hide_carrier), tr(host, R.string.ui_control_page_hide_carrier_summary), settings.hideCarrier,
                    value -> update(host, draft().withHideCarrier(value)));

            host.addPageSectionLabel(content, tr(host, R.string.ui_control_page_appearance_scope));
            SystemSurfaceConfig systemSurfaces = settings.systemSurfaces;
            host.addPageToggle(content, tr(host, R.string.ui_control_page_power_menu), "", systemSurfaces.powerMenu, value ->
                    update(host, draft().withSystemSurfaces(draft().systemSurfaces.withPowerMenu(value))));
            host.addPageToggle(content, tr(host, R.string.ui_control_page_volume_menu), "", systemSurfaces.volumeDialog, value ->
                    update(host, draft().withSystemSurfaces(draft().systemSurfaces.withVolumeDialog(value))));
            host.addPageToggle(content, tr(host, R.string.ui_control_page_notification_center), "", systemSurfaces.notifications, value ->
                    update(host, draft().withSystemSurfaces(draft().systemSurfaces.withNotifications(value))));
            host.addPageToggle(content, tr(host, R.string.ui_control_page_heads_up), "", systemSurfaces.headsUp, value ->
                    update(host, draft().withSystemSurfaces(draft().systemSurfaces.withHeadsUp(value))));
            host.addPageSectionLabel(content, tr(host, R.string.ui_control_page_system_menu));
            host.addPageToggle(content, tr(host, R.string.ui_control_page_extended_power_menu), "", systemSurfaces.extendedPowerMenu, value ->
                    update(host, draft().withSystemSurfaces(draft().systemSurfaces.withExtendedPowerMenu(value))));
            host.addPageChoice(content, tr(host, R.string.ui_control_page_volume_length), new String[] {tr(host, R.string.ui_control_page_standard), tr(host, R.string.ui_control_page_short)},
                    systemSurfaces.compactVolume ? 1 : 0, index ->
                            update(host, draft().withSystemSurfaces(
                                    draft().systemSurfaces.withCompactVolume(index == 1))));

            host.addPageSectionLabel(content, tr(host, R.string.ui_control_page_global_appearance));
            ControlCenterSurface[] surfaces = ControlCenterSurface.values();
            String[] surfaceLabels = new String[surfaces.length];
            for (int i = 0; i < surfaces.length; i++) surfaceLabels[i] = surfaces[i].label(host.pageContext());
            View[] containerBlurRows = new View[2];
            host.addPageChoice(content, tr(host, R.string.ui_control_page_surface), surfaceLabels, settings.surface.ordinal(), index -> {
                ControlCenterSurface selected = surfaces[index];
                update(host, draft().withSurface(selected));
                for (View row : containerBlurRows) {
                    if (row != null) host.setPageSliderEnabled(row, selected.allowsContainerBlur());
                }
            });
            View[] borderWidthRow = new View[1];
            host.addPageToggle(content, tr(host, R.string.ui_control_page_global_border), "", settings.borderEnabled, value -> {
                ControlCenterConfig current = draft();
                update(host, current.withGlobalBorder(value, current.borderWidthDp));
                if (borderWidthRow[0] != null) host.setPageSliderEnabled(borderWidthRow[0], value);
            });
            borderWidthRow[0] = host.addPageSlider(content, tr(host, R.string.ui_control_page_border_width), "", 1, 4,
                    settings.borderWidthDp, " dp", value -> {
                        ControlCenterConfig current = draft();
                        update(host, current.withGlobalBorder(current.borderEnabled, value));
                    });
            host.setPageSliderEnabled(borderWidthRow[0], settings.borderEnabled);
            host.addPageHint(content, tr(host, R.string.ui_control_page_appearance_summary));
            host.addPageSlider(content, tr(host, R.string.ui_control_page_content_scale), tr(host, R.string.ui_control_page_content_scale_summary),
                    70, 130, settings.tileScale, "%", value -> update(host, draft().withTileScale(value)));
            host.addPageSlider(content, tr(host, R.string.ui_control_page_default_radius), tr(host, R.string.ui_control_page_default_radius_summary),
                    0, 32, settings.cornerRadius, " dp", value -> update(host, draft().withCornerRadius(value)));
            host.addPageSlider(content, tr(host, R.string.ui_control_page_item_spacing), tr(host, R.string.ui_control_page_item_spacing_summary),
                    0, 24, settings.spacing, " dp", value -> update(host, draft().withSpacing(value)));
            host.addPageBlurSlider(content, tr(host, R.string.ui_control_page_background_blur), settings.backgroundBlur, value -> {
                ControlCenterConfig current = draft();
                update(host, current.withBlur(value, current.cardBlur, current.tileBlur));
            });
            containerBlurRows[0] = host.addPageBlurSlider(content, tr(host, R.string.ui_control_page_card_blur), settings.cardBlur, value -> {
                ControlCenterConfig current = draft();
                update(host, current.withBlur(current.backgroundBlur, value, current.tileBlur));
            });
            containerBlurRows[1] = host.addPageBlurSlider(content, tr(host, R.string.ui_control_page_tile_blur), settings.tileBlur, value -> {
                ControlCenterConfig current = draft();
                update(host, current.withBlur(current.backgroundBlur, current.cardBlur, value));
            });
            for (View row : containerBlurRows) {
                host.setPageSliderEnabled(row, settings.surface.allowsContainerBlur());
            }
        });
    }

    private void update(Host host, ControlCenterConfig next) {
        if (edit(next)) host.onPageDraftChanged();
    }

    void replaceSession(ControlCenterEditorSession next) {
        if (next == null) throw new IllegalArgumentException("editor_session_required");
        session = next;
    }

    private static String tr(Host host, int resource, Object... args) {
        return ModuleStrings.get(host.pageContext(), resource, args);
    }
}
