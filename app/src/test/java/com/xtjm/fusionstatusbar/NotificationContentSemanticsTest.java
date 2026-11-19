package com.xtjm.fusionstatusbar;

import android.app.Activity;
import android.app.Notification;
import android.app.Person;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.RemoteViews;
import android.widget.TextView;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, qualifiers = "notnight-mdpi")
public class NotificationContentSemanticsTest {
    private final Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
    private final FusionConfig settings = FusionConfig.defaults().withControlCenter(
            ControlCenterConfig.defaults().withEnabled(true)
                    .withSurface(ControlCenterSurface.HIGHLIGHT_GLASS).withTileScale(130));

    @Test public void preservesBodyEmphasisDisabledColorsAndFontScale() {
        Row row = new Row(activity);
        TextView body = label(row, Color.BLACK);
        TextView emphasis = label(row, 0xff0645ad);
        ColorStateList states = new ColorStateList(new int[][] {
                new int[] {-android.R.attr.state_enabled}, new int[] {android.R.attr.state_pressed}, new int[0]
        }, new int[] {0xff666666, 0xffaa3300, 0xff0645ad});
        emphasis.setTextColor(states);
        float size = body.getTextSize();
        try {
            NotificationSurfaceHooks.applyRow(row, settings);
            assertEquals(Color.BLACK, body.getCurrentTextColor());
            assertSame(states, emphasis.getTextColors());
            emphasis.setEnabled(false);
            assertEquals(0xff666666, emphasis.getCurrentTextColor());
            emphasis.setEnabled(true);
            emphasis.setPressed(true);
            assertEquals(0xffaa3300, emphasis.getCurrentTextColor());
            assertEquals(size, body.getTextSize(), 0f);
        } finally { disable(row); }
    }

    @Test public void remoteViewsMediaIconAndActionsRemainHostOwned() {
        Row row = new Row(activity);
        RemoteViews remote = new RemoteViews("android", android.R.layout.simple_list_item_1);
        remote.setTextViewText(android.R.id.text1, "App-defined reply");
        remote.setTextColor(android.R.id.text1, 0xff8b008b);
        View content = remote.apply(activity, row);
        row.addView(content);
        TextView reply = content.findViewById(android.R.id.text1);
        FrameLayout media = new FrameLayout(activity);
        row.addView(media);
        TextView track = new TextView(activity);
        track.setText("Track title");
        track.setTextColor(0xff008577);
        media.addView(track);
        ImageView icon = new ImageView(activity);
        ColorStateList tint = ColorStateList.valueOf(0xffb71c1c);
        icon.setImageTintList(tint);
        media.addView(icon);
        AtomicInteger clicks = new AtomicInteger();
        reply.setOnClickListener(v -> clicks.incrementAndGet());
        icon.setOnClickListener(v -> clicks.incrementAndGet());
        try {
            NotificationSurfaceHooks.applyRow(row, settings);
            assertEquals(0xff8b008b, reply.getCurrentTextColor());
            assertEquals(0xff008577, track.getCurrentTextColor());
            assertSame(tint, icon.getImageTintList());
            assertSame(row, content.getParent());
            assertSame(media, icon.getParent());
            reply.performClick();
            icon.performClick();
            assertEquals(2, clicks.get());
        } finally { disable(row); }
    }

    @Test public void rebindThemeAndDisableKeepTheLatestHostValues() {
        Row row = new Row(activity);
        TextView label = label(row, Color.BLUE);
        try {
            NotificationSurfaceHooks.applyRow(row, settings);
            ColorStateList rebound = ColorStateList.valueOf(0xff006400);
            label.setTextColor(rebound);
            label.setTextSize(21);
            float reboundSize = label.getTextSize();
            NotificationSurfaceHooks.applyRow(row, settings);
            Configuration config = new Configuration(activity.getResources().getConfiguration());
            config.uiMode = (config.uiMode & ~Configuration.UI_MODE_NIGHT_MASK) | Configuration.UI_MODE_NIGHT_YES;
            activity.getResources().updateConfiguration(config, activity.getResources().getDisplayMetrics());
            row.getViewTreeObserver().dispatchOnPreDraw();
            row.mBackgroundNormal.getViewTreeObserver().dispatchOnPreDraw();
            disable(row);
            assertSame(rebound, label.getTextColors());
            assertEquals(reboundSize, label.getTextSize(), 0f);
            assertEquals("Host content", label.getText().toString());
        } finally { disable(row); }
    }

