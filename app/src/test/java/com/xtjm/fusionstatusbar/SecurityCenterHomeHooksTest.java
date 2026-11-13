package com.xtjm.fusionstatusbar;

import android.content.Context;
import android.content.ContextWrapper;
import android.content.res.ColorStateList;
import android.content.res.Resources;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import io.github.libxposed.api.XposedInterface;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.Arrays;
import java.util.Collections;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class SecurityCenterHomeHooksTest {
    private final Context context = RuntimeEnvironment.getApplication();
    private final int warning = R.string.app_language_title;
    private final int titleColor = android.R.color.white;
    private final int summaryColor = android.R.color.darker_gray;

    @Test public void cardChangesOnlyAppearanceAndKeepsNativeInputResultAndFailures() throws Throwable {
        Card card = new Card(context);
        Object[] input = {true, card, 1234L};
        int[] calls = {0};
        Object result = SecurityCenterHomeHooks.interceptCard(chain(null, input, args -> {
            calls[0]++;
            assertArrayEquals(input, args);
            nativeWarning(card, "11 percent", 0xffff0000);
            return "native result";
        }), true, binding());
        assertEquals("native result", result);
        assertEquals(1, calls[0]);
        assertArrayEquals(new Object[] {true, card, 1234L}, input);
        assertEquals(context.getString(warning), card.summaryTextView.getText().toString());
        assertEquals(context.getColor(titleColor), card.titleTextView.getCurrentTextColor());
        assertEquals(context.getColor(summaryColor), card.summaryTextView.getCurrentTextColor());
        assertEquals(View.GONE, card.redPointView.getVisibility());
        IllegalStateException failure = new IllegalStateException("native failure");
        assertSame(failure, assertThrows(IllegalStateException.class, () ->
                SecurityCenterHomeHooks.interceptCard(chain(null, input, args -> { throw failure; }),
                        true, binding())));
    }

    @Test public void disablingRestoresLatestNativeTextColorsAndDotAfterAnotherRefresh() throws Throwable {
        Card card = new Card(context);
        bindWarning(card, true, "first warning", 0xffff0000);
        bindWarning(card, true, "new warning", 0xffff8800);
        SecurityCenterHomeHooks.refresh(false);
        assertEquals("new warning", card.summaryTextView.getText().toString());
        assertEquals(0xffff8800, card.titleTextView.getCurrentTextColor());
        assertEquals(0xffff8801, card.summaryTextView.getCurrentTextColor());
        assertEquals(View.VISIBLE, card.redPointView.getVisibility());
        SecurityCenterHomeHooks.refresh(true);
        assertEquals(View.GONE, card.redPointView.getVisibility());
        SecurityCenterHomeHooks.refresh(false);
        assertEquals("new warning", card.summaryTextView.getText().toString());
    }

    @Test public void disabledCardIsUnchangedAndCanBeEnabledWithoutAnotherNativeRefresh() throws Throwable {
        Card card = new Card(context);
        bindWarning(card, false, "native warning", 0xffff0000);
        assertEquals("native warning", card.summaryTextView.getText().toString());
        assertEquals(View.VISIBLE, card.redPointView.getVisibility());
        SecurityCenterHomeHooks.refresh(true);
        assertEquals(View.GONE, card.redPointView.getVisibility());
        SecurityCenterHomeHooks.refresh(false);
    }

    @Test public void recycledRootDropsOldSnapshotsWithoutChangingOtherCards() throws Throwable {
        Card first = new Card(context);
        Card other = new Card(context);
        bindWarning(first, true, "old virus reminder", 0xffff0000);
        bindWarning(other, true, "other live card", 0xffff1100);
        LinearLayout root = new LinearLayout(context);
        root.addView(first.titleTextView);
        root.addView(first.summaryTextView);
        root.addView(first.redPointView);
        SecurityCenterHomeHooks.interceptFill(chain(null, new Object[] {root, null, 0}, args -> {
            assertEquals("old virus reminder", first.summaryTextView.getText().toString());
            nativeWarning(first, "unrelated newly bound card", 0xff00ff00);
            return null;
        }));
        assertEquals(View.GONE, other.redPointView.getVisibility());
        SecurityCenterHomeHooks.refresh(false);
        assertEquals("unrelated newly bound card", first.summaryTextView.getText().toString());
        assertEquals(0xff00ff00, first.titleTextView.getCurrentTextColor());
        assertEquals("other live card", other.summaryTextView.getText().toString());
    }

    @Test public void warningHeadlineHidesButProgressAndNativeTextRemainUnchanged() throws Throwable {
        Header header = new Header(context);
        String text = context.getString(warning);
        Object[] input = {text};
        assertEquals("native", SecurityCenterHomeHooks.interceptHeadline(chain(header, input, args -> {
            assertArrayEquals(input, args);
            header.e.setText((String) args[0]);
            return "native";
        }), headerField(), true, warning));
        assertEquals(text, header.e.getText().toString());
        assertEquals(View.INVISIBLE, header.e.getVisibility());
        SecurityCenterHomeHooks.interceptHeadline(chain(header, new Object[] {"Scanning 12%"}, args -> {
            assertEquals(View.VISIBLE, header.e.getVisibility());
            header.e.setText((String) args[0]);
            return null;
        }), headerField(), true, warning);
        assertEquals(View.VISIBLE, header.e.getVisibility());
        assertEquals("Scanning 12%", header.e.getText().toString());
    }

    @Test public void animationReappliesHiddenHeadlineAndDisablingRespectsNativeGone() throws Throwable {
        Header header = new Header(context);
        header.e.setText(context.getString(warning));
        SecurityCenterHomeHooks.interceptHeadline(chain(header, new Object[0], args -> null),
                headerField(), false, warning);
        assertEquals(View.VISIBLE, header.e.getVisibility());
        SecurityCenterHomeHooks.refresh(true);
        assertEquals(View.INVISIBLE, header.e.getVisibility());
        SecurityCenterHomeHooks.interceptHeadline(chain(header, new Object[0], args -> {
            header.e.setVisibility(View.VISIBLE);
            return null;
        }), headerField(), true, warning);
        assertEquals(View.INVISIBLE, header.e.getVisibility());
        SecurityCenterHomeHooks.refresh(false);
        assertEquals(View.VISIBLE, header.e.getVisibility());
        SecurityCenterHomeHooks.refresh(true);
        header.e.setVisibility(View.GONE);
        SecurityCenterHomeHooks.refresh(false);
        assertEquals(View.GONE, header.e.getVisibility());
    }

    @Test public void configuredTopFilterHidesScanningInHostLanguagesAndKeepsTheScanCall() throws Throwable {
        Field configured = SecurityCenterHomeHooks.class.getDeclaredField("WARNINGS");
        configured.setAccessible(true);
        int[] resources = ((int[]) configured.get(null)).clone();
        for (String label : new String[] {"正在体检…", "Scanning…"}) {
            Header header = new Header(scanningHost(label));
            int[] nativeCalls = {0};
            Object[] input = {label};
            assertEquals("scan started", SecurityCenterHomeHooks.interceptHeadline(chain(header, input, args -> {
                assertArrayEquals(input, args);
                nativeCalls[0]++;
                header.e.setText((String) args[0]);
                return "scan started";
            }), headerField(), true, resources));
            assertEquals(1, nativeCalls[0]);
            assertEquals(label, header.e.getText().toString());
            assertEquals(View.INVISIBLE, header.e.getVisibility());
            SecurityCenterHomeHooks.refresh(false);
            assertEquals(View.VISIBLE, header.e.getVisibility());
        }
    }

    @Test public void bottomScanningTextStaysHiddenAfterVisibilityAndAnimationButResultsStayVisible()
            throws Throwable {
        String label = "正在体检…";
        Header header = new Header(scanningHost(label));
        Field bottom = Header.class.getDeclaredField("f");
        bottom.setAccessible(true);
        SecurityCenterHomeHooks.interceptHeadline(chain(header, new Object[] {label}, args -> {
            header.f.setText((String) args[0]);
            return null;
        }), bottom, true, 0x7f120e06);
        assertEquals(View.INVISIBLE, header.f.getVisibility());
        for (Object[] args : new Object[][] {{View.VISIBLE}, {}}) {
            SecurityCenterHomeHooks.interceptHeadline(chain(header, args, nativeArgs -> {
                header.f.setVisibility(View.VISIBLE);
                return null;
            }), bottom, true, 0x7f120e06);
            assertEquals(View.INVISIBLE, header.f.getVisibility());
        }
        SecurityCenterHomeHooks.refresh(false);
        assertEquals(label, header.f.getText().toString());
        assertEquals(View.VISIBLE, header.f.getVisibility());
        SecurityCenterHomeHooks.interceptHeadline(chain(header, new Object[] {"发现危险应用，请立即处理"}, args -> {
            header.f.setText((String) args[0]);
            return null;
        }), bottom, true, 0x7f120e06);
        assertEquals(View.VISIBLE, header.f.getVisibility());
        assertEquals("发现危险应用，请立即处理", header.f.getText().toString());
        SecurityCenterHomeHooks.interceptHeadline(chain(header, new Object[] {View.GONE}, args -> {
            header.f.setVisibility((Integer) args[0]);
            return null;
        }), bottom, true, 0x7f120e06);
        SecurityCenterHomeHooks.refresh(false);
        assertEquals(View.GONE, header.f.getVisibility());
    }

    @Test public void unexpectedOrIncompleteCardKeepsNativeExecution() throws Throwable {
        int[] calls = {0};
        assertEquals("ok", SecurityCenterHomeHooks.interceptCard(chain(null,
                new Object[] {true, new Object()}, args -> { calls[0]++; return "ok"; }), true, binding()));
        Card incomplete = new Card(context);
        incomplete.summaryTextView = null;
        SecurityCenterHomeHooks.interceptCard(chain(null, new Object[] {true, incomplete},
                args -> { calls[0]++; return null; }), true, binding());
        assertEquals(2, calls[0]);
    }

    private void bindWarning(Card card, boolean enabled, String text, int color) throws Throwable {
        SecurityCenterHomeHooks.interceptCard(chain(null, new Object[] {true, card}, args -> {
            nativeWarning(card, text, color);
            return null;
        }), enabled, binding());
    }

    private void nativeWarning(Card card, String text, int color) {
        card.titleTextView.setTextColor(ColorStateList.valueOf(color));
        card.summaryTextView.setTextColor(ColorStateList.valueOf(color + 1));
        card.summaryTextView.setText(text);
        card.redPointView.setVisibility(View.VISIBLE);
    }

    private SecurityCenterHomeHooks.CardBinding binding() throws ReflectiveOperationException {
        return new SecurityCenterHomeHooks.CardBinding(Card.class, titleColor, summaryColor, warning);
    }

    private Field headerField() throws ReflectiveOperationException {
        Field field = Header.class.getDeclaredField("e");
        field.setAccessible(true);
        return field;
    }

    private Context scanningHost(String label) {
        Resources hostResources = new Resources(context.getAssets(), context.getResources().getDisplayMetrics(),
                context.getResources().getConfiguration()) {
            @Override public String getString(int id) throws NotFoundException {
                if (id == 0x7f120e06) return label;
                return super.getString(id);
            }
        };
        return new ContextWrapper(context) {
            @Override public Resources getResources() { return hostResources; }
        };
    }

    private XposedInterface.Chain chain(Object owner, Object[] input, NativeCall nativeCall) {
        return (XposedInterface.Chain) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[] {XposedInterface.Chain.class}, (proxy, method, args) -> {
                    if (method.getName().equals("getArgs")) return Collections.unmodifiableList(Arrays.asList(input));
                    if (method.getName().equals("getThisObject")) return owner;
                    if (method.getName().equals("proceed")) return nativeCall.run(args == null
                            || args.length == 0 ? input.clone() : ((Object[]) args[0]).clone());
                    throw new UnsupportedOperationException(method.getName());
                });
    }

    private interface NativeCall { Object run(Object[] args) throws Throwable; }
    private static final class Header {
        private final TextView e, f;
        Header(Context context) { e = new TextView(context); f = new TextView(context); }
    }
    private static final class Card {
        private final TextView titleTextView;
        private TextView summaryTextView;
        private final ImageView redPointView;
        Card(Context context) {
            titleTextView = new TextView(context);
            summaryTextView = new TextView(context);
            redPointView = new ImageView(context);
        }
    }
}
