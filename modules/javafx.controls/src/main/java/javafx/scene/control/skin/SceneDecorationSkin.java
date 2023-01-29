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
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.SkinBase;
import javafx.scene.control.SceneDecoration;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.stage.Stage;
import javafx.stage.WindowEvent;

public class SceneDecorationSkin extends SkinBase<SceneDecoration> {
    private final SceneDecorationBehaviour behaviour;
    private final Stage stage;

    private final MainRegion mainRegion;
    private final HeaderRegion headerRegion;

    private final HeaderLeftRegion leftRegion;
    private final HeaderRightRegion rightRegion;
    private final HeaderButtonsRegion stageButtons;

    private IconRegion icon;
    private TitleRegion title;

    public SceneDecorationSkin(SceneDecoration control, Stage stage) {
        super(control);
        this.behaviour = new SceneDecorationBehaviour(control);
        this.stage = stage;

        mainRegion = new MainRegion();
        headerRegion = new HeaderRegion();
        leftRegion = new HeaderLeftRegion();
        rightRegion = new HeaderRightRegion();
        stageButtons = new HeaderButtonsRegion();

        ListenerHelper lh = ListenerHelper.get(this);
        lh.addChangeListener(this::updateHeader, control.showIconProperty(), control.showTitleProperty(),
                control.headerLeftProperty(), control.headerRightProperty());

        lh.addChangeListener(this::update, stage.fullScreenProperty(), control.contentProperty());

        getChildren().add(mainRegion);
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
        mainRegion.getChildren().add(headerRegion);

        if (getSkinnable().isShowIcon()) {
            if (icon == null) {
                icon = new IconRegion();
            }
            headerRegion.getChildren().add(icon);
            HBox.setHgrow(icon, Priority.NEVER);
        }

        if (getSkinnable().getHeaderLeft() != null) {
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
            headerRegion.getChildren().add(rightRegion);
            HBox.setHgrow(rightRegion, Priority.SOMETIMES);
        }

        headerRegion.getChildren().add(stageButtons);
        HBox.setHgrow(stageButtons, Priority.NEVER);
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

            fitHeightProperty().bind(headerRegion.heightProperty());

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
            getStyleClass().setAll("buttons");

            iconify = new HeaderButton("iconify");
            maximize = new HeaderButton("maximize");
            close = new HeaderButton("close");

            iconify.setOnAction(e -> stage.setIconified(!stage.isIconified()));
            maximize.setOnAction(e -> stage.setMaximized(!stage.isMaximized()));
            close.setOnAction(e -> stage.fireEvent(new WindowEvent(stage, WindowEvent.WINDOW_CLOSE_REQUEST)));

            ListenerHelper lh = new ListenerHelper(this);
            lh.addChangeListener(this::update, stage.resizableProperty());

            update();
        }

        private void update() {
            getChildren().clear();
            getChildren().add(iconify);

            if (stage.isResizable()) {
                getChildren().add(maximize);
            }

            getChildren().add(close);
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
            StackPane icon = new StackPane();
            icon.getStyleClass().setAll("icon", css);
            icon.setId(css);
            setGraphic(icon);
        }
    }
}