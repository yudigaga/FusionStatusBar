package com.xtjm.fusionstatusbar;

import android.content.res.ColorStateList;
import android.graphics.Color;
import android.view.View;
import android.widget.TextView;
import androidx.core.graphics.ColorUtils;
import java.lang.reflect.Method;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/** Pairs the ordinary OEM blur carrier with readable tones for its visible stock content. */
final class NotificationGlassContrast {
    private static final String WRAPPER = "com.android.systemui.statusbar.notification.row.wrapper.";
    private static final String HYBRID = "com.android.systemui.statusbar.notification.row.HybridNotificationView";
    private static final String GROUP =
            "com.android.systemui.statusbar.notification.stack.NotificationChildrenContainer";
    private static final int MAX_GROUP_CHILDREN = 8;
    private static final Class<?>[] NO_ARGUMENTS = new Class<?>[0];
    private static final Map<View, Map<TextView, TextColors>> ROW_COLORS = new WeakHashMap<>();
    private static final Map<TextView, TextColors> ACTIVE_COLORS = new WeakHashMap<>();
    private static final double MIN_CONTRAST = 4.5;

    private NotificationGlassContrast() { }

    private enum Role { TITLE, BODY, METADATA }

    enum Tone {
        DAY(0xb0f8fafd, 0xff17191e, 0xff292f39, 0xff383e48),
        NIGHT(0xbd181b24, 0xfff8f9fc, 0xffe4e7ed, 0xffc8cdd6);

        final int tint;
        private final int darkBackdrop, lightBackdrop;
        private final int title, body, metadata;

        Tone(int tint, int title, int body, int metadata) {
            this.tint = tint;
            this.title = title;
            this.body = body;
            this.metadata = metadata;
            // Black and white bound the range of opaque colors behind a steady-state glass card.
            darkBackdrop = ColorUtils.compositeColors(tint, Color.BLACK);
            lightBackdrop = ColorUtils.compositeColors(tint, Color.WHITE);
        }

        int foreground(Role role) {
            return switch (role) {
                case TITLE -> title;
                case BODY -> body;
                case METADATA -> metadata;
            };
        }

        boolean readsAcrossBackdrops(int foreground) {
            return ColorUtils.calculateContrast(foreground, darkBackdrop) >= MIN_CONTRAST
                    && ColorUtils.calculateContrast(foreground, lightBackdrop) >= MIN_CONTRAST;
        }
    }

    static Tone apply(View row) {
        Map<TextView, Role> targets = new IdentityHashMap<>();
        View content = visibleContent(row);
        addStockText(targets, content);
        boolean whiteGroupArrow = content != null && content.getClass().getName().equals(GROUP)
                && addGroupText(targets, content);
        if (targets.isEmpty()) {
            restore(row);
            return null;
        }
        Tone tone = !whiteGroupArrow && ControlCenterSurface.HIGHLIGHT_GLASS.resolve(row.getContext())
                == ControlCenterSurface.HIGHLIGHT_GLASS ? Tone.DAY : Tone.NIGHT;
        Map<TextView, TextColors> changes = ROW_COLORS.computeIfAbsent(row, ignored -> new WeakHashMap<>());
        changes.entrySet().removeIf(entry -> {
            if (targets.containsKey(entry.getKey())) return false;
            restore(entry.getKey(), entry.getValue());
            return true;
        });
        targets.forEach((text, role) -> apply(text, role, tone, changes));
        return tone;
    }

    static void restore(View row) {
        Map<TextView, TextColors> changes = ROW_COLORS.remove(row);
        if (changes != null) changes.forEach(NotificationGlassContrast::restore);
    }

    private static void apply(TextView text, Role role, Tone tone, Map<TextView, TextColors> changes) {
        ColorStateList current = text.getTextColors();
        TextColors previous = ACTIVE_COLORS.get(text);
        boolean owned = previous != null && current == previous.applied;
        ColorStateList host = owned ? previous.host : current;
        int foreground = host.getDefaultColor();
        if (!text.isEnabled() || host.isStateful() || !visibleNeutral(foreground)
                || tone.readsAcrossBackdrops(foreground)) {
            if (previous != null) restore(text, previous);
            changes.remove(text);
            return;
        }
        int resolved = tone.foreground(role);
        boolean sameColor = owned && current.getDefaultColor() == resolved;
        if (sameColor && changes.get(text) == previous) return;
        // A distinct list lets us recognize our write and yield to the next OEM bind.
        ColorStateList applied = sameColor ? current
                : new ColorStateList(new int[][] {new int[0]}, new int[] {resolved});
        if (!sameColor) text.setTextColor(applied);
        TextColors update = new TextColors(host, applied);
        // A grouped child can move from the parent's backing to its own without a rebind.
        ACTIVE_COLORS.put(text, update);
        changes.put(text, update);
    }

    private static boolean visibleNeutral(int color) {
        int red = Color.red(color), green = Color.green(color), blue = Color.blue(color);
        return Color.alpha(color) != 0
                && Math.max(red, Math.max(green, blue)) - Math.min(red, Math.min(green, blue)) <= 24;
    }

    private static void restore(TextView text, TextColors colors) {
        if (text == null || ACTIVE_COLORS.get(text) != colors) return;
        ACTIVE_COLORS.remove(text);
        if (text.getTextColors() == colors.applied) text.setTextColor(colors.host);
    }

