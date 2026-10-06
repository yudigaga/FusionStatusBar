package com.xtjm.fusionstatusbar;

import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.Switch;

/** Owns the settings page's editable control-center session, independent of its view tree. */
final class ControlCenterSettingsPage {
    interface ContentBuilder { void build(LinearLayout content); }

    interface Host {
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
                ? "组合卡片" : host.tileLabel(selected.firstSpec);
        host.showPageDialog(title, content -> buildItemProperties(content, id, selected, host));
    }

    private void buildItemProperties(LinearLayout content, String id,
            ControlCenterLayoutPlan.Item selected, Host host) {
        View groupEditor = selected.type == ControlCenterLayoutPlan.Type.GROUP
                ? host.addPageGroupEditor(content, id) : null;
        boolean selectedCompact = compact();
        int columns = host.currentGridPlan().mode(selectedCompact).columns;
        host.addPageSlider(content, "宽度", "占用网格列数", 1, columns,
                selected.width, " 列", value -> editGridItem(id, item -> item.withSize(value,
                        item.height), false, host));
        host.addPageSlider(content, "高度", "占用网格行数", 1, 6,
                selected.height, " 行", value -> editGridItem(id, item -> item.withSize(item.width,
                        value), false, host));
        ControlCenterCardStylePicker stylePicker = host.addPageCardStylePicker(content, selected,
                proposed -> {
                    editItemAppearance(id, proposed, host);
                    return findResolvedItem(id, compact(), host);
                });

        Button remove = host.addPageButton(content, "从网格移除", !selected.locked, () -> {
            applyGridAction(id, ControlCenterGridEditor.EditAction.REMOVE, host);
            if (selected.type == ControlCenterLayoutPlan.Type.GROUP
                    && findResolvedItem(id, compact(), host) == null) content.removeAllViews();
        });
        Button[] lock = new Button[1];
        lock[0] = host.addPageButton(content,
                selected.locked ? "解锁此项目" : "锁定此项目", true, () -> {
                    editGridItem(id, item -> item.withLocked(!item.locked), true, host);
                    ControlCenterLayoutPlan.Item current = findResolvedItem(id, compact(), host);
                    if (current == null) return;
                    lock[0].setText(current.locked ? "解锁此项目" : "锁定此项目");
                    stylePicker.bind(current);
                    remove.setEnabled(!current.locked);
                    if (groupEditor != null) host.refreshPageGroupEditor(groupEditor);
                });
        Button[] visibility = new Button[1];
        visibility[0] = host.addPageButton(content,
                selected.hidden ? "显示此项目" : "隐藏此项目", true, () -> {
            editGridItem(id, item -> item.withHidden(!item.hidden), true, host);
            ControlCenterLayoutPlan.Item current = findResolvedItem(id, compact(), host);
            if (current != null) visibility[0].setText(current.hidden ? "显示此项目" : "隐藏此项目");
        });
        host.addPageSlider(content, "层级", "重叠时的绘制顺序", -20, 20,
                selected.zIndex, "", value -> editGridItem(id, item -> item.withZIndex(value),
                        true, host));
        if (columns > selected.width) {
            host.addPageSlider(content, "横向位置", "网格起始列", 0, columns - selected.width,
                    selected.x, " 列", value -> editGridItem(id,
                            item -> item.withPosition(value, item.y), false, host));
        }
        host.addPageSlider(content, "纵向位置", "网格起始行", 0,
                Math.min(ControlCenterLayoutPlan.MAX_ROWS - selected.height,
                        Math.max(24, selected.y + 4)), selected.y, " 行",
                value -> editGridItem(id, item -> item.withPosition(item.x, value), false, host));
        if (selected.type == ControlCenterLayoutPlan.Type.PAIR) {
            boolean isHorizontal = selected.direction == ControlCenterLayoutPlan.Direction.HORIZONTAL;
            Button[] direction = new Button[1];
            direction[0] = host.addPageButton(content,
                    isHorizontal ? "改为上下排列" : "改为左右排列", true, () -> {
                applyGridAction(id, ControlCenterGridEditor.EditAction.TOGGLE_DIRECTION, host);
                ControlCenterLayoutPlan.Item current = findResolvedItem(id, compact(), host);
                if (current != null) direction[0].setText(current.direction
                        == ControlCenterLayoutPlan.Direction.HORIZONTAL ? "改为上下排列" : "改为左右排列");
            });
            host.addPageButton(content, "拆分卡片", true,
                    () -> applyGridAction(id, ControlCenterGridEditor.EditAction.UNPAIR, host));
        } else if (selected.type == ControlCenterLayoutPlan.Type.TILE) {
            host.addPageButton(content, "与另一磁贴组成卡片", true, host::showPairCreation);
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
            host.rejectPageEdit("项目已锁定，请先解锁");
            return false;
        }
        ControlCenterLayoutPlan.Mode placed = ControlCenterGridPlacement.place(mode,
                current.withShape(proposed.shape, proposed.cornerRadius));
        if (placed == null) {
            host.rejectPageEdit("目标位置超出画布或被锁定项目占用");
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
            host.rejectPageEdit("项目已锁定，请先解锁");
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
                host.rejectPageEdit("目标位置超出画布或被锁定项目占用");
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
            host.rejectPageEdit("当前位置空间不足，布局未修改");
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
        host.showPageDialog("布局与外观", content -> {
            ControlCenterConfig settings = draft();
            boolean selectedCompact = compact();
            String modeLabel = selectedCompact ? "紧凑布局" : "标准布局";
            host.addPageSectionLabel(content, "当前画布");
            ControlCenterLayoutPlan initialPlan = host.currentGridPlan();
            host.addPageSlider(content, "画布列数", "仅调整当前" + modeLabel + "的网格列数", 3, 6,
                    initialPlan.mode(selectedCompact).columns, " 列", value -> {
                        ControlCenterConfig current = draft();
                        ControlCenterLayoutPlan plan = host.currentGridPlan();
                        update(host, current.withLayoutPlan(plan.withMode(selectedCompact,
                                plan.mode(selectedCompact).withColumns(value)).encode()));
                    });

            host.addPageSectionLabel(content, "控制中心顶部信息");
            host.addPageToggle(content, "隐藏日期", "仅隐藏控制中心顶部日期", settings.hideDate,
                    value -> update(host, draft().withHideDate(value)));
            host.addPageToggle(content, "隐藏运营商", "保留隐私提示图标", settings.hideCarrier,
                    value -> update(host, draft().withHideCarrier(value)));

            host.addPageSectionLabel(content, "统一外观范围");
            SystemSurfaceConfig systemSurfaces = settings.systemSurfaces;
            host.addPageToggle(content, "接管电源菜单", "", systemSurfaces.powerMenu, value ->
                    update(host, draft().withSystemSurfaces(draft().systemSurfaces.withPowerMenu(value))));
            host.addPageToggle(content, "接管音量菜单", "", systemSurfaces.volumeDialog, value ->
                    update(host, draft().withSystemSurfaces(draft().systemSurfaces.withVolumeDialog(value))));
            host.addPageToggle(content, "接管通知中心通知", "", systemSurfaces.notifications, value ->
                    update(host, draft().withSystemSurfaces(draft().systemSurfaces.withNotifications(value))));
            host.addPageToggle(content, "接管悬浮通知", "", systemSurfaces.headsUp, value ->
                    update(host, draft().withSystemSurfaces(draft().systemSurfaces.withHeadsUp(value))));
            host.addPageSectionLabel(content, "系统菜单");
            host.addPageToggle(content, "三条电源菜单", "", systemSurfaces.extendedPowerMenu, value ->
                    update(host, draft().withSystemSurfaces(draft().systemSurfaces.withExtendedPowerMenu(value))));
            host.addPageChoice(content, "音量条长度", new String[] {"标准", "短款"},
                    systemSurfaces.compactVolume ? 1 : 0, index ->
                            update(host, draft().withSystemSurfaces(
                                    draft().systemSurfaces.withCompactVolume(index == 1))));

            host.addPageSectionLabel(content, "全局外观");
            ControlCenterSurface[] surfaces = ControlCenterSurface.values();
            String[] surfaceLabels = new String[surfaces.length];
            for (int i = 0; i < surfaces.length; i++) surfaceLabels[i] = surfaces[i].label;
            View[] containerBlurRows = new View[2];
            host.addPageChoice(content, "全局容器背景", surfaceLabels, settings.surface.ordinal(), index -> {
                ControlCenterSurface selected = surfaces[index];
                update(host, draft().withSurface(selected));
                for (View row : containerBlurRows) {
                    if (row != null) host.setPageSliderEnabled(row, selected.allowsContainerBlur());
                }
            });
            View[] borderWidthRow = new View[1];
            host.addPageToggle(content, "全局描边", "", settings.borderEnabled, value -> {
                ControlCenterConfig current = draft();
                update(host, current.withGlobalBorder(value, current.borderWidthDp));
                if (borderWidthRow[0] != null) host.setPageSliderEnabled(borderWidthRow[0], value);
            });
            borderWidthRow[0] = host.addPageSlider(content, "描边粗细", "", 1, 4,
                    settings.borderWidthDp, " dp", value -> {
                        ControlCenterConfig current = draft();
                        update(host, current.withGlobalBorder(current.borderEnabled, value));
                    });
            host.setPageSliderEnabled(borderWidthRow[0], settings.borderEnabled);
            host.addPageHint(content, "以下设置同时应用于标准布局和紧凑布局。项目尺寸、形状和独立圆角请在画布中点击项目调整。");
            host.addPageSlider(content, "内容缩放", "统一缩放组件外框、内容、间距和占用高度",
                    70, 130, settings.tileScale, "%", value -> update(host, draft().withTileScale(value)));
            host.addPageSlider(content, "默认圆角", "用于矩形项目；单项目设置的独立圆角优先，圆角为 0 时使用此默认值",
                    0, 32, settings.cornerRadius, " dp", value -> update(host, draft().withCornerRadius(value)));
            host.addPageSlider(content, "项目间距", "调整画布中相邻项目之间的留白",
                    0, 24, settings.spacing, " dp", value -> update(host, draft().withSpacing(value)));
            host.addPageBlurSlider(content, "背景模糊", settings.backgroundBlur, value -> {
                ControlCenterConfig current = draft();
                update(host, current.withBlur(value, current.cardBlur, current.tileBlur));
            });
            containerBlurRows[0] = host.addPageBlurSlider(content, "卡片模糊", settings.cardBlur, value -> {
                ControlCenterConfig current = draft();
                update(host, current.withBlur(current.backgroundBlur, value, current.tileBlur));
            });
            containerBlurRows[1] = host.addPageBlurSlider(content, "磁贴模糊", settings.tileBlur, value -> {
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
}
