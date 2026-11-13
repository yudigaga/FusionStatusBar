package com.xtjm.fusionstatusbar;

import android.content.res.ColorStateList;
import android.content.res.Resources;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.view.ViewParent;
import android.widget.ImageView;
import android.widget.TextView;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Map;
import java.util.WeakHashMap;
import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;

/** Cosmetic changes at the verified 13.8 home render boundaries; native scan data stays untouched. */
final class SecurityCenterHomeHooks {
    private static final Map<Object, CardState> CARDS = new WeakHashMap<>();
    private static final Map<TextView, HeadlineState> HEADLINES = new WeakHashMap<>();
    private static final int SCANNING_TEXT = 0x7f120e06;
    private static final int[] WARNINGS = {0x7f1208c4, 0x7f1208c6, SCANNING_TEXT};
    private static final Runnable CHANGED = () -> {
        Runnable update = () -> refresh(enabled());
        if (Looper.myLooper() == Looper.getMainLooper()) update.run();
        else new Handler(Looper.getMainLooper()).post(update);
    };

    private SecurityCenterHomeHooks() { }

    static int install(XposedModule module, ClassLoader loader) {
        int count = 0;
        try {
            Class<?> owner = Class.forName("com.miui.securityscan.ui.main.MainContentFrame", false, loader);
            Field text = field(owner, "e", TextView.class);
            count += hook(module, owner.getDeclaredMethod("setStatusTopText", String.class),
                    chain -> interceptHeadline(chain, text, enabled(), WARNINGS));
            count += hook(module, owner.getDeclaredMethod("n"),
                    chain -> interceptHeadline(chain, text, enabled(), WARNINGS));
            Field bottom = field(owner, "f", TextView.class);
            // The result line is shared with real findings; only hide its scanning label.
            for (Method method : new Method[] {
                    owner.getDeclaredMethod("setStatusBottomText", String.class),
                    owner.getDeclaredMethod("setStatusBottomVisible", int.class),
                    owner.getDeclaredMethod("t")}) {
                count += hook(module, method,
                        chain -> interceptHeadline(chain, bottom, enabled(), SCANNING_TEXT));
            }
            deoptimize(module, loader, "com.miui.securityscan.MainFragment",
                    "com.miui.securityscan.MainFragment$c0", "com.miui.securityscan.MainFragment$j0");
        } catch (ReflectiveOperationException | LinkageError error) {
            module.log(Log.WARN, FusionModule.TAG, "phone manager home headline unavailable", error);
        }
        try {
            String prefix = "com.miui.common.card.models.FuncGrid6CardModel$";
            Class<?> owner = Class.forName(prefix + "FuncGrid6ViewHolder", false, loader);
            Class<?> display = Class.forName(prefix + "DisplayViewHolder", false, loader);
            CardBinding scan = new CardBinding(display, 0x7f060eb2, 0x7f060eb1, 0x7f120fbb);
            CardBinding memory = new CardBinding(display, 0x7f060eb2, 0x7f060eb1, 0x7f120fc5);
            count += hook(module, owner.getDeclaredMethod("refreshSecurityScan", boolean.class, display),
                    chain -> interceptCard(chain, enabled(), scan));
            count += hook(module, owner.getDeclaredMethod("refreshOptimizemanage", boolean.class, display, long.class),
                    chain -> interceptCard(chain, enabled(), memory));
            count += hook(module, owner.getDeclaredMethod("fillData", View.class,
                    Class.forName("com.miui.common.card.models.BaseCardModel", false, loader), int.class),
                    SecurityCenterHomeHooks::interceptFill);
            deoptimize(module, loader, owner.getName(), owner.getName() + "$1");
        } catch (ReflectiveOperationException | LinkageError error) {
            module.log(Log.WARN, FusionModule.TAG, "phone manager home cards unavailable", error);
        }
        SecurityCenterConfigRuntime.addListener(CHANGED);
        return count;
    }

    private static boolean enabled() {
        return SecurityCenterConfigRuntime.current().isEnabled(SecurityCenterFeature.HIDE_HOME_WARNINGS);
    }

    private static int hook(XposedModule module, Method method, XposedInterface.Hooker callback)
            throws NoSuchMethodException {
        if (method.getReturnType() != void.class) throw new NoSuchMethodException("host_return_type_changed");
        return SecurityCenterHookSupport.hook(module, method, "home:" + method.toGenericString(), callback);
    }

    private static void deoptimize(XposedModule module, ClassLoader loader, String... owners) {
        for (String owner : owners) try {
            for (Method method : Class.forName(owner, false, loader).getDeclaredMethods()) {
                try { module.deoptimize(method); } catch (Throwable ignored) { }
            }
        } catch (ReflectiveOperationException | LinkageError ignored) { }
    }

    static Object interceptCard(XposedInterface.Chain chain, boolean enabled, CardBinding binding) throws Throwable {
        Object display = chain.getArgs().size() > 1 ? chain.getArgs().get(1) : null;
        CardState previous;
        synchronized (CARDS) { previous = CARDS.remove(display); }
        if (previous != null) previous.restore(display);
        Object result = chain.proceed();
        CardViews views = binding.read(display);
        if (views != null) {
            CardState state = new CardState(binding, views);
            synchronized (CARDS) { CARDS.put(display, state); }
            if (enabled) state.apply(display);
        }
        return result;
    }

