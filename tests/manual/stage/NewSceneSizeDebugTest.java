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

// Manual debug counterpart of test.javafx.scene.NewSceneSizeTest
//
// TEST INSTRUCTIONS:
//   1. Run this application directly (no arguments needed).
//   2. A primary window will appear briefly.
//   3. After ~300ms, it creates 10 child stages with different scene sizes,
//      each alternating between resizable and non-resizable.
//   4. Observe the console output: for each stage it prints:
//        - The requested scene size
//        - The expected size (after scale rounding)
//        - Every intermediate width/height change (via property listeners)
//        - A PASS / FAIL summary line once stable
//   5. The primary window will show an overall PASS/FAIL count after all
//      stages have settled (or after the 5-second timeout).
//
// The test is equivalent to the automated NewSceneSizeTest but uses only
// the first 10 iterations (i=0..9) so the output stays readable.

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import javafx.stage.WindowEvent;

public class NewSceneSizeDebugTest extends Application {

    // How many child stages to create (keep small for readable output)
    private static final int N = 10;

    private static double scaleX, scaleY;

    private Stage[] childStage   = new Stage[N];
    private double[] expectedW   = new double[N];
    private double[] expectedH   = new double[N];

    private final CountDownLatch latch  = new CountDownLatch(2 * N);
    private final AtomicInteger  passed = new AtomicInteger(0);
    private final AtomicInteger  failed = new AtomicInteger(0);

    private Label summaryLabel;

    // -----------------------------------------------------------------------
    // Entry point
    // -----------------------------------------------------------------------
    public static void main(String[] args) {
        Application.launch(NewSceneSizeDebugTest.class, args);
    }

    // -----------------------------------------------------------------------
    // Primary stage setup
    // -----------------------------------------------------------------------
    @Override
    public void start(Stage primaryStage) {
        summaryLabel = new Label("Running…");
        summaryLabel.setStyle("-fx-font-size: 16; -fx-font-weight: bold;");

        VBox root = new VBox(12, summaryLabel);
        root.setPadding(new Insets(20));
        root.setAlignment(Pos.CENTER);

        Scene primaryScene = new Scene(root, 400, 120);
        primaryStage.setScene(primaryScene);
        primaryStage.setTitle("NewSceneSizeDebugTest – primary");

        // Capture scale once the window is fully shown
        primaryStage.addEventHandler(WindowEvent.WINDOW_SHOWN, e -> {
            scaleX = primaryStage.getOutputScaleX();
            scaleY = primaryStage.getOutputScaleY();
            System.out.printf("[main] outputScale x=%.2f y=%.2f%n", scaleX, scaleY);
            // Start the real test after a short settling delay
            Platform.runLater(() -> {
                try { Thread.sleep(300); } catch (InterruptedException ignored) {}
                Platform.runLater(this::runTest);
            });
        });

        primaryStage.show();
    }

    // -----------------------------------------------------------------------
    // Create child stages (equivalent to the automated test loop)
    // -----------------------------------------------------------------------
    private void runTest() {
        System.out.println("[test] Creating " + N + " child stages…");

        for (int i = 0; i < N; i++) {
            final int idx = i;
            Platform.runLater(() -> createChildStage(idx));
        }

        // Wait for all listeners to fire (or timeout) in a background thread,
        // then post a summary back to the FX thread.
        Thread waiter = new Thread(() -> {
            try {
                boolean completed = latch.await(5, TimeUnit.SECONDS);
                System.out.println("\n[test] Latch " + (completed ? "completed" : "TIMED OUT"));
            } catch (InterruptedException ignored) {}

            // Extra settling sleep (same as automated test)
            try { Thread.sleep(200); } catch (InterruptedException ignored) {}

            // Final check
            for (int i = 0; i < N; i++) {
                final int idx = i;
                Platform.runLater(() -> finalCheck(idx));
            }

            Platform.runLater(this::showSummary);
        }, "NewSceneSizeDebugTest-Waiter");
        waiter.setDaemon(true);
        waiter.start();
    }

