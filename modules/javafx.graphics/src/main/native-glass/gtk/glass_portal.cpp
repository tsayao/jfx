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

#include "glass_portal.h"

gchar* Portal::get_parent_handle(GdkWindow *gdk_window) {
    if (!gdk_window) return g_strdup("");
    return g_strdup_printf("x11:%lx",
            (unsigned long) gdk_x11_window_get_xid(gdk_window));
}

gchar* Portal::uri_to_path(const gchar *uri) {
    GFile *file = g_file_new_for_uri(uri);
    gchar *path = g_file_get_path(file);
    g_object_unref(file);
    return path;
}

gchar* Portal::add_handle_token(GVariantBuilder *opts) {
    gchar *token = g_strdup_printf("javafx%u", g_random_int());
    g_variant_builder_add(opts, "{sv}", "handle_token",
            g_variant_new_string(token));
    return token;
}

void Portal::on_response(GDBusConnection *connection,
                          const gchar *sender_name,
                          const gchar *object_path,
                          const gchar *interface_name,
                          const gchar *signal_name,
                          GVariant *parameters,
                          gpointer user_data) {
    (void)connection; (void)sender_name; (void)object_path;
    (void)interface_name; (void)signal_name;

    CallbackData *cb = (CallbackData *) user_data;
    g_variant_get(parameters, "(u@a{sv})",
            &cb->response.status, &cb->response.results);
    g_main_loop_quit(cb->loop);
}

Portal::Response Portal::call(const char *iface,
                               const char *method,
                               GVariant *params,
                               const gchar *handle_token) {
    Response response = {};

    GDBusConnection *bus = g_bus_get_sync(G_BUS_TYPE_SESSION, NULL, NULL);
    if (!bus) {
        if (params) g_variant_unref(params);
        return response;
    }

    // Build the expected Request object path from sender + token
    const gchar *sender = g_dbus_connection_get_unique_name(bus);
    gchar *sender_path = g_strdup(sender + 1);
    for (gchar *p = sender_path; *p; p++) {
        if (*p == '.') *p = '_';
    }
    gchar *request_path = g_strdup_printf(
            PORTAL_OBJECT_PATH "/request/%s/%s",
            sender_path, handle_token);
    g_free(sender_path);

    // Subscribe to Response BEFORE making the call
    CallbackData cb = {};
    cb.loop = g_main_loop_new(NULL, FALSE);

    guint sig_id = g_dbus_connection_signal_subscribe(bus,
            PORTAL_BUS_NAME,
            PORTAL_IFACE_REQUEST,
            "Response",
            request_path,
            NULL,
            G_DBUS_SIGNAL_FLAGS_NO_MATCH_RULE,
            on_response,
            &cb,
            NULL);

    // Make the D-Bus call
    GVariant *ret = g_dbus_connection_call_sync(bus,
            PORTAL_BUS_NAME,
            PORTAL_OBJECT_PATH,
            iface,
            method,
            params,
            G_VARIANT_TYPE("(o)"),
            G_DBUS_CALL_FLAGS_NONE,
            -1, NULL, NULL);

    if (ret) {
        g_variant_unref(ret);
        g_main_loop_run(cb.loop);
        response = cb.response;
    }

    g_dbus_connection_signal_unsubscribe(bus, sig_id);
    g_main_loop_unref(cb.loop);
    g_free(request_path);
    g_object_unref(bus);

    return response;
}

