package com.xtjm.fusionstatusbar;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.os.UserManager;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;

/** Authenticated configuration, application acknowledgements and immutable previews. */
public final class FusionConfigProvider extends ContentProvider {
    private static final String SYSTEM_UI_PACKAGE = "com.android.systemui";

    @Override public boolean onCreate() { return true; }

    @Override public Bundle call(String method, String arg, Bundle extras) {
        Context context = getContext();
        String caller = getCallingPackage();
        enforceCaller(caller, context == null ? null : context.getPackageName(),
                FusionConfig.METHOD_PREVIEW_REQUEST.equals(method)
                        || FusionConfig.METHOD_PREVIEW_CLEAR.equals(method));
        if (FusionConfig.METHOD_GET.equals(method)) {
            UserManager users = context.getSystemService(UserManager.class);
            if (users != null && !users.isUserUnlocked()
                    && !FusionConfigStore.availableBeforeUnlock(context)) return null;
            FusionConfig config = FusionConfigStore.read(context);
            if (SYSTEM_UI_PACKAGE.equals(caller)) FusionActivationStatus.reportRead(context,
                    config.revision, FusionActivationStatus.callerSession(
                            android.os.Binder.getCallingUid(), android.os.Binder.getCallingPid(), extras));
            return config.toBundle();
        }
        if (FusionConfig.METHOD_REPORT_APPLIED.equals(method)) {
            if (!SYSTEM_UI_PACKAGE.equals(caller)) throw new SecurityException("systemui_report_only");
            Bundle result = new Bundle();
            result.putBoolean("accepted", FusionActivationStatus.recordApplied(context, extras,
                    FusionActivationStatus.callerSession(android.os.Binder.getCallingUid(),
                            android.os.Binder.getCallingPid(), extras)));
            return result;
        }
        ControlCenterPreviewStore previews = new ControlCenterPreviewStore(context);
        try {
            Bundle result;
            if (FusionConfig.METHOD_PREVIEW_REQUEST.equals(method)) result = previews.request(false);
            else if (FusionConfig.METHOD_PREVIEW_CLEAR.equals(method)) result = previews.request(true);
            else if (FusionConfig.METHOD_PREVIEW_STATE.equals(method)) return previews.state();
            else if (FusionConfig.METHOD_PREVIEW_READY.equals(method)) {
                result = previews.ready(extras == null ? 0L : extras.getLong(FusionConfig.KEY_PREVIEW_READY, 0L),
                        extras == null ? "" : extras.getString(FusionConfig.KEY_PREVIEW_LAYOUT, ""),
                        extras == null ? "" : extras.getString(FusionConfig.KEY_PREVIEW_CATALOG, ""));
            } else if (FusionConfig.METHOD_PREVIEW_CATALOG.equals(method)) {
                result = previews.refreshCatalog(
                        extras == null ? 0L : extras.getLong(FusionConfig.KEY_PREVIEW_READY, 0L),
                        extras == null ? "" : extras.getString(FusionConfig.KEY_PREVIEW_CATALOG, ""));
            } else return super.call(method, arg, extras);
            context.getContentResolver().notifyChange(FusionConfig.controlCenterPreviewUri(), null);
            return result;
        } catch (IOException error) {
            android.util.Log.w("FusionStatusBar", "preview transaction rejected", error);
            return previews.state();
        }
    }

    @Override public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        if (!FusionConfig.controlCenterPreviewUri().getAuthority().equals(uri.getAuthority())
                || !FusionConfig.controlCenterPreviewUri().getPath().equals(uri.getPath())) {
            throw new FileNotFoundException("unsupported_preview_uri");
        }
        enforceCaller(getCallingPackage(), getContext().getPackageName(), false);
        return new ControlCenterPreviewStore(getContext()).open(uri, mode);
    }

    static void enforceCaller(String caller, String ownPackage, boolean ownOnly) {
        // ContentProvider.getCallingPackage verifies Binder UID ownership before returning a name.
        if (caller == null || !(caller.equals(ownPackage)
                || (!ownOnly && SYSTEM_UI_PACKAGE.equals(caller)))) {
            throw new SecurityException("caller_not_authorized");
        }
    }

    static File previewFile(Context context) {
        return new ControlCenterPreviewStore(context).currentFile();
    }

    @Override public String getType(Uri uri) {
        return FusionConfig.controlCenterPreviewUri().getPath().equals(uri.getPath())
                ? "image/png" : "vnd.android.cursor.item/vnd.xtjm.fusionstatusbar.config";
    }

    @Override public Cursor query(Uri uri, String[] projection, String selection,
            String[] selectionArgs, String sortOrder) { return null; }

    @Override public Uri insert(Uri uri, ContentValues values) {
        throw new UnsupportedOperationException("read-only");
    }

    @Override public int delete(Uri uri, String selection, String[] selectionArgs) {
        throw new UnsupportedOperationException("read-only");
    }

    @Override public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        throw new UnsupportedOperationException("read-only");
    }
}
