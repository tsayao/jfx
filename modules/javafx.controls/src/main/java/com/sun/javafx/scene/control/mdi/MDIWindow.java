/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

package com.sun.javafx.scene.control.mdi;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.event.Event;
import javafx.event.EventHandler;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.shape.Rectangle;

/**
 * A window drawn inside an {@link MDIPane}.
 * <p>
 * It has a title bar (drag to move, double-click to maximize), minimize,
 * maximize and close buttons, and can be resized from its edges.
 */
public class MDIWindow extends Region {
    private static final double RESIZE_BORDER = 6;
    private static final double MIN_WIDTH = 120;
    private static final double TITLE_BAR_HEIGHT = 26;

    private final StringProperty title = new SimpleStringProperty(this, "title", "");
    private final BooleanProperty maximized = new SimpleBooleanProperty(this, "maximized");
    private final BooleanProperty minimized = new SimpleBooleanProperty(this, "minimized");
    private final BooleanProperty resizable = new SimpleBooleanProperty(this, "resizable", true);
    private final BooleanProperty active = new SimpleBooleanProperty(this, "active");

    private final HBox titleBar = new HBox(4);
    private final Label titleLabel = new Label();
    private final StackPane contentHolder = new StackPane();
    private final Rectangle contentClip = new Rectangle();

    private MDIPane pane;
    private Node content;
    private boolean positioned;
    private boolean userSized;
    private double normalHeight;
    private EventHandler<Event> onCloseRequest;

    // drag/resize state, in parent coordinates
    private double pressX, pressY, startX, startY, startW, startH;
    private int resizeDir;  // bit mask: 1=W 2=E 4=N 8=S
    private boolean dragging;

    // geometry saved before maximize
    private double restoreX, restoreY, restoreW, restoreH;

    public MDIWindow(String title, Node content) {
        getStyleClass().add("mdi-window");
        this.title.set(title);
        titleLabel.textProperty().bind(this.title);
        titleLabel.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(titleLabel, Priority.ALWAYS);

        Button min = button("–", this::toggleMinimize);
        Button max = button("□", () -> setMaximized(!isMaximized()));
        Button close = button("✕", this::requestClose);
        min.setFocusTraversable(false);
        max.setFocusTraversable(false);
        close.setFocusTraversable(false);
        max.disableProperty().bind(resizable.not());

        titleBar.setAlignment(Pos.CENTER_LEFT);
        titleBar.setPadding(new Insets(0, 4, 0, 8));
        titleBar.getChildren().addAll(titleLabel, min, max, close);
        titleBar.setMinHeight(TITLE_BAR_HEIGHT);
        titleBar.setPrefHeight(TITLE_BAR_HEIGHT);
        titleBar.setMaxHeight(TITLE_BAR_HEIGHT);

        contentHolder.setStyle("-fx-background-color: white;");
        contentHolder.setClip(contentClip);
        contentClip.widthProperty().bind(contentHolder.widthProperty());
        contentClip.heightProperty().bind(contentHolder.heightProperty());
        setContent(content);

        getChildren().addAll(titleBar, contentHolder);

        active.addListener((o, a, b) -> updateStyle());
        updateStyle();
        minimized.addListener((o, a, b) -> {
            contentHolder.setVisible(!b);
            contentHolder.setManaged(!b);
            requestLayout();
            if (pane != null) {
                pane.requestLayout();
            }
        });
        maximized.addListener((o, was, now) -> {
            if (now) {
                restoreX = getLayoutX();
                restoreY = getLayoutY();
                restoreW = getWidth();
                restoreH = userSized ? normalHeight : getHeight();
                setMinimized(false);
            } else {
                userResizeRelocate(restoreX, restoreY, restoreW, restoreH);
            }
            updateStyle();
            if (pane != null) {
                pane.requestLayout();
            }
        });

        addEventFilter(MouseEvent.MOUSE_PRESSED, e -> {
            if (pane != null) {
                pane.activate(this);
            }
        });
        installMouseHandlers();
    }

    private static Button button(String text, Runnable action) {
        Button b = new Button(text);
        b.setStyle("-fx-padding: 0 6 0 6; -fx-background-radius: 3; -fx-font-size: 11;");
        b.setOnAction(e -> action.run());
        return b;
    }

