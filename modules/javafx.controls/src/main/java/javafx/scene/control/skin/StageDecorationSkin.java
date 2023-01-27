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
import com.sun.javafx.scene.control.behavior.StageDecorationBehaviour;
import javafx.scene.control.Label;
import javafx.scene.control.SkinBase;
import javafx.scene.control.StageDecoration;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;

import java.util.Comparator;

public class StageDecorationSkin extends SkinBase<StageDecoration> {
    private final StageDecorationBehaviour behaviour;
    private final Stage stage;

    private final Container container;

    private final LeftRegion leftRegion;
    private final RightRegion rightRegion;
    private final StageButtonsRegion stageButtons;

    private IconRegion icon;
    private TitleRegion title;

    public StageDecorationSkin(StageDecoration control, Stage stage) {
        super(control);
        this.behaviour = new StageDecorationBehaviour(control);
        this.stage = stage;

        container = new Container();

        leftRegion = new LeftRegion();
        rightRegion = new RightRegion();
        stageButtons = new StageButtonsRegion();

        ListenerHelper lh = ListenerHelper.get(this);
        lh.addChangeListener(this::update, control.showIconProperty(), control.showTitleProperty(),
                stage.fullScreenProperty());
        update();
    }

    private void update() {
        getChildren().clear();

        if (stage.isFullScreen()) {
            return;
        }

        getChildren().add(container);

        if (getSkinnable().isShowIcon()) {
            if (icon == null) {
                icon = new IconRegion();
            }
            container.getChildren().add(icon);
            HBox.setHgrow(icon, Priority.NEVER);
        }

        container.getChildren().add(leftRegion);
        HBox.setHgrow(leftRegion, Priority.SOMETIMES);

        if (getSkinnable().isShowTitle()) {
            if (title == null) {
                title = new TitleRegion();
            }
            container.getChildren().add(title);
            HBox.setHgrow(title, Priority.ALWAYS);
        }

        container.getChildren().add(rightRegion);
        HBox.setHgrow(rightRegion, Priority.SOMETIMES);

        container.getChildren().add(stageButtons);
        HBox.setHgrow(stageButtons, Priority.NEVER);
    }

    @Override
    public void dispose() {
        super.dispose();
        behaviour.dispose();
    }

    class Container extends HBox {
        Container() {
            getStyleClass().setAll("container");
        }
    }

    class IconRegion extends ImageView {
        IconRegion() {
            getStyleClass().add("icon");

            ListenerHelper lh = new ListenerHelper(this);
            lh.addChangeListener(this::update, getSkinnable().heightProperty());
            update();
        }

        private void update() {
            double height = getSkinnable().getHeight();

            setImage(null);
            stage.getIcons().stream().filter(i -> i.getHeight() < height)
                    .max(Comparator.comparingDouble(Image::getHeight))
                    .ifPresent(this::setImage);
        }
    }

    class TitleRegion extends Label {
        TitleRegion() {
            getStyleClass().setAll("title");
            textProperty().bind(stage.titleProperty());
        }
    }

    class StageButtonsRegion extends HBox {
        StageButtonsRegion() {
            getStyleClass().setAll("stage-buttons");
            ListenerHelper lh = new ListenerHelper(this);
            lh.addChangeListener(this::update, stage.resizableProperty());
        }

        private void update() {
        }
    }

    class LeftRegion extends StackPane {
        LeftRegion() {
            getStyleClass().setAll("left");
        }
    }

    class RightRegion extends StackPane {
        RightRegion() {
            getStyleClass().setAll("right");
        }
    }
}