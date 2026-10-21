package com.loxs;

import javafx.application.Application;

import java.util.logging.LogManager;

public final class LoxsFxLauncher {
    private LoxsFxLauncher() {
    }

    public static void main(String[] args) {
        // JavaFX/JDK bilgi uyarıları terminali kirletmesin.
        LogManager.getLogManager().reset();
        Application.launch(LoxsFxApp.class, args);
    }
}
