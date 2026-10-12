package mdi;

import com.sun.javafx.scene.control.mdi.MDIPane;
import com.sun.javafx.scene.control.mdi.MDIWindow;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.TextArea;
import javafx.scene.control.ToolBar;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;

/**
 * Run with:
 * java --module-path build/sdk/lib --add-modules javafx.controls \
 *   --add-exports javafx.controls/com.sun.javafx.scene.control.mdi=ALL-UNNAMED tests/manual/mdi/MDITest.java
 */
public class MDITest extends Application {
    private int counter;

    @Override
    public void start(Stage stage) {
        MDIPane mdi = new MDIPane();
        Button add = new Button("New window");
        add.setOnAction(e -> {
            TextArea ta = new TextArea("Document " + (++counter));
            ta.setPrefSize(320, 220);
            mdi.addWindow(new MDIWindow("Document " + counter, ta));
        });
        Button cascade = new Button("Cascade");
        cascade.setOnAction(e -> mdi.cascade());
        Button tile = new Button("Tile");
        tile.setOnAction(e -> mdi.tile());

        for (int i = 0; i < 2; i++) {
            add.fire();
        }

        BorderPane root = new BorderPane(mdi, new ToolBar(add, cascade, tile), null, null, null);
        stage.setScene(new Scene(root, 900, 600));
        stage.setTitle("MDI test");
        stage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
