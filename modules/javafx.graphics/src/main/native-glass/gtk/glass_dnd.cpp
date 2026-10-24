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
#include "glass_dnd.h"
#include "glass_general.h"

#include "com_sun_glass_events_DndEvent.h"
#include "com_sun_glass_ui_gtk_GtkDnDClipboard.h"

#include <jni.h>
#include <algorithm>
#include <climits>
#include <cstring>
#include <memory>

#include <gtk/gtk.h>
#include <gdk/gdkx.h>

gboolean is_dnd_owner = FALSE;

namespace
{
/************************* COMMON *********************************************/

constexpr jint ACTION_NONE = com_sun_glass_ui_gtk_GtkDnDClipboard_ACTION_NONE;
constexpr const char* SOURCE_DND_DATA = "fx-dnd-data";

constexpr const char* MIME_TEXT = "text/plain";
constexpr const char* MIME_URI_LIST = "text/uri-list";
constexpr const char* MIME_FILE_LIST = "application/x-java-file-list";
constexpr const char* MIME_RAW_IMAGE = "application/x-java-rawimage";
constexpr const char* MIME_DRAG_IMAGE = "application/x-java-drag-image";
constexpr const char* MIME_DRAG_IMAGE_OFFSET = "application/x-java-drag-image-offset";

// Passed as the target info of the source target list, so drag-data-get
// knows which conversion to apply
enum class TargetInfo : guint {
    RAW,
    TEXT,
    IMAGE,
    URI
};

struct GFreeDeleter {
    void operator()(gpointer p) const { g_free(p); }
};

struct GObjectDeleter {
    void operator()(gpointer p) const { g_object_unref(p); }
};

using GCharPtr = std::unique_ptr<gchar, GFreeDeleter>;
using PixbufPtr = std::unique_ptr<GdkPixbuf, GObjectDeleter>;

// The UTF-8 chars of a jstring, released when going out of scope
class JStringUTF {
public:
    JStringUTF(JNIEnv* env, jstring str)
        : env(env), str(str), chars(str ? env->GetStringUTFChars(str, nullptr) : nullptr) {}

    ~JStringUTF() {
        if (chars) {
            env->ReleaseStringUTFChars(str, chars);
        }
    }

    JStringUTF(const JStringUTF&) = delete;
    JStringUTF& operator=(const JStringUTF&) = delete;

    const char* get() const { return chars; }
    explicit operator bool() const { return chars != nullptr; }

private:
    JNIEnv* env;
    jstring str;
    const char* chars;
};

// The elements of a jbyteArray, released without copying back when going out of scope
class JByteArrayElements {
public:
    JByteArrayElements(JNIEnv* env, jbyteArray array)
        : env(env), array(array),
          bytes(array ? env->GetByteArrayElements(array, nullptr) : nullptr),
          length(bytes ? env->GetArrayLength(array) : 0) {}

    ~JByteArrayElements() {
        if (bytes) {
            env->ReleaseByteArrayElements(array, bytes, JNI_ABORT);
        }
    }

    JByteArrayElements(const JByteArrayElements&) = delete;
    JByteArrayElements& operator=(const JByteArrayElements&) = delete;

