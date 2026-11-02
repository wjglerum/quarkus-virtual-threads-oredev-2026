package nl.wjglerum._04_structured;

import java.util.concurrent.ThreadFactory;

/**
 * Helpers that make structured cancellation visible in the logs.
 */
final class Cancellation {

    private Cancellation() {
    }

    /**
     * Names the forked virtual threads bartender-1, bartender-2, ... so each one is recognisable in the log.
     */
    static ThreadFactory bartenders() {
        return Thread.ofVirtual().name("bartender-", 1).factory();
    }

    /**
     * True when the failure was caused by the scope interrupting this thread, i.e. the subtask was cancelled.
     */
    static boolean isCancellation(Throwable failure) {
        if (Thread.currentThread().isInterrupted()) {
            return true;
        }
        for (var cause = failure; cause != null; cause = cause.getCause()) {
            if (cause instanceof InterruptedException) {
                return true;
            }
        }
        return false;
    }
}
