package com.xtjm.fusionstatusbar;

import android.view.View;
import android.view.ViewGroup;
import java.util.ArrayList;

/** Gives the lockscreen's battery branch the configured status-bar content height. */
final class KeyguardStatusBarHeightController {
    private KeyguardStatusBarHeightController() { }

    static int heightPx(View root, FusionConfig config) {
        return Math.max(1, StatusBarWindowHeightController.heightPx(root.getContext(),
                config.statusBarHeight) - StatusBarWindowHeightController.waterfallInsetTop(root));
    }

    static void apply(ViewGroup root, View battery, FusionConfig config) {
        if (root == null || config == null) return;

        int availableHeight = heightPx(root, config);
        applyHeight(root, availableHeight);
        allowOverflow(root);
        if (battery == null || battery == root) return;

        // Verify the whole branch before touching descendants. A stale battery reference
        // can belong to a different host tree during a keyguard transition.
        ArrayList<View> branch = new ArrayList<>();
        View current = battery;
        while (current != root) {
            branch.add(current);
            if (!(current.getParent() instanceof ViewGroup parent)) return;
            current = parent;
        }

        ViewGroup parent = root;
        for (int index = branch.size() - 1; index >= 0; index--) {
            View child = branch.get(index);
            ViewGroup.LayoutParams params = child.getLayoutParams();
            int margins = params instanceof ViewGroup.MarginLayoutParams marginParams
                    ? marginParams.topMargin + marginParams.bottomMargin : 0;
            availableHeight = Math.max(1, availableHeight
                    - parent.getPaddingTop() - parent.getPaddingBottom() - margins);
            applyHeight(child, availableHeight);
            if (child instanceof ViewGroup group) {
                allowOverflow(group);
                parent = group;
            }
        }
    }

    private static void applyHeight(View view, int height) {
        ViewGroup.LayoutParams params = view.getLayoutParams();
        // Keep the parent's actual LayoutParams type, including its existing positioning.
        if (params == null || params.height == height) return;
        params.height = height;
        view.setLayoutParams(params);
    }

    private static void allowOverflow(ViewGroup group) {
        if (group.getClipChildren()) group.setClipChildren(false);
        if (group.getClipToPadding()) group.setClipToPadding(false);
    }
}
