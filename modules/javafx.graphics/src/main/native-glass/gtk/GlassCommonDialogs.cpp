/*
 * Copyright (c) 2011, 2026, Oracle and/or its affiliates. All rights reserved.
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
#include <com_sun_glass_ui_gtk_GtkCommonDialogs.h>
#include "glass_general.h"
#include "glass_window.h"

#include <gdk/gdk.h>
#include <gdk/gdkx.h>
#include <gtk/gtk.h>

#include <cstring>
#include <cstdlib>

static gboolean jstring_to_utf_get(JNIEnv *env, jstring jstr,
                                   const char **cstr) {
    const char *newstr;

    if (jstr == NULL) {
        *cstr = NULL;
        return TRUE;
    }

    newstr = env->GetStringUTFChars(jstr, NULL);
    if (newstr != NULL) {
        *cstr = newstr;
        return TRUE;
    }

    return FALSE;
}

static void jstring_to_utf_release(JNIEnv *env, jstring jstr,
                                   const char *cstr) {
    if (cstr != NULL) {
        env->ReleaseStringUTFChars(jstr, cstr);
    }
}

static GdkWindow *get_gdk_window(jlong handle) {
    return  (handle != 0)
                ? ((WindowContext*)JLONG_TO_PTR(handle))->get_gdk_window()
                : NULL;
}

static jobject create_empty_result() {
    jclass jFileChooserResult = (jclass) mainEnv->FindClass("com/sun/glass/ui/CommonDialogs$FileChooserResult");
    if (EXCEPTION_OCCURED(mainEnv)) return NULL;
    jmethodID jFileChooserResultInit = mainEnv->GetMethodID(jFileChooserResult, "<init>", "()V");
    if (EXCEPTION_OCCURED(mainEnv)) return NULL;
    jobject jResult = mainEnv->NewObject(jFileChooserResult, jFileChooserResultInit);
    if (EXCEPTION_OCCURED(mainEnv)) return NULL;
    return jResult;
}

/* ---- Portal FileChooser (org.freedesktop.portal.FileChooser) ----
 *
 * We call the portal D-Bus interface directly so we can pass the parent
 * window handle as "x11:<XID>" without needing a GtkWindow*.
 */

static gchar* get_parent_window_handle(GdkWindow *gdk_window) {
    if (!gdk_window) return g_strdup("");
    return g_strdup_printf("x11:%lx", (unsigned long) gdk_x11_window_get_xid(gdk_window));
}

static gchar* uri_to_path(const gchar *uri) {
    GFile *file = g_file_new_for_uri(uri);
    gchar *path = g_file_get_path(file);
    g_object_unref(file);
    return path;
}

struct PortalResponse {
    guint32 status;       // 0 = success, 1 = cancelled, 2 = other
    gchar **uris;
    gsize n_uris;
    gchar *filter_name;   // name of the selected filter (or NULL)
    GMainLoop *loop;
};

static void portal_response_free(PortalResponse *resp) {
    if (resp->uris) g_strfreev(resp->uris);
    g_free(resp->filter_name);
    if (resp->loop) g_main_loop_unref(resp->loop);
}

static void on_portal_response(GDBusConnection *connection,
                                const gchar *sender_name,
                                const gchar *object_path,
                                const gchar *interface_name,
                                const gchar *signal_name,
                                GVariant *parameters,
                                gpointer user_data) {
    (void)connection; (void)sender_name; (void)object_path;
    (void)interface_name; (void)signal_name;

    PortalResponse *resp = (PortalResponse *) user_data;
    GVariant *results = NULL;
    g_variant_get(parameters, "(u@a{sv})", &resp->status, &results);

    if (resp->status == 0 && results) {
        GVariant *v;

        v = g_variant_lookup_value(results, "uris", G_VARIANT_TYPE_STRING_ARRAY);
        if (v) {
            resp->uris = g_variant_dup_strv(v, &resp->n_uris);
            g_variant_unref(v);
        }

        v = g_variant_lookup_value(results, "current_filter", NULL);
        if (v) {
            const gchar *name = NULL;
            GVariant *patterns = NULL;
            g_variant_get(v, "(&s@a(us))", &name, &patterns);
            if (name) resp->filter_name = g_strdup(name);
            if (patterns) g_variant_unref(patterns);
            g_variant_unref(v);
        }
    }

    if (results) g_variant_unref(results);
    g_main_loop_quit(resp->loop);
}

/*
 * Subscribe to the portal Response signal BEFORE making the D-Bus method
 * call, using a handle_token for a predictable Request object path.
 */
