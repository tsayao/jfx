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

/**
 * GTK4 native header button rendering.
 *
 * This file dynamically loads libgtk-4.so at runtime using dlopen/dlsym
 * (with RTLD_LOCAL to avoid conflicts with the already-loaded GTK3).
 * It uses GtkWindowControls to render native window control buttons to a
 * Cairo surface, which is then returned as pixel data for JavaFX to display.
 *
 * The approach:
 * 1. Create a minimal offscreen GTK4 window with a GtkWindowControls widget
 * 2. Use gtk_widget_snapshot() + GskRenderer to render to a GdkTexture
 * 3. Download the texture pixels via gdk_texture_download()
 * 4. Return the ARGB pixel data to Java
 *
 * Since GTK4 doesn't easily support running alongside GTK3 in the same process,
 * we use an alternative approach: render using Cairo surfaces directly with
 * gtk_snapshot + GskRenderNode rendering.
 *
 * Minimum GTK4 version targeted: 4.0 (uses only base GTK4 API)
 */

#include <jni.h>
#include <dlfcn.h>
#include <string.h>
#include <stdlib.h>
#include <stdio.h>

/* Forward declarations for GTK4/GDK4/GSK types to avoid requiring GTK4 headers at build time */
typedef unsigned int guint;
typedef int gint;
typedef int gboolean;
typedef char gchar;
typedef void* gpointer;
typedef unsigned long gsize;
typedef unsigned int guint32;
typedef float gfloat;

/* GLib/GObject basics */
typedef struct _GObject GObject;
typedef struct _GTypeInstance GTypeInstance;
typedef unsigned long GType;

/* GTK4 types */
typedef struct _GtkWidget GtkWidget;
typedef struct _GtkWindow GtkWindow;
typedef struct _GtkWindowControls GtkWindowControls;
typedef struct _GtkHeaderBar GtkHeaderBar;
typedef struct _GtkSnapshot GtkSnapshot;
typedef struct _GtkSettings GtkSettings;

/* GDK4 types */
typedef struct _GdkDisplay GdkDisplay;
typedef struct _GdkSurface GdkSurface;
typedef struct _GdkTexture GdkTexture;
typedef struct _GdkPaintable GdkPaintable;

/* GSK types */
typedef struct _GskRenderNode GskRenderNode;
typedef struct _GskRenderer GskRenderer;

/* Cairo types */
typedef struct _cairo_surface cairo_surface_t;
typedef struct _cairo cairo_t;

/* Graphene types */
typedef struct {
    float origin_x, origin_y;
    float size_width, size_height;
} graphene_rect_t;

/* GtkPackType enum */
typedef enum {
    GTK_PACK_START = 0,
    GTK_PACK_END = 1
} GtkPackType;

/* GtkStateFlags enum */
typedef enum {
    GTK_STATE_FLAG_NORMAL = 0,
    GTK_STATE_FLAG_ACTIVE = 1 << 0,
    GTK_STATE_FLAG_PRELIGHT = 1 << 1,
    GTK_STATE_FLAG_SELECTED = 1 << 2,
    GTK_STATE_FLAG_INSENSITIVE = 1 << 3,
    GTK_STATE_FLAG_INCONSISTENT = 1 << 4,
    GTK_STATE_FLAG_FOCUSED = 1 << 5,
    GTK_STATE_FLAG_BACKDROP = 1 << 6
} GtkStateFlags;

/* GtkOrientation enum */
typedef enum {
    GTK_ORIENTATION_HORIZONTAL = 0,
    GTK_ORIENTATION_VERTICAL = 1
} GtkOrientation;

/* GdkMemoryFormat - we want ARGB32 premultiplied for JavaFX */
typedef enum {
    GDK_MEMORY_B8G8R8A8_PREMULTIPLIED = 0,
    GDK_MEMORY_A8R8G8B8_PREMULTIPLIED = 1,
    GDK_MEMORY_R8G8B8A8_PREMULTIPLIED = 2,
    GDK_MEMORY_B8G8R8A8 = 3,
    GDK_MEMORY_A8R8G8B8 = 4,
    GDK_MEMORY_R8G8B8A8 = 5,
    GDK_MEMORY_A8B8G8R8 = 6,
    GDK_MEMORY_R8G8B8 = 7,
    GDK_MEMORY_B8G8R8 = 8
} GdkMemoryFormat;

/* Requisition for widget measurement */
typedef struct {
    int width;
    int height;
} GtkRequisition;

/* Allocation for widget layout */
typedef struct {
    int x;
    int y;
    int width;
    int height;
} GtkAllocation;

#ifndef TRUE
#define TRUE 1
#endif
#ifndef FALSE
#define FALSE 0
#endif
#ifndef NULL
#define NULL ((void*)0)
#endif

/* ========== Function pointer declarations for dynamically loaded GTK4 symbols ========== */

/* GLib/GObject */
static void (*fp_g_object_unref)(gpointer object);
static void (*fp_g_object_ref)(gpointer object);
static void (*fp_g_free)(gpointer mem);

/* GTK4 initialization */
static void (*fp_gtk_init)(void);

/* GtkWindow - use GtkWidget* for all params since types are opaque forward decls */
static GtkWidget* (*fp_gtk_window_new)(void);
static void (*fp_gtk_window_set_decorated)(GtkWidget *window, gboolean setting);
static void (*fp_gtk_window_set_child)(GtkWidget *window, GtkWidget *child);
static void (*fp_gtk_window_set_deletable)(GtkWidget *window, gboolean setting);

