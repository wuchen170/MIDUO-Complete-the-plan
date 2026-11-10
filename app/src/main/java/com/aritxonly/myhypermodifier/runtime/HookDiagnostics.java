package com.aritxonly.myhypermodifier;

import android.content.Context;
import android.util.Log;

/**
 * Minimal hook diagnostics for the standalone module: failures are written to logcat instead of
 * being broadcast to the companion app, so the runtime has no IPC or receiver dependency.
 */
final class HookDiagnostics {
    private static final String TAG = "SoftGlassNav";

    private HookDiagnostics() {
    }

    static void attach(Context context) {
        // No application-level bookkeeping is required for the ported features.
    }

    static void ready(String packageName) {
        Log.i(TAG, "Hooks ready: " + packageName);
    }

    static void available(String packageName, String feature) {
        Log.i(TAG, feature + " available in " + packageName);
    }

    static void observed(String packageName, String feature, String detail) {
        Log.i(TAG, feature + " (" + packageName + "): " + detail);
    }

    static void unavailable(String packageName, String feature, String detail) {
        Log.w(TAG, feature + " unavailable in " + packageName + ": " + detail);
    }

    static void failure(String packageName, String feature, Throwable error) {
        Log.w(TAG, feature + " failed in " + packageName, error);
    }
}
