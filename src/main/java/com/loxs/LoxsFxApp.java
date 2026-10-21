package com.loxs;

import com.loxs.gui.LoxsFxView;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.scene.paint.Color;
import javafx.stage.Stage;

import java.io.InputStream;

public final class LoxsFxApp extends Application {
    @Override
    public void start(Stage stage) {
        LoxsFxView view = new LoxsFxView(stage);
        Scene scene = new Scene(view.root(), 1200, 760);
        scene.setFill(Color.web("#0a0f1c"));
        scene.getStylesheets().add(LoxsFxApp.class.getResource("/loxs.css").toExternalForm());
        stage.setScene(scene);
        stage.setTitle(AppInfo.TITLE);
        stage.setMinWidth(1040);
        stage.setMinHeight(640);
        try (InputStream icon = LoxsFxApp.class.getResourceAsStream("/loxs-icon.png")) {
            if (icon != null) {
                stage.getIcons().add(new Image(icon));
            }
        } catch (Exception ignored) {
            // Simge yüklenemezse devam et.
        }
        com.loxs.gui.WindowsDarkTitleBar.apply(stage);
        stage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
