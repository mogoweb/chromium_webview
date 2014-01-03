// Copyright 2013 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

// Populates data fields from Android contacts profile API (i.e. "me" contact).

package org.chromium.components.browser.autofill;

import android.app.Activity;
import android.content.ContentProviderOperation;
import android.content.ContentResolver;
import android.content.Context;
import android.content.OperationApplicationException;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.net.Uri;
import android.os.Bundle;
import android.os.RemoteException;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.provider.ContactsContract.Profile;
import android.provider.ContactsContract;
import android.util.Log;
import android.view.View.OnClickListener;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import org.chromium.base.CalledByNative;
import org.chromium.base.JNINamespace;

import java.util.ArrayList;

/**
 * Loads user profile information stored under the "Me" contact.
 * Requires permissions: READ_CONTACTS and READ_PROFILE.
 */
@JNINamespace("autofill")
public class PersonalAutofillPopulator {
    /**
     * SQL query definitions for obtaining specific profile information.
     */
    private abstract static class ProfileQuery {
        Uri profileDataUri = Uri.withAppendedPath(
                ContactsContract.Profile.CONTENT_URI,
                ContactsContract.Contacts.Data.CONTENT_DIRECTORY
                );
        public abstract String[] projection();
        public abstract String mimeType();
    }

    private static class EmailProfileQuery extends ProfileQuery {
        private static final int EMAIL_ADDRESS = 0;

        @Override
        public String[] projection() {
            return new String[] {
                ContactsContract.CommonDataKinds.Email.ADDRESS,
            };
        }

        @Override
        public String mimeType() {
            return ContactsContract.CommonDataKinds.Email.CONTENT_ITEM_TYPE;
        }
    }

    private static class PhoneProfileQuery extends ProfileQuery {
        private static final int NUMBER = 0;

        @Override
        public String[] projection() {
            return new String[] {
                ContactsContract.CommonDataKinds.Phone.NUMBER,
            };
        }

        @Override
        public String mimeType() {
            return ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE;
        }
    }

    private static class AddressProfileQuery extends ProfileQuery {
        private static final int STREET = 0;
        private static final int POBOX = 1;
        private static final int NEIGHBORHOOD = 2;
        private static final int CITY = 3;
        private static final int REGION = 4;
        private static final int POSTALCODE = 5;
        private static final int COUNTRY = 6;

        @Override
        public String[] projection() {
            return new String[] {
                ContactsContract.CommonDataKinds.StructuredPostal.STREET,
                    ContactsContract.CommonDataKinds.StructuredPostal.POBOX,
                    ContactsContract.CommonDataKinds.StructuredPostal.NEIGHBORHOOD,
                    ContactsContract.CommonDataKinds.StructuredPostal.CITY,
                    ContactsContract.CommonDataKinds.StructuredPostal.REGION,
                    ContactsContract.CommonDataKinds.StructuredPostal.POSTCODE,
                    ContactsContract.CommonDataKinds.StructuredPostal.COUNTRY,
            };
        }

        @Override
        public String mimeType() {
            return ContactsContract.CommonDataKinds.StructuredPostal.CONTENT_ITEM_TYPE;
        }
    }

    private static class NameProfileQuery extends ProfileQuery {
        private static final int GIVEN_NAME = 0;
        private static final int MIDDLE_NAME = 1;
        private static final int FAMILY_NAME = 2;
        private static final int SUFFIX = 3;

        @Override
        public String[] projection() {
            return new String[] {
                ContactsContract.CommonDataKinds.StructuredName.GIVEN_NAME,
                    ContactsContract.CommonDataKinds.StructuredName.MIDDLE_NAME,
                    ContactsContract.CommonDataKinds.StructuredName.FAMILY_NAME,
                    ContactsContract.CommonDataKinds.StructuredName.SUFFIX
            };
        }

        @Override
        public String mimeType() {
            return ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE;
        }
    }

    /**
     * Takes a query object, transforms into actual query and returns cursor.
     * Primary contact values will be first.
     */
    private Cursor cursorFromProfileQuery(ProfileQuery query, ContentResolver contentResolver) {
        String sortDescriptor = ContactsContract.Contacts.Data.IS_PRIMARY + " DESC";
        return contentResolver.query(
                query.profileDataUri,
                query.projection(),
                ContactsContract.Contacts.Data.MIMETYPE + " = ?",
                new String[]{query.mimeType()},
                sortDescriptor
                );
    }
    // Extracted data variables.
    private String[] mEmailAddresses;
    private String mGivenName;
    private String mMiddleName;
    private String mFamilyName;
    private String mSuffix;
    private String mPobox;
    private String mStreet;
    private String mNeighborhood;
    private String mCity;
    private String mRegion;
    private String mCountry;
    private String mPostalCode;
    private String[] mPhoneNumbers;
    private boolean mHasPermissions;