/* GtkWindowControls */
static GtkWidget* (*fp_gtk_window_controls_new)(GtkPackType side);
static void (*fp_gtk_window_controls_set_side)(GtkWidget *self, GtkPackType side);
static void (*fp_gtk_window_controls_set_decoration_layout)(GtkWidget *self, const char *layout);

/* GtkHeaderBar */
static GtkWidget* (*fp_gtk_header_bar_new)(void);
static void (*fp_gtk_header_bar_set_show_title_buttons)(GtkWidget *bar, gboolean setting);
static void (*fp_gtk_header_bar_set_decoration_layout)(GtkWidget *bar, const char *layout);

/* GtkWidget */
static void (*fp_gtk_widget_realize)(GtkWidget *widget);
static void (*fp_gtk_widget_show)(GtkWidget *widget);
static void (*fp_gtk_widget_set_visible)(GtkWidget *widget, gboolean visible);
static void (*fp_gtk_widget_measure)(GtkWidget *widget, GtkOrientation orientation,
                                     int for_size, int *minimum, int *natural,
                                     int *minimum_baseline, int *natural_baseline);
static void (*fp_gtk_widget_set_size_request)(GtkWidget *widget, int width, int height);
static void (*fp_gtk_widget_get_preferred_size)(GtkWidget *widget, GtkRequisition *minimum,
                                                GtkRequisition *natural);
static GtkSnapshot* (*fp_gtk_widget_get_snapshot)(GtkWidget *widget);
static void (*fp_gtk_widget_snapshot_child)(GtkWidget *widget, GtkWidget *child, GtkSnapshot *snapshot);
static void (*fp_gtk_widget_queue_draw)(GtkWidget *widget);
static void (*fp_gtk_widget_set_state_flags)(GtkWidget *widget, GtkStateFlags flags, gboolean clear);
static void (*fp_gtk_widget_unset_state_flags)(GtkWidget *widget, GtkStateFlags flags);
static GtkWidget* (*fp_gtk_widget_get_first_child)(GtkWidget *widget);
static GtkWidget* (*fp_gtk_widget_get_next_sibling)(GtkWidget *widget);
static void (*fp_gtk_widget_get_allocation)(GtkWidget *widget, GtkAllocation *allocation);
static void (*fp_gtk_widget_add_css_class)(GtkWidget *widget, const char *css_class);
static void (*fp_gtk_widget_remove_css_class)(GtkWidget *widget, const char *css_class);
static int (*fp_gtk_widget_get_width)(GtkWidget *widget);
static int (*fp_gtk_widget_get_height)(GtkWidget *widget);

/* GtkSnapshot */
static GtkSnapshot* (*fp_gtk_snapshot_new)(void);
static GskRenderNode* (*fp_gtk_snapshot_free_to_node)(GtkSnapshot *snapshot);
static GskRenderNode* (*fp_gtk_snapshot_to_node)(GtkSnapshot *snapshot);
static GdkPaintable* (*fp_gtk_snapshot_free_to_paintable)(GtkSnapshot *snapshot,
                                                           const graphene_rect_t *size);
static void (*fp_gtk_snapshot_render_background)(GtkSnapshot *snapshot,
                                                  void *context, double x, double y,
                                                  double width, double height);

/* GskRenderer - for offscreen rendering */
static GskRenderer* (*fp_gsk_cairo_renderer_new)(void);
static gboolean (*fp_gsk_renderer_realize)(GskRenderer *renderer, GdkSurface *surface, void **error);
static gboolean (*fp_gsk_renderer_realize_for_display)(GskRenderer *renderer, GdkDisplay *display, void **error);
static GdkTexture* (*fp_gsk_renderer_render_texture)(GskRenderer *renderer,
                                                      GskRenderNode *root,
                                                      const graphene_rect_t *viewport);
static void (*fp_gsk_renderer_unrealize)(GskRenderer *renderer);
static void (*fp_gsk_render_node_unref)(GskRenderNode *node);

/* GdkTexture */
static int (*fp_gdk_texture_get_width)(GdkTexture *texture);
static int (*fp_gdk_texture_get_height)(GdkTexture *texture);
static void (*fp_gdk_texture_download)(GdkTexture *texture, unsigned char *data, gsize stride);

/* GdkDisplay */
static GdkDisplay* (*fp_gdk_display_get_default)(void);

/* GtkSettings */
static GtkSettings* (*fp_gtk_settings_get_default)(void);

/* GLib main loop - needed to process GTK4 events */
static gboolean (*fp_g_main_context_iteration)(void *context, gboolean may_block);

/* ========== Global state ========== */

static void *gtk4_libhandle = NULL;
static gboolean gtk4_initialized = FALSE;
static GskRenderer *offscreen_renderer = NULL;

/* Cached widgets for rendering */
static GtkWidget *offscreen_window = NULL;
static GtkWidget *header_bar_start = NULL; /* WindowControls for start (left) side */
static GtkWidget *header_bar_end = NULL;   /* WindowControls for end (right) side */

/* ========== Symbol loading ========== */

#define LOAD_SYMBOL(fp_name, name) do {                     \
    (fp_name) = dlsym(gtk4_libhandle, name);                \
    if (!(fp_name)) {                                       \
        fprintf(stderr, "gtk4_header_buttons: "             \
                "error loading symbol %s: %s\n",            \
                name, dlerror());                           \
        goto fail;                                          \
    }                                                       \
} while(0)

