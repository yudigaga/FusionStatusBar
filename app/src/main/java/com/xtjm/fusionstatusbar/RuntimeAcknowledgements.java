package com.xtjm.fusionstatusbar;

import android.content.Context;
import android.os.Handler;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.RejectedExecutionException;

/** Latest state per feature, bounded retries, and the same single worker used for provider reads. */
final class RuntimeAcknowledgements {
    interface Sender {
        boolean send(Context context, long revision, String feature, String state, String reason);
    }

    interface Scheduler {
        boolean post(Runnable task, long delayMillis);
        void remove(Runnable task);
    }

    private static final int MAX_RETRIES = 6;
    private final RuntimeWorkQueue work;
    private final Scheduler scheduler;
    private final Sender sender;
    private final Map<String, Report> latest = new HashMap<>();
    private long revision = -1;

    private static final class Report {
        final Context context;
        final long revision;
        final String feature;
        final String state;
        final String reason;
        int retries;
        boolean queued;
        Runnable retry;

        Report(Context context, long revision, String feature, String state, String reason) {
            this.context = context;
            this.revision = revision;
            this.feature = feature;
            this.state = state;
            this.reason = reason;
        }
    }

    RuntimeAcknowledgements(RuntimeWorkQueue work, Scheduler scheduler, Sender sender) {
        this.work = work;
        this.scheduler = scheduler;
        this.sender = sender;
    }

    static Scheduler scheduler(Handler handler) {
        return new Scheduler() {
            @Override public boolean post(Runnable task, long delayMillis) {
                return handler.postDelayed(task, delayMillis);
            }
            @Override public void remove(Runnable task) { handler.removeCallbacks(task); }
        };
    }

    synchronized void advanceRevision(long value) {
        if (value <= revision) return;
        revision = value;
        for (Report report : latest.values()) cancelRetry(report);
        latest.clear();
    }

    void report(Context context, long value, String feature, String state, String reason) {
        if (!knownFeature(feature) || value < 0) return;
        Report next;
        synchronized (this) {
            if (value < revision) return;
            advanceRevision(value);
            Report previous = latest.get(feature);
            if (previous != null && Objects.equals(previous.state, state)
                    && Objects.equals(previous.reason, reason)) return;
            if (previous != null) cancelRetry(previous);
            next = new Report(context, value, feature, state, reason);
            latest.put(feature, next);
        }
        enqueue(next);
    }

    private void enqueue(Report report) {
        synchronized (this) {
            if (!isCurrent(report) || report.queued) return;
            report.queued = true;
        }
        if (!work.execute("ack:" + report.feature, () -> sendLatest(report.feature))) {
            synchronized (this) {
                report.queued = false;
                retry(report);
            }
        }
    }

    private void sendLatest(String feature) {
        Report report;
        synchronized (this) {
            report = latest.get(feature);
            if (report == null || !report.queued) return;
            report.queued = false;
        }
        boolean sent;
        try {
            sent = sender.send(report.context, report.revision, report.feature,
                    report.state, report.reason);
        } catch (Throwable error) {
            sent = false;
        }
        synchronized (this) {
            if (!sent) retry(report);
        }
    }

    private void retry(Report report) {
        if (!isCurrent(report) || report.retry != null || report.retries >= MAX_RETRIES) return;
        long delay = Math.min(30_000L, 1_000L << report.retries++);
        report.retry = () -> {
            synchronized (RuntimeAcknowledgements.this) {
                report.retry = null;
                if (!isCurrent(report)) return;
            }
            enqueue(report);
        };
        boolean posted;
        try {
            posted = scheduler.post(report.retry, delay);
        } catch (RejectedExecutionException rejected) {
            posted = false;
        }
        if (!posted) {
            report.retry = null;
            // No timer was accepted. A later report must remain eligible for another attempt.
            latest.remove(report.feature);
        }
    }

    private boolean isCurrent(Report report) {
        return report.revision == revision && latest.get(report.feature) == report;
    }

    private void cancelRetry(Report report) {
        if (report.retry != null) scheduler.remove(report.retry);
        report.retry = null;
    }

    private static boolean knownFeature(String feature) {
        return FusionActivationStatus.FEATURE_STATUS_BAR.equals(feature)
                || FusionActivationStatus.FEATURE_CONTROL_CENTER.equals(feature)
                || FusionActivationStatus.FEATURE_NOTIFICATION_CLOCK.equals(feature);
    }
}
