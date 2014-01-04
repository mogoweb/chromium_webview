// Copyright (c) 2012 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#include "chromeview/native/chromeview_main_delegate.h"

#include "base/android/jni_android.h"
#include "base/android/jni_registrar.h"
#include "chrome/browser/search_engines/template_url_prepopulate_data.h"
#include "chromeview/native/chromeview_tab.h"

static const char kDefaultCountryCode[] = "US";

static base::android::RegistrationMethod kRegistrationMethods[] = {
    { "ChromeViewTab", ChromeViewTab::RegisterChromeViewTab },
};

ChromeMainDelegateAndroid* ChromeMainDelegateAndroid::Create() {
  return new ChromeViewMainDelegate();
}

ChromeViewMainDelegate::ChromeViewMainDelegate() {
}

ChromeViewMainDelegate::~ChromeViewMainDelegate() {
}

bool ChromeViewMainDelegate::BasicStartupComplete(int* exit_code) {
  TemplateURLPrepopulateData::InitCountryCode(kDefaultCountryCode);
  return ChromeMainDelegateAndroid::BasicStartupComplete(exit_code);
}

bool ChromeViewMainDelegate::RegisterApplicationNativeMethods(
    JNIEnv* env) {
  return ChromeMainDelegateAndroid::RegisterApplicationNativeMethods(env) &&
      base::android::RegisterNativeMethods(env,
                                           kRegistrationMethods,
                                           arraysize(kRegistrationMethods));
}
