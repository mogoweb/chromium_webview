// Copyright (c) 2012 The Chromium Authors. All rights reserved.
// Copyright (c) 2014 mogoweb. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#ifndef CHROMEVIEW_NATIVE_CHROMEVIEW_TAB_H_
#define CHROMEVIEW_NATIVE_CHROMEVIEW_TAB_H_

#include <jni.h>

#include "base/compiler_specific.h"
#include "base/memory/scoped_ptr.h"
#include "chrome/browser/android/tab_android.h"

namespace browser_sync {
class SyncedTabDelegate;
}

namespace chrome {
struct NavigateParams;
}

namespace chrome {
namespace android {
class ChromeWebContentsDelegateAndroid;
}
}

namespace content {
class WebContents;
}

namespace ui {
class WindowAndroid;
}

class ChromeViewTab : public TabAndroid {
 public:
  ChromeViewTab(JNIEnv* env, jobject obj);
  void Destroy(JNIEnv* env, jobject obj);

  // --------------------------------------------------------------------------
  // TabAndroid Methods
  // --------------------------------------------------------------------------
  virtual void OnReceivedHttpAuthRequest(jobject auth_handler,
                                         const string16& host,
                                         const string16& realm) OVERRIDE;
  virtual void ShowContextMenu(
      const content::ContextMenuParams& params) OVERRIDE;

  virtual void ShowCustomContextMenu(
      const content::ContextMenuParams& params,
      const base::Callback<void(int)>& callback) OVERRIDE;

  virtual void AddShortcutToBookmark(const GURL& url,
                                     const string16& title,
                                     const SkBitmap& skbitmap,
                                     int r_value,
                                     int g_value,
                                     int b_value) OVERRIDE;
  virtual void EditBookmark(int64 node_id,
                            const base::string16& node_title,
                            bool is_folder,
                            bool is_partner_bookmark) OVERRIDE;

  virtual bool ShouldWelcomePageLinkToTermsOfService() OVERRIDE;
  virtual void OnNewTabPageReady() OVERRIDE;

  virtual void HandlePopupNavigation(chrome::NavigateParams* params) OVERRIDE;

  // Register the Tab's native methods through JNI.
  static bool RegisterChromeViewTab(JNIEnv* env);

  // --------------------------------------------------------------------------
  // Methods called from Java via JNI
  // --------------------------------------------------------------------------
  base::android::ScopedJavaLocalRef<jstring> FixupUrl(JNIEnv* env,
                                                      jobject obj,
                                                      jstring url);

 protected:
  virtual ~ChromeViewTab();

 private:
  DISALLOW_COPY_AND_ASSIGN(ChromeViewTab);
};

#endif  // CHROMEVIEW_NATIVE_CHROMEVIEW_TAB_H_
