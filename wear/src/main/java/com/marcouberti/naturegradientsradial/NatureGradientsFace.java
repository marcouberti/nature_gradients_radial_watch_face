package com.marcouberti.naturegradientsradial;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Rect;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.wearable.watchface.CanvasWatchFaceService;
import android.support.wearable.watchface.WatchFaceService;
import android.support.wearable.watchface.WatchFaceStyle;
import android.text.format.Time;
import android.util.Log;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.WindowInsets;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.wearable.DataApi;
import com.google.android.gms.wearable.DataEvent;
import com.google.android.gms.wearable.DataEventBuffer;
import com.google.android.gms.wearable.DataItem;
import com.google.android.gms.wearable.DataMap;
import com.google.android.gms.wearable.DataMapItem;
import com.google.android.gms.wearable.Wearable;
import com.marcouberti.naturegradientsradial.utils.ScreenUtils;

import java.lang.ref.WeakReference;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;
import java.util.TimeZone;

/**
 * Analog watch face with a ticking second hand. In ambient mode, the second hand isn't shown. On
 * devices with low-bit ambient mode, the hands are drawn without anti-aliasing in ambient mode.
 */
public class NatureGradientsFace extends CanvasWatchFaceService {

    private static final String TAG = "NatureGradientsFace";

    private static final Typeface NORMAL_TYPEFACE =
            Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD);
    /**
     * Update rate in milliseconds for interactive mode. We update once a second to advance the
     * second hand.
     */
    private static final long INTERACTIVE_UPDATE_RATE_MS = 1000;

    /**
     * Handler message id for updating the time periodically in interactive mode.
     */
    private static final int MSG_UPDATE_TIME = 0;

    Shader shader;
    int selectedGradient;

    @Override
    public Engine onCreateEngine() {
        return new Engine();
    }

    private class Engine extends CanvasWatchFaceService.Engine implements DataApi.DataListener,
            GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener{
        Paint mBackgroundPaint;
        Paint mHandPaint;
        Paint mDatePaint;
        Paint mSecondsCirclePaint;
        boolean mAmbient;
        Time mTime;
        boolean mIsRound =false;

        final Handler mUpdateTimeHandler = new EngineHandler(this);

        GoogleApiClient mGoogleApiClient = new GoogleApiClient.Builder(NatureGradientsFace.this)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .addApi(Wearable.API)
                .build();

        final BroadcastReceiver mTimeZoneReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                mTime.clear(intent.getStringExtra("time-zone"));
                mTime.setToNow();
            }
        };
        boolean mRegisteredTimeZoneReceiver = false;

        /**
         * Whether the display supports fewer bits for each color in ambient mode. When true, we
         * disable anti-aliasing in ambient mode.
         */
        boolean mLowBitAmbient;

        @Override
        public void onApplyWindowInsets(WindowInsets insets) {
            super.onApplyWindowInsets(insets);
            mIsRound = insets.isRound();
            if(mIsRound) {
                mHandPaint.setTextSize(getResources().getDimension(R.dimen.font_size_time_round));
            }else{
                mHandPaint.setTextSize(getResources().getDimension(R.dimen.font_size_time_square));
            }
        }

        int INFO_DETAILS_MODE = 0;
        @Override
        public void onTapCommand(@TapType int tapType, int x, int y, long eventTime) {
            switch (tapType) {
                case WatchFaceService.TAP_TYPE_TAP:
                    //switch between date infos
                    if(INFO_DETAILS_MODE == 0) INFO_DETAILS_MODE =1;
                    else INFO_DETAILS_MODE = 0;
                    invalidate();
                    break;

                case WatchFaceService.TAP_TYPE_TOUCH:
                    break;
                case WatchFaceService.TAP_TYPE_TOUCH_CANCEL:
                    break;

                default:
                    super.onTapCommand(tapType, x, y, eventTime);
                    break;
            }
        }

        @Override
        public void onCreate(SurfaceHolder holder) {
            super.onCreate(holder);

            setWatchFaceStyle(new WatchFaceStyle.Builder(NatureGradientsFace.this)
                    .setAcceptsTapEvents(true)
                    .setCardPeekMode(WatchFaceStyle.PEEK_MODE_SHORT)
                    .setBackgroundVisibility(WatchFaceStyle.BACKGROUND_VISIBILITY_INTERRUPTIVE)
                    .setShowSystemUiTime(false).
                    setViewProtectionMode(WatchFaceStyle.PROTECT_STATUS_BAR)
                    .build());

            Resources resources = NatureGradientsFace.this.getResources();

            mBackgroundPaint = new Paint();
            mBackgroundPaint.setAntiAlias(true);
            mBackgroundPaint.setColor(resources.getColor(R.color.time_date_color));

            mSecondsCirclePaint= new Paint();
            mSecondsCirclePaint.setAntiAlias(true);
            mSecondsCirclePaint.setStyle(Paint.Style.FILL_AND_STROKE);
            mSecondsCirclePaint.setColor(Color.WHITE);
            mSecondsCirclePaint.setStrokeWidth(4);

            mHandPaint = new Paint();
            mHandPaint.setColor(resources.getColor(R.color.time_date_color));
            mHandPaint.setAntiAlias(true);
            mHandPaint.setStrokeCap(Paint.Cap.BUTT);
            mHandPaint.setTypeface(Typeface.createFromAsset(getApplicationContext().getAssets(), "fonts/Dolce Vita Light.ttf"));
            mHandPaint.setTextSize(getResources().getDimension(R.dimen.font_size_time_round));

            mDatePaint = new Paint();
            mDatePaint.setColor(resources.getColor(R.color.time_date_color));
            mDatePaint.setAntiAlias(true);
            mDatePaint.setStrokeCap(Paint.Cap.BUTT);
            mDatePaint.setTypeface(NORMAL_TYPEFACE);
            mDatePaint.setTextSize(getResources().getDimension(R.dimen.font_size_date));
            mDatePaint.setTypeface(Typeface.createFromAsset(getApplicationContext().getAssets(), "fonts/Dolce Vita Light.ttf"));

            mTime = new Time();
        }

        @Override
        public void onDestroy() {
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME);
            super.onDestroy();
        }

        @Override
        public void onPropertiesChanged(Bundle properties) {
            super.onPropertiesChanged(properties);
            mLowBitAmbient = properties.getBoolean(PROPERTY_LOW_BIT_AMBIENT, false);
        }

        @Override
        public void onTimeTick() {
            super.onTimeTick();
            invalidate();
        }

        @Override
        public void onAmbientModeChanged(boolean inAmbientMode) {
            super.onAmbientModeChanged(inAmbientMode);
            if (mAmbient != inAmbientMode) {
                mAmbient = inAmbientMode;
                if (mLowBitAmbient) {
                    mHandPaint.setAntiAlias(!inAmbientMode);
                }
                invalidate();
            }

            // Whether the timer should be running depends on whether we're visible (as well as
            // whether we're in ambient mode), so we may need to start or stop the timer.
            updateTimer();
        }

        @Override
        public void onDraw(Canvas canvas, Rect bounds) {
            mTime.setToNow();

            int width = bounds.width();
            int height = bounds.height();
            float secRot = mTime.second / 30f * (float) Math.PI;

            // Draw the background.
            Resources resources = NatureGradientsFace.this.getResources();

            //Only one time init
            if(shader == null) {
                int[] rainbow = GradientsUtils.getGradients(getApplicationContext(),selectedGradient);

                //shader = new LinearGradient(0, 0, 0, bounds.width(), rainbow,
                  //      null, Shader.TileMode.MIRROR);

                shader = new RadialGradient(width/2f, height/2f,height/1.5f, rainbow[0], rainbow[1], Shader.TileMode.MIRROR);

                //Matrix matrix = new Matrix();
                //matrix.setRotate(180);
                //shader.setLocalMatrix(matrix);

                mBackgroundPaint.setShader(shader);
            }

            //AMBIET MODE
            if (!mAmbient) {
                //BACKGROUND
                canvas.drawRect(0, 0, canvas.getWidth(), canvas.getHeight(), mBackgroundPaint);
                canvas.save();
                canvas.rotate((float) (Math.toDegrees(secRot)), width / 2, width / 2);
                canvas.drawCircle(width / 2, ScreenUtils.convertDpToPixels(getApplicationContext(),15), ScreenUtils.convertDpToPixels(getApplicationContext(),5), mSecondsCirclePaint);
                canvas.restore();
            }else {
                //BLACK BG TO SAVE ENERGY
                canvas.drawColor(Color.BLACK);
            }

            String TIME_FORMAT = "hh:mm";
            if(android.text.format.DateFormat.is24HourFormat(getApplicationContext())) {
                TIME_FORMAT = "HH:mm";
            }

            //TIME TEXT
            Rect fontBounds = new Rect();
            String word  = new SimpleDateFormat(TIME_FORMAT).format(Calendar.getInstance().getTime());
            //remove first 0
            if(word.startsWith("0")){
                word = word.substring(1);
            }
            mHandPaint.getTextBounds(word, 0, word.length(), fontBounds);

            float textWidth = mHandPaint.measureText(word);

            //int left = (int)((double)width/(double)2 - (double)fontBounds.width()/(double)2);
            //int top = (int)((double)height/2 + (double)(fontBounds.height()/2));
            int left = (int)(width/2-textWidth/2);
            int top = (int)(height/2+fontBounds.height()/2);
            canvas.drawText(word, left, top, mHandPaint);

            //compute the height of an A
            Rect singleCharBounds = new Rect();
            mDatePaint.getTextBounds("A", 0, "A".length(), singleCharBounds);
            int A_HEIGHT = singleCharBounds.height();

            //MODE DAY WEEK AND DATE
            if(INFO_DETAILS_MODE == 0) {
                //WEEK DAY AND DAY OF MONTH
                String weekDay = new SimpleDateFormat("EEEE d").format(Calendar.getInstance().getTime()).toUpperCase();

                Rect dateBounds = new Rect();
                String format = weekDay.toUpperCase();
                mDatePaint.getTextBounds(format, 0, format.length(), dateBounds);

                int dateLeft = (int) ((double) width / (double) 2 - (double) dateBounds.width() / (double) 2);
                canvas.drawText(format, dateLeft, top + A_HEIGHT * 2, mDatePaint);
            } else {
                //int weekOfYear = Calendar.getInstance().get(Calendar.WEEK_OF_YEAR);
                //DATE TEXT
                Rect dateBounds = new Rect();
                Locale current = getResources().getConfiguration().locale;
                DateFormat formatter = DateFormat.getDateInstance(DateFormat.LONG, current);
                //String pattern       = ((SimpleDateFormat)formatter).toPattern();
                String localPattern = ((SimpleDateFormat) formatter).toLocalizedPattern();
                String format = new SimpleDateFormat(localPattern).format(Calendar.getInstance().getTime()).toUpperCase();
                mDatePaint.getTextBounds(format, 0, format.length(), dateBounds);

                int dateLeft = (int) ((double) width / (double) 2 - (double) dateBounds.width() / (double) 2);
                canvas.drawText(format, dateLeft, top + A_HEIGHT * 2, mDatePaint);
            }
        }

        @Override
        public void onVisibilityChanged(boolean visible) {
            super.onVisibilityChanged(visible);

            if (visible) {
                mGoogleApiClient.connect();
                registerReceiver();

                // Update time zone in case it changed while we weren't visible.
                mTime.clear(TimeZone.getDefault().getID());
                mTime.setToNow();
            } else {
                unregisterReceiver();
                if (mGoogleApiClient != null && mGoogleApiClient.isConnected()) {
                    Wearable.DataApi.removeListener(mGoogleApiClient, this);
                    mGoogleApiClient.disconnect();
                }
            }

            // Whether the timer should be running depends on whether we're visible (as well as
            // whether we're in ambient mode), so we may need to start or stop the timer.
            updateTimer();
        }

        private void registerReceiver() {
            if (mRegisteredTimeZoneReceiver) {
                return;
            }
            mRegisteredTimeZoneReceiver = true;
            IntentFilter filter = new IntentFilter(Intent.ACTION_TIMEZONE_CHANGED);
            NatureGradientsFace.this.registerReceiver(mTimeZoneReceiver, filter);
        }

        private void unregisterReceiver() {
            if (!mRegisteredTimeZoneReceiver) {
                return;
            }
            mRegisteredTimeZoneReceiver = false;
            NatureGradientsFace.this.unregisterReceiver(mTimeZoneReceiver);
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
                long delayMs = INTERACTIVE_UPDATE_RATE_MS
                        - (timeMs % INTERACTIVE_UPDATE_RATE_MS);
                mUpdateTimeHandler.sendEmptyMessageDelayed(MSG_UPDATE_TIME, delayMs);
            }
        }


        private void updateConfigDataItemAndUiOnStartup() {
            NatureGradientsWatchFaceUtil.fetchConfigDataMap(mGoogleApiClient,
                    new NatureGradientsWatchFaceUtil.FetchConfigDataMapCallback() {
                        @Override
                        public void onConfigDataMapFetched(DataMap startupConfig) {
                            // If the DataItem hasn't been created yet or some keys are missing,
                            // use the default values.
                            setDefaultValuesForMissingConfigKeys(startupConfig);
                            NatureGradientsWatchFaceUtil.putConfigDataItem(mGoogleApiClient, startupConfig);

                            updateUiForConfigDataMap(startupConfig);
                        }
                    }
            );
        }

        private void setDefaultValuesForMissingConfigKeys(DataMap config) {
            addIntKeyIfMissing(config, NatureGradientsWatchFaceUtil.KEY_BACKGROUND_COLOR,
                    NatureGradientsWatchFaceUtil.COLOR_VALUE_DEFAULT_AND_AMBIENT_BACKGROUND);
        }

        private void addIntKeyIfMissing(DataMap config, String key, int color) {
            if (!config.containsKey(key)) {
                config.putInt(key, color);
            }
        }

        @Override // DataApi.DataListener
        public void onDataChanged(DataEventBuffer dataEvents) {
            for (DataEvent dataEvent : dataEvents) {
                if (dataEvent.getType() != DataEvent.TYPE_CHANGED) {
                    continue;
                }

                DataItem dataItem = dataEvent.getDataItem();
                if (!dataItem.getUri().getPath().equals(
                        NatureGradientsWatchFaceUtil.PATH_WITH_FEATURE)) {
                    continue;
                }

                DataMapItem dataMapItem = DataMapItem.fromDataItem(dataItem);
                DataMap config = dataMapItem.getDataMap();
                if (Log.isLoggable(TAG, Log.DEBUG)) {
                    Log.d(TAG, "Config DataItem updated:" + config);
                }
                updateUiForConfigDataMap(config);
            }
        }

        private void updateUiForConfigDataMap(final DataMap config) {
            boolean uiUpdated = false;
            for (String configKey : config.keySet()) {
                if (!config.containsKey(configKey)) {
                    continue;
                }
                int color = config.getInt(configKey);
                if (Log.isLoggable(TAG, Log.DEBUG)) {
                    Log.d(TAG, "Found watch face config key: " + configKey + " -> "
                            + color);
                }
                if (updateUiForKey(configKey, color)) {
                    uiUpdated = true;
                }
            }
            if (uiUpdated) {
                invalidate();
            }
        }

        /**
         * Updates the color of a UI item according to the given {@code configKey}. Does nothing if
         * {@code configKey} isn't recognized.
         *
         * @return whether UI has been updated
         */
        private boolean updateUiForKey(String configKey, int color) {
            if (configKey.equals(NatureGradientsWatchFaceUtil.KEY_BACKGROUND_COLOR)) {
                setGradient(color);
            } else {
                Log.w(TAG, "Ignoring unknown config key: " + configKey);
                return false;
            }
            return true;
        }

        private void setGradient(int color) {
            Log.d("color=",color+"");
            shader = null;
            selectedGradient = color;
        }

        @Override  // GoogleApiClient.ConnectionCallbacks
        public void onConnected(Bundle connectionHint) {
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "onConnected: " + connectionHint);
            }
            Wearable.DataApi.addListener(mGoogleApiClient, Engine.this);
            updateConfigDataItemAndUiOnStartup();
        }

        @Override  // GoogleApiClient.ConnectionCallbacks
        public void onConnectionSuspended(int cause) {
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "onConnectionSuspended: " + cause);
            }
        }

        @Override  // GoogleApiClient.OnConnectionFailedListener
        public void onConnectionFailed(ConnectionResult result) {
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "onConnectionFailed: " + result);
            }
        }
    }

    private static class EngineHandler extends Handler {
        private final WeakReference<NatureGradientsFace.Engine> mWeakReference;

        public EngineHandler(NatureGradientsFace.Engine reference) {
            mWeakReference = new WeakReference<>(reference);
        }

        @Override
        public void handleMessage(Message msg) {
            NatureGradientsFace.Engine engine = mWeakReference.get();
            if (engine != null) {
                switch (msg.what) {
                    case MSG_UPDATE_TIME:
                        engine.handleUpdateTimeMessage();
                        break;
                }
            }
        }
    }

}
