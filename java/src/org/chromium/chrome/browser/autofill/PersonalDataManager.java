// Copyright (c) 2013 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.autofill;

import org.chromium.base.CalledByNative;
import org.chromium.base.JNINamespace;
import org.chromium.base.ThreadUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * Android wrapper of the PersonalDataManager which provides access from the Java
 * layer.
 *
 * Only usable from the UI thread as it's primary purpose is for supporting the Android
 * preferences UI.
 *
 * See chrome/browser/autofill/personal_data_manager.h for more details.
 */
@JNINamespace("autofill")
public class PersonalDataManager {

    public interface PersonalDataManagerObserver {
        public abstract void onPersonalDataChanged();
    }

    public static class AutofillProfile {
        private String mGUID;
        private String mOrigin;
        private String mFullName;
        private String mCompanyName;
        private String mAddressLine1;
        private String mAddressLine2;
        private String mCity;
        private String mState;
        private String mZip;
        private String mCountry;
        private String mPhoneNumber;
        private String mEmailAddress;

        @CalledByNative("AutofillProfile")
        public static AutofillProfile create(String guid, String origin, String fullName,
                String companyName, String addressLine1, String addressLine2, String city,
                String state, String zip, String country, String phoneNumber, String emailAddress) {
            return new AutofillProfile(guid, origin, fullName, companyName, addressLine1,
                    addressLine2, city, state, zip, country, phoneNumber, emailAddress);
        }

        public AutofillProfile(String guid, String origin, String fullName, String companyName,
                String addressLine1, String addressLine2, String city, String state,
                String zip, String country, String phoneNumber, String emailAddress) {
            mGUID = guid;
            mOrigin = origin;
            mFullName = fullName;
            mCompanyName = companyName;
            mAddressLine1 = addressLine1;
            mAddressLine2 = addressLine2;
            mCity = city;
            mState = state;
            mZip = zip;
            mCountry = country;
            mPhoneNumber = phoneNumber;
            mEmailAddress = emailAddress;
        }

        @CalledByNative("AutofillProfile")
        public String getGUID() {
            return mGUID;
        }

        @CalledByNative("AutofillProfile")
        public String getOrigin() {
            return mOrigin;
        }

        @CalledByNative("AutofillProfile")
        public String getFullName() {
            return mFullName;
        }

        @CalledByNative("AutofillProfile")
        public String getCompanyName() {
            return mCompanyName;
        }

        @CalledByNative("AutofillProfile")
        public String getAddressLine1() {
            return mAddressLine1;
        }

        @CalledByNative("AutofillProfile")
        public String getAddressLine2() {
            return mAddressLine2;
        }

        @CalledByNative("AutofillProfile")
        public String getCity() {
            return mCity;
        }

        @CalledByNative("AutofillProfile")
        public String getState() {
            return mState;
        }

        @CalledByNative("AutofillProfile")
        public String getZip() {
            return mZip;
        }

        @CalledByNative("AutofillProfile")
        public String getCountry() {
            return mCountry;
        }

        public String getCountryCode() {
            return nativeToCountryCode(mCountry);
        }

        @CalledByNative("AutofillProfile")
        public String getPhoneNumber() {
            return mPhoneNumber;
        }

        @CalledByNative("AutofillProfile")
        public String getEmailAddress() {
            return mEmailAddress;
        }

        public void setGUID(String guid) {
            mGUID = guid;
        }

        public void setOrigin(String origin) {
            mOrigin = origin;
        }

        public void setFullName(String fullName) {
            mFullName = fullName;
        }

        public void setCompanyName(String companyName) {
            mCompanyName = companyName;
        }

        public void setAddressLine1(String addressLine1) {
            mAddressLine1 = addressLine1;
        }

        public void setAddressLine2(String addressLine2) {
            mAddressLine2 = addressLine2;
        }

        public void setCity(String city) {
            mCity = city;
        }

        public void setState(String state) {
            mState = state;
        }

        public void setZip(String zip) {
            mZip = zip;
        }

        public void setCountry(String country) {
            mCountry = country;
        }

        public void setPhoneNumber(String phoneNumber) {
            mPhoneNumber = phoneNumber;
        }

        public void setEmailAddress(String emailAddress) {
            mEmailAddress = emailAddress;
        }
    }

    public static class CreditCard {
        // Note that while some of these fields are numbers, they're predominantly read,
        // marshaled and compared as strings. To save conversions, we use strings everywhere.
        private String mGUID;
        private String mOrigin;
        private String mName;
        private String mNumber;
        private String mObfuscatedNumber;
        private String mMonth;
        private String mYear;

        @CalledByNative("CreditCard")
        public static CreditCard create(String guid, String origin, String name, String number,
                String obfuscatedNumber, String month, String year) {
            return new CreditCard(guid, origin, name, number, obfuscatedNumber, month, year);
        }

        public CreditCard(String guid, String origin, String name, String number,
                String obfuscatedNumber, String month, String year) {
            mGUID = guid;
            mOrigin = origin;
            mName = name;
            mNumber = number;
            mObfuscatedNumber = obfuscatedNumber;
            mMonth = month;
            mYear = year;
        }

        @CalledByNative("CreditCard")
        public String getGUID() {
            return mGUID;
        }

        @CalledByNative("CreditCard")
        public String getOrigin() {
            return mOrigin;
        }

        @CalledByNative("CreditCard")
        public String getName() {
            return mName;
        }

        @CalledByNative("CreditCard")
        public String getNumber() {
            return mNumber;
        }

        public String getObfuscatedNumber() {
            return mObfuscatedNumber;
        }

        @CalledByNative("CreditCard")
        public String getMonth() {
            return mMonth;
        }

        @CalledByNative("CreditCard")
        public String getYear() {
            return mYear;
        }

