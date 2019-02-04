package com.example.dmitrischamrin.rogue;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.ComposeShader;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.PorterDuff;
import android.graphics.RadialGradient;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.SweepGradient;
import android.graphics.drawable.ShapeDrawable;
import android.os.BatteryManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.v7.graphics.Palette;
import android.support.wearable.complications.ComplicationData;
import android.support.wearable.complications.rendering.ComplicationDrawable;
import android.support.wearable.watchface.CanvasWatchFaceService;
import android.support.wearable.watchface.WatchFaceService;
import android.support.wearable.watchface.WatchFaceStyle;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.SparseArray;
import android.view.Display;
import android.view.SurfaceHolder;
import android.view.WindowManager;
import android.widget.Toast;

import java.lang.ref.WeakReference;
import java.util.Calendar;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;


/**
 * Analog watch face with a ticking second hand. In ambient mode, the second hand isn't
 * shown. On devices with low-bit ambient mode, the hands are drawn without anti-aliasing in ambient
 * mode. The watch face is drawn with less contrast in mute mode.
 * <p>
 * Important Note: Because watch face apps do not have a default Activity in
 * their project, you will need to set your Configurations to
 * "Do not launch Activity" for both the Wear and/or Application modules. If you
 * are unsure how to do this, please review the "Run Starter project" section
 * in the Google Watch Face Code Lab:
 * https://codelabs.developers.google.com/codelabs/watchface/index.html#0
 */
public class Rogue extends CanvasWatchFaceService {

    private static final String TAG = "AnalogWatchFace";

    // Unique IDs for each complication. The settings activity that supports allowing users
    // to select their complication data provider requires numbers to be >= 0.
    private static final int BACKGROUND_COMPLICATION_ID = 0;

    //    private static final int LEFT_COMPLICATION_ID = 100;
    private static final int RIGHT_COMPLICATION_ID = 101;
    // Background, Left and right complication IDs as array for Complication API.
    private static final int[] COMPLICATION_IDS = {
            BACKGROUND_COMPLICATION_ID, /*LEFT_COMPLICATION_ID, */RIGHT_COMPLICATION_ID
    };

    // Left and right dial supported types.
    private static final int[][] COMPLICATION_SUPPORTED_TYPES = {
            {ComplicationData.TYPE_LARGE_IMAGE},
            {
                    ComplicationData.TYPE_RANGED_VALUE,
                    ComplicationData.TYPE_ICON,
                    ComplicationData.TYPE_SHORT_TEXT,
                    ComplicationData.TYPE_SMALL_IMAGE
            },
            {
                    ComplicationData.TYPE_RANGED_VALUE,
                    ComplicationData.TYPE_ICON,
                    ComplicationData.TYPE_SHORT_TEXT,
                    ComplicationData.TYPE_SMALL_IMAGE
            }
    };

    // Used by {@link AnalogComplicationConfigRecyclerViewAdapter} to check if complication location
    // is supported in settings config activity.
    public static int getComplicationId(
            RogueComplicationConfigRecyclerViewAdapter.ComplicationLocation complicationLocation) {
        // Add any other supported locations here.
        switch (complicationLocation) {
            case BACKGROUND:
                return BACKGROUND_COMPLICATION_ID;
//            case LEFT:
//                return LEFT_COMPLICATION_ID;
            case RIGHT:
                return RIGHT_COMPLICATION_ID;
            default:
                return -1;
        }
    }

    // Used by {@link AnalogComplicationConfigRecyclerViewAdapter} to retrieve all complication ids.
    public static int[] getComplicationIds() {
        return COMPLICATION_IDS;
    }

    // Used by {@link AnalogComplicationConfigRecyclerViewAdapter} to see which complication types
    // are supported in the settings config activity.
    public static int[] getSupportedComplicationTypes(
            RogueComplicationConfigRecyclerViewAdapter.ComplicationLocation complicationLocation) {
        // Add any other supported locations here.
        switch (complicationLocation) {
            case BACKGROUND:
                return COMPLICATION_SUPPORTED_TYPES[0];
            case LEFT:
                return COMPLICATION_SUPPORTED_TYPES[1];
            case RIGHT:
                return COMPLICATION_SUPPORTED_TYPES[2];
            default:
                return new int[] {};
        }
    }


    /*
     * Updates rate in milliseconds for interactive mode. We update once a second to advance the
     * second hand.
     */
    private static final long INTERACTIVE_UPDATE_RATE_MS = TimeUnit.SECONDS.toMillis(1);

    /**
     * Handler message id for updating the time periodically in interactive mode.
     */
    private static final int MSG_UPDATE_TIME = 0;

    @Override
    public Engine onCreateEngine() {
        return new Engine();
    }

    private static class EngineHandler extends Handler {
        private final WeakReference<Rogue.Engine> mWeakReference;

        public EngineHandler(Rogue.Engine reference) {
            mWeakReference = new WeakReference<>(reference);
        }

        @Override
        public void handleMessage(Message msg) {
            Rogue.Engine engine = mWeakReference.get();
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

        /* Handler to update the time once a second in interactive mode. */
        private final Handler mUpdateTimeHandler = new EngineHandler(this);
        private Calendar mCalendar;
        private final BroadcastReceiver mTimeZoneReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                mCalendar.setTimeZone(TimeZone.getDefault());
                invalidate();
            }
        };
        private boolean mRegisteredTimeZoneReceiver = false;
        private boolean mMuteMode;
        private float mCenterX;
        private float mCenterY;



        private int mBackgroundColor;
        private int mRogueColor;
        private Paint mArcMinutesPaint;
        private Paint mHelperLines;
        private Paint mArcRoughMinutes;
        private Paint mArcAmPmPaint;
        private Paint mArcHoursPaint;
        private Paint mArcBatteryLevel;
        private Paint mArcGradientTest;