    @Test public void messagingAndMediaTemplatesKeepTheirNativeColorStateLists() {
        Notification.Style[] styles = new Notification.Style[] {
                new Notification.MessagingStyle(new Person.Builder().setName("Me").build())
                        .addMessage("Incoming message", 123L, new Person.Builder().setName("Sender").build()),
                new Notification.MediaStyle()
        };
        for (Notification.Style style : styles) {
            Row row = new Row(activity);
            View template = new Notification.Builder(activity, "fixture")
                    .setSmallIcon(android.R.drawable.ic_dialog_info).setContentTitle("Host title")
                    .setContentText("Host body").setStyle(style).createContentView().apply(activity, row);
            row.addView(template);
            List<TextView> labels = new ArrayList<>();
            collectText(template, labels);
            assertFalse("The real notification template must contain readable text", labels.isEmpty());
            List<ColorStateList> colors = new ArrayList<>();
            for (TextView text : labels) colors.add(text.getTextColors());
            try {
                NotificationSurfaceHooks.applyRow(row, settings);
                for (int index = 0; index < labels.size(); index++)
                    assertSame(colors.get(index), labels.get(index).getTextColors());
                assertSame(row, template.getParent());
            } finally { disable(row); }
        }
    }

    @Test public void sameUiModeThemeChangeAndOffscreenReuseKeepNewHostContent() {
        Row row = new Row(activity);
        TextView label = label(row, Color.BLUE);
        try {
            NotificationSurfaceHooks.applyRow(row, settings);
            int mode = activity.getResources().getConfiguration().uiMode;
            row.removeView(label);
            activity.setTheme(android.R.style.Theme_Material_Light_NoActionBar);
            ColorStateList reused = ColorStateList.valueOf(0xff673ab7);
            label.setTextColor(reused);
            label.setText("Reused notification");
            label.setTextSize(23);
            float reusedSize = label.getTextSize();
            row.addView(label);
            row.getViewTreeObserver().dispatchOnPreDraw();
            NotificationSurfaceHooks.applyRow(row, settings);
            disable(row);
            assertEquals(mode, activity.getResources().getConfiguration().uiMode);
            assertSame(reused, label.getTextColors());
            assertEquals(reusedSize, label.getTextSize(), 0f);
            assertEquals("Reused notification", label.getText().toString());
        } finally { disable(row); }
    }

    private static void collectText(View view, List<TextView> labels) {
        if (view instanceof TextView text) labels.add(text);
        if (view instanceof ViewGroup group)
            for (int index = 0; index < group.getChildCount(); index++) collectText(group.getChildAt(index), labels);
    }

    private TextView label(Row row, int color) {
        TextView text = new TextView(activity);
        text.setText("Host content");
        text.setTextColor(color);
        row.addView(text);
        return text;
    }

    private void disable(Row row) {
        NotificationSurfaceHooks.applyRow(row, settings.withControlCenter(settings.controlCenter
                .withSystemSurfaces(settings.controlCenter.systemSurfaces.withNotifications(false))));
    }

    static final class Row extends FrameLayout {
        final View mBackgroundNormal;
        Row(Activity activity) {
            super(activity);
            mBackgroundNormal = new View(activity);
            mBackgroundNormal.setBackground(new ColorDrawable(Color.WHITE));
            addView(mBackgroundNormal);
        }
    }
}
