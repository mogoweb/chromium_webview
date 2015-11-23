# Copyright (c) 2014 mogoweb. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.
{
  'variables': {
    # A hook that can be overridden in other repositories to add additional
    # compilation targets to 'All'
    'app_targets%': [],
    # For Android-specific targets.
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
      'android_unmangled_name': 1,
      'dependencies': [
        '../android_webview/android_webview.gyp:android_webview_common',
      ],
      'variables': {
        'use_native_jni_exports': 1,
      },
      'sources': [
        'native/jni_entry_point.cpp',
        'native/draw_gl_functor.cpp',
      ],
      'include_dirs': [
        './native',
      ],
      'cflags!': [
        '-Werror',
      ],
    },
  ],
}
