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

#ifndef GLASS_PORTAL_H
#define GLASS_PORTAL_H

#include <gdk/gdk.h>
#include <gdk/gdkx.h>
#include <gtk/gtk.h>

#define PORTAL_BUS_NAME    "org.freedesktop.portal.Desktop"
#define PORTAL_OBJECT_PATH "/org/freedesktop/portal/desktop"
#define PORTAL_IFACE_REQUEST "org.freedesktop.portal.Request"

/**
 * Generic helper for calling xdg-desktop-portal D-Bus methods
 * that follow the Request/Response pattern.
 */
class Portal {
public:
    struct Response {
        guint32 status;    // 0 = success, 1 = cancelled, 2 = other
        GVariant *results; // a{sv} dict or NULL — caller must g_variant_unref()
    };

    /**
     * Get the parent window handle for portal calls.
     * For X11: "x11:<XID>". Returns "" if gdk_window is NULL.
     * Caller must g_free().
     */
    static gchar* get_parent_handle(GdkWindow *gdk_window);

    /**
     * Convert a file:// URI to a filesystem path.
     * Caller must g_free().
     */
    static gchar* uri_to_path(const gchar *uri);

    /**
     * Generate a unique handle_token, add it to an options GVariantBuilder,
     * and return the token. Caller must g_free() the returned string.
     */
    static gchar* add_handle_token(GVariantBuilder *opts);

    /**
     * Call a portal D-Bus method that uses the Request/Response pattern
     * and block until the response arrives.
     *
     * @param iface        Portal interface (e.g. "org.freedesktop.portal.FileChooser")
     * @param method       Method name (e.g. "OpenFile")
     * @param params       Full method parameters (consumed by the call)
     * @param handle_token Token previously returned by add_handle_token()
     * @return Response with status and results (results may be NULL on failure)
     */
    static Response call(const char *iface,
                         const char *method,
                         GVariant *params,
                         const gchar *handle_token);

private:
    struct CallbackData {
        Response response;
        GMainLoop *loop;
    };

    static void on_response(GDBusConnection *connection,
                            const gchar *sender_name,
                            const gchar *object_path,
                            const gchar *interface_name,
                            const gchar *signal_name,
                            GVariant *parameters,
                            gpointer user_data);
};

#endif /* GLASS_PORTAL_H */

