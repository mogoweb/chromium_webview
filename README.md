ChromiumWebView
================

Android WebView wrapper based on chromium
## Why you need

The current performance of Android webview is so poor. ChromeView gives your 
application early access to the newest features in Chromium, and removes the
variability due to different WebView implementations in different versions of
Android.

Inspired by [ChromeView project](https://github.com/pwnall/chromeview), our 
goal is to provide full Android WebKit compatible API, so the web apps or 
hybrid apps can easily immigrate to chromium. 

## Setting Up

This section explains how to set up your Android project to use ChromiumWebView.

### Get the Code

Check out the repository in your Eclipse workspace, and make your project use 
ChromiumWebView as a library. In Eclipse, right-click your project directory, 
select Properties, choose the Android category, and click on the Add button 
in the Library section.

### Copy Data

Copy `assets/webviewchromium.pak` to your project's `assets` directory. [Star 
this bug](https://code.google.com/p/android/issues/detail?id=35748) if you 
agree that this is annoying.

### Initialize

In your Application subclass, call ChromeIntializer.initialize and pass it the 
application's context. For example,

```java
import com.mogoweb.chrome.ChromeInitializer;
import android.app.Application;

public class MyApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        ChromeInitializer.initialize(this);
    }
}
```

Now you can use ChromiumWebView as the same as Android WebView except different
package name.

### TestShell

There is a sample project to illustrate the usage of ChromiumWebView
in the test folder. It is only a shell program that can navigate website.

### Copyright and License

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
