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
 *
 * Modifications have been made but was based on code from the documentation.
 */

package com.example.android.sunshine;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.Icon;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.annotation.Nullable;
import android.support.v4.content.ContextCompat;
import android.support.wearable.complications.ComplicationData;
import android.support.wearable.complications.ComplicationText;
import android.support.wearable.watchface.CanvasWatchFaceService;
import android.support.wearable.watchface.WatchFaceStyle;
import android.text.TextUtils;
import android.util.Log;
import android.util.SparseArray;
import android.view.SurfaceHolder;
import android.view.WindowInsets;

import com.example.android.sunshine.wear.R;

import java.lang.ref.WeakReference;
import java.util.Calendar;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

public class SunshineWatchFace extends CanvasWatchFaceService {

    private static final String TAG = "SunshineWatchFace";
    private static final long INTERACTIVE_UPDATE_RATE_MS = TimeUnit.SECONDS.toMillis(1);
    private static final int MSG_UPDATE_TIME = 0;
    private static final int TOP_COMPLICATION = 0;
    private static final int LEFT_COMPLICATION = 1;
    private static final int MIDDLE_COMPLICATION = 2;
    private static final int RIGHT_COMPLICATION = 3;
    public static final int[] COMPLICATION_IDS = {
            TOP_COMPLICATION,
            LEFT_COMPLICATION,
            MIDDLE_COMPLICATION,
            RIGHT_COMPLICATION
    };
    public static final int[][] COMPLICATION_SUPPORTED_TYPES = {
            {ComplicationData.TYPE_SHORT_TEXT},
            {ComplicationData.TYPE_SHORT_TEXT, ComplicationData.TYPE_ICON},
            {ComplicationData.TYPE_SHORT_TEXT, ComplicationData.TYPE_ICON},
            {ComplicationData.TYPE_SHORT_TEXT, ComplicationData.TYPE_ICON}
    };

    @Override
    public Engine onCreateEngine() {
        return new Engine();
    }

    private static class EngineHandler extends Handler {
        private final WeakReference<SunshineWatchFace.Engine> mWeakReference;

        EngineHandler(SunshineWatchFace.Engine reference) {
            mWeakReference = new WeakReference<>(reference);
        }

        @Override
        public void handleMessage(Message msg) {
            SunshineWatchFace.Engine engine = mWeakReference.get();
            if (engine != null) {
                switch (msg.what) {
                    case MSG_UPDATE_TIME:
                        engine.handleUpdateTimeMessage();
                        break;
                }
            }
        }
    }

