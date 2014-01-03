// Copyright (c) 2012 The Chromium Authors. All rights reserved.
// Copyright (c) 2014 mogoweb. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#ifndef CHROMEVIEW_NATIVE_CHROMEVIEW_GOOGLE_LOCATION_SETTINGS_HELPER_H_
#define CHROMEVIEW_NATIVE_CHROMEVIEW_GOOGLE_LOCATION_SETTINGS_HELPER_H_

#include "chrome/browser/android/google_location_settings_helper.h"

// Stub implementation of GoogleLocationSettingsHelper for chromeview.
class ChromeViewGoogleLocationSettingsHelper
    : public GoogleLocationSettingsHelper {
 public:
  // GoogleLocationSettingsHelper implementation:
  virtual std::string GetAcceptButtonLabel() OVERRIDE;
  virtual void ShowGoogleLocationSettings() OVERRIDE;
  virtual bool IsMasterLocationSettingEnabled() OVERRIDE;
  virtual bool IsGoogleAppsLocationSettingEnabled() OVERRIDE;

 protected:
  ChromeViewGoogleLocationSettingsHelper();
  virtual ~ChromeViewGoogleLocationSettingsHelper();

 private:
  friend class GoogleLocationSettingsHelper;

  DISALLOW_COPY_AND_ASSIGN(ChromeViewGoogleLocationSettingsHelper);
};

#endif  // CHROMEVIEW_NATIVE_CHROMEVIEW_GOOGLE_LOCATION_SETTINGS_HELPER_H_
