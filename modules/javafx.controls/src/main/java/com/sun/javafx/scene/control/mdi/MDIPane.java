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

import java.util.ArrayList;
import java.util.List;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.collections.ObservableList;
import javafx.scene.Node;
import javafx.scene.layout.Region;
import javafx.scene.shape.Rectangle;

/**
 * A container that hosts {@link MDIWindow}s (a "multiple document interface").
 * Child windows are drawn in the scene graph, are clipped to this pane, and
 * can be moved, resized, minimized, maximized and closed by the user.
 * <p>
 * Windows are not laid out by the pane: each one keeps its own position and
 * size, like in a {@code Pane}. Use {@link #cascade()} and {@link #tile()}
 * to arrange them.
 */
public class MDIPane extends Region {
    private static final double CASCADE_OFFSET = 28;

    private final ReadOnlyObjectWrapper<MDIWindow> activeWindow = new ReadOnlyObjectWrapper<>(this, "activeWindow");
    private final Rectangle clip = new Rectangle();

    public MDIPane() {
        getStyleClass().add("mdi-pane");
        setStyle("-fx-background-color: #d8dde3;");
        setClip(clip);
        clip.widthProperty().bind(widthProperty());
        clip.heightProperty().bind(heightProperty());
    }

    /** The window that currently has focus, or {@code null}. */
    public final ReadOnlyObjectProperty<MDIWindow> activeWindowProperty() {
        return activeWindow.getReadOnlyProperty();
    }

    public final MDIWindow getActiveWindow() {
        return activeWindow.get();
    }

    /** Returns the windows in this pane, back-to-front. */
    public final List<MDIWindow> getWindows() {
        List<MDIWindow> list = new ArrayList<>();
        for (Node n : getChildren()) {
            if (n instanceof MDIWindow w) {
                list.add(w);
            }
        }
        return list;
    }

    /** Adds a window and activates it. A window without a location is placed in cascade. */
    public void addWindow(MDIWindow window) {
        if (window.getParent() == this) {
            activate(window);
            return;
        }
        if (!window.isPositioned()) {
            int n = getWindows().size();
            window.relocate(CASCADE_OFFSET * (n % 10) + 8, CASCADE_OFFSET * (n % 10) + 8);
        }
        window.setPane(this);
        getChildren().add(window);
        activate(window);
    }

    /** Removes the window from this pane. */
    public void removeWindow(MDIWindow window) {
        if (window.getParent() != this) {
            return;
        }
        getChildren().remove(window);
        window.setPane(null);
        if (getActiveWindow() == window) {
            activeWindow.set(null);
            List<MDIWindow> rest = getWindows();
            for (int i = rest.size() - 1; i >= 0; i--) {
                if (!rest.get(i).isMinimized()) {
                    activate(rest.get(i));
                    break;
                }
            }
        }
    }

    /** Brings the window to front and marks it active. */
    public void activate(MDIWindow window) {
        if (window != null && window.getParent() == this) {
            window.toFront();
        }
        MDIWindow old = activeWindow.get();
        if (old == window) {
            return;
        }
        activeWindow.set(window);
        if (old != null) {
            old.setActive(false);
        }
        if (window != null) {
            window.setActive(true);
        }
    }

    /** Arranges the non-minimized windows in a staggered stack. */
    public void cascade() {
        int i = 0;
        for (MDIWindow w : getWindows()) {
            if (w.isMaximized()) {
                w.setMaximized(false);
            }
            w.relocate(8 + CASCADE_OFFSET * (i % 10), 8 + CASCADE_OFFSET * (i % 10));
            i++;
        }
    }

    /** Arranges the non-minimized windows in a grid that fills the pane. */
    public void tile() {
        List<MDIWindow> list = new ArrayList<>();
        for (MDIWindow w : getWindows()) {
            if (w.isMaximized()) {
                w.setMaximized(false);
            }
            if (!w.isMinimized()) {
                list.add(w);
            }
        }
        if (list.isEmpty()) {
            return;
        }
        int cols = (int) Math.ceil(Math.sqrt(list.size()));
        int rows = (int) Math.ceil(list.size() / (double) cols);
        double cw = getWidth() / cols;
        double ch = getHeight() / rows;
        for (int i = 0; i < list.size(); i++) {
            MDIWindow w = list.get(i);
            w.userResizeRelocate((i % cols) * cw, (i / cols) * ch, cw, ch);
        }
    }

    @Override
    protected void layoutChildren() {
        for (MDIWindow w : getWindows()) {
            if (w.isMaximized()) {
                w.resizeRelocate(0, 0, getWidth(), getHeight());
            } else {
                double ww = w.isUserSized() ? w.getWidth() : w.prefWidth(-1);
                double h;
                if (w.isMinimized()) {
                    h = w.getTitleBarHeight();
                } else {
                    h = w.isUserSized() ? w.getNormalHeight() : w.prefHeight(-1);
                }
                w.resize(ww, h);
            }
        }
    }
}