        private Paint mBackgroundPaint;
        /* Maps active complication ids to the data for that complication. Note: Data will only be
         * present if the user has chosen a provider via the settings activity for the watch face.
         */
        private SparseArray<ComplicationData> mActiveComplicationDataSparseArray;

        /* Maps complication ids to corresponding ComplicationDrawable that renders the
         * the complication data on the watch face.
         */
        private SparseArray<ComplicationDrawable> mComplicationDrawableSparseArray;

        private boolean mAmbient;
        private boolean mLowBitAmbient;
        private boolean mBurnInProtection;

        SharedPreferences mSharedPref;
        private boolean mUnreadNotificationsPreference;
        private int mNumberOfUnreadNotifications = 0;


        @Override
        public void onCreate(SurfaceHolder holder) {
            Log.d(TAG, "onCreate");

            super.onCreate(holder);




            // Used throughout watch face to pull user's preferences.
            Context context = getApplicationContext();
            mSharedPref =
                    context.getSharedPreferences(
                            getString(R.string.analog_complication_preference_file_key),
                            Context.MODE_PRIVATE);

            mCalendar = Calendar.getInstance();

            setWatchFaceStyle(
                    new WatchFaceStyle.Builder(Rogue.this)
                            .setAcceptsTapEvents(true)
                            .setHideNotificationIndicator(true)
                            .build());

            loadSavedPreferences();
            initializeComplicationsAndBackground();
            initializeWatchFace();
        }

        private void initializeBackground() {
            mBackgroundPaint = new Paint();
            mBackgroundPaint.setColor(Color.BLACK);

        }

        // Pulls all user's preferences for watch face appearance.
        private void loadSavedPreferences() {

            String backgroundColorResourceName =
                    getApplicationContext().getString(R.string.saved_background_color);

            mBackgroundColor = mSharedPref.getInt(backgroundColorResourceName, Color.BLACK);

            String markerColorResourceName =
                    getApplicationContext().getString(R.string.saved_marker_color);

            // Set defaults for colors
            mRogueColor = mSharedPref.getInt(markerColorResourceName, Color.rgb(112,253,16));



            String unreadNotificationPreferenceResourceName =
                    getApplicationContext().getString(R.string.saved_unread_notifications_pref);

            mUnreadNotificationsPreference =
                    mSharedPref.getBoolean(unreadNotificationPreferenceResourceName, true);
        }

        private void initializeComplicationsAndBackground() {
            Log.d(TAG, "initializeComplications()");


            // Initialize background color (in case background complication is inactive).
            mBackgroundPaint = new Paint();
            mBackgroundPaint.setColor(mBackgroundColor);

            mActiveComplicationDataSparseArray = new SparseArray<>(COMPLICATION_IDS.length);

            // Creates a ComplicationDrawable for each location where the user can render a
            // complication on the watch face. In this watch face, we create one for left, right,
            // and background, but you could add many more.
            ComplicationDrawable leftComplicationDrawable =
                    new ComplicationDrawable(getApplicationContext());

            ComplicationDrawable rightComplicationDrawable =
                    new ComplicationDrawable(getApplicationContext());

            ComplicationDrawable backgroundComplicationDrawable =
                    new ComplicationDrawable(getApplicationContext());

            // Adds new complications to a SparseArray to simplify setting styles and ambient
            // properties for all complications, i.e., iterate over them all.
            mComplicationDrawableSparseArray = new SparseArray<>(COMPLICATION_IDS.length);

//            mComplicationDrawableSparseArray.put(LEFT_COMPLICATION_ID, leftComplicationDrawable);
            mComplicationDrawableSparseArray.put(RIGHT_COMPLICATION_ID, rightComplicationDrawable);
            mComplicationDrawableSparseArray.put(
                    BACKGROUND_COMPLICATION_ID, backgroundComplicationDrawable);

            setActiveComplications(COMPLICATION_IDS);
        }

        /* Sets active/ambient mode colors for all complications.
         *
         * Note: With the rest of the watch face, we update the paint colors based on
         * ambient/active mode callbacks, but because the ComplicationDrawable handles
         * the active/ambient colors, we only set the colors twice. Once at initialization and
         * again if the user changes the highlight color via AnalogComplicationConfigActivity.
         */


        private void setComplicationsActiveAndAmbientColors(int primaryComplicationColor) {
            int complicationId;
            ComplicationDrawable complicationDrawable;

            for (int i = 0; i < COMPLICATION_IDS.length; i++) {
                complicationId = COMPLICATION_IDS[i];
                complicationDrawable = mComplicationDrawableSparseArray.get(complicationId);

                if (complicationId == BACKGROUND_COMPLICATION_ID) {
                    // It helps for the background color to be black in case the image used for the
                    // watch face's background takes some time to load.
                    complicationDrawable.setBackgroundColorActive(Color.BLACK);
                } else {
                    // Active mode colors.
                    complicationDrawable.setBorderColorActive(mRogueColor);
                    complicationDrawable.setRangedValuePrimaryColorActive(mRogueColor);
                    complicationDrawable.setTextColorActive(mRogueColor);
                    complicationDrawable.setIconColorActive(mRogueColor);

                    // Ambient mode colors.
                    complicationDrawable.setBorderColorAmbient(Color.DKGRAY);
                    complicationDrawable.setIconColorAmbient(Color.DKGRAY);
                    complicationDrawable.setRangedValuePrimaryColorAmbient(Color.DKGRAY);
                    complicationDrawable.setTextColorAmbient(Color.DKGRAY);
                }
            }
        }