    // ---- properties ---------------------------------------------------

    public final StringProperty titleProperty() { return title; }
    public final String getTitle() { return title.get(); }
    public final void setTitle(String value) { title.set(value); }

    public final BooleanProperty maximizedProperty() { return maximized; }
    public final boolean isMaximized() { return maximized.get(); }
    public final void setMaximized(boolean value) {
        if (value && !isResizable()) {
            return;
        }
        maximized.set(value);
    }

    public final BooleanProperty minimizedProperty() { return minimized; }
    public final boolean isMinimized() { return minimized.get(); }
    public final void setMinimized(boolean value) { minimized.set(value); }

    /** Whether the user can resize or maximize the window. */
    public final BooleanProperty resizableProperty() { return resizable; }
    @Override
    public final boolean isResizable() { return resizable.get(); }
    public final void setResizable(boolean value) { resizable.set(value); }

    public final BooleanProperty activeProperty() { return active; }
    public final boolean isActive() { return active.get(); }
    final void setActive(boolean value) { active.set(value); }

    public final Node getContent() { return content; }
    public final void setContent(Node value) {
        content = value;
        contentHolder.getChildren().setAll(value == null ? new Node[0] : new Node[] { value });
    }

    /**
     * Called when the user presses the close button. Consume the event
     * to veto closing. By default the window is removed from its pane.
     */
    public final void setOnCloseRequest(EventHandler<Event> handler) { onCloseRequest = handler; }

    public final MDIPane getPane() { return pane; }

    // ---- package/internal ---------------------------------------------

    final void setPane(MDIPane p) { pane = p; }
    final boolean isPositioned() { return positioned; }
    final boolean isUserSized() { return userSized; }
    final double getTitleBarHeight() { return TITLE_BAR_HEIGHT + 2; }

    @Override
    public void relocate(double x, double y) {
        positioned = true;
        super.relocate(x, y);
    }

    final double getNormalHeight() { return normalHeight; }

    /** Sets bounds that stick, i.e. that the pane keeps instead of using the preferred size. */
    final void userResizeRelocate(double x, double y, double w, double h) {
        userSized = true;
        normalHeight = h;
        positioned = true;
        resizeRelocate(x, y, w, h);
    }

    public void close() {
        if (pane != null) {
            pane.removeWindow(this);
        }
    }

    private void requestClose() {
        if (onCloseRequest != null) {
            Event ev = new Event(Event.ANY);
            onCloseRequest.handle(ev);
            if (ev.isConsumed()) {
                return;
            }
        }
        close();
    }

    private void toggleMinimize() {
        setMinimized(!isMinimized());
        if (pane != null && isMinimized() && pane.getActiveWindow() == this) {
            pane.activate(null);
        }
    }

    private void updateStyle() {
        String border = isActive() ? "#3b6fb6" : "#8a9099";
        String bar = isActive() ? "#3b6fb6" : "#aeb4bc";
        setStyle("-fx-background-color: " + border + "; -fx-background-insets: 0; "
            + "-fx-padding: 1; -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.35), "
            + (isActive() ? "14" : "6") + ", 0, 0, 2);");
        titleBar.setStyle("-fx-background-color: " + bar + ";");
        titleLabel.setStyle("-fx-text-fill: white; -fx-font-weight: bold;");
    }

    // ---- layout -------------------------------------------------------

    @Override
    protected double computePrefWidth(double height) {
        double c = content == null ? 0 : content.prefWidth(-1);
        return Math.max(MIN_WIDTH, c + 2);
    }

    @Override
    protected double computePrefHeight(double width) {
        double c = content == null ? 0 : content.prefHeight(-1);
        return getTitleBarHeight() + (minimized.get() ? 0 : c) + 1;
    }

    @Override
    protected double computeMinWidth(double height) {
        return MIN_WIDTH;
    }

    @Override
    protected double computeMinHeight(double width) {
        return getTitleBarHeight();
    }

    @Override
    protected void layoutChildren() {
        double w = getWidth() - 2;
        double th = TITLE_BAR_HEIGHT;
        titleBar.resizeRelocate(1, 1, w, th);
        if (!isMinimized()) {
            contentHolder.resizeRelocate(1, 1 + th, w, Math.max(0, getHeight() - 2 - th));
        }
    }

