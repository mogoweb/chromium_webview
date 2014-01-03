// Copyright 2013 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.autofill;

import org.chromium.base.CalledByNative;
import org.chromium.base.JNINamespace;

/**
 * Java-side result of a non-cancelled AutofillDialog invocation, and
 * JNI glue for C++ AutofillDialogResult used by AutofillDialogControllerAndroid.
 */
@JNINamespace("autofill")
public class AutofillDialogResult {
    /**
     * Information about the credit card in the dialog result.
     */
    public static class ResultCard {
        private final int mExpirationMonth;
        private final int mExpirationYear;
        private final String mPan;
        private final String mCvn;

        /**
         * Creates a ResultCard.
         * @param expirationMonth Expiration month
         * @param expirationYear Expiration year
         * @param pan Credit card number
         * @param cvn Credit card verification number
         */
        public ResultCard(int expirationMonth, int expirationYear, String pan, String cvn) {
            mExpirationMonth = expirationMonth;
            mExpirationYear = expirationYear;
            mPan = pan;
            mCvn = cvn;
        }

        /**
         * @return Expiration month
         */
        @CalledByNative("ResultCard")
        public int getExpirationMonth() {
            return mExpirationMonth;
        }

        /**
         * @return Expiration year
         */
        @CalledByNative("ResultCard")
        public int getExpirationYear() {
            return mExpirationYear;
        }

        /**
         * @return Credit card number
         */
        @CalledByNative("ResultCard")
        public String getPan() {
            return mPan;
        }

        /**
         * @return Credit card verification number
         */
        @CalledByNative("ResultCard")
        public String getCvn() {
            return mCvn;
        }
    }

    /**
     * Information about an address in the dialog result.
     */
    public static class ResultAddress {
        private final String mName;
        private final String mPhoneNumber;
        private final String mAddress1;
        private final String mAddress2;
        private final String mCity;
        private final String mState;
        private final String mPostalCode;
        private final String mCountryCode;

        /**
         * Creates a ResultAddress.
         * Any parameter can be empty or null.
         * @param name Full name
         * @param phoneNumber Phone number
         * @param address1 Address line 1
         * @param address2 Address line 2
         * @param city City
         * @param state State
         * @param postalCode Postal code
         * @param countryCode Country code
         */
        public ResultAddress(
                String name, String phoneNumber,
                String address1, String address2,
                String city, String state, String postalCode,
                String countryCode) {
            mName = name;
            mPhoneNumber = phoneNumber;
            mAddress1 = address1;
            mAddress2 = address2;
            mCity = city;
            mState = state;
            mPostalCode = postalCode;
            mCountryCode = countryCode;
        }

        /**
         * @return Full name
         */
        @CalledByNative("ResultAddress")
        public String getName() {
            return mName;
        }

        /**
         * @return Phone number
         */
        @CalledByNative("ResultAddress")
        public String getPhoneNumber() {
            return mPhoneNumber;
        }

        /**
         * @return Address line 1
         */
        @CalledByNative("ResultAddress")
        public String getAddress1() {
            return mAddress1;
        }

        /**
         * @return Address line 2
         */
        @CalledByNative("ResultAddress")
        public String getAddress2() {
            return mAddress2;
        }

        /**
         * @return City
         */
        @CalledByNative("ResultAddress")
        public String getCity() {
            return mCity;
        }

        /**
         * @return State
         */
        @CalledByNative("ResultAddress")
        public String getState() {
            return mState;
        }

        /**
         * @return Postal code
         */
        @CalledByNative("ResultAddress")
        public String getPostalCode() {
            return mPostalCode;
        }

        /**
         * @return Country code
         */
        @CalledByNative("ResultAddress")
        public String getCountryCode() {
            return mCountryCode;
        }
    }

    /**
     * A response from the dialog.
     */
    public static class ResultWallet {
        private final String mEmail;
        private final String mGoogleTransactionId;
        private final ResultCard mCard;
        private final ResultAddress mBillingAddress;
        private final ResultAddress mShippingAddress;

        /**
         * Creates a ResultWallet.
         * Any fields could be empty or null.
         * @param email Email address
         * @param googleTransactionId Google transaction ID if any
         * @param card Information about the credit card
         * @param billingAddress Information about the billing address
         * @param shippingAddress Information about the shipping address
         */
        public ResultWallet(
                String email, String googleTransactionId,
                ResultCard card, ResultAddress billingAddress, ResultAddress shippingAddress) {
            mEmail = email;
            mGoogleTransactionId = googleTransactionId;
            mCard = card;
            mBillingAddress = billingAddress;
            mShippingAddress = shippingAddress;
        }

        /**
         * @return Email address
         */
        @CalledByNative("ResultWallet")
        public String getEmail() {
            return mEmail;
        }

        /**
         * @return Google transaction ID if any
         */
        @CalledByNative("ResultWallet")
        public String getGoogleTransactionId() {
            return mGoogleTransactionId;
        }

        /**
         * @return Credit card information, or null
         */
        @CalledByNative("ResultWallet")
        public ResultCard getCard() {
            return mCard;
        }

        /**
         * @return Billing address information, or null
         */
        @CalledByNative("ResultWallet")
        public ResultAddress getBillingAddress() {
            return mBillingAddress;
        }

        /**
         * @return Shipping address information, or null
         */
        @CalledByNative("ResultWallet")
        public ResultAddress getShippingAddress() {
            return mShippingAddress;
        }
    }
}
