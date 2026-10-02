package com.xtjm.fusionstatusbar;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import java.util.Objects;

/** Visual-only renderer. No gesture, persistence, or SystemUI side effects. */
final class ControlCenterCardView extends FrameLayout {
    static final int ACTIVE_SURFACE = Color.rgb(248, 249, 251);
    static final int INACTIVE_SURFACE = Color.argb(200, 24, 25, 27);

    /** Display data only. Sample state is explicit; captures are not stretchable card content. */
    static final class Model {
        final String spec, secondSpec, label, secondLabel;
        final boolean paired, horizontal, active, secondActive, large;
        final ControlCenterLayoutPlan.Shape shape;
        final int radiusPx, scale;
        final ControlCenterCapturedStyle firstStyle, secondStyle;

        Model(String spec, String secondSpec, String label, String secondLabel,
                boolean horizontal, boolean active, boolean secondActive, boolean large,
                ControlCenterLayoutPlan.Shape shape, int radiusPx, int scale) {
            this(spec, secondSpec, label, secondLabel, horizontal, active, secondActive, large,
                    shape, radiusPx, scale, null, null);
        }

        Model(String spec, String secondSpec, String label, String secondLabel,
                boolean horizontal, boolean active, boolean secondActive, boolean large,
                ControlCenterLayoutPlan.Shape shape, int radiusPx, int scale,
                ControlCenterCapturedStyle firstStyle, ControlCenterCapturedStyle secondStyle) {
            this.spec = spec;
            this.secondSpec = secondSpec;
            this.label = label;
            this.secondLabel = secondLabel;
            this.paired = secondLabel != null;
            this.horizontal = horizontal;
            this.active = active;
            this.secondActive = secondActive;
            this.large = large;
            this.shape = shape;
            this.radiusPx = radiusPx;
            this.scale = scale;
            this.firstStyle = firstStyle;
            this.secondStyle = secondStyle;
        }

        boolean sameVisual(Model other) {
            return other != null && Objects.equals(spec, other.spec)
                    && Objects.equals(secondSpec, other.secondSpec)
                    && Objects.equals(label, other.label) && Objects.equals(secondLabel, other.secondLabel)
                    && horizontal == other.horizontal && active == other.active
                    && secondActive == other.secondActive && large == other.large
                    && shape == other.shape && radiusPx == other.radiusPx && scale == other.scale
                    && firstStyle == other.firstStyle && secondStyle == other.secondStyle;
        }
    }

    private final CardSurface surface;
    private final GradientDrawable material = new GradientDrawable();
    private Model model;

    void setMaterialStrength(int card, int tile) {
        if (model == null) return;
        boolean cardSurface = model.large || model.paired || ControlCenterComponentSpec.isSpecial(model.spec)
                || model.spec.startsWith("group:");
        int strength = cardSurface ? card : tile;
        material.setAlpha(Math.round(255f * strength / 100));
        int backdrop = ControlCenterIcons.composite(material.getColor().getDefaultColor(), strength, 0xff080808);
        if (surface.getChildCount() > 0 && surface.getChildAt(0) instanceof ControlCenterPreviewTile preview) {
            preview.setBackdropColor(backdrop);
        }
        if (surface.getChildCount() > 0 && surface.getChildAt(0) instanceof ControlCenterGroupView group) {
            group.getBackground().setAlpha(Math.round(255f * card / 100));
            int groupBackdrop = ControlCenterIcons.composite(group.item().group.surface.color, card, backdrop);
            group.visitMemberViews((spec, view) -> {
                if (view.getBackground() != null) view.getBackground().setAlpha(Math.round(255f * tile / 100));
                updatePreviewBackdrop(view, tile, groupBackdrop);
            });
        }
        if (model.paired && surface.getChildCount() > 0 && surface.getChildAt(0) instanceof LinearLayout pair) {
            for (int i = 0; i < pair.getChildCount(); i++) {
                if (pair.getChildAt(i).getBackground() != null) pair.getChildAt(i).getBackground().setAlpha(Math.round(255f * tile / 100));
                updatePreviewBackdrop(pair.getChildAt(i), tile, backdrop);
            }
        }
    }

    private void updatePreviewBackdrop(View view, int strength, int backdrop) {
        if (view instanceof ControlCenterPreviewTile preview && view.getBackground() instanceof GradientDrawable background) {
            preview.setBackdropColor(ControlCenterIcons.composite(background.getColor().getDefaultColor(), strength, backdrop));
        }
    }

