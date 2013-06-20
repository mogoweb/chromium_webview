///*
// * Copyright (C) 201 The Android Open Source Project
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
//import android.os.AsyncTask;
//import android.security.KeyChain;
//import android.security.KeyChainException;
//import android.webkit.ClientCertRequestHandler;
//import java.security.PrivateKey;
//import java.security.cert.X509Certificate;
//
//final class KeyChainLookup extends AsyncTask<Void, Void, Void> {
//    private final Context mContext;
//    private final ClientCertRequestHandler mHandler;
//    private final String mAlias;
//    KeyChainLookup(Context context, ClientCertRequestHandler handler, String alias) {
//        mContext = context.getApplicationContext();
//        mHandler = handler;
//        mAlias = alias;
//    }
//    @Override protected Void doInBackground(Void... params) {
//        PrivateKey privateKey;
//        X509Certificate[] certificateChain;
//        try {
//            privateKey = KeyChain.getPrivateKey(mContext, mAlias);
//            certificateChain = KeyChain.getCertificateChain(mContext, mAlias);
//        } catch (InterruptedException e) {
//            mHandler.ignore();
//            return null;
//        } catch (KeyChainException e) {
//            mHandler.ignore();
//            return null;
//        }
//        mHandler.proceed(privateKey, certificateChain);
//        return null;
//    }
//}
