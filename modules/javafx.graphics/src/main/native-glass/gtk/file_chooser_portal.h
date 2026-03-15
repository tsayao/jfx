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

#ifndef FILE_CHOOSER_PORTAL_H
#define FILE_CHOOSER_PORTAL_H

#include "glass_portal.h"

#include <vector>
#include <string>

#define PORTAL_IFACE_FILE_CHOOSER "org.freedesktop.portal.FileChooser"

/**
 * File/folder chooser dialogs via the org.freedesktop.portal.FileChooser
 * D-Bus interface, built on the generic Portal helper.
 */
class FileChooserPortal {
public:
    struct Filter {
        std::string name;
        std::vector<std::string> patterns; // glob patterns (e.g. "*.txt")
    };

    struct Result {
        bool accepted;
        std::vector<std::string> paths;
        std::string filter_name;
    };

    static Result open_file(GdkWindow *parent_window,
                            const char *title,
                            const char *folder,
                            bool multiple,
                            const std::vector<Filter> &filters,
                            int default_filter);

    static Result save_file(GdkWindow *parent_window,
                            const char *title,
                            const char *folder,
                            const char *filename,
                            const std::vector<Filter> &filters,
                            int default_filter);

    static Result open_folder(GdkWindow *parent_window,
                              const char *title,
                              const char *folder);

private:
    static void add_filters(GVariantBuilder *opts,
                            const std::vector<Filter> &filters,
                            int default_filter);

    static Result call(GdkWindow *parent_window,
                       const char *title,
                       const char *method,
                       GVariantBuilder *opts);

    static Result parse_response(const Portal::Response &resp);
};

#endif /* FILE_CHOOSER_PORTAL_H */