    ControlCenterCardView(Context context) {
        super(context);
        setClipChildren(false);
        setClipToPadding(false);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
        surface = new CardSurface(context);
        surface.setBackground(material);
        surface.setClipToOutline(true);
        surface.setElevation(dp(2));
        surface.setOutlineAmbientShadowColor(Color.argb(26, 0, 0, 0));
        surface.setOutlineSpotShadowColor(Color.argb(26, 0, 0, 0));
        addView(surface, new FrameLayout.LayoutParams(-1, -1, Gravity.CENTER));
    }

    void setEditing(boolean editing, boolean animated) {
        animate().cancel();
        surface.animate().cancel();
        float scale = editing ? 1.05f : 1f;
        float elevation = dp(editing ? 8 : 2);
        if (animated) {
            animate().scaleX(scale).scaleY(scale).setDuration(140).start();
            surface.animate().z(elevation).setDuration(140).start();
        } else {
            setScaleX(scale);
            setScaleY(scale);
            surface.setZ(elevation);
        }
    }

    void bind(Model next) {
        if (next.sameVisual(model)) return;
        model = next;
        surface.removeAllViews();
        material.setColor(!next.paired && next.active ? ACTIVE_SURFACE : INACTIVE_SURFACE);
        material.setShape(next.shape == ControlCenterLayoutPlan.Shape.CIRCLE
                ? GradientDrawable.OVAL : GradientDrawable.RECTANGLE);
        if (next.paired) {
            LinearLayout pair = new LinearLayout(getContext());
            pair.setOrientation(next.horizontal ? LinearLayout.HORIZONTAL : LinearLayout.VERTICAL);
            pair.setPadding(dp(4), dp(4), dp(4), dp(4));
            pair.addView(pairContent(next.spec, next.label, next.active),
                    new LinearLayout.LayoutParams(next.horizontal ? 0 : -1,
                            next.horizontal ? -1 : 0, 1));
            LinearLayout.LayoutParams second = new LinearLayout.LayoutParams(
                    next.horizontal ? 0 : -1, next.horizontal ? -1 : 0, 1);
            if (next.horizontal) second.leftMargin = dp(4); else second.topMargin = dp(4);
            pair.addView(pairContent(next.secondSpec, next.secondLabel, next.secondActive), second);
            surface.addView(pair, new FrameLayout.LayoutParams(-1, -1));
        } else {
            surface.addView(content(next.spec, next.label, next.active,
                    next.large && next.shape != ControlCenterLayoutPlan.Shape.CIRCLE),
                    new FrameLayout.LayoutParams(-1, -1));
        }
        requestLayout();
        invalidate();
    }

    private View pairContent(String spec, String label, boolean active) {
        View view = content(spec, label, active, false);
        GradientDrawable background = new GradientDrawable();
        background.setColor(active ? ACTIVE_SURFACE : Color.TRANSPARENT);
        background.setCornerRadius(model.radiusPx);
        view.setBackground(background);
        view.setClipToOutline(true);
        return view;
    }

    private View content(String spec, String label, boolean active, boolean large) {
        ControlCenterCapturedStyle captured = Objects.equals(spec, model.spec) ? model.firstStyle : model.secondStyle;
        if (ControlCenterComponentSpec.BRIGHTNESS.equalsIgnoreCase(spec)
                || ControlCenterComponentSpec.VOLUME.equalsIgnoreCase(spec)
                || ControlCenterComponentSpec.SLIDER.equalsIgnoreCase(spec)) {
            return new SliderPreview(getContext(), spec, captured);
        }
        if (ControlCenterComponentSpec.MEDIA.equalsIgnoreCase(spec)) return new MediaPreview(getContext());
        if (ControlCenterComponentSpec.isSpecial(spec)) return new ComponentCardPreview(getContext(), label);
        return new ControlCenterPreviewTile(getContext(), spec,
                captured != null && !captured.label.isEmpty() ? captured.label : label,
                captured != null && !captured.subtitle.isEmpty() ? captured.subtitle : active ? "已开启" : "已关闭",
                large ? ControlCenterPreviewTile.MODE_LARGE : ControlCenterPreviewTile.MODE_CIRCLE,
                active, 0, model.scale, false, captured == null ? null : captured.icon);
    }

