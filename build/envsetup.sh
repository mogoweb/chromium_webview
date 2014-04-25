#!/bin/bash

# Copyright (c) 2014 mogoweb. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

SCRIPT_DIR="$(dirname "${BASH_SOURCE:-$0}")"

. ${SCRIPT_DIR}/../../build/android/envsetup.sh "$@"

export PATH=$PATH:${CHROME_SRC}/chromeview/tools:${CHROME_SRC}/chromeview/build

export CHROMIUM_GYP_FILE=${CHROME_SRC}/chromeview/chromeview.gyp 
 
mogo_gyp() {
  echo "GYP_GENERATORS set to '$GYP_GENERATORS'"
  (
    "${CHROME_SRC}/build/gyp_chromium" --depth="${CHROME_SRC}" --check "$@"
  )
}
