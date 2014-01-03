// Copyright (c) 2012 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.text.Html;
import android.text.TextUtils;
import android.util.Patterns;

import org.chromium.base.CalledByNative;

import java.io.File;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Helper for issuing intents to the android framework.
 */
public abstract class IntentHelper {

    private IntentHelper() {}

    /**
     * Triggers a send email intent.  If no application has registered to receive these intents,
     * this will fail silently.
     *
     * @param context The context for issuing the intent.
     * @param email The email address to send to.
     * @param subject The subject of the email.
     * @param body The body of the email.
     * @param chooserTitle The title of the activity chooser.
     * @param fileToAttach The file name of the attachment.
     */
    @CalledByNative
    static void sendEmail(Context context, String email, String subject, String body,
            String chooserTitle, String fileToAttach) {
        Set<String> possibleEmails = new HashSet<String>();

        if (!TextUtils.isEmpty(email)) {
            possibleEmails.add(email);
        } else {
            Pattern emailPattern = Patterns.EMAIL_ADDRESS;
            Account[] accounts = AccountManager.get(context).getAccounts();
            for (Account account : accounts) {
                if (emailPattern.matcher(account.name).matches()) {
                    possibleEmails.add(account.name);
                }
            }
        }

        Intent send = new Intent(Intent.ACTION_SEND);
        send.setType("message/rfc822");
        if (possibleEmails.size() != 0) {
            send.putExtra(Intent.EXTRA_EMAIL,
                    possibleEmails.toArray(new String[possibleEmails.size()]));
        }
        send.putExtra(Intent.EXTRA_SUBJECT, subject);
        send.putExtra(Intent.EXTRA_TEXT, Html.fromHtml(body));
        if (!TextUtils.isEmpty(fileToAttach)) {
            File fileIn = new File(fileToAttach);
            send.putExtra(Intent.EXTRA_STREAM, Uri.fromFile(fileIn));
        }

        try {
            Intent chooser = Intent.createChooser(send, chooserTitle);
            // we start this activity outside the main activity.
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(chooser);
        } catch (android.content.ActivityNotFoundException ex) {
            // If no app handles it, do nothing.
        }
    }
}