#define LOAD_SYMBOL_OPT(fp_name, name) do {                 \
    (fp_name) = dlsym(gtk4_libhandle, name);                \
} while(0)

static gboolean loadGtk4Symbols(void) {
    /* Try versioned first, then unversioned */
    gtk4_libhandle = dlopen("libgtk-4.so.1", RTLD_LAZY | RTLD_LOCAL);
    if (!gtk4_libhandle) {
        gtk4_libhandle = dlopen("libgtk-4.so", RTLD_LAZY | RTLD_LOCAL);
    }

    if (!gtk4_libhandle) {
        return FALSE;
    }

    /* GLib/GObject */
    LOAD_SYMBOL(fp_g_object_unref, "g_object_unref");
    LOAD_SYMBOL(fp_g_object_ref, "g_object_ref");
    LOAD_SYMBOL(fp_g_free, "g_free");

    /* GTK4 init */
    LOAD_SYMBOL(fp_gtk_init, "gtk_init");

    /* GtkWindow */
    LOAD_SYMBOL(fp_gtk_window_new, "gtk_window_new");
    LOAD_SYMBOL(fp_gtk_window_set_decorated, "gtk_window_set_decorated");
    LOAD_SYMBOL(fp_gtk_window_set_child, "gtk_window_set_child");
    LOAD_SYMBOL(fp_gtk_window_set_deletable, "gtk_window_set_deletable");

    /* GtkWindowControls */
    LOAD_SYMBOL(fp_gtk_window_controls_new, "gtk_window_controls_new");
    LOAD_SYMBOL(fp_gtk_window_controls_set_side, "gtk_window_controls_set_side");
    LOAD_SYMBOL(fp_gtk_window_controls_set_decoration_layout, "gtk_window_controls_set_decoration_layout");

    /* GtkHeaderBar */
    LOAD_SYMBOL(fp_gtk_header_bar_new, "gtk_header_bar_new");
    LOAD_SYMBOL(fp_gtk_header_bar_set_show_title_buttons, "gtk_header_bar_set_show_title_buttons");
    LOAD_SYMBOL(fp_gtk_header_bar_set_decoration_layout, "gtk_header_bar_set_decoration_layout");

    /* GtkWidget */
    LOAD_SYMBOL(fp_gtk_widget_realize, "gtk_widget_realize");
    LOAD_SYMBOL(fp_gtk_widget_show, "gtk_widget_show");
    LOAD_SYMBOL(fp_gtk_widget_set_visible, "gtk_widget_set_visible");
    LOAD_SYMBOL(fp_gtk_widget_measure, "gtk_widget_measure");
    LOAD_SYMBOL(fp_gtk_widget_set_size_request, "gtk_widget_set_size_request");
    LOAD_SYMBOL(fp_gtk_widget_get_preferred_size, "gtk_widget_get_preferred_size");
    LOAD_SYMBOL(fp_gtk_widget_queue_draw, "gtk_widget_queue_draw");
    LOAD_SYMBOL(fp_gtk_widget_set_state_flags, "gtk_widget_set_state_flags");
    LOAD_SYMBOL(fp_gtk_widget_unset_state_flags, "gtk_widget_unset_state_flags");
    LOAD_SYMBOL(fp_gtk_widget_get_first_child, "gtk_widget_get_first_child");
    LOAD_SYMBOL(fp_gtk_widget_get_next_sibling, "gtk_widget_get_next_sibling");
    LOAD_SYMBOL(fp_gtk_widget_get_allocation, "gtk_widget_get_allocation");
    LOAD_SYMBOL(fp_gtk_widget_add_css_class, "gtk_widget_add_css_class");
    LOAD_SYMBOL(fp_gtk_widget_remove_css_class, "gtk_widget_remove_css_class");
    LOAD_SYMBOL(fp_gtk_widget_get_width, "gtk_widget_get_width");
    LOAD_SYMBOL(fp_gtk_widget_get_height, "gtk_widget_get_height");

    /* GtkSnapshot */
    LOAD_SYMBOL(fp_gtk_snapshot_new, "gtk_snapshot_new");
    LOAD_SYMBOL(fp_gtk_snapshot_free_to_node, "gtk_snapshot_free_to_node");
    LOAD_SYMBOL(fp_gtk_snapshot_to_node, "gtk_snapshot_to_node");

    /* GskRenderer - offscreen rendering */
    LOAD_SYMBOL(fp_gsk_cairo_renderer_new, "gsk_cairo_renderer_new");
    LOAD_SYMBOL(fp_gsk_renderer_realize, "gsk_renderer_realize");
    LOAD_SYMBOL(fp_gsk_renderer_render_texture, "gsk_renderer_render_texture");
    LOAD_SYMBOL(fp_gsk_renderer_unrealize, "gsk_renderer_unrealize");
    LOAD_SYMBOL(fp_gsk_render_node_unref, "gsk_render_node_unref");

    /* Optional: gsk_renderer_realize_for_display (GTK 4.4+) */
    LOAD_SYMBOL_OPT(fp_gsk_renderer_realize_for_display, "gsk_renderer_realize_for_display");

    /* GdkTexture */
    LOAD_SYMBOL(fp_gdk_texture_get_width, "gdk_texture_get_width");
    LOAD_SYMBOL(fp_gdk_texture_get_height, "gdk_texture_get_height");
    LOAD_SYMBOL(fp_gdk_texture_download, "gdk_texture_download");

    /* GdkDisplay */
    LOAD_SYMBOL(fp_gdk_display_get_default, "gdk_display_get_default");

    /* GtkSettings */
    LOAD_SYMBOL(fp_gtk_settings_get_default, "gtk_settings_get_default");

    /* GLib main context */
    LOAD_SYMBOL(fp_g_main_context_iteration, "g_main_context_iteration");

    return TRUE;

fail:
    dlclose(gtk4_libhandle);
    gtk4_libhandle = NULL;
    return FALSE;
}

