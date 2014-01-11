#!/usr/bin/env python

# Copyright (c) 2013 China LianTong. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

import os
import subprocess

repos = [
  #'external/googlemock',
  #'external/googletest',
  #'external/angle',
  #'chromium/third_party/ffmpeg',
  #'chromium/deps/flac',
  #'chromium/deps/icu46',
  #'external/skia/gyp',
  #'external/skia/include',
  #'external/skia/src',
  #'external/v8',
  #'android_tools',
  #'git/chromium/src/third_party/freetype',
  #'external/webrtc/stable/talk',
  #'external/libphonenumber/cpp/src/phonenumbers',
  #'external/libphonenumber/resources',
  #'external/libphonenumber/cpp/test',
  #'external/leveldb',
  #'external/open-vcdiff',
  #'chromium/deps/hunspell',
  #'chromium/deps/hunspell_dictionaries',
  #'external/libyuv',
  #'external/ots',
  #'chromium/deps/libjpeg_turbo',
  #'chromium/deps/openssl',
  #'external/grit-i18n',
  #'external/gyp',
  #'chromium/deps/mesa',
  #'external/webrtc/deps/third_party/openmax',
  #'chromium/deps/libsrtp',
  #'external/webrtc/stable/webrtc',
  #'chromium/deps/libvpx',
  #'external/trace-viewer',
  #'external/linux-syscall-support/lss',
  #'external/jsr-305',
  #'chromium/deps/jarjar',
  #'chromium/deps/httpcomponents-core',
  #'chromium/deps/httpcomponents-client',
  #'external/guava-libraries',
  #'external/eyes-free/braille/client/src/com/googlecode/eyesfree/braille',
  #'chromium/deps/apache-mime4j',
  #'external/google-cache-invalidation-api/src',
  #'external/sfntly/cpp/src',
  #'chromium/deps/opus',
  #'external/snappy',
  #'external/smhasher',
  #'external/accessibility-developer-tools',
  #'external/google-breakpad/src',
  #'chromium/tools/swarm_client',
  #'chromium/src/third_party/freetype',
  # need by chrome v28
  #'external/google-url',
  #'external/angleproject',
  #'external/libjingle',
  #'external/pymox',
  #'external/v8-i18n',
  #'chromium/cdm',
]
google_repo_root = 'https://chromium.googlesource.com/'
local_repo_root = '/home/alex/gitroot/chromium'

def mirror_chromium_repos():

  for repo in repos:
    repo_folder = os.path.dirname(local_repo_root + '/' + repo)
    google_repo = google_repo_root + repo + '.git'
    print repo_folder
    
    if not os.path.exists(repo_folder):
      os.makedirs(repo_folder)

    os.chdir(repo_folder)
    # git clone with mirror
    subprocess.call(["git", "clone", "--mirror", google_repo])

def main():
  mirror_chromium_repos()

if __name__ == '__main__':
  main()
