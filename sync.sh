#!/bin/bash
#
# Copyright (c) 2013 mogoweb. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.
#

# A script used to sync libraries and source from Chromium project.
# before execute this script, you should make android_webview_apk under
# chromium source. We assume this script is in the second level folder of
# chromium's main source directory. you may need modify this script
# according to your source structure.
#
# Use --help to print full usage instructions.
#

PROGNAME=$(basename "$0")
PROGDIR=$(dirname "$0")

# Location of Chromium-top-level sources.
CHROMIUM_SRC=$(cd "$PROGDIR"/.. && pwd 2>/dev/null)

PATH=$PATH:$CHROMIUM_SRC/third_party/android_tools/ndk/toolchains/arm-linux-androideabi-4.8/prebuilt/linux-x86_64/bin

BUILDTYPE=Debug
CHROMIUMVIEW_PROJECT_ROOT=java
SHELLVIEW_TEST_PROJECT_ROOT=testshell/javatests

for opt; do
  optarg=$(expr "x$opt" : 'x[^=]*=\(.*\)')
  case $opt in
    --help|-h|-?)
      HELP=true
      ;;
    --release)
      BUILDTYPE=Release
      ;;
    --debug)
      BUILDTYPE=Debug
      ;;
    --project_root=*)
      CHROMIUMVIEW_PROJECT_ROOT=$optarg
      ;;
    -*)
      panic "Unknown option $OPT, see --help." >&2
      ;;
  esac
done

if [ "$HELP" ]; then
  cat <<EOF
Usage: $PROGNAME [options]

Sync libraries and source from Chromium project.

Valid options:
  --help|-h|-?          Print this message.
  --debug               Use libraries under out/Debug.
  --release             Use libraries under out/Release.
  --project_root=<path> The root of ChromiumView project

EOF

 exit 0
fi

# libchromeview
arm-linux-androideabi-strip --strip-unneeded -o ${CHROMIUMVIEW_PROJECT_ROOT}/libs/armeabi-v7a/libchromeview.so ${CHROMIUM_SRC}/out/${BUILDTYPE}/lib/libchromeview.so