    const jbyte* data() const { return bytes; }
    jsize size() const { return length; }
    explicit operator bool() const { return bytes != nullptr; }

private:
    JNIEnv* env;
    jbyteArray array;
    jbyte* bytes;
    jsize length;
};

jint translate_gdk_action_to_glass(GdkDragAction action)
{
    jint result = 0;
    result |= (action & GDK_ACTION_COPY) ? com_sun_glass_ui_gtk_GtkDnDClipboard_ACTION_COPY : 0;
    result |= (action & GDK_ACTION_MOVE) ? com_sun_glass_ui_gtk_GtkDnDClipboard_ACTION_MOVE : 0;
    result |= (action & GDK_ACTION_LINK) ? com_sun_glass_ui_gtk_GtkDnDClipboard_ACTION_REFERENCE : 0;
    return result;
}

GdkDragAction translate_glass_action_to_gdk(jint action)
{
    int result = 0;
    result |= (action & com_sun_glass_ui_gtk_GtkDnDClipboard_ACTION_COPY) ? GDK_ACTION_COPY : 0;
    result |= (action & com_sun_glass_ui_gtk_GtkDnDClipboard_ACTION_MOVE) ? GDK_ACTION_MOVE : 0;
    result |= (action & com_sun_glass_ui_gtk_GtkDnDClipboard_ACTION_REFERENCE) ? GDK_ACTION_LINK : 0;
    return static_cast<GdkDragAction>(result);
}

bool target_is_text(GdkAtom target)
{
    return gtk_targets_include_text(&target, 1);
}

bool target_is_uri(GdkAtom target)
{
    return gtk_targets_include_uri(&target, 1);
}

bool target_is_image(GdkAtom target)
{
    return gtk_targets_include_image(&target, 1, FALSE);
}

void clear_global_ref(gpointer data)
{
    mainEnv->DeleteGlobalRef(static_cast<jobject>(data));
}

/************************* TARGET *********************************************/

struct DropTarget {
    GdkDragContext* ctx = nullptr;
    bool just_entered = false;
    bool dropped = false;
    jobjectArray mimes = nullptr;
    gint dx = 0;
    gint dy = 0;
};

DropTarget enter_ctx;

void reset_enter_ctx()
{
    if (enter_ctx.mimes != nullptr) {
        mainEnv->DeleteGlobalRef(enter_ctx.mimes);
    }

    enter_ctx = DropTarget();
}

void process_dnd_target_drag_enter(WindowContext* ctx, GdkEventDND* event)
{
    reset_enter_ctx();
    enter_ctx.ctx = event->context;
    enter_ctx.just_entered = true;
    gdk_window_get_origin(ctx->get_gdk_window(), &enter_ctx.dx, &enter_ctx.dy);
    is_dnd_owner = is_in_drag();
}

void process_dnd_target_drag_motion(WindowContext* ctx, GdkEventDND* event)
{
    if (!enter_ctx.ctx) {
        gdk_drag_status(event->context, static_cast<GdkDragAction>(0), GDK_CURRENT_TIME);
        return; // Do not process motion events if no enter event was received
    }
    jmethodID method = enter_ctx.just_entered ? jViewNotifyDragEnter : jViewNotifyDragOver;
    GdkDragAction suggested = gdk_drag_context_get_suggested_action(event->context);
    GdkDragAction result = translate_glass_action_to_gdk(mainEnv->CallIntMethod(ctx->get_jview(), method,
            static_cast<jint>(event->x_root) - enter_ctx.dx, static_cast<jint>(event->y_root) - enter_ctx.dy,
            static_cast<jint>(event->x_root), static_cast<jint>(event->y_root),
            translate_gdk_action_to_glass(suggested)));
    CHECK_JNI_EXCEPTION(mainEnv)

    enter_ctx.just_entered = false;
    gdk_drag_status(event->context, result, GDK_CURRENT_TIME);
}

void process_dnd_target_drag_leave(WindowContext* ctx, GdkEventDND*)
{
    // if there is a drop going on do not report drag leave to java because
    // it will reset the drag gesture.
    if (!enter_ctx.dropped) {
        mainEnv->CallVoidMethod(ctx->get_jview(), jViewNotifyDragLeave, nullptr);
        CHECK_JNI_EXCEPTION(mainEnv)
    }
}

void process_dnd_target_drop_start(WindowContext* ctx, GdkEventDND* event)
{
    if (!enter_ctx.ctx || enter_ctx.just_entered) {
        gdk_drop_finish(event->context, FALSE, GDK_CURRENT_TIME);
        gdk_drop_reply(event->context, FALSE, GDK_CURRENT_TIME);
        return; // Do not process drop events if no enter event and subsequent motion event were received
    }
    GdkDragAction selected = gdk_drag_context_get_selected_action(event->context);
    enter_ctx.dropped = true;
    mainEnv->CallIntMethod(ctx->get_jview(), jViewNotifyDragDrop,
            static_cast<jint>(event->x_root) - enter_ctx.dx, static_cast<jint>(event->y_root) - enter_ctx.dy,
            static_cast<jint>(event->x_root), static_cast<jint>(event->y_root),
            translate_gdk_action_to_glass(selected));
    LOG_EXCEPTION(mainEnv)

    gdk_drop_finish(event->context, TRUE, GDK_CURRENT_TIME);
    gdk_drop_reply(event->context, TRUE, GDK_CURRENT_TIME);
}

// The drag data is transferred through a selection (XdndSelection), which
// GtkClipboard can read like any other
GtkClipboard* dnd_target_get_selection()
{
    return gtk_clipboard_get(gdk_drag_get_selection(enter_ctx.ctx));
}

bool check_state_in_drag(JNIEnv* env)
{
    if (!enter_ctx.ctx) {
        jclass jc = env->FindClass("java/lang/IllegalStateException");
        if (!env->ExceptionCheck()) {
            env->ThrowNew(jc,
                    "Cannot get supported actions. Drag pointer haven't entered the application window");
        }
        return true;
    }
    return false;
}

void add_mime(JNIEnv* env, jobject set, const char* mime)
{
    jstring jStr = env->NewStringUTF(mime);
    EXCEPTION_OCCURED(env);
    env->CallBooleanMethod(set, jSetAdd, jStr, nullptr);
    EXCEPTION_OCCURED(env);
}

jobject dnd_target_get_raw(JNIEnv* env, GdkAtom target, bool string_data)
{
    GtkSelectionData* data = gtk_clipboard_wait_for_contents(dnd_target_get_selection(), target);
    if (data == nullptr) {
        return nullptr;
    }

    jobject result = nullptr;
    const guchar* raw = gtk_selection_data_get_data(data);
    gint length = gtk_selection_data_get_length(data);

    if (string_data) {
        if (raw && length >= 0) {
            result = env->NewStringUTF(reinterpret_cast<const char*>(raw));
            EXCEPTION_OCCURED(env);
        }
    } else if (raw && length > 0) {
        jbyteArray array = env->NewByteArray(length);
        EXCEPTION_OCCURED(env);
        env->SetByteArrayRegion(array, 0, length, reinterpret_cast<const jbyte*>(raw));
        EXCEPTION_OCCURED(env);
        result = env->CallStaticObjectMethod(jByteBufferCls, jByteBufferWrap, array);
        EXCEPTION_OCCURED(env);
    }

    gtk_selection_data_free(data);
    return result;
}

jobject dnd_target_get_string(JNIEnv* env)
{
    GCharPtr text(gtk_clipboard_wait_for_text(dnd_target_get_selection()));
    if (!text) {
        // gtk_clipboard_wait_for_text() does not ask for plain "text/plain"
        return dnd_target_get_raw(env, gdk_atom_intern_static_string(MIME_TEXT), true);
    }

    jobject result = env->NewStringUTF(text.get());
    EXCEPTION_OCCURED(env);
    return result;
}

jobject dnd_target_get_list(JNIEnv* env, bool files)
{
    return uris_to_java(env, gtk_clipboard_wait_for_uris(dnd_target_get_selection()), files);
}

jobject dnd_target_get_image(JNIEnv* env)
{
    PixbufPtr pixbuf(gtk_clipboard_wait_for_image(dnd_target_get_selection()));
    return pixbuf ? pixbuf_to_java(env, pixbuf.get()) : nullptr;
}

/************************* SOURCE *********************************************/

struct DragSource {
    // the window the drag started from, it receives the drag source signals
    GtkWidget* widget = nullptr;
    // set when GTK is done with the drag, execute_dnd() then cleans up
    bool finished = false;
    bool failed = false;
    jint performed_action = ACTION_NONE;
};

DragSource drag_source;

void pixbuf_destroy_notify(guchar* pixels, gpointer)
{
    g_free(pixels);
}

jobject dnd_source_get_data(GtkWidget* widget, const char* key)
{
    auto data = static_cast<jobject>(g_object_get_data(G_OBJECT(widget), SOURCE_DND_DATA));
    jstring string = mainEnv->NewStringUTF(key);
    EXCEPTION_OCCURED(mainEnv);
    jobject result = mainEnv->CallObjectMethod(data, jMapGet, string, nullptr);

    return EXCEPTION_OCCURED(mainEnv) ? nullptr : result;
}

void add_gtk_target_from_jstring(JNIEnv* env, GtkTargetList* list, jstring string)
{
    JStringUTF mime(env, string);
    if (!mime) {
        return;
    }

    if (g_strcmp0(mime.get(), MIME_TEXT) == 0) {
        gtk_target_list_add_text_targets(list, static_cast<guint>(TargetInfo::TEXT));
    } else if (g_strcmp0(mime.get(), MIME_RAW_IMAGE) == 0) {
        gtk_target_list_add_image_targets(list, static_cast<guint>(TargetInfo::IMAGE), TRUE);
    } else if (g_strcmp0(mime.get(), MIME_FILE_LIST) == 0
            || g_strcmp0(mime.get(), MIME_URI_LIST) == 0) {
        gtk_target_list_add_uri_targets(list, static_cast<guint>(TargetInfo::URI));
    } else if (g_strcmp0(mime.get(), MIME_DRAG_IMAGE) == 0
            || g_strcmp0(mime.get(), MIME_DRAG_IMAGE_OFFSET) == 0) {
        // do nothing - those are drag icon information
    } else {
        gtk_target_list_add(list, gdk_atom_intern(mime.get(), FALSE), 0, static_cast<guint>(TargetInfo::RAW));
    }
}

GtkTargetList* data_to_gtk_target_list(JNIEnv* env, jobject data)
{
    GtkTargetList* tlist = gtk_target_list_new(nullptr, 0);

    jobject keys = env->CallObjectMethod(data, jMapKeySet, nullptr);
    JNI_EXCEPTION_TO_CPP(env)
    jobject keysIterator = env->CallObjectMethod(keys, jIterableIterator, nullptr);
    JNI_EXCEPTION_TO_CPP(env)
    while (env->CallBooleanMethod(keysIterator, jIteratorHasNext) == JNI_TRUE) {
        auto next = static_cast<jstring>(env->CallObjectMethod(keysIterator, jIteratorNext, nullptr));
        JNI_EXCEPTION_TO_CPP(env)
        add_gtk_target_from_jstring(env, tlist, next);
    }

    return tlist;
}

void dnd_source_set_string(GtkWidget* widget, GtkSelectionData* data)
{
    JStringUTF text(mainEnv, static_cast<jstring>(dnd_source_get_data(widget, MIME_TEXT)));
    if (text) {
        // converts to the encoding of the requested target
        gtk_selection_data_set_text(data, text.get(), -1);
    }
}

void dnd_source_set_image(GtkWidget* widget, GtkSelectionData* data)
{
    jobject pixels = dnd_source_get_data(widget, MIME_RAW_IMAGE);
    if (!pixels) {
        return;
    }

    GdkPixbuf* raw_pixbuf = nullptr;
    mainEnv->CallVoidMethod(pixels, jPixelsAttachData, PTR_TO_JLONG(&raw_pixbuf));
    PixbufPtr pixbuf(raw_pixbuf);

    if (!EXCEPTION_OCCURED(mainEnv) && pixbuf) {
        gtk_selection_data_set_pixbuf(data, pixbuf.get());
    }
}

void dnd_source_set_uri(GtkWidget* widget, GtkSelectionData* data)
{
    auto jurl = static_cast<jstring>(dnd_source_get_data(widget, MIME_URI_LIST));
    auto files_array = static_cast<jobjectArray>(dnd_source_get_data(widget, MIME_FILE_LIST));
    jsize files_cnt = files_array ? mainEnv->GetArrayLength(files_array) : 0;

    if (!jurl && !files_cnt) {
        return;
    }

    std::unique_ptr<GPtrArray, decltype(&g_ptr_array_unref)> uris(
            g_ptr_array_new_with_free_func(g_free), g_ptr_array_unref);

    for (jsize i = 0; i < files_cnt; ++i) {
        auto string = static_cast<jstring>(mainEnv->GetObjectArrayElement(files_array, i));
        EXCEPTION_OCCURED(mainEnv);
        JStringUTF file(mainEnv, string);
        if (file) {
            gchar* uri = g_filename_to_uri(file.get(), nullptr, nullptr);
            if (uri) {
                g_ptr_array_add(uris.get(), uri);
            }
        }
    }

    JStringUTF url(mainEnv, jurl);
    if (url) {
        g_ptr_array_add(uris.get(), g_strdup(url.get()));
    }

    g_ptr_array_add(uris.get(), nullptr);
    gtk_selection_data_set_uris(data, reinterpret_cast<gchar**>(uris->pdata));
}

void dnd_source_set_raw(GtkWidget* widget, GtkSelectionData* sel_data)
{
    GdkAtom atom = gtk_selection_data_get_target(sel_data);
    GCharPtr target_name(gdk_atom_name(atom));
    jobject data = dnd_source_get_data(widget, target_name.get());
    if (!data) {
        return;
    }

    if (mainEnv->IsInstanceOf(data, jStringCls)) {
        JStringUTF text(mainEnv, static_cast<jstring>(data));
        if (text) {
            // set as is, gtk_selection_data_set_text() only accepts text targets
            gtk_selection_data_set(sel_data, atom, 8,
                    reinterpret_cast<const guchar*>(text.get()), strlen(text.get()));
        }
    } else if (mainEnv->IsInstanceOf(data, jByteBufferCls)) {
        auto byteArray = static_cast<jbyteArray>(mainEnv->CallObjectMethod(data, jByteBufferArray));
        if (!EXCEPTION_OCCURED(mainEnv)) {
            JByteArrayElements bytes(mainEnv, byteArray);
            if (bytes) {
                gtk_selection_data_set(sel_data, atom, 8,
                        reinterpret_cast<const guchar*>(bytes.data()), bytes.size());
            }
        }
    }
}

void dnd_end_callback(GtkWidget*, GdkDragContext* context, gpointer)
{
    if (!drag_source.failed) {
        GdkDragAction action = gdk_drag_context_get_selected_action(context);
        drag_source.performed_action = translate_gdk_action_to_glass(action);
    }
    drag_source.finished = true;
}

gboolean dnd_drag_failed_callback(GtkWidget*, GdkDragContext*, GtkDragResult, gpointer)
{
    // drag-end follows and finishes the drag
    drag_source.failed = true;
    drag_source.performed_action = ACTION_NONE;

    return FALSE;
}

void dnd_data_get_callback(GtkWidget* widget, GdkDragContext*, GtkSelectionData* data,
                           guint info, guint, gpointer)
{
    switch (static_cast<TargetInfo>(info)) {
        case TargetInfo::TEXT:
            dnd_source_set_string(widget, data);
            break;
        case TargetInfo::IMAGE:
            dnd_source_set_image(widget, data);
            break;
        case TargetInfo::URI:
            dnd_source_set_uri(widget, data);
            break;
        case TargetInfo::RAW:
            dnd_source_set_raw(widget, data);
            break;
    }
}

bool dnd_get_drag_image_offset(GtkWidget* widget, int* x, int* y)
{
    jobject bb = dnd_source_get_data(widget, MIME_DRAG_IMAGE_OFFSET);
    if (!bb) {
        return false;
    }

    auto byteArray = static_cast<jbyteArray>(mainEnv->CallObjectMethod(bb, jByteBufferArray));
    if (EXCEPTION_OCCURED(mainEnv)) {
        return false;
    }

    JByteArrayElements bytes(mainEnv, byteArray);
    if (!bytes || static_cast<size_t>(bytes.size()) < sizeof(jint) * 2) {
        return false;
    }

    auto offset = reinterpret_cast<const jint*>(bytes.data());
    *x = BSWAP_32(offset[0]);
    *y = BSWAP_32(offset[1]);
    return true;
}

PixbufPtr dnd_get_drag_image(GtkWidget* widget, gint* width, gint* height)
{
    PixbufPtr pixbuf;

    jobject drag_image = dnd_source_get_data(widget, MIME_DRAG_IMAGE);

    if (drag_image) {
        auto byteArray = static_cast<jbyteArray>(mainEnv->CallObjectMethod(drag_image, jByteBufferArray));
        if (!EXCEPTION_OCCURED(mainEnv)) {
            JByteArrayElements bytes(mainEnv, byteArray);

            // Pixels are stored right after two ints in this byteArray: width and height
            constexpr int whsz = sizeof(jint) * 2;
            if (bytes && bytes.size() > whsz) {
                auto int_raw = reinterpret_cast<const jint*>(bytes.data());
                int w = BSWAP_32(int_raw[0]);
                int h = BSWAP_32(int_raw[1]);

                // We should have enough pixels for requested width and height
                if (w > 0 && h > 0 && w < (INT_MAX / 4) / h &&
                        (bytes.size() - whsz) / 4 - w * h >= 0) {
                    guchar* data = convert_BGRA_to_RGBA(reinterpret_cast<const int*>(bytes.data() + whsz), w * 4, h);
                    if (data) {
                        pixbuf.reset(gdk_pixbuf_new_from_data(data, GDK_COLORSPACE_RGB, TRUE, 8,
                                                              w, h, w * 4, pixbuf_destroy_notify, nullptr));
                    }
                }
            }
        }
    }

    if (!pixbuf) {
        jobject pixels = dnd_source_get_data(widget, MIME_RAW_IMAGE);
        if (pixels) {
            GdkPixbuf* raw_pixbuf = nullptr;
            mainEnv->CallVoidMethod(pixels, jPixelsAttachData, PTR_TO_JLONG(&raw_pixbuf));
            pixbuf.reset(raw_pixbuf);
            CHECK_JNI_EXCEPTION_RET(mainEnv, nullptr)
        }
    }

    if (!pixbuf) {
        return nullptr;
    }

    int w = gdk_pixbuf_get_width(pixbuf.get());
    int h = gdk_pixbuf_get_height(pixbuf.get());

    if (w > DRAG_IMAGE_MAX_WIDTH || h > DRAG_IMAGE_MAX_HEIGH) {
        double r = std::min(DRAG_IMAGE_MAX_WIDTH / static_cast<double>(w),
                            DRAG_IMAGE_MAX_HEIGH / static_cast<double>(h));

        w = w * r;
        h = h * r;

        pixbuf.reset(gdk_pixbuf_scale_simple(pixbuf.get(), w, h, GDK_INTERP_TILES));
        if (!pixbuf) {
            return nullptr;
        }
    }

    *width = w;
    *height = h;

    return pixbuf;
}

void dnd_drag_begin_callback(GtkWidget* widget, GdkDragContext* context, gpointer)
{
    gint w = 0, h = 0;
    PixbufPtr pixbuf = dnd_get_drag_image(widget, &w, &h);

    if (pixbuf) {
        gint offset_x = w / 2;
        gint offset_y = h / 2;

        dnd_get_drag_image_offset(widget, &offset_x, &offset_y);

        gtk_drag_set_icon_pixbuf(context, pixbuf.get(), offset_x, offset_y);
    }
}

// The window the drag starts from: the one holding the mouse drag grab,
// or else the one under the pointer
GtkWidget* dnd_source_get_window()
{
    WindowContext* ctx = WindowContextBase::get_mouse_drag_window();

    if (ctx == nullptr) {
        GdkDevice* pointer = gdk_seat_get_pointer(gdk_display_get_default_seat(gdk_display_get_default()));
        GdkWindow* window = gdk_device_get_window_at_position(pointer, nullptr, nullptr);
        if (window != nullptr) {
            ctx = static_cast<WindowContext*>(g_object_get_data(G_OBJECT(gdk_window_get_toplevel(window)),
                                                                GDK_WINDOW_DATA_CONTEXT));
        }
    }

    return (ctx != nullptr) ? GTK_WIDGET(ctx->get_gtk_window()) : nullptr;
}

void dnd_source_cleanup()
{
    GtkWidget* widget = drag_source.widget;
    if (widget == nullptr) {
        return;
    }

    g_signal_handlers_disconnect_by_func(widget, reinterpret_cast<gpointer>(dnd_drag_begin_callback), nullptr);
    g_signal_handlers_disconnect_by_func(widget, reinterpret_cast<gpointer>(dnd_drag_failed_callback), nullptr);
    g_signal_handlers_disconnect_by_func(widget, reinterpret_cast<gpointer>(dnd_data_get_callback), nullptr);
    g_signal_handlers_disconnect_by_func(widget, reinterpret_cast<gpointer>(dnd_end_callback), nullptr);
    // releases the global ref to the data
    g_object_set_data(G_OBJECT(widget), SOURCE_DND_DATA, nullptr);

    g_object_unref(widget);
    drag_source.widget = nullptr;
}

void dnd_source_push_data(JNIEnv* env, jobject data, jint supported)
{
    if (supported == 0) {
        return; // No supported actions, do nothing
    }

    GtkWidget* window = dnd_source_get_window();
    if (window == nullptr) {
        return;
    }

    data = env->NewGlobalRef(data);

    GdkDragAction actions = translate_glass_action_to_gdk(supported);

    // the source window receives the drag source signals while the drag
    // lasts, keep it alive until then
    drag_source.widget = GTK_WIDGET(g_object_ref(window));

    g_object_set_data_full(G_OBJECT(drag_source.widget), SOURCE_DND_DATA, data, clear_global_ref);

    g_signal_connect(drag_source.widget, "drag-begin", G_CALLBACK(dnd_drag_begin_callback), nullptr);
    g_signal_connect(drag_source.widget, "drag-failed", G_CALLBACK(dnd_drag_failed_callback), nullptr);
    g_signal_connect(drag_source.widget, "drag-data-get", G_CALLBACK(dnd_data_get_callback), nullptr);
    g_signal_connect(drag_source.widget, "drag-end", G_CALLBACK(dnd_end_callback), nullptr);

    GtkTargetList* tlist = data_to_gtk_target_list(env, data);

    is_dnd_owner = TRUE;

    GdkDragContext* context = gtk_drag_begin(drag_source.widget, tlist, actions, 1, nullptr);

    gtk_target_list_unref(tlist);

    if (context == nullptr) {
        // the drag did not start, so no drag-end will come
        drag_source.finished = true;
    }
}

} // namespace