        public void setGUID(String guid) {
            mGUID = guid;
        }

        public void setOrigin(String origin) {
            mOrigin = origin;
        }

        public void setName(String name) {
            mName = name;
        }

        public void setNumber(String number) {
            mNumber = number;
        }

        public void setObfuscatedNumber(String obfuscatedNumber) {
            mObfuscatedNumber = obfuscatedNumber;
        }

        public void setMonth(String month) {
            mMonth = month;
        }

        public void setYear(String year) {
            mYear = year;
        }
    }

    private static PersonalDataManager sManager;

    public static PersonalDataManager getInstance() {
        ThreadUtils.assertOnUiThread();
        if (sManager == null) {
            sManager = new PersonalDataManager();
        }
        return sManager;
    }

    private final int mPersonalDataManagerAndroid;
    private final List<PersonalDataManagerObserver> mDataObservers =
            new ArrayList<PersonalDataManagerObserver>();

    private PersonalDataManager() {
        // Note that this technically leaks the native object, however, PersonalDataManager
        // is a singleton that lives forever and there's no clean shutdown of Chrome on Android
        mPersonalDataManagerAndroid = nativeInit();
    }

    /**
     * Called from native when template URL service is done loading.
     */
    @CalledByNative
    private void personalDataChanged() {
        ThreadUtils.assertOnUiThread();
        for (PersonalDataManagerObserver observer : mDataObservers) {
            observer.onPersonalDataChanged();
        }
    }

    /**
     * Registers a PersonalDataManagerObserver on the native side.
     */
    public void registerDataObserver(PersonalDataManagerObserver observer) {
        ThreadUtils.assertOnUiThread();
        assert !mDataObservers.contains(observer);
        mDataObservers.add(observer);
    }

    /**
     * Unregisters the provided observer.
     */
    public void unregisterDataObserver(PersonalDataManagerObserver observer) {
        ThreadUtils.assertOnUiThread();
        assert (mDataObservers.size() > 0);
        assert (mDataObservers.contains(observer));
        mDataObservers.remove(observer);
    }

    public List<AutofillProfile> getProfiles() {
        ThreadUtils.assertOnUiThread();
        int profileCount = nativeGetProfileCount(mPersonalDataManagerAndroid);
        List<AutofillProfile> profiles = new ArrayList<AutofillProfile>(profileCount);
        for (int i = 0; i < profileCount; i++) {
            profiles.add(nativeGetProfileByIndex(mPersonalDataManagerAndroid, i));
        }
        return profiles;
    }

    public AutofillProfile getProfile(String guid) {
        ThreadUtils.assertOnUiThread();
        return nativeGetProfileByGUID(mPersonalDataManagerAndroid, guid);
    }

    public void deleteProfile(String guid) {
        ThreadUtils.assertOnUiThread();
        nativeRemoveByGUID(mPersonalDataManagerAndroid, guid);
    }

    public String setProfile(AutofillProfile profile) {
        ThreadUtils.assertOnUiThread();
        return nativeSetProfile(mPersonalDataManagerAndroid, profile);
    }

    public List<CreditCard> getCreditCards() {
        ThreadUtils.assertOnUiThread();
        int count = nativeGetCreditCardCount(mPersonalDataManagerAndroid);
        List<CreditCard> cards = new ArrayList<CreditCard>(count);
        for (int i = 0; i < count; i++) {
            cards.add(nativeGetCreditCardByIndex(mPersonalDataManagerAndroid, i));
        }
        return cards;
    }

    public CreditCard getCreditCard(String guid) {
        ThreadUtils.assertOnUiThread();
        return nativeGetCreditCardByGUID(mPersonalDataManagerAndroid, guid);
    }

    public String setCreditCard(CreditCard card) {
        ThreadUtils.assertOnUiThread();
        return nativeSetCreditCard(mPersonalDataManagerAndroid, card);
    }

    public void deleteCreditCard(String guid) {
        ThreadUtils.assertOnUiThread();
        nativeRemoveByGUID(mPersonalDataManagerAndroid, guid);
    }

    /**
     * @return Whether the Autofill feature is enabled.
     */
    public static boolean isAutofillEnabled() {
        return nativeIsAutofillEnabled();
    }

    /**
     * Enables or disables the Autofill feature.
     * @param enable True to disable Autofill, false otherwise.
     */
    public static void setAutofillEnabled(boolean enable) {
        nativeSetAutofillEnabled(enable);
    }

    /**
     * @return Whether the Autofill feature is managed.
     */
    public static boolean isAutofillManaged() {
        return nativeIsAutofillManaged();
    }

    private native int nativeInit();
    private native int nativeGetProfileCount(int nativePersonalDataManagerAndroid);
    private native AutofillProfile nativeGetProfileByIndex(int nativePersonalDataManagerAndroid,
            int index);
    private native AutofillProfile nativeGetProfileByGUID(int nativePersonalDataManagerAndroid,
            String guid);
    private native String nativeSetProfile(int nativePersonalDataManagerAndroid,
            AutofillProfile profile);
    private native int nativeGetCreditCardCount(int nativePersonalDataManagerAndroid);
    private native CreditCard nativeGetCreditCardByIndex(int nativePersonalDataManagerAndroid,
            int index);
    private native CreditCard nativeGetCreditCardByGUID(int nativePersonalDataManagerAndroid,
            String guid);
    private native String nativeSetCreditCard(int nativePersonalDataManagerAndroid,
            CreditCard card);
    private native void nativeRemoveByGUID(int nativePersonalDataManagerAndroid, String guid);
    private static native boolean nativeIsAutofillEnabled();
    private static native void nativeSetAutofillEnabled(boolean enable);
    private static native boolean nativeIsAutofillManaged();
    private static native String nativeToCountryCode(String countryName);
}
