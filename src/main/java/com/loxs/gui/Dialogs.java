package com.loxs.gui;

import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.stage.Window;

/**
 * Uygulamanın tüm bilgi/hata/onay pencereleri. JavaFX'in varsayılan Alert
 * penceresi ilk karede beyaz göründüğü için kendi koyu sahnemizi kuruyoruz.
 */
public final class Dialogs {
    private static final String CSS = Dialogs.class.getResource("/loxs.css").toExternalForm();
    private static final Color SCENE_FILL = Color.web("#0a0f1c");
    private static final String SHELL_STYLE =
            "-fx-background-color: #111827;"
                    + "-fx-border-color: #2b3b52;"
                    + "-fx-border-width: 1;"
                    + "-fx-background-radius: 10;"
                    + "-fx-border-radius: 10;";

    private Dialogs() {
    }

    public static void info(Window owner, String title, String message) {
        show(owner, "i", "dialog-kind-info", title, message, null, false, "Tamam");
    }

    public static void error(Window owner, String title, String message) {
        show(owner, "!", "dialog-kind-error", title, message, null, false, "Tamam");
    }

    public static boolean confirm(Window owner, String title, String message) {
        return show(owner, "?", "dialog-kind-question", title, message, null, true, "Evet");
    }

    public static boolean authorize(Window owner) {
        CheckBox approval = new CheckBox("Bu sistemde test iznim olduğunu onaylıyorum.");
        approval.getStyleClass().add("dialog-check");
        approval.setWrapText(true);
        return show(owner, "?", "dialog-kind-question", "Yetki Onayı",
                "Taramaya başlamadan önce yetkinizi onaylayın.", approval, true,
                "Onayla ve devam et");
    }

    private static boolean show(Window owner, String kindText, String kindClass, String title,
                                String message, Node extra, boolean showCancel, String okText) {
        Stage stage = new Stage(StageStyle.UNDECORATED);
        stage.setResizable(false);
        stage.setTitle(title);
        if (owner != null) {
            stage.initOwner(owner);
            stage.initModality(Modality.WINDOW_MODAL);
        }

        Label kind = new Label(kindText);
        kind.getStyleClass().addAll("dialog-kind", kindClass);
        Label heading = new Label(title);
        heading.getStyleClass().add("dialog-title");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        Button close = new Button("✕");
        close.getStyleClass().add("dialog-close");
        close.setFocusTraversable(false);
        HBox header = new HBox(10, kind, heading, spacer, close);
        header.setAlignment(Pos.CENTER_LEFT);
        header.getStyleClass().add("dialog-header");

        Label body = new Label(message);
        body.setWrapText(true);
        body.getStyleClass().add("dialog-message");
        VBox content = new VBox(12, body);
        if (extra != null) {
            content.getChildren().add(extra);
        }
        content.getStyleClass().add("dialog-body");

        Button ok = new Button(okText);
        ok.getStyleClass().add("dialog-button-primary");
        ok.setDefaultButton(true);
        Button cancel = new Button("İptal");
        cancel.getStyleClass().add("dialog-button");
        cancel.setCancelButton(true);
        HBox buttons = new HBox(8);
        buttons.setAlignment(Pos.CENTER_RIGHT);
        if (showCancel) {
            buttons.getChildren().add(cancel);
        }
        buttons.getChildren().add(ok);
        buttons.getStyleClass().add("dialog-buttons");

        if (extra instanceof CheckBox checkBox) {
            ok.disableProperty().bind(checkBox.selectedProperty().not());
        }

        VBox shell = new VBox(header, content, buttons);
        shell.getStyleClass().add("dialog-shell");
        shell.setStyle(SHELL_STYLE);
        shell.setPrefWidth(480);
        shell.setMaxWidth(560);

        Scene scene = new Scene(shell);
        scene.setFill(SCENE_FILL);
        scene.getStylesheets().add(CSS);
        scene.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ESCAPE) {
                stage.close();
            }
        });
        stage.setScene(scene);

        boolean[] accepted = {false};
        ok.setOnAction(event -> {
            accepted[0] = true;
            stage.close();
        });
        cancel.setOnAction(event -> stage.close());
        close.setOnAction(event -> stage.close());

        double[] drag = new double[2];
        header.setOnMousePressed(event -> {
            drag[0] = event.getScreenX() - stage.getX();
            drag[1] = event.getScreenY() - stage.getY();
        });
        header.setOnMouseDragged(event -> {
            stage.setX(event.getScreenX() - drag[0]);
            stage.setY(event.getScreenY() - drag[1]);
        });

        stage.setOnShown(event -> {
            Platform.runLater(() -> {
                if (owner != null) {
                    stage.setX(Math.round(owner.getX() + (owner.getWidth() - stage.getWidth()) / 2));
                    stage.setY(Math.round(owner.getY() + (owner.getHeight() - stage.getHeight()) / 2));
                } else {
                    stage.centerOnScreen();
                }
            });
            ok.requestFocus();
        });

        stage.showAndWait();
        return accepted[0];
    }
}
