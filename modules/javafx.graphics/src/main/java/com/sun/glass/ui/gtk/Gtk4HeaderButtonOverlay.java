/*
 * Copyright (c) 2025, 2026, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.glass.ui.gtk;

import com.sun.glass.events.MouseEvent;
import com.sun.glass.ui.HeaderButtonMetrics;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.value.ObservableValue;
import javafx.event.Event;
import javafx.geometry.Dimension2D;
import javafx.scene.Scene;
import javafx.scene.image.ImageView;
import javafx.scene.image.PixelFormat;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.HeaderBar;
import javafx.scene.layout.HeaderButtonType;
import javafx.scene.layout.Region;
import javafx.stage.Stage;
import javafx.stage.WindowEvent;
import javafx.util.Subscription;

/**
 * A header button overlay that uses GTK4's native {@code GtkWindowControls} widget
 * to render window control buttons (minimize, maximize, close) with the native
 * desktop appearance.
 * <p>
 * This overlay renders the GTK4 controls offscreen and displays them as a
 * {@link WritableImage} in the JavaFX scene graph. It supports mouse interaction
 * states (hover, pressed) and window states (focused, maximized).
 * <p>
 * This class is used as a replacement for the CSS-based {@code HeaderButtonOverlay}
 * when GTK4 is available on the system, providing pixel-perfect native appearance.
 *
 * @implNote This overlay creates a new GTK4 window for each render call to capture
 *           the buttons in the correct state. The rendering is done on the JavaFX
 *           application thread.
 */
final class Gtk4HeaderButtonOverlay extends Region {

    private static final String DEFAULT_DECORATION_LAYOUT = "close,minimize,maximize:";

    private final ObjectProperty<HeaderButtonMetrics> metrics = new SimpleObjectProperty<>(
        this, "metrics", HeaderButtonMetrics.EMPTY);

    private final DoubleProperty prefButtonHeight = new SimpleDoubleProperty(
        this, "prefButtonHeight", HeaderBar.USE_DEFAULT_SIZE) {
            @Override
            protected void invalidated() {
                requestRender();
                requestLayout();
            }
        };

    private final ImageView imageView = new ImageView();
    private final Subscription subscriptions;
    private final boolean rightToLeft;
    private final boolean utility;
    private final boolean modalOrOwned;

    // Cached render state
    private WritableImage currentImage;
    private int cachedWidth;
    private int cachedHeight;
    private int buttonCount;
    private int[] buttonXPositions = new int[3]; // X position of each button
    private int[] buttonWidths = new int[3];     // Width of each button
    private String decorationLayout;

    // Mouse interaction state
    private int hoveredButtonIndex = -1;
    private boolean mousePressed = false;
    private boolean windowFocused = true;
    private boolean windowMaximized = false;
    private javafx.scene.Node buttonAtMouseDown = null;

    Gtk4HeaderButtonOverlay(ObservableValue<String> stylesheet,
                            boolean modalOrOwned, boolean utility, boolean rightToLeft) {
        this.modalOrOwned = modalOrOwned;
        this.utility = utility;
        this.rightToLeft = rightToLeft;
        this.decorationLayout = getDecorationLayoutForDesktop();

        imageView.setPreserveRatio(false);
        imageView.setSmooth(false);
        getChildren().add(imageView);

        var stage = sceneProperty()
            .flatMap(Scene::windowProperty)
            .map(w -> w instanceof Stage ? (Stage)w : null);

        var focusedSubscription = stage
            .flatMap(Stage::focusedProperty)
            .orElse(true)
            .subscribe(this::onFocusedChanged);

        var maximizedSubscription = stage
            .flatMap(Stage::maximizedProperty)
            .orElse(false)
            .subscribe(this::onMaximizedChanged);

        var themeSubscription = stylesheet.subscribe(s -> {
            // Re-render when the theme changes
            requestRender();
        });

        subscriptions = Subscription.combine(
            focusedSubscription,
            maximizedSubscription,
            themeSubscription);

        // Do initial measurement and render
        updateMetricsAndRender();
    }

    void dispose() {
        subscriptions.unsubscribe();
    }

