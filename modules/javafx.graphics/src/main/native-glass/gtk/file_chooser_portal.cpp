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

#include "file_chooser_portal.h"

/* ---- filters ---- */

void FileChooserPortal::add_filters(GVariantBuilder *opts,
                                     const std::vector<Filter> &filters,
                                     int default_filter) {
    if (filters.empty()) return;

    GVariantBuilder fb;
    g_variant_builder_init(&fb, G_VARIANT_TYPE("a(sa(us))"));

    for (size_t i = 0; i < filters.size(); i++) {
        const Filter &f = filters[i];

        GVariantBuilder pb;
        g_variant_builder_init(&pb, G_VARIANT_TYPE("a(us)"));
        for (const auto &p : f.patterns) {
            g_variant_builder_add(&pb, "(us)", (guint32) 0, p.c_str());
        }
        g_variant_builder_add(&fb, "(sa(us))", f.name.c_str(), &pb);

        if ((int) i == default_filter) {
            GVariantBuilder cpb;
            g_variant_builder_init(&cpb, G_VARIANT_TYPE("a(us)"));
            for (const auto &p : f.patterns) {
                g_variant_builder_add(&cpb, "(us)", (guint32) 0, p.c_str());
            }
            g_variant_builder_add(opts, "{sv}", "current_filter",
                    g_variant_new("(sa(us))", f.name.c_str(), &cpb));
        }
    }

    g_variant_builder_add(opts, "{sv}", "filters",
            g_variant_builder_end(&fb));
}

/* ---- response parsing ---- */

FileChooserPortal::Result FileChooserPortal::parse_response(
        const Portal::Response &resp) {

    Result result = {};

    if (resp.status != 0 || !resp.results) return result;

    result.accepted = true;

    GVariant *v = g_variant_lookup_value(resp.results, "uris",
            G_VARIANT_TYPE_STRING_ARRAY);
    if (v) {
        gsize n = 0;
        gchar **uris = g_variant_dup_strv(v, &n);
        for (gsize i = 0; i < n; i++) {
            gchar *path = Portal::uri_to_path(uris[i]);
            if (path) {
                result.paths.emplace_back(path);
                g_free(path);
            }
        }
        g_strfreev(uris);
        g_variant_unref(v);
    }

    v = g_variant_lookup_value(resp.results, "current_filter", NULL);
    if (v) {
        const gchar *name = NULL;
        GVariant *patterns = NULL;
        g_variant_get(v, "(&s@a(us))", &name, &patterns);
        if (name) result.filter_name = name;
        if (patterns) g_variant_unref(patterns);
        g_variant_unref(v);
    }

    return result;
}

/* ---- portal call ---- */

FileChooserPortal::Result FileChooserPortal::call(
        GdkWindow *parent_window,
        const char *title,
        const char *method,
        GVariantBuilder *opts) {

    gchar *token = Portal::add_handle_token(opts);
    g_variant_builder_add(opts, "{sv}", "modal",
            g_variant_new_boolean(TRUE));

    gchar *parent_handle = Portal::get_parent_handle(parent_window);

    Portal::Response resp = Portal::call(
            PORTAL_IFACE_FILE_CHOOSER,
            method,
            g_variant_new("(ssa{sv})", parent_handle, title ? title : "", opts),
            token);

    g_free(parent_handle);
    g_free(token);

    Result result = parse_response(resp);
    if (resp.results) g_variant_unref(resp.results);

    return result;
}

/* ---- public API ---- */

FileChooserPortal::Result FileChooserPortal::open_file(
        GdkWindow *parent_window,
        const char *title,
        const char *folder,
        bool multiple,
        const std::vector<Filter> &filters,
        int default_filter) {

    GVariantBuilder opts;
    g_variant_builder_init(&opts, G_VARIANT_TYPE_VARDICT);

    g_variant_builder_add(&opts, "{sv}", "multiple",
            g_variant_new_boolean(multiple));
    add_filters(&opts, filters, default_filter);
    if (folder) {
        g_variant_builder_add(&opts, "{sv}", "current_folder",
                g_variant_new_bytestring(folder));
    }

    return call(parent_window, title, "OpenFile", &opts);
}

FileChooserPortal::Result FileChooserPortal::save_file(
        GdkWindow *parent_window,
        const char *title,
        const char *folder,
        const char *filename,
        const std::vector<Filter> &filters,
        int default_filter) {

    GVariantBuilder opts;
    g_variant_builder_init(&opts, G_VARIANT_TYPE_VARDICT);

    add_filters(&opts, filters, default_filter);
    if (folder) {
        g_variant_builder_add(&opts, "{sv}", "current_folder",
                g_variant_new_bytestring(folder));
    }
    if (filename) {
        g_variant_builder_add(&opts, "{sv}", "current_name",
                g_variant_new_string(filename));
    }

    return call(parent_window, title, "SaveFile", &opts);
}

FileChooserPortal::Result FileChooserPortal::open_folder(
        GdkWindow *parent_window,
        const char *title,
        const char *folder) {

    GVariantBuilder opts;
    g_variant_builder_init(&opts, G_VARIANT_TYPE_VARDICT);

    g_variant_builder_add(&opts, "{sv}", "directory",
            g_variant_new_boolean(TRUE));
    if (folder) {
        g_variant_builder_add(&opts, "{sv}", "current_folder",
                g_variant_new_bytestring(folder));
    }

    return call(parent_window, title, "OpenFile", &opts);
}

