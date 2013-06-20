///*
// * Copyright (C) 2009 The Android Open Source Project
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
//package com.mogoweb.browser.preferences;
//
//import android.app.AlertDialog;
//import android.app.ListFragment;
//import android.content.Context;
//import android.content.DialogInterface;
//import android.database.Cursor;
//import android.graphics.Bitmap;
//import android.graphics.BitmapFactory;
//import android.net.Uri;
//import android.os.AsyncTask;
//import android.os.Bundle;
//import android.os.Parcel;
//import android.os.Parcelable;
//import android.preference.PreferenceActivity;
//import android.provider.BrowserContract.Bookmarks;
//import android.util.Log;
//import android.view.LayoutInflater;
//import android.view.View;
//import android.view.View.OnClickListener;
//import android.view.ViewGroup;
//import android.webkit.GeolocationPermissions;
//import android.webkit.ValueCallback;
//import android.webkit.WebStorage;
//import android.widget.AdapterView;
//import android.widget.ArrayAdapter;
//import android.widget.ImageView;
//import android.widget.TextView;
//
//import com.mogoweb.browser.R;
//import com.mogoweb.browser.WebStorageSizeManager;
//
//import java.util.HashMap;
//import java.util.HashSet;
//import java.util.Iterator;
//import java.util.Map;
//import java.util.Set;
//
///**
// * Manage the settings for an origin.
// * We use it to keep track of the 'HTML5' settings, i.e. database (webstorage)
// * and Geolocation.
// */
//public class WebsiteSettingsFragment extends ListFragment implements OnClickListener {
//
//    private static final String EXTRA_SITE = "site";
//    private String LOGTAG = "WebsiteSettingsActivity";
//    private static String sMBStored = null;
//    private SiteAdapter mAdapter = null;
//    private Site mSite = null;
//
//    static class Site implements Parcelable {
//        private String mOrigin;
//        private String mTitle;
//        private Bitmap mIcon;
//        private int mFeatures;
//
//        // These constants provide the set of features that a site may support
//        // They must be consecutive. To add a new feature, add a new FEATURE_XXX
//        // variable with value equal to the current value of FEATURE_COUNT, then
//        // increment FEATURE_COUNT.
//        final static int FEATURE_WEB_STORAGE = 0;
//        final static int FEATURE_GEOLOCATION = 1;
//        // The number of features available.
//        final static int FEATURE_COUNT = 2;
//
//        public Site(String origin) {
//            mOrigin = origin;
//            mTitle = null;
//            mIcon = null;
//            mFeatures = 0;
//        }
//
//        public void addFeature(int feature) {
//            mFeatures |= (1 << feature);
//        }
//
//        public void removeFeature(int feature) {
//            mFeatures &= ~(1 << feature);
//        }
//
//        public boolean hasFeature(int feature) {
//            return (mFeatures & (1 << feature)) != 0;
//        }
//
//        /**
//         * Gets the number of features supported by this site.
//         */
//        public int getFeatureCount() {
//            int count = 0;
//            for (int i = 0; i < FEATURE_COUNT; ++i) {
//                count += hasFeature(i) ? 1 : 0;
//            }
//            return count;
//        }
//
//        /**
//         * Gets the ID of the nth (zero-based) feature supported by this site.
//         * The return value is a feature ID - one of the FEATURE_XXX values.
//         * This is required to determine which feature is displayed at a given
//         * position in the list of features for this site. This is used both
//         * when populating the view and when responding to clicks on the list.
//         */
//        public int getFeatureByIndex(int n) {
//            int j = -1;
//            for (int i = 0; i < FEATURE_COUNT; ++i) {
//                j += hasFeature(i) ? 1 : 0;
//                if (j == n) {
//                    return i;
//                }
//            }
//            return -1;
//        }
//
//        public String getOrigin() {
//            return mOrigin;
//        }
//
//        public void setTitle(String title) {
//            mTitle = title;
//        }
//
//        public void setIcon(Bitmap icon) {
//            mIcon = icon;
//        }
//
//        public Bitmap getIcon() {
//            return mIcon;
//        }
//
//        public String getPrettyOrigin() {
//            return mTitle == null ? null : hideHttp(mOrigin);
//        }
//
//        public String getPrettyTitle() {
//            return mTitle == null ? hideHttp(mOrigin) : mTitle;
//        }
//
//        private String hideHttp(String str) {
//            Uri uri = Uri.parse(str);
//            return "http".equals(uri.getScheme()) ?  str.substring(7) : str;
//        }
//
//        @Override
//        public int describeContents() {
//            return 0;
//        }
//
//        @Override
//        public void writeToParcel(Parcel dest, int flags) {
//            dest.writeString(mOrigin);
//            dest.writeString(mTitle);
//            dest.writeInt(mFeatures);
//            dest.writeParcelable(mIcon, flags);
//        }
//
//        private Site(Parcel in) {
//            mOrigin = in.readString();
//            mTitle = in.readString();
//            mFeatures = in.readInt();
//            mIcon = in.readParcelable(null);
//        }
//
//        public static final Parcelable.Creator<Site> CREATOR
//                = new Parcelable.Creator<Site>() {
//            public Site createFromParcel(Parcel in) {
//                return new Site(in);
//            }
//
//            public Site[] newArray(int size) {
//                return new Site[size];
//            }
//        };
//
//    }
//
//    class SiteAdapter extends ArrayAdapter<Site>
//            implements AdapterView.OnItemClickListener {
//        private int mResource;
//        private LayoutInflater mInflater;
//        private Bitmap mDefaultIcon;
//        private Bitmap mUsageEmptyIcon;
//        private Bitmap mUsageLowIcon;
//        private Bitmap mUsageHighIcon;
//        private Bitmap mLocationAllowedIcon;
//        private Bitmap mLocationDisallowedIcon;
//        private Site mCurrentSite;
//
//        public SiteAdapter(Context context, int rsc) {
//            this(context, rsc, null);
//        }
//
//        public SiteAdapter(Context context, int rsc, Site site) {
//            super(context, rsc);
//            mResource = rsc;
//            mInflater = (LayoutInflater)context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
//            mDefaultIcon = BitmapFactory.decodeResource(getResources(),
//                    R.drawable.app_web_browser_sm);
//            mUsageEmptyIcon = BitmapFactory.decodeResource(getResources(),
//                    R.drawable.ic_list_data_off);
//            mUsageLowIcon = BitmapFactory.decodeResource(getResources(),
//                    R.drawable.ic_list_data_small);
//            mUsageHighIcon = BitmapFactory.decodeResource(getResources(),
//                    R.drawable.ic_list_data_large);
//            mLocationAllowedIcon = BitmapFactory.decodeResource(getResources(),
//                    R.drawable.ic_gps_on_holo_dark);
//            mLocationDisallowedIcon = BitmapFactory.decodeResource(getResources(),
//                    R.drawable.ic_gps_denied_holo_dark);
//            mCurrentSite = site;
//            if (mCurrentSite == null) {
//                askForOrigins();
//            }
//        }
//
//        /**
//         * Adds the specified feature to the site corresponding to supplied
//         * origin in the map. Creates the site if it does not already exist.
//         */
//        private void addFeatureToSite(Map<String, Site> sites, String origin, int feature) {
//            Site site = null;
//            if (sites.containsKey(origin)) {
//                site = (Site) sites.get(origin);
//            } else {
//                site = new Site(origin);
//                sites.put(origin, site);
//            }
//            site.addFeature(feature);
//        }
//
//        public void askForOrigins() {
//            // Get the list of origins we want to display.
//            // All 'HTML 5 modules' (Database, Geolocation etc) form these
//            // origin strings using WebCore::SecurityOrigin::toString(), so it's
//            // safe to group origins here. Note that WebCore::SecurityOrigin
//            // uses 0 (which is not printed) for the port if the port is the
//            // default for the protocol. Eg http://www.google.com and
//            // http://www.google.com:80 both record a port of 0 and hence
//            // toString() == 'http://www.google.com' for both.
//
//            WebStorage.getInstance().getOrigins(new ValueCallback<Map>() {
//                public void onReceiveValue(Map origins) {
//                    Map<String, Site> sites = new HashMap<String, Site>();
//                    if (origins != null) {
//                        Iterator<String> iter = origins.keySet().iterator();
//                        while (iter.hasNext()) {
//                            addFeatureToSite(sites, iter.next(), Site.FEATURE_WEB_STORAGE);
//                        }
//                    }
//                    askForGeolocation(sites);
//                }
//            });
//        }
//
//        public void askForGeolocation(final Map<String, Site> sites) {
//            GeolocationPermissions.getInstance().getOrigins(new ValueCallback<Set<String> >() {
//                public void onReceiveValue(Set<String> origins) {
//                    if (origins != null) {
//                        Iterator<String> iter = origins.iterator();
//                        while (iter.hasNext()) {
//                            addFeatureToSite(sites, iter.next(), Site.FEATURE_GEOLOCATION);
//                        }
//                    }
//                    populateIcons(sites);
//                    populateOrigins(sites);
//                }
//            });
//        }
//
//        public void populateIcons(Map<String, Site> sites) {
//            // Create a map from host to origin. This is used to add metadata
//            // (title, icon) for this origin from the bookmarks DB. We must do
//            // the DB access on a background thread.
//            new UpdateFromBookmarksDbTask(this.getContext(), sites).execute();
//        }
//
//        private class UpdateFromBookmarksDbTask extends AsyncTask<Void, Void, Void> {
//
//            private Context mContext;
//            private boolean mDataSetChanged;
//            private Map<String, Site> mSites;
//
//            public UpdateFromBookmarksDbTask(Context ctx, Map<String, Site> sites) {
//                mContext = ctx.getApplicationContext();
//                mSites = sites;
//            }
//
//            protected Void doInBackground(Void... unused) {
//                HashMap<String, Set<Site>> hosts = new HashMap<String, Set<Site>>();
//                Set<Map.Entry<String, Site>> elements = mSites.entrySet();
//                Iterator<Map.Entry<String, Site>> originIter = elements.iterator();
//                while (originIter.hasNext()) {
//                    Map.Entry<String, Site> entry = originIter.next();
//                    Site site = entry.getValue();
//                    String host = Uri.parse(entry.getKey()).getHost();
//                    Set<Site> hostSites = null;
//                    if (hosts.containsKey(host)) {
//                        hostSites = (Set<Site>)hosts.get(host);
//                    } else {
//                        hostSites = new HashSet<Site>();
//                        hosts.put(host, hostSites);
//                    }
//                    hostSites.add(site);
//                }
//
//                // Check the bookmark DB. If we have data for a host used by any of
//                // our origins, use it to set their title and favicon
//                Cursor c = mContext.getContentResolver().query(Bookmarks.CONTENT_URI,
//                        new String[] { Bookmarks.URL, Bookmarks.TITLE, Bookmarks.FAVICON },
//                        Bookmarks.IS_FOLDER + " == 0", null, null);
//
//                if (c != null) {
//                    if (c.moveToFirst()) {
//                        int urlIndex = c.getColumnIndex(Bookmarks.URL);
//                        int titleIndex = c.getColumnIndex(Bookmarks.TITLE);
//                        int faviconIndex = c.getColumnIndex(Bookmarks.FAVICON);
//                        do {
//                            String url = c.getString(urlIndex);
//                            String host = Uri.parse(url).getHost();
//                            if (hosts.containsKey(host)) {
//                                String title = c.getString(titleIndex);
//                                Bitmap bmp = null;
//                                byte[] data = c.getBlob(faviconIndex);
//                                if (data != null) {
//                                    bmp = BitmapFactory.decodeByteArray(data, 0, data.length);
//                                }
//                                Set matchingSites = (Set) hosts.get(host);
//                                Iterator<Site> sitesIter = matchingSites.iterator();
//                                while (sitesIter.hasNext()) {
//                                    Site site = sitesIter.next();
//                                    // We should only set the title if the bookmark is for the root
//                                    // (i.e. www.google.com), as website settings act on the origin
//                                    // as a whole rather than a single page under that origin. If the
//                                    // user has bookmarked a page under the root but *not* the root,
//                                    // then we risk displaying the title of that page which may or
//                                    // may not have any relevance to the origin.
//                                    if (url.equals(site.getOrigin()) ||
//                                            (new String(site.getOrigin()+"/")).equals(url)) {
//                                        mDataSetChanged = true;
//                                        site.setTitle(title);
//                                    }
//
//                                    if (bmp != null) {
//                                        mDataSetChanged = true;
//                                        site.setIcon(bmp);
//                                    }
//                                }
//                            }
//                        } while (c.moveToNext());
//                    }
//                    c.close();
//                }
//            return null;
//            }
//
//            protected void onPostExecute(Void unused) {
//                if (mDataSetChanged) {
//                    notifyDataSetChanged();
//                }
//            }
//        }
//
//
//        public void populateOrigins(Map<String, Site> sites) {
//            clear();
//
//            // We can now simply populate our array with Site instances
//            Set<Map.Entry<String, Site>> elements = sites.entrySet();
//            Iterator<Map.Entry<String, Site>> entryIterator = elements.iterator();
//            while (entryIterator.hasNext()) {
//                Map.Entry<String, Site> entry = entryIterator.next();
//                Site site = entry.getValue();
//                add(site);
//            }
//
//            notifyDataSetChanged();
//
//            if (getCount() == 0) {
//                finish(); // we close the screen
//            }
//        }
//
//        public int getCount() {
//            if (mCurrentSite == null) {
//                return super.getCount();
//            }
//            return mCurrentSite.getFeatureCount();
//        }
//
//        public String sizeValueToString(long bytes) {
//            // We display the size in MB, to 1dp, rounding up to the next 0.1MB.
//            // bytes should always be greater than zero.
//            if (bytes <= 0) {
//                Log.e(LOGTAG, "sizeValueToString called with non-positive value: " + bytes);
//                return "0";
//            }
//            float megabytes = (float) bytes / (1024.0F * 1024.0F);
//            int truncated = (int) Math.ceil(megabytes * 10.0F);
//            float result = (float) (truncated / 10.0F);
//            return String.valueOf(result);
//        }
//
//        /*
//         * If we receive the back event and are displaying
//         * site's settings, we want to go back to the main
//         * list view. If not, we just do nothing (see
//         * dispatchKeyEvent() below).
//         */
//        public boolean backKeyPressed() {
//            if (mCurrentSite != null) {
//                mCurrentSite = null;
//                askForOrigins();
//                return true;
//            }
//            return false;
//        }
//
//        /**
//         * @hide
//         * Utility function
//         * Set the icon according to the usage
//         */
//        public void setIconForUsage(ImageView usageIcon, long usageInBytes) {
//            float usageInMegabytes = (float) usageInBytes / (1024.0F * 1024.0F);
//            // We set the correct icon:
//            // 0 < empty < 0.1MB
//            // 0.1MB < low < 5MB
//            // 5MB < high
//            if (usageInMegabytes <= 0.1) {
//                usageIcon.setImageBitmap(mUsageEmptyIcon);
//            } else if (usageInMegabytes > 0.1 && usageInMegabytes <= 5) {
//                usageIcon.setImageBitmap(mUsageLowIcon);
//            } else if (usageInMegabytes > 5) {
//                usageIcon.setImageBitmap(mUsageHighIcon);
//            }
//        }
//
//        @Override
//        public View getView(int position, View convertView, ViewGroup parent) {
//            View view;
//            final TextView title;
//            final TextView subtitle;
//            final ImageView icon;
//            final ImageView usageIcon;
//            final ImageView locationIcon;
//            final ImageView featureIcon;
//
//            if (convertView == null) {
//                view = mInflater.inflate(mResource, parent, false);
//            } else {
//                view = convertView;
//            }
//
//            title = (TextView) view.findViewById(R.id.title);
//            subtitle = (TextView) view.findViewById(R.id.subtitle);
//            icon = (ImageView) view.findViewById(R.id.icon);
//            featureIcon = (ImageView) view.findViewById(R.id.feature_icon);
//            usageIcon = (ImageView) view.findViewById(R.id.usage_icon);
//            locationIcon = (ImageView) view.findViewById(R.id.location_icon);
//            usageIcon.setVisibility(View.GONE);
//            locationIcon.setVisibility(View.GONE);
//
//            if (mCurrentSite == null) {
//
//                Site site = getItem(position);
//                title.setText(site.getPrettyTitle());
//                String subtitleText = site.getPrettyOrigin();
//                if (subtitleText != null) {
//                    title.setMaxLines(1);
//                    title.setSingleLine(true);
//                    subtitle.setVisibility(View.VISIBLE);
//                    subtitle.setText(subtitleText);
//                } else {
//                    subtitle.setVisibility(View.GONE);
//                    title.setMaxLines(2);
//                    title.setSingleLine(false);
//                }
//
//                icon.setVisibility(View.VISIBLE);
//                usageIcon.setVisibility(View.INVISIBLE);
//                locationIcon.setVisibility(View.INVISIBLE);
//                featureIcon.setVisibility(View.GONE);
//                Bitmap bmp = site.getIcon();
//                if (bmp == null) {
//                    bmp = mDefaultIcon;
//                }
//                icon.setImageBitmap(bmp);
//                // We set the site as the view's tag,
//                // so that we can get it in onItemClick()
//                view.setTag(site);
//
//                String origin = site.getOrigin();
//                if (site.hasFeature(Site.FEATURE_WEB_STORAGE)) {
//                    WebStorage.getInstance().getUsageForOrigin(origin, new ValueCallback<Long>() {
//                        public void onReceiveValue(Long value) {
//                            if (value != null) {
//                                setIconForUsage(usageIcon, value.longValue());
//                                usageIcon.setVisibility(View.VISIBLE);
//                            }
//                        }
//                    });
//                }
//
//                if (site.hasFeature(Site.FEATURE_GEOLOCATION)) {
//                    locationIcon.setVisibility(View.VISIBLE);
//                    GeolocationPermissions.getInstance().getAllowed(origin, new ValueCallback<Boolean>() {
//                        public void onReceiveValue(Boolean allowed) {
//                            if (allowed != null) {
//                                if (allowed.booleanValue()) {
//                                    locationIcon.setImageBitmap(mLocationAllowedIcon);
//                                } else {
//                                    locationIcon.setImageBitmap(mLocationDisallowedIcon);
//                                }
//                            }
//                        }
//                    });
//                }
//            } else {
//                icon.setVisibility(View.GONE);
//                locationIcon.setVisibility(View.GONE);
//                usageIcon.setVisibility(View.GONE);
//                featureIcon.setVisibility(View.VISIBLE);
//                String origin = mCurrentSite.getOrigin();
//                switch (mCurrentSite.getFeatureByIndex(position)) {
//                    case Site.FEATURE_WEB_STORAGE:
//                        WebStorage.getInstance().getUsageForOrigin(origin, new ValueCallback<Long>() {
//                            public void onReceiveValue(Long value) {
//                                if (value != null) {
//                                    String usage = sizeValueToString(value.longValue()) + " " + sMBStored;
//                                    title.setText(R.string.webstorage_clear_data_title);
//                                    subtitle.setText(usage);
//                                    subtitle.setVisibility(View.VISIBLE);
//                                    setIconForUsage(featureIcon, value.longValue());
//                                }
//                            }
//                        });
//                        break;
//                    case Site.FEATURE_GEOLOCATION:
//                        title.setText(R.string.geolocation_settings_page_title);
//                        GeolocationPermissions.getInstance().getAllowed(origin, new ValueCallback<Boolean>() {
//                            public void onReceiveValue(Boolean allowed) {
//                                if (allowed != null) {
//                                    if (allowed.booleanValue()) {
//                                        subtitle.setText(R.string.geolocation_settings_page_summary_allowed);
//                                        featureIcon.setImageBitmap(mLocationAllowedIcon);
//                                    } else {
//                                        subtitle.setText(R.string.geolocation_settings_page_summary_not_allowed);
//                                        featureIcon.setImageBitmap(mLocationDisallowedIcon);
//                                    }
//                                    subtitle.setVisibility(View.VISIBLE);
//                                }
//                            }
//                        });
//                        break;
//                }
//            }
//
//            return view;
//        }
//
//        public void onItemClick(AdapterView<?> parent,
//                                View view,
//                                int position,
//                                long id) {
//            if (mCurrentSite != null) {
//                switch (mCurrentSite.getFeatureByIndex(position)) {
//                    case Site.FEATURE_WEB_STORAGE:
//                        new AlertDialog.Builder(getContext())
//                            .setMessage(R.string.webstorage_clear_data_dialog_message)
//                            .setPositiveButton(R.string.webstorage_clear_data_dialog_ok_button,
//                                               new AlertDialog.OnClickListener() {
//                                public void onClick(DialogInterface dlg, int which) {
//                                    WebStorage.getInstance().deleteOrigin(mCurrentSite.getOrigin());
//                                    // If this site has no more features, then go back to the
//                                    // origins list.
//                                    mCurrentSite.removeFeature(Site.FEATURE_WEB_STORAGE);
//                                    if (mCurrentSite.getFeatureCount() == 0) {
//                                        finish();
//                                    }
//                                    askForOrigins();
//                                    notifyDataSetChanged();
//                                }})
//                            .setNegativeButton(R.string.webstorage_clear_data_dialog_cancel_button, null)
//                            .setIconAttribute(android.R.attr.alertDialogIcon)
//                            .show();
//                        break;
//                    case Site.FEATURE_GEOLOCATION:
//                        new AlertDialog.Builder(getContext())
//                            .setMessage(R.string.geolocation_settings_page_dialog_message)
//                            .setPositiveButton(R.string.geolocation_settings_page_dialog_ok_button,
//                                               new AlertDialog.OnClickListener() {
//                                public void onClick(DialogInterface dlg, int which) {
//                                    GeolocationPermissions.getInstance().clear(mCurrentSite.getOrigin());
//                                    mCurrentSite.removeFeature(Site.FEATURE_GEOLOCATION);
//                                    if (mCurrentSite.getFeatureCount() == 0) {
//                                        finish();
//                                    }
//                                    askForOrigins();
//                                    notifyDataSetChanged();
//                                }})
//                            .setNegativeButton(R.string.geolocation_settings_page_dialog_cancel_button, null)
//                            .setIconAttribute(android.R.attr.alertDialogIcon)
//                            .show();
//                        break;
//                }
//            } else {
//                Site site = (Site) view.getTag();
//                PreferenceActivity activity = (PreferenceActivity) getActivity();
//                if (activity != null) {
//                    Bundle args = new Bundle();
//                    args.putParcelable(EXTRA_SITE, site);
//                    activity.startPreferencePanel(WebsiteSettingsFragment.class.getName(), args, 0,
//                            site.getPrettyTitle(), null, 0);
//                }
//            }
//        }
//
//        public Site currentSite() {
//            return mCurrentSite;
//        }
//    }
//
//    @Override
//    public View onCreateView(LayoutInflater inflater, ViewGroup container,
//            Bundle savedInstanceState) {
//        View view = inflater.inflate(R.layout.website_settings, container, false);
//        Bundle args = getArguments();
//        if (args != null) {
//            mSite = (Site) args.getParcelable(EXTRA_SITE);
//        }
//        if (mSite == null) {
//            View clear = view.findViewById(R.id.clear_all_button);
//            clear.setVisibility(View.VISIBLE);
//            clear.setOnClickListener(this);
//        }
//        return view;
//    }
//
//    @Override
//    public void onActivityCreated(Bundle savedInstanceState) {
//        super.onActivityCreated(savedInstanceState);
//        if (sMBStored == null) {
//            sMBStored = getString(R.string.webstorage_origin_summary_mb_stored);
//        }
//        mAdapter = new SiteAdapter(getActivity(), R.layout.website_settings_row);
//        if (mSite != null) {
//            mAdapter.mCurrentSite = mSite;
//        }
//        getListView().setAdapter(mAdapter);
//        getListView().setOnItemClickListener(mAdapter);
//    }
//
//    @Override
//    public void onResume() {
//        super.onResume();
//        mAdapter.askForOrigins();
//    }
//
//    private void finish() {
//        PreferenceActivity activity = (PreferenceActivity) getActivity();
//        if (activity != null) {
//            activity.finishPreferencePanel(this, 0, null);
//        }
//    }
//
//    @Override
//    public void onClick(View v) {
//        switch (v.getId()) {
//        case R.id.clear_all_button:
//         // Show the prompt to clear all origins of their data and geolocation permissions.
//            new AlertDialog.Builder(getActivity())
//                    .setMessage(R.string.website_settings_clear_all_dialog_message)
//                    .setPositiveButton(R.string.website_settings_clear_all_dialog_ok_button,
//                            new AlertDialog.OnClickListener() {
//                                public void onClick(DialogInterface dlg, int which) {
//                                    WebStorage.getInstance().deleteAllData();
//                                    GeolocationPermissions.getInstance().clearAll();
//                                    WebStorageSizeManager.resetLastOutOfSpaceNotificationTime();
//                                    mAdapter.askForOrigins();
//                                    finish();
//                                }})
//                    .setNegativeButton(R.string.website_settings_clear_all_dialog_cancel_button, null)
//                    .setIconAttribute(android.R.attr.alertDialogIcon)
//                    .show();
//            break;
//        }
//    }
//}
