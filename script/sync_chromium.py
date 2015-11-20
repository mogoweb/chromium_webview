#!/usr/bin/env python
#
# Copyright (c) 2015 The mogoweb project. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

import optparse
import os
import sys

import constants
import resource_util

sys.path.append(os.path.join(os.path.dirname(__file__), "dirsync-2.1"))
from dirsync import sync

def sync_java_files(options):
    app_java_dir = os.path.join(constants.DIR_APP_ROOT, "src", "main", "java")
    chrome_java_dir = os.path.join(options.chromium_root, "chrome", "android", "java", "src")
    args = {'exclude': ['\S+\\.aidl']}
    sync(chrome_java_dir, app_java_dir, "sync", **args)

    # sync aidl files

    app_aidl_dir = os.path.join(constants.DIR_APP_ROOT, "src", "main", "aidl")
    args = {'only': ['\S+\\.aidl'], 'ignore': ['\S*common.aidl']}
    sync(chrome_java_dir, app_aidl_dir, "sync", **args)

    # sync generated enums files
    gen_enums_dir = os.path.join(options.chromium_root, "out", options.buildtype,
                                 "gen", "enums")
    for dir in os.listdir(gen_enums_dir):
        java_dir = os.path.join(gen_enums_dir, dir)
        args = {'exclude':['org/chromium/(android_webview|base|blink_public|content|content_public|media|net|sync|ui)\S*',
                           'org/chromium/components/(dom_distiller|bookmarks)S*']}
        sync(java_dir, app_java_dir, "sync", **args)

    # sync generated template files
    gen_template_dir = os.path.join(options.chromium_root, "out", options.buildtype,
                                 "gen", "templates")
    for dir in os.listdir(gen_template_dir):
        java_dir = os.path.join(gen_template_dir, dir)
        args = {'exclude':['org/chromium/(android_webview|base|blink_public|content|content_public|media|net|sync|ui)\S*',
                           'org/chromium/components/(dom_distiller|bookmarks)S*']}
        sync(java_dir, app_java_dir, "sync", **args)

    # syn NativeLibraries.java
    native_libraries_dir = os.path.join(options.chromium_root, "out", options.buildtype,
                                        "chrome_public_apk", "native_libraries_java")
    java_dir = os.path.join(constants.DIR_APP_ROOT, "src", "main", "java", "org", "chromium", "base", "library_loader")
    sync(native_libraries_dir, java_dir, "sync")

def sync_so_files(options):
    app_lib_dir = os.path.join(constants.DIR_APP_ROOT, "src", "main", "jniLibs")
    chrome_so_lib_dir = os.path.join(options.chromium_root, "out", options.buildtype,
                                       "chrome_public_apk", "libs", "armeabi-v7a")
    args = {'only':['\\.so$']}
    sync(chrome_so_lib_dir, app_lib_dir, "sync", **args)

def sync_jar_files(options):
    app_lib_dir = os.path.join(constants.DIR_APP_ROOT, "libs")
    chrome_java_lib_dir = os.path.join(options.chromium_root, "out", options.buildtype, "lib.java")
    args = {'only':['\w+_java\\.jar$', 'cacheinvalidation_javalib\\.jar$', 'jsr_305_javalib\\.jar$',
                'protobuf_nano_javalib\\.jar$', 'web_contents_delegate_android_java\\.jar$'],
            'ignore': ['chrome_java\\.jar$']}
    sync(chrome_java_lib_dir, app_lib_dir, "sync", **args)

def sync_chromium_res_files(options):
    library_res_dir = os.path.join(constants.DIR_LIBRARIES_ROOT, "chrome_res", "src", "main", "res")
    chrome_res_dir = os.path.join(options.chromium_root, "chrome", "android", "java", "res")
    sync(chrome_res_dir, library_res_dir, "sync")

    chrome_res_dir = os.path.join(options.chromium_root, "chrome", "android", "java", "res_chromium")
    sync(chrome_res_dir, library_res_dir, "sync")

    # sync chrome generated string resources
    chrome_gen_res_dir = os.path.join(options.chromium_root, "out", options.buildtype,
                                      "gen", "chrome", "java", "res")
    sync(chrome_gen_res_dir, library_res_dir, "sync")

    # sync grd generated string resources
    chrome_grd_res_dir = os.path.join(options.chromium_root, "out", options.buildtype,
                                      "obj", "chrome", "chrome_strings_grd.gen", "chrome_strings_grd", "res_grit")
    args = {'exclude': ['values-\S+'], 'include': ['values-zh-rCN']}
    sync(chrome_grd_res_dir, library_res_dir, "sync", **args)

    # remove duplicate strings in android_chrome_strings.xml and generated_resources.xml
    resource_util.remove_duplicated_strings(library_res_dir + '/values/android_chrome_strings.xml',
                                            library_res_dir + '/values/generated_resources.xml')