    /**
     * Constructor
     * @param context a valid android context reference
     */
    PersonalAutofillPopulator(Context context) {
        mHasPermissions = hasPermissions(context);
        if (mHasPermissions) {
            ContentResolver contentResolver = context.getContentResolver();
            populateName(contentResolver);
            populateEmail(contentResolver);
            populateAddress(contentResolver);
            populatePhone(contentResolver);
        }
    }

    // Check if the user has granted permissions.
    private boolean hasPermissions(Context context) {
        String [] permissions = {
            "android.permission.READ_CONTACTS",
            "android.permission.READ_PROFILE"
        };
        for (String permission : permissions) {
            int res = context.checkCallingOrSelfPermission(permission);
            if (res != PackageManager.PERMISSION_GRANTED) return false;
        }
        return true;
    }

    // Populating data fields.
    private void populateName(ContentResolver contentResolver) {
        NameProfileQuery nameProfileQuery = new NameProfileQuery();
        Cursor nameCursor = cursorFromProfileQuery(nameProfileQuery, contentResolver);
        if (nameCursor.moveToNext()) {
            mGivenName = nameCursor.getString(nameProfileQuery.GIVEN_NAME);
            mMiddleName = nameCursor.getString(nameProfileQuery.MIDDLE_NAME);
            mFamilyName = nameCursor.getString(nameProfileQuery.FAMILY_NAME);
            mSuffix = nameCursor.getString(nameProfileQuery.SUFFIX);
        }
        nameCursor.close();
    }

    private void populateEmail(ContentResolver contentResolver) {
        EmailProfileQuery emailProfileQuery = new EmailProfileQuery();
        Cursor emailCursor = cursorFromProfileQuery(emailProfileQuery, contentResolver);
        mEmailAddresses = new String[emailCursor.getCount()];
        for (int i = 0; emailCursor.moveToNext(); i++) {
            mEmailAddresses[i] = emailCursor.getString(emailProfileQuery.EMAIL_ADDRESS);
        }
        emailCursor.close();
    }

    private void populateAddress(ContentResolver contentResolver) {
        AddressProfileQuery addressProfileQuery = new AddressProfileQuery();
        Cursor addressCursor = cursorFromProfileQuery(addressProfileQuery, contentResolver);
        if(addressCursor.moveToNext()) {
            mPobox = addressCursor.getString(addressProfileQuery.POBOX);
            mStreet = addressCursor.getString(addressProfileQuery.STREET);
            mNeighborhood = addressCursor.getString(addressProfileQuery.NEIGHBORHOOD);
            mCity = addressCursor.getString(addressProfileQuery.CITY);
            mRegion = addressCursor.getString(addressProfileQuery.REGION);
            mPostalCode = addressCursor.getString(addressProfileQuery.POSTALCODE);
            mCountry = addressCursor.getString(addressProfileQuery.COUNTRY);
        }
        addressCursor.close();
    }

    private void populatePhone(ContentResolver contentResolver) {
        PhoneProfileQuery phoneProfileQuery = new PhoneProfileQuery();
        Cursor phoneCursor = cursorFromProfileQuery(phoneProfileQuery, contentResolver);
        mPhoneNumbers = new String[phoneCursor.getCount()];
        for (int i = 0; phoneCursor.moveToNext(); i++) {
            mPhoneNumbers[i] = phoneCursor.getString(phoneProfileQuery.NUMBER);
        }
        phoneCursor.close();
    }

    /**
     * Static factory method for instance creation.
     * @param context valid Android context.
     * @return PersonalAutofillPopulator new instance of PersonalAutofillPopulator.
     */
    @CalledByNative
    static PersonalAutofillPopulator create(Context context) {
        return new PersonalAutofillPopulator(context);
    }

    @CalledByNative
    private String getFirstName() {
        return mGivenName;
    }

    @CalledByNative
    private String getLastName() {
        return mFamilyName;
    }

    @CalledByNative
    private String getMiddleName() {
        return mMiddleName;
    }

    @CalledByNative
    private String getSuffix() {
        return mSuffix;
    }

    @CalledByNative
    private String[] getEmailAddresses() {
        return mEmailAddresses;
    }

    @CalledByNative
    private String getStreet() {
        return mStreet;
    }

    @CalledByNative
    private String getPobox() {
        return mPobox;
    }

    @CalledByNative
    private String getNeighborhood() {
        return mNeighborhood;
    }

    @CalledByNative
    private String getCity() {
        return mCity;
    }

    @CalledByNative
    private String getRegion() {
        return mRegion;
    }

    @CalledByNative
    private String getPostalCode() {
        return mPostalCode;
    }

    @CalledByNative
    private String getCountry() {
        return mCountry;
    }

    @CalledByNative
    private String[] getPhoneNumbers() {
        return mPhoneNumbers;
    }

    @CalledByNative
    private boolean getHasPermissions() {
        return mHasPermissions;
    }
}
