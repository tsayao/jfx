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

import javafx.application.Application;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.collections.FXCollections;

/**
 * Manual test for FullScreenTest.fullScreenRestoreCycle
 *
 * WHAT THIS TEST VERIFIES:
 * When a Stage enters and exits fullscreen twice (cycle), the window must
 * restore to its original size (200 x 250) and position (x=100, y=150)
 * each time it exits fullscreen.
 *
 * HOW TO RUN THE TEST:
 * 1. Select a StageStyle from the combo box.
 * 2. Click "Create Test Stage" — a new window (200x250 at x=100, y=150) appears.
 * 3. Click "Enter FullScreen" — the window should fill the entire screen.
 * 4. Click "Exit FullScreen" — the window must restore to 200x250 at (100,150).
 *    Check the "After Restore 1" status row: all values must show PASS (green).
 * 5. Click "Enter FullScreen" again — the window should fill the screen again.
 * 6. Click "Exit FullScreen" again — the window must once more restore to 200x250 at (100,150).
 *    Check the "After Restore 2" status row: all values must show PASS (green).
 *
 * PASS CRITERIA:
 * - After EACH exit from fullscreen: width=200, height=250, x=100, y=150
 *   (within a tolerance of ±2px for size, ±10px for position).
 * - Both restore cycles must pass independently.
 *
 * FAIL CRITERIA:
 * - Any dimension or position value shows FAIL (red) after exiting fullscreen.
 * - The window does not visually restore to the expected size/position.
 */
public class FullScreenRestoreCycleTest extends Application {

    private static final int EXPECTED_X = 100;
    private static final int EXPECTED_Y = 150;
    private static final int EXPECTED_W = 200;
    private static final int EXPECTED_H = 250;
    private static final double SIZE_DELTA = 2.0;
    private static final double POS_DELTA = 10.0;

    // ---- labels that display measured values ----
    private final Label lblRestoreW1  = makeValueLabel();
    private final Label lblRestoreH1  = makeValueLabel();
    private final Label lblRestoreX1  = makeValueLabel();
    private final Label lblRestoreY1  = makeValueLabel();
    private final Label lblPassFail1  = makePassFailLabel();

    private final Label lblRestoreW2  = makeValueLabel();
    private final Label lblRestoreH2  = makeValueLabel();
    private final Label lblRestoreX2  = makeValueLabel();
    private final Label lblRestoreY2  = makeValueLabel();
    private final Label lblPassFail2  = makePassFailLabel();

    private final Label lblCurrentW   = makeValueLabel();
    private final Label lblCurrentH   = makeValueLabel();
    private final Label lblCurrentX   = makeValueLabel();
    private final Label lblCurrentY   = makeValueLabel();
    private final Label lblFullScreen = makeValueLabel();

    // ---- controls ----
    private final ComboBox<StageStyle> cbStyle =
            new ComboBox<>(FXCollections.observableArrayList(
                    StageStyle.DECORATED,
                    StageStyle.UNDECORATED,
                    StageStyle.EXTENDED,
                    StageStyle.TRANSPARENT));

    private final Button btnCreate      = new Button("1. Create Test Stage");
    private final Button btnFullOn1     = new Button("2. Enter FullScreen");
    private final Button btnFullOff1    = new Button("3. Exit FullScreen  →  check Restore 1");
    private final Button btnFullOn2     = new Button("4. Enter FullScreen again");
    private final Button btnFullOff2    = new Button("5. Exit FullScreen  →  check Restore 2");
    private final Button btnReset       = new Button("Reset");
    private final Button btnAutoRun     = new Button("▶ Auto Run");
    private final Spinner<Integer> spinnerDelay = new Spinner<>(
            new SpinnerValueFactory.IntegerSpinnerValueFactory(200, 5000, 800, 100));

    private Stage testStage;
    private int cycleStep = 0; // 0=not started, 1=after create, 2=after fullOn1, 3=after fullOff1, ...
    private volatile boolean autoRunCancelled = false;

