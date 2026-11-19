package com.xtjm.fusionstatusbar;

import android.app.Activity;
import android.view.View;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class PublicWindowBlurObserverTest {
    @Test public void attachedDecorReadsInitialStateAndRegistersOnlyOnce() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().visible().get();
        View decor = activity.getWindow().getDecorView();
        Backend backend = new Backend();
        backend.enabled = true;
        List<PublicWindowBlurObserver.Availability> changes = new ArrayList<>();
        PublicWindowBlurObserver observer = new PublicWindowBlurObserver(decor, backend, changes::add);
        assertEquals(List.of(PublicWindowBlurObserver.Availability.TRUE), changes);
        observer.onViewAttachedToWindow(decor);
        assertEquals(1, backend.added.size());
        observer.close();
        observer.close();
        assertEquals(1, backend.removed.size());
    }

    @Test public void inlineRegistrationCallbackOverridesInitialRead() {
        View decor = detachedView();
        Backend backend = new Backend();
        backend.inline = true;
        List<PublicWindowBlurObserver.Availability> changes = new ArrayList<>();
        PublicWindowBlurObserver observer = new PublicWindowBlurObserver(decor, backend, changes::add);
        assertTrue(backend.added.isEmpty());
        observer.onViewAttachedToWindow(decor);
        assertEquals(List.of(PublicWindowBlurObserver.Availability.FALSE,
                PublicWindowBlurObserver.Availability.TRUE), changes);
        observer.close();
    }

    @Test public void delayedCallbacksChangeStateWithoutReregistering() {
        View decor = detachedView();
        Backend backend = new Backend();
        List<PublicWindowBlurObserver.Availability> changes = new ArrayList<>();
        PublicWindowBlurObserver observer = new PublicWindowBlurObserver(decor, backend, changes::add);
        observer.onViewAttachedToWindow(decor);
        Consumer<Boolean> callback = backend.added.get(0);
        callback.accept(true);
        callback.accept(false);
        callback.accept(true);
        assertEquals(List.of(PublicWindowBlurObserver.Availability.FALSE,
                PublicWindowBlurObserver.Availability.TRUE, PublicWindowBlurObserver.Availability.FALSE,
                PublicWindowBlurObserver.Availability.TRUE), changes);
        assertEquals(1, backend.added.size());
        observer.close();
    }

    @Test public void detachedOldGenerationCannotOverrideReattachedStateEvenWhenRemovalThrows() {
        View decor = detachedView();
        Backend backend = new Backend();
        backend.enabled = true;
        List<PublicWindowBlurObserver.Availability> changes = new ArrayList<>();
        PublicWindowBlurObserver observer = new PublicWindowBlurObserver(decor, backend, changes::add);
        observer.onViewAttachedToWindow(decor);
        Consumer<Boolean> old = backend.added.get(0);
        backend.failRemove = true;
        observer.onViewDetachedFromWindow(decor);
        assertEquals(PublicWindowBlurObserver.Availability.UNKNOWN, changes.get(changes.size() - 1));
        backend.enabled = false;
        observer.onViewAttachedToWindow(decor);
        assertNotSame(old, backend.added.get(1));
        int delivered = changes.size();
        old.accept(true);
        assertEquals(delivered, changes.size());
        backend.added.get(1).accept(true);
        assertEquals(PublicWindowBlurObserver.Availability.TRUE, changes.get(changes.size() - 1));
        observer.close();
        delivered = changes.size();
        backend.added.get(1).accept(false);
        observer.onViewAttachedToWindow(decor);
        assertEquals(delivered, changes.size());
        assertEquals(2, backend.added.size());
    }

    @Test public void partialRegistrationFailureIsUnknownAndRecoversAtNextAttach() {
        View decor = detachedView();
        Backend backend = new Backend();
        backend.enabled = true;
        backend.failAdd = true;
        backend.failRemove = true;
        List<PublicWindowBlurObserver.Availability> changes = new ArrayList<>();
        PublicWindowBlurObserver observer = new PublicWindowBlurObserver(decor, backend, changes::add);
        observer.onViewAttachedToWindow(decor);
        assertEquals(PublicWindowBlurObserver.Availability.UNKNOWN, changes.get(changes.size() - 1));
        int delivered = changes.size();
        backend.added.get(0).accept(false);
        assertEquals(delivered, changes.size());
        observer.onViewDetachedFromWindow(decor);
        backend.failAdd = false;
        backend.failRemove = false;
        observer.onViewAttachedToWindow(decor);
        assertEquals(PublicWindowBlurObserver.Availability.TRUE, changes.get(changes.size() - 1));
        assertEquals(2, backend.added.size());
        observer.close();
    }

    @Test public void failedInitialReadDoesNotLeaveACallbackAndCanRecover() {
        View decor = detachedView();
        Backend backend = new Backend();
        backend.failRead = true;
        List<PublicWindowBlurObserver.Availability> changes = new ArrayList<>();
        PublicWindowBlurObserver observer = new PublicWindowBlurObserver(decor, backend, changes::add);
        observer.onViewAttachedToWindow(decor);
        assertTrue(backend.added.isEmpty());
        observer.onViewDetachedFromWindow(decor);
        backend.failRead = false;
        observer.onViewAttachedToWindow(decor);
        assertEquals(List.of(PublicWindowBlurObserver.Availability.FALSE), changes);
        observer.close();
    }

    @Test public void separateDecorObserversDoNotShareRegistrationOrRelease() {
        View first = detachedView(), second = detachedView();
        Backend backend = new Backend();
        List<PublicWindowBlurObserver.Availability> firstChanges = new ArrayList<>();
        List<PublicWindowBlurObserver.Availability> secondChanges = new ArrayList<>();
        PublicWindowBlurObserver a = new PublicWindowBlurObserver(first, backend, firstChanges::add);
        PublicWindowBlurObserver b = new PublicWindowBlurObserver(second, backend, secondChanges::add);
        a.onViewAttachedToWindow(first);
        b.onViewAttachedToWindow(second);
        a.close();
        backend.added.get(0).accept(true);
        backend.added.get(1).accept(true);
        assertEquals(List.of(PublicWindowBlurObserver.Availability.FALSE), firstChanges);
        assertEquals(List.of(PublicWindowBlurObserver.Availability.FALSE,
                PublicWindowBlurObserver.Availability.TRUE), secondChanges);
        b.close();
    }

    @Test public void delayedListenerFailureFallsBackToUnknownAndNextCallbackCanRecover() {
        View decor = detachedView();
        Backend backend = new Backend();
        List<PublicWindowBlurObserver.Availability> changes = new ArrayList<>();
        boolean[] failOnce = {true};
        PublicWindowBlurObserver observer = new PublicWindowBlurObserver(decor, backend, value -> {
            changes.add(value);
            if (value == PublicWindowBlurObserver.Availability.TRUE && failOnce[0]) {
                failOnce[0] = false;
                throw new IllegalStateException("host callback");
            }
        });
        observer.onViewAttachedToWindow(decor);
        Consumer<Boolean> callback = backend.added.get(0);
        callback.accept(true);
        callback.accept(true);
        assertEquals(List.of(PublicWindowBlurObserver.Availability.FALSE,
                PublicWindowBlurObserver.Availability.TRUE, PublicWindowBlurObserver.Availability.UNKNOWN,
                PublicWindowBlurObserver.Availability.TRUE), changes);
        assertEquals(1, backend.added.size());
        observer.close();
    }

    @Test public void linkageAndUnknownFallbackFailuresAreContainedWithoutDisablingRecovery() {
        View decor = detachedView();
        Backend backend = new Backend();
        List<PublicWindowBlurObserver.Availability> changes = new ArrayList<>();
        PublicWindowBlurObserver observer = new PublicWindowBlurObserver(decor, backend, value -> {
            changes.add(value);
            if (value == PublicWindowBlurObserver.Availability.TRUE)
                throw new NoClassDefFoundError("host linkage");
            if (value == PublicWindowBlurObserver.Availability.UNKNOWN)
                throw new IllegalStateException("fallback also unavailable");
        });
        observer.onViewAttachedToWindow(decor);
        backend.added.get(0).accept(true);
        backend.added.get(0).accept(false);
        assertEquals(List.of(PublicWindowBlurObserver.Availability.FALSE,
                PublicWindowBlurObserver.Availability.TRUE, PublicWindowBlurObserver.Availability.UNKNOWN,
                PublicWindowBlurObserver.Availability.FALSE), changes);
        observer.close();
    }

    @Test public void listenerErrorsOutsideTheRecoveryBoundaryStillPropagate() {
        View decor = detachedView();
        Backend backend = new Backend();
        PublicWindowBlurObserver observer = new PublicWindowBlurObserver(decor, backend, value -> {
            if (value == PublicWindowBlurObserver.Availability.TRUE) throw new AssertionError("fatal boundary");
        });
        observer.onViewAttachedToWindow(decor);
        assertThrows(AssertionError.class, () -> backend.added.get(0).accept(true));
        observer.close();
    }

    @Test public void oldFailingPublicationCannotOverrideAReattachedRegistration() {
        View decor = detachedView();
        Backend backend = new Backend();
        List<PublicWindowBlurObserver.Availability> changes = new ArrayList<>();
        PublicWindowBlurObserver[] holder = new PublicWindowBlurObserver[1];
        boolean[] reattachOnce = {true};
        holder[0] = new PublicWindowBlurObserver(decor, backend, value -> {
            changes.add(value);
            if (value == PublicWindowBlurObserver.Availability.TRUE && reattachOnce[0]) {
                reattachOnce[0] = false;
                holder[0].onViewDetachedFromWindow(decor);
                backend.enabled = true;
                holder[0].onViewAttachedToWindow(decor);
                throw new IllegalStateException("old registration listener");
            }
        });
        holder[0].onViewAttachedToWindow(decor);
        Consumer<Boolean> old = backend.added.get(0);
        old.accept(true);
        assertEquals(List.of(PublicWindowBlurObserver.Availability.FALSE,
                PublicWindowBlurObserver.Availability.TRUE, PublicWindowBlurObserver.Availability.UNKNOWN,
                PublicWindowBlurObserver.Availability.TRUE), changes);
        int delivered = changes.size();
        old.accept(false);
        assertEquals(delivered, changes.size());
        Consumer<Boolean> current = backend.added.get(1);
        current.accept(false);
        current.accept(true);
        assertEquals(List.of(PublicWindowBlurObserver.Availability.FALSE,
                PublicWindowBlurObserver.Availability.TRUE, PublicWindowBlurObserver.Availability.UNKNOWN,
                PublicWindowBlurObserver.Availability.TRUE, PublicWindowBlurObserver.Availability.FALSE,
                PublicWindowBlurObserver.Availability.TRUE), changes);
        holder[0].close();
    }

    @Test public void oldFailingPublicationCannotOverrideNewerValuesInTheSameRegistration() {
        View decor = detachedView();
        Backend backend = new Backend();
        List<PublicWindowBlurObserver.Availability> changes = new ArrayList<>();
        boolean[] nestOnce = {true};
        PublicWindowBlurObserver observer = new PublicWindowBlurObserver(decor, backend, value -> {
            changes.add(value);
            if (value == PublicWindowBlurObserver.Availability.TRUE && nestOnce[0]) {
                nestOnce[0] = false;
                Consumer<Boolean> callback = backend.added.get(0);
                callback.accept(false);
                callback.accept(true);
                throw new IllegalStateException("outer publication listener");
            }
        });
        observer.onViewAttachedToWindow(decor);
        Consumer<Boolean> callback = backend.added.get(0);
        callback.accept(true);
        assertEquals(List.of(PublicWindowBlurObserver.Availability.FALSE,
                PublicWindowBlurObserver.Availability.TRUE, PublicWindowBlurObserver.Availability.FALSE,
                PublicWindowBlurObserver.Availability.TRUE), changes);
        assertEquals(1, backend.added.size());
        callback.accept(false);
        callback.accept(true);
        assertEquals(6, changes.size());
        assertEquals(PublicWindowBlurObserver.Availability.TRUE, changes.get(5));
        observer.close();
    }

    @Test public void closingInsideAFailingListenerPreventsFallbackAndAnyLaterWrites() {
        View decor = detachedView();
        Backend backend = new Backend();
        List<PublicWindowBlurObserver.Availability> changes = new ArrayList<>();
        PublicWindowBlurObserver[] holder = new PublicWindowBlurObserver[1];
        holder[0] = new PublicWindowBlurObserver(decor, backend, value -> {
            changes.add(value);
            if (value == PublicWindowBlurObserver.Availability.TRUE) {
                holder[0].close();
                throw new IllegalStateException("closed listener");
            }
        });
        holder[0].onViewAttachedToWindow(decor);
        Consumer<Boolean> old = backend.added.get(0);
        old.accept(true);
        old.accept(false);
        holder[0].onViewAttachedToWindow(decor);
        assertEquals(List.of(PublicWindowBlurObserver.Availability.FALSE,
                PublicWindowBlurObserver.Availability.TRUE), changes);
        assertEquals(1, backend.added.size());
        assertEquals(1, backend.removed.size());
    }

    private static View detachedView() {
        return new View(Robolectric.buildActivity(Activity.class).setup().get());
    }

    private static final class Backend implements PublicWindowBlurObserver.Backend {
        boolean enabled, inline, failRead, failAdd, failRemove;
        final List<Consumer<Boolean>> added = new ArrayList<>(), removed = new ArrayList<>();
        @Override public boolean isEnabled() {
            if (failRead) throw new IllegalStateException("read");
            return enabled;
        }
        @Override public void add(Consumer<Boolean> callback) {
            added.add(callback);
            if (inline) callback.accept(true);
            if (failAdd) throw new IllegalStateException("add after storing callback");
        }
        @Override public void remove(Consumer<Boolean> callback) {
            removed.add(callback);
            if (failRemove) throw new IllegalStateException("remove");
        }
    }
}
