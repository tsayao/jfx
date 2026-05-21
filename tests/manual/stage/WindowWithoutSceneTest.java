/*
 * Copyright (c) 2026 Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

// Manual test for a Stage shown without a Scene.
//
// TEST INSTRUCTIONS:
//   1. Use the "Style" combo to pick a StageStyle.
//   2. Click "Show Window" to open a Stage with NO Scene attached.
//   3. Verify that the window appears without crashing — the window
//      area should be empty (or show the native background / GTK CSS
//      background for the chosen style).
//   4. Optionally click "Set Scene" to attach a Scene to the running
//      window and confirm it renders correctly afterwards.
//   5. Click "Close Window" to dismiss the test window.
//
// PASS criteria:
//   • Opening a Stage without a Scene does not throw or crash.
//   • The window can be closed, resized and moved normally.
//   • Attaching a Scene afterwards shows its content correctly.

import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.stage.WindowEvent;

public class WindowWithoutSceneTest extends Application {

    private Stage testWindow;

    private final Label statusLabel = new Label("No window open.");
    private final Button btnShow    = new Button("Show Window");
    private final Button btnSetScene = new Button("Set Scene");
    private final Button btnClose   = new Button("Close Window");
    private final ComboBox<StageStyle> styleCombo = new ComboBox<>();

    @Override
    public void start(Stage primaryStage) {
        styleCombo.getItems().addAll(StageStyle.values());
        styleCombo.setValue(StageStyle.DECORATED);

        btnSetScene.setDisable(true);
        btnClose.setDisable(true);

        btnShow.setOnAction(e -> openWindow());
        btnSetScene.setOnAction(e -> attachScene());
        btnClose.setOnAction(e -> closeWindow());

        Label instructions = new Label("""
                Instructions:
                1. Choose a StageStyle from the combo box.
                2. Click "Show Window" — a Stage with NO Scene will open.
                3. Verify no crash occurs and the window appears.
                4. Optionally click "Set Scene" to attach a Scene.
                5. Click "Close Window" to dismiss it.

                PASS: no exception; window opens, can be moved/resized,
                      and attaching a Scene renders content correctly.""");
        instructions.setWrapText(true);
        instructions.setMaxWidth(450);

        HBox controls = new HBox(10, new Label("Style:"), styleCombo,
                btnShow, btnSetScene, btnClose);
        controls.setAlignment(Pos.CENTER_LEFT);

        VBox root = new VBox(14, instructions, controls, statusLabel);
        root.setPadding(new Insets(20));
        root.setAlignment(Pos.TOP_LEFT);

        primaryStage.setTitle("WindowWithoutSceneTest");
        primaryStage.setScene(new Scene(root, 520, 280));
        primaryStage.show();
    }

    private void openWindow() {
        if (testWindow != null) {
            testWindow.close();
        }

        StageStyle style = styleCombo.getValue();
        testWindow = new Stage();
        testWindow.initStyle(style);
        testWindow.setTitle("No-Scene Window  [" + style + "]");
        testWindow.setWidth(400);
        testWindow.setHeight(300);

        testWindow.addEventHandler(WindowEvent.WINDOW_SHOWN, e ->
                setStatus("Window shown (no Scene) — style=" + style, false));
        testWindow.addEventHandler(WindowEvent.WINDOW_HIDDEN, e -> {
            testWindow = null;
            setStatus("Window closed.", false);
            btnShow.setDisable(false);
            btnSetScene.setDisable(true);
            btnClose.setDisable(true);
        });

        // Show WITHOUT setting a Scene
        testWindow.show();

        btnShow.setDisable(true);
        btnSetScene.setDisable(false);
        btnClose.setDisable(false);
        setStatus("Window open with no Scene — style=" + style, false);
    }

    private void attachScene() {
        if (testWindow == null) return;

        Label label = new Label("Scene attached after show()");
        label.setStyle("-fx-font-size: 18px; -fx-font-weight: bold;");
        StackPane pane = new StackPane(label);
        pane.setStyle("-fx-background-color: #e0f0ff;");

        testWindow.setScene(new Scene(pane, testWindow.getWidth(), testWindow.getHeight()));
        btnSetScene.setDisable(true);
        setStatus("Scene attached successfully.", false);
    }

    private void closeWindow() {
        if (testWindow != null) {
            testWindow.close();
        }
    }

    private void setStatus(String msg, boolean error) {
        statusLabel.setTextFill(error ? Color.RED : Color.DARKGREEN);
        statusLabel.setText(msg);
    }

    public static void main(String[] args) {
        launch(WindowWithoutSceneTest.class, args);
    }
}

