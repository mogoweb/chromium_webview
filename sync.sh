#!/bin/bash

CURRENT_DIR="$(readlink -f "$(dirname $BASH_SOURCE)/..")"
if [[ -z "${CHROME_SRC}" ]]; then
  # If $CHROME_SRC was not set, assume current directory is CHROME_SRC.
  export CHROME_SRC="${CURRENT_DIR}"
fi

if [[ "${CURRENT_DIR/"${CHROME_SRC}"/}" == "${CURRENT_DIR}" ]]; then
  # If current directory is not in $CHROME_SRC, it might be set for other
  # source tree. If $CHROME_SRC was set correctly and we are in the correct
  # directory, "${CURRENT_DIR/"${CHROME_SRC}"/}" will be "".
  # Otherwise, it will equal to "${CURRENT_DIR}"
  echo "Warning: Current directory is out of CHROME_SRC, it may not be \
the one you want."
  echo "${CHROME_SRC}"
fi

RELEASE=Debug
ANDROID_PROJECT_ROOT=java

# android_webview
rsync -avz ${CHROME_SRC}/out/${RELEASE}/android_webview_apk/assets/*.pak ${ANDROID_PROJECT_ROOT}/assets
rsync -avz ${CHROME_SRC}/out/${RELEASE}/android_webview_apk/libs/ ${ANDROID_PROJECT_ROOT}/libs
rsync -avz ${CHROME_SRC}/android_webview/java/src/ ${ANDROID_PROJECT_ROOT}/src/

## Dependencies inferred from android_webview/Android.mk

# Resources.
rsync -avz ${CHROME_SRC}/content/public/android/java/resource_map/ ${ANDROID_PROJECT_ROOT}/src/
rsync -avz ${CHROME_SRC}/ui/android/java/resource_map/ ${ANDROID_PROJECT_ROOT}/src/

# ContentView dependencies.
rsync -avz ${CHROME_SRC}/base/android/java/src/ ${ANDROID_PROJECT_ROOT}/src/
rsync -avz ${CHROME_SRC}/content/public/android/java/src/ ${ANDROID_PROJECT_ROOT}/src/
rsync -avz ${CHROME_SRC}/media/base/android/java/src/ ${ANDROID_PROJECT_ROOT}/src/
rsync -avz ${CHROME_SRC}/net/android/java/src/ ${ANDROID_PROJECT_ROOT}/src/
rsync -avz ${CHROME_SRC}/ui/android/java/src/ ${ANDROID_PROJECT_ROOT}/src/
rsync -avz ${CHROME_SRC}/third_party/eyesfree/src/android/java/src/ ${ANDROID_PROJECT_ROOT}/src/

# Strip a ContentView file that's not supposed to be here.
rm ${ANDROID_PROJECT_ROOT}/src/org/chromium/content/common/common.aidl

# Get rid of the .git directory in eyesfree.
rm -rf ${ANDROID_PROJECT_ROOT}/src/com/googlecode/eyesfree/braille/.git

# Browser components.
rsync -avz ${CHROME_SRC}/components/web_contents_delegate_android/android/java/src/ ${ANDROID_PROJECT_ROOT}/src/
rsync -avz ${CHROME_SRC}/components/navigation_interception/android/java/src/ ${ANDROID_PROJECT_ROOT}/src/

# Generated files.
rsync -avz ${CHROME_SRC}/out/${RELEASE}/gen/templates/ ${ANDROID_PROJECT_ROOT}/src/

# Get rid of
rm -rf ${ANDROID_PROJECT_ROOT}/src/org.chromium.content.browser
rm -rf ${ANDROID_PROJECT_ROOT}/src/org.chromium.net

# JARs.
rsync -avz ${CHROME_SRC}/out/${RELEASE}/lib.java/guava_javalib.jar ${ANDROID_PROJECT_ROOT}/libs/
rsync -avz ${CHROME_SRC}/out/${RELEASE}/lib.java/jsr_305_javalib.jar ${ANDROID_PROJECT_ROOT}/libs/

# android_webview generated sources. Must come after all the other sources.
rsync -avz ${CHROME_SRC}/android_webview/java/generated_src/ ${ANDROID_PROJECT_ROOT}/src/
