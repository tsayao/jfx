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
import javafx.scene.effect.BoxBlur;
import javafx.scene.image.Image;
import javafx.scene.layout.Background;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.SVGPath;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

public class SceneDecorationTest extends Application {
    private GridPane pane;

    public static void main(String[] args) {
        launch(SceneDecorationTest.class, args);
    }

    @Override
    public void start(Stage stage) throws Exception {
        CheckBox showIcon = new CheckBox();
        showIcon.setSelected(true);
        CheckBox showTitle = new CheckBox();
        showTitle.setSelected(true);
        ComboBox<HPos> position = new ComboBox<>(FXCollections.observableArrayList(HPos.values()));
        position.getSelectionModel().select(HPos.RIGHT);

        Button fullScrn = new Button("Full Screen");
        fullScrn.setOnAction(e -> stage.setFullScreen(true));

        pane = new GridPane();
        addOption("Show Icon", showIcon);
        addOption("Show Title", showTitle);
        addOption("Buttons Pos", position);
        addOption("Full Scrn", fullScrn);

        SceneDecoration decoration = new SceneDecoration(stage, pane);
        pane.prefWidthProperty().bind(decoration.widthProperty().multiply(0.80));

        decoration.showIconProperty().bind(showIcon.selectedProperty());
        decoration.showTitleProperty().bind(showTitle.selectedProperty());
        decoration.headerButtonsPositionProperty().bind(position.valueProperty());
        decoration.setHeaderLeft(getHamburgerButton());

        var scene = new Scene(decoration, Color.TRANSPARENT);
        String css = getClass().getClassLoader().getResource("tests/manual/controls/decoration.css").toExternalForm();
        scene.getStylesheets().add(css);

        stage.setScene(scene);
        stage.getIcons().add(new Image("https://openjdk.org/images/duke-thinking.png"));
        stage.initStyle(StageStyle.TRANSPARENT);
        stage.setTitle("Test Stage");
        stage.setWidth(600);
        stage.setHeight(400);
        stage.show();
    }

    private Button getHamburgerButton() {
        Button btn = new Button();

        var svg = new SVGPath();
        svg.setContent("M0 96C0 78.3 14.3 64 32 64H416c17.7 0 32 14.3 32 32s-14.3 32-32 32H32C14.3 128 0 113.7 0 96zM0 256c0-17.7 14.3-32 32-32H416c17.7 0 32 14.3 32 32s-14.3 32-32 32H32c-17.7 0-32-14.3-32-32zM448 416c0 17.7-14.3 32-32 32H32c-17.7 0-32-14.3-32-32s14.3-32 32-32H416c17.7 0 32 14.3 32 32z");

        final StackPane svgShape = new StackPane();
        svgShape.setScaleShape(true);
        svgShape.setShape(svg);
        svgShape.setPrefSize(16, 16);
        svgShape.setBackground(Background.fill(Color.BLACK));

        btn.setGraphic(svgShape);

        return btn;
    }

    int currentRow = 0;
    private void addOption(String lbl, Node opt) {
        pane.add(new Label(lbl), 0, currentRow);
        pane.add(opt, 1, currentRow);

        currentRow++;
    }
}