    ReadOnlyObjectProperty<HeaderButtonMetrics> metricsProperty() {
        return metrics;
    }

    DoubleProperty prefButtonHeightProperty() {
        return prefButtonHeight;
    }

    /**
     * Classifies and returns the button type at the specified coordinate, or returns
     * {@code null} if the specified coordinate does not intersect a button.
     *
     * @param x the X coordinate, in pixels relative to the window
     * @param y the Y coordinate, in pixels relative to the window
     * @return the {@code HeaderButtonType} or {@code null}
     */
    HeaderButtonType buttonAt(double x, double y) {
        if (cachedHeight <= 0 || cachedWidth <= 0 || buttonCount <= 0) {
            return null;
        }

        // Check if y is within the button area
        double localY = y - getLayoutY();
        if (localY < 0 || localY >= cachedHeight) {
            return null;
        }

        // Check which button the x coordinate is over
        double localX = x - getLayoutX();
        for (int i = 0; i < buttonCount; i++) {
            if (localX >= buttonXPositions[i] && localX < buttonXPositions[i] + buttonWidths[i]) {
                return mapButtonIndex(i);
            }
        }

        return null;
    }

    /**
     * Handles the specified mouse event.
     *
     * @param type the event type
     * @param button the button type
     * @param x the X coordinate, in pixels relative to the window
     * @param y the Y coordinate, in pixels relative to the window
     * @return {@code true} if the event was handled, {@code false} otherwise
     */
    boolean handleMouseEvent(int type, int button, double x, double y) {
        HeaderButtonType buttonType = buttonAt(x, y);
        int newHoveredIndex = buttonType != null ? getButtonIndex(buttonType) : -1;

        if (type == MouseEvent.ENTER || type == MouseEvent.MOVE || type == MouseEvent.DRAG) {
            if (newHoveredIndex != hoveredButtonIndex) {
                hoveredButtonIndex = newHoveredIndex;
                if (!mousePressed) {
                    requestRender();
                }
            }
        } else if (type == MouseEvent.EXIT) {
            hoveredButtonIndex = -1;
            mousePressed = false;
            buttonAtMouseDown = null;
            requestRender();
        } else if (type == MouseEvent.DOWN && button == MouseEvent.BUTTON_LEFT && buttonType != null) {
            mousePressed = true;
            buttonAtMouseDown = this; // marker
            requestRender();
        } else if (type == MouseEvent.UP && button == MouseEvent.BUTTON_LEFT) {
            boolean releasedOnSameButton = (mousePressed && hoveredButtonIndex == newHoveredIndex
                                            && buttonAtMouseDown != null);
            mousePressed = false;
            buttonAtMouseDown = null;
            requestRender();

            if (releasedOnSameButton && buttonType != null) {
                executeButtonAction(buttonType);
            }
        }

        if (type == MouseEvent.ENTER || type == MouseEvent.EXIT) {
            return false;
        }

        return buttonType != null || buttonAtMouseDown != null;
    }

    private void executeButtonAction(HeaderButtonType buttonType) {
        Scene scene = getScene();
        if (scene == null || !(scene.getWindow() instanceof Stage stage)) {
            return;
        }

        switch (buttonType) {
            case ICONIFY -> stage.setIconified(true);
            case MAXIMIZE -> stage.setMaximized(!stage.isMaximized());
            case CLOSE -> Event.fireEvent(stage, new WindowEvent(stage, WindowEvent.WINDOW_CLOSE_REQUEST));
        }
    }

    private void onFocusedChanged(boolean focused) {
        if (windowFocused != focused) {
            windowFocused = focused;
            requestRender();
        }
    }

    private void onMaximizedChanged(boolean maximized) {
        if (windowMaximized != maximized) {
            windowMaximized = maximized;
            requestRender();
        }
    }

    private void requestRender() {
        updateMetricsAndRender();
    }

