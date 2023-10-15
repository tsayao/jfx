/*
 * Copyright (c) 2011, 2023, Oracle and/or its affiliates. All rights reserved.
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
#include "glass_window.h"
#include "glass_general.h"
#include "glass_key.h"
#include "glass_screen.h"
#include "glass_dnd.h"

#include <com_sun_glass_ui_gtk_GtkWindow.h>
#include <com_sun_glass_events_WindowEvent.h>
#include <com_sun_glass_events_ViewEvent.h>
#include <com_sun_glass_events_MouseEvent.h>
#include <com_sun_glass_events_KeyEvent.h>

#include <com_sun_glass_ui_Window_Level.h>

#include <cairo.h>
#include <gdk/gdk.h>

#include <string.h>

#include <algorithm>

#define MOUSE_BACK_BTN 8
#define MOUSE_FORWARD_BTN 9

#define USER_PTR_TO_CTX(value) ((WindowContext *) value)

WindowContext * WindowContext::sm_grab_window = NULL;
WindowContext * WindowContext::sm_mouse_drag_window = NULL;

static inline jint gtk_button_number_to_mouse_button(guint button) {
    switch (button) {
        case 1:
            return com_sun_glass_events_MouseEvent_BUTTON_LEFT;
        case 2:
            return com_sun_glass_events_MouseEvent_BUTTON_OTHER;
        case 3:
            return com_sun_glass_events_MouseEvent_BUTTON_RIGHT;
        case MOUSE_BACK_BTN:
            return com_sun_glass_events_MouseEvent_BUTTON_BACK;
        case MOUSE_FORWARD_BTN:
            return com_sun_glass_events_MouseEvent_BUTTON_FORWARD;
        default:
            // Other buttons are not supported by quantum and are not reported by other platforms
            return com_sun_glass_events_MouseEvent_BUTTON_NONE;
    }
}

//------------------------- SIGNALS

static gboolean event_button_press(GtkWidget* self, GdkEventButton* event, gpointer user_data) {
    WindowContext *ctx = USER_PTR_TO_CTX(user_data);
    ctx->process_mouse_button(event);

    return FALSE;
}

static gboolean event_button_release(GtkWidget* self, GdkEventButton* event, gpointer user_data) {
    WindowContext *ctx = USER_PTR_TO_CTX(user_data);
    ctx->process_mouse_button(event);

    return FALSE;
}

static gboolean event_configure_view(GtkWidget* self, GdkEventConfigure* event, gpointer user_data) {
    WindowContext *ctx = USER_PTR_TO_CTX(user_data);
    ctx->process_configure_view(event);

    return TRUE;
}

static gboolean event_configure_window(GtkWidget* self, GdkEventConfigure* event, gpointer user_data) {
    WindowContext *ctx = USER_PTR_TO_CTX(user_data);
    ctx->process_configure_window(event);

    return FALSE;
}

static gboolean event_enter_notify(GtkWidget* self, GdkEventCrossing* event, gpointer user_data) {
    WindowContext *ctx = USER_PTR_TO_CTX(user_data);
    ctx->process_mouse_cross(event);

    return FALSE;
}

static gboolean event_leave_notify(GtkWidget* self, GdkEventCrossing* event, gpointer user_data) {
    WindowContext *ctx = USER_PTR_TO_CTX(user_data);
    ctx->process_mouse_cross(event);

    return FALSE;
}

static gboolean event_focus_in(GtkWidget* self, GdkEventFocus* event, gpointer user_data) {
    WindowContext *ctx = USER_PTR_TO_CTX(user_data);
    ctx->process_focus(event);

    return FALSE;
}

static gboolean event_focus_out(GtkWidget* self, GdkEventFocus* event, gpointer user_data) {
    WindowContext *ctx = USER_PTR_TO_CTX(user_data);
    ctx->process_focus(event);

    return FALSE;
}

static gboolean event_scroll(GtkWidget* self, GdkEventScroll* event, gpointer user_data) {
    WindowContext *ctx = USER_PTR_TO_CTX(user_data);
    ctx->process_mouse_scroll(event);

    return FALSE;
}

static gboolean event_window_state(GtkWidget* self, GdkEventWindowState* event, gpointer user_data) {
    WindowContext *ctx = USER_PTR_TO_CTX(user_data);
    ctx->process_state(event);

    return FALSE;
}


static gboolean event_delete(GtkWidget* self, GdkEvent* event, gpointer user_data) {
    WindowContext *ctx = USER_PTR_TO_CTX(user_data);
    ctx->process_delete();
    return FALSE;
}

static gboolean event_destroy(GtkWidget* self, GdkEvent* event, gpointer user_data) {
    WindowContext *ctx = USER_PTR_TO_CTX(user_data);
    destroy_and_delete_ctx(ctx);
    return FALSE;
}

static gboolean event_motion_notify(GtkWidget* self, GdkEventMotion* event, gpointer user_data) {
    WindowContext *ctx = USER_PTR_TO_CTX(user_data);
    ctx->process_mouse_motion(event);
    return FALSE;
}

static gboolean event_key_press(GtkWidget* self, GdkEventKey* event, gpointer user_data) {
    WindowContext *ctx = USER_PTR_TO_CTX(user_data);

    if (!ctx->filterIME(event)) {
        ctx->process_key(event);
    }

    return FALSE;
}

static gboolean event_key_release(GtkWidget* self, GdkEventKey* event, gpointer user_data) {
    WindowContext *ctx = USER_PTR_TO_CTX(user_data);
    ctx->process_key(event);
    return FALSE;
}

static gboolean event_damage(GtkWidget* self, GdkEventExpose* event, gpointer user_data) {
    WindowContext *ctx = USER_PTR_TO_CTX(user_data);
    ctx->process_paint();
    return FALSE;
}

static gboolean event_draw(GtkWidget* self, cairo_t* cr, gpointer user_data) {
    WindowContext *ctx = USER_PTR_TO_CTX(user_data);
    ctx->process_paint();
    return FALSE;
}

static void event_realize(GtkWidget* self, gpointer user_data) {
    WindowContext *ctx = USER_PTR_TO_CTX(user_data);
    ctx->process_realize();
}

//------------------------- SIGNALS END

WindowContext::WindowContext(jobject _jwindow, WindowContext* _owner, long _screen,
        WindowFrameType _frame_type, WindowType type, int mask) :
            im_ctx(),
            events_processing_cnt(0),
            screen(_screen),
            frame_type(_frame_type),
            window_type(type),
            owner(_owner),
            resizable(),
            on_top(false),
            is_fullscreen(false),
            is_iconified(false),
            is_maximized(false),
            is_mouse_entered(false),
            is_disabled(false),
            can_be_deleted(false),
            jview(NULL),
            gdk_window(NULL) {
    jwindow = mainEnv->NewGlobalRef(_jwindow);

    window = gtk_window_new(type == POPUP ? GTK_WINDOW_POPUP : GTK_WINDOW_TOPLEVEL);

    // Create a header bar and set it as the title bar of the window
    if (frame_type == TITLED) {
        g_print ("gtk_header_bar_new\n");
        headerbar = gtk_header_bar_new();
        gtk_header_bar_set_show_close_button(GTK_HEADER_BAR(headerbar), TRUE);
        gtk_header_bar_set_title(GTK_HEADER_BAR(headerbar), "");
        gtk_window_set_titlebar(GTK_WINDOW(window), headerbar);
        gtk_header_bar_set_show_close_button(GTK_HEADER_BAR(headerbar), mask & com_sun_glass_ui_gtk_GtkWindow_CLOSABLE);
    }

    // Create the drawing area
    drawing_area = gtk_drawing_area_new();
    gtk_widget_set_events(drawing_area, GDK_FILTERED_EVENTS_MASK);
    g_signal_connect(G_OBJECT(drawing_area), "draw", G_CALLBACK(event_draw), this);
    g_signal_connect(G_OBJECT(drawing_area), "damage-event", G_CALLBACK(event_damage), this);
    g_signal_connect(G_OBJECT(drawing_area), "configure-event", G_CALLBACK(event_configure_view), this);
    gtk_container_add(GTK_CONTAINER(window), drawing_area);

    g_signal_connect(G_OBJECT(window), "window-state-event", G_CALLBACK(event_window_state), this);
    g_signal_connect(G_OBJECT(window), "delete-event", G_CALLBACK(event_delete), this);
    g_signal_connect(G_OBJECT(window), "destroy-event", G_CALLBACK(event_destroy), this);
    g_signal_connect(G_OBJECT(window), "realize", G_CALLBACK(event_realize), this);
    g_signal_connect(G_OBJECT(window), "button-press-event", G_CALLBACK(event_button_press), this);
    g_signal_connect(G_OBJECT(window), "button-release-event", G_CALLBACK(event_button_release), this);
    g_signal_connect(G_OBJECT(window), "focus-in-event", G_CALLBACK(event_focus_in), this);
    g_signal_connect(G_OBJECT(window), "focus-out-event", G_CALLBACK(event_focus_out), this);
    g_signal_connect(G_OBJECT(window), "enter-notify-event", G_CALLBACK(event_enter_notify), this);
    g_signal_connect(G_OBJECT(window), "leave-notify-event", G_CALLBACK(event_leave_notify), this);
    g_signal_connect(G_OBJECT(window), "scroll-event", G_CALLBACK(event_scroll), this);
    g_signal_connect(G_OBJECT(window), "key-press-event", G_CALLBACK(event_key_press), this);
    g_signal_connect(G_OBJECT(window), "key-release-event", G_CALLBACK(event_key_release), this);
    g_signal_connect(G_OBJECT(window), "configure-event", G_CALLBACK(event_configure_window), this);

    if (gchar* app_name = get_application_name()) {
        gtk_window_set_wmclass(GTK_WINDOW(window), app_name, app_name);
        g_free(app_name);
    }

    if (owner) {
        owner->add_child(this);
        if (on_top_inherited()) {
            gtk_window_set_keep_above(GTK_WINDOW(window), TRUE);
        }
    }

    if (type == UTILITY) {
        gtk_window_set_type_hint(GTK_WINDOW(window), GDK_WINDOW_TYPE_HINT_UTILITY);
    }

//    gdk_window_register_dnd(gdk_window);

    if (frame_type != TITLED) {
        gtk_window_set_decorated(GTK_WINDOW(window), FALSE);
    }
}


GdkWindow* WindowContext::get_gdk_window(){
    return gtk_widget_get_window(drawing_area);
}

jobject WindowContext::get_jwindow() {
    return jwindow;
}

jobject WindowContext::get_jview() {
    return jview;
}

bool WindowContext::isEnabled() {
    if (jwindow) {
        bool result = (JNI_TRUE == mainEnv->CallBooleanMethod(jwindow, jWindowIsEnabled));
        LOG_EXCEPTION(mainEnv)
        return result;
    } else {
        return false;
    }
}

GtkWindow *WindowContext::get_gtk_window() {
    return GTK_WINDOW(window);
}

int WindowContext::get_left_pos() {
    gint x;
    if (gtk_widget_translate_coordinates(drawing_area, window, 0, 0, &x, 0)) {
        g_print("left_pos: %d\n", x);
        return x;
    }

    return 0;
}

int WindowContext::get_top_pos() {
    gint y;
    if (gtk_widget_translate_coordinates(drawing_area, window, 0, 0, 0, &y)) {
        g_print("top_pos: %d\n", y);
        return y;
    }

    return 0;
}

void WindowContext::paint(void* data, jint width, jint height) {
    cairo_rectangle_int_t rect = {0, 0, width, height};
    cairo_region_t *region = cairo_region_create_rectangle(&rect);

    GdkWindow *draw_win = gtk_widget_get_window(drawing_area);

    gdk_window_begin_paint_region(draw_win, region);
    cairo_t* context = gdk_cairo_create(draw_win);

    cairo_surface_t* cairo_surface =
        cairo_image_surface_create_for_data(
            (unsigned char*)data,
            CAIRO_FORMAT_ARGB32,
            width, height, width * 4);

    applyShapeMask(data, width, height);

    cairo_set_source_surface(context, cairo_surface, 0, 0);
    cairo_set_operator(context, CAIRO_OPERATOR_SOURCE);
    cairo_paint(context);

    gdk_window_end_paint(draw_win);
    cairo_region_destroy(region);

    cairo_destroy(context);
    cairo_surface_destroy(cairo_surface);
}

void WindowContext::add_child(WindowContext* child) {
    children.insert(child);
    gtk_window_set_transient_for(child->get_gtk_window(), this->get_gtk_window());
}

void WindowContext::remove_child(WindowContext* child) {
    children.erase(child);
    gtk_window_set_transient_for(child->get_gtk_window(), NULL);
}

void WindowContext::show_or_hide_children(bool show) {
    std::set<WindowContext*>::iterator it;
    for (it = children.begin(); it != children.end(); ++it) {
        (*it)->set_minimized(!show);
        (*it)->show_or_hide_children(show);
    }
}

bool WindowContext::is_visible() {
    return gtk_widget_get_visible(window);
}

bool WindowContext::set_view(jobject view) {
    if (jview) {
        mainEnv->CallVoidMethod(jview, jViewNotifyMouse,
                com_sun_glass_events_MouseEvent_EXIT,
                com_sun_glass_events_MouseEvent_BUTTON_NONE,
                0, 0,
                0, 0,
                0,
                JNI_FALSE,
                JNI_FALSE);
        mainEnv->DeleteGlobalRef(jview);
    }

    if (view) {
        jview = mainEnv->NewGlobalRef(view);
    } else {
        jview = NULL;
    }
    return TRUE;
}

bool WindowContext::grab_focus() {
    if (WindowContext::sm_mouse_drag_window
            || glass_gdk_mouse_devices_grab(gdk_window)) {
        WindowContext::sm_grab_window = this;
        return true;
    } else {
        return false;
    }
}

bool WindowContext::grab_mouse_drag_focus() {
    if (glass_gdk_mouse_devices_grab_with_cursor(
            gdk_window, gdk_window_get_cursor(gdk_window), FALSE)) {
        WindowContext::sm_mouse_drag_window = this;
        return true;
    } else {
        return false;
    }
}

void WindowContext::ungrab_focus() {
    if (!WindowContext::sm_mouse_drag_window) {
        glass_gdk_mouse_devices_ungrab();
    }
    WindowContext::sm_grab_window = NULL;

    if (jwindow) {
        mainEnv->CallVoidMethod(jwindow, jWindowNotifyFocusUngrab);
        CHECK_JNI_EXCEPTION(mainEnv)
    }
}

void WindowContext::ungrab_mouse_drag_focus() {
    WindowContext::sm_mouse_drag_window = NULL;
    glass_gdk_mouse_devices_ungrab();
    if (WindowContext::sm_grab_window) {
        WindowContext::sm_grab_window->grab_focus();
    }
}

void WindowContext::process_realize() {
    gdk_window = gtk_widget_get_window(window);
    g_object_set_data_full(G_OBJECT(gdk_window), GDK_WINDOW_DATA_CONTEXT, this, NULL);
}

void WindowContext::process_focus(GdkEventFocus* event) {
    if (!event->in && WindowContext::sm_grab_window == this) {
        ungrab_focus();
    }

    if (im_ctx.enabled && im_ctx.ctx) {
        if (event->in) {
            gtk_im_context_focus_in(im_ctx.ctx);
        } else {
            gtk_im_context_focus_out(im_ctx.ctx);
        }
    }

    if (jwindow) {
        if (!event->in || isEnabled()) {
            mainEnv->CallVoidMethod(jwindow, jWindowNotifyFocus,
                    event->in ? com_sun_glass_events_WindowEvent_FOCUS_GAINED
                              : com_sun_glass_events_WindowEvent_FOCUS_LOST);
            CHECK_JNI_EXCEPTION(mainEnv)
        } else {
            // when the user tries to activate a disabled window, send FOCUS_DISABLED
            mainEnv->CallVoidMethod(jwindow, jWindowNotifyFocusDisabled);
            CHECK_JNI_EXCEPTION(mainEnv)
        }
    }
}

void WindowContext::process_destroy() {
    if (owner) {
        owner->remove_child(this);
    }

    if (WindowContext::sm_mouse_drag_window == this) {
        ungrab_mouse_drag_focus();
    }

    if (WindowContext::sm_grab_window == this) {
        ungrab_focus();
    }

    std::set<WindowContext*>::iterator it;
    for (it = children.begin(); it != children.end(); ++it) {
        // FIX JDK-8226537: this method calls set_owner(NULL) which prevents
        // WindowContext::process_destroy() to call remove_child() (because children
        // is being iterated here) but also prevents gtk_window_set_transient_for from
        // being called - this causes the crash on gnome.
        gtk_window_set_transient_for((*it)->get_gtk_window(), NULL);
        (*it)->set_owner(NULL);
        destroy_and_delete_ctx(*it);
    }
    children.clear();

    if (jwindow) {
        mainEnv->CallVoidMethod(jwindow, jWindowNotifyDestroy);
        EXCEPTION_OCCURED(mainEnv);
    }

    if (jview) {
        mainEnv->DeleteGlobalRef(jview);
        jview = NULL;
    }

    if (jwindow) {
        mainEnv->DeleteGlobalRef(jwindow);
        jwindow = NULL;
    }

    can_be_deleted = true;
}

void WindowContext::process_delete() {
    if (jwindow && isEnabled()) {
        mainEnv->CallVoidMethod(jwindow, jWindowNotifyClose);
        CHECK_JNI_EXCEPTION(mainEnv)
    }
}

void WindowContext::process_paint() {
    if (jview) {
        int w = gtk_widget_get_allocated_width(window);
        int h = gtk_widget_get_allocated_height(window);

        mainEnv->CallVoidMethod(jview, jViewNotifyRepaint, 0, 0, w, h);
        CHECK_JNI_EXCEPTION(mainEnv)
    }
}

void WindowContext::process_mouse_button(GdkEventButton* event) {
    bool press = event->type == GDK_BUTTON_PRESS;
    guint state = event->state;
    guint mask = 0;

    // We need to add/remove current mouse button from the modifier flags
    // as X lib state represents the state just prior to the event and
    // glass needs the state just after the event
    switch (event->button) {
        case 1:
            mask = GDK_BUTTON1_MASK;
            break;
        case 2:
            mask = GDK_BUTTON2_MASK;
            break;
        case 3:
            mask = GDK_BUTTON3_MASK;
            break;
        case MOUSE_BACK_BTN:
            mask = GDK_BUTTON4_MASK;
            break;
        case MOUSE_FORWARD_BTN:
            mask = GDK_BUTTON5_MASK;
            break;
    }

    if (press) {
        state |= mask;
    } else {
        state &= ~mask;
    }

    if (press) {
        GdkDevice* device = event->device;

        if (glass_gdk_device_is_grabbed(device)
                && (glass_gdk_device_get_window_at_position(device, NULL, NULL)
                == NULL)) {
            ungrab_focus();
            return;
        }
    }

    if (!press) {
        if ((event->state & MOUSE_BUTTONS_MASK) && !(state & MOUSE_BUTTONS_MASK)) { // all buttons released
            ungrab_mouse_drag_focus();
        } else if (event->button == 8 || event->button == 9) {
            // GDK X backend interprets button press events for buttons 4-7 as
            // scroll events so GDK_BUTTON4_MASK and GDK_BUTTON5_MASK will never
            // be set on the event->state from GDK. Thus we cannot check if all
            // buttons have been released in the usual way (as above).
            ungrab_mouse_drag_focus();
        }
    }

    jint button = gtk_button_number_to_mouse_button(event->button);

    if (jview && button != com_sun_glass_events_MouseEvent_BUTTON_NONE) {
        mainEnv->CallVoidMethod(jview, jViewNotifyMouse,
                press ? com_sun_glass_events_MouseEvent_DOWN : com_sun_glass_events_MouseEvent_UP,
                button,
                (jint) event->x, (jint) event->y,
                (jint) event->x_root, (jint) event->y_root,
                gdk_modifier_mask_to_glass(state),
                (event->button == 3 && press) ? JNI_TRUE : JNI_FALSE,
                JNI_FALSE);
        CHECK_JNI_EXCEPTION(mainEnv)

        if (jview && event->button == 3 && press) {
            mainEnv->CallVoidMethod(jview, jViewNotifyMenu,
                    (jint)event->x, (jint)event->y,
                    (jint)event->x_root, (jint)event->y_root,
                    JNI_FALSE);
            CHECK_JNI_EXCEPTION(mainEnv)
        }
    }
}

void WindowContext::process_mouse_motion(GdkEventMotion* event) {
    jint glass_modifier = gdk_modifier_mask_to_glass(event->state);
    jint isDrag = glass_modifier & (
            com_sun_glass_events_KeyEvent_MODIFIER_BUTTON_PRIMARY |
            com_sun_glass_events_KeyEvent_MODIFIER_BUTTON_MIDDLE |
            com_sun_glass_events_KeyEvent_MODIFIER_BUTTON_SECONDARY |
            com_sun_glass_events_KeyEvent_MODIFIER_BUTTON_BACK |
            com_sun_glass_events_KeyEvent_MODIFIER_BUTTON_FORWARD);
    jint button = com_sun_glass_events_MouseEvent_BUTTON_NONE;

    if (isDrag && WindowContext::sm_mouse_drag_window == NULL) {
        // Upper layers expects from us Windows behavior:
        // all mouse events should be delivered to window where drag begins
        // and no exit/enter event should be reported during this drag.
        // We can grab mouse pointer for these needs.
        grab_mouse_drag_focus();
    }

    if (glass_modifier & com_sun_glass_events_KeyEvent_MODIFIER_BUTTON_PRIMARY) {
        button = com_sun_glass_events_MouseEvent_BUTTON_LEFT;
    } else if (glass_modifier & com_sun_glass_events_KeyEvent_MODIFIER_BUTTON_MIDDLE) {
        button = com_sun_glass_events_MouseEvent_BUTTON_OTHER;
    } else if (glass_modifier & com_sun_glass_events_KeyEvent_MODIFIER_BUTTON_SECONDARY) {
        button = com_sun_glass_events_MouseEvent_BUTTON_RIGHT;
    } else if (glass_modifier & com_sun_glass_events_KeyEvent_MODIFIER_BUTTON_BACK) {
        button = com_sun_glass_events_MouseEvent_BUTTON_BACK;
    } else if (glass_modifier & com_sun_glass_events_KeyEvent_MODIFIER_BUTTON_FORWARD) {
        button = com_sun_glass_events_MouseEvent_BUTTON_FORWARD;
    }

    if (jview) {
        mainEnv->CallVoidMethod(jview, jViewNotifyMouse,
                isDrag ? com_sun_glass_events_MouseEvent_DRAG : com_sun_glass_events_MouseEvent_MOVE,
                button,
                (jint) event->x, (jint) event->y,
                (jint) event->x_root, (jint) event->y_root,
                glass_modifier,
                JNI_FALSE,
                JNI_FALSE);
        CHECK_JNI_EXCEPTION(mainEnv)
    }
}

void WindowContext::process_mouse_scroll(GdkEventScroll* event) {
    jdouble dx = 0;
    jdouble dy = 0;

    // converting direction to change in pixels
    switch (event->direction) {
#if GTK_CHECK_VERSION(3, 4, 0)
        case GDK_SCROLL_SMOOTH:
            //FIXME 3.4 ???
            break;
#endif
        case GDK_SCROLL_UP:
            dy = 1;
            break;
        case GDK_SCROLL_DOWN:
            dy = -1;
            break;
        case GDK_SCROLL_LEFT:
            dx = 1;
            break;
        case GDK_SCROLL_RIGHT:
            dx = -1;
            break;
    }
    if (event->state & GDK_SHIFT_MASK) {
        jdouble t = dy;
        dy = dx;
        dx = t;
    }
    if (jview) {
        mainEnv->CallVoidMethod(jview, jViewNotifyScroll,
                (jint) event->x, (jint) event->y,
                (jint) event->x_root, (jint) event->y_root,
                dx, dy,
                gdk_modifier_mask_to_glass(event->state),
                (jint) 0, (jint) 0,
                (jint) 0, (jint) 0,
                (jdouble) 40.0, (jdouble) 40.0);
        CHECK_JNI_EXCEPTION(mainEnv)
    }
}

void WindowContext::process_mouse_cross(GdkEventCrossing* event) {
    bool enter = event->type == GDK_ENTER_NOTIFY;
    if (jview) {
        guint state = event->state;
        if (enter) { // workaround for RT-21590
            state &= ~MOUSE_BUTTONS_MASK;
        }

        if (enter != is_mouse_entered) {
            is_mouse_entered = enter;
            mainEnv->CallVoidMethod(jview, jViewNotifyMouse,
                    enter ? com_sun_glass_events_MouseEvent_ENTER : com_sun_glass_events_MouseEvent_EXIT,
                    com_sun_glass_events_MouseEvent_BUTTON_NONE,
                    (jint) event->x, (jint) event->y,
                    (jint) event->x_root, (jint) event->y_root,
                    gdk_modifier_mask_to_glass(state),
                    JNI_FALSE,
                    JNI_FALSE);
            CHECK_JNI_EXCEPTION(mainEnv)
        }
    }
}

void WindowContext::process_key(GdkEventKey* event) {
    bool press = event->type == GDK_KEY_PRESS;
    jint glassKey = get_glass_key(event);
    jint glassModifier = gdk_modifier_mask_to_glass(event->state);
    if (press) {
        glassModifier |= glass_key_to_modifier(glassKey);
    } else {
        glassModifier &= ~glass_key_to_modifier(glassKey);
    }
    jcharArray jChars = NULL;
    jchar key = gdk_keyval_to_unicode(event->keyval);
    if (key >= 'a' && key <= 'z' && (event->state & GDK_CONTROL_MASK)) {
        key = key - 'a' + 1; // map 'a' to ctrl-a, and so on.
    }

    if (key > 0) {
        jChars = mainEnv->NewCharArray(1);
        if (jChars) {
            mainEnv->SetCharArrayRegion(jChars, 0, 1, &key);
            CHECK_JNI_EXCEPTION(mainEnv)
        }
    } else {
        jChars = mainEnv->NewCharArray(0);
    }

    if (!jview) {
        return;
    }

    mainEnv->CallVoidMethod(jview, jViewNotifyKey,
            (press) ? com_sun_glass_events_KeyEvent_PRESS
                    : com_sun_glass_events_KeyEvent_RELEASE,
            glassKey,
            jChars,
            glassModifier);
    CHECK_JNI_EXCEPTION(mainEnv)

    if (press && key > 0) { // TYPED events should only be sent for printable characters.
        mainEnv->CallVoidMethod(jview, jViewNotifyKey,
                com_sun_glass_events_KeyEvent_TYPED,
                com_sun_glass_events_KeyEvent_VK_UNDEFINED,
                jChars,
                glassModifier);
        CHECK_JNI_EXCEPTION(mainEnv)
    }
}

void WindowContext::process_state(GdkEventWindowState* event) {
    if (event->changed_mask & (GDK_WINDOW_STATE_ICONIFIED | GDK_WINDOW_STATE_MAXIMIZED)) {

        if (event->changed_mask & GDK_WINDOW_STATE_ICONIFIED) {
            is_iconified = event->new_window_state & GDK_WINDOW_STATE_ICONIFIED;
        }

        if (event->changed_mask & GDK_WINDOW_STATE_MAXIMIZED) {
            is_maximized = event->new_window_state & GDK_WINDOW_STATE_MAXIMIZED;
        }

        jint stateChangeEvent;

        if (is_iconified) {
            stateChangeEvent = com_sun_glass_events_WindowEvent_MINIMIZE;
        } else if (is_maximized) {
            stateChangeEvent = com_sun_glass_events_WindowEvent_MAXIMIZE;
        } else {
            stateChangeEvent = com_sun_glass_events_WindowEvent_RESTORE;
        }

        notify_state(stateChangeEvent);
    } else if (event->changed_mask & GDK_WINDOW_STATE_ABOVE) {
        notify_on_top(event->new_window_state & GDK_WINDOW_STATE_ABOVE);
    }

    if (event->changed_mask & GDK_WINDOW_STATE_FULLSCREEN) {
        is_fullscreen = event->new_window_state & GDK_WINDOW_STATE_FULLSCREEN;
    }

//FIXME
//    if (event->changed_mask & GDK_WINDOW_STATE_MAXIMIZED
//        && !(event->new_window_state & GDK_WINDOW_STATE_MAXIMIZED)) {
//        gtk_window_resize(GTK_WINDOW(window), geometry_get_content_width(&geometry),
//                                    geometry_get_content_height(&geometry));
//    }
}

void WindowContext::process_configure_view(GdkEventConfigure* event) {
    int w = event->width;
    int h = event->height;

    if (jview) {
        mainEnv->CallVoidMethod(jview, jViewNotifyResize, w, h);
        CHECK_JNI_EXCEPTION(mainEnv)
    }

    g_print("configure_view: %d, %d\n", w, h);
}

void WindowContext::process_configure_window(GdkEventConfigure* event) {
    int ww = event->width;
    int wh = event->height;

    g_print("configure_window: %d, %d\n", ww, wh);

    // Do not report if iconified, because Java side would set the state to NORMAL
    if (jwindow && !is_iconified) {
        mainEnv->CallVoidMethod(jwindow, jWindowNotifyResize,
                (is_maximized)
                    ? com_sun_glass_events_WindowEvent_MAXIMIZE
                    : com_sun_glass_events_WindowEvent_RESIZE,
                ww, wh);
        CHECK_JNI_EXCEPTION(mainEnv)
    }

    mainEnv->CallVoidMethod(jwindow, jWindowNotifyMove, event->x, event->y);
    CHECK_JNI_EXCEPTION(mainEnv)

    if (jview) {
        mainEnv->CallVoidMethod(jview, jViewNotifyView,
                com_sun_glass_events_ViewEvent_MOVE);
        CHECK_JNI_EXCEPTION(mainEnv)
    }

    glong to_screen = getScreenPtrForLocation(event->x, event->y);
    if (to_screen != -1) {
        if (to_screen != screen) {
            if (jwindow) {
                //notify screen changed
                jobject jScreen = createJavaScreen(mainEnv, to_screen);
                mainEnv->CallVoidMethod(jwindow, jWindowNotifyMoveToAnotherScreen, jScreen);
                CHECK_JNI_EXCEPTION(mainEnv)
            }
            screen = to_screen;
        }
    }
}

void WindowContext::notify_state(jint glass_state) {
    if (glass_state == com_sun_glass_events_WindowEvent_RESTORE) {
        if (is_maximized) {
            glass_state = com_sun_glass_events_WindowEvent_MAXIMIZE;
        }

        int w, h;
        glass_gdk_window_get_size(gdk_window, &w, &h);
        if (jview) {
            mainEnv->CallVoidMethod(jview,
                    jViewNotifyRepaint,
                    0, 0, w, h);
            CHECK_JNI_EXCEPTION(mainEnv);
        }
    }

    if (jwindow) {
       mainEnv->CallVoidMethod(jwindow,
               jGtkWindowNotifyStateChanged,
               glass_state);
       CHECK_JNI_EXCEPTION(mainEnv);
    }
}

void WindowContext::set_cursor(GdkCursor* cursor) {
    if (!is_in_drag()) {
        if (WindowContext::sm_mouse_drag_window) {
            glass_gdk_mouse_devices_grab_with_cursor(
                    WindowContext::sm_mouse_drag_window->get_gdk_window(), cursor, FALSE);
        } else if (WindowContext::sm_grab_window) {
            glass_gdk_mouse_devices_grab_with_cursor(
                    WindowContext::sm_grab_window->get_gdk_window(), cursor, TRUE);
        }
    }
    gdk_window_set_cursor(gdk_window, cursor);
}

void WindowContext::set_background(float r, float g, float b) {
    GdkRGBA rgba = {0, 0, 0, 1.};
    rgba.red = r;
    rgba.green = g;
    rgba.blue = b;

    gtk_widget_override_background_color(window, GTK_STATE_FLAG_NORMAL, &rgba);
}

void WindowContext::set_minimized(bool minimize) {
    is_iconified = minimize;
    if (minimize) {
        gtk_window_iconify(GTK_WINDOW(window));
    } else {
        gtk_window_deiconify(GTK_WINDOW(window));
        gdk_window_focus(gdk_window, GDK_CURRENT_TIME);
    }
}

void WindowContext::set_maximized(bool maximize) {
    is_maximized = maximize;
    if (maximize) {
        gtk_window_maximize(GTK_WINDOW(window));
    } else {
        gtk_window_unmaximize(GTK_WINDOW(window));
    }
}

void WindowContext::set_bounds(int x, int y, bool xSet, bool ySet, int w, int h, int cw, int ch,
                                  float gravity_x, float gravity_y) {
    g_print("set_bounds -> x = %d, y = %d, xset = %d, yset = %d, w = %d, h = %d, cw = %d, ch = %d, gx = %f, gy = %f\n",
            x, y, xSet, ySet, w, h, cw, ch, gravity_x, gravity_y);

    if (w > 0 || h > 0) {
        update_window_constraints();
        if (gtk_widget_get_realized(window)) {
            gtk_window_resize(GTK_WINDOW(window), w, h);
        } else {
            gtk_window_set_default_size(GTK_WINDOW(window), w, h);
            mainEnv->CallVoidMethod(jwindow, jWindowNotifyResize,
                         com_sun_glass_events_WindowEvent_RESIZE, w, h);
            CHECK_JNI_EXCEPTION(mainEnv)
        }
    } else if (cw > 0 || ch > 0) {
        gtk_widget_set_size_request(drawing_area, cw, ch);
    }

    if (xSet || ySet) {
        g_print("move: %d, %d\n", x, y);
        gtk_window_move(GTK_WINDOW(window), x, y);
    }
}

void WindowContext::set_resizable(bool res) {
    resizable.value = res;
    update_window_constraints();
}

void WindowContext::set_focusable(bool focusable) {
    gtk_window_set_accept_focus(GTK_WINDOW(window), focusable ? TRUE : FALSE);
}

void WindowContext::set_title(const char* title) {
    if (headerbar != NULL) {
        gtk_header_bar_set_title(GTK_HEADER_BAR(headerbar), title);
    }
}

void WindowContext::set_alpha(double alpha) {
    gtk_window_set_opacity(GTK_WINDOW(window), (gdouble)alpha);
}

void WindowContext::set_enabled(bool enabled) {
    is_disabled = !enabled;
    update_window_constraints();
}

void WindowContext::set_minimum_size(int w, int h) {
    resizable.minw = (w <= 0) ? 1 : w;
    resizable.minh = (h <= 0) ? 1 : h;
    update_window_constraints();
}

void WindowContext::set_maximum_size(int w, int h) {
    resizable.maxw = w;
    resizable.maxh = h;
    update_window_constraints();
}

void WindowContext::set_icon(GdkPixbuf* pixbuf) {
    gtk_window_set_icon(GTK_WINDOW(window), pixbuf);
}

void WindowContext::set_modal(bool modal, WindowContext* parent) {
    if (modal) {
        //gtk_window_set_type_hint(GTK_WINDOW(gtk_widget), GDK_WINDOW_TYPE_HINT_DIALOG);
        if (parent) {
            gtk_window_set_transient_for(GTK_WINDOW(window), parent->get_gtk_window());
        }
    }
    gtk_window_set_modal(GTK_WINDOW(window), modal ? TRUE : FALSE);
}

void WindowContext::set_level(int level) {
    if (level == com_sun_glass_ui_Window_Level_NORMAL) {
        on_top = false;
    } else if (level == com_sun_glass_ui_Window_Level_FLOATING
            || level == com_sun_glass_ui_Window_Level_TOPMOST) {
        on_top = true;
    }
    // We need to emulate always on top behaviour on child windows

    if (!on_top_inherited()) {
        update_ontop_tree(on_top);
    }
}

void WindowContext::set_visible(bool visible) {
    if (visible) {
        gtk_widget_show_all(window);

        int w, h;
        gtk_window_get_default_size(GTK_WINDOW(window), &w, &h);

        if (w == -1 || h == -1) {
            set_bounds(0, 0, false, false, 320, 200, -1, -1, 0, 0);
        }

        //JDK-8220272 - fire event first because GDK_FOCUS_CHANGE is not always in order
        if (jwindow && isEnabled()) {
            mainEnv->CallVoidMethod(jwindow, jWindowNotifyFocus, com_sun_glass_events_WindowEvent_FOCUS_GAINED);
            CHECK_JNI_EXCEPTION(mainEnv);
        }
    } else {
        gtk_widget_hide(window);
        if (jview && is_mouse_entered) {
            is_mouse_entered = false;
            mainEnv->CallVoidMethod(jview, jViewNotifyMouse,
                    com_sun_glass_events_MouseEvent_EXIT,
                    com_sun_glass_events_MouseEvent_BUTTON_NONE,
                    0, 0,
                    0, 0,
                    0,
                    JNI_FALSE,
                    JNI_FALSE);
            CHECK_JNI_EXCEPTION(mainEnv)
        }
    }
}

void WindowContext::set_owner(WindowContext * owner_ctx) {
    owner = owner_ctx;
}

void WindowContext::to_front() {
    gdk_window_raise(gdk_window);
}

void WindowContext::to_back() {
    gdk_window_lower(gdk_window);
}

void WindowContext::request_focus() {
    if (is_visible()) {
        gtk_window_present(GTK_WINDOW(window));
    }
}

void WindowContext::notify_on_top(bool top) {
    // Do not report effective (i.e. native) values to the FX, only if the user sets it manually
    if (top != effective_on_top() && jwindow) {
        if (on_top_inherited() && !top) {
            // Disallow user's "on top" handling on windows that inherited the property
            gtk_window_set_keep_above(GTK_WINDOW(window), TRUE);
        } else {
            on_top = top;
            update_ontop_tree(top);
            mainEnv->CallVoidMethod(jwindow,
                    jWindowNotifyLevelChanged,
                    top ? com_sun_glass_ui_Window_Level_FLOATING :  com_sun_glass_ui_Window_Level_NORMAL);
            CHECK_JNI_EXCEPTION(mainEnv);
        }
    }
}

void WindowContext::enter_fullscreen() {
    gtk_window_fullscreen(GTK_WINDOW(window));
    is_fullscreen = true;
}

void WindowContext::exit_fullscreen() {
    gtk_window_unfullscreen(GTK_WINDOW(window));
}


// Applied to a temporary full screen window to prevent sending events to Java
void WindowContext::detach_from_java() {
    if (jview) {
        mainEnv->DeleteGlobalRef(jview);
        jview = NULL;
    }
    if (jwindow) {
        mainEnv->DeleteGlobalRef(jwindow);
        jwindow = NULL;
    }
}

void WindowContext::increment_events_counter() {
    ++events_processing_cnt;
}

void WindowContext::decrement_events_counter() {
    --events_processing_cnt;
}

size_t WindowContext::get_events_count() {
    return events_processing_cnt;
}

bool WindowContext::is_dead() {
    return can_be_deleted;
}

void WindowContext::applyShapeMask(void* data, uint width, uint height) {
    if (frame_type != TRANSPARENT) {
        return;
    }

    glass_window_apply_shape_mask(gtk_widget_get_window(window), data, width, height);
}

void WindowContext::update_window_constraints() {
    GdkGeometry hints;

    if (resizable.value && !is_disabled) {

        int min_w = (resizable.minw == -1) ? 1 : resizable.minw;
        int min_h =  (resizable.minh == -1) ? 1 : resizable.minh;

        hints.min_width = (min_w < 1) ? 1 : min_w;
        hints.min_height = (min_h < 1) ? 1 : min_h;

        hints.max_width = (resizable.maxw == -1) ? G_MAXINT : resizable.maxw;

        hints.max_height = (resizable.maxh == -1) ? G_MAXINT : resizable.maxh;
    } else {
        int w = gtk_widget_get_allocated_width(drawing_area);
        int h = gtk_widget_get_allocated_height(drawing_area);

        hints.min_width = w;
        hints.min_height = h;
        hints.max_width = w;
        hints.max_height = h;
    }

    gtk_window_set_geometry_hints(GTK_WINDOW(window), NULL, &hints,
                                  (GdkWindowHints)(GDK_HINT_MIN_SIZE | GDK_HINT_MAX_SIZE));
}


void WindowContext::update_view_size() {
    notify_view_resize();
}

void WindowContext::update_ontop_tree(bool on_top) {
    bool effective_on_top = on_top || this->on_top;
    gtk_window_set_keep_above(GTK_WINDOW(window), effective_on_top ? TRUE : FALSE);
    for (std::set<WindowContext*>::iterator it = children.begin(); it != children.end(); ++it) {
        (*it)->update_ontop_tree(effective_on_top);
    }
}

bool WindowContext::on_top_inherited() {
    WindowContext* o = owner;
    while (o) {
        WindowContext* topO = dynamic_cast<WindowContext*>(o);
        if (!topO) break;
        if (topO->on_top) {
            return true;
        }
        o = topO->owner;
    }
    return false;
}

bool WindowContext::effective_on_top() {
    if (owner) {
        WindowContext* topO = dynamic_cast<WindowContext*>(owner);
        return (topO && topO->effective_on_top()) || on_top;
    }
    return on_top;
}

void WindowContext::notify_view_resize() {
    if (jview) {
        int cw = gtk_widget_get_allocated_width(drawing_area);
        int ch = gtk_widget_get_allocated_height(drawing_area);

        mainEnv->CallVoidMethod(jview, jViewNotifyResize, cw, ch);
        CHECK_JNI_EXCEPTION(mainEnv)
    }
}

void destroy_and_delete_ctx(WindowContext* ctx) {
    if (ctx) {
        ctx->process_destroy();

        if (!ctx->get_events_count()) {
            delete ctx;
        }
        // else: ctx will be deleted in EventsCounterHelper after completing
        // an event processing
    }
}

WindowContext::~WindowContext() {
    disableIME();
    gtk_widget_destroy(window);
}
