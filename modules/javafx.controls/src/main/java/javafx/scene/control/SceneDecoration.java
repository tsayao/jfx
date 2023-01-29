package javafx.scene.control;

import javafx.beans.property.*;
import javafx.scene.Node;
import javafx.scene.control.skin.SceneDecorationSkin;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

public class SceneDecoration extends Control {
    private static final String DEFAULT_STYLE_CLASS = "stage-decoration";
    private final Stage stage;

    public SceneDecoration(Stage stage) {
        this.stage = stage;
        stage.initStyle(StageStyle.TRANSPARENT);
        getStyleClass().setAll(DEFAULT_STYLE_CLASS);
    }

    public SceneDecoration(Stage stage, Node content) {
        this(stage);
        setContent(content);
    }

    @Override
    protected Skin<?> createDefaultSkin() {
        return new SceneDecorationSkin(this, stage);
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