/************************* TARGET *********************************************/

// Events coming from application that are related to us being a DnD target
void process_dnd_target(WindowContext* ctx, GdkEventDND* event)
{
    switch (event->type) {
        case GDK_DRAG_ENTER:
            process_dnd_target_drag_enter(ctx, event);
            break;
        case GDK_DRAG_MOTION:
            process_dnd_target_drag_motion(ctx, event);
            break;
        case GDK_DRAG_LEAVE:
            process_dnd_target_drag_leave(ctx, event);
            break;
        case GDK_DROP_START:
            process_dnd_target_drop_start(ctx, event);
            break;
        default:
            break;
    }
}

jobjectArray dnd_target_get_mimes(JNIEnv* env)
{
    if (check_state_in_drag(env)) {
        return nullptr;
    }
    if (!enter_ctx.mimes) {
        jobject set = env->NewObject(jHashSetCls, jHashSetInit, nullptr);
        EXCEPTION_OCCURED(env);

        for (GList* targets = gdk_drag_context_list_targets(enter_ctx.ctx); targets; targets = targets->next) {
            GdkAtom target = GDK_POINTER_TO_ATOM(targets->data);

            if (target_is_text(target)) {
                add_mime(env, set, MIME_TEXT);
            }

            if (target_is_image(target)) {
                add_mime(env, set, MIME_RAW_IMAGE);
            }

            if (target_is_uri(target)) {
                gchar** uris = gtk_clipboard_wait_for_uris(dnd_target_get_selection());
                if (uris) {
                    guint size = g_strv_length(uris);
                    guint files_cnt = get_files_count(uris);
                    if (files_cnt) {
                        add_mime(env, set, MIME_FILE_LIST);
                    }
                    if (size - files_cnt) {
                        add_mime(env, set, MIME_URI_LIST);
                    }
                    g_strfreev(uris);
                }
            } else {
                GCharPtr name(gdk_atom_name(target));
                add_mime(env, set, name.get());
            }
        }
        auto mimes = env->NewObjectArray(env->CallIntMethod(set, jSetSize, nullptr), jStringCls, nullptr);
        EXCEPTION_OCCURED(env);
        mimes = static_cast<jobjectArray>(env->CallObjectMethod(set, jSetToArray, mimes, nullptr));
        enter_ctx.mimes = static_cast<jobjectArray>(env->NewGlobalRef(mimes));
    }
    return enter_ctx.mimes;
}

