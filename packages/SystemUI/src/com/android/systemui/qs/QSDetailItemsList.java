/*
 * Copyright (C) 2014 The Android Open Source Project
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

package com.android.systemui.qs;

import android.content.Context;
import android.graphics.Typeface;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListAdapter;
import android.widget.ListView;

import android.widget.TextView;
import com.android.systemui.R;

import java.util.List;

/**
 * Quick settings common detail list view with line items.
 */
public class QSDetailItemsList extends LinearLayout {
    private static final String TAG = "QSDetailItemsList";

    private ListView mListView;
    private View mEmpty;
    private TextView mEmptyText;
    private ImageView mEmptyIcon;

    public QSDetailItemsList(Context context, AttributeSet attrs) {
        super(context, attrs);
        mContext = context;
        mTag = TAG;
    }

    public static QSDetailItemsList convertOrInflate(Context context,
            View convertView, ViewGroup parent) {
        if (convertView instanceof QSDetailItemsList) {
            return (QSDetailItemsList) convertView;
        }
        LayoutInflater inflater = LayoutInflater.from(context);
        return (QSDetailItemsList) inflater.inflate(R.layout.qs_detail_items_list, parent, false);
    }

    public void setAdapter(ListAdapter adapter) {
        mListView.setAdapter(adapter);
    }

    public ListView getListView() {
        return mListView;
    }

    public void setEmptyState(int icon, int text) {
        mEmptyIcon.setImageResource(icon);
        mEmptyText.setText(text);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        mListView = (ListView) findViewById(android.R.id.list);
        mListView.setOnTouchListener(new OnTouchListener() {
            // Setting on Touch Listener for handling the touch inside ScrollView
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                // Disallow the touch request for parent scroll on touch of child view
                v.getParent().requestDisallowInterceptTouchEvent(true);
                return false;
            }
        });
        mEmpty = findViewById(android.R.id.empty);
        mEmpty.setVisibility(GONE);
        mEmptyText = (TextView) mEmpty.findViewById(android.R.id.title);
        mEmptyIcon = (ImageView) mEmpty.findViewById(android.R.id.icon);
        mListView.setEmptyView(mEmpty);
    }

    public static class QSDetailListAdapter extends ArrayAdapter<QSDetailItems.Item> {
        private QSDetailItems.Callback mCallback;

        public QSDetailListAdapter(Context context, List<QSDetailItems.Item> objects) {
            super(context, R.layout.qs_detail_item, objects);
        }

        public void setCallback(QSDetailItems.Callback cb) {
            mCallback = cb;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            LayoutInflater inflater = LayoutInflater.from(getContext());
            LinearLayout view = (LinearLayout) inflater.inflate(
                    R.layout.qs_detail_item, parent, false);

            view.setClickable(false); // let list view handle this

            final QSDetailItems.Item item = getItem(position);
            if (item.doDisableFocus) {
                view.setFocusable(false);
            }

            final ImageView iv = (ImageView) view.findViewById(android.R.id.icon);
            if (item.doDisableTint) {
                iv.setColorFilter(null);
                iv.setImageTintList(null);
            }
            if (item.icon != null) {
                iv.setImageDrawable(item.icon.getDrawable(iv.getContext()));
            } else {
                iv.setImageResource(item.iconResId);
            }
            iv.getOverlay().clear();
            if (item.overlay != null) {
                item.overlay.setBounds(0, 0, item.overlay.getIntrinsicWidth(),
                        item.overlay.getIntrinsicHeight());
                iv.getOverlay().add(item.overlay);
            }
            final TextView title = (TextView) view.findViewById(android.R.id.title);
            Typeface tf = null;
            if (item.fontPath != null) {
                Typeface.Builder builder = new Typeface.Builder(item.fontPath);
                tf = builder.build();
                title.setTypeface(tf);
            }
            title.setText(item.line1);
            final TextView summary = (TextView) view.findViewById(android.R.id.summary);
            final boolean twoLines = !TextUtils.isEmpty(item.line2);
            if (twoLines && tf != null) {
                summary.setTypeface(tf);
            }
            title.setMaxLines(twoLines ? 1 : 2);
            summary.setVisibility(twoLines ? VISIBLE : GONE);
            summary.setText(twoLines ? item.line2 : null);
            view.setMinimumHeight(getContext().getResources().getDimensionPixelSize(
                    twoLines ? R.dimen.qs_detail_item_height_twoline : R.dimen.qs_detail_item_height));

            final ImageView disconnect = (ImageView) view.findViewById(android.R.id.icon2);
            disconnect.setVisibility(item.canDisconnect ? VISIBLE : GONE);
            disconnect.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (mCallback != null) {
                        mCallback.onDetailItemDisconnect(item);
                    }
                }
            });
            return view;
        }
    }
}
