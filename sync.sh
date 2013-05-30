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

BUILDTYPE==Debug
CHROMIUMVIEW_PROJECT_ROOT=java

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

# android_webview
rsync -avz ${CHROMIUM_SRC}/out/${BUILDTYPE}/android_webview_apk/assets/*.pak ${CHROMIUMVIEW_PROJECT_ROOT}/assets
rsync -avz ${CHROMIUM_SRC}/out/${BUILDTYPE}/android_webview_apk/libs/ ${CHROMIUMVIEW_PROJECT_ROOT}/libs
rsync -avz ${CHROMIUM_SRC}/android_webview/java/src/ ${CHROMIUMVIEW_PROJECT_ROOT}/src/

## Dependencies inferred from android_webview/Android.mk

# Resources.
rsync -avz ${CHROMIUM_SRC}/content/public/android/java/resource_map/ ${CHROMIUMVIEW_PROJECT_ROOT}/src/
rsync -avz ${CHROMIUM_SRC}/ui/android/java/resource_map/ ${CHROMIUMVIEW_PROJECT_ROOT}/src/

# ContentView dependencies.
rsync -avz ${CHROMIUM_SRC}/base/android/java/src/ ${CHROMIUMVIEW_PROJECT_ROOT}/src/
rsync -avz ${CHROMIUM_SRC}/content/public/android/java/src/ ${CHROMIUMVIEW_PROJECT_ROOT}/src/
rsync -avz ${CHROMIUM_SRC}/media/base/android/java/src/ ${CHROMIUMVIEW_PROJECT_ROOT}/src/
rsync -avz ${CHROMIUM_SRC}/net/android/java/src/ ${CHROMIUMVIEW_PROJECT_ROOT}/src/
rsync -avz ${CHROMIUM_SRC}/ui/android/java/src/ ${CHROMIUMVIEW_PROJECT_ROOT}/src/
rsync -avz ${CHROMIUM_SRC}/third_party/eyesfree/src/android/java/src/ ${CHROMIUMVIEW_PROJECT_ROOT}/src/

# Strip a ContentView file that's not supposed to be here.
rm ${CHROMIUMVIEW_PROJECT_ROOT}/src/org/chromium/content/common/common.aidl

# Get rid of the .git directory in eyesfree.
rm -rf ${CHROMIUMVIEW_PROJECT_ROOT}/src/com/googlecode/eyesfree/braille/.git

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
rsync -avz ${CHROMIUM_SRC}/android_webview/java/generated_src/ ${CHROMIUMVIEW_PROJECT_ROOT}/src/
