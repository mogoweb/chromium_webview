ChromiumWebView
================

Android WebView wrapper based on chromium

## Notice

This is just a experimental project, don't use it in product.

## Why you need

The current performance of Android webview is so poor. ChromiumWebView gives your 
application early access to the newest features in Chromium, and removes the
variability due to different WebView implementations in different versions of
Android.

Inspired by [ChromeView project](https://github.com/pwnall/chromeview), our 
goal is to provide full Android WebKit compatible API, so the web apps or 
hybrid apps can easily immigrate to chromium. 

You can use ChromiumWebView as the same as Android WebView except for different
package name.

## Setting Up

This section explains how to set up your Android project to use ChromiumWebView.

### Get the Code

Check out the repository in your Eclipse workspace, and make your project use 
ChromiumWebView as a library. In Eclipse, right-click your project directory, 
select `Properties`, choose the `Android` category, and click on the `Add` button 
in the `Library` section.

### Copy Data

Copy `assets/webviewchromium.pak` to your project's `assets` directory. [Star 
this bug](https://code.google.com/p/android/issues/detail?id=35748) if you 
agree that this is annoying.

### TestShell

There is a sample project to illustrate the usage of ChromiumWebView
in the test folder. It is only a shell program that can navigate website.

## How to build libchromeview

### Environment

1. Please set up your build environment by following the instructions on the Chromium wiki:
 * [Building on Linux](http://code.google.com/p/chromium/wiki/LinuxBuildInstructionsPrerequisites)
2. You need to install extra pre-requisites to build for Android, covered in [building Chrome for Android](http://code.google.com/p/chromium/wiki/AndroidBuildInstructions#Install_prerequisites).
3. [depot_tools](http://www.chromium.org/developers/how-tos/install-depot-tools) contains the following tools, used to manage and build libchromeview from source:

 * *gclient* manages code and dependencies.
 * *ninja* is the recommended tool for building libchromeview on Android. Its [website](http://code.google.com/p/chromium/wiki/NinjaBuild) contains detailed usage instructions.

### Download the source

1. Create a source directory:
 
 ```
 cd <home directory>
 mkdir chromium-src
 cd chromium-src
 ```
2. Auto-generate gclient's configuration file (.gclient):
 
 ```
 gclient config --name=src/chromeview git://github.com/mogoweb/chromium_webview.git
 ```
 You can replace git:// with ssh://git@ to use your GitHub credentials when checking out the code.
3. From the same directory containing the .gclient file, fetch the source with:
 
 ```
 gclient sync
 ```

### Building libchromium_webview

1. Setup build environment
 
 ```
 cd chromium-src
 source ./chromeview/build/envsetup.sh
 mogo_gyp
 ```
2. build libchromeview
 
 ```
 ninja -C out/Debug libchromeview -j8
 ```

## Copyright and License

The directories below contain code from the
[The Chromium Project](http://www.chromium.org/), which is subject to the
copyright and license on the project site.

* `assets/`
* `libs/`
* `src/com/googlecode`
* `src/org/chromium`

Some of the source code in `src/com/mogoweb/chrome` has been derived from the
Android source code, and is therefore covered by the
[Android project licenses](http://source.android.com/source/licenses.html).

The rest of the code is Copyright 2013 mogoweb, All rights reserved. Use of 
this source code is governed by a BSD-style license that can be found in the LICENSE file.