/**
 * Initialize GTK4 and create the offscreen rendering infrastructure.
 * GTK4's gtk_init() takes no arguments and is safe to call.
 * We use RTLD_LOCAL when loading libgtk-4 to avoid symbol conflicts with GTK3.
 */
static gboolean initGtk4(void) {
    if (gtk4_initialized) {
        return TRUE;
    }

    if (!loadGtk4Symbols()) {
        return FALSE;
    }

    /* Initialize GTK4. Note: since we loaded with RTLD_LOCAL, this GTK4 init
     * won't interfere with the GTK3 that's already loaded in the process. */
    fp_gtk_init();

    /* Process pending events to complete initialization */
    while (fp_g_main_context_iteration(NULL, FALSE)) {
        /* drain */
    }

    /* Create a GskCairoRenderer for offscreen rendering */
    offscreen_renderer = fp_gsk_cairo_renderer_new();
    if (!offscreen_renderer) {
        fprintf(stderr, "gtk4_header_buttons: failed to create GskCairoRenderer\n");
        goto fail;
    }

    /* Try to realize the renderer - try realize_for_display first (GTK 4.4+),
     * fall back to realize with NULL surface */
    void *error = NULL;
    gboolean realized = FALSE;

    if (fp_gsk_renderer_realize_for_display) {
        GdkDisplay *display = fp_gdk_display_get_default();
        if (display) {
            realized = fp_gsk_renderer_realize_for_display(offscreen_renderer, display, &error);
        }
    }

    if (!realized) {
        realized = fp_gsk_renderer_realize(offscreen_renderer, NULL, &error);
    }

    if (!realized) {
        fprintf(stderr, "gtk4_header_buttons: failed to realize renderer\n");
        if (error) {
            /* GError - just free it */
            fp_g_free(error);
        }
        fp_g_object_unref(offscreen_renderer);
        offscreen_renderer = NULL;
        goto fail;
    }

    gtk4_initialized = TRUE;
    return TRUE;

fail:
    if (gtk4_libhandle) {
        dlclose(gtk4_libhandle);
        gtk4_libhandle = NULL;
    }
    return FALSE;
}

/**
 * Create a GtkWindow with a GtkHeaderBar containing GtkWindowControls
 * for the specified side (GTK_PACK_START = left, GTK_PACK_END = right).
 *
 * Returns the GtkWindowControls widget, or NULL on failure.
 * The window is stored in offscreen_window.
 */
static GtkWidget* createWindowControlsWidget(int side, const char *decorationLayout) {
    /* Create a GtkWindow */
    GtkWidget *window = fp_gtk_window_new();
    if (!window) return NULL;

    /* Create a header bar with window controls */
    GtkWidget *headerBar = fp_gtk_header_bar_new();
    fp_gtk_header_bar_set_show_title_buttons(headerBar, TRUE);

    if (decorationLayout && decorationLayout[0] != '\0') {
        fp_gtk_header_bar_set_decoration_layout(headerBar, decorationLayout);
    }

    /* Set the header bar as the window's titlebar */
    /* In GTK4, we use gtk_window_set_titlebar to set the header bar */
    /* Since we don't have this symbol loaded, let's use the child approach */
    fp_gtk_window_set_child(window, headerBar);
    fp_gtk_window_set_decorated(window, TRUE);

    /* Realize the window (creates the underlying GDK surface) */
    fp_gtk_widget_realize(window);

    /* Process events to ensure widget measurement works */
    while (fp_g_main_context_iteration(NULL, FALSE)) { }

    /* Find the GtkWindowControls inside the header bar */
    GtkWidget *child = fp_gtk_widget_get_first_child(headerBar);
    GtkWidget *controls = NULL;

    while (child != NULL) {
        /* The GtkWindowControls is a child of the header bar.
         * We identify it by iterating children. In a standard header bar,
         * window controls are the first and last children. */
        controls = child;
        if (side == GTK_PACK_START) {
            break; /* First child is the start controls */
        }
        GtkWidget *next = fp_gtk_widget_get_next_sibling(child);
        if (next == NULL) {
            break; /* Last child is the end controls */
        }
        child = next;
    }

    return window; /* Return the window - caller can access controls via header bar */
}

/* ========== Rendering Pipeline ========== */

