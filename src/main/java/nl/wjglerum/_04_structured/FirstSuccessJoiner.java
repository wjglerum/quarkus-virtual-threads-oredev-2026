package nl.wjglerum._04_structured;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.StructuredTaskScope.CancelledByTimeoutException;
import java.util.concurrent.StructuredTaskScope.Joiner;
import java.util.concurrent.StructuredTaskScope.Subtask;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Custom Joiner: the first subtask to succeed wins and the scope cancels the rest.
 * Same behaviour as Joiner.anySuccessfulOrThrow(), written out to show the API.
 */
@SuppressWarnings("preview")
class FirstSuccessJoiner<T> implements Joiner<T, T, ExecutionException> {

    private final AtomicReference<T> winner = new AtomicReference<>();
    private final AtomicReference<Throwable> failure = new AtomicReference<>();

    @Override
    public boolean onComplete(Subtask<T> subtask) {
        return switch (subtask.state()) {
            case SUCCESS -> {
                winner.compareAndSet(null, subtask.get());
                yield true; // cancel the scope: interrupts the siblings
            }
            case FAILED -> {
                failure.compareAndSet(null, subtask.exception());
                yield false; // keep waiting for another subtask to succeed
            }
            case UNAVAILABLE -> false;
        };
    }

    @Override
    public T result() throws ExecutionException {
        var result = winner.get();
        if (result == null) {
            throw new ExecutionException(failure.get());
        }
        return result;
    }

    @Override
    public T timeout() throws ExecutionException {
        throw new ExecutionException(new CancelledByTimeoutException());
    }
}
