package com.drnas.console;

/** Install before any activity/X11 thread can fail. */
public final class DrnasApplication extends android.app.Application {
    @Override public void onCreate() {
        super.onCreate();
        AndroidCrashDiagnostics.install(this);
    }
}
