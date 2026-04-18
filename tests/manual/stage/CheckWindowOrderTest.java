/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.text.Text;
import javafx.scene.layout.StackPane;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;

/**
 * Manual test for checking window order (see JDK-8220272).
 *
 * Four windows are opened in order:
 *   1. Primary Stage (owner of First and Second)
 *   2. First Window (owned by Primary Stage)
 *   3. Second Window (owned by Primary Stage)
 *   4. Last Window (WINDOW_MODAL, owned by Second Window)
 *
 * Expected behavior:
 *   - The "Last Window" should be the topmost and focused window.
 *   - The "Last Window" title bar should be active/focused.
 *   - You should NOT need to click on it to bring it to front.
 */
public class CheckWindowOrderTest extends Application {

    @Override
    public void start(Stage primaryStage) {
        primaryStage.setTitle("Primary Stage");
        primaryStage.setScene(new Scene(new StackPane(new Text("""
                This is the Primary Stage.
                Four windows are opened simultaneously.

                EXPECTED: The "Last Window" should be focused and on top.

                1. Verify that "Last Window" is the topmost window
                2. Verify that "Last Window" has focus (active title bar)
                3. The window order from back to front should be:
                   Primary Stage -> First Window -> Second Window -> Last Window
                   Primary Stage -> First Window -> Second Window -> Last Window""")), 500, 300));

        primaryStage.show();

        Stage firstWindow = createOwnedStage(primaryStage, "First Window", 100, 100);
        firstWindow.show();

        Stage secondWindow = createOwnedStage(primaryStage, "Second Window", 200, 200);
        secondWindow.show();

        Stage lastWindow = createOwnedStage(secondWindow, "Last Window", 300, 300);
        lastWindow.initModality(Modality.WINDOW_MODAL);
        lastWindow.show();
    }

    private Stage createOwnedStage(Window owner, String title, double offset, double offsetY) {
        Stage stage = new Stage();
        stage.initOwner(owner);
        stage.setTitle(title);
        stage.setScene(new Scene(new StackPane(new Label(title)), 300, 200));
        addFocusListener(stage, title);
        stage.setX(offset + 100);
        stage.setY(offsetY + 100);
        return stage;
    }

    private void addFocusListener(Stage stage, String baseTitle) {
        stage.focusedProperty().addListener((obs, wasFocused, isFocused) -> {
            String state = isFocused ? "focused" : "unfocused";
            stage.setTitle(baseTitle + " [" + state + "]");
            System.out.println(baseTitle + " -> " + state);
        });
    }

    public static void main(String[] args) {
        launch(CheckWindowOrderTest.class, args);
    }
}

