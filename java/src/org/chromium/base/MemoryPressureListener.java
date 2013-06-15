// Copyright 2013 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.base;

import android.content.ComponentCallbacks2;
import android.content.Context;
import android.content.res.Configuration;

import org.chromium.base.MemoryPressureLevelList;


/**
 * This is an internal implementation of the C++ counterpart.
 * It registers a ComponentCallbacks2 with the system, and dispatches into
 * native.
 */
class MemoryPressureListener {
  @CalledByNative
  private static void registerSystemCallback(Context context) {
      context.registerComponentCallbacks(
          new ComponentCallbacks2() {
                @Override
                public void onTrimMemory(int level) {
                    nativeOnMemoryPressure(translate(level));
                }

                @Override
                public void onLowMemory() {
                    nativeOnMemoryPressure(MemoryPressureLevelList.MEMORY_PRESSURE_CRITICAL);
                }

                @Override
                public void onConfigurationChanged(Configuration configuration) {
                }
          });
  }

  private static int translate(int level) {
      if (level == ComponentCallbacks2.TRIM_MEMORY_COMPLETE) {
          return MemoryPressureLevelList.MEMORY_PRESSURE_CRITICAL;
      }
      return MemoryPressureLevelList.MEMORY_PRESSURE_MODERATE;
  }

  private static native void nativeOnMemoryPressure(int memoryPressureType);
}