    // -----------------------------------------------------------------------
    // Create a single child stage
    // -----------------------------------------------------------------------
    private void createChildStage(int idx) {
        boolean resizable = (idx % 2 == 0);
        int sceneW = 300 - idx;
        int sceneH = 200 - idx;

        double expW = Math.ceil(sceneW * scaleX) / scaleX;
        double expH = Math.ceil(sceneH * scaleY) / scaleY;

        expectedW[idx] = expW;
        expectedH[idx] = expH;

        System.out.printf("[stage %2d] requested scene=(%d x %d)  resizable=%-5b  "
                        + "expected=(%.2f x %.2f)%n",
                idx, sceneW, sceneH, resizable, expW, expH);

        Stage stage = new Stage();
        childStage[idx] = stage;
        stage.setResizable(resizable);
        stage.setScene(new Scene(new VBox(), sceneW, sceneH));
        stage.setTitle("child-" + idx + (resizable ? " [R]" : " [NR]"));

        // --- width listener ---
        final ChangeListener<Number>[] lwRef = new ChangeListener[1];
        lwRef[0] = (obs, oldVal, newVal) -> {
            double w = newVal.doubleValue();
            System.out.printf("[stage %2d]   width  changed → %.2f  (expected %.2f, diff=%.4f)%n",
                    idx, w, expW, w - expW);
            if (Math.abs(w - expW) < 0.1) {
                stage.widthProperty().removeListener(lwRef[0]);
                System.out.printf("[stage %2d]   width  MATCHED ✓%n", idx);
                Platform.runLater(latch::countDown);
            }
        };

        // --- height listener ---
        final ChangeListener<Number>[] lhRef = new ChangeListener[1];
        lhRef[0] = (obs, oldVal, newVal) -> {
            double h = newVal.doubleValue();
            System.out.printf("[stage %2d]   height changed → %.2f  (expected %.2f, diff=%.4f)%n",
                    idx, h, expH, h - expH);
            if (Math.abs(h - expH) < 0.1) {
                stage.heightProperty().removeListener(lhRef[0]);
                System.out.printf("[stage %2d]   height MATCHED ✓%n", idx);
                Platform.runLater(latch::countDown);
            }
        };

        stage.widthProperty().addListener(lwRef[0]);
        stage.heightProperty().addListener(lhRef[0]);
        stage.show();
    }

    // -----------------------------------------------------------------------
    // Final assertion (mirrors the JUnit assertions in the automated test)
    // -----------------------------------------------------------------------
    private void finalCheck(int idx) {
        if (childStage[idx] == null) return;

        double actualW = childStage[idx].getScene().getWidth();
        double actualH = childStage[idx].getScene().getHeight();
        double expW    = expectedW[idx];
        double expH    = expectedH[idx];
        boolean ok     = Math.abs(actualW - expW) < 0.1 && Math.abs(actualH - expH) < 0.1;

        String result = ok ? "PASS ✓" : "FAIL ✗";
        if (ok) passed.incrementAndGet(); else failed.incrementAndGet();

        System.out.printf("[stage %2d] FINAL  scene=(%.2f x %.2f)  expected=(%.2f x %.2f)  → %s%n",
                idx, actualW, actualH, expW, expH, result);

        if (!ok) {
            System.out.printf("           *** diff: w=%.4f  h=%.4f%n",
                    actualW - expW, actualH - expH);
        }
    }

    // -----------------------------------------------------------------------
    // Update the primary-stage summary label
    // -----------------------------------------------------------------------
    private void showSummary() {
        int p = passed.get();
        int f = failed.get();
        String text = String.format("PASSED: %d / %d   FAILED: %d / %d", p, N, f, N);
        summaryLabel.setText(text);
        summaryLabel.setTextFill(f == 0 ? Color.GREEN : Color.RED);
        System.out.println("\n[test] " + text);
    }
}