    /**
     * Performs the actual GTK4 rendering and updates the image.
     */
    private void updateMetricsAndRender() {
        int side = getSide();

        // Render with current state
        int[] result = Gtk4WindowControls.render(
            side, decorationLayout,
            hoveredButtonIndex, mousePressed, windowFocused);

        if (result == null || result.length < Gtk4WindowControls.METADATA_SIZE) {
            return;
        }

        // Extract metadata
        cachedWidth = result[Gtk4WindowControls.IDX_WIDTH];
        cachedHeight = result[Gtk4WindowControls.IDX_HEIGHT];
        buttonCount = result[Gtk4WindowControls.IDX_BUTTON_COUNT];
        buttonXPositions[0] = result[Gtk4WindowControls.IDX_BUTTON_X0];
        buttonXPositions[1] = result[Gtk4WindowControls.IDX_BUTTON_X1];
        buttonXPositions[2] = result[Gtk4WindowControls.IDX_BUTTON_X2];
        buttonWidths[0] = result[Gtk4WindowControls.IDX_BUTTON_W0];
        buttonWidths[1] = result[Gtk4WindowControls.IDX_BUTTON_W1];
        buttonWidths[2] = result[Gtk4WindowControls.IDX_BUTTON_W2];

        if (cachedWidth <= 0 || cachedHeight <= 0) {
            return;
        }

        // Extract pixel data
        int pixelCount = cachedWidth * cachedHeight;
        int expectedLength = Gtk4WindowControls.METADATA_SIZE + pixelCount;
        if (result.length < expectedLength) {
            return;
        }

        // Create or reuse the WritableImage
        if (currentImage == null ||
            (int)currentImage.getWidth() != cachedWidth ||
            (int)currentImage.getHeight() != cachedHeight) {
            currentImage = new WritableImage(cachedWidth, cachedHeight);
            imageView.setImage(currentImage);
        }

        // Write pixel data to the image
        currentImage.getPixelWriter().setPixels(
            0, 0, cachedWidth, cachedHeight,
            PixelFormat.getIntArgbPreInstance(),
            result, Gtk4WindowControls.METADATA_SIZE,
            cachedWidth);

        // Apply preferred button height scaling if needed
        double prefHeight = prefButtonHeight.get();
        double displayHeight = prefHeight >= 0 ? prefHeight : cachedHeight;
        double scale = displayHeight / cachedHeight;
        double displayWidth = cachedWidth * scale;

        imageView.setFitWidth(displayWidth);
        imageView.setFitHeight(displayHeight);

        // Update metrics
        updateMetrics(displayWidth, displayHeight);

        // Request layout
        requestLayout();
    }

    private void updateMetrics(double width, double height) {
        var empty = new Dimension2D(0, 0);
        var size = new Dimension2D(width, height);

        boolean isLeft = isLeftSide();
        HeaderButtonMetrics newMetrics = isLeft
            ? new HeaderButtonMetrics(size, empty, height)
            : new HeaderButtonMetrics(empty, size, height);

        if (!newMetrics.equals(metrics.get())) {
            metrics.set(newMetrics);
        }
    }

    @Override
    protected void layoutChildren() {
        if (currentImage == null) return;

        double width = getWidth();
        boolean isLeft = isLeftSide();

        double imageWidth = imageView.getFitWidth();
        double x = isLeft ? 0 : width - imageWidth;

        imageView.setLayoutX(x);
        imageView.setLayoutY(0);
    }

    @Override
    public boolean usesMirroring() {
        return false;
    }

    /**
     * Determines the GTK4 pack side based on desktop and layout configuration.
     */
    private int getSide() {
        // For typical GNOME layouts, buttons are on the right (PACK_END)
        // For Ubuntu/Unity style, buttons may be on the left (PACK_START)
        boolean isLeft = isLeftSide();
        return isLeft ? Gtk4WindowControls.SIDE_START : Gtk4WindowControls.SIDE_END;
    }

    private boolean isLeftSide() {
        // Parse the decoration layout to determine button placement.
        // In GTK decoration layout format: "buttons_before_colon:buttons_after_colon"
        // Buttons before the colon are on the left, after on the right.
        if (decorationLayout != null) {
            int colonIdx = decorationLayout.indexOf(':');
            if (colonIdx >= 0) {
                String leftPart = decorationLayout.substring(0, colonIdx);
                String rightPart = decorationLayout.substring(colonIdx + 1);
                // If there are button names on the left and none on the right, it's left-side
                boolean hasLeft = !leftPart.trim().isEmpty();
                boolean hasRight = !rightPart.trim().isEmpty();
                if (hasLeft && !hasRight) return true;
                if (!hasLeft && hasRight) return false;
                // Both sides have buttons - determine which side has the main controls
                // (close is the primary indicator)
                if (leftPart.contains("close")) return true;
            }
        }
        // Default: right side (most common)
        return rightToLeft;
    }

