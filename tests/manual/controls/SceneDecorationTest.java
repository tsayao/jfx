/*
 *  Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
 *  DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 *  This code is free software; you can redistribute it and/or modify it
 *  under the terms of the GNU General Public License version 2 only, as
 *  published by the Free Software Foundation.  Oracle designates this
 *  particular file as subject to the "Classpath" exception as provided
 *  by Oracle in the LICENSE file that accompanied this code.
 *
 *  This code is distributed in the hope that it will be useful, but WITHOUT
 *  ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 *  FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 *  version 2 for more details (a copy is included in the LICENSE file that
 *  accompanied this code).
 *
 *  You should have received a copy of the GNU General Public License version
 *  2 along with this work; if not, write to the Free Software Foundation,
 *  Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 *  Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 *  or visit www.oracle.com if you need additional information or have any
 *  questions.
 *
 */

import javafx.application.Application;
import javafx.collections.FXCollections;
import javafx.geometry.HPos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.layout.GridPane;
import javafx.scene.paint.Color;
import javafx.stage.Stage;

public class SceneDecorationTest extends Application {
    GridPane pane = new GridPane();

    public static void main(String[] args) {
        launch(SceneDecorationTest.class, args);
    }

    @Override
    public void start(Stage stage) throws Exception {
        pane.prefWidthProperty().bind(stage.widthProperty().multiply(0.80));
        CheckBox showIcon = new CheckBox();
        showIcon.setSelected(true);
        CheckBox showTitle = new CheckBox();
        showTitle.setSelected(true);
        ComboBox<HPos> position = new ComboBox<>(FXCollections.observableArrayList(HPos.values()));
        position.getSelectionModel().select(HPos.RIGHT);

        addOption("Show Icon", showIcon);
        addOption("Show Title", showTitle);
        addOption("Buttons Pos", position);

        SceneDecoration decoration = new SceneDecoration(stage, pane);

        decoration.showIconProperty().bind(showIcon.selectedProperty());
        decoration.showTitleProperty().bind(showTitle.selectedProperty());
        decoration.headerButtonsPositionProperty().bind(position.valueProperty());

        var scene = new Scene(decoration, Color.TRANSPARENT);
        String css = getClass().getClassLoader().getResource("tests/manual/controls/decoration.css").toExternalForm();
        System.out.println(css);
        scene.getStylesheets().add(css);

        stage.setScene(scene);
        stage.getIcons().add(new Image("https://openjdk.org/images/duke-thinking.png"));
        stage.setTitle("Test Stage");
        stage.setWidth(400);
        stage.setHeight(200);
        stage.setX(0);
        stage.setY(0);
        stage.show();
    }

    int currentRow = 0;
    void addOption(String lbl, Node opt) {
        pane.add(new Label(lbl), 0, currentRow);
        pane.add(opt, 1, currentRow);

        currentRow++;
    }
}