    @Override protected void onMeasure(int widthSpec, int heightSpec) {
        super.onMeasure(widthSpec, heightSpec);
        int width = getMeasuredWidth();
        int height = getMeasuredHeight();
        if (model != null && model.shape == ControlCenterLayoutPlan.Shape.CIRCLE) {
            width = height = Math.min(width, height);
        }
        surface.measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY));
        if (model != null) {
            material.setCornerRadius(model.shape == ControlCenterLayoutPlan.Shape.CAPSULE
                    ? Math.min(width, height) / 2f
                    : Math.min(model.radiusPx, Math.min(width, height) / 2f));
        }
        surface.updateClip(width, height);
    }

    /** Clip content as well as the material, including software-rendered reference images. */
    private final class CardSurface extends FrameLayout {
        private final Path clip = new Path();
        CardSurface(Context context) { super(context); }
        void updateClip(int width, int height) {
            clip.reset();
            if (model != null && model.shape == ControlCenterLayoutPlan.Shape.CIRCLE) {
                clip.addCircle(width / 2f, height / 2f, Math.min(width, height) / 2f, Path.Direction.CW);
            } else {
                float radius = material.getCornerRadius();
                clip.addRoundRect(0, 0, width, height, radius, radius, Path.Direction.CW);
            }
            invalidate();
        }
        @Override protected void dispatchDraw(Canvas canvas) {
            int save = canvas.save();
            canvas.clipPath(clip);
            super.dispatchDraw(canvas);
            canvas.restoreToCount(save);
        }
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
    private static final Typeface MEDIUM_TYPEFACE = Typeface.create("sans-serif-medium", Typeface.NORMAL);

    private final class SliderPreview extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final android.graphics.drawable.Drawable icon;
        private final float level;

        SliderPreview(Context context, String spec, ControlCenterCapturedStyle captured) {
            super(context);
            boolean brightness = ControlCenterComponentSpec.BRIGHTNESS.equalsIgnoreCase(spec);
            level = captured != null && captured.level >= 0 ? captured.level : brightness ? 0.70f : 0.20f;
            icon = ControlCenterIcons.load(context, brightness ? R.drawable.cc_icon_wb_sunny
                    : R.drawable.cc_icon_volume_up, brightness ? Color.rgb(255, 159, 5) : Color.rgb(52, 130, 255));
        }

        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float width = getWidth(), height = getHeight();
            paint.setColor(ACTIVE_SURFACE);
            boolean wide = width > height;
            if (wide) canvas.drawRect(0, 0, width * level, height, paint);
            else canvas.drawRect(0, height * (1f - level), width, height, paint);
            float edge = Math.min(width, height);
            float size = Math.min(edge * 0.5f, dp(34)) * Math.min(1.3f, model.scale / 100f);
            float cx = wide ? Math.min(height * 0.5f, width * 0.22f) : width / 2f;
            float cy = wide ? height / 2f : height - Math.min(width * 0.5f, height * 0.22f);
            ControlCenterIcons.draw(canvas, icon, cx, cy, size);
        }
    }

    void bindGroup(ControlCenterLayoutPlan.Item item, java.util.Map<String, ControlCenterCapturedStyle> styles,
            java.util.Map<String, String> labels) {
        bindGroup(item, styles, labels, 24);
    }

    void bindGroup(ControlCenterLayoutPlan.Item item, java.util.Map<String, ControlCenterCapturedStyle> styles,
            java.util.Map<String, String> labels, int defaultRadius) {
        model = new Model(item.id, "", "组合卡片", null, false, false, false, true,
                item.shape, dp(item.cornerRadius > 0 ? item.cornerRadius : defaultRadius), 100);
        surface.removeAllViews();
        material.setColor(Color.TRANSPARENT);
        material.setShape(item.shape == ControlCenterLayoutPlan.Shape.CIRCLE ? GradientDrawable.OVAL : GradientDrawable.RECTANGLE);
        surface.addView(new ControlCenterGroupView(getContext(), item, defaultRadius, member -> {
            ControlCenterCapturedStyle style = styles.get(member.spec);
            boolean active = style != null && style.state >= 0 ? style.state == 2
                    : member.spec.equals("wifi") || member.spec.equals("bt") || member.spec.equals("cell");
            View icon = new ControlCenterPreviewTile(getContext(), member.spec, "", "",
                    ControlCenterPreviewTile.MODE_CIRCLE, active, 32, 100, true, style == null ? null : style.icon);
            return new ControlCenterGroupEntry(getContext(), icon, member.showLabel, false, () -> new String[] {
                    style != null && !style.label.isEmpty() ? style.label : labels.getOrDefault(member.spec, member.spec),
                    style != null && !style.subtitle.isEmpty() ? style.subtitle : active ? "已开启" : "已关闭"});
        }), new FrameLayout.LayoutParams(-1, -1));
        requestLayout();
    }

    private final class MediaPreview extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final android.graphics.drawable.Drawable play, previous, next, cast;

        MediaPreview(Context context) {
            super(context);
            play = ControlCenterIcons.load(context, R.drawable.cc_icon_play_arrow, ACTIVE_SURFACE);
            previous = ControlCenterIcons.load(context, R.drawable.cc_icon_skip_previous, Color.rgb(136, 139, 143));
            next = ControlCenterIcons.load(context, R.drawable.cc_icon_skip_next, Color.rgb(136, 139, 143));
            cast = ControlCenterIcons.load(context, R.drawable.cc_icon_cast, Color.rgb(116, 119, 123));
            paint.setColor(Color.rgb(153, 155, 158));
            paint.setTypeface(MEDIUM_TYPEFACE);
            paint.setTextAlign(Paint.Align.CENTER);
        }

        @Override protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
            paint.setTextSize(dp(14) * model.scale / 100f);
            float available = Math.max(1, width - dp(24));
            if (paint.measureText("暂无播放") > available) {
                paint.setTextSize(paint.getTextSize() * available / paint.measureText("暂无播放"));
            }
        }

        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float width = getWidth(), height = getHeight();
            float edge = Math.min(width, height);
            float castSize = Math.min(dp(24), edge * 0.15f);
            ControlCenterIcons.draw(canvas, cast, width - edge * 0.14f, edge * 0.15f, castSize);
            canvas.drawText("暂无播放", width / 2f,
                    height * 0.46f - (paint.ascent() + paint.descent()) / 2f, paint);
            float size = Math.min(dp(28), edge * 0.16f) * model.scale / 100f;
            float cy = height * 0.80f;
            ControlCenterIcons.draw(canvas, play, width * 0.5f, cy, size * 1.2f);
            ControlCenterIcons.draw(canvas, previous, width * 0.20f, cy, size);
            ControlCenterIcons.draw(canvas, next, width * 0.80f, cy, size);
        }
    }

    private final class ComponentCardPreview extends View {
        private final String title;
        private final android.text.TextPaint paint = new android.text.TextPaint(Paint.ANTI_ALIAS_FLAG);
        private String displayTitle;
        private float mark, gap;

        ComponentCardPreview(Context context, String title) {
            super(context);
            this.title = title == null ? "控制中心" : title;
            paint.setTypeface(MEDIUM_TYPEFACE);
        }

        @Override protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
            mark = Math.min(dp(32), Math.min(width, height) * 0.4f);
            gap = Math.min(dp(12), width * 0.04f);
            paint.setTextSize(dp(15) * model.scale / 100f);
            float available = Math.max(1, width - mark - gap - dp(24));
            if (paint.measureText(title) > available) {
                paint.setTextSize(Math.max(dp(8), paint.getTextSize() * available / paint.measureText(title)));
            }
            displayTitle = android.text.TextUtils.ellipsize(title, paint, available,
                    android.text.TextUtils.TruncateAt.END).toString();
        }

        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            if (displayTitle == null) return;
            paint.setColor(Color.rgb(235, 236, 238));
            float start = (getWidth() - paint.measureText(displayTitle) - mark - gap) / 2f;
            float cy = getHeight() / 2f;
            canvas.drawText(displayTitle, start + mark + gap,
                    cy - (paint.ascent() + paint.descent()) / 2f, paint);
            paint.setColor(Color.rgb(147, 115, 255));
            canvas.drawCircle(start + mark * 0.24f, cy - mark * 0.16f, mark * 0.23f, paint);
            paint.setColor(Color.rgb(101, 221, 175));
            canvas.drawCircle(start + mark * 0.78f, cy - mark * 0.10f, mark * 0.17f, paint);
            paint.setColor(Color.rgb(255, 157, 137));
            canvas.drawCircle(start + mark * 0.50f, cy + mark * 0.28f, mark * 0.14f, paint);
        }
    }
}
