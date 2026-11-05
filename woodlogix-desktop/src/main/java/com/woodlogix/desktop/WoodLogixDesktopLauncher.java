package com.woodlogix.desktop;

import javafx.application.Application;

public final class WoodLogixDesktopLauncher {
    private WoodLogixDesktopLauncher() {
    }

    public static void main(String[] args) {
        Application.launch(AppLauncher.class, args);
    }
}
