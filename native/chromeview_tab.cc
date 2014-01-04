// Copyright (c) 2012 The Chromium Authors. All rights reserved.
// Copyright (c) 2014 mogoweb. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#include "chromeview/native/chromeview_tab.h"

#include "base/android/jni_string.h"
#include "base/logging.h"
#include "chrome/browser/android/chrome_web_contents_delegate_android.h"
#include "chrome/browser/ui/android/window_android_helper.h"
#include "chrome/browser/ui/browser_navigator.h"
#include "chrome/common/net/url_fixer_upper.h"
#include "content/public/browser/android/content_view_core.h"
#include "content/public/browser/web_contents.h"
#include "jni/ChromeViewTab_jni.h"
#include "ui/base/android/window_android.h"
#include "url/gurl.h"

using base::android::ConvertJavaStringToUTF8;
using base::android::ConvertUTF8ToJavaString;
using base::android::ScopedJavaLocalRef;
using chrome::android::ChromeWebContentsDelegateAndroid;
using content::WebContents;
using ui::WindowAndroid;

ChromeViewTab::ChromeViewTab(JNIEnv* env,
                           jobject obj)
    : TabAndroid(env, obj) {
}

ChromeViewTab::~ChromeViewTab() {
}

void ChromeViewTab::Destroy(JNIEnv* env, jobject obj) {
  delete this;
}

void ChromeViewTab::OnReceivedHttpAuthRequest(jobject auth_handler,
                                             const string16& host,
                                             const string16& realm) {
  NOTIMPLEMENTED();
}

void ChromeViewTab::ShowContextMenu(
    const content::ContextMenuParams& params) {
  NOTIMPLEMENTED();
}

void ChromeViewTab::ShowCustomContextMenu(
    const content::ContextMenuParams& params,
    const base::Callback<void(int)>& callback) {
  NOTIMPLEMENTED();
}

void ChromeViewTab::AddShortcutToBookmark(
    const GURL& url, const string16& title, const SkBitmap& skbitmap,
    int r_value, int g_value, int b_value) {
  NOTIMPLEMENTED();
}

void ChromeViewTab::EditBookmark(int64 node_id,
                                const base::string16& node_title,
                                bool is_folder,
                                bool is_partner_bookmark) {
  NOTIMPLEMENTED();
}

bool ChromeViewTab::ShouldWelcomePageLinkToTermsOfService() {
  NOTIMPLEMENTED();
  return false;
}

void ChromeViewTab::OnNewTabPageReady() {
  NOTIMPLEMENTED();
}

void ChromeViewTab::HandlePopupNavigation(chrome::NavigateParams* params) {
  NOTIMPLEMENTED();
}

bool ChromeViewTab::RegisterChromeViewTab(JNIEnv* env) {
  return RegisterNativesImpl(env);
}

ScopedJavaLocalRef<jstring> ChromeViewTab::FixupUrl(JNIEnv* env,
                                                   jobject obj,
                                                   jstring url) {
  GURL fixed_url(URLFixerUpper::FixupURL(ConvertJavaStringToUTF8(env, url),
                                         std::string()));

  std::string fixed_spec;
  if (fixed_url.is_valid())
    fixed_spec = fixed_url.spec();

  return ConvertUTF8ToJavaString(env, fixed_spec);
}

static jint Init(JNIEnv* env, jobject obj) {
  return reinterpret_cast<jint>(new ChromeViewTab(env, obj));
}
