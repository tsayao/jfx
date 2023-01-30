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

package javafx.scene.control.skin;

import com.sun.javafx.scene.control.ListenerHelper;
import com.sun.javafx.scene.control.behavior.SceneDecorationBehaviour;
import javafx.geometry.HPos;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.SkinBase;
import javafx.scene.control.SceneDecoration;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.stage.Stage;
import javafx.stage.WindowEvent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class SceneDecorationSkin extends SkinBase<SceneDecoration> {
    private final SceneDecorationBehaviour behaviour;
    private final Stage stage;

    private final MainRegion mainRegion;
    private final HeaderRegion headerRegion;

    private  HeaderLeftRegion leftRegion = null;
    private HeaderRightRegion rightRegion = null;
    private HeaderButtonsRegion headerButtons = null;

    private IconRegion icon = null;
    private TitleRegion title = null;

    public SceneDecorationSkin(SceneDecoration control, Stage stage) {
        super(control);
        this.behaviour = new SceneDecorationBehaviour(control);
        this.stage = stage;

        mainRegion = new MainRegion();
        headerRegion = new HeaderRegion();

        ListenerHelper lh = ListenerHelper.get(this);
        lh.addChangeListener(this::updateHeader, control.showIconProperty(), control.showTitleProperty(),
                control.headerLeftProperty(), control.headerRightProperty(), control.headerButtonsPositionProperty());

        lh.addChangeListener(this::update, stage.fullScreenProperty(), control.contentProperty());

        getChildren().setAll(mainRegion);
        update();
    }

    private void update() {
        mainRegion.getChildren().clear();

        if (!stage.isFullScreen()) {
            updateHeader();
        }

        mainRegion.getChildren().add(getSkinnable().getContent());
    }

    private void updateHeader() {
        headerRegion.getChildren().clear();

        if (!mainRegion.getChildren().contains(headerRegion)) {
            mainRegion.getChildren().add(headerRegion);
        }

        if (getSkinnable().getHeaderButtonsPosition() == HPos.LEFT) {
            headerRegion.getChildren().add(headerButtons);
        }

        if (getSkinnable().isShowIcon()) {
            if (icon == null) {
                icon = new IconRegion();
            }
            headerRegion.getChildren().add(icon);
            HBox.setHgrow(icon, Priority.NEVER);
        }

        if (getSkinnable().getHeaderLeft() != null) {
            if (leftRegion == null) {
                leftRegion = new HeaderLeftRegion();
            }

            headerRegion.getChildren().add(leftRegion);
            HBox.setHgrow(leftRegion, Priority.SOMETIMES);
        }

        headerRegion.getChildren().add(getSpanPane());

        if (getSkinnable().isShowTitle()) {
            if (title == null) {
                title = new TitleRegion();
            }
            headerRegion.getChildren().add(title);
            HBox.setHgrow(title, Priority.ALWAYS);
        }

        headerRegion.getChildren().add(getSpanPane());

        if (getSkinnable().getHeaderRight() != null) {
            if (rightRegion == null) {
                rightRegion = new HeaderRightRegion();
            }
            headerRegion.getChildren().add(rightRegion);
            HBox.setHgrow(rightRegion, Priority.SOMETIMES);
        }

        if (getSkinnable().getHeaderButtonsPosition() == HPos.RIGHT) {
            headerButtons = new HeaderButtonsRegion();
            headerRegion.getChildren().add(headerButtons);
        }

        HBox.setHgrow(headerButtons, Priority.NEVER);
    }

    private StackPane getSpanPane() {
        StackPane pane = new StackPane();
        HBox.setHgrow(pane, Priority.SOMETIMES);
        return pane;
    }

    @Override
    public void dispose() {
        super.dispose();
        behaviour.dispose();
    }

    class HeaderRegion extends HBox {
        HeaderRegion() {
            getStyleClass().setAll("header");
        }
    }

    class IconRegion extends ImageView {
        IconRegion() {
            getStyleClass().add("icon");
            fitHeightProperty().bind(headerRegion.heightProperty().multiply(0.80f));
            setPreserveRatio(true);

            ListenerHelper lh = new ListenerHelper(this);
            lh.addChangeListener(this::update, getSkinnable().heightProperty());
            update();
        }

        private void update() {
            double height = getSkinnable().getHeight();

            setImage(null);

            //find best height
            stage.getIcons().stream()
                    .min((f1, f2) -> (int) ((f1.getHeight() - height) -  (f2.getHeight() - height)))
                    .ifPresent(this::setImage);
        }
    }

    class TitleRegion extends Label {
        TitleRegion() {
            getStyleClass().setAll("title");
            textProperty().bind(stage.titleProperty());
        }
    }

    class HeaderButtonsRegion extends HBox {
        private final HeaderButton iconify;
        private final HeaderButton maximize;
        private final HeaderButton close;

        HeaderButtonsRegion() {
            getStyleClass().setAll("header-buttons");

            iconify = new HeaderButton("iconify");
            maximize = new HeaderButton("maximize");
            close = new HeaderButton("close");

            iconify.setOnAction(e -> stage.setIconified(!stage.isIconified()));
            maximize.setOnAction(e -> stage.setMaximized(!stage.isMaximized()));
            close.setOnAction(e -> stage.fireEvent(new WindowEvent(stage, WindowEvent.WINDOW_CLOSE_REQUEST)));

            ListenerHelper lh = new ListenerHelper(this);
            lh.addChangeListener(this::update, stage.resizableProperty(), getSkinnable().headerButtonsPositionProperty());

            update();
        }

        private void update() {
            getChildren().clear();

            List<Node> buttons = new ArrayList<>();
            buttons.add(iconify);

            if (stage.isResizable()) {
                buttons.add(maximize);
            }

            buttons.add(close);

            if (getSkinnable().getHeaderButtonsPosition() == HPos.LEFT) {
                Collections.reverse(buttons);
            }

            getChildren().addAll(buttons);
        }
    }

    class HeaderLeftRegion extends StackPane {
        HeaderLeftRegion() {
            getStyleClass().setAll("left");
            ListenerHelper lh = new ListenerHelper(this);
            lh.addChangeListener(this::update, getSkinnable().headerLeftProperty());
        }

        private void update() {
            getChildren().setAll(getSkinnable().getHeaderLeft());
        }
    }

    class HeaderRightRegion extends StackPane {
        HeaderRightRegion() {
            getStyleClass().setAll("right");
            ListenerHelper lh = new ListenerHelper(this);
            lh.addChangeListener(this::update, getSkinnable().headerRightProperty());
        }

        private void update() {
            getChildren().setAll(getSkinnable().getHeaderRight());
        }
    }

    class MainRegion extends VBox {
        public MainRegion() {
            setAlignment(Pos.TOP_LEFT);
        }
    }

    class ContentRegion extends StackPane {
        ContentRegion() {
            getStyleClass().setAll("content");
        }
    }

    class HeaderButton extends Button {
        HeaderButton(final String css) {
            getStyleClass().setAll("header-button");

            StackPane icon = new StackPane();
            icon.getStyleClass().setAll("icon", css);
            icon.setId(css);
            setGraphic(icon);
        }
    }
}