    @Override
    public void start(Stage primaryStage) {
        cbStyle.getSelectionModel().select(StageStyle.DECORATED);
        setButtonsEnabled(false);

        btnCreate.setOnAction(e -> createTestStage());
        btnFullOn1.setOnAction(e -> {
            testStage.setFullScreen(true);
            setButtonsEnabled(false);
            btnFullOff1.setDisable(false);
            cycleStep = 2;
        });
        btnFullOff1.setOnAction(e -> {
            testStage.setFullScreen(false);
            // Delay measurement to let the window manager settle
            new Thread(() -> {
                try { Thread.sleep(600); } catch (InterruptedException ex) { Thread.currentThread().interrupt(); }
                Platform.runLater(() -> {
                    captureRestore1();
                    setButtonsEnabled(false);
                    btnFullOn2.setDisable(false);
                    cycleStep = 3;
                });
            }).start();
        });
        btnFullOn2.setOnAction(e -> {
            testStage.setFullScreen(true);
            setButtonsEnabled(false);
            btnFullOff2.setDisable(false);
            cycleStep = 4;
        });
        btnFullOff2.setOnAction(e -> {
            testStage.setFullScreen(false);
            new Thread(() -> {
                try { Thread.sleep(600); } catch (InterruptedException ex) { Thread.currentThread().interrupt(); }
                Platform.runLater(() -> {
                    captureRestore2();
                    setButtonsEnabled(false);
                    btnReset.setDisable(false);
                    cycleStep = 5;
                });
            }).start();
        });
        btnReset.setOnAction(e -> reset());
        btnAutoRun.setOnAction(e -> startAutoRun());

        // ---- layout ----
        Label title = new Label("FullScreenTest.fullScreenRestoreCycle — Manual Test");
        title.setFont(Font.font(title.getFont().getFamily(), FontWeight.BOLD, 13));

        Label instructions = new Label(
                "Follow buttons 1 → 5 in order.\n" +
                "After each 'Exit FullScreen' step, verify the Restore row reports PASS.\n" +
                "Expected size: 200 × 250 px  |  Expected position: x=100, y=150  (±tolerance)");
        instructions.setWrapText(true);
        instructions.setStyle("-fx-text-fill: #444;");

        // Current live readings
        GridPane liveGrid = buildSectionGrid("Live Stage State",
                new String[]{"Width", "Height", "X", "Y", "FullScreen"},
                new Label[]{lblCurrentW, lblCurrentH, lblCurrentX, lblCurrentY, lblFullScreen});

        // Restore 1 readings
        GridPane restore1Grid = buildSectionGrid("After Restore 1 (step 3)",
                new String[]{"Width", "Height", "X", "Y", "Result"},
                new Label[]{lblRestoreW1, lblRestoreH1, lblRestoreX1, lblRestoreY1, lblPassFail1});

        // Restore 2 readings
        GridPane restore2Grid = buildSectionGrid("After Restore 2 (step 5)",
                new String[]{"Width", "Height", "X", "Y", "Result"},
                new Label[]{lblRestoreW2, lblRestoreH2, lblRestoreX2, lblRestoreY2, lblPassFail2});

        HBox styleRow = new HBox(10, new Label("Stage Style:"), cbStyle);
        styleRow.setAlignment(Pos.CENTER_LEFT);

        spinnerDelay.setEditable(true);
        spinnerDelay.setMaxWidth(90);
        spinnerDelay.setMinWidth(90);
        HBox autoRow = new HBox(8,
                new Label("Delay between steps (ms):"), spinnerDelay, btnAutoRun);
        autoRow.setAlignment(Pos.CENTER_LEFT);

        HBox buttonRow1 = new HBox(8, btnCreate, btnFullOn1, btnFullOff1);
        buttonRow1.setAlignment(Pos.CENTER_LEFT);
        HBox buttonRow2 = new HBox(8, btnFullOn2, btnFullOff2, btnReset);
        buttonRow2.setAlignment(Pos.CENTER_LEFT);

        VBox root = new VBox(10,
                title,
                new Separator(),
                instructions,
                new Separator(),
                styleRow,
                autoRow,
                buttonRow1,
                buttonRow2,
                new Separator(),
                liveGrid,
                new Separator(),
                restore1Grid,
                new Separator(),
                restore2Grid
        );
        root.setPadding(new Insets(14));

        Scene scene = new Scene(root, 560, 660);
        primaryStage.setTitle("FullScreenRestoreCycleTest");
        primaryStage.setScene(scene);
        primaryStage.setResizable(false);
        primaryStage.show();

        // Start automatically after the primary stage is shown
        Platform.runLater(this::startAutoRun);
    }

