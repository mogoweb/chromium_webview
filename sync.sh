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

# chromeview
rsync -avz ${CHROMIUM_SRC}/out/assets/chromium_testshell/chrome_100_percent.pak ${CHROMIUMVIEW_PROJECT_ROOT}/assets
rsync -avz ${CHROMIUM_SRC}/out/assets/chromium_testshell/en-US.pak ${CHROMIUMVIEW_PROJECT_ROOT}/assets
rsync -avz ${CHROMIUM_SRC}/out/assets/chromium_testshell/resources.pak ${CHROMIUMVIEW_PROJECT_ROOT}/assets
rsync -avz ${CHROMIUM_SRC}/out/assets/chromium_testshell/zh-CN.pak ${CHROMIUMVIEW_PROJECT_ROOT}/assets
arm-linux-androideabi-strip --strip-unneeded -o ${CHROMIUMVIEW_PROJECT_ROOT}/libs/armeabi-v7a/libchromeview.so ${CHROMIUM_SRC}/out/${BUILDTYPE}/lib/libchromeview.so

# Resources.
#rsync -avz ${CHROMIUM_SRC}/content/public/android/java/resource_map/ ${CHROMIUMVIEW_PROJECT_ROOT}/src/
#rsync -avz ${CHROMIUM_SRC}/ui/android/java/resource_map/ ${CHROMIUMVIEW_PROJECT_ROOT}/src/
rsync -avz ${CHROMIUM_SRC}/chrome/android/java/res/ ${CHROMIUMVIEW_PROJECT_ROOT}/res/
rsync -avz ${CHROMIUM_SRC}/content/public/android/java/res/ ${CHROMIUMVIEW_PROJECT_ROOT}/res/
rsync -avz ${CHROMIUM_SRC}/ui/android/java/res/ ${CHROMIUMVIEW_PROJECT_ROOT}/res/
rsync -avz ${CHROMIUM_SRC}/out/${BUILDTYPE}/gen/content_java/res_grit/values/ ${CHROMIUMVIEW_PROJECT_ROOT}/res/values/
rsync -avz ${CHROMIUM_SRC}/out/${BUILDTYPE}/gen/content_java/res_grit/values-zh-rCN/ ${CHROMIUMVIEW_PROJECT_ROOT}/res/values-zh-rCN/
rsync -avz ${CHROMIUM_SRC}/out/${BUILDTYPE}/gen/ui_java/res_grit/values/ ${CHROMIUMVIEW_PROJECT_ROOT}/res/values/
rsync -avz ${CHROMIUM_SRC}/out/${BUILDTYPE}/gen/ui_java/res_grit/values-zh-rCN/ ${CHROMIUMVIEW_PROJECT_ROOT}/res/values-zh-rCN/
rsync -avz ${CHROMIUM_SRC}/out/${BUILDTYPE}/gen/chrome_java/res_grit/values/ ${CHROMIUMVIEW_PROJECT_ROOT}/res/values/
rsync -avz ${CHROMIUM_SRC}/out/${BUILDTYPE}/gen/chrome_java/res_grit/values-zh-rCN/ ${CHROMIUMVIEW_PROJECT_ROOT}/res/values-zh-rCN/
rsync -avz ${CHROMIUM_SRC}/out/${BUILDTYPE}/gen/chrome/java/res/values/ ${CHROMIUMVIEW_PROJECT_ROOT}/res/values/
rsync -avz ${CHROMIUM_SRC}/out/${BUILDTYPE}/gen/chrome/java/res/values-zh-rCN/ ${CHROMIUMVIEW_PROJECT_ROOT}/res/values-zh-rCN/