/**
 * Renders a GtkWindowControls widget to a pixel buffer.
 *
 * Strategy: Use GtkSnapshot to capture the widget's render tree, then
 * use GskRenderer to render it to a GdkTexture, and finally download
 * the texture pixels.
 *
 * @param side 0=start(left), 1=end(right)
 * @param decorationLayout the GTK decoration layout string (e.g., "close,minimize,maximize:")
 * @param hoveredIndex -1 for none, 0/1/2 for which button is hovered
 * @param pressed TRUE if the hovered button is pressed
 * @param focused TRUE if the window is focused
 * @param outWidth output: width of rendered image
 * @param outHeight output: height of rendered image
 * @param outButtonCount output: number of buttons
 * @param outButtonXPositions output: X position of each button (up to 3)
 * @param outButtonWidths output: width of each button (up to 3)
 * @return pixel data in ARGB format (pre-multiplied), or NULL on failure.
 *         Caller must free() the returned buffer.
 */
static unsigned char* renderWindowControls(
    int side,
    const char *decorationLayout,
    int hoveredIndex,
    gboolean pressed,
    gboolean focused,
    int *outWidth,
    int *outHeight,
    int *outButtonCount,
    int *outButtonXPositions,
    int *outButtonWidths)
{
    if (!gtk4_initialized) return NULL;

    /* Create a window with a header bar */
    GtkWidget *window = fp_gtk_window_new();
    if (!window) return NULL;

    /* Create header bar */
    GtkWidget *headerBar = fp_gtk_header_bar_new();
    fp_gtk_header_bar_set_show_title_buttons(headerBar, TRUE);

    if (decorationLayout && decorationLayout[0] != '\0') {
        fp_gtk_header_bar_set_decoration_layout(headerBar, decorationLayout);
    }

    /* We need to use gtk_window_set_titlebar instead of set_child */
    /* Load this symbol dynamically */
    void (*fp_gtk_window_set_titlebar)(GtkWidget*, GtkWidget*) =
        dlsym(gtk4_libhandle, "gtk_window_set_titlebar");

    if (fp_gtk_window_set_titlebar) {
        fp_gtk_window_set_titlebar(window, headerBar);
    } else {
        fp_gtk_window_set_child(window, headerBar);
    }

    /* Set window state for focus */
    if (!focused) {
        fp_gtk_widget_add_css_class(window, "backdrop");
    }

    /* Set window size to something reasonable */
    fp_gtk_widget_set_size_request(window, 400, 48);

    /* Realize and show */
    fp_gtk_widget_show(window);

    /* Process events to let the widget realize and lay out */
    for (int i = 0; i < 10; i++) {
        fp_g_main_context_iteration(NULL, FALSE);
    }

    /* Measure the header bar to get the natural size */
    int minWidth = 0, natWidth = 0, minHeight = 0, natHeight = 0;
    fp_gtk_widget_measure(headerBar, GTK_ORIENTATION_HORIZONTAL, -1,
                          &minWidth, &natWidth, NULL, NULL);
    fp_gtk_widget_measure(headerBar, GTK_ORIENTATION_VERTICAL, natWidth,
                          &minHeight, &natHeight, NULL, NULL);

    /* Now find the GtkWindowControls child and its button children */
    GtkWidget *child = fp_gtk_widget_get_first_child(headerBar);
    GtkWidget *targetControls = NULL;
    int buttonCount = 0;

    /* Iterate to find the window controls on the correct side */
    while (child != NULL) {
        /* Check if this is window controls by measuring it */
        int cMinW = 0, cNatW = 0;
        fp_gtk_widget_measure(child, GTK_ORIENTATION_HORIZONTAL, -1,
                              &cMinW, &cNatW, NULL, NULL);

        if (cNatW > 0) {
            if (side == GTK_PACK_START && targetControls == NULL) {
                targetControls = child;
                break;
            }
            targetControls = child; /* Keep last for PACK_END */
        }

        child = fp_gtk_widget_get_next_sibling(child);
    }

    if (!targetControls) {
        /* No controls found - destroy window and return */
        /* GTK4 doesn't have gtk_widget_destroy, use gtk_window_destroy */
        void (*fp_gtk_window_destroy)(GtkWindow*) =
            dlsym(gtk4_libhandle, "gtk_window_destroy");
        if (fp_gtk_window_destroy) {
            fp_gtk_window_destroy((GtkWindow*)window);
        } else {
            fp_g_object_unref(window);
        }
        return NULL;
    }

    /* Measure the window controls */
    int controlsMinW = 0, controlsNatW = 0, controlsMinH = 0, controlsNatH = 0;
    fp_gtk_widget_measure(targetControls, GTK_ORIENTATION_HORIZONTAL, -1,
                          &controlsMinW, &controlsNatW, NULL, NULL);
    fp_gtk_widget_measure(targetControls, GTK_ORIENTATION_VERTICAL, controlsNatW,
                          &controlsMinH, &controlsNatH, NULL, NULL);

    int width = controlsNatW > 0 ? controlsNatW : minWidth;
    int height = controlsNatH > 0 ? controlsNatH : natHeight;

    if (width <= 0 || height <= 0) {
        width = width > 0 ? width : 100;
        height = height > 0 ? height : 32;
    }

    /* Count and measure individual buttons within the controls */
    GtkWidget *btnChild = fp_gtk_widget_get_first_child(targetControls);
    int btnIndex = 0;

    while (btnChild != NULL && btnIndex < 3) {
        int btnMinW = 0, btnNatW = 0;
        fp_gtk_widget_measure(btnChild, GTK_ORIENTATION_HORIZONTAL, -1,
                              &btnMinW, &btnNatW, NULL, NULL);

        if (btnNatW > 0) {
            if (outButtonWidths) outButtonWidths[btnIndex] = btnNatW;
            btnIndex++;
        }

        btnChild = fp_gtk_widget_get_next_sibling(btnChild);
    }

    buttonCount = btnIndex;

    /* Calculate button X positions */
    if (outButtonXPositions && buttonCount > 0) {
        int xPos = 0;
        for (int i = 0; i < buttonCount; i++) {
            outButtonXPositions[i] = xPos;
            xPos += outButtonWidths[i];
        }
    }

    /* Apply hover/pressed states to the appropriate button */
    if (hoveredIndex >= 0 && hoveredIndex < buttonCount) {
        btnChild = fp_gtk_widget_get_first_child(targetControls);
        btnIndex = 0;
        while (btnChild != NULL && btnIndex <= hoveredIndex) {
            if (btnIndex == hoveredIndex) {
                fp_gtk_widget_set_state_flags(btnChild, GTK_STATE_FLAG_PRELIGHT, FALSE);
                if (pressed) {
                    fp_gtk_widget_set_state_flags(btnChild, GTK_STATE_FLAG_ACTIVE, FALSE);
                }
            }
            btnChild = fp_gtk_widget_get_next_sibling(btnChild);
            btnIndex++;
        }
    }

    /* Process events to apply state changes */
    for (int i = 0; i < 5; i++) {
        fp_g_main_context_iteration(NULL, FALSE);
    }

    /* Create a snapshot of the controls */
    GtkSnapshot *snapshot = fp_gtk_snapshot_new();
    if (!snapshot) {
        void (*fp_gtk_window_destroy)(GtkWindow*) =
            dlsym(gtk4_libhandle, "gtk_window_destroy");
        if (fp_gtk_window_destroy) fp_gtk_window_destroy((GtkWindow*)window);
        return NULL;
    }

    /* Snapshot the widget */
    void (*fp_gtk_widget_snapshot)(GtkWidget*, GtkSnapshot*) =
        dlsym(gtk4_libhandle, "gtk_widget_snapshot");

    if (fp_gtk_widget_snapshot) {
        fp_gtk_widget_snapshot(targetControls, snapshot);
    } else {
        /* Fallback: try snapshot_child */
        fp_gtk_widget_snapshot_child =
            dlsym(gtk4_libhandle, "gtk_widget_snapshot_child");
        if (fp_gtk_widget_snapshot_child) {
            fp_gtk_widget_snapshot_child(headerBar, targetControls, snapshot);
        }
    }

    /* Convert snapshot to render node */
    GskRenderNode *node = fp_gtk_snapshot_free_to_node(snapshot);
    if (!node) {
        void (*fp_gtk_window_destroy)(GtkWindow*) =
            dlsym(gtk4_libhandle, "gtk_window_destroy");
        if (fp_gtk_window_destroy) fp_gtk_window_destroy((GtkWindow*)window);
        return NULL;
    }

    /* Render to texture */
    graphene_rect_t viewport = { 0, 0, (float)width, (float)height };
    GdkTexture *texture = fp_gsk_renderer_render_texture(offscreen_renderer, node, &viewport);
    fp_gsk_render_node_unref(node);

    if (!texture) {
        void (*fp_gtk_window_destroy)(GtkWindow*) =
            dlsym(gtk4_libhandle, "gtk_window_destroy");
        if (fp_gtk_window_destroy) fp_gtk_window_destroy((GtkWindow*)window);
        return NULL;
    }

    /* Get texture dimensions */
    int texWidth = fp_gdk_texture_get_width(texture);
    int texHeight = fp_gdk_texture_get_height(texture);

    if (texWidth <= 0 || texHeight <= 0) {
        fp_g_object_unref(texture);
        void (*fp_gtk_window_destroy)(GtkWindow*) =
            dlsym(gtk4_libhandle, "gtk_window_destroy");
        if (fp_gtk_window_destroy) fp_gtk_window_destroy((GtkWindow*)window);
        return NULL;
    }

    /* Allocate pixel buffer (BGRA format, 4 bytes per pixel) */
    gsize stride = texWidth * 4;
    unsigned char *pixels = (unsigned char*)malloc(stride * texHeight);

    if (!pixels) {
        fp_g_object_unref(texture);
        void (*fp_gtk_window_destroy)(GtkWindow*) =
            dlsym(gtk4_libhandle, "gtk_window_destroy");
        if (fp_gtk_window_destroy) fp_gtk_window_destroy((GtkWindow*)window);
        return NULL;
    }

    /* Download texture pixels in B8G8R8A8 premultiplied format */
    fp_gdk_texture_download(texture, pixels, stride);
    fp_g_object_unref(texture);

    /* Convert from BGRA (GDK default) to INT_ARGB_PRE (JavaFX format) */
    /* GDK downloads in the default memory format which is B8G8R8A8_PREMULTIPLIED on little-endian.
     * In memory this is: B, G, R, A bytes.
     * JavaFX INT_ARGB_PRE is stored as int with A in highest byte: 0xAARRGGBB on big-endian,
     * but on little-endian as stored bytes it's BB, GG, RR, AA.
     * Actually, JavaFX pixel format for IntBuffer is ARGB where int value = (A<<24)|(R<<16)|(G<<8)|B.
     * For byte buffer, we need: position 0=B or position 0=A depending on format.
     *
     * For WritableImage with PixelFormat.getIntArgbPreInstance(), each int is 0xAARRGGBB.
     * When stored as byte array in native order (little-endian): BB, GG, RR, AA.
     *
     * GDK B8G8R8A8_PREMULTIPLIED in memory order: B, G, R, A
     * JavaFX IntArgbPre as bytes (little-endian): B, G, R, A
     *
     * So the formats actually match on little-endian! No conversion needed.
     */

    *outWidth = texWidth;
    *outHeight = texHeight;
    *outButtonCount = buttonCount;

    /* Clean up the GTK4 window */
    void (*fp_gtk_window_destroy)(GtkWindow*) =
        dlsym(gtk4_libhandle, "gtk_window_destroy");
    if (fp_gtk_window_destroy) {
        fp_gtk_window_destroy((GtkWindow*)window);
    }

    /* Process cleanup events */
    while (fp_g_main_context_iteration(NULL, FALSE)) { }

    return pixels;
}

