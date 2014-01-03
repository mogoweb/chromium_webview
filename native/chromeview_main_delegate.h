// Copyright (c) 2012 The Chromium Authors. All rights reserved.
// Copyright (c) 2014 mogoweb. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#ifndef CHROMEVIEW_NATIVE_CHROMEVIEW_MAIN_DELEGATE_H_
#define CHROMEVIEW_NATIVE_CHROMEVIEW_MAIN_DELEGATE_H_

#include "chrome/app/android/chrome_main_delegate_android.h"

class ChromeViewMainDelegate : public ChromeMainDelegateAndroid {
 public:
  ChromeViewMainDelegate();
  virtual ~ChromeViewMainDelegate();

  virtual bool BasicStartupComplete(int* exit_code) OVERRIDE;

  virtual bool RegisterApplicationNativeMethods(JNIEnv* env) OVERRIDE;

 private:
  DISALLOW_COPY_AND_ASSIGN(ChromeViewMainDelegate);
};

#endif  // CHROMEVIEW_NATIVE_CHROMEVIEW_MAIN_DELEGATE_H_
