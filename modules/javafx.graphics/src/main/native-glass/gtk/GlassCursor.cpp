/*
 * Copyright (c) 2011, 2014, Oracle and/or its affiliates. All rights reserved.
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
 * accompanied this code);
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
#include <com_sun_glass_ui_gtk_GtkCursor.h>

#include <gdk/gdk.h>
#include <stdlib.h>
#include <jni.h>

#include "com_sun_glass_ui_Cursor.h"
#include "glass_general.h"

static GdkCursor* get_cursor(const char *name) {
    return gdk_cursor_new_from_name(gdk_display_get_default(), name);
}

GdkCursor* get_native_cursor(int type) {
    switch (type) {
        case com_sun_glass_ui_Cursor_CURSOR_DEFAULT:
            return get_cursor("default");
        case com_sun_glass_ui_Cursor_CURSOR_TEXT:
            return get_cursor("text");
        case com_sun_glass_ui_Cursor_CURSOR_CROSSHAIR:
            return get_cursor("crosshair");
        case com_sun_glass_ui_Cursor_CURSOR_CLOSED_HAND:
            return get_cursor("grabbing");
        case com_sun_glass_ui_Cursor_CURSOR_OPEN_HAND:
            return get_cursor("grab");
        case com_sun_glass_ui_Cursor_CURSOR_POINTING_HAND:
            return get_cursor("pointer");
        case com_sun_glass_ui_Cursor_CURSOR_RESIZE_UP:
            return get_cursor("n-resize");
        case com_sun_glass_ui_Cursor_CURSOR_RESIZE_DOWN:
            return get_cursor("s-resize");
        case com_sun_glass_ui_Cursor_CURSOR_RESIZE_UPDOWN:
            return get_cursor("ns-resize");
        case com_sun_glass_ui_Cursor_CURSOR_RESIZE_LEFT:
            return get_cursor("w-resize");
        case com_sun_glass_ui_Cursor_CURSOR_RESIZE_RIGHT:
            return get_cursor("e-resize");
        case com_sun_glass_ui_Cursor_CURSOR_RESIZE_LEFTRIGHT:
            return get_cursor("ew-resize");
        case com_sun_glass_ui_Cursor_CURSOR_RESIZE_SOUTHWEST:
            return get_cursor("sw-resize");
        case com_sun_glass_ui_Cursor_CURSOR_RESIZE_NORTHEAST:
            return get_cursor("ne-resize");
        case com_sun_glass_ui_Cursor_CURSOR_RESIZE_SOUTHEAST:
            return get_cursor("se-resize");
        case com_sun_glass_ui_Cursor_CURSOR_RESIZE_NORTHWEST:
            return get_cursor("nw-resize");
        case com_sun_glass_ui_Cursor_CURSOR_MOVE:
            return get_cursor("move");
        case com_sun_glass_ui_Cursor_CURSOR_WAIT:
            return get_cursor("wait");
        case com_sun_glass_ui_Cursor_CURSOR_DISAPPEAR:
        case com_sun_glass_ui_Cursor_CURSOR_NONE:
            return get_cursor("none");
        default:
            return get_cursor("default");
    }
}

extern "C" {

/*
 * Class:     com_sun_glass_ui_gtk_GtkCursor
 * Method:    _createCursor
 * Signature: (IILcom/sun/glass/ui/Pixels;)J
 */
JNIEXPORT jlong JNICALL Java_com_sun_glass_ui_gtk_GtkCursor__1createCursor
  (JNIEnv * env, jobject obj, jint x, jint y, jobject pixels)
{
    (void)obj;

    GdkPixbuf *pixbuf = NULL;
    GdkCursor *cursor = NULL;
    env->CallVoidMethod(pixels, jPixelsAttachData, PTR_TO_JLONG(&pixbuf));
    if (!EXCEPTION_OCCURED(env)) {
        cursor = gdk_cursor_new_from_pixbuf(gdk_display_get_default(), pixbuf, x, y);
    }
    g_object_unref(pixbuf);

    return PTR_TO_JLONG(cursor);
}

/*
 * Class:     com_sun_glass_ui_gtk_GtkCursor
 * Method:    _getBestSize
 * Signature: (II)Lcom.sun.glass.ui.Size
 */
JNIEXPORT jobject JNICALL Java_com_sun_glass_ui_gtk_GtkCursor__1getBestSize
        (JNIEnv *env, jclass jCursorClass, jint width, jint height)
{
    (void)jCursorClass;
    (void)width;
    (void)height;

    int size = gdk_display_get_default_cursor_size(gdk_display_get_default());

    jclass jc = env->FindClass("com/sun/glass/ui/Size");
    if (env->ExceptionCheck()) return NULL;
    jobject jo =  env->NewObject(
            jc,
            jSizeInit,
            size,
            size);
    EXCEPTION_OCCURED(env);
    return jo;
}

} // extern "C"
