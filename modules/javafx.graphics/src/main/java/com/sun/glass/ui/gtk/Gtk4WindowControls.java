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

import com.sun.glass.utils.NativeLibLoader;

/**
 * JNI bridge to the native GTK4 window controls rendering library.
 * <p>
 * This class dynamically loads a separate native library ({@code libglassgtk4controls.so})
 * which uses GTK4's {@code GtkWindowControls} widget to render native window control
 * buttons (minimize, maximize, close) to a pixel buffer.
 * <p>
 * The native library is loaded at runtime only if GTK4 is available on the system.
 * If GTK4 is not available, or if the native library cannot be loaded, all methods
 * in this class will indicate that the feature is unavailable.
 * <p>
 * The native library uses {@code dlopen} with {@code RTLD_LOCAL} to load GTK4 symbols,
 * which avoids conflicts with the already-loaded GTK3 in the JavaFX Glass toolkit.
 */
final class Gtk4WindowControls {

    /** Side constant for start (left) side window controls */
    static final int SIDE_START = 0;

    /** Side constant for end (right) side window controls */
    static final int SIDE_END = 1;

    /** Metadata size in the render result array (before pixel data) */
    static final int METADATA_SIZE = 9;

    /** Index constants for metadata in the render result */
    static final int IDX_WIDTH = 0;
    static final int IDX_HEIGHT = 1;
    static final int IDX_BUTTON_COUNT = 2;
    static final int IDX_BUTTON_X0 = 3;
    static final int IDX_BUTTON_X1 = 4;
    static final int IDX_BUTTON_X2 = 5;
    static final int IDX_BUTTON_W0 = 6;
    static final int IDX_BUTTON_W1 = 7;
    static final int IDX_BUTTON_W2 = 8;

    private static final boolean AVAILABLE;

    static {
        boolean available = false;
        try {
            NativeLibLoader.loadLibrary("glassgtk4controls");
            available = nInit();
        } catch (UnsatisfiedLinkError | SecurityException e) {
            // GTK4 native library not available
            if (Boolean.getBoolean("javafx.verbose")) {
                System.err.println("Gtk4WindowControls: native library not available: " + e.getMessage());
            }
        }
        AVAILABLE = available;
    }

    private Gtk4WindowControls() {}

    /**
     * Returns whether GTK4 window controls rendering is available.
     *
     * @return {@code true} if the GTK4 native library was loaded successfully
     *         and GTK4 is available on this system
     */
    static boolean isAvailable() {
        return AVAILABLE;
    }

    /**
     * Gets the metrics (preferred size and button dimensions) of the window controls
     * for the specified side.
     *
     * @param side {@link #SIDE_START} for left side or {@link #SIDE_END} for right side
     * @param decorationLayout the GTK decoration layout string, or {@code null} for default
     * @return an int array {@code [width, height, buttonCount, btn0Width, btn1Width, btn2Width]},
     *         or {@code null} if measurement failed
     */
    static int[] getMetrics(int side, String decorationLayout) {
        if (!AVAILABLE) return null;
        return nGetMetrics(side, decorationLayout);
    }

    /**
     * Renders the window controls to a pixel buffer.
     * <p>
     * The returned array contains metadata followed by pixel data in ARGB pre-multiplied format.
     * The first {@link #METADATA_SIZE} ints are:
     * {@code [width, height, buttonCount, btn0X, btn1X, btn2X, btn0Width, btn1Width, btn2Width]}
     * followed by {@code width * height} ints of pixel data.
     *
     * @param side {@link #SIDE_START} for left side or {@link #SIDE_END} for right side
     * @param decorationLayout the GTK decoration layout string, or {@code null} for default
     * @param hoveredIndex the index of the hovered button (0-2), or -1 for none
     * @param pressed {@code true} if the hovered button is pressed
     * @param focused {@code true} if the window is focused
     * @return an int array with metadata and pixel data, or {@code null} if rendering failed
     */
    static int[] render(int side, String decorationLayout,
                        int hoveredIndex, boolean pressed, boolean focused) {
        if (!AVAILABLE) return null;
        return nRender(side, decorationLayout, hoveredIndex, pressed, focused);
    }

    /**
     * Disposes of native resources used by GTK4 rendering.
     */
    static void dispose() {
        if (AVAILABLE) {
            nDispose();
        }
    }

    /* ========== Native methods ========== */

    /**
     * Initializes the GTK4 subsystem by dynamically loading libgtk-4.so and
     * resolving all required symbols.
     *
     * @return {@code true} if GTK4 was successfully initialized
     */
    private static native boolean nInit();

    /**
     * Gets the native metrics of window controls.
     *
     * @param side 0 for start, 1 for end
     * @param decorationLayout the decoration layout string
     * @return metric data array or null
     */
    private static native int[] nGetMetrics(int side, String decorationLayout);

    /**
     * Renders window controls to a pixel buffer.
     *
     * @param side 0 for start, 1 for end
     * @param decorationLayout the decoration layout string
     * @param hoveredIndex index of hovered button, or -1
     * @param pressed whether the hovered button is pressed
     * @param focused whether the window is focused
     * @return metadata + pixel data array, or null
     */
    private static native int[] nRender(int side, String decorationLayout,
                                         int hoveredIndex, boolean pressed,
                                         boolean focused);

    /**
     * Disposes native resources.
     */
    private static native void nDispose();
}

