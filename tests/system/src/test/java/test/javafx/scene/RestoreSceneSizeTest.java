/*
 * Copyright (c) 2017, 2025, Oracle and/or its affiliates. All rights reserved.
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
package test.javafx.scene;

import static org.junit.jupiter.api.Assumptions.assumeTrue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.scene.Scene;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.stage.WindowEvent;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import com.sun.javafx.PlatformUtil;
import test.util.Util;

public class RestoreSceneSizeTest {
    static CountDownLatch startupLatch = new CountDownLatch(1);
    static Stage stage;

    private static final int WIDTH = 234;
    private static final int HEIGHT = 255;
    private static double scaleX, scaleY;

    public static void main(String[] args) throws Exception {
        initFX();
        try {
            RestoreSceneSizeTest test = new RestoreSceneSizeTest();
            test.testUnfullscreenSize();
        } catch (Throwable e) {
            e.printStackTrace();
        } finally {
            teardown();
        }
    }

    public static class TestApp extends Application {

        @Override
        public void start(Stage primaryStage) throws Exception {
            primaryStage.setScene(new Scene(new VBox(), WIDTH, HEIGHT));
            stage = primaryStage;
            stage.setFullScreen(true);
            stage.addEventHandler(WindowEvent.WINDOW_SHOWN, e -> {
                scaleX = stage.getOutputScaleX();
                scaleY = stage.getOutputScaleY();

                Platform.runLater(startupLatch::countDown);
            });
            stage.show();
        }
    }

    @BeforeAll
    public static void initFX() {
        Util.launch(startupLatch, TestApp.class);
    }

    @AfterAll
    public static void teardown() {
        Util.shutdown();
    }

    @Test
    public void testUnfullscreenSize() throws Exception {
        // Disable on Linux until JDK-8353556 is fixed
        assumeTrue(!PlatformUtil.isLinux());

        Thread.sleep(200);
        final double w = (Math.ceil(WIDTH * scaleX)) / scaleX;
        final double h = (Math.ceil(HEIGHT * scaleY)) / scaleY;
        Assertions.assertTrue(stage.isShowing());
        Assertions.assertTrue(stage.isFullScreen());

        CountDownLatch latch = new CountDownLatch(2);
        ChangeListener<Number> listenerW = (observable, oldValue, newValue) -> {
            if (Math.abs((Double) newValue - w) < 0.1) {
                latch.countDown();
            };
        };
        ChangeListener<Number> listenerH = (observable, oldValue, newValue) -> {
            if (Math.abs((Double) newValue - h) < 0.1) {
                latch.countDown();
            };
        };
        stage.getScene().widthProperty().addListener(listenerW);
        stage.getScene().heightProperty().addListener(listenerH);
        Platform.runLater(() -> stage.setFullScreen(false));
        latch.await(5, TimeUnit.SECONDS);
        Thread.sleep(200);
        Assertions.assertFalse(stage.isFullScreen());
        stage.getScene().widthProperty().removeListener(listenerW);
        stage.getScene().heightProperty().removeListener(listenerH);

        Assertions.assertEquals(w, stage.getScene().getWidth(), 0.1, "Scene got wrong width");
        Assertions.assertEquals(h, stage.getScene().getHeight(), 0.1, "Scene got wrong height");
    }
}