    // ---- helpers ----

    private void createTestStage() {
        if (testStage != null) {
            testStage.close();
        }
        resetLabels();

        testStage = new Stage();
        testStage.initStyle(cbStyle.getValue());
        testStage.setTitle("Test Stage (" + cbStyle.getValue() + ")");
        testStage.setWidth(EXPECTED_W);
        testStage.setHeight(EXPECTED_H);
        testStage.setX(EXPECTED_X);
        testStage.setY(EXPECTED_Y);

        Label info = new Label("This is the test stage.\nSize: 200×250  Position: (100, 150)");
        info.setStyle("-fx-font-size: 14;");
        info.setWrapText(true);
        Scene scene = new Scene(info, EXPECTED_W, EXPECTED_H);
        if (cbStyle.getValue() == StageStyle.TRANSPARENT) {
            scene.setFill(Color.color(1, 0.5, 0.7, 0.6));
        }
        testStage.setScene(scene);

        // bind live labels
        testStage.widthProperty().addListener((obs, o, n) -> lblCurrentW.setText(fmt(n.doubleValue())));
        testStage.heightProperty().addListener((obs, o, n) -> lblCurrentH.setText(fmt(n.doubleValue())));
        testStage.xProperty().addListener((obs, o, n) -> lblCurrentX.setText(fmt(n.doubleValue())));
        testStage.yProperty().addListener((obs, o, n) -> lblCurrentY.setText(fmt(n.doubleValue())));
        testStage.fullScreenProperty().addListener((obs, o, n) -> lblFullScreen.setText(n.toString()));

        testStage.show();
        cycleStep = 1;
        setButtonsEnabled(false);
        btnFullOn1.setDisable(false);
        cbStyle.setDisable(true);
    }

    private void captureRestore1() {
        double w = testStage.getWidth();
        double h = testStage.getHeight();
        double x = testStage.getX();
        double y = testStage.getY();

        lblRestoreW1.setText(fmt(w));
        lblRestoreH1.setText(fmt(h));
        lblRestoreX1.setText(fmt(x));
        lblRestoreY1.setText(fmt(y));

        boolean pass = check(w, EXPECTED_W, SIZE_DELTA)
                && check(h, EXPECTED_H, SIZE_DELTA)
                && check(x, EXPECTED_X, POS_DELTA)
                && check(y, EXPECTED_Y, POS_DELTA);
        setPassFail(lblPassFail1, pass);
    }

    private void captureRestore2() {
        double w = testStage.getWidth();
        double h = testStage.getHeight();
        double x = testStage.getX();
        double y = testStage.getY();

        lblRestoreW2.setText(fmt(w));
        lblRestoreH2.setText(fmt(h));
        lblRestoreX2.setText(fmt(x));
        lblRestoreY2.setText(fmt(y));

        boolean pass = check(w, EXPECTED_W, SIZE_DELTA)
                && check(h, EXPECTED_H, SIZE_DELTA)
                && check(x, EXPECTED_X, POS_DELTA)
                && check(y, EXPECTED_Y, POS_DELTA);
        setPassFail(lblPassFail2, pass);
    }

    private void reset() {
        autoRunCancelled = true;
        if (testStage != null) {
            testStage.close();
            testStage = null;
        }
        resetLabels();
        setButtonsEnabled(false);
        cbStyle.setDisable(false);
        btnAutoRun.setDisable(false);
        btnAutoRun.setText("▶ Auto Run");
        cycleStep = 0;
    }