jint dnd_target_get_supported_actions(JNIEnv* env)
{
    if (check_state_in_drag(env)) {
        return 0;
    }
    return translate_gdk_action_to_glass(gdk_drag_context_get_actions(enter_ctx.ctx));
}

jobject dnd_target_get_data(JNIEnv* env, jstring jmime)
{
    if (check_state_in_drag(env)) {
        return nullptr;
    }

    JStringUTF mime(env, jmime);
    if (!mime) {
        return nullptr;
    }

    jobject ret;
    if (g_strcmp0(mime.get(), MIME_TEXT) == 0) {
        ret = dnd_target_get_string(env);
    } else if (g_strcmp0(mime.get(), MIME_URI_LIST) == 0) {
        ret = dnd_target_get_list(env, false);
    } else if (g_str_has_prefix(mime.get(), "text/")) {
        ret = dnd_target_get_raw(env, gdk_atom_intern(mime.get(), FALSE), true);
    } else if (g_strcmp0(mime.get(), MIME_FILE_LIST) == 0) {
        ret = dnd_target_get_list(env, true);
    } else if (g_strcmp0(mime.get(), MIME_RAW_IMAGE) == 0) {
        ret = dnd_target_get_image(env);
    } else {
        ret = dnd_target_get_raw(env, gdk_atom_intern(mime.get(), FALSE), false);
    }
    LOG_EXCEPTION(env)

    return ret;
}

/************************* SOURCE *********************************************/

gboolean is_in_drag()
{
    return drag_source.widget != nullptr;
}

jint execute_dnd(JNIEnv* env, jobject data, jint supported)
{
    drag_source.finished = false;
    drag_source.failed = false;
    drag_source.performed_action = ACTION_NONE;

    try {
        dnd_source_push_data(env, data, supported);
    } catch (jni_exception&) {
        dnd_source_cleanup();
        return ACTION_NONE;
    }

    while (is_in_drag() && !drag_source.finished) {
        gtk_main_iteration();
    }

    dnd_source_cleanup();

    // The drag grab consumed the button release, so the mouse drag grab
    // started before the DnD must be reset or a later set_cursor() would
    // re-grab the pointer to the drag source window.
    WindowContextBase::reset_mouse_drag_focus();

    return drag_source.performed_action;
}
