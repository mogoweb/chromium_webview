//
///*
// * Copyright (C) 2011 The Android Open Source Project
// *
// * Licensed under the Apache License, Version 2.0 (the "License");
// * you may not use this file except in compliance with the License.
// * You may obtain a copy of the License at
// *
// *      http://www.apache.org/licenses/LICENSE-2.0
// *
// * Unless required by applicable law or agreed to in writing, software
// * distributed under the License is distributed on an "AS IS" BASIS,
// * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// * See the License for the specific language governing permissions and
// * limitations under the License.
// */
//
//package com.mogoweb.browser;
//
//import android.content.Context;
//import android.content.SharedPreferences;
//import android.content.SharedPreferences.Editor;
//import android.database.Cursor;
//import android.net.Uri;
//import android.os.AsyncTask;
//import android.os.Message;
//import android.preference.PreferenceManager;
//import android.provider.ContactsContract;
//import android.util.Log;
//import android.webkit.WebSettingsClassic.AutoFillProfile;
//
//import java.util.concurrent.CountDownLatch;
//
//public class AutofillHandler {
//
//    private AutoFillProfile mAutoFillProfile;
//    // Default to zero. In the case no profile is set up, the initial
//    // value will come from the AutoFillSettingsFragment when the user
//    // creates a profile. Otherwise, we'll read the ID of the last used
//    // profile from the prefs db.
//    private int mAutoFillActiveProfileId;
//    private static final int NO_AUTOFILL_PROFILE_SET = 0;
//
//    private CountDownLatch mLoaded = new CountDownLatch(1);
//    private Context mContext;
//
//    private static final String LOGTAG = "AutofillHandler";
//
//    public AutofillHandler(Context context) {
//        mContext = context.getApplicationContext();
//    }
//
//    /**
//     * Load settings from the browser app's database. It is performed in
//     * an AsyncTask as it involves plenty of slow disk IO.
//     * NOTE: Strings used for the preferences must match those specified
//     * in the various preference XML files.
//     */
//    public void asyncLoadFromDb() {
//        // Run the initial settings load in an AsyncTask as it hits the
//        // disk multiple times through SharedPreferences and SQLite. We
//        // need to be certain though that this has completed before we start
//        // to load pages though, so in the worst case we will block waiting
//        // for it to finish in BrowserActivity.onCreate().
//         new LoadFromDb().start();
//    }
//
//    private void waitForLoad() {
//        try {
//            mLoaded.await();
//        } catch (InterruptedException e) {
//            Log.w(LOGTAG, "Caught exception while waiting for AutofillProfile to load.");
//        }
//    }
//
//    private class LoadFromDb extends Thread {
//
//        @Override
//        public void run() {
//            // Note the lack of synchronization over mAutoFillActiveProfileId and
//            // mAutoFillProfile here. This is because we control all other access
//            // to these members through the public functions of this class, and they
//            // all wait for this thread via the mLoaded CountDownLatch.
//
//            SharedPreferences p = PreferenceManager.getDefaultSharedPreferences(mContext);
//
//            // Read the last active AutoFill profile id.
//            mAutoFillActiveProfileId = p.getInt(
//                    PreferenceKeys.PREF_AUTOFILL_ACTIVE_PROFILE_ID,
//                    mAutoFillActiveProfileId);
//
//            // Load the autofill profile data from the database. We use a database separate
//            // to the browser preference DB to make it easier to support multiple profiles
//            // and switching between them. Note that this may block startup if this DB lookup
//            // is extremely slow. We do this to ensure that if there's a profile set, the
//            // user never sees the "setup Autofill" option.
//            AutoFillProfileDatabase autoFillDb = AutoFillProfileDatabase.getInstance(mContext);
//            Cursor c = autoFillDb.getProfile(mAutoFillActiveProfileId);
//
//            if (c.getCount() > 0) {
//                c.moveToFirst();
//
//                String fullName = c.getString(c.getColumnIndex(
//                        AutoFillProfileDatabase.Profiles.FULL_NAME));
//                String email = c.getString(c.getColumnIndex(
//                        AutoFillProfileDatabase.Profiles.EMAIL_ADDRESS));
//                String company = c.getString(c.getColumnIndex(
//                        AutoFillProfileDatabase.Profiles.COMPANY_NAME));
//                String addressLine1 = c.getString(c.getColumnIndex(
//                        AutoFillProfileDatabase.Profiles.ADDRESS_LINE_1));
//                String addressLine2 = c.getString(c.getColumnIndex(
//                        AutoFillProfileDatabase.Profiles.ADDRESS_LINE_2));
//                String city = c.getString(c.getColumnIndex(
//                        AutoFillProfileDatabase.Profiles.CITY));
//                String state = c.getString(c.getColumnIndex(
//                        AutoFillProfileDatabase.Profiles.STATE));
//                String zip = c.getString(c.getColumnIndex(
//                        AutoFillProfileDatabase.Profiles.ZIP_CODE));
//                String country = c.getString(c.getColumnIndex(
//                        AutoFillProfileDatabase.Profiles.COUNTRY));
//                String phone = c.getString(c.getColumnIndex(
//                        AutoFillProfileDatabase.Profiles.PHONE_NUMBER));
//                mAutoFillProfile = new AutoFillProfile(mAutoFillActiveProfileId,
//                        fullName, email, company, addressLine1, addressLine2, city,
//                        state, zip, country, phone);
//            }
//            c.close();
//            autoFillDb.close();
//
//            // At this point we've loaded the profile if there was one, so let any thread
//            // waiting on initialization continue.
//            mLoaded.countDown();
//
//            // Synchronization note: strictly speaking, it's possible that mAutoFillProfile
//            // may get a value after we check below, but that's OK. This check is only an
//            // optimisation, and we do a proper synchronized check further down when it comes
//            // to actually setting the inferred profile.
//            if (mAutoFillProfile == null) {
//                // We did not load a profile from disk. Try to infer one from the user's
//                // "me" contact.
//                final Uri profileUri = Uri.withAppendedPath(ContactsContract.Profile.CONTENT_URI,
//                        ContactsContract.Contacts.Data.CONTENT_DIRECTORY);
//                String name = getContactField(profileUri,
//                        ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME,
//                        ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE);
//                // Only attempt to read other data and set a profile if we could successfully
//                // get a name.
//                if (name != null) {
//                    String email = getContactField(profileUri,
//                            ContactsContract.CommonDataKinds.Email.ADDRESS,
//                            ContactsContract.CommonDataKinds.Email.CONTENT_ITEM_TYPE);
//                    String phone = getContactField(profileUri,
//                            ContactsContract.CommonDataKinds.Phone.NUMBER,
//                            ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE);
//                    String company = getContactField(profileUri,
//                            ContactsContract.CommonDataKinds.Organization.COMPANY,
//                            ContactsContract.CommonDataKinds.Organization.CONTENT_ITEM_TYPE);
//
//                    // Can't easily get structured postal address information (even using
//                    // CommonDataKinds.StructuredPostal) so omit prepopulating that for now.
//                    // When querying structured postal data, it often all comes back as a string
//                    // inside the "street" field.
//
//                    synchronized(AutofillHandler.this) {
//                        // Only use this profile if one hasn't been set inbetween the
//                        // inital import and this thread getting to this point.
//                        if (mAutoFillProfile == null) {
//                            setAutoFillProfile(new AutoFillProfile(1, name, email, company,
//                                    null, null, null, null, null, null, phone), null);
//                        }
//                    }
//                }
//            }
//        }
//
//        private String getContactField(Uri uri, String field, String itemType) {
//            String result = null;
//
//            Cursor c = mContext.getContentResolver().query(uri, new String[] { field },
//                    ContactsContract.Data.MIMETYPE + "=?", new String[] { itemType }, null);
//
//            if (c == null) {
//                return null;
//            }
//
//            try {
//                // Just use the first returned value if we get more than one.
//                if (c.moveToFirst()) {
//                    result = c.getString(0);
//                }
//            } finally {
//                c.close();
//            }
//            return result;
//        }
//    }
//
//    public synchronized void setAutoFillProfile(AutoFillProfile profile, Message msg) {
//        waitForLoad();
//        int profileId = NO_AUTOFILL_PROFILE_SET;
//        if (profile != null) {
//            profileId = profile.getUniqueId();
//            // Update the AutoFill DB with the new profile.
//            new SaveProfileToDbTask(msg).execute(profile);
//        } else {
//            // Delete the current profile.
//            if (mAutoFillProfile != null) {
//                new DeleteProfileFromDbTask(msg).execute(mAutoFillProfile.getUniqueId());
//            }
//        }
//        // Make sure we set mAutoFillProfile before calling setActiveAutoFillProfileId
//        // Calling setActiveAutoFillProfileId will trigger an update of WebViews
//        // which will expect a new profile to be set
//        mAutoFillProfile = profile;
//        setActiveAutoFillProfileId(profileId);
//    }
//
//    public synchronized AutoFillProfile getAutoFillProfile() {
//        waitForLoad();
//        return mAutoFillProfile;
//    }
//
//    private synchronized void setActiveAutoFillProfileId(int activeProfileId) {
//        mAutoFillActiveProfileId = activeProfileId;
//        Editor ed = PreferenceManager.
//            getDefaultSharedPreferences(mContext).edit();
//        ed.putInt(PreferenceKeys.PREF_AUTOFILL_ACTIVE_PROFILE_ID, activeProfileId);
//        ed.apply();
//    }
//
//    private abstract class AutoFillProfileDbTask<T> extends AsyncTask<T, Void, Void> {
//        AutoFillProfileDatabase mAutoFillProfileDb;
//        Message mCompleteMessage;
//
//        public AutoFillProfileDbTask(Message msg) {
//            mCompleteMessage = msg;
//        }
//
//        @Override
//        protected void onPostExecute(Void result) {
//            if (mCompleteMessage != null) {
//                mCompleteMessage.sendToTarget();
//            }
//            mAutoFillProfileDb.close();
//        }
//
//        @Override
//        abstract protected Void doInBackground(T... values);
//    }
//
//
//    private class SaveProfileToDbTask extends AutoFillProfileDbTask<AutoFillProfile> {
//        public SaveProfileToDbTask(Message msg) {
//            super(msg);
//        }
//
//        @Override
//        protected Void doInBackground(AutoFillProfile... values) {
//            mAutoFillProfileDb = AutoFillProfileDatabase.getInstance(mContext);
//            synchronized (AutofillHandler.this) {
//                assert mAutoFillActiveProfileId != NO_AUTOFILL_PROFILE_SET;
//                AutoFillProfile newProfile = values[0];
//                mAutoFillProfileDb.addOrUpdateProfile(mAutoFillActiveProfileId, newProfile);
//            }
//            return null;
//        }
//    }
//
//    private class DeleteProfileFromDbTask extends AutoFillProfileDbTask<Integer> {
//        public DeleteProfileFromDbTask(Message msg) {
//            super(msg);
//        }
//
//        @Override
//        protected Void doInBackground(Integer... values) {
//            mAutoFillProfileDb = AutoFillProfileDatabase.getInstance(mContext);
//            int id = values[0];
//            assert  id > 0;
//            mAutoFillProfileDb.dropProfile(id);
//            return null;
//        }
//    }
//}
