// Copyright 2013 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.sync.internal_api.pub;

import android.os.Parcel;
import android.os.Parcelable;

/**
 * This enum describes the type of passphrase required, if any, to decrypt synced data.
 *
 * It implements the Android {@link Parcelable} interface so it is easy to pass around in intents.
 *
 * It maps the native enum syncer::PassphraseType, but has the additional values INVALID and NONE.
 */
public enum SyncDecryptionPassphraseType implements Parcelable {
    INVALID(-2),                   // Used as default value and is not a valid decryption type.
    NONE(-1),                      // No encryption (deprecated).
    IMPLICIT_PASSPHRASE(0),        // GAIA-based passphrase (deprecated).
    KEYSTORE_PASSPHRASE(1),        // Keystore passphrase.
    FROZEN_IMPLICIT_PASSPHRASE(2), // Frozen GAIA passphrase.
    CUSTOM_PASSPHRASE(3);          // User-provided passphrase.

    public static Parcelable.Creator CREATOR =
            new Parcelable.Creator<SyncDecryptionPassphraseType>() {
        @Override
        public SyncDecryptionPassphraseType createFromParcel(Parcel parcel) {
            return fromInternalValue(parcel.readInt());
        }

        @Override
        public SyncDecryptionPassphraseType[] newArray(int size) {
            return new SyncDecryptionPassphraseType[size];
        }
    };

    public static SyncDecryptionPassphraseType fromInternalValue(int value) {
        for (SyncDecryptionPassphraseType type : values()) {
            if (type.internalValue() == value) {
                return type;
            }
        }
        // Falling back to INVALID. Should not happen if |value| was retrieved from native.
        return INVALID;
    }

    private final int mNativeValue;

    private SyncDecryptionPassphraseType(int nativeValue) {
        mNativeValue = nativeValue;
    }

    public int internalValue() {
        // Since the values in this enums are constant and very small, this cast is safe.
        return mNativeValue;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(mNativeValue);
    }
}
