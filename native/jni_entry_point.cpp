// Copyright (c) 2014 mogoweb. All rights reserved.
// Copyright (c) 2012 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#include "android_webview/lib/main/aw_main_delegate.h"
#include "android_webview/native/android_webview_jni_registrar.h"
#include "base/android/jni_android.h"
#include "base/android/jni_registrar.h"
#include "base/android/library_loader/library_loader_hooks.h"
#include "components/navigation_interception/component_jni_registrar.h"
#include "components/web_contents_delegate_android/component_jni_registrar.h"
#include "content/public/app/android_library_loader_hooks.h"
#include "content/public/app/content_main.h"
#include "url/url_util.h"

static base::android::RegistrationMethod
    kWebViewDependencyRegisteredMethods[] = {
    { "NavigationInterception",
        navigation_interception::RegisterNavigationInterceptionJni },
    { "WebContentsDelegateAndroid",
        web_contents_delegate_android::RegisterWebContentsDelegateAndroidJni },
};

namespace android {
  void RegisterDrawGLFunctor(JNIEnv* env);
}

// This is called by the VM when the shared library is first loaded.
// Most of the initialization is done in LibraryLoadedOnMainThread(), not here.
JNI_EXPORT jint JNI_OnLoad(JavaVM* vm, void* reserved) {

  base::android::SetLibraryLoadedHook(&content::LibraryLoaded);

  base::android::InitVM(vm);
  JNIEnv* env = base::android::AttachCurrentThread();
  if (!base::android::RegisterLibraryLoaderEntryHook(env))
    return -1;

  // Register content JNI functions now, rather than waiting until
  // LibraryLoadedOnMainThread, so that we can call into native code early.
  if (!content::EnsureJniRegistered(env))
    return -1;

  // Register JNI for components we depend on.
  if (!RegisterNativeMethods(
      env,
      kWebViewDependencyRegisteredMethods,
      arraysize(kWebViewDependencyRegisteredMethods)))
    return -1;

  if (!android_webview::RegisterJni(env))
    return -1;

  android::RegisterDrawGLFunctor(env);

  content::SetContentMainDelegate(new android_webview::AwMainDelegate());

  // Initialize url_util here while we are still single-threaded, in case we use
  // CookieManager before initializing Chromium (which would normally have done
  // this). It's safe to call this multiple times.
  url_util::Initialize();

  return JNI_VERSION_1_4;
}