# android_webview
rsync -avz ${CHROMIUM_SRC}/out/${BUILDTYPE}/android_webview_apk/assets/*.pak ${CHROMIUMVIEW_PROJECT_ROOT}/assets
rsync -avz ${CHROMIUM_SRC}/android_webview/java/src/ ${CHROMIUMVIEW_PROJECT_ROOT}/src/

## Dependencies inferred from android_webview/Android.mk

# Resources.
rsync -avz ${CHROMIUM_SRC}/content/public/android/java/resource_map/ ${CHROMIUMVIEW_PROJECT_ROOT}/src/
rsync -avz ${CHROMIUM_SRC}/ui/android/java/resource_map/ ${CHROMIUMVIEW_PROJECT_ROOT}/src/

# sync content resources
sync_content_resources() {
    # Sync content resources such as drawable, layout, etc. Assume they are not duplicate.
    local resfolders=(drawable-hdpi drawable-mdpi drawable-xhdpi drawable-xxhdpi layout layout-land menu mipmap-hdpi mipmap-mdpi mipmap-xhdpi mipmap-xxhdpi)
    for folder in ${resfolders[*]}
    do
        rsync -avz ${CHROMIUM_SRC}/content/public/android/java/res/$folder/ ${CHROMIUMVIEW_PROJECT_ROOT}/res/$folder/
    done

    # Sync content resource such as strings. They may have duplicated name, so rename it
    local stringfolders=(values/attrs values/dimens values/strings values-v17/styles)
    for stringfolder in ${stringfolders[*]}
    do
        rsync -avz ${CHROMIUM_SRC}/content/public/android/java/res/${stringfolder}.xml ${CHROMIUMVIEW_PROJECT_ROOT}/res/${stringfolder}_content.xml
    done

    # Sync content strings. They are generated form grit
    # not found?
    #local gritfolders=(values values-zh-rCN)
    #for gritfolder in ${gritfolders[*]}
    #do
    #    rsync -avz ${CHROMIUM_SRC}/out/${BUILDTYPE}/gen/content_java/res_grit/$gritfolder/ ${CHROMIUMVIEW_PROJECT_ROOT}/res/$gritfolder/
    #done
}

# sync ui resources
sync_ui_resources() {
    # Sync ui resources such as drawable, layout, etc. Assume they are not duplicate.
    local resfolders=(drawable drawable-hdpi drawable-xhdpi layout)
    for folder in ${resfolders[*]}
    do
        rsync -avz ${CHROMIUM_SRC}/ui/android/java/res/$folder/ ${CHROMIUMVIEW_PROJECT_ROOT}/res/$folder/
    done

    # Sync ui resource such as strings. They may have duplicated name, so rename it
    local stringfolders=(values/colors values/dimens values/strings values/values values-v17/styles)
    for stringfolder in ${stringfolders[*]}
    do
        rsync -avz ${CHROMIUM_SRC}/ui/android/java/res/${stringfolder}.xml ${CHROMIUMVIEW_PROJECT_ROOT}/res/${stringfolder}_ui.xml
    done

    # Sync ui strings. They are generated form grit
    # not found?
    #local gritfolders=(values values-zh-rCN)
    #for gritfolder in ${gritfolders[*]}
    #do
    #    rsync -avz ${CHROMIUM_SRC}/out/${BUILDTYPE}/gen/ui_java/res_grit/$gritfolder/ ${CHROMIUMVIEW_PROJECT_ROOT}/res/$gritfolder/
    #done
}

sync_content_resources
sync_ui_resources

# ContentView dependencies.
rsync -avz ${CHROMIUM_SRC}/base/android/java/src/ ${CHROMIUMVIEW_PROJECT_ROOT}/src/
rsync -avz ${CHROMIUM_SRC}/content/public/android/java/src/ ${CHROMIUMVIEW_PROJECT_ROOT}/src/
rsync -avz ${CHROMIUM_SRC}/media/base/android/java/src/ ${CHROMIUMVIEW_PROJECT_ROOT}/src/
rsync -avz ${CHROMIUM_SRC}/net/android/java/src/ ${CHROMIUMVIEW_PROJECT_ROOT}/src/
rsync -avz ${CHROMIUM_SRC}/ui/android/java/src/ ${CHROMIUMVIEW_PROJECT_ROOT}/src/
rsync -avz ${CHROMIUM_SRC}/third_party/eyesfree/src/android/java/src/ ${CHROMIUMVIEW_PROJECT_ROOT}/src/

# Strip files that's not supposed to be here.
rm ${CHROMIUMVIEW_PROJECT_ROOT}/src/org/chromium/content/common/common.aidl
rm ${CHROMIUMVIEW_PROJECT_ROOT}/src/org/chromium/net/IRemoteAndroidKeyStoreInterface.aidl

# Get rid of the .svn directory in eyesfree.
rm -rf ${CHROMIUMVIEW_PROJECT_ROOT}/src/com/googlecode/eyesfree/braille/.svn

# Browser components.
rsync -avz ${CHROMIUM_SRC}/components/web_contents_delegate_android/android/java/src/ ${CHROMIUMVIEW_PROJECT_ROOT}/src/
rsync -avz ${CHROMIUM_SRC}/components/navigation_interception/android/java/src/ ${CHROMIUMVIEW_PROJECT_ROOT}/src/

# Generated files.
rsync -avz ${CHROMIUM_SRC}/out/${BUILDTYPE}/gen/templates/ ${CHROMIUMVIEW_PROJECT_ROOT}/src/

# Get rid of
rm -rf ${CHROMIUMVIEW_PROJECT_ROOT}/src/org.chromium.content.browser
rm -rf ${CHROMIUMVIEW_PROJECT_ROOT}/src/org.chromium.net

# JARs.
rsync -avz ${CHROMIUM_SRC}/out/${BUILDTYPE}/lib.java/guava_javalib.jar ${CHROMIUMVIEW_PROJECT_ROOT}/libs/
rsync -avz ${CHROMIUM_SRC}/out/${BUILDTYPE}/lib.java/jsr_305_javalib.jar ${CHROMIUMVIEW_PROJECT_ROOT}/libs/

# android_webview generated sources. Must come after all the other sources.
rsync -avz ${CHROMIUM_SRC}/out/${BUILDTYPE}/android_webview_apk/native_libraries_java/NativeLibraries.java ${CHROMIUMVIEW_PROJECT_ROOT}/src/org/chromium/base/library_loader/
# replase standalonelibwebviewchromium with chromeview
sed -i "s/standalonelibwebviewchromium/chromeview/g" ${CHROMIUMVIEW_PROJECT_ROOT}/src/org/chromium/base/library_loader/NativeLibraries.java

# sync test class
rsync -avz ${CHROMIUM_SRC}/base/test/android/javatests/src/ ${SHELLVIEW_TEST_PROJECT_ROOT}/src/
rsync -avz ${CHROMIUM_SRC}/content/public/test/android/javatests/src/ ${SHELLVIEW_TEST_PROJECT_ROOT}/src/