        private void initializeWatchFace() {
            /* Set defaults for colors */
            Context context = getApplicationContext();
            DisplayMetrics metrics = new DisplayMetrics();
            WindowManager windowManager = (WindowManager) context
                    .getSystemService(Context.WINDOW_SERVICE);
            windowManager.getDefaultDisplay().getMetrics(metrics);
            Display display = windowManager.getDefaultDisplay();
            Point size = new Point();
            display.getSize(size);
            int width = size.x;
            int height = size.y;


            mArcMinutesPaint = new Paint();
            mArcMinutesPaint.setColor(mRogueColor);
            mArcMinutesPaint.setStrokeWidth((width/2)*0.045f);
            mArcMinutesPaint.setStrokeCap(Paint.Cap.BUTT);
            mArcMinutesPaint.setStyle(Paint.Style.STROKE);
            mArcMinutesPaint.setAntiAlias(true);

            mArcHoursPaint = new Paint();
            mArcHoursPaint.setColor(mRogueColor);
            mArcHoursPaint.setStrokeWidth((width/2)*0.085f);
            mArcHoursPaint.setStrokeCap(Paint.Cap.BUTT);
            mArcHoursPaint.setStyle(Paint.Style.STROKE);
            mArcHoursPaint.setAntiAlias(true);

            mArcRoughMinutes = new Paint();
            mArcRoughMinutes.setColor(mRogueColor);
            mArcRoughMinutes.setStrokeWidth((width/2)*0.17f);
            mArcRoughMinutes.setStrokeCap(Paint.Cap.BUTT);
            mArcRoughMinutes.setStyle(Paint.Style.STROKE);
            mArcRoughMinutes.setAntiAlias(true);

            mArcAmPmPaint = new Paint();
            mArcAmPmPaint.setColor(mRogueColor);
            mArcAmPmPaint.setStrokeWidth((width/2)*0.022f);
            mArcAmPmPaint.setStrokeCap(Paint.Cap.BUTT);
            mArcAmPmPaint.setStyle(Paint.Style.STROKE);
            mArcAmPmPaint.setAntiAlias(true);



            mArcBatteryLevel = new Paint();
            mArcBatteryLevel.setColor(mRogueColor);
            mArcBatteryLevel.setStrokeWidth((width/2)*0.012f);
            mArcBatteryLevel.setStrokeCap(Paint.Cap.BUTT);
            mArcBatteryLevel.setStyle(Paint.Style.STROKE);
            mArcBatteryLevel.setAntiAlias(true);

            mHelperLines = new Paint();
            mHelperLines.setColor(Color.YELLOW);
            mHelperLines.setStrokeWidth(1f);
            mHelperLines.setStrokeCap(Paint.Cap.BUTT);
            mHelperLines.setStyle(Paint.Style.STROKE);
            mHelperLines.setAntiAlias(true);

            mArcGradientTest = new Paint();
            mArcGradientTest.setColor(Color.WHITE);
            mArcGradientTest.setStrokeWidth(2);
            mArcGradientTest.setAntiAlias(true);
            mArcGradientTest.setStrokeCap(Paint.Cap.BUTT);
            mArcGradientTest.setStyle(Paint.Style.STROKE);


        }

