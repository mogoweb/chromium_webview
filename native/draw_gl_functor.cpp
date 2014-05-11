/*
 * Copyright (C) 2012 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

// Provides a webviewchromium glue layer adapter from the internal Android
// GL Functor data types into the types the chromium stack expects, and back.

#define LOG_TAG "webviewchromium_plat_support"

#include "android_webview/public/browser/draw_gl.h"

#include <errno.h>
#include <jni.h>
#include <private/hwui/DrawGlInfo.h>
#include <string.h>
#include <sys/resource.h>
#include <sys/time.h>
#include <utils/Functor.h>
#include <android/log.h>

#define NELEM(x) ((int) (sizeof(x) / sizeof((x)[0])))
#define COMPILE_ASSERT(expr, err) static const char err[(expr) ? 1 : -1] = "";

#define ALOGE(...) (__android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__))

#define CONDITION(cond)    (__builtin_expect((cond)!=0, 0))

#define LOG_ALWAYS_FATAL_IF(cond, ...) \
    ( (CONDITION(cond)) \
    ? ((void)__android_log_assert(#cond, LOG_TAG, ## __VA_ARGS__)) \
    : (void)0 )

namespace android {
namespace {

AwDrawGLFunction* g_aw_drawgl_function = NULL;

class DrawGLFunctor : public Functor {
 public:
  DrawGLFunctor(jint view_context) : view_context_(view_context) {}
  virtual ~DrawGLFunctor() {}

  // Functor
  virtual status_t operator ()(int what, void* data) {
    using uirenderer::DrawGlInfo;
    if (!g_aw_drawgl_function) {
      ALOGE("Cannot draw: no DrawGL Function installed");
      return DrawGlInfo::kStatusDone;
    }

    AwDrawGLInfo aw_info;
    aw_info.mode = (what == DrawGlInfo::kModeProcess) ?
        AwDrawGLInfo::kModeProcess : AwDrawGLInfo::kModeDraw;
    DrawGlInfo* gl_info = reinterpret_cast<DrawGlInfo*>(data);

    // Map across the input values.
    aw_info.clip_left = gl_info->clipLeft;
    aw_info.clip_top = gl_info->clipTop;
    aw_info.clip_right = gl_info->clipRight;
    aw_info.clip_bottom = gl_info->clipBottom;
    aw_info.width = gl_info->width;
    aw_info.height = gl_info->height;
    aw_info.is_layer = gl_info->isLayer;
    COMPILE_ASSERT(NELEM(aw_info.transform) == NELEM(gl_info->transform),
                   mismatched_transform_matrix_sizes);
    for (int i = 0; i < NELEM(aw_info.transform); ++i) {
      aw_info.transform[i] = gl_info->transform[i];
    }

    // Also pre-initialize the output fields in case the implementation does
    // not modify them.
    aw_info.status_mask = AwDrawGLInfo::kStatusMaskDone;
    aw_info.dirty_left = gl_info->dirtyLeft;
    aw_info.dirty_top = gl_info->dirtyTop;
    aw_info.dirty_right = gl_info->dirtyRight;
    aw_info.dirty_bottom = gl_info->dirtyBottom;

    // Invoke the DrawGL method.
    g_aw_drawgl_function(view_context_, &aw_info, NULL);

    // Copy out the outputs.
    gl_info->dirtyLeft = aw_info.dirty_left;
    gl_info->dirtyTop = aw_info.dirty_top;
    gl_info->dirtyRight = aw_info.dirty_right;
    gl_info->dirtyBottom = aw_info.dirty_bottom;

    // Calculate the return code.
    status_t res = DrawGlInfo::kStatusDone;
    if (aw_info.status_mask & AwDrawGLInfo::kStatusMaskDraw)
      res |= DrawGlInfo::kStatusDraw;
    if (aw_info.status_mask & AwDrawGLInfo::kStatusMaskInvoke)
      res |= DrawGlInfo::kStatusInvoke;

    return res;
  }

 private:
  int view_context_;
};

// Raise the file handle soft limit to the hard limit since gralloc buffers
// uses file handles.
void RaiseFileNumberLimit() {
  static bool have_raised_limit = false;
  if (have_raised_limit)
    return;

  have_raised_limit = true;
  struct rlimit limit_struct;
  limit_struct.rlim_cur = 0;
  limit_struct.rlim_max = 0;
  if (getrlimit(RLIMIT_NOFILE, &limit_struct) == 0) {
    limit_struct.rlim_cur = limit_struct.rlim_max;
    if (setrlimit(RLIMIT_NOFILE, &limit_struct) != 0) {
      ALOGE("setrlimit failed: %s", strerror(errno));
    }
  } else {
    ALOGE("getrlimit failed: %s", strerror(errno));
  }
}

jint CreateGLFunctor(JNIEnv*, jclass, jint view_context) {
  RaiseFileNumberLimit();
  return reinterpret_cast<jint>(new DrawGLFunctor(view_context));
}

void DestroyGLFunctor(JNIEnv*, jclass, jint functor) {
  delete reinterpret_cast<DrawGLFunctor*>(functor);
}

void SetChromiumAwDrawGLFunction(JNIEnv*, jclass, jint draw_function) {
  g_aw_drawgl_function = reinterpret_cast<AwDrawGLFunction*>(draw_function);
}

const char kClassName[] = "com/mogoweb/chrome/impl/DrawGLFunctor";
const JNINativeMethod kJniMethods[] = {
    { "nativeCreateGLFunctor", "(I)I",
        reinterpret_cast<void*>(CreateGLFunctor) },
    { "nativeDestroyGLFunctor", "(I)V",
        reinterpret_cast<void*>(DestroyGLFunctor) },
    { "nativeSetChromiumAwDrawGLFunction", "(I)V",
        reinterpret_cast<void*>(SetChromiumAwDrawGLFunction) },
};

}  // namespace

void RegisterDrawGLFunctor(JNIEnv* env) {
  jclass clazz = env->FindClass(kClassName);
  LOG_ALWAYS_FATAL_IF(!clazz, "Unable to find class '%s'", kClassName);

  int res = env->RegisterNatives(clazz, kJniMethods, NELEM(kJniMethods));
  LOG_ALWAYS_FATAL_IF(res < 0, "register native methods failed: res=%d", res);
}

}  // namespace android
