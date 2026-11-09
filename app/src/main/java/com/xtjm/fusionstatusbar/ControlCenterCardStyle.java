package com.xtjm.fusionstatusbar;

/** Named combinations of existing runtime-supported shape fields, not a new wire format. */
enum ControlCenterCardStyle {
    DEFAULT(R.string.ui_control_style_default, "跟随默认", ControlCenterLayoutPlan.Shape.RECTANGLE, 0),
    SUBTLE(R.string.ui_control_style_subtle, "微圆角", ControlCenterLayoutPlan.Shape.RECTANGLE, 8),
    CLASSIC(R.string.ui_control_style_classic, "经典圆角", ControlCenterLayoutPlan.Shape.RECTANGLE, 20),
    SOFT(R.string.ui_control_style_soft, "柔和圆角", ControlCenterLayoutPlan.Shape.RECTANGLE, 28),
    ROUNDED(R.string.ui_control_style_rounded, "大圆角", ControlCenterLayoutPlan.Shape.RECTANGLE, 40),
    CAPSULE(R.string.ui_control_style_capsule, "胶囊", ControlCenterLayoutPlan.Shape.CAPSULE, 0),
    CIRCLE(R.string.ui_control_style_circle, "圆形", ControlCenterLayoutPlan.Shape.CIRCLE, 0),
    CUSTOM(R.string.ui_control_style_custom, "自定义", null, 0);

    @Deprecated final String label;
    @androidx.annotation.StringRes final int labelResource;
    final ControlCenterLayoutPlan.Shape shape;
    final int radius;

    ControlCenterCardStyle(int labelResource, String label, ControlCenterLayoutPlan.Shape shape, int radius) {
        this.labelResource = labelResource;
        this.label = label;
        this.shape = shape;
        this.radius = radius;
    }

    ControlCenterLayoutPlan.Item apply(ControlCenterLayoutPlan.Item item) {
        return shape == null ? item : item.withShape(shape, radius);
    }

    static ControlCenterCardStyle of(ControlCenterLayoutPlan.Item item) {
        for (ControlCenterCardStyle style : values()) {
            if (style.shape == item.shape && (style.shape != ControlCenterLayoutPlan.Shape.RECTANGLE
                    || style.radius == item.cornerRadius)) return style;
        }
        return CUSTOM;
    }

    String label(android.content.Context context) { return ModuleStrings.get(context, labelResource); }

    @Override public String toString() { return label; }
}
