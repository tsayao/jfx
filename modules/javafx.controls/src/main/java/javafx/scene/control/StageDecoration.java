package javafx.scene.control;

import javafx.beans.DefaultProperty;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ObjectPropertyBase;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.collections.ListChangeListener;
import javafx.scene.Node;
import javafx.scene.control.skin.StageDecorationSkin;
import javafx.stage.Stage;

import java.util.List;

@DefaultProperty("content")
public class StageDecoration extends Control {
    private static final String DEFAULT_STYLE_CLASS = "stage-decoration";
    private final Stage stage;

    public StageDecoration(Stage stage) {
        this.stage = stage;
        getStyleClass().setAll(DEFAULT_STYLE_CLASS);
    }

    @Override
    protected Skin<?> createDefaultSkin() {
        return new StageDecorationSkin(this, stage);
    }

    private final BooleanProperty showTitle = new SimpleBooleanProperty(this, "showTitle", true);

    public boolean isShowTitle() {
        return showTitle.get();
    }

    public BooleanProperty showTitleProperty() {
        return showTitle;
    }

    public void setShowTitle(boolean showTitle) {
        this.showTitle.set(showTitle);
    }

    private final BooleanProperty showIcon =  new SimpleBooleanProperty(this, "showIcon", true);

    public boolean isShowIcon() {
        return showIcon.get();
    }

    public BooleanProperty showIconProperty() {
        return showIcon;
    }

    public void setShowIcon(boolean showIcon) {
        this.showIcon.set(showIcon);
    }

    private ObjectProperty<Node> left;

    public final ObjectProperty<Node> leftProperty() {
        if (left == null) {
            left = new StageDecoration.HeaderBarPositionProperty("left");
        }
        return left;
    }

    public final void setLeft(Node value) {
        leftProperty().set(value);
    }

    public final Node getLeft() {
        return left == null ? null : left.get();
    }

    private ObjectProperty<Node> right;

    public final ObjectProperty<Node> rightProperty() {
        if (right == null) {
            right = new StageDecoration.HeaderBarPositionProperty("right");
        }
        return right;
    }

    public final void setRight(Node value) {
        rightProperty().set(value);
    }

    public final Node getRight() {
        return right == null ? null : right.get();
    }

    private final class HeaderBarPositionProperty extends ObjectPropertyBase<Node> {
        private Node oldValue = null;
        private final String propertyName;
        private boolean isBeingInvalidated;

        HeaderBarPositionProperty(String propertyName) {
            this.propertyName = propertyName;
            getChildren().addListener((ListChangeListener<Node>) c -> {
                if (oldValue == null || isBeingInvalidated) {
                    return;
                }
                while (c.next()) {
                    if (c.wasRemoved()) {
                        List<? extends Node> removed = c.getRemoved();
                        for (Node node : removed) {
                            if (node == oldValue) {
                                oldValue = null; // Do not remove again in invalidated
                                set(null);
                            }
                        }
                    }
                }
            });
        }

        @Override
        protected void invalidated() {
            final List<Node> children = getChildren();
            isBeingInvalidated = true;
            try {
                if (oldValue != null) {
                    children.remove(oldValue);
                }

                final Node value = get();
                this.oldValue = value;

                if (value != null) {
                    children.add(value);
                }
            } finally {
                isBeingInvalidated = false;
            }
        }

        @Override
        public Object getBean() {
            return StageDecoration.this;
        }

        @Override
        public String getName() {
            return propertyName;
        }
    }
}