# ContentView dependencies.
rsync -avz ${CHROMIUM_SRC}/base/android/java/src/ ${CHROMIUMVIEW_PROJECT_ROOT}/src/
rsync -avz ${CHROMIUM_SRC}/content/public/android/java/src/ ${CHROMIUMVIEW_PROJECT_ROOT}/src/
rsync -avz ${CHROMIUM_SRC}/media/base/android/java/src/ ${CHROMIUMVIEW_PROJECT_ROOT}/src/
rsync -avz ${CHROMIUM_SRC}/net/android/java/src/ ${CHROMIUMVIEW_PROJECT_ROOT}/src/
rsync -avz ${CHROMIUM_SRC}/ui/android/java/src/ ${CHROMIUMVIEW_PROJECT_ROOT}/src/

# chrome
rsync -avz ${CHROMIUM_SRC}/chrome/android/java/src/ ${CHROMIUMVIEW_PROJECT_ROOT}/src/

# sync, need by chrome
rsync -avz ${CHROMIUM_SRC}/sync/android/java/src/ ${CHROMIUMVIEW_PROJECT_ROOT}/src/

# Strip a ContentView file that's not supposed to be here.
rm ${CHROMIUMVIEW_PROJECT_ROOT}/src/org/chromium/content/common/common.aidl

# Browser components.
rsync -avz ${CHROMIUM_SRC}/components/web_contents_delegate_android/android/java/src/ ${CHROMIUMVIEW_PROJECT_ROOT}/src/
rsync -avz ${CHROMIUM_SRC}/components/navigation_interception/android/java/src/ ${CHROMIUMVIEW_PROJECT_ROOT}/src/
rsync -avz ${CHROMIUM_SRC}/components/autofill/core/browser/android/java/src/ ${CHROMIUMVIEW_PROJECT_ROOT}/src/

# chrome resources.
#rsync -avz ${CHROMIUM_SRC}/out/${BUILDTYPE}/gen/chrome_java/java_R/ ${CHROMIUMVIEW_PROJECT_ROOT}/src/ 

# Generated files.
rsync -avz ${CHROMIUM_SRC}/out/${BUILDTYPE}/gen/templates/ ${CHROMIUMVIEW_PROJECT_ROOT}/src/

# Get rid of
rm -rf ${CHROMIUMVIEW_PROJECT_ROOT}/src/org.chromium.content.browser
rm -rf ${CHROMIUMVIEW_PROJECT_ROOT}/src/org.chromium.net

# JARs.
rsync -avz ${CHROMIUM_SRC}/out/${BUILDTYPE}/lib.java/guava_javalib.jar ${CHROMIUMVIEW_PROJECT_ROOT}/libs/
rsync -avz ${CHROMIUM_SRC}/out/${BUILDTYPE}/lib.java/jsr_305_javalib.jar ${CHROMIUMVIEW_PROJECT_ROOT}/libs/
rsync -avz ${CHROMIUM_SRC}/out/${BUILDTYPE}/lib.java/cacheinvalidation_javalib.jar ${CHROMIUMVIEW_PROJECT_ROOT}/libs/
rsync -avz ${CHROMIUM_SRC}/out/${BUILDTYPE}/lib.java/cacheinvalidation_proto_java.jar ${CHROMIUMVIEW_PROJECT_ROOT}/libs/
rsync -avz ${CHROMIUM_SRC}/out/${BUILDTYPE}/lib.java/eyesfree_java.jar ${CHROMIUMVIEW_PROJECT_ROOT}/libs/
rsync -avz ${CHROMIUM_SRC}/out/${BUILDTYPE}/lib.java/protobuf_lite_javalib.jar ${CHROMIUMVIEW_PROJECT_ROOT}/libs/

# sync NativeLibraries.java
rsync -avz ${CHROMIUM_SRC}/chromeview/generated_src/ ${CHROMIUMVIEW_PROJECT_ROOT}/src/

# sync test class
rsync -avz ${CHROMIUM_SRC}/base/test/android/javatests/src/ ${SHELLVIEW_TEST_PROJECT_ROOT}/src/
rsync -avz ${CHROMIUM_SRC}/content/public/test/android/javatests/src/ ${SHELLVIEW_TEST_PROJECT_ROOT}/src/