    /**
     * Maps a button index (as returned by the native code, which iterates
     * buttons in layout order) to a {@link HeaderButtonType}.
     */
    private HeaderButtonType mapButtonIndex(int index) {
        if (utility) {
            return HeaderButtonType.CLOSE;
        }

        // The button order depends on the decoration layout.
        // For standard GNOME (":minimize,maximize,close"), the buttons are:
        //   index 0 = minimize, index 1 = maximize, index 2 = close
        // For Ubuntu old-style ("close,minimize,maximize:"):
        //   index 0 = close, index 1 = minimize, index 2 = maximize
        // We parse the layout to determine the order.
        String[] buttons = parseButtonOrder();
        if (index >= 0 && index < buttons.length) {
            return switch (buttons[index].trim().toLowerCase()) {
                case "close" -> HeaderButtonType.CLOSE;
                case "minimize" -> HeaderButtonType.ICONIFY;
                case "maximize" -> HeaderButtonType.MAXIMIZE;
                default -> null;
            };
        }

        // Fallback: assume standard order
        return switch (index) {
            case 0 -> HeaderButtonType.ICONIFY;
            case 1 -> HeaderButtonType.MAXIMIZE;
            case 2 -> HeaderButtonType.CLOSE;
            default -> null;
        };
    }

    /**
     * Gets the index of a button type in the current layout order.
     */
    private int getButtonIndex(HeaderButtonType type) {
        String[] buttons = parseButtonOrder();
        String name = switch (type) {
            case ICONIFY -> "minimize";
            case MAXIMIZE -> "maximize";
            case CLOSE -> "close";
        };

        for (int i = 0; i < buttons.length; i++) {
            if (buttons[i].trim().equalsIgnoreCase(name)) {
                return i;
            }
        }

        // Fallback
        return switch (type) {
            case ICONIFY -> 0;
            case MAXIMIZE -> 1;
            case CLOSE -> 2;
        };
    }

    private String[] parseButtonOrder() {
        if (decorationLayout == null || decorationLayout.isEmpty()) {
            return new String[] { "minimize", "maximize", "close" };
        }

        // Get the side we're rendering
        boolean isLeft = isLeftSide();
        int colonIdx = decorationLayout.indexOf(':');
        String buttonsPart;

        if (colonIdx >= 0) {
            buttonsPart = isLeft
                ? decorationLayout.substring(0, colonIdx)
                : decorationLayout.substring(colonIdx + 1);
        } else {
            buttonsPart = decorationLayout;
        }

        if (buttonsPart.trim().isEmpty()) {
            return new String[] { "minimize", "maximize", "close" };
        }

        return buttonsPart.split(",");
    }

    /**
     * Determines the decoration layout based on the current desktop environment.
     * GTK4 reads this from GSettings automatically, but we need it for button
     * mapping. Returns a default value if we can't determine it.
     */
    private static String getDecorationLayoutForDesktop() {
        // Try to read from GSettings via the gsettings command
        try {
            Process process = new ProcessBuilder(
                "gsettings", "get", "org.gnome.desktop.wm.preferences", "button-layout")
                .start();

            try (var reader = process.inputReader()) {
                if (process.waitFor(2, java.util.concurrent.TimeUnit.SECONDS)
                    && process.exitValue() == 0) {
                    String line = reader.readLine();
                    if (line != null) {
                        // gsettings returns values quoted, e.g., "'close,minimize,maximize:'"
                        line = line.trim().replace("'", "").replace("\"", "");
                        if (!line.isEmpty()) {
                            return line;
                        }
                    }
                }
            }
        } catch (Exception ignored) {
            // Fall through to default
        }

        // Default layout for GNOME (buttons on the right)
        return DEFAULT_DECORATION_LAYOUT;
    }
}

