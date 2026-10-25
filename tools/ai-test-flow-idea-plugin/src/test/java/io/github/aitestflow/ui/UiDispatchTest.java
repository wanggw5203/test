package io.github.aitestflow.ui;

import org.junit.jupiter.api.Test;
import javax.swing.SwingUtilities;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;

class UiDispatchTest {
    @Test void dispatchesBackgroundCompletionOnEdt() throws Exception {
        CompletableFuture<Boolean> notificationOnEdt = new CompletableFuture<>();
        CompletableFuture.runAsync(() -> UiDispatch.run(() ->
                notificationOnEdt.complete(SwingUtilities.isEventDispatchThread()))).get(5, TimeUnit.SECONDS);
        assertTrue(notificationOnEdt.get(5, TimeUnit.SECONDS));
    }

    @Test void existingEdtActionsRemainSynchronous() throws Exception {
        CompletableFuture<Boolean> done = new CompletableFuture<>();
        SwingUtilities.invokeAndWait(() -> {
            UiDispatch.run(() -> done.complete(SwingUtilities.isEventDispatchThread()));
            assertTrue(done.isDone());
        });
        assertTrue(done.get());
    }
}