static guint portal_subscribe(GDBusConnection *bus,
                               PortalResponse *resp,
                               gchar **out_token,
                               gchar **out_request_path) {
    const gchar *sender = g_dbus_connection_get_unique_name(bus);
    gchar *sender_path = g_strdup(sender + 1); // skip leading ':'
    for (gchar *p = sender_path; *p; p++) {
        if (*p == '.') *p = '_';
    }

    *out_token = g_strdup_printf("javafx%u", g_random_int());
    *out_request_path = g_strdup_printf(
            "/org/freedesktop/portal/desktop/request/%s/%s",
            sender_path, *out_token);
    g_free(sender_path);

    return g_dbus_connection_signal_subscribe(bus,
            "org.freedesktop.portal.Desktop",
            "org.freedesktop.portal.Request",
            "Response",
            *out_request_path,
            NULL,
            G_DBUS_SIGNAL_FLAGS_NO_MATCH_RULE,
            on_portal_response,
            resp,
            NULL);
}

/*
 * Build portal file filters from Java ExtensionFilter[].
 *
 * Portal type: a(sa(us))  —  array of (name, [(type, pattern), ...])
 *   type 0 = glob pattern, type 1 = MIME type
 *
 * Also sets "current_filter" in opts when default_filter_index is valid.
 */
static void build_portal_filters(JNIEnv *env,
                                  jobjectArray jFilters,
                                  int default_filter_index,
                                  GVariantBuilder *opts) {
    if (jFilters == NULL) return;

    jclass jcls = env->FindClass("com/sun/glass/ui/CommonDialogs$ExtensionFilter");
    if (EXCEPTION_OCCURED(env)) return;
    jmethodID jgetDesc = env->GetMethodID(jcls, "getDescription", "()Ljava/lang/String;");
    if (EXCEPTION_OCCURED(env)) return;
    jmethodID jgetExts = env->GetMethodID(jcls, "extensionsToArray", "()[Ljava/lang/String;");
    if (EXCEPTION_OCCURED(env)) return;

    jsize n_filters = env->GetArrayLength(jFilters);
    if (n_filters == 0) return;

    GVariantBuilder filters_builder;
    g_variant_builder_init(&filters_builder, G_VARIANT_TYPE("a(sa(us))"));

    for (jsize i = 0; i < n_filters; i++) {
        jobject jfilter = env->GetObjectArrayElement(jFilters, i);

        jstring jdesc = (jstring) env->CallObjectMethod(jfilter, jgetDesc);
        const char *desc = env->GetStringUTFChars(jdesc, NULL);

        jobjectArray jexts = (jobjectArray) env->CallObjectMethod(jfilter, jgetExts);
        jsize n_exts = env->GetArrayLength(jexts);

        GVariantBuilder patterns;
        g_variant_builder_init(&patterns, G_VARIANT_TYPE("a(us)"));
        for (jsize j = 0; j < n_exts; j++) {
            jstring jext = (jstring) env->GetObjectArrayElement(jexts, j);
            const char *ext = env->GetStringUTFChars(jext, NULL);
            g_variant_builder_add(&patterns, "(us)", (guint32) 0, ext);
            env->ReleaseStringUTFChars(jext, ext);
        }

        g_variant_builder_add(&filters_builder, "(sa(us))", desc, &patterns);

        if (i == default_filter_index) {
            GVariantBuilder cur_patterns;
            g_variant_builder_init(&cur_patterns, G_VARIANT_TYPE("a(us)"));
            for (jsize j = 0; j < n_exts; j++) {
                jstring jext = (jstring) env->GetObjectArrayElement(jexts, j);
                const char *ext = env->GetStringUTFChars(jext, NULL);
                g_variant_builder_add(&cur_patterns, "(us)", (guint32) 0, ext);
                env->ReleaseStringUTFChars(jext, ext);
            }
            g_variant_builder_add(opts, "{sv}", "current_filter",
                    g_variant_new("(sa(us))", desc, &cur_patterns));
        }

        env->ReleaseStringUTFChars(jdesc, desc);
    }

    g_variant_builder_add(opts, "{sv}", "filters",
            g_variant_builder_end(&filters_builder));
}

/*
 * Match the portal's selected filter name to a Java filter index.
 */
static int find_filter_index(JNIEnv *env, jobjectArray jFilters,
                              const gchar *name) {
    if (!name || !jFilters) return -1;

    jclass jcls = env->FindClass("com/sun/glass/ui/CommonDialogs$ExtensionFilter");
    if (EXCEPTION_OCCURED(env)) return -1;
    jmethodID jgetDesc = env->GetMethodID(jcls, "getDescription", "()Ljava/lang/String;");
    if (EXCEPTION_OCCURED(env)) return -1;

    jsize n = env->GetArrayLength(jFilters);
    for (jsize i = 0; i < n; i++) {
        jobject jfilter = env->GetObjectArrayElement(jFilters, i);
        jstring jdesc = (jstring) env->CallObjectMethod(jfilter, jgetDesc);
        const char *desc = env->GetStringUTFChars(jdesc, NULL);
        gboolean match = (g_strcmp0(name, desc) == 0);
        env->ReleaseStringUTFChars(jdesc, desc);
        if (match) return (int) i;
    }
    return -1;
}