def sync_ui_res_files(options):
    library_res_dir = os.path.join(constants.DIR_LIBRARIES_ROOT, "ui_res", "src", "main", "res")
    ui_res_dir = os.path.join(options.chromium_root, "ui", "android", "java", "res")

    sync(ui_res_dir, library_res_dir, "sync")

    # sync grd generated string resources
    ui_grd_res_dir = os.path.join(options.chromium_root, "out", options.buildtype,
                                      "obj", "ui", "android", "ui_strings_grd.gen", "ui_strings_grd", "res_grit")
    args = {'exclude': ['values-\S+'], 'include': ['values-zh-rCN']}
    sync(ui_grd_res_dir, library_res_dir, "sync", **args)

def sync_content_res_files(options):
    library_res_dir = os.path.join(constants.DIR_LIBRARIES_ROOT, "content_res", "src", "main", "res")
    content_res_dir = os.path.join(options.chromium_root, "content", "public", "android", "java", "res")
    sync(content_res_dir, library_res_dir, "sync")

    # sync grd generated string resources
    content_grd_res_dir = os.path.join(options.chromium_root, "out", options.buildtype,
                                  "obj", "content", "content_strings_grd.gen", "content_strings_grd", "res_grit")
    args = {'exclude': ['values-\S+'], 'include': ['values-zh-rCN']}
    sync(content_grd_res_dir, library_res_dir, "sync", **args)

def sync_datausagechart_res_files(options):
    library_res_dir = os.path.join(constants.DIR_LIBRARIES_ROOT, "datausagechart_res", "src", "main", "res")
    datausagechart_res_dir = os.path.join(options.chromium_root, "third_party", "android_data_chart", "java", "res")
    sync(datausagechart_res_dir, library_res_dir, "sync")

def sync_androidmedia_res_files(options):
    library_res_dir = os.path.join(constants.DIR_LIBRARIES_ROOT, "androidmedia_res", "src", "main", "res")
    media_res_dir = os.path.join(options.chromium_root, "third_party", "android_media", "java", "res")
    sync(media_res_dir, library_res_dir, "sync")

def sync_manifest_files(options):
    main_dir = os.path.join(constants.DIR_APP_ROOT, "src", "main")
    public_apk_gen_dir = os.path.join(options.chromium_root, "out", options.buildtype,
                                      "gen", "chrome_public_apk_manifest")
    sync(public_apk_gen_dir, main_dir, "sync")

    # sync meta xml files
    xml_dir = os.path.join(constants.DIR_APP_ROOT, "src", "main", "res", "xml")
    policy_gen_dir = os.path.join(options.chromium_root, "out", options.buildtype,
                                  "gen", "policy")
    args = {'only': ['\S+\\.xml']}
    # TODO(alex)
    # sync(policy_gen_dir, xml_dir, "sync", **args)

def sync_data_files(options):
    # TODO(alex)
    # locales_dir = os.path.join(constants.DIR_LIBRARIES_ROOT, "chrome_res", "src", "main", "res", "raw")
    # pak_gen_dir = os.path.join(options.chromium_root, "out", options.buildtype, "locales")
    # args = {'only': ['en-US.pak', 'zh-CN.pak']}
    # sync(pak_gen_dir, locales_dir, "sync", **args)

    assets_dir = os.path.join(constants.DIR_APP_ROOT, "src", "main", "assets")
    webview_assets_dir = os.path.join(options.chromium_root, "out", options.buildtype, "android_webview_apk", "assets")
    sync(webview_assets_dir, assets_dir, "sync")

def main(argv):
    parser = optparse.OptionParser(usage='Usage: %prog [options]', description=__doc__)
    parser.add_option('--chromium_root',
                      default="/work/chromium/v42.0.2311.68/chromium-android/src",
                      help="The root of chromium sources")
    parser.add_option('--buildtype',
                      default="Debug",
                      help="build type of chromium build(Debug or Release), default Debug")
    options, args = parser.parse_args(argv)

    if options.buildtype not in ["Debug", "Release"]:
        print("buildtype argument value must be Debug or Release")
        exit(0)

    #sync_java_files(options)
    #sync_jar_files(options)
    #sync_chromium_res_files(options)
    #sync_ui_res_files(options)
    #sync_content_res_files(options)
    #sync_datausagechart_res_files(options)
    #sync_androidmedia_res_files(options)
    #sync_manifest_files(options)
    sync_data_files(options)

if __name__ == '__main__':
    main(sys.argv)
