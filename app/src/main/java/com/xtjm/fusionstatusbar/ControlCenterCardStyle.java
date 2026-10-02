package com.xtjm.fusionstatusbar;

/** Named combinations of existing runtime-supported shape fields, not a new wire format. */
enum ControlCenterCardStyle {
    DEFAULT("跟随默认", ControlCenterLayoutPlan.Shape.RECTANGLE, 0),
    SUBTLE("微圆角", ControlCenterLayoutPlan.Shape.RECTANGLE, 8),
    CLASSIC("经典圆角", ControlCenterLayoutPlan.Shape.RECTANGLE, 20),
    SOFT("柔和圆角", ControlCenterLayoutPlan.Shape.RECTANGLE, 28),
    ROUNDED("大圆角", ControlCenterLayoutPlan.Shape.RECTANGLE, 40),
    CAPSULE("胶囊", ControlCenterLayoutPlan.Shape.CAPSULE, 0),
    CIRCLE("圆形", ControlCenterLayoutPlan.Shape.CIRCLE, 0),
    CUSTOM("自定义", null, 0);

    final String label;
    final ControlCenterLayoutPlan.Shape shape;
    final int radius;

    ControlCenterCardStyle(String label, ControlCenterLayoutPlan.Shape shape, int radius) {
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

    @Override public String toString() { return label; }
}