extern "C" {

JNIEXPORT jobject JNICALL Java_com_sun_glass_ui_gtk_GtkCommonDialogs__1showFileChooser
  (JNIEnv *env, jclass clazz, jlong parent, jstring folder, jstring name, jstring title,
   jint type, jboolean multiple, jobjectArray jFilters, jint default_filter_index) {
    (void)clazz;

    const char* chooser_folder;
    const char* chooser_filename;
    const char* chooser_title;

    if (!jstring_to_utf_get(env, folder, &chooser_folder)) {
        return create_empty_result();
    }
    if (!jstring_to_utf_get(env, title, &chooser_title)) {
        jstring_to_utf_release(env, folder, chooser_folder);
        return create_empty_result();
    }
    if (!jstring_to_utf_get(env, name, &chooser_filename)) {
        jstring_to_utf_release(env, folder, chooser_folder);
        jstring_to_utf_release(env, title, chooser_title);
        return create_empty_result();
    }

    GDBusConnection *bus = g_bus_get_sync(G_BUS_TYPE_SESSION, NULL, NULL);
    if (!bus) {
        jstring_to_utf_release(env, folder, chooser_folder);
        jstring_to_utf_release(env, title, chooser_title);
        jstring_to_utf_release(env, name, chooser_filename);
        return create_empty_result();
    }

    PortalResponse resp = {};
    resp.loop = g_main_loop_new(NULL, FALSE);

    gchar *token = NULL, *req_path = NULL;
    guint sig_id = portal_subscribe(bus, &resp, &token, &req_path);

    // Build options
    GVariantBuilder opts;
    g_variant_builder_init(&opts, G_VARIANT_TYPE_VARDICT);
    g_variant_builder_add(&opts, "{sv}", "handle_token",
            g_variant_new_string(token));
    g_variant_builder_add(&opts, "{sv}", "modal",
            g_variant_new_boolean(TRUE));

    if (type == 0) { // Open
        g_variant_builder_add(&opts, "{sv}", "multiple",
                g_variant_new_boolean(JNI_TRUE == multiple));
    }

    build_portal_filters(env, jFilters, default_filter_index, &opts);

    if (chooser_folder) {
        g_variant_builder_add(&opts, "{sv}", "current_folder",
                g_variant_new_bytestring(chooser_folder));
    }
    if (type != 0 && chooser_filename) { // Save
        g_variant_builder_add(&opts, "{sv}", "current_name",
                g_variant_new_string(chooser_filename));
    }

    gchar *parent_handle = get_parent_window_handle(get_gdk_window(parent));
    const gchar *method = (type == 0) ? "OpenFile" : "SaveFile";

    GVariant *ret = g_dbus_connection_call_sync(bus,
            "org.freedesktop.portal.Desktop",
            "/org/freedesktop/portal/desktop",
            "org.freedesktop.portal.FileChooser",
            method,
            g_variant_new("(ssa{sv})", parent_handle, chooser_title, &opts),
            G_VARIANT_TYPE("(o)"),
            G_DBUS_CALL_FLAGS_NONE,
            -1, NULL, NULL);

    if (ret) {
        g_variant_unref(ret);
        g_main_loop_run(resp.loop);
    }

    g_dbus_connection_signal_unsubscribe(bus, sig_id);

    // Build Java result
    jobjectArray jFileNames = NULL;
    int filter_index = -1;

    if (resp.status == 0 && resp.uris) {
        filter_index = find_filter_index(env, jFilters, resp.filter_name);
        jsize n = (jsize) resp.n_uris;

        if (n > 0) {
            jFileNames = env->NewObjectArray(n, jStringCls, NULL);
            EXCEPTION_OCCURED(env);
            const jmethodID bytesInit = env->GetMethodID(jStringCls, "<init>", "([B)V");
            EXCEPTION_OCCURED(env);

            for (jsize i = 0; i < n; i++) {
                gchar *path = uri_to_path(resp.uris[i]);
                int len = strlen(path);
                jbyteArray bytes = env->NewByteArray(len);
                EXCEPTION_OCCURED(env);
                env->SetByteArrayRegion(bytes, 0, len, (jbyte *) path);
                EXCEPTION_OCCURED(env);
                jstring jfn = (jstring) env->NewObject(jStringCls, bytesInit, bytes);
                EXCEPTION_OCCURED(env);
                env->DeleteLocalRef(bytes);
                EXCEPTION_OCCURED(env);
                env->SetObjectArrayElement(jFileNames, i, jfn);
                EXCEPTION_OCCURED(env);
                g_free(path);
            }
        }
    }

    if (!jFileNames) {
        jFileNames = env->NewObjectArray(0, jStringCls, NULL);
        EXCEPTION_OCCURED(env);
    }

    jclass jCommonDialogs = (jclass) env->FindClass("com/sun/glass/ui/CommonDialogs");
    EXCEPTION_OCCURED(env);
    jmethodID jCreateFileChooserResult = env->GetStaticMethodID(jCommonDialogs,
            "createFileChooserResult",
            "([Ljava/lang/String;[Lcom/sun/glass/ui/CommonDialogs$ExtensionFilter;I)Lcom/sun/glass/ui/CommonDialogs$FileChooserResult;");
    EXCEPTION_OCCURED(env);

    jobject result = env->CallStaticObjectMethod(
            jCommonDialogs, jCreateFileChooserResult, jFileNames, jFilters, filter_index);
    LOG_EXCEPTION(env)

    portal_response_free(&resp);
    g_free(token);
    g_free(req_path);
    g_free(parent_handle);
    g_object_unref(bus);

    jstring_to_utf_release(env, folder, chooser_folder);
    jstring_to_utf_release(env, title, chooser_title);
    jstring_to_utf_release(env, name, chooser_filename);

    LOG_STRING_ARRAY(env, jFileNames);
    return result;
}

JNIEXPORT jstring JNICALL Java_com_sun_glass_ui_gtk_GtkCommonDialogs__1showFolderChooser
  (JNIEnv *env, jclass clazz, jlong parent, jstring folder, jstring title) {
    (void)clazz;

    const char *chooser_folder;
    const char *chooser_title;

    if (!jstring_to_utf_get(env, folder, &chooser_folder)) {
        return NULL;
    }
    if (!jstring_to_utf_get(env, title, &chooser_title)) {
        jstring_to_utf_release(env, folder, chooser_folder);
        return NULL;
    }

    GDBusConnection *bus = g_bus_get_sync(G_BUS_TYPE_SESSION, NULL, NULL);
    if (!bus) {
        jstring_to_utf_release(env, folder, chooser_folder);
        jstring_to_utf_release(env, title, chooser_title);
        return NULL;
    }

    PortalResponse resp = {};
    resp.loop = g_main_loop_new(NULL, FALSE);

    gchar *token = NULL, *req_path = NULL;
    guint sig_id = portal_subscribe(bus, &resp, &token, &req_path);

    GVariantBuilder opts;
    g_variant_builder_init(&opts, G_VARIANT_TYPE_VARDICT);
    g_variant_builder_add(&opts, "{sv}", "handle_token",
            g_variant_new_string(token));
    g_variant_builder_add(&opts, "{sv}", "modal",
            g_variant_new_boolean(TRUE));
    g_variant_builder_add(&opts, "{sv}", "directory",
            g_variant_new_boolean(TRUE));

    if (chooser_folder) {
        g_variant_builder_add(&opts, "{sv}", "current_folder",
                g_variant_new_bytestring(chooser_folder));
    }

    gchar *parent_handle = get_parent_window_handle(get_gdk_window(parent));

    GVariant *ret = g_dbus_connection_call_sync(bus,
            "org.freedesktop.portal.Desktop",
            "/org/freedesktop/portal/desktop",
            "org.freedesktop.portal.FileChooser",
            "OpenFile",
            g_variant_new("(ssa{sv})", parent_handle, chooser_title, &opts),
            G_VARIANT_TYPE("(o)"),
            G_DBUS_CALL_FLAGS_NONE,
            -1, NULL, NULL);

    jstring jfilename = NULL;

    if (ret) {
        g_variant_unref(ret);
        g_main_loop_run(resp.loop);

        if (resp.status == 0 && resp.uris && resp.n_uris > 0) {
            gchar *path = uri_to_path(resp.uris[0]);
            jfilename = env->NewStringUTF(path);
            LOG1("Selected folder: %s\n", path);
            g_free(path);
        }
    }

    g_dbus_connection_signal_unsubscribe(bus, sig_id);
    portal_response_free(&resp);
    g_free(token);
    g_free(req_path);
    g_free(parent_handle);
    g_object_unref(bus);

    jstring_to_utf_release(env, folder, chooser_folder);
    jstring_to_utf_release(env, title, chooser_title);

    return jfilename;
}

} // extern "C"