    private class Engine extends CanvasWatchFaceService.Engine {
        private float mTimeXOffset;
        private float mTimeYOffset;
        private int mComplicationX;
        private int mComplicationY;
        private int mSurfaceWidth;
        private int mSurfaceHeight;
        private boolean mIsRound;
        private boolean mAmbient;
        private boolean mLowBitAmbient;
        private boolean mRegisteredTimeZoneReceiver;
        private Paint mBackgroundPaint;
        private Paint mTimePaint;
        private Paint mComplicationsPaint;
        private SparseArray<ComplicationData> mActiveComplicationDataSparseArray;
        private Calendar mCalendar;
        private final Handler mUpdateTimeHandler = new EngineHandler(this);
        private final BroadcastReceiver mTimeZoneReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                mCalendar.setTimeZone(TimeZone.getDefault());
                invalidate();
            }
        };

        @Override
        public void onCreate(SurfaceHolder holder) {
            super.onCreate(holder);
            setWatchFaceStyle(new WatchFaceStyle.Builder(SunshineWatchFace.this).build());
            mRegisteredTimeZoneReceiver = false;
            mBackgroundPaint = new Paint();
            mBackgroundPaint.setColor(ContextCompat.getColor(getApplicationContext(), R.color.background));
            mTimePaint = new Paint();
            mTimePaint = createTimePaint(ContextCompat.getColor(getApplicationContext(), R.color.digital_text));
            mCalendar = Calendar.getInstance();
            initialiseComplications();
        }

        private void initialiseComplications() {
            mActiveComplicationDataSparseArray = new SparseArray<>(COMPLICATION_IDS.length);
            mComplicationsPaint = new Paint();
            mComplicationsPaint = createComplicationsPaint(Color.WHITE);
            setActiveComplications(COMPLICATION_IDS);
        }

        private Paint createTimePaint(int textColor) {
            Paint timePaint = new Paint();
            timePaint.setColor(textColor);
            timePaint.setTypeface(Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL));
            timePaint.setAntiAlias(true);
            return timePaint;
        }

        private Paint createComplicationsPaint(int textColor) {
            Paint complicationsPaint = new Paint();
            complicationsPaint.setColor(textColor);
            complicationsPaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
            complicationsPaint.setAntiAlias(true);
            return complicationsPaint;
        }

        @Override
        public void onComplicationDataUpdate(int complicationId, ComplicationData complicationData) {
            mActiveComplicationDataSparseArray.put(complicationId, complicationData);
            invalidate();
        }

        @Override
        public void onSurfaceChanged(SurfaceHolder holder, int format, int width, int height) {
            super.onSurfaceChanged(holder, format, width, height);
            mSurfaceWidth = width;
            mSurfaceHeight = height;
        }

        @Override
        public void onAmbientModeChanged(boolean inAmbientMode) {
            super.onAmbientModeChanged(inAmbientMode);
            if (mAmbient != inAmbientMode) {
                mAmbient = inAmbientMode;
                if (mLowBitAmbient) {
                    boolean antiAlias = !inAmbientMode;
                    mTimePaint.setAntiAlias(antiAlias);
                    mComplicationsPaint.setAntiAlias(antiAlias);
                }
                invalidate();
            }
            updateTimer();
        }

        @Override
        public void onPropertiesChanged(Bundle properties) {
            super.onPropertiesChanged(properties);
            mLowBitAmbient = properties.getBoolean(PROPERTY_LOW_BIT_AMBIENT, false);
        }

        @Override
        public void onApplyWindowInsets(WindowInsets insets) {
            super.onApplyWindowInsets(insets);
            Resources resources = SunshineWatchFace.this.getResources();
            mIsRound = insets.isRound();
            mTimeYOffset = resources.getDimension(mIsRound
                    ? R.dimen.digital_time_y_offset_round
                    : R.dimen.digital_time_y_offset);
            mTimeXOffset = resources.getDimension(mIsRound
                    ? R.dimen.digital_time_x_offset_round
                    : R.dimen.digital_time_x_offset);
            float timeTextSize = resources.getDimension(mIsRound
                    ? R.dimen.digital_time_text_size_round
                    : R.dimen.digital_time_text_size);
            mTimePaint.setTextSize(timeTextSize);
        }

        @Override
        public void onTimeTick() {
            super.onTimeTick();
            invalidate();
        }

        @Override
        public void onDraw(Canvas canvas, Rect bounds) {
            if (isInAmbientMode()) {
                canvas.drawColor(Color.BLACK);
            } else {
                canvas.drawRect(0, 0, bounds.width(), bounds.height(), mBackgroundPaint);
            }
            onDrawTime(canvas);
            onDrawComplications(canvas);
        }

        private void onDrawTime(Canvas canvas) {
            long now = System.currentTimeMillis();
            mCalendar.setTimeInMillis(now);
            String time = mAmbient
                    ? String.format(Locale.getDefault(), "%d:%02d", mCalendar.get(Calendar.HOUR),
                    mCalendar.get(Calendar.MINUTE))
                    : String.format(Locale.getDefault(), "%d:%02d:%02d", mCalendar.get(Calendar.HOUR),
                    mCalendar.get(Calendar.MINUTE), mCalendar.get(Calendar.SECOND));
            canvas.drawText(time, mTimeXOffset, mTimeYOffset, mTimePaint);
        }

        private void onDrawComplications(Canvas canvas) {
            for (int COMPLICATION_ID : COMPLICATION_IDS) {
                onDrawComplication(canvas, COMPLICATION_ID);
            }
        }

        private void onDrawComplication(Canvas canvas, int id) {
            ComplicationData complicationData = mActiveComplicationDataSparseArray.get(id);
            long now = System.currentTimeMillis();
            if ((complicationData != null) && (complicationData.isActive(now))) {
                switch (complicationData.getType()) {
                    case ComplicationData.TYPE_SHORT_TEXT:
                    case ComplicationData.TYPE_NO_PERMISSION:
                        onDrawShortTextComplication(canvas, id, complicationData);
                        break;
                    case ComplicationData.TYPE_ICON:
                        onDrawIconComplication(canvas, id, complicationData);
                        break;
                }
            }
        }

        private void onDrawShortTextComplication(Canvas canvas, int id, ComplicationData data) {
            CharSequence complicationMessage = getComplicationMessage(data);
            configureShortTextComplicationForDrawing(id, complicationMessage);
            Log.d(TAG, "onDrawShortTextComplication: Y: " + mComplicationY);
            canvas.drawText(complicationMessage, 0, complicationMessage.length(), mComplicationX,
                    mComplicationY, mComplicationsPaint);
        }

        private CharSequence getComplicationMessage(ComplicationData data) {
            ComplicationText mainText = data.getShortText();
            ComplicationText subText = data.getShortTitle();
            long now = System.currentTimeMillis();
            CharSequence complicationMessage = mainText.getText(getApplicationContext(), now);
            if (subText != null) {
                complicationMessage = TextUtils.concat(complicationMessage, " ",
                        subText.getText(getApplicationContext(), now));
            }
            return complicationMessage;
        }

        private void configureShortTextComplicationForDrawing(int id, CharSequence complicationMessage) {
            setComplicationTextSize(id);
            setComplicationY(id, false);
            setComplicationX(id, complicationMessage, null);
        }

        private void setComplicationTextSize(int id) {
            Resources resources = SunshineWatchFace.this.getResources();
            float complicationsTextSize = 0;
            switch (id) {
                case TOP_COMPLICATION:
                    complicationsTextSize = resources.getDimension(mIsRound
                            ? R.dimen.digital_top_complication_text_size_round
                            : R.dimen.digital_top_complication_text_size);
                    break;
                case LEFT_COMPLICATION:
                case MIDDLE_COMPLICATION:
                case RIGHT_COMPLICATION:
                    complicationsTextSize = resources.getDimension(mIsRound
                            ? R.dimen.digital_dial_complication_text_size_round
                            : R.dimen.digital_dial_complication_text_size);
                    break;
            }
            mComplicationsPaint.setTextSize(complicationsTextSize);
        }

        private void setComplicationY(int id, boolean isIcon) {
            switch (id) {
                case TOP_COMPLICATION:
                    mComplicationY = (int) ((mSurfaceHeight / 2)
                            + (mComplicationsPaint.getTextSize() / 2));
                    break;
                case LEFT_COMPLICATION:
                case MIDDLE_COMPLICATION:
                case RIGHT_COMPLICATION:
                    mComplicationY = isIcon
                            ? ((mSurfaceHeight / 3)) * 2
                            : ((mSurfaceHeight / 3)) * 2 + (int) mComplicationsPaint.getTextSize();
                    break;
            }
        }

        private void setComplicationX(int id, @Nullable CharSequence complicationMessage,
                                      @Nullable Bitmap bitmap) {
            if (complicationMessage != null) {
                double textWidth = mComplicationsPaint.measureText(complicationMessage, 0,
                        complicationMessage.length());
                int offset;
                switch (id) {
                    case TOP_COMPLICATION:
                        mComplicationX = (int) (mSurfaceWidth - textWidth) / 2;
                        break;
                    case LEFT_COMPLICATION:
                        mComplicationX = (int) ((mSurfaceWidth / 3) - textWidth) / 2;
                        break;
                    case MIDDLE_COMPLICATION:
                        offset = (int) ((mSurfaceWidth / 3) - textWidth) / 2;
                        mComplicationX = (mSurfaceWidth / 3) + offset;
                        break;
                    case RIGHT_COMPLICATION:
                        offset = (int) ((mSurfaceWidth / 3) - textWidth) / 2;
                        mComplicationX = (mSurfaceWidth / 3 * 2) + offset;
                        break;
                }
            }
            if (bitmap != null) {
                int offset;
                switch (id) {
                    case LEFT_COMPLICATION:
                        mComplicationX = ((mSurfaceWidth / 3) - bitmap.getWidth()) / 2;
                        break;
                    case MIDDLE_COMPLICATION:
                        offset = ((mSurfaceWidth / 3) - bitmap.getWidth()) / 2;
                        mComplicationX = (mSurfaceWidth / 3) + offset;
                        break;
                    case RIGHT_COMPLICATION:
                        offset = ((mSurfaceWidth / 3) - bitmap.getWidth()) / 2;
                        mComplicationX = (mSurfaceWidth / 3 * 2) + offset;
                        break;
                }
            }
        }

        private void onDrawIconComplication(Canvas canvas, int id, ComplicationData data) {
            Bitmap icon = getComplicationIcon(data);
            if (icon != null) {
                configureIconComplicationForDrawing(id, icon);
                Log.d(TAG, "onDrawIconComplication: Y: " + mComplicationY);
                canvas.drawBitmap(icon, mComplicationX, mComplicationY, null);
            }
        }

        @Nullable
        private Bitmap getComplicationIcon(ComplicationData data) {
            Icon icon = data.getIcon();
            Drawable drawable = icon.loadDrawable(getApplicationContext());
            if (drawable instanceof BitmapDrawable) {
                return ((BitmapDrawable) drawable).getBitmap();
            } else {
                return null;
            }
        }

        private void configureIconComplicationForDrawing(int id, Bitmap bitmap) {
            setComplicationY(id, true);
            setComplicationX(id, null, bitmap);
        }

        @Override
        public void onVisibilityChanged(boolean visible) {
            super.onVisibilityChanged(visible);
            if (visible) {
                registerReceiver();
                mCalendar.setTimeZone(TimeZone.getDefault());
                invalidate();
            } else {
                unregisterReceiver();
            }
            updateTimer();
        }

        private void registerReceiver() {
            if (mRegisteredTimeZoneReceiver) {
                return;
            }
            mRegisteredTimeZoneReceiver = true;
            IntentFilter filter = new IntentFilter(Intent.ACTION_TIMEZONE_CHANGED);
            SunshineWatchFace.this.registerReceiver(mTimeZoneReceiver, filter);
        }

        private void unregisterReceiver() {
            if (!mRegisteredTimeZoneReceiver) {
                return;
            }
            mRegisteredTimeZoneReceiver = false;
            SunshineWatchFace.this.unregisterReceiver(mTimeZoneReceiver);
        }

        /**
         * Starts the {@link #mUpdateTimeHandler} timer if it should be running and isn't currently
         * or stops it if it shouldn't be running but currently is.
         */
        private void updateTimer() {
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME);
            if (shouldTimerBeRunning()) {
                mUpdateTimeHandler.sendEmptyMessage(MSG_UPDATE_TIME);
            }
        }

        /**
         * Returns whether the {@link #mUpdateTimeHandler} timer should be running. The timer should
         * only run when we're visible and in interactive mode.
         */
        private boolean shouldTimerBeRunning() {
            return isVisible() && !isInAmbientMode();
        }

        /**
         * Handle updating the time periodically in interactive mode.
         */
        private void handleUpdateTimeMessage() {
            invalidate();
            if (shouldTimerBeRunning()) {
                long timeMs = System.currentTimeMillis();
                long delayMs = INTERACTIVE_UPDATE_RATE_MS - (timeMs % INTERACTIVE_UPDATE_RATE_MS);
                mUpdateTimeHandler.sendEmptyMessageDelayed(MSG_UPDATE_TIME, delayMs);
            }
        }

        @Override
        public void onDestroy() {
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME);
            super.onDestroy();
        }
    }
}