        @Override
        public void onDestroy() {
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME);
            super.onDestroy();
        }



        @Override
        public void onTimeTick() {
            super.onTimeTick();
            invalidate();
        }

        @Override
        public void onAmbientModeChanged(boolean inAmbientMode) {
            super.onAmbientModeChanged(inAmbientMode);
            mAmbient = inAmbientMode;

            updateWatchHandStyle();

            ComplicationDrawable complicationDrawable;

            for (int i = 0; i < COMPLICATION_IDS.length; i++) {
                complicationDrawable = mComplicationDrawableSparseArray.get(COMPLICATION_IDS[i]);
                complicationDrawable.setInAmbientMode(mAmbient);
            }

            /* Check and trigger whether or not timer should be running (only in active mode). */
            updateTimer();
        }

        private void updateWatchHandStyle() {
            if (mAmbient) {
                mArcAmPmPaint.setColor(Color.DKGRAY);
                mArcHoursPaint.setColor(Color.DKGRAY);
                mArcMinutesPaint.setColor(Color.DKGRAY);
                mArcRoughMinutes.setColor(Color.DKGRAY);
                mArcBatteryLevel.setColor(Color.DKGRAY);


                mBackgroundPaint.setColor(Color.BLACK);
                Shader gradient = new RadialGradient(mCenterX,mCenterY, mCenterX,Color.BLACK, Color.BLACK,Shader.TileMode.MIRROR);
                mBackgroundPaint.setShader(gradient);

            } else {

                int[] colours = new int[]{mRogueColor, manipulateColor(mRogueColor,0.3f),manipulateColor(mRogueColor,0.2f),Color.BLACK};
                float[] stops = new float[]{0f, 0.32f, 0.4f, 1f};
                mArcAmPmPaint.setColor(mRogueColor);
                mArcMinutesPaint.setColor(mRogueColor);
                mArcRoughMinutes.setColor(mRogueColor);
                mArcBatteryLevel.setColor(mRogueColor);
                mArcHoursPaint.setColor(mRogueColor);

                if(mUnreadNotificationsPreference){
                    Shader gradient = new RadialGradient(0, mCenterY*2, 350, colours, stops, Shader.TileMode.CLAMP);
                    Shader gradient2 = new RadialGradient(0, 0, 350, colours, stops, Shader.TileMode.CLAMP);
                    Shader gradient3 = new RadialGradient(mCenterX*2-30, -10f, 350, colours, stops, Shader.TileMode.CLAMP);
                    Shader gradient4 = new RadialGradient(mCenterX*2, mCenterY*2, 350, colours, stops, Shader.TileMode.CLAMP);
                    ComposeShader left = new ComposeShader(gradient,gradient2, PorterDuff.Mode.ADD);
                    ComposeShader right = new ComposeShader(gradient3,gradient4, PorterDuff.Mode.ADD);
                    ComposeShader sides = new ComposeShader(gradient,right, PorterDuff.Mode.ADD);
    //                ComposeShader total = new ComposeShader(sides,center, PorterDuff.Mode.ADD);
                    mBackgroundPaint.setShader(sides);
                }
                else{
                    Shader gradient = new RadialGradient(mCenterX,mCenterY, mCenterX*20,Color.BLACK, Color.BLACK,Shader.TileMode.MIRROR);
                    mBackgroundPaint.setShader(gradient);
                }


            }
        }



        @Override
        public void onPropertiesChanged(Bundle properties) {
            super.onPropertiesChanged(properties);
            Log.d(TAG, "onPropertiesChanged: low-bit ambient = " + mLowBitAmbient);

            mLowBitAmbient = properties.getBoolean(PROPERTY_LOW_BIT_AMBIENT, false);
            mBurnInProtection = properties.getBoolean(PROPERTY_BURN_IN_PROTECTION, false);

            // Updates complications to properly render in ambient mode based on the
            // screen's capabilities.
            ComplicationDrawable complicationDrawable;

            for (int i = 0; i < COMPLICATION_IDS.length; i++) {
                complicationDrawable = mComplicationDrawableSparseArray.get(COMPLICATION_IDS[i]);

                complicationDrawable.setLowBitAmbient(mLowBitAmbient);
                complicationDrawable.setBurnInProtection(mBurnInProtection);
            }
        }

        @Override
        public void onComplicationDataUpdate(
                int complicationId, ComplicationData complicationData) {
            Log.d(TAG, "onComplicationDataUpdate() id: " + complicationId);

            // Adds/updates active complication data in the array.
            mActiveComplicationDataSparseArray.put(complicationId, complicationData);

            // Updates correct ComplicationDrawable with updated data.
            ComplicationDrawable complicationDrawable =
                    mComplicationDrawableSparseArray.get(complicationId);
            complicationDrawable.setComplicationData(complicationData);

            invalidate();
        }

        @Override
        public void onInterruptionFilterChanged(int interruptionFilter) {
            super.onInterruptionFilterChanged(interruptionFilter);
            boolean inMuteMode = (interruptionFilter == WatchFaceService.INTERRUPTION_FILTER_NONE);

            /* Dim display in mute mode. */
            if (mMuteMode != inMuteMode) {
                mMuteMode = inMuteMode;
//                mHourPaint.setAlpha(inMuteMode ? 100 : 255);
//                mMinutePaint.setAlpha(inMuteMode ? 100 : 255);
//                mSecondPaint.setAlpha(inMuteMode ? 80 : 255);
                invalidate();
            }
        }

        @Override
        public void onSurfaceChanged(SurfaceHolder holder, int format, int width, int height) {
            super.onSurfaceChanged(holder, format, width, height);

            /*
             * Find the coordinates of the center point on the screen, and ignore the window
             * insets, so that, on round watches with a "chin", the watch face is centered on the
             * entire screen, not just the usable portion.
             */
            mCenterX = width / 2f;
            mCenterY = height / 2f;

            Rect rightBounds =
                    // Left, Top, Right, Bottom
                    new Rect(
                            ((int) mCenterX-60),
                            (int)mCenterY-60,
                            ((int)mCenterX + +60),
                            ((int)mCenterY + 60));

            ComplicationDrawable rightComplicationDrawable =
                    mComplicationDrawableSparseArray.get(RIGHT_COMPLICATION_ID);
            rightComplicationDrawable.setBounds(rightBounds);

            Rect screenForBackgroundBound =
                    // Left, Top, Right, Bottom
                    new Rect(0, 0, width, height);

            ComplicationDrawable backgroundComplicationDrawable =
                    mComplicationDrawableSparseArray.get(BACKGROUND_COMPLICATION_ID);
            backgroundComplicationDrawable.setBounds(screenForBackgroundBound);
        }



        /**
         * Captures tap event (and tap type). The {@link WatchFaceService#TAP_TYPE_TAP} case can be
         * used for implementing specific logic to handle the gesture.
         */
        @Override
        public void onTapCommand(int tapType, int x, int y, long eventTime) {
            switch (tapType) {
                case TAP_TYPE_TOUCH:
                    // The user has started touching the screen.
                    break;
                case TAP_TYPE_TOUCH_CANCEL:
                    // The user has started a different gesture or otherwise cancelled the tap.
                    break;
                case TAP_TYPE_TAP:
                    // The user has completed the tap gesture.
                    // TODO: Add code to handle the tap gesture.
                    for (int i = COMPLICATION_IDS.length - 1; i >= 0; i--) {
                        int complicationId = COMPLICATION_IDS[i];
                        ComplicationDrawable complicationDrawable =
                                mComplicationDrawableSparseArray.get(complicationId);

                        boolean successfulTap = complicationDrawable.onTap(x, y);

                        if (successfulTap) {
                            return;
                        }
                    }
                    break;
            }
            invalidate();
        }

        @Override
        public void onDraw(Canvas canvas, Rect bounds) {
            long now = System.currentTimeMillis();
            mCalendar.setTimeInMillis(now);

            drawBackground(canvas);
            drawComplications(canvas, now);
            drawWatchFace(canvas);
        }

        private void drawBackground(Canvas canvas) {
            canvas.drawPaint(mBackgroundPaint);
        }

        private void drawComplications(Canvas canvas, long currentTimeMillis) {
            int complicationId;
            ComplicationDrawable complicationDrawable;

            for (int i = 0; i < COMPLICATION_IDS.length; i++) {
                complicationId = COMPLICATION_IDS[i];
                complicationDrawable = mComplicationDrawableSparseArray.get(complicationId);

                complicationDrawable.draw(canvas, currentTimeMillis);
            }
        }
        public int manipulateColor(int color, float factor) {
            int a = Color.alpha(color);
            int r = Math.round(Color.red(color) * factor);
            int g = Math.round(Color.green(color) * factor);
            int b = Math.round(Color.blue(color) * factor);
            return Color.argb(a,
                    Math.min(r,255),
                    Math.min(g,255),
                    Math.min(b,255));
        }
        private void drawWatchFace(Canvas canvas) {

            mArcGradientTest.setStrokeWidth((mCenterX*0.045f));


            Context context = getApplicationContext();
            DisplayMetrics metrics = new DisplayMetrics();
            WindowManager windowManager = (WindowManager) context
                    .getSystemService(Context.WINDOW_SERVICE);
            windowManager.getDefaultDisplay().getMetrics(metrics);
            float hourScale = metrics.widthPixels*0.1975f;
            float batteryScale = metrics.widthPixels*0.295f;
            float amPmScale = metrics.widthPixels*0.33f;
            float minutesScale = metrics.widthPixels*0.474f;

//            float minutesTest = metrics.widthPixels*0.33f;


            RectF hourRect = new RectF(mCenterX-hourScale,mCenterY-hourScale,mCenterX+hourScale,mCenterY+hourScale);
            RectF batteryRect = new RectF(mCenterX-batteryScale,mCenterY-batteryScale,mCenterX+batteryScale,mCenterY+batteryScale);
            RectF amPmRect = new RectF(mCenterX-amPmScale,mCenterY-amPmScale,mCenterX+amPmScale,mCenterY+amPmScale);
            RectF minutesRect = new RectF(mCenterX-minutesScale,mCenterY-minutesScale,mCenterX+minutesScale,mCenterY+minutesScale);
//            RectF minutesRectTest = new RectF(mCenterX-minutesTest,mCenterY-minutesTest,mCenterX+minutesTest,mCenterY+minutesTest);
            IntentFilter iFilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
            Intent batteryStatus =  getApplicationContext().registerReceiver(null, iFilter);
            int level = batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);


            canvas.drawArc(batteryRect,90f, (level *3.6f),false, mArcBatteryLevel);