    /**
     * Runs the full cycle automatically:
     * create → fullOn1 → wait → fullOff1 → wait (capture1) → fullOn2 → wait → fullOff2 → wait (capture2)
     * The delay between each step is controlled by the spinner.
     */
    private void startAutoRun() {
        autoRunCancelled = false;
        btnAutoRun.setText("■ Running…");
        btnAutoRun.setDisable(true);
        cbStyle.setDisable(true);

        // Step 1: create
        Platform.runLater(this::createTestStage);

        int delay = spinnerDelay.getValue();

        new Thread(() -> {
            // Step 2: enter fullscreen 1
            sleep(delay);
            if (autoRunCancelled) return;
            Platform.runLater(() -> {
                testStage.setFullScreen(true);
                cycleStep = 2;
            });

            // Step 3: exit fullscreen 1 + capture
            sleep(delay);
            if (autoRunCancelled) return;
            Platform.runLater(() -> testStage.setFullScreen(false));

            sleep(delay);
            if (autoRunCancelled) return;
            Platform.runLater(() -> {
                captureRestore1();
                cycleStep = 3;
            });

            // Step 4: enter fullscreen 2
            sleep(delay);
            if (autoRunCancelled) return;
            Platform.runLater(() -> {
                testStage.setFullScreen(true);
                cycleStep = 4;
            });

            // Step 5: exit fullscreen 2 + capture
            sleep(delay);
            if (autoRunCancelled) return;
            Platform.runLater(() -> testStage.setFullScreen(false));

            sleep(delay);
            if (autoRunCancelled) return;
            Platform.runLater(() -> {
                captureRestore2();
                setButtonsEnabled(false);
                btnReset.setDisable(false);
                btnAutoRun.setText("▶ Auto Run");
                btnAutoRun.setDisable(false);
                cycleStep = 5;
            });
        }, "auto-run-thread").start();
    }

    private static void sleep(int ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void resetLabels() {
        for (Label l : new Label[]{
                lblRestoreW1, lblRestoreH1, lblRestoreX1, lblRestoreY1, lblPassFail1,
                lblRestoreW2, lblRestoreH2, lblRestoreX2, lblRestoreY2, lblPassFail2,
                lblCurrentW, lblCurrentH, lblCurrentX, lblCurrentY, lblFullScreen}) {
            l.setText("—");
            l.setStyle("");
        }
    }

    private void setButtonsEnabled(boolean all) {
        btnCreate.setDisable(!all && cycleStep > 0);
        btnFullOn1.setDisable(true);
        btnFullOff1.setDisable(true);
        btnFullOn2.setDisable(true);
        btnFullOff2.setDisable(true);
        btnReset.setDisable(true);
        if (all) {
            btnCreate.setDisable(false);
            btnReset.setDisable(false);
            btnAutoRun.setDisable(false);
        }
    }

    private boolean check(double actual, double expected, double delta) {
        return Math.abs(actual - expected) <= delta;
    }

    private static String fmt(double v) {
        return String.format("%.1f", v);
    }

    private static Label makeValueLabel() {
        Label l = new Label("—");
        l.setMinWidth(70);
        return l;
    }

    private static Label makePassFailLabel() {
        Label l = new Label("—");
        l.setMinWidth(60);
        l.setFont(Font.font(l.getFont().getFamily(), FontWeight.BOLD, 13));
        return l;
    }

    private static void setPassFail(Label label, boolean pass) {
        label.setText(pass ? "PASS" : "FAIL");
        label.setStyle(pass
                ? "-fx-text-fill: #1a7a1a;"
                : "-fx-text-fill: #cc0000;");
    }

    private GridPane buildSectionGrid(String sectionTitle, String[] names, Label[] valueLabels) {
        GridPane grid = new GridPane();
        grid.setHgap(12);
        grid.setVgap(6);
        grid.setPadding(new Insets(6, 10, 6, 10));

        Label header = new Label(sectionTitle);
        header.setFont(Font.font(header.getFont().getFamily(), FontWeight.BOLD, 12));
        grid.add(header, 0, 0, 4, 1);

        // column headers
        String[] colHeaders = {"Property", "Expected", "Actual", ""};
        String[] expectedVals = {
                String.valueOf(EXPECTED_W),
                String.valueOf(EXPECTED_H),
                String.valueOf(EXPECTED_X),
                String.valueOf(EXPECTED_Y),
                "—"
        };

        for (int i = 0; i < names.length; i++) {
            grid.add(new Label(names[i] + ":"), 0, i + 1);
            grid.add(new Label(i < expectedVals.length - 1 ? expectedVals[i] : "—"), 1, i + 1);
            grid.add(valueLabels[i], 2, i + 1);
        }
        return grid;
    }

    public static void main(String[] args) {
        launch(FullScreenRestoreCycleTest.class, args);
    }
}