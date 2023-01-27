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

import com.sun.javafx.scene.control.behavior.StageDecorationBehaviour;
import javafx.scene.control.Label;
import javafx.scene.control.SkinBase;
import javafx.scene.control.StageDecoration;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.stage.Stage;

import java.util.Comparator;

public class StageDecorationSkin extends SkinBase<StageDecoration> {
    private IconRegion icon;
    private final Label title;
    private final StageDecorationBehaviour behaviour;
    private final StageDecoration control;
    private Stage stage;


    public StageDecorationSkin(StageDecoration control, Stage stage) {
        super(control);
        this.behaviour = new StageDecorationBehaviour(control);
        this.stage = stage;
        this.control = getSkinnable();

        title = new Label();
        title.textProperty().bind(stage.titleProperty());

        registerChangeListener(control.showIconProperty(), e -> updateChildren());
        registerChangeListener(control.leftProperty(), e -> updateChildren());
        registerChangeListener(control.rightProperty(), e -> updateChildren());
    }

    private void updateChildren() {
        getChildren().clear();

        if (control.isShowIcon() && !stage.getIcons().isEmpty()) {
            icon = new IconRegion();
            getChildren().add(icon);
        }
    }

    @Override
    public void dispose() {
        super.dispose();
        behaviour.dispose();
    }

    class IconRegion extends ImageView {
        public IconRegion() {
            double height = getSkinnable().getHeight();

            stage.getIcons().stream().filter(i -> i.getHeight() < height)
                    .max(Comparator.comparingDouble(Image::getHeight))
                    .ifPresent(this::setImage);
        }
    }

    class TitleButtonsRegion extends HBox {
        public TitleButtonsRegion() {
            getStyleClass().setAll("title-buttons");
        }
    }
}