//            canvas.drawArc(minutesRectTest,90f, (level),false, mArcGradientTest);

            int spacingHelper =0;
            for(int i = 0; i<60; i++){

                if(i <= mCalendar.get(Calendar.MINUTE)){
                    if(i%5 == 0){
                        canvas.drawArc(minutesRect,i*6f - 91f,2f,false,mArcMinutesPaint);
                        spacingHelper = 0;
                    }
                    else{

                        switch(spacingHelper){
                            case 0:
                                canvas.drawArc(minutesRect,i*6f - 94.7f,6.5f,false,mArcMinutesPaint);
//                                canvas.drawArc(minutesRectTest,i*6f - 94.7f,6.5f,false,mArcGradientTest);
                                spacingHelper = 1;
                                break;
                            case 1:
                                canvas.drawArc(minutesRect,i*6f - 93.7f,6.5f,false,mArcMinutesPaint);
//                                canvas.drawArc(minutesRectTest,i*6f - 93.7f,6.5f,false,mArcGradientTest);
                                spacingHelper = 2;
                                break;
                            case 2:
                                canvas.drawArc(minutesRect,i*6f - 92.7f,6.5f,false,mArcMinutesPaint);
//                                canvas.drawArc(minutesRectTest,i*6f - 92.7f,6.5f,false,mArcGradientTest);
                                spacingHelper = 3;
                                break;
                            case 3:
                                canvas.drawArc(minutesRect,i*6f - 91.8f,6.5f,false,mArcMinutesPaint);
//                                canvas.drawArc(minutesRectTest,i*6f - 91.8f,6.5f,false,mArcGradientTest);
                                spacingHelper = 4;
                                break;
                        }
                    }
                }

            }

            for(int i = 0; i<12; i++){
                if(i != mCalendar.get(Calendar.HOUR)){

                    if(i%3 == 0){
                        canvas.drawArc(hourRect, i*30f - 110,40f,false,mArcHoursPaint);
//                        canvas.drawArc(minutesRectTest, i*30f - 110,30f,false,mArcGradientTest);
                    }
                    else{
                        if(i== 2 || i == 5 || i==8 || i == 11){
                            canvas.drawArc(hourRect, i*30f - 104,20f,false,mArcHoursPaint);
//                            canvas.drawArc(hourRect, i*30f - 104,20f,false,mArcGradientTest);
                        }
                        else{

                            canvas.drawArc(hourRect, i*30f - 95,20f,false,mArcHoursPaint);
//                            canvas.drawArc(hourRect, i*30f - 95,20f,false,mArcGradientTest);
                        }
                    }
                }
            }


            if(mCalendar.get(Calendar.AM_PM) == mCalendar.get(Calendar.AM)){
                //sollte AM sein, wird aber seltsamer weise ausgeführt wenn Uhr auf PM eingestell ist
                canvas.drawArc(amPmRect, -65f,130f,false,mArcAmPmPaint);
//                canvas.drawArc(minutesRectTest, -65f,100f,false,mArcGradientTest);
            }
            else
            {
                //halbkreis auf der linken seite für PM
                canvas.drawArc(amPmRect, 118f,125f,false,mArcAmPmPaint);
//                canvas.drawArc(minutesRectTest, 118f,100f,false,mArcGradientTest);
            }

            drawRoughMinutes(mCalendar.get(Calendar.MINUTE), canvas);
