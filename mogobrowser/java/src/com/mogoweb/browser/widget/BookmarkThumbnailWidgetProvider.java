///*
// * Copyright (C) 2010 The Android Open Source Project
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
//package com.mogoweb.browser.widget;
//
//import android.app.PendingIntent;
//import android.appwidget.AppWidgetManager;
//import android.appwidget.AppWidgetProvider;
//import android.content.ComponentName;
//import android.content.Context;
//import android.content.Intent;
//import android.net.Uri;
//import android.widget.RemoteViews;
//
//import com.mogoweb.browser.BrowserActivity;
//import com.mogoweb.browser.R;
//
///**
// * Widget that shows a preview of the user's bookmarks.
// */
//public class BookmarkThumbnailWidgetProvider extends AppWidgetProvider {
//    public static final String ACTION_BOOKMARK_APPWIDGET_UPDATE =
//        "com.mogoweb.browser.BOOKMARK_APPWIDGET_UPDATE";
//
//    @Override
//    public void onReceive(Context context, Intent intent) {
//        // Handle bookmark-specific updates ourselves because they might be
//        // coming in without extras, which AppWidgetProvider then blocks.
//        final String action = intent.getAction();
//        if (ACTION_BOOKMARK_APPWIDGET_UPDATE.equals(action)) {
//            AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(context);
//            performUpdate(context, appWidgetManager,
//                    appWidgetManager.getAppWidgetIds(getComponentName(context)));
//        } else {
//            super.onReceive(context, intent);
//        }
//    }
//
//    @Override
//    public void onUpdate(Context context, AppWidgetManager mngr, int[] ids) {
//        performUpdate(context, mngr, ids);
//    }
//
//    @Override
//    public void onDeleted(Context context, int[] appWidgetIds) {
//        super.onDeleted(context, appWidgetIds);
//        for (int widgetId : appWidgetIds) {
//            BookmarkThumbnailWidgetService.deleteWidgetState(context, widgetId);
//        }
//        removeOrphanedFiles(context);
//    }
//
//    @Override
//    public void onDisabled(Context context) {
//        super.onDisabled(context);
//        removeOrphanedFiles(context);
//    }
//
//    /**
//     *  Checks for any state files that may have not received onDeleted
//     */
//    void removeOrphanedFiles(Context context) {
//        AppWidgetManager wm = AppWidgetManager.getInstance(context);
//        int[] ids = wm.getAppWidgetIds(getComponentName(context));
//        BookmarkThumbnailWidgetService.removeOrphanedStates(context, ids);
//    }
//
//    private void performUpdate(Context context,
//            AppWidgetManager appWidgetManager, int[] appWidgetIds) {
//        PendingIntent launchBrowser = PendingIntent.getActivity(context, 0,
//                new Intent(BrowserActivity.ACTION_SHOW_BROWSER, null, context,
//                    BrowserActivity.class),
//                PendingIntent.FLAG_UPDATE_CURRENT);
//        for (int appWidgetId : appWidgetIds) {
//            Intent updateIntent = new Intent(context, BookmarkThumbnailWidgetService.class);
//            updateIntent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);
//            updateIntent.setData(Uri.parse(updateIntent.toUri(Intent.URI_INTENT_SCHEME)));
//            RemoteViews views = new RemoteViews(context.getPackageName(),
//                    R.layout.bookmarkthumbnailwidget);
//            views.setOnClickPendingIntent(R.id.app_shortcut, launchBrowser);
//            views.setRemoteAdapter(R.id.bookmarks_list, updateIntent);
//            appWidgetManager.notifyAppWidgetViewDataChanged(appWidgetId, R.id.bookmarks_list);
//            Intent ic = new Intent(context, BookmarkWidgetProxy.class);
//            views.setPendingIntentTemplate(R.id.bookmarks_list,
//                    PendingIntent.getBroadcast(context, 0, ic,
//                    PendingIntent.FLAG_UPDATE_CURRENT));
//            appWidgetManager.updateAppWidget(appWidgetId, views);
//        }
//    }
//
//    /**
//     * Build {@link ComponentName} describing this specific
//     * {@link AppWidgetProvider}
//     */
//    static ComponentName getComponentName(Context context) {
//        return new ComponentName(context, BookmarkThumbnailWidgetProvider.class);
//    }
//
//    public static void refreshWidgets(Context context) {
//        context.sendBroadcast(new Intent(
//                BookmarkThumbnailWidgetProvider.ACTION_BOOKMARK_APPWIDGET_UPDATE,
//                null, context, BookmarkThumbnailWidgetProvider.class));
//    }
//
//}