    static Object interceptFill(XposedInterface.Chain chain) throws Throwable {
        if (!chain.getArgs().isEmpty() && chain.getArgs().get(0) instanceof View root) {
            synchronized (CARDS) {
                var iterator = CARDS.entrySet().iterator();
                while (iterator.hasNext()) {
                    var entry = iterator.next();
                    CardViews views = entry.getValue().binding.read(entry.getKey());
                    if (views != null && within(views.title, root)) {
                        entry.getValue().restore(entry.getKey());
                        iterator.remove();
                    }
                }
            }
        }
        return chain.proceed();
    }

    private static boolean within(View view, View root) {
        if (view == root) return true;
        for (ViewParent parent = view.getParent(); parent != null; parent = parent.getParent())
            if (parent == root) return true;
        return false;
    }

    static Object interceptHeadline(XposedInterface.Chain chain, Field field, boolean enabled,
            int... warnings) throws Throwable {
        TextView view = headline(chain.getThisObject(), field);
        if (view != null) synchronized (HEADLINES) {
            HeadlineState state = HEADLINES.get(view);
            if (state != null) state.restore(view);
        }
        Object result = chain.proceed();
        view = headline(chain.getThisObject(), field);
        if (view != null) synchronized (HEADLINES) {
            HeadlineState state = new HeadlineState(warnings);
            HEADLINES.put(view, state);
            if (enabled) state.apply(view);
        }
        return result;
    }

    private static TextView headline(Object owner, Field field) {
        try { return field.get(owner) instanceof TextView view ? view : null; }
        catch (ReflectiveOperationException | IllegalArgumentException error) { return null; }
    }

    static void refresh(boolean enabled) {
        synchronized (CARDS) {
            for (var entry : new ArrayList<>(CARDS.entrySet())) {
                entry.getValue().restore(entry.getKey());
                if (enabled) entry.getValue().apply(entry.getKey());
            }
        }
        synchronized (HEADLINES) {
            for (var entry : HEADLINES.entrySet()) {
                entry.getValue().restore(entry.getKey());
                if (enabled) entry.getValue().apply(entry.getKey());
            }
        }
    }

    private static Field field(Class<?> owner, String name, Class<?> type) throws ReflectiveOperationException {
        Field field = owner.getDeclaredField(name);
        if (field.getType() != type) throw new NoSuchFieldException("host_field_type_changed:" + name);
        field.setAccessible(true);
        return field;
    }

    static final class CardBinding {
        private final Field title, summary, dot;
        private final int titleColor, summaryColor, normalText;
        CardBinding(Class<?> owner, int titleColor, int summaryColor, int normalText)
                throws ReflectiveOperationException {
            title = field(owner, "titleTextView", TextView.class);
            summary = field(owner, "summaryTextView", TextView.class);
            dot = field(owner, "redPointView", ImageView.class);
            this.titleColor = titleColor; this.summaryColor = summaryColor; this.normalText = normalText;
        }
        CardViews read(Object owner) {
            if (owner == null) return null;
            try {
                if (title.get(owner) instanceof TextView t && summary.get(owner) instanceof TextView s
                        && dot.get(owner) instanceof ImageView d) return new CardViews(t, s, d);
            } catch (ReflectiveOperationException | IllegalArgumentException ignored) { }
            return null;
        }
    }

    private record CardViews(TextView title, TextView summary, ImageView dot) { }

    private static final class CardState {
        final CardBinding binding;
        final ColorStateList titleColors, summaryColors;
        final CharSequence summaryText;
        final int dotVisibility;
        CardState(CardBinding binding, CardViews views) {
            this.binding = binding;
            titleColors = views.title.getTextColors(); summaryColors = views.summary.getTextColors();
            summaryText = views.summary.getText().toString(); dotVisibility = views.dot.getVisibility();
        }
        void restore(Object owner) {
            CardViews views = binding.read(owner);
            if (views == null) return;
            views.title.setTextColor(titleColors); views.summary.setTextColor(summaryColors);
            views.summary.setText(summaryText); views.dot.setVisibility(dotVisibility);
        }
        void apply(Object owner) {
            CardViews views = binding.read(owner);
            if (views == null) return;
            try {
                var context = views.title.getContext();
                ColorStateList normalTitle = context.getColorStateList(binding.titleColor);
                ColorStateList normalSummary = context.getColorStateList(binding.summaryColor);
                String normalText = context.getString(binding.normalText);
                views.title.setTextColor(normalTitle); views.summary.setTextColor(normalSummary);
                views.summary.setText(normalText); views.dot.setVisibility(View.GONE);
            } catch (Resources.NotFoundException unavailable) {
                restore(owner);
                Log.w(FusionModule.TAG, "phone manager normal card resources unavailable", unavailable);
            }
        }
    }

    private static final class HeadlineState {
        final int[] warnings;
        boolean hidden;
        HeadlineState(int[] warnings) { this.warnings = warnings.clone(); }
        void restore(TextView view) {
            // Native result transitions may already have made the view GONE.
            if (hidden && view.getVisibility() == View.INVISIBLE) view.setVisibility(View.VISIBLE);
            hidden = false;
        }
        void apply(TextView view) {
            if (view.getVisibility() != View.VISIBLE) return;
            for (int resource : warnings) try {
                if (TextUtils.equals(view.getText(), view.getResources().getString(resource))) {
                    hidden = true;
                    view.setVisibility(View.INVISIBLE);
                    return;
                }
            } catch (Resources.NotFoundException ignored) { }
        }
    }
}
