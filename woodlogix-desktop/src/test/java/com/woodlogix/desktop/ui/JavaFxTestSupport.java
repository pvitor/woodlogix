package com.woodlogix.desktop.ui;

import javafx.application.Platform;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public final class JavaFxTestSupport {
    private static final Object LOCK = new Object();
    private static final String TOOLKIT_ALREADY_INITIALIZED = "Toolkit already initialized";
    private static boolean initialized;

    private JavaFxTestSupport() {
    }

    public static void start() throws Exception {
        synchronized (LOCK) {
            if (initialized) {
                return;
            }

            CountDownLatch started = new CountDownLatch(1);
            try {
                Platform.startup(() -> {
                    Platform.setImplicitExit(false);
                    started.countDown();
                });
            } catch (IllegalStateException exception) {
                if (!TOOLKIT_ALREADY_INITIALIZED.equals(exception.getMessage())) {
                    throw exception;
                }
                Platform.runLater(() -> {
                    Platform.setImplicitExit(false);
                    started.countDown();
                });
            }

            if (!started.await(20, TimeUnit.SECONDS)) {
                throw new IllegalStateException("JavaFX toolkit did not start in time");
            }
            initialized = true;
        }
    }
}