//            /* Restore the canvas' original orientation. */
//            canvas.restore();

            if(!mAmbient){
//                canvas.drawCircle(0f,mCenterX*2f,100,mBackgroundPaint);
            }
        }



        private void drawRoughMinutes(int dontPaint, Canvas canvas){
             /*
            TEST REct - test scalable rect
             */
            Context context = getApplicationContext();
            DisplayMetrics metrics = new DisplayMetrics();
            WindowManager windowManager = (WindowManager) context
                    .getSystemService(Context.WINDOW_SERVICE);
            windowManager.getDefaultDisplay().getMetrics(metrics);
            float hourScale = metrics.widthPixels*0.40f;

//            canvas.drawLine(0f,mCenterY,mCenterX+mCenterX,mCenterY,mHelperLines);
//            canvas.drawLine(mCenterX,0f,mCenterX,mCenterY+mCenterY,mHelperLines);
//            canvas.drawLine(mCenterX+mCenterX,0f,0f,mCenterY+mCenterY,mHelperLines);
//            canvas.drawLine(0f,0f,mCenterX+mCenterX,mCenterY+mCenterY,mHelperLines);

            float offset = 2.5f;
//            RectF viereck = new RectF(mCenterX-183f, mCenterY-183f, mCenterX+ 183f,mCenterY+183f);
            RectF viereck = new RectF(mCenterX-hourScale, mCenterY-hourScale, mCenterX+ hourScale,mCenterY+hourScale);
//            canvas.drawArc(hourRect, -113.5f + offset,28f,false,mArcGradientTest);
//            canvas.drawArc(hourRect, -80f + offset,32f,false,mArcGradientTest);
//            canvas.drawArc(hourRect, -47f + offset,16f,false,mArcGradientTest);
//            canvas.drawArc(hourRect, -23.5f + offset,18f,false,mArcGradientTest);
//            canvas.drawArc(hourRect, -3f + offset,44.5f,false,mArcGradientTest);
//            canvas.drawArc(hourRect, 43f + offset,18f,false,mArcGradientTest);
//            canvas.drawArc(hourRect, 64f + offset,30f,false,mArcGradientTest);
//            canvas.drawArc(hourRect, 98f + offset,31f,false,mArcGradientTest);
//            canvas.drawArc(hourRect, 130f + offset,19f,false,mArcGradientTest);
//            canvas.drawArc(hourRect, 156f + offset,18f,false,mArcGradientTest);
//            canvas.drawArc(hourRect, 177f + offset,44f,false,mArcGradientTest);
//            canvas.drawArc(hourRect, 222.5f + offset,20f,false,mArcGradientTest);
            if(dontPaint == 57 || dontPaint == 58 || dontPaint == 59 || dontPaint == 0 || dontPaint == 1)
            {
                canvas.drawArc(viereck, -80f + offset,32f,false,mArcRoughMinutes);
                canvas.drawArc(viereck, -47f + offset,16f,false,mArcRoughMinutes);
                canvas.drawArc(viereck, -23.5f + offset,18f,false,mArcRoughMinutes);
                canvas.drawArc(viereck, -3f + offset,44.5f,false,mArcRoughMinutes);
                canvas.drawArc(viereck, 43f + offset,18f,false,mArcRoughMinutes);
                canvas.drawArc(viereck, 64f + offset,30f,false,mArcRoughMinutes);
                canvas.drawArc(viereck, 98f + offset,31f,false,mArcRoughMinutes);
                canvas.drawArc(viereck, 130f + offset,19f,false,mArcRoughMinutes);
                canvas.drawArc(viereck, 156f + offset,18f,false,mArcRoughMinutes);
                canvas.drawArc(viereck, 177f + offset,44f,false,mArcRoughMinutes);
                canvas.drawArc(viereck, 222.5f + offset,20f,false,mArcRoughMinutes);
            }
            if(dontPaint >= 2 && dontPaint<=7)
            {
                canvas.drawArc(viereck, -113.5f + offset,28f,false,mArcRoughMinutes);
                canvas.drawArc(viereck, -47f + offset,16f,false,mArcRoughMinutes);
                canvas.drawArc(viereck, -23.5f + offset,18f,false,mArcRoughMinutes);
                canvas.drawArc(viereck, -3f + offset,44.5f,false,mArcRoughMinutes);
                canvas.drawArc(viereck, 43f + offset,18f,false,mArcRoughMinutes);
                canvas.drawArc(viereck, 64f + offset,30f,false,mArcRoughMinutes);
                canvas.drawArc(viereck, 98f + offset,31f,false,mArcRoughMinutes);
                canvas.drawArc(viereck, 130f + offset,19f,false,mArcRoughMinutes);
                canvas.drawArc(viereck, 156f + offset,18f,false,mArcRoughMinutes);
                canvas.drawArc(viereck, 177f + offset,44f,false,mArcRoughMinutes);
                canvas.drawArc(viereck, 222.5f + offset,20f,false,mArcRoughMinutes);
            }
            if(dontPaint >= 8 && dontPaint<=10)
            {
                canvas.drawArc(viereck, -113.5f + offset,28f,false,mArcRoughMinutes);
                canvas.drawArc(viereck, -80f + offset,32f,false,mArcRoughMinutes);
                canvas.drawArc(viereck, -23.5f + offset,18f,false,mArcRoughMinutes);
                canvas.drawArc(viereck, -3f + offset,44.5f,false,mArcRoughMinutes);
                canvas.drawArc(viereck, 43f + offset,18f,false,mArcRoughMinutes);
                canvas.drawArc(viereck, 64f + offset,30f,false,mArcRoughMinutes);
                canvas.drawArc(viereck, 98f + offset,31f,false,mArcRoughMinutes);
                canvas.drawArc(viereck, 130f + offset,19f,false,mArcRoughMinutes);
                canvas.drawArc(viereck, 156f + offset,18f,false,mArcRoughMinutes);
                canvas.drawArc(viereck, 177f + offset,44f,false,mArcRoughMinutes);
                canvas.drawArc(viereck, 222.5f + offset,20f,false,mArcRoughMinutes);
            }
            if(dontPaint >= 11 && dontPaint<=14)
            {
                canvas.drawArc(viereck, -113.5f + offset,28f,false,mArcRoughMinutes);
                canvas.drawArc(viereck, -80f + offset,32f,false,mArcRoughMinutes);
                canvas.drawArc(viereck, -47f + offset,16f,false,mArcRoughMinutes);
                canvas.drawArc(viereck, -3f + offset,44.5f,false,mArcRoughMinutes);
                canvas.drawArc(viereck, 43f + offset,18f,false,mArcRoughMinutes);
                canvas.drawArc(viereck, 64f + offset,30f,false,mArcRoughMinutes);
                canvas.drawArc(viereck, 98f + offset,31f,false,mArcRoughMinutes);
                canvas.drawArc(viereck, 130f + offset,19f,false,mArcRoughMinutes);
                canvas.drawArc(viereck, 156f + offset,18f,false,mArcRoughMinutes);
                canvas.drawArc(viereck, 177f + offset,44f,false,mArcRoughMinutes);
                canvas.drawArc(viereck, 222.5f + offset,20f,false,mArcRoughMinutes);
            }
            if(dontPaint >= 15 && dontPaint<=22)
            {
                canvas.drawArc(viereck, -113.5f + offset,28f,false,mArcRoughMinutes);
                canvas.drawArc(viereck, -80f + offset,32f,false,mArcRoughMinutes);
                canvas.drawArc(viereck, -47f + offset,16f,false,mArcRoughMinutes);
                canvas.drawArc(viereck, -23.5f + offset,18f,false,mArcRoughMinutes);
                canvas.drawArc(viereck, 43f + offset,18f,false,mArcRoughMinutes);
                canvas.drawArc(viereck, 64f + offset,30f,false,mArcRoughMinutes);
                canvas.drawArc(viereck, 98f + offset,31f,false,mArcRoughMinutes);
                canvas.drawArc(viereck, 130f + offset,19f,false,mArcRoughMinutes);
                canvas.drawArc(viereck, 156f + offset,18f,false,mArcRoughMinutes);
                canvas.drawArc(viereck, 177f + offset,44f,false,mArcRoughMinutes);
                canvas.drawArc(viereck, 222.5f + offset,20f,false,mArcRoughMinutes);
            }
            if(dontPaint >= 23 && dontPaint<=25)
            {
                canvas.drawArc(viereck, -113.5f + offset,28f,false,mArcRoughMinutes);
                canvas.drawArc(viereck, -80f + offset,32f,false,mArcRoughMinutes);
                canvas.drawArc(viereck, -47f + offset,16f,false,mArcRoughMinutes);
                canvas.drawArc(viereck, -23.5f + offset,18f,false,mArcRoughMinutes);
                canvas.drawArc(viereck, -3f + offset,44.5f,false,mArcRoughMinutes);
                canvas.drawArc(viereck, 64f + offset,30f,false,mArcRoughMinutes);
                canvas.drawArc(viereck, 98f + offset,31f,false,mArcRoughMinutes);
                canvas.drawArc(viereck, 130f + offset,19f,false,mArcRoughMinutes);
                canvas.drawArc(viereck, 156f + offset,18f,false,mArcRoughMinutes);
                canvas.drawArc(viereck, 177f + offset,44f,false,mArcRoughMinutes);
                canvas.drawArc(viereck, 222.5f + offset,20f,false,mArcRoughMinutes);
            }
            if(dontPaint >= 26 && dontPaint<=31)
            {
                canvas.drawArc(viereck, -113.5f + offset,28f,false,mArcRoughMinutes);
                canvas.drawArc(viereck, -80f + offset,32f,false,mArcRoughMinutes);
                canvas.drawArc(viereck, -47f + offset,16f,false,mArcRoughMinutes);
                canvas.drawArc(viereck, -23.5f + offset,18f,false,mArcRoughMinutes);
                canvas.drawArc(viereck, -3f + offset,44.5f,false,mArcRoughMinutes);
                canvas.drawArc(viereck, 43f + offset,18f,false,mArcRoughMinutes);
                canvas.drawArc(viereck, 98f + offset,31f,false,mArcRoughMinutes);
                canvas.drawArc(viereck, 130f + offset,19f,false,mArcRoughMinutes);
                canvas.drawArc(viereck, 156f + offset,18f,false,mArcRoughMinutes);
                canvas.drawArc(viereck, 177f + offset,44f,false,mArcRoughMinutes);
                canvas.drawArc(viereck, 222.5f + offset,20f,false,mArcRoughMinutes);
            }
            if(dontPaint >= 32 && dontPaint<=37)
            {
                canvas.drawArc(viereck, -113.5f + offset,28f,false,mArcRoughMinutes);
                canvas.drawArc(viereck, -80f + offset,32f,false,mArcRoughMinutes);
                canvas.drawArc(viereck, -47f + offset,16f,false,mArcRoughMinutes);
                canvas.drawArc(viereck, -23.5f + offset,18f,false,mArcRoughMinutes);
                canvas.drawArc(viereck, -3f + offset,44.5f,false,mArcRoughMinutes);
                canvas.drawArc(viereck, 43f + offset,18f,false,mArcRoughMinutes);
                canvas.drawArc(viereck, 64f + offset,30f,false,mArcRoughMinutes);
                canvas.drawArc(viereck, 130f + offset,19f,false,mArcRoughMinutes);
                canvas.drawArc(viereck, 156f + offset,18f,false,mArcRoughMinutes);
                canvas.drawArc(viereck, 177f + offset,44f,false,mArcRoughMinutes);
                canvas.drawArc(viereck, 222.5f + offset,20f,false,mArcRoughMinutes);
            }
            if(dontPaint >= 38 && dontPaint<=40)
            {
                canvas.drawArc(viereck, -113.5f + offset,28f,false,mArcRoughMinutes);
                canvas.drawArc(viereck, -80f + offset,32f,false,mArcRoughMinutes);
                canvas.drawArc(viereck, -47f + offset,16f,false,mArcRoughMinutes);
                canvas.drawArc(viereck, -23.5f + offset,18f,false,mArcRoughMinutes);
                canvas.drawArc(viereck, -3f + offset,45f,false,mArcRoughMinutes);
                canvas.drawArc(viereck, 42.5f + offset,18f,false,mArcRoughMinutes);
                canvas.drawArc(viereck, 64f + offset,30f,false,mArcRoughMinutes);
                canvas.drawArc(viereck, 98f + offset,31f,false,mArcRoughMinutes);
                canvas.drawArc(viereck, 156f + offset,18f,false,mArcRoughMinutes);
                canvas.drawArc(viereck, 177f + offset,44f,false,mArcRoughMinutes);
                canvas.drawArc(viereck, 222.5f + offset,20f,false,mArcRoughMinutes);
            }
            if(dontPaint >= 41 && dontPaint<=44)
            {
                canvas.drawArc(viereck, -113.5f + offset,28f,false,mArcRoughMinutes);
                canvas.drawArc(viereck, -80f + offset,32f,false,mArcRoughMinutes);
                canvas.drawArc(viereck, -47f + offset,16f,false,mArcRoughMinutes);
                canvas.drawArc(viereck, -23.5f + offset,18f,false,mArcRoughMinutes);
                canvas.drawArc(viereck, -3f + offset,44.5f,false,mArcRoughMinutes);
                canvas.drawArc(viereck, 43f + offset,18f,false,mArcRoughMinutes);
                canvas.drawArc(viereck, 64f + offset,30f,false,mArcRoughMinutes);
                canvas.drawArc(viereck, 98f + offset,31f,false,mArcRoughMinutes);
                canvas.drawArc(viereck, 130f + offset,19f,false,mArcRoughMinutes);
                canvas.drawArc(viereck, 177f + offset,44f,false,mArcRoughMinutes);
                canvas.drawArc(viereck, 222.5f + offset,20f,false,mArcRoughMinutes);
            }
            if(dontPaint >= 45 && dontPaint<=52)
            {
                canvas.drawArc(viereck, -113.5f + offset,28f,false,mArcRoughMinutes);
                canvas.drawArc(viereck, -80f + offset,32f,false,mArcRoughMinutes);
                canvas.drawArc(viereck, -47f + offset,16f,false,mArcRoughMinutes);
                canvas.drawArc(viereck, -23.5f + offset,18f,false,mArcRoughMinutes);
                canvas.drawArc(viereck, -3f + offset,44.5f,false,mArcRoughMinutes);
                canvas.drawArc(viereck, 43f + offset,18f,false,mArcRoughMinutes);
                canvas.drawArc(viereck, 64f + offset,30f,false,mArcRoughMinutes);
                canvas.drawArc(viereck, 98f + offset,31f,false,mArcRoughMinutes);
                canvas.drawArc(viereck, 130f + offset,19f,false,mArcRoughMinutes);
                canvas.drawArc(viereck, 156f + offset,18f,false,mArcRoughMinutes);
                canvas.drawArc(viereck, 222.5f + offset,20f,false,mArcRoughMinutes);
            }
            if(dontPaint >= 53 && dontPaint<=56)
            {
                canvas.drawArc(viereck, -113.5f + offset,28f,false,mArcRoughMinutes);
                canvas.drawArc(viereck, -80f + offset,32f,false,mArcRoughMinutes);
                canvas.drawArc(viereck, -47f + offset,16f,false,mArcRoughMinutes);
                canvas.drawArc(viereck, -23.5f + offset,18f,false,mArcRoughMinutes);
                canvas.drawArc(viereck, -3f + offset,44.5f,false,mArcRoughMinutes);
                canvas.drawArc(viereck, 43f + offset,18f,false,mArcRoughMinutes);
                canvas.drawArc(viereck, 64f + offset,30f,false,mArcRoughMinutes);
                canvas.drawArc(viereck, 98f + offset,31f,false,mArcRoughMinutes);
                canvas.drawArc(viereck, 130f + offset,19f,false,mArcRoughMinutes);
                canvas.drawArc(viereck, 156f + offset,18f,false,mArcRoughMinutes);
                canvas.drawArc(viereck, 177f + offset,44f,false,mArcRoughMinutes);
            }

//            float testScale = metrics.widthPixels*0.40f;
//            RectF testRect = new RectF(mCenterX-testScale,mCenterY-testScale,mCenterX+testScale,mCenterY+testScale);
////            canvas.drawArc(testRect,0,360f,false,mHelperLines);
//            canvas.drawArc(testRect, -113.5f + offset,18f,false,mArcGradientTest);

        }

        @Override
        public void onUnreadCountChanged(int count) {
            Log.d(TAG, "onUnreadCountChanged(): " + count);

            updateWatchHandStyle();
        }

        @Override
        public void onVisibilityChanged(boolean visible) {
            super.onVisibilityChanged(visible);

            if (visible) {
                loadSavedPreferences();

                // With the rest of the watch face, we update the paint colors based on
                // ambient/active mode callbacks, but because the ComplicationDrawable handles
                // the active/ambient colors, we only need to update the complications' colors when
                // the user actually makes a change to the highlight color, not when the watch goes
                // in and out of ambient mode.
                setComplicationsActiveAndAmbientColors(mRogueColor);
                updateWatchHandStyle();
                registerReceiver();
                /* Update time zone in case it changed while we weren't visible. */
                mCalendar.setTimeZone(TimeZone.getDefault());
                invalidate();
            } else {
                unregisterReceiver();
            }

            /* Check and trigger whether or not timer should be running (only in active mode). */
            updateTimer();
        }

        private void registerReceiver() {
            if (mRegisteredTimeZoneReceiver) {
                return;
            }
            mRegisteredTimeZoneReceiver = true;
            IntentFilter filter = new IntentFilter(Intent.ACTION_TIMEZONE_CHANGED);
            Rogue.this.registerReceiver(mTimeZoneReceiver, filter);
        }

        private void unregisterReceiver() {
            if (!mRegisteredTimeZoneReceiver) {
                return;
            }
            mRegisteredTimeZoneReceiver = false;
            Rogue.this.unregisterReceiver(mTimeZoneReceiver);
        }

        /**
         * Starts/stops the {@link #mUpdateTimeHandler} timer based on the state of the watch face.
         */
        private void updateTimer() {
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME);
            if (shouldTimerBeRunning()) {
                mUpdateTimeHandler.sendEmptyMessage(MSG_UPDATE_TIME);
            }
        }

        /**
         * Returns whether the {@link #mUpdateTimeHandler} timer should be running. The timer
         * should only run in active mode.
         */
        private boolean shouldTimerBeRunning() {
            return isVisible() && !mAmbient;
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
    }
}
