package javafx.scene.control;

import javafx.beans.property.*;
import javafx.css.PseudoClass;
import javafx.geometry.HPos;
import javafx.scene.Node;
import javafx.scene.control.skin.SceneDecorationSkin;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

public class SceneDecoration extends Control {
    private static final String DEFAULT_STYLE_CLASS = "decoration";

    private static final PseudoClass PSEUDO_CLASS_FOCUSED =
            PseudoClass.getPseudoClass("focused");

    private static final PseudoClass PSEUDO_CLASS_MAXIMIZED =
            PseudoClass.getPseudoClass("maximized");

    private final Stage stage;

    public SceneDecoration(Stage stage) {
        this.stage = stage;
        stage.initStyle(StageStyle.TRANSPARENT);
        getStyleClass().setAll(DEFAULT_STYLE_CLASS);

        stage.focusedProperty().addListener((observable, oldValue, newValue)
                                                -> pseudoClassStateChanged(PSEUDO_CLASS_FOCUSED, newValue));

        stage.maximizedProperty().addListener((observable, oldValue, newValue)
                                                -> pseudoClassStateChanged(PSEUDO_CLASS_MAXIMIZED, newValue));
    }

    public SceneDecoration(Stage stage, Node content) {
        this(stage);
        setContent(content);
    }

    @Override
    protected Skin<?> createDefaultSkin() {
        return new SceneDecorationSkin(this, stage);
    }

    private final ObjectProperty<HPos> headerButtonsPosition = new SimpleObjectProperty<>(HPos.RIGHT);

    public HPos getHeaderButtonsPosition() {
        return headerButtonsPosition.get();
    }

    public ObjectProperty<HPos> headerButtonsPositionProperty() {
        return headerButtonsPosition;
    }

    public void setHeaderButtonsPosition(HPos headerButtonsPosition) {
        this.headerButtonsPosition.set(headerButtonsPosition);
    }

    private ObjectProperty<Node> content;

    public final void setContent(Node value) {
        contentProperty().set(value);
    }

    public final Node getContent() {
        return content == null ? null : content.get();
    }

    public final ObjectProperty<Node> contentProperty() {
        if (content == null) {
            content = new SimpleObjectProperty<>(this, "content");
        }
        return content;
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

    private ObjectProperty<Node> headerLeft;

    public final ObjectProperty<Node> headerLeftProperty() {
        if (headerLeft == null) {
            headerLeft = new SimpleObjectProperty<>(this,"left");
        }
        return headerLeft;
    }

    public final void setHeaderLeft(Node value) {
        headerLeftProperty().set(value);
    }

    public final Node getHeaderLeft() {
        return headerLeft == null ? null : headerLeft.get();
    }

    private ObjectProperty<Node> headerRight;

    public final ObjectProperty<Node> headerRightProperty() {
        if (headerRight == null) {
            headerRight = new SimpleObjectProperty<>(this,"right");
        }
        return headerRight;
    }

    public final void setHeaderRight(Node value) {
        headerRightProperty().set(value);
    }

    public final Node getHeaderRight() {
        return headerRight == null ? null : headerRight.get();
    }
}