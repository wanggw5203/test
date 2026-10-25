package io.github.aitestflow.ui;

import javax.swing.SwingUtilities;

/** All UI notifications must be dispatched independently from background pipeline results. */
public final class UiDispatch {
    private UiDispatch() { }

    public static void run(Runnable callback) {
        if (SwingUtilities.isEventDispatchThread()) callback.run();
        else SwingUtilities.invokeLater(callback);
    }
}
