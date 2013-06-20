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
//package com.mogoweb.browser;
//
//import android.content.Context;
//import android.util.AttributeSet;
//import android.view.ContextThemeWrapper;
//import android.view.View;
//import android.view.View.OnClickListener;
//import android.widget.ArrayAdapter;
//import android.widget.Button;
//import android.widget.LinearLayout;
//import android.widget.ProgressBar;
//import android.widget.Spinner;
//import android.widget.TextView;
//
//import com.mogoweb.browser.DeviceAccountLogin.AutoLoginCallback;
//
//public class AutologinBar extends LinearLayout implements OnClickListener,
//        AutoLoginCallback {
//
//    protected Spinner mAutoLoginAccount;
//    protected Button mAutoLoginLogin;
//    protected ProgressBar mAutoLoginProgress;
//    protected TextView mAutoLoginError;
//    protected View mAutoLoginCancel;
//    protected DeviceAccountLogin mAutoLoginHandler;
//    protected ArrayAdapter<String> mAccountsAdapter;
//    protected TitleBar mTitleBar;
//
//    public AutologinBar(Context context) {
//        super(context);
//    }
//
//    public AutologinBar(Context context, AttributeSet attrs) {
//        super(context, attrs);
//    }
//
//    public AutologinBar(Context context, AttributeSet attrs, int defStyle) {
//        super(context, attrs, defStyle);
//    }
//
//    @Override
//    protected void onFinishInflate() {
//        super.onFinishInflate();
//        mAutoLoginAccount = (Spinner) findViewById(R.id.autologin_account);
//        mAutoLoginLogin = (Button) findViewById(R.id.autologin_login);
//        mAutoLoginLogin.setOnClickListener(this);
//        mAutoLoginProgress = (ProgressBar) findViewById(R.id.autologin_progress);
//        mAutoLoginError = (TextView) findViewById(R.id.autologin_error);
//        mAutoLoginCancel = findViewById(R.id.autologin_close);
//        mAutoLoginCancel.setOnClickListener(this);
//    }
//
//    public void setTitleBar(TitleBar titleBar) {
//        mTitleBar = titleBar;
//    }
//
//    @Override
//    public void onClick(View v) {
//        if (mAutoLoginCancel == v) {
//            if (mAutoLoginHandler != null) {
//                mAutoLoginHandler.cancel();
//                mAutoLoginHandler = null;
//            }
//            hideAutoLogin(true);
//        } else if (mAutoLoginLogin == v) {
//            if (mAutoLoginHandler != null) {
//                mAutoLoginAccount.setEnabled(false);
//                mAutoLoginLogin.setEnabled(false);
//                mAutoLoginProgress.setVisibility(View.VISIBLE);
//                mAutoLoginError.setVisibility(View.GONE);
//                mAutoLoginHandler.login(
//                        mAutoLoginAccount.getSelectedItemPosition(), this);
//            }
//        }
//    }
//
//    public void updateAutoLogin(Tab tab, boolean animate) {
//        DeviceAccountLogin login = tab.getDeviceAccountLogin();
//        if (login != null) {
//            mAutoLoginHandler = login;
//            ContextThemeWrapper wrapper = new ContextThemeWrapper(mContext,
//                    android.R.style.Theme_Holo_Light);
//            mAccountsAdapter = new ArrayAdapter<String>(wrapper,
//                    android.R.layout.simple_spinner_item, login.getAccountNames());
//            mAccountsAdapter.setDropDownViewResource(
//                    android.R.layout.simple_spinner_dropdown_item);
//            mAutoLoginAccount.setAdapter(mAccountsAdapter);
//            mAutoLoginAccount.setSelection(0);
//            mAutoLoginAccount.setEnabled(true);
//            mAutoLoginLogin.setEnabled(true);
//            mAutoLoginProgress.setVisibility(View.INVISIBLE);
//            mAutoLoginError.setVisibility(View.GONE);
//            switch (login.getState()) {
//                case DeviceAccountLogin.PROCESSING:
//                    mAutoLoginAccount.setEnabled(false);
//                    mAutoLoginLogin.setEnabled(false);
//                    mAutoLoginProgress.setVisibility(View.VISIBLE);
//                    break;
//                case DeviceAccountLogin.FAILED:
//                    mAutoLoginProgress.setVisibility(View.INVISIBLE);
//                    mAutoLoginError.setVisibility(View.VISIBLE);
//                    break;
//                case DeviceAccountLogin.INITIAL:
//                    break;
//                default:
//                    throw new IllegalStateException();
//            }
//            showAutoLogin(animate);
//        } else {
//            hideAutoLogin(animate);
//        }
//    }
//
//    void showAutoLogin(boolean animate) {
//        mTitleBar.showAutoLogin(animate);
//    }
//
//    void hideAutoLogin(boolean animate) {
//        mTitleBar.hideAutoLogin(animate);
//    }
//
//    @Override
//    public void loginFailed() {
//        mAutoLoginAccount.setEnabled(true);
//        mAutoLoginLogin.setEnabled(true);
//        mAutoLoginProgress.setVisibility(View.INVISIBLE);
//        mAutoLoginError.setVisibility(View.VISIBLE);
//    }
//
//}