/**
 * Gets the preferred size of the window controls for a given side.
 *
 * @param side 0=start(left), 1=end(right)
 * @param decorationLayout the GTK decoration layout string
 * @param outWidth output: preferred width
 * @param outHeight output: preferred height
 * @param outButtonCount output: number of buttons
 * @param outButtonWidths output: width of each button (up to 3)
 * @return TRUE on success
 */
static gboolean measureWindowControls(
    int side,
    const char *decorationLayout,
    int *outWidth,
    int *outHeight,
    int *outButtonCount,
    int *outButtonWidths)
{
    if (!gtk4_initialized) return FALSE;

    /* Create a temporary window for measurement */
    GtkWidget *window = fp_gtk_window_new();
    if (!window) return FALSE;

    GtkWidget *headerBar = fp_gtk_header_bar_new();
    fp_gtk_header_bar_set_show_title_buttons(headerBar, TRUE);

    if (decorationLayout && decorationLayout[0] != '\0') {
        fp_gtk_header_bar_set_decoration_layout(headerBar, decorationLayout);
    }

    void (*fp_gtk_window_set_titlebar)(GtkWidget*, GtkWidget*) =
        dlsym(gtk4_libhandle, "gtk_window_set_titlebar");

    if (fp_gtk_window_set_titlebar) {
        fp_gtk_window_set_titlebar(window, headerBar);
    } else {
        fp_gtk_window_set_child(window, headerBar);
    }

    fp_gtk_widget_set_size_request(window, 400, 48);
    fp_gtk_widget_show(window);

    for (int i = 0; i < 10; i++) {
        fp_g_main_context_iteration(NULL, FALSE);
    }

    /* Find the window controls on the correct side */
    GtkWidget *child = fp_gtk_widget_get_first_child(headerBar);
    GtkWidget *targetControls = NULL;

    while (child != NULL) {
        int cMinW = 0, cNatW = 0;
        fp_gtk_widget_measure(child, GTK_ORIENTATION_HORIZONTAL, -1,
                              &cMinW, &cNatW, NULL, NULL);

        if (cNatW > 0) {
            if (side == GTK_PACK_START && targetControls == NULL) {
                targetControls = child;
                break;
            }
            targetControls = child;
        }

        child = fp_gtk_widget_get_next_sibling(child);
    }

    gboolean result = FALSE;

    if (targetControls) {
        int controlsMinW = 0, controlsNatW = 0, controlsMinH = 0, controlsNatH = 0;
        fp_gtk_widget_measure(targetControls, GTK_ORIENTATION_HORIZONTAL, -1,
                              &controlsMinW, &controlsNatW, NULL, NULL);
        fp_gtk_widget_measure(targetControls, GTK_ORIENTATION_VERTICAL, controlsNatW,
                              &controlsMinH, &controlsNatH, NULL, NULL);

        *outWidth = controlsNatW;
        *outHeight = controlsNatH;

        /* Count and measure buttons */
        GtkWidget *btnChild = fp_gtk_widget_get_first_child(targetControls);
        int btnIndex = 0;
        while (btnChild != NULL && btnIndex < 3) {
            int btnMinW = 0, btnNatW = 0;
            fp_gtk_widget_measure(btnChild, GTK_ORIENTATION_HORIZONTAL, -1,
                                  &btnMinW, &btnNatW, NULL, NULL);
            if (btnNatW > 0) {
                if (outButtonWidths) outButtonWidths[btnIndex] = btnNatW;
                btnIndex++;
            }
            btnChild = fp_gtk_widget_get_next_sibling(btnChild);
        }
        *outButtonCount = btnIndex;
        result = TRUE;
    }

    void (*fp_gtk_window_destroy)(GtkWindow*) =
        dlsym(gtk4_libhandle, "gtk_window_destroy");
    if (fp_gtk_window_destroy) {
        fp_gtk_window_destroy((GtkWindow*)window);
    }
    while (fp_g_main_context_iteration(NULL, FALSE)) { }

    return result;
}

