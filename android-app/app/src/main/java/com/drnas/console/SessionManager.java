package com.drnas.console;

import android.os.Handler;
import android.os.Looper;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

/**
 * Owns exactly one Android-native test session. No Wine binaries are executed.
 * The only command here is a static harmless /system/bin/sh diagnostic.
 */
public final class SessionManager {
    public enum State { IDLE, STARTING, RUNNING, STOPPING, FAILED }
    public interface Listener {
        void onStateChanged(State state);
        void onLog(String message);
    }

    private final Object lock = new Object();
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Listener listener;
    private volatile State state = State.IDLE;
    private Process process;
    private boolean stopRequested;
    private long generation = 0;

    public SessionManager(Listener listener) { this.listener = listener; }
    public State getState() { return state; }
    private void publishState(State newState) {
        state = newState;
        main.post(() -> listener.onStateChanged(newState));
    }
    private void log(String line) {
        main.post(() -> listener.onLog(line));
    }

    public boolean startAndroidSmokeTest() {
        final long ticket;
        synchronized (lock) {
            if (state != State.IDLE && state != State.FAILED) return false;
            stopRequested = false;
            ticket = ++generation;
            publishState(State.STARTING);
        }
        new Thread(() -> runSmoke(ticket), "drnas-smoke-session").start();
        return true;
    }

    private void runSmoke(long ticket) {
        Process local = null;
        boolean requestedStop;
        try {
            // Using an OS executable is intentional: API 29+ forbids executing
            // arbitrary downloaded ELF from the app's writable private storage.
            // This is *not* the Wine/Proton launcher.
            String script = "echo 'DRNAS_NATIVE_SMOKE_OK'; " +
                    "echo 'This is the Android system shell, NOT Proton'; " +
                    "i=1; while [ \"$i\" -le 30 ]; do " +
                    "echo \"heartbeat=$i\"; i=$((i+1)); sleep 1; done";
            ProcessBuilder pb = new ProcessBuilder("/system/bin/sh", "-c", script);
            pb.redirectErrorStream(true);
            local = pb.start();
            synchronized (lock) {
                if (generation != ticket || stopRequested) {
                    local.destroy();
                } else {
                    process = local;
                    publishState(State.RUNNING);
                }
            }
            log("[session] Android test process created");
            try (BufferedReader in = new BufferedReader(
                    new InputStreamReader(local.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = in.readLine()) != null) {
                    log("[native] " + line);
                }
            } catch (IOException readError) {
                // Android's Process.destroy() closes stdout while readLine()
                // is waiting. An InterruptedIOException here is an expected
                // consequence of an intentional Stop, not a failed session.
                final boolean intentionalStop;
                synchronized (lock) {
                    intentionalStop = stopRequested || generation != ticket;
                }
                if (!intentionalStop) throw readError;
                log("[session] output stream closed after Stop (expected)");
            }
            // Always reap the process, including when Stop closed stdout.
            int exit = local.waitFor();
            synchronized (lock) { requestedStop = stopRequested; }
            log("[session] exit=" + exit + (requestedStop ? " (stopped by user)" : ""));
            synchronized (lock) {
                if (generation == ticket) {
                    process = null;
                    publishState(exit == 0 || requestedStop ? State.IDLE : State.FAILED);
                }
            }
        } catch (Exception ex) {
            final boolean intentionalStop;
            synchronized (lock) { intentionalStop = stopRequested; }
            if (ex instanceof InterruptedException) Thread.currentThread().interrupt();
            if (intentionalStop) {
                log("[session] stopped while cleaning up (" +
                        ex.getClass().getSimpleName() + ")");
            } else {
                log("[error] " + ex.getClass().getSimpleName() + ": " + ex.getMessage());
            }
            synchronized (lock) {
                if (generation == ticket) {
                    process = null;
                    publishState(intentionalStop ? State.IDLE : State.FAILED);
                }
            }
        } finally {
            if (local != null && local.isAlive()) local.destroyForcibly();
        }
    }

    public void stop() {
        final Process local;
        synchronized (lock) {
            if (state == State.IDLE || state == State.FAILED) return;
            stopRequested = true;
            local = process;
            publishState(State.STOPPING);
        }
        if (local == null) return; // worker will notice stopRequested
        new Thread(() -> {
            local.destroy();
            try {
                if (!local.waitFor(2, TimeUnit.SECONDS)) local.destroyForcibly();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                local.destroyForcibly();
            }
        }, "drnas-session-stopper").start();
    }
}
