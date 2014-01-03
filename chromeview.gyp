# Copyright (c) 2014 mogoweb. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.
{
  'variables': {
    # A hook that can be overridden in other repositories to add additional
    # compilation targets to 'All'
    'android_app_targets%': [],
  },
  'targets': [
    {
      'target_name': 'All',
      'type': 'none',
      'dependencies': [
        'libchromeview',
      ],
    },
    {
      'target_name': 'libchromeview',
      'type': 'shared_library',
      'dependencies': [
        '../base/base.gyp:base',
        '../chrome/chrome.gyp:browser_ui',
        '../chrome/chrome.gyp:chrome_android_core',
        '../content/content.gyp:content_app_browser',
      ],
      'sources': [
        '../chrome/android/testshell/testshell_stubs.cc',
        # This file must always be included in the shared_library step to ensure
        # JNI_OnLoad is exported.
        '../chrome/app/android/chrome_jni_onload.cc',
        'native/chromeview_google_location_settings_helper.cc',
        'native/chromeview_google_location_settings_helper.h',
        'native/chromeview_main_delegate.cc',
        'native/chromeview_main_delegate.h',
      ],
      'include_dirs': [
        '<(SHARED_INTERMEDIATE_DIR)/chromeview',
        '../skia/config',
      ],
      'conditions': [
        [ 'order_profiling!=0', {
          'conditions': [
            [ 'OS=="android"', {
              'dependencies': [ '../tools/cygprofile/cygprofile.gyp:cygprofile', ],
            }],
          ],
        }],
        [ 'android_use_tcmalloc==1', {
          'dependencies': [
            '../base/allocator/allocator.gyp:allocator', ],
        }],
      ],
    },
  ],
}