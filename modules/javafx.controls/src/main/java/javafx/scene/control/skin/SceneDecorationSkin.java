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
import javafx.beans.InvalidationListener;
import javafx.geometry.HPos;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.SceneDecoration;
import javafx.scene.control.SkinBase;
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
    private HeaderRegion headerRegion = null;

    public SceneDecorationSkin(SceneDecoration control, Stage stage) {
        super(control);
        this.behaviour = new SceneDecorationBehaviour(control);
        this.stage = stage;

        mainRegion = new MainRegion();

        ListenerHelper lh = ListenerHelper.get(this);
        lh.addChangeListener(this::updateHeader, control.showIconProperty(), control.showTitleProperty(),
                control.headerLeftProperty(), control.headerRightProperty(), control.headerButtonsPositionProperty());

        lh.addChangeListener(this::update, true, stage.fullScreenProperty(), control.contentProperty());

        getChildren().setAll(mainRegion);
    }

    private void update() {
        mainRegion.getChildren().clear();

        updateHeader();

        mainRegion.getChildren().add(getSkinnable().getContent());
        VBox.setVgrow(getSkinnable().getContent(), Priority.ALWAYS);
    }

    private void updateHeader() {
        if (stage.isFullScreen()) {
            return;
        }

        if (headerRegion == null) {
            headerRegion = new HeaderRegion();
        }

        mainRegion.getChildren().add(headerRegion);
        headerRegion.update();
    }

    @Override
    public void dispose() {
        super.dispose();
        behaviour.dispose();
    }

    class HeaderRegion extends Region {
        private HeaderLeftRegion leftRegion = null;
        private HeaderRightRegion rightRegion = null;
        private HeaderButtonsRegion headerButtons = null;

        private IconRegion icon = null;
        private TitleRegion title = null;

        HeaderRegion() {
            getStyleClass().setAll("header");
        }

        private void update() {
            getChildren().clear();
            updateIcon();
            updateLeft();
            updateTitle();
            updateRight();
            updateButtons();

            requestLayout();
        }

        private void updateButtons() {
            if (getSkinnable().isShowHeaderButtons()) {
                headerButtons = new HeaderButtonsRegion();
                getChildren().add(headerButtons);
            } else {
                headerButtons = null;
            }
        }

        private void updateTitle() {
            if (getSkinnable().isShowTitle()) {
                if (title == null) {
                    title = new TitleRegion();
                    getChildren().add(title);
                }
            } else {
                title = null;
            }
        }

        private void updateIcon() {
            if (getSkinnable().isShowIcon()) {
                if (icon == null) {
                    icon = new IconRegion();
                    getChildren().add(icon);
                }
            } else {
                icon = null;
            }
        }

        private void updateRight() {
            if (getSkinnable().getHeaderRight() != null) {
                if (rightRegion != null) {
                    rightRegion = new HeaderRightRegion();
                    getChildren().add(rightRegion);
                }
            } else {
                rightRegion = null;
            }
        }

        private void updateLeft() {
            if (getSkinnable().getHeaderLeft() != null) {
                if (leftRegion == null) {
                    leftRegion = new HeaderLeftRegion();
                    getChildren().add(leftRegion);
                }
            } else {
                leftRegion = null;
            }
        }

        private double getY(double h) {
            return (getHeight() - h) / 2;
        }

        @Override
        protected void layoutChildren() {
            //adjust the position to be in the middle
            double left  = getInsets().getLeft();
            double right = getInsets().getRight();

            double w = snapSizeX(getWidth());
            double mh = snapSizeY(getHeight());

            if (headerButtons != null) {
                headerButtons.setMaxHeight(mh);
                headerButtons.autosize();
            }

            if (icon != null && icon.getImage() != null) {
                double imgH = icon.getImage().getHeight();

                if (imgH > mh) {
                    icon.setFitHeight(mh);
                }

                icon.relocate(left, getY(icon.getFitHeight()));
                left += icon.getFitWidth();
            }

            if (headerButtons != null
                    && getSkinnable().getHeaderButtonsPosition() == HPos.LEFT) {
                headerButtons.relocate(left, getY(headerButtons.getHeight()));
                left += headerButtons.getWidth();
            }

            if (leftRegion != null) {
                leftRegion.relocate(left, getY(leftRegion.getHeight()));
                left += leftRegion.getWidth();
            }

            //title goes in the middle;
            if (title != null) {
                double tw = title.getWidth();
                double tx = (w - tw) / 2;
                double ty = getY(title.getHeight());

                double tmw = getWidth() - snappedLeftInset() - snappedRightInset();

                tmw -= (headerButtons != null) ? headerButtons.getWidth() : 0;
                tmw -= (icon != null) ? icon.getFitWidth() : 0;

                title.setMaxWidth((tmw < 0) ? 0 : tmw);
                title.setMaxHeight(mh);
                title.autosize();

//                System.out.printf("label w = %f, h = %f, x, y: %f, %f%n", tw, title.getHeight(), tx, ty);
                title.relocate(tx, ty);
            }

            if (headerButtons != null
                    && getSkinnable().getHeaderButtonsPosition() == HPos.RIGHT) {

                double hbw = headerButtons.getWidth();
                headerButtons.relocate(w  - right - hbw, getY(headerButtons.getHeight()));
                right += hbw;
            }

        }
    }

    class TitleRegion extends Label {
        TitleRegion() {
            setManaged(false);
            getStyleClass().add("title");
            textProperty().bind(stage.titleProperty());
        }
    }

    class IconRegion extends ImageView {
        IconRegion() {
            setManaged(false);
            getStyleClass().add("icon");
            setPreserveRatio(true);

            //TODO: do it elsewhere
//            stage.getIcons().addListener((InvalidationListener) l -> update());
            update();
        }

        private void update() {
            double height = headerRegion.getHeight();

            setImage(null);

            //find best height
            stage.getIcons().stream()
                    .min((f1, f2) -> (int) ((f1.getHeight() - height) - (f2.getHeight() - height)))
                    .ifPresent(this::setImage);
        }
    }

    class HeaderButtonsRegion extends HBox {
        private final HeaderButton iconify;
        private final HeaderButton maximize;
        private final HeaderButton close;

        HeaderButtonsRegion() {
            setManaged(false);
            getStyleClass().setAll("header-buttons");

            iconify = new HeaderButton("iconify");
            maximize = new HeaderButton("maximize");
            close = new HeaderButton("close");

            iconify.setOnAction(e -> stage.setIconified(!stage.isIconified()));
            maximize.setOnAction(e -> stage.setMaximized(!stage.isMaximized()));
            close.setOnAction(e -> stage.fireEvent(new WindowEvent(stage, WindowEvent.WINDOW_CLOSE_REQUEST)));

            ListenerHelper lh = new ListenerHelper(this);
            lh.addChangeListener(this::update, true, stage.resizableProperty(), getSkinnable().headerButtonsPositionProperty());
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
            setManaged(false);
            getStyleClass().setAll("left");
            ListenerHelper lh = new ListenerHelper(this);
            lh.addChangeListener(this::update, true, getSkinnable().headerLeftProperty());
        }

        private void update() {
            if (getSkinnable().getHeaderLeft() != null) {
                getChildren().setAll(getSkinnable().getHeaderLeft());
            }
        }
    }

    class HeaderRightRegion extends StackPane {
        HeaderRightRegion() {
            setManaged(false);
            getStyleClass().setAll("right");
            ListenerHelper lh = new ListenerHelper(this);
            lh.addChangeListener(this::update, true, getSkinnable().headerRightProperty());
        }

        private void update() {
            if (getSkinnable().getHeaderRight() != null) {
                getChildren().setAll(getSkinnable().getHeaderRight());
            }
        }
    }

    static class MainRegion extends VBox {
        public MainRegion() {
            getStyleClass().setAll("main");
            setAlignment(Pos.TOP_LEFT);
        }
    }

    static class HeaderButton extends Button {
        HeaderButton(final String css) {
            getStyleClass().setAll("header-button");

            StackPane icon = new StackPane();
            icon.getStyleClass().setAll("icon", css);
            icon.setId(css);
            setGraphic(icon);
        }
    }
}