/* ========== JNI Methods ========== */

static JavaVM *javaVM = NULL;

JNIEXPORT jint JNICALL
JNI_OnLoad(JavaVM *vm, void *reserved) {
    (void)reserved;
    javaVM = vm;
    return JNI_VERSION_1_6;
}

/*
 * Class:     com_sun_glass_ui_gtk_Gtk4WindowControls
 * Method:    nInit
 * Signature: ()Z
 */
JNIEXPORT jboolean JNICALL
Java_com_sun_glass_ui_gtk_Gtk4WindowControls_nInit(JNIEnv *env, jclass clazz) {
    (void)env;
    (void)clazz;
    return initGtk4() ? JNI_TRUE : JNI_FALSE;
}

/*
 * Class:     com_sun_glass_ui_gtk_Gtk4WindowControls
 * Method:    nGetMetrics
 * Signature: (ILjava/lang/String;)[I
 *
 * Returns an int array: [width, height, buttonCount, btn0Width, btn1Width, btn2Width]
 */
JNIEXPORT jintArray JNICALL
Java_com_sun_glass_ui_gtk_Gtk4WindowControls_nGetMetrics(JNIEnv *env, jclass clazz,
                                                          jint side, jstring jLayout) {
    (void)clazz;

    const char *layout = NULL;
    if (jLayout) {
        layout = (*env)->GetStringUTFChars(env, jLayout, NULL);
    }

    int width = 0, height = 0, buttonCount = 0;
    int buttonWidths[3] = {0, 0, 0};

    gboolean ok = measureWindowControls(side, layout, &width, &height,
                                         &buttonCount, buttonWidths);

    if (jLayout && layout) {
        (*env)->ReleaseStringUTFChars(env, jLayout, layout);
    }

    if (!ok) {
        return NULL;
    }

    /* Return [width, height, buttonCount, btn0W, btn1W, btn2W] */
    jintArray result = (*env)->NewIntArray(env, 6);
    if (!result) return NULL;

    jint data[6] = { width, height, buttonCount,
                     buttonWidths[0], buttonWidths[1], buttonWidths[2] };
    (*env)->SetIntArrayRegion(env, result, 0, 6, data);

    return result;
}

