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
#include "file_chooser_portal.h"

#include <gdk/gdk.h>
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

/*
 * Build a std::vector<FileChooserPortal::Filter> from Java ExtensionFilter[].
 */
static std::vector<FileChooserPortal::Filter> build_filters(
        JNIEnv *env, jobjectArray jFilters) {
    std::vector<FileChooserPortal::Filter> filters;
    if (jFilters == NULL) return filters;

    jclass jcls = env->FindClass("com/sun/glass/ui/CommonDialogs$ExtensionFilter");
    if (EXCEPTION_OCCURED(env)) return filters;
    jmethodID jgetDesc = env->GetMethodID(jcls, "getDescription", "()Ljava/lang/String;");
    if (EXCEPTION_OCCURED(env)) return filters;
    jmethodID jgetExts = env->GetMethodID(jcls, "extensionsToArray", "()[Ljava/lang/String;");
    if (EXCEPTION_OCCURED(env)) return filters;

    jsize n = env->GetArrayLength(jFilters);
    filters.reserve(n);

    for (jsize i = 0; i < n; i++) {
        jobject jfilter = env->GetObjectArrayElement(jFilters, i);
        jstring jdesc = (jstring) env->CallObjectMethod(jfilter, jgetDesc);
        const char *desc = env->GetStringUTFChars(jdesc, NULL);

        jobjectArray jexts = (jobjectArray) env->CallObjectMethod(jfilter, jgetExts);
        jsize n_exts = env->GetArrayLength(jexts);

        FileChooserPortal::Filter f;
        f.name = desc;
        for (jsize j = 0; j < n_exts; j++) {
            jstring jext = (jstring) env->GetObjectArrayElement(jexts, j);
            const char *ext = env->GetStringUTFChars(jext, NULL);
            f.patterns.emplace_back(ext);
            env->ReleaseStringUTFChars(jext, ext);
        }
        filters.push_back(std::move(f));

        env->ReleaseStringUTFChars(jdesc, desc);
    }
    return filters;
}

/*
 * Find the index of a filter by name.
 */
static int find_filter_index(const std::vector<FileChooserPortal::Filter> &filters,
                              const std::string &name) {
    if (name.empty()) return -1;
    for (size_t i = 0; i < filters.size(); i++) {
        if (filters[i].name == name) return (int) i;
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

    auto filters = build_filters(env, jFilters);

    WindowContext::dismiss_grab();

    FileChooserPortal::Result portal_result = (type == 0)
            ? FileChooserPortal::open_file(
                    get_gdk_window(parent), chooser_title, chooser_folder,
                    (JNI_TRUE == multiple), filters, default_filter_index)
            : FileChooserPortal::save_file(
                    get_gdk_window(parent), chooser_title, chooser_folder,
                    chooser_filename, filters, default_filter_index);

    // Build Java result
    jobjectArray jFileNames = NULL;
    int filter_index = -1;

    if (portal_result.accepted && !portal_result.paths.empty()) {
        filter_index = find_filter_index(filters, portal_result.filter_name);
        jsize n = (jsize) portal_result.paths.size();

        jFileNames = env->NewObjectArray(n, jStringCls, NULL);
        EXCEPTION_OCCURED(env);
        const jmethodID bytesInit = env->GetMethodID(jStringCls, "<init>", "([B)V");
        EXCEPTION_OCCURED(env);

        for (jsize i = 0; i < n; i++) {
            const std::string &path = portal_result.paths[i];
            int len = (int) path.size();
            jbyteArray bytes = env->NewByteArray(len);
            EXCEPTION_OCCURED(env);
            env->SetByteArrayRegion(bytes, 0, len, (jbyte *) path.c_str());
            EXCEPTION_OCCURED(env);
            jstring jfn = (jstring) env->NewObject(jStringCls, bytesInit, bytes);
            EXCEPTION_OCCURED(env);
            env->DeleteLocalRef(bytes);
            EXCEPTION_OCCURED(env);
            env->SetObjectArrayElement(jFileNames, i, jfn);
            EXCEPTION_OCCURED(env);
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

    WindowContext::dismiss_grab();

    FileChooserPortal::Result portal_result = FileChooserPortal::open_folder(
            get_gdk_window(parent), chooser_title, chooser_folder);

    jstring jfilename = NULL;

    if (portal_result.accepted && !portal_result.paths.empty()) {
        const std::string &path = portal_result.paths[0];
        jfilename = env->NewStringUTF(path.c_str());
        LOG1("Selected folder: %s\n", path.c_str());
    }

    jstring_to_utf_release(env, folder, chooser_folder);
    jstring_to_utf_release(env, title, chooser_title);

    return jfilename;
}

} // extern "C"