    // ---- mouse: move & resize ------------------------------------------

    private int directionAt(MouseEvent e) {
        if (!isResizable() || isMaximized()) {
            return 0;
        }
        double x = e.getX(), y = e.getY(), w = getWidth(), h = getHeight();
        int d = 0;
        if (x < RESIZE_BORDER) d |= 1;
        else if (x > w - RESIZE_BORDER) d |= 2;
        if (!isMinimized()) {
            if (y < RESIZE_BORDER) d |= 4;
            else if (y > h - RESIZE_BORDER) d |= 8;
        }
        return d;
    }

    private static Cursor cursorFor(int d) {
        return switch (d) {
            case 1 -> Cursor.W_RESIZE;
            case 2 -> Cursor.E_RESIZE;
            case 4 -> Cursor.N_RESIZE;
            case 8 -> Cursor.S_RESIZE;
            case 5 -> Cursor.NW_RESIZE;
            case 6 -> Cursor.NE_RESIZE;
            case 9 -> Cursor.SW_RESIZE;
            case 10 -> Cursor.SE_RESIZE;
            default -> Cursor.DEFAULT;
        };
    }

    private void installMouseHandlers() {
        addEventFilter(MouseEvent.MOUSE_MOVED, e -> setCursor(cursorFor(directionAt(e))));
        addEventFilter(MouseEvent.MOUSE_EXITED, e -> {
            if (!dragging) {
                setCursor(Cursor.DEFAULT);
            }
        });

        addEventFilter(MouseEvent.MOUSE_PRESSED, e -> {
            if (!e.isPrimaryButtonDown() || getParent() == null) {
                return;
            }
            resizeDir = directionAt(e);
            boolean onTitle = resizeDir == 0 && !isMaximized()
                && titleBar.getBoundsInParent().contains(e.getX(), e.getY())
                && !(e.getTarget() instanceof Button)
                && !(e.getTarget() instanceof Node n && isInsideButton(n));
            dragging = resizeDir != 0 || onTitle;
            if (!dragging) {
                return;
            }
            var p = getParent().sceneToLocal(e.getSceneX(), e.getSceneY());
            pressX = p.getX();
            pressY = p.getY();
            startX = getLayoutX();
            startY = getLayoutY();
            startW = getWidth();
            startH = getHeight();
            if (resizeDir != 0) {
                e.consume();
            }
        });

        addEventFilter(MouseEvent.MOUSE_DRAGGED, e -> {
            if (!dragging || getParent() == null) {
                return;
            }
            var p = getParent().sceneToLocal(e.getSceneX(), e.getSceneY());
            double dx = p.getX() - pressX;
            double dy = p.getY() - pressY;
            if (resizeDir == 0) {
                relocate(startX + dx, Math.max(0, startY + dy));
            } else {
                double x = startX, y = startY, w = startW, h = startH;
                double minH = getTitleBarHeight() + 20;
                if ((resizeDir & 2) != 0) w = Math.max(MIN_WIDTH, startW + dx);
                if ((resizeDir & 1) != 0) {
                    w = Math.max(MIN_WIDTH, startW - dx);
                    x = startX + startW - w;
                }
                if ((resizeDir & 8) != 0) h = Math.max(minH, startH + dy);
                if ((resizeDir & 4) != 0) {
                    h = Math.max(minH, startH - dy);
                    y = startY + startH - h;
                }
                userResizeRelocate(x, y, w, h);
            }
            e.consume();
        });

        addEventFilter(MouseEvent.MOUSE_RELEASED, e -> {
            dragging = false;
            resizeDir = 0;
        });

        titleBar.addEventHandler(MouseEvent.MOUSE_CLICKED, e -> {
            if (e.getClickCount() == 2 && !(e.getTarget() instanceof Button)
                    && !(e.getTarget() instanceof Node n && isInsideButton(n))) {
                setMaximized(!isMaximized());
            }
        });
    }

    private boolean isInsideButton(Node n) {
        while (n != null && n != titleBar) {
            if (n instanceof Button) {
                return true;
            }
            n = n.getParent();
        }
        return false;
    }
}
