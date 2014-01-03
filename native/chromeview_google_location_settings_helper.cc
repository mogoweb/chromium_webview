// Copyright (c) 2012 The Chromium Authors. All rights reserved.
// Copyright (c) 2014 mogoweb. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#include "chromeview/native/chromeview_google_location_settings_helper.h"

// Factory function
GoogleLocationSettingsHelper* GoogleLocationSettingsHelper::Create() {
  return new ChromeViewGoogleLocationSettingsHelper();
}

ChromeViewGoogleLocationSettingsHelper::ChromeViewGoogleLocationSettingsHelper()
    : GoogleLocationSettingsHelper() {
}

ChromeViewGoogleLocationSettingsHelper::
    ~ChromeViewGoogleLocationSettingsHelper() {
}

std::string ChromeViewGoogleLocationSettingsHelper::GetAcceptButtonLabel() {
  return "Allow";
}

void ChromeViewGoogleLocationSettingsHelper::ShowGoogleLocationSettings() {
}

bool ChromeViewGoogleLocationSettingsHelper::
    IsGoogleAppsLocationSettingEnabled() {
  return true;
}

bool ChromeViewGoogleLocationSettingsHelper::IsMasterLocationSettingEnabled() {
  return true;
}