/*
 * Class:     com_sun_glass_ui_gtk_Gtk4WindowControls
 * Method:    nRender
 * Signature: (ILjava/lang/String;IZZ)[I
 *
 * Renders the window controls and returns the pixel data as an int array
 * in ARGB_PRE format. The first 6 ints are metadata:
 * [width, height, buttonCount, btn0Width, btn1Width, btn2Width, ...pixels...]
 */
JNIEXPORT jintArray JNICALL
Java_com_sun_glass_ui_gtk_Gtk4WindowControls_nRender(JNIEnv *env, jclass clazz,
                                                      jint side, jstring jLayout,
                                                      jint hoveredIndex, jboolean pressed,
                                                      jboolean focused) {
    (void)clazz;

    const char *layout = NULL;
    if (jLayout) {
        layout = (*env)->GetStringUTFChars(env, jLayout, NULL);
    }

    int width = 0, height = 0, buttonCount = 0;
    int buttonXPositions[3] = {0, 0, 0};
    int buttonWidths[3] = {0, 0, 0};

    unsigned char *pixels = renderWindowControls(
        side, layout, hoveredIndex, pressed, focused,
        &width, &height, &buttonCount,
        buttonXPositions, buttonWidths);

    if (jLayout && layout) {
        (*env)->ReleaseStringUTFChars(env, jLayout, layout);
    }

    if (!pixels || width <= 0 || height <= 0) {
        if (pixels) free(pixels);
        return NULL;
    }

    /* Metadata: width, height, buttonCount, 3x button X positions, 3x button widths = 9 ints */
    int metadataSize = 9;
    int pixelInts = width * height;
    int totalSize = metadataSize + pixelInts;

    jintArray result = (*env)->NewIntArray(env, totalSize);
    if (!result) {
        free(pixels);
        return NULL;
    }

    /* Fill metadata */
    jint metadata[9] = {
        width, height, buttonCount,
        buttonXPositions[0], buttonXPositions[1], buttonXPositions[2],
        buttonWidths[0], buttonWidths[1], buttonWidths[2]
    };
    (*env)->SetIntArrayRegion(env, result, 0, metadataSize, metadata);

    /* Fill pixel data - convert byte array to int array
     * Pixels are in BGRA byte order (from GDK).
     * We need ARGB int format for JavaFX:
     * int = (A << 24) | (R << 16) | (G << 8) | B
     */
    jint *pixelData = (jint*)malloc(pixelInts * sizeof(jint));
    if (!pixelData) {
        free(pixels);
        return NULL;
    }

    for (int i = 0; i < pixelInts; i++) {
        unsigned char b = pixels[i * 4 + 0];
        unsigned char g = pixels[i * 4 + 1];
        unsigned char r = pixels[i * 4 + 2];
        unsigned char a = pixels[i * 4 + 3];
        pixelData[i] = (a << 24) | (r << 16) | (g << 8) | b;
    }

    (*env)->SetIntArrayRegion(env, result, metadataSize, pixelInts, pixelData);
    free(pixelData);
    free(pixels);

    return result;
}

/*
 * Class:     com_sun_glass_ui_gtk_Gtk4WindowControls
 * Method:    nDispose
 * Signature: ()V
 */
JNIEXPORT void JNICALL
Java_com_sun_glass_ui_gtk_Gtk4WindowControls_nDispose(JNIEnv *env, jclass clazz) {
    (void)env;
    (void)clazz;

    if (offscreen_renderer) {
        fp_gsk_renderer_unrealize(offscreen_renderer);
        fp_g_object_unref(offscreen_renderer);
        offscreen_renderer = NULL;
    }

    /* Note: we don't dlclose gtk4_libhandle because GTK4 may have registered
     * GType classes that can't be safely unloaded */
    gtk4_initialized = FALSE;
}