    private static View visibleContent(View row) {
        // OEM chooses the public layout and group header here. Never traverse the hidden layout.
        Object current = call(row, "getContentView");
        if (current == null) current = call(row, "getShowingLayout");
        return current instanceof View content && visibleWithin(content, row) ? content : null;
    }

    private static void addStockText(Map<TextView, Role> targets, View content) {
        if (content == null) return;
        Object wrapper = call(content, "getVisibleWrapper");
        if (wrapper == null) {
            Object singleLine = NotificationSurfaceHooks.field(content, "mSingleLineView");
            if (Integer.valueOf(3).equals(NotificationSurfaceHooks.field(content, "mVisibleType"))
                    && singleLine instanceof View hybrid && hybrid.getClass().getName().equals(HYBRID)
                    && visibleWithin(hybrid, content)) {
                add(targets, hybrid, call(hybrid, "getTitleView"), Role.TITLE);
                add(targets, hybrid, call(hybrid, "getTextView"), Role.BODY);
            }
            return;
        }
        if (!(NotificationSurfaceHooks.field(wrapper, "mView") instanceof View template)
                || !visibleWithin(template, content)
                || call(wrapper, "getCustomBackgroundColor") instanceof Integer color && color != 0)
            return;
        switch (wrapper.getClass().getName()) {
            case WRAPPER + "MiuiNotificationTemplateViewWrapper" -> {
                addField(targets, template, wrapper, "mTitle", Role.TITLE);
                addField(targets, template, wrapper, "mText", Role.BODY);
                addField(targets, template, wrapper, "mSubText", Role.METADATA);
                addField(targets, template, wrapper, "mTime", Role.METADATA);
                addField(targets, template, wrapper, "mChronometer", Role.METADATA);
            }
            case WRAPPER + "MiuiNotificationBigTextViewWrapper" -> {
                addField(targets, template, wrapper, "mTitle", Role.TITLE);
                addField(targets, template, wrapper, "mBigText", Role.BODY);
                addField(targets, template, wrapper, "mSubText", Role.METADATA);
                addField(targets, template, wrapper, "mTime", Role.METADATA);
            }
            case WRAPPER + "NotificationHeaderViewWrapper" -> {
                addField(targets, template, wrapper, "mAppNameText", Role.TITLE);
                addField(targets, template, wrapper, "mHeaderText", Role.BODY);
                addResource(targets, template, "time", "android", Role.METADATA);
            }
            case WRAPPER + "MiuiNotificationOneLineViewWrapper" -> {
                addResource(targets, template, "title", "com.android.systemui", Role.TITLE);
                addResource(targets, template, "text", "com.android.systemui", Role.BODY);
            }
            default -> { }
        }
    }

    private static boolean addGroupText(Map<TextView, Role> targets, View group) {
        Object injector = NotificationSurfaceHooks.field(group, "mInjector");
        boolean expanded = Boolean.TRUE.equals(NotificationSurfaceHooks.field(group, "mChildrenExpanded"));
        boolean useDarkTone = false;
        if (expanded && NotificationSurfaceHooks.field(injector, "mMiuiGroupHeader") instanceof View header
                && header.getAlpha() > 0f && visibleWithin(header, group)) {
            addField(targets, header, injector, "mGroupInfo", Role.BODY);
            // The native white collapse arrow shares this backing and keeps its own drawable.
            useDarkTone = true;
        }
        if (!expanded && call(group, "getAttachedChildren") instanceof List<?> children) {
            for (int index = 0; index < Math.min(children.size(), MAX_GROUP_CHILDREN); index++) {
                if (!(children.get(index) instanceof View child) || child.getAlpha() <= 0f
                        || !visibleWithin(child, group)) continue;
                View background = NotificationSurfaceHooks.backgroundOf(child);
                if (background != null && background.getVisibility() != View.VISIBLE)
                    addStockText(targets, visibleContent(child));
            }
        }
        return useDarkTone;
    }

    private static void addField(Map<TextView, Role> targets, View root, Object wrapper, String field, Role role) {
        add(targets, root, NotificationSurfaceHooks.field(wrapper, field), role);
    }

    private static void addResource(Map<TextView, Role> targets, View root, String name, String owner, Role role) {
        int id = root.getResources().getIdentifier(name, "id", owner);
        if (id != 0) add(targets, root, root.findViewById(id), role);
    }

    private static void add(Map<TextView, Role> targets, View root, Object value, Role role) {
        if (value instanceof TextView text && visibleWithin(text, root)) targets.put(text, role);
    }

    private static boolean visibleWithin(View view, View ancestor) {
        for (View current = view; current != null; ) {
            if (current.getVisibility() != View.VISIBLE) return false;
            if (current == ancestor) return true;
            current = current.getParent() instanceof View parent ? parent : null;
        }
        return false;
    }

    private static Object call(Object owner, String name) {
        if (owner == null) return null;
        try {
            Method method = RuntimeReflection.exact(owner.getClass(), name, NO_ARGUMENTS, true);
            return method == null ? null : method.invoke(owner);
        } catch (ReflectiveOperationException | RuntimeException ignored) { return null; }
    }

    private record TextColors(ColorStateList host, ColorStateList applied) { }
}
