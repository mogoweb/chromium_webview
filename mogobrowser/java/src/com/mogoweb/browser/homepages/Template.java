
/*
 * Copyright (C) 2011 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.mogoweb.browser.homepages;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import android.content.Context;
import android.content.res.Resources;
import android.database.Cursor;
import android.util.TypedValue;

import com.mogoweb.browser.R;

public class Template {

    private static HashMap<Integer, Template> sCachedTemplates = new HashMap<Integer, Template>();

    public static Template getCachedTemplate(Context context, int id) {
        synchronized (sCachedTemplates) {
            Template template = sCachedTemplates.get(id);
            if (template == null) {
                template = new Template(context, id);
                sCachedTemplates.put(id, template);
            }
            // Return a copy so that we don't share data
            return template.copy();
        }
    }

    interface Entity {
        void write(OutputStream stream, EntityData params) throws IOException;
    }

    interface EntityData {
        void writeValue(OutputStream stream, String key) throws IOException;
        ListEntityIterator getListIterator(String key);
    }

    interface ListEntityIterator extends EntityData {
        void reset();
        boolean moveToNext();
    }

    static class StringEntity implements Entity {

        byte[] mValue;

        public StringEntity(String value) {
            mValue = value.getBytes();
        }

        @Override
        public void write(OutputStream stream, EntityData params) throws IOException {
            stream.write(mValue);
        }

    }

    static class SimpleEntity implements Entity {

        String mKey;

        public SimpleEntity(String key) {
            mKey = key;
        }

        @Override
        public void write(OutputStream stream, EntityData params) throws IOException {
            params.writeValue(stream, mKey);
        }

    }

    static class ListEntity implements Entity {

        String mKey;
        Template mSubTemplate;

        public ListEntity(Context context, String key, String subTemplate) {
            mKey = key;
            mSubTemplate = new Template(context, subTemplate);
        }

        @Override
        public void write(OutputStream stream, EntityData params) throws IOException {
            ListEntityIterator iter = params.getListIterator(mKey);
            iter.reset();
            while (iter.moveToNext()) {
                mSubTemplate.write(stream, iter);
            }
        }

    }

    public abstract static class CursorListEntityWrapper implements ListEntityIterator {

        private Cursor mCursor;

        public CursorListEntityWrapper(Cursor cursor) {
            mCursor = cursor;
        }

        @Override
        public boolean moveToNext() {
            return mCursor.moveToNext();
        }

        @Override
        public void reset() {
            mCursor.moveToPosition(-1);
        }

        @Override
        public ListEntityIterator getListIterator(String key) {
            return null;
        }

        public Cursor getCursor() {
            return mCursor;
        }

    }

    static class HashMapEntityData implements EntityData {

        HashMap<String, Object> mData;

        public HashMapEntityData(HashMap<String, Object> map) {
            mData = map;
        }

        @Override
        public ListEntityIterator getListIterator(String key) {
            return (ListEntityIterator) mData.get(key);
        }

        @Override
        public void writeValue(OutputStream stream, String key) throws IOException {
            stream.write((byte[]) mData.get(key));
        }

    }

    private List<Entity> mTemplate;
    private HashMap<String, Object> mData = new HashMap<String, Object>();
    private Template(Context context, int tid) {
        this(context, readRaw(context, tid));
    }

    private Template(Context context, String template) {
        mTemplate = new ArrayList<Entity>();
        template = replaceConsts(context, template);
        parseTemplate(context, template);
    }

    private Template(Template copy) {
        mTemplate = copy.mTemplate;
    }

    Template copy() {
        return new Template(this);
    }

    void parseTemplate(Context context, String template) {
        final Pattern pattern = Pattern.compile("<%([=\\{])\\s*(\\w+)\\s*%>");
        Matcher m = pattern.matcher(template);
        int start = 0;
        while (m.find()) {
            String static_part = template.substring(start, m.start());
            if (static_part.length() > 0) {
                mTemplate.add(new StringEntity(static_part));
            }
            String type = m.group(1);
            String name = m.group(2);
            if (type.equals("=")) {
                mTemplate.add(new SimpleEntity(name));
            } else if (type.equals("{")) {
                Pattern p = Pattern.compile("<%\\}\\s*" + Pattern.quote(name) + "\\s*%>");
                Matcher end_m = p.matcher(template);
                if (end_m.find(m.end())) {
                    start = m.end();
                    m.region(end_m.end(), template.length());
                    String subTemplate = template.substring(start, end_m.start());
                    mTemplate.add(new ListEntity(context, name, subTemplate));
                    start = end_m.end();
                    continue;
                }
            }
            start = m.end();
        }
        String static_part = template.substring(start, template.length());
        if (static_part.length() > 0) {
            mTemplate.add(new StringEntity(static_part));
        }
    }

    public void assign(String name, String value) {
        mData.put(name, value.getBytes());
    }

    public void assignLoop(String name, ListEntityIterator iter) {
        mData.put(name, iter);
    }

    public void write(OutputStream stream) throws IOException {
        write(stream, new HashMapEntityData(mData));
    }

    public void write(OutputStream stream, EntityData data) throws IOException {
        for (Entity ent : mTemplate) {
            ent.write(stream, data);
        }
    }

    private static String replaceConsts(Context context, String template) {
        final Pattern pattern = Pattern.compile("<%@\\s*(\\w+/\\w+)\\s*%>");
        final Resources res = context.getResources();
        final String packageName = R.class.getPackage().getName();
        Matcher m = pattern.matcher(template);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            String name = m.group(1);
            if (name.startsWith("drawable/")) {
                m.appendReplacement(sb, "res/" + name);
            } else {
                int id = res.getIdentifier(name, null, packageName);
                if (id != 0) {
                    TypedValue value = new TypedValue();
                    res.getValue(id, value, true);
                    String replacement;
                    if (value.type == TypedValue.TYPE_DIMENSION) {
                        float dimen = res.getDimension(id);
                        int dimeni = (int) dimen;
                        if (dimeni == dimen)
                            replacement = Integer.toString(dimeni);
                        else
                            replacement = Float.toString(dimen);
                    } else {
                        replacement = value.coerceToString().toString();
                    }
                    m.appendReplacement(sb, replacement);
                }
            }
        }
        m.appendTail(sb);
        return sb.toString();
    }

    private static String readRaw(Context context, int id) {
        InputStream ins = context.getResources().openRawResource(id);
        try {
            byte[] buf = new byte[ins.available()];
            ins.read(buf);
            return new String(buf, "utf-8");
        } catch (IOException ex) {
            return "<html><body>Error</body></html>";
        }
    }

}
