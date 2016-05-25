package com.atakmap.android.UasDJI.DJI;
//
//import org.opencv.android.OpenCVLoader;
//import org.opencv.android.Utils;


import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.SurfaceTexture;
import android.graphics.Matrix;
import android.os.Bundle;
import android.os.Handler;

import android.support.v4.app.Fragment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.TextureView.SurfaceTextureListener;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

//import org.opencv.core.CvType;
//import org.opencv.core.Mat;
//import org.opencv.core.MatOfRect;
//import org.opencv.core.Size;
//import org.opencv.imgproc.CLAHE;
//import org.opencv.imgproc.Imgproc;
//import org.opencv.objdetect.CascadeClassifier;

import com.atakmap.android.UasDJI.HelloWorldDropDownReceiver;
import com.atakmap.android.UasDJI.R;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;


import dji.sdk.AirLink.DJILBAirLink.DJIOnReceivedVideoCallback;
import dji.sdk.Camera.DJICamera;
import dji.sdk.Camera.DJICamera.CameraReceivedVideoDataCallback;
import dji.sdk.Codec.DJICodecManager;
import dji.sdk.Products.DJIAircraft;
import dji.sdk.base.DJIBaseComponent.DJICompletionCallback;
import dji.sdk.base.DJIBaseProduct;
import dji.sdk.base.DJIBaseProduct.Model;
import dji.sdk.base.DJIError;
import dji.sdk.Camera.DJICameraSettingsDef.CameraMode;
import dji.sdk.Camera.DJICameraSettingsDef.CameraShootPhotoMode;

//import static org.opencv.core.Core.flip;


public class DJIVideo extends Fragment implements SurfaceTextureListener,SurfaceTexture.OnFrameAvailableListener{

    public static final String DJIVideo_CREATE = "create video";

    private Button captureAction;
    private Button recordAction;
    private Button captureMode;

    private static final String TAG = DJIVideo.class.getName();
    private static final int VIDEO_HEIGHT = 9;
    private static final int VIDEO_WIDTH = 16;
    private static final int INTERVAL_LOG = 300;
    private static long mLastTime = 0l;

    protected CameraReceivedVideoDataCallback mReceivedVideoDataCallBack = null;
    protected DJIOnReceivedVideoCallback mOnReceivedVideoCallback = null;

    private DJIBaseProduct mProduct = null;
    private DJICamera mCamera = null;
    // Codec for video live view
    protected DJICodecManager mCodecManager = null;
    protected TextView mConnectStatusTextView;
    protected ImageView myFrameCaptured;
    //Video Preview
    protected TextureView mVideoSurface = null;
    protected TextureView mOverlapSurface = null;

    private TextView viewTimer, debugText;
    private int i = 0;
    private int TIME = 1000;
    // private boolean frameReady = true;
    //private Timer timer = null;
    private static final int DELAYPERFRAMEUS = 2000;

    public Handler mhandler;
    private Renderer mRenderer;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);

        LayoutInflater pluginInflater =
                (LayoutInflater)(HelloWorldDropDownReceiver.getPluginContext()).getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        View v = pluginInflater.inflate(R.layout.dji_video, null);

//
//        LayoutInflater pluginInflater = LayoutInflater.from(HelloWorldDropDownReceiver.getPluginContext());
//        View v = pluginInflater.inflate(R.layout.dji_video, container, false);

        captureAction = (Button) v.findViewById(R.id.button1);
        recordAction = (Button) v.findViewById(R.id.button2);
        captureMode = (Button) v.findViewById(R.id.button3);

        captureAction.getBackground().setColorFilter(0xFFFFFFFF, PorterDuff.Mode.MULTIPLY);
        recordAction.getBackground().setColorFilter(0xFFFFFFFF, PorterDuff.Mode.MULTIPLY);
        captureMode.getBackground().setColorFilter(0xFFFFFFFF, PorterDuff.Mode.MULTIPLY);

        mVideoSurface = (TextureView) v.findViewById(R.id.videopreviewersurface);
        mOverlapSurface = (TextureView) v.findViewById(R.id.overlapsurface);
    //    Log.d(TAG,"test : " + mVideoSurface.toString() + ", " + mOverlapSurface.toString());
//
//        if (!OpenCVLoader.initDebug()) {
//            Log.e("classify","opencv init error!");// Handle initialization error
//        }
//        initUI();

        // The callback for receiving the raw H264 video data for camera live view
        mReceivedVideoDataCallBack = new CameraReceivedVideoDataCallback() {

            @Override
            public void onResult(byte[] videoBuffer, int size) {

                if(mCodecManager != null){
                    mCodecManager.sendDataToDecoder(videoBuffer, size);

                    //frameReady = true;
                }else {
                    Log.e(TAG, "mCodecManager is null");
                }
            }
        };

        // The callback for receiving the raw video data from Airlink
        mOnReceivedVideoCallback = new DJIOnReceivedVideoCallback() {

            @Override
            public void onResult(byte[] videoBuffer, int size) {
                int i = 0;
                //Log.i("myinfo","here1\n");
                if(mCodecManager != null){
                    // Send the raw H264 video data to codec manager for decoding
                    mCodecManager.sendDataToDecoder(videoBuffer, size);
                }
            }
        };

        // Register the broadcast receiver for receiving the device connection's changes.
        IntentFilter filter = new IntentFilter();
        filter.addAction(DJIBackgroundApp.FLAG_CONNECTION_CHANGE);
        getActivity().registerReceiver(mReceiver, filter);


//
//        IntentFilter filter1 = new IntentFilter();
//        filter1.addAction(MainActivity.FLAG_VID_SIZE_CHANGE);
//        getActivity().registerReceiver(mReceiver_vid, filter);

        return v;
    }


    @Override
    public void onResume() {
        Log.e(TAG, "onResume");
        super.onResume();
        initPreviewer();
        updateTitleBar();
        if(mVideoSurface == null) {
            Log.e(TAG, "mVideoSurface is null");
        }
    }

    @Override
    public void onPause() {
        Log.e(TAG, "onPause");
        uninitPreviewer();
        super.onPause();
    }

    @Override
    public void onStop() {
        Log.e(TAG, "onStop");
        super.onStop();
    }
    /*
        public void onReturn(View view){
            Log.e(TAG, "onReturn");
            this.finish();
        }
    */
    @Override
    public void onDestroy() {
        Log.e(TAG, "onDestroy");
        uninitPreviewer();

        getActivity().unregisterReceiver(mReceiver);
        getActivity().unregisterReceiver(mReceiver_vid);
        super.onDestroy();
    }

    private void initUI() {
        mRenderer = new Renderer();
        mRenderer.start();


        mOverlapSurface.setSurfaceTextureListener(mRenderer);
        mOverlapSurface.setAlpha(0.99f);

        // viewTimer = (TextView) findViewById(R.id.timer);

        if (null != mVideoSurface) {
            mVideoSurface.setSurfaceTextureListener(this);
        }

        //frameReady = true;

//        mhandler = new Handler();
//        mhandler.postDelayed(bitmapReadinessChecker, DELAYPERFRAMEUS);
    }




    private Handler handlerTimer = new Handler();
    Runnable runnable = new Runnable(){
        @Override
        public void run() {
            try {

                handlerTimer.postDelayed(this, TIME);
                viewTimer.setText(Integer.toString(i++));

            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    };


    private void initPreviewer() {
        try {
            mProduct = DJIBackgroundApp.getProductInstance();
        } catch (Exception exception) {
            mProduct = null;
        }

        if (null == mProduct || !mProduct.isConnected()) {
            mCamera = null;
            showToast("disconnected");
        } else {


            if (null != mVideoSurface) {
                mVideoSurface.setSurfaceTextureListener(this);
            }

            if (!mProduct.getModel().equals(Model.UnknownAircraft)) {
                mCamera = mProduct.getCamera();
                if (mCamera != null){
                    // Set the callback
                    mCamera.setDJICameraReceivedVideoDataCallback(mReceivedVideoDataCallBack);

                }
            } else {
                if (null != mProduct.getAirLink()) {
                    if (null != mProduct.getAirLink().getLBAirLink()) {
                        // Set the callback
                        mProduct.getAirLink().getLBAirLink().setDJIOnReceivedVideoCallback(mOnReceivedVideoCallback);
                    }
                }
            }
        }
    }

    private void uninitPreviewer() {
        try {
            mProduct = DJIBackgroundApp.getProductInstance();
        } catch (Exception exception) {
            mProduct = null;
        }

        if (null == mProduct || !mProduct.isConnected()) {
            mCamera = null;
            showToast("disconnected");
        } else {
            if (!mProduct.getModel().equals(Model.UnknownAircraft)) {
                mCamera = mProduct.getCamera();
                if (mCamera != null){
                    // Set the callback
                    mCamera.setDJICameraReceivedVideoDataCallback(null);

                }
            } else {
                if (null != mProduct.getAirLink()) {
                    if (null != mProduct.getAirLink().getLBAirLink()) {
                        // Set the callback
                        mProduct.getAirLink().getLBAirLink().setDJIOnReceivedVideoCallback(null);
                    }
                }
            }
        }
    }

    //
    @Override
    public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
        Log.e(TAG, "onSurfaceTextureAvailable");
        if (mCodecManager == null) {
            Log.e(TAG, "mCodecManager is null 2");
            mCodecManager = new DJICodecManager(getActivity(), surface, width, height);

            // surface.setOnFrameAvailableListener(this);
            Log.i("bitmap","surface tex1: " + surface.toString());
        }
    }

    //
    @Override
    public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {
        //updateSize(width);
        mCodecManager.cleanSurface();
        mCodecManager.destroyCodec();
        //adjustAspectRatio();

        mCodecManager = new DJICodecManager(getActivity(), surface, width, height);

        //TODO: remove buttons when too small
        if (width < 400){
            captureAction.setVisibility(View.GONE);
            recordAction.setVisibility(View.GONE);
            captureMode.setVisibility(View.GONE);
        }else{
            captureAction.setVisibility(View.VISIBLE);
            recordAction.setVisibility(View.VISIBLE);
            captureMode.setVisibility(View.VISIBLE);
        }
        // Log.e(TAG, "onSurfaceTextureSizeChanged: " + width + ", " + height);
    }
    /**
     * Sets the TextureView transform to preserve the aspect ratio of the video.
     */
    private void adjustAspectRatio() {
        Log.i("FPV","changing aspect ratio");
        int viewWidth = mVideoSurface.getWidth();
        int viewHeight = mVideoSurface.getHeight();
        Log.i("FPV","viewWidth :" + viewWidth + " viewHeight: " +viewHeight);
        double aspectRatio = (double) VIDEO_HEIGHT / VIDEO_WIDTH;

        int newWidth, newHeight;
        if (viewHeight > (int) (viewWidth * aspectRatio)) {
            // limited by narrow width; restrict height
            newWidth = viewWidth;
            newHeight = (int) (viewWidth * aspectRatio);
        } else {
            // limited by short height; restrict width
            newWidth = (int) (viewHeight / aspectRatio);
            newHeight = viewHeight;
        }
        int xoff = (viewWidth - newWidth) / 2;
        int yoff = (viewHeight - newHeight) / 2;
        Log.v(TAG, "video=" + VIDEO_WIDTH + "x" + VIDEO_HEIGHT +
                " view=" + viewWidth + "x" + viewHeight +
                " newView=" + newWidth + "x" + newHeight +
                " off=" + xoff + "," + yoff);

        Matrix txform = new Matrix();
        mVideoSurface.getTransform(txform);
        txform.setScale((float) newWidth / viewWidth, (float) newHeight / viewHeight);
        //txform.postRotate(10);          // just for fun
        txform.postTranslate(xoff, yoff);
        mVideoSurface.setTransform(txform);
    }

    //
    @Override
    public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
        Log.e(TAG, "onSurfaceTextureDestroyed");
        if (mCodecManager != null) {
            mCodecManager.cleanSurface();
            mCodecManager = null;
        }

        return false;
    }

    //
    @Override
    public void onSurfaceTextureUpdated(SurfaceTexture surface) {
        //Log.e(TAG, "onSurfaceTextureUpdated");

    }

    protected BroadcastReceiver mReceiver = new BroadcastReceiver() {

        @Override
        public void onReceive(Context context, Intent intent) {
            Log.d(TAG,"mReceiver received: " + intent.getAction());
            updateTitleBar();
            onProductChange();
        }

    };


    protected BroadcastReceiver mReceiver_vid = new BroadcastReceiver() {

        @Override
        public void onReceive(Context context, Intent intent) {
            updateSize(getView().getWidth());
        }

    };


    private void updateTitleBar() {
        if(mConnectStatusTextView == null) return;
        boolean ret = false;
        DJIBaseProduct product = DJIBackgroundApp.getProductInstance();
        if (product != null) {

            if(product.isConnected()) {
                //The product is connected
                mConnectStatusTextView.setText(DJIBackgroundApp.getProductInstance().getModel() + " Connected");
                ret = true;
            } else {

                if(product instanceof DJIAircraft) {
                    DJIAircraft aircraft = (DJIAircraft)product;
                    if(aircraft.getRemoteController() != null && aircraft.getRemoteController().isConnected()) {
                        // The product is not connected, but the remote controller is connected
                        mConnectStatusTextView.setText("only RC Connected");
                        ret = true;
                    }
                }
            }
        }

        if(!ret) {
            // The product or the remote controller are not connected.
            mConnectStatusTextView.setText("Disconnected");
        }
    }

    protected void onProductChange() {
        initPreviewer();

    }
    /*
        @Override
        public boolean dispatchTouchEvent(MotionEvent ev) {
            if (ev.getAction() == MotionEvent.ACTION_DOWN) {
                final long current = System.currentTimeMillis();
                if (current - mLastTime < INTERVAL_LOG) {
                    Log.d("", "click double");
                    mLastTime = 0;
                } else {
                    mLastTime = current;
                    Log.d("", "click single");
                }
            }
            return super.dispatchTouchEvent(ev);
        }
    */
    public void showToast(final String msg) {
        getActivity().runOnUiThread(new Runnable() {
            public void run() {
                Toast.makeText(getActivity(), msg, Toast.LENGTH_SHORT).show();
            }
        });
    }

    // function for taking photo
    public void captureAction(View v){
        try {
            mProduct = DJIBackgroundApp.getProductInstance();
        } catch (Exception exception) {
            mProduct = null;
        }

        if (null == mProduct || !mProduct.isConnected()) {
            mCamera = null;
            showToast("disconnected");
            return;
        }
        CameraMode cameraMode = CameraMode.ShootPhoto;

        mCamera = mProduct.getCamera();

        mCamera.setCameraMode(cameraMode, new DJICompletionCallback() {

            @Override
            public void onResult(DJIError error) {

                if (error == null) {
                    CameraShootPhotoMode photoMode = CameraShootPhotoMode.Single; // Set the camera capture mode as Single mode

                    mCamera.startShootPhoto(photoMode, new DJICompletionCallback() {

                        @Override
                        public void onResult(DJIError error) {
                            if (error == null) {
                                showToast("take photo: success");
                            } else {
                                showToast(error.getDescription());
                            }
                        }

                    }); // Execute the startShootPhoto API
                } else {
                    showToast(error.getDescription());
                }

            }

        });

    }
    // function for starting recording
    public void recordAction(View v){
        try {
            mProduct = DJIBackgroundApp.getProductInstance();
        } catch (Exception exception) {
            mProduct = null;
        }

        if (null == mProduct || !mProduct.isConnected()) {
            mCamera = null;
            showToast("disconnected");
            return;
        }
        CameraMode cameraMode = CameraMode.RecordVideo;

        mCamera = mProduct.getCamera();

        mCamera.setCameraMode(cameraMode, new DJICompletionCallback() {

            @Override
            public void onResult(DJIError error) {

                if (error == null) {


                    mCamera.startRecordVideo(new DJICompletionCallback() {

                        @Override
                        public void onResult(DJIError error) {
                            if (error == null) {
                                showToast("Record video: success");
                                handlerTimer.postDelayed(runnable, TIME); // Start the timer for recording
                            } else {
                                showToast(error.getDescription());
                            }
                        }

                    }); // Execute the startShootPhoto API
                } else {
                    showToast(error.getDescription());
                }

            }

        });

    }
    // function for stopping recording
    public void stopRecord(View v){
        try {
            mProduct = DJIBackgroundApp.getProductInstance();
        } catch (Exception exception) {
            mProduct = null;
        }

        if (null == mProduct || !mProduct.isConnected()) {
            mCamera = null;
            showToast("disconnected");
            return;
        }
        mCamera = mProduct.getCamera();

        mCamera.stopRecordVideo(new DJICompletionCallback() {

            @Override
            public void onResult(DJIError error) {
                if (error == null) {
                    showToast("Stop recording: success");
                } else {
                    showToast(error.getDescription());
                }
                handlerTimer.removeCallbacks(runnable); // Start the timer for recording
                i = 0; // Reset the timer for recording
            }

        });
    }

    public void updateSize(int w) {
        Log.e("FPV","updating size");
        //mVideoSurface.getLayoutParams().width = w;
        //mCodecManager.cleanSurface();


    }

    @Override
    public void onFrameAvailable(SurfaceTexture surfaceTexture) {
        Log.i("FPV", "st frame available");
    }

    public class Renderer extends Thread implements SurfaceTextureListener {
        private Object mLock = new Object();        // guards mSurfaceTexture, mDone
        private boolean mDone;

        private int mWidth;     // from SurfaceTexture
        private int mHeight;
        //haar
//        private CascadeClassifier haar_faceClassifier = null;
//        private CascadeClassifier haar_sideClassifier = null;
//
//        private CascadeClassifier lbp_faceClassifier = null;
//        private CascadeClassifier lbp_sideClassifier = null;
//
//        private Mat mat = null;
//        private Mat mat1 = null;
        private int mAbsoluteFaceSize   = 0;
        private float mRelativeFaceSize   = 0.02f;
        SurfaceTexture mSurfaceTexture = null;
        public Renderer() {
            super("TextureViewCanvas Renderer");


        }

        @Override
        public void run() {
            while (true) {
                SurfaceTexture surfaceTexture = null;

                // Latch the SurfaceTexture when it becomes available.  We have to wait for
                // the TextureView to create it.
                synchronized (mLock) {
                    while (!mDone && (surfaceTexture = mSurfaceTexture) == null) {
                        try {
                            mLock.wait();
                        } catch (InterruptedException ie) {
                            throw new RuntimeException(ie);     // not expected
                        }
                    }
                    if (mDone) {
                        break;
                    }
                }
                Log.d("bitmap", "Got surfaceTexture=" + surfaceTexture);

                // Render frames until we're told to stop or the SurfaceTexture is destroyed.
                doAnimation();
            }

//        Log.d(TAG, "Renderer thread exiting");
        }

        /**
         * Draws updates as fast as the system will allow.
         * <p>
         * In 4.4, with the synchronous buffer queue queue, the frame rate will be limited.
         * In previous (and future) releases, with the async queue, many of the frames we
         * render may be dropped.
         * <p>
         * The correct thing to do here is use Choreographer to schedule frame updates off
         * of vsync, but that's not nearly as much fun.
         */


        private void doAnimation() {

            // Create a Surface for the SurfaceTexture.
            Surface surface = null;
            synchronized (mLock) {
                SurfaceTexture surfaceTexture = mSurfaceTexture;
                if (surfaceTexture == null) {
                    Log.d("bitmap", "ST null on entry");
                    return;
                }
                surface = new Surface(surfaceTexture);
            }


            Paint myPaint = new Paint();
            myPaint.setColor(Color.rgb(255, 255, 255));
            myPaint.setStrokeWidth(15);
            myPaint.setStyle(Paint.Style.STROKE);


            Paint borderPaint = new Paint();
            borderPaint.setColor(Color.rgb(0x29, 0x80, 0xb9));
            borderPaint.setStrokeWidth(10);
            borderPaint.setStyle(Paint.Style.STROKE);

//            prepareClassifiers();

//            CLAHE clahe = Imgproc.createCLAHE(2.0, new Size(8, 8));
            int bitmapH = 0;
            int bitmapW = 0;
            Canvas canvas = null;
            while (true) {
                try {

                    if (mSurfaceTexture == null) break;
                    canvas = surface.lockCanvas(null);

                    if (canvas == null) {
                        Log.e("classify", "lockCanvas() failed");
                        break;
                    }
                    canvas.drawColor(0, PorterDuff.Mode.CLEAR); //clear canvas of previosu rectangle

//                    if (mAbsoluteFaceSize == 0) {
//                        int height = 0;
//                        if (bitmapH != 0) {
//                            height = bitmapH / 2;
//                        } else {
//                            height = canvas.getHeight() / 2;
//                        }
//                        if (Math.round(height * mRelativeFaceSize) > 0) {
//                            mAbsoluteFaceSize = Math.round(height * mRelativeFaceSize);
//                        }
//                    }
//
//
//                    if (bitmapW == 0 || bitmapH == 0) {
//                        Bitmap b0 = mVideoSurface.getBitmap();
//                        bitmapW = b0.getWidth();
//                        bitmapH = b0.getHeight();
//                    }
//
//                    Bitmap b = Bitmap.createScaledBitmap(mVideoSurface.getBitmap(), bitmapW / 2, bitmapH / 2, false);
//
//                    if (mat == null) mat = new Mat(b.getWidth(), b.getHeight(), CvType.CV_8UC1);
//                    if (mat1 == null) mat1 = new Mat(b.getWidth(), b.getHeight(), CvType.CV_8UC1);
//                    Mat grayMat = new Mat();
//
//
//                    Utils.bitmapToMat(b, mat);
//                    Imgproc.cvtColor(mat, mat1, Imgproc.COLOR_BGR2GRAY);
//                    clahe.apply(mat1, grayMat);
//
//                    Mat grayMat_flipped = new Mat();
//                    flip(grayMat, grayMat_flipped, 1);
//
//                    MatOfRect faces0 = new MatOfRect();
//                    haar_faceClassifier.detectMultiScale(grayMat, faces0, 1.1, 4, 2, new Size(mAbsoluteFaceSize, mAbsoluteFaceSize), new Size());
//                    MatOfRect faces1 = new MatOfRect();
//                    haar_sideClassifier.detectMultiScale(grayMat, faces1, 1.1, 4, 2, new Size(mAbsoluteFaceSize, mAbsoluteFaceSize), new Size());
//                    MatOfRect faces2 = new MatOfRect();
//                    haar_sideClassifier.detectMultiScale(grayMat_flipped, faces2, 1.1, 4, 2, new Size(mAbsoluteFaceSize, mAbsoluteFaceSize), new Size());
//
//                    for (org.opencv.core.Rect face : faces0.toArray()) {
//                        int top = (int) (face.tl().y) * 2;
//                        int left = (int) (face.tl().x) * 2;
//                        int bottom = (int) (face.br().y) * 2;
//                        int right = (int) (face.br().x) * 2;
//                        canvas.drawRect(left, top, right, bottom, myPaint);
//                    }
//                    for (org.opencv.core.Rect face : faces1.toArray()) {
//                        int top = (int) (face.tl().y) * 2;
//                        int left = (int) (face.tl().x) * 2;
//                        int bottom = (int) (face.br().y) * 2;
//                        int right = (int) (face.br().x) * 2;
//                        canvas.drawRect(left, top, right, bottom, myPaint);
//                    }
//                    for (org.opencv.core.Rect face : faces2.toArray()) {
//                        int top = (int) (face.tl().y) * 2;
//                        int left = (int) (face.tl().x) * 2;
//                        int bottom = (int) (face.br().y) * 2;
//                        int right = (int) (face.br().x) * 2;
//                        canvas.drawRect(canvas.getWidth() - left, top, canvas.getWidth() - right, bottom, myPaint);
//                    }

                    canvas.drawRect(0, 0, canvas.getWidth(), canvas.getHeight(), borderPaint);


                    if (canvas != null) {
                        if (surface == null) break;
                        // if (surface)

                        surface.unlockCanvasAndPost(canvas);
                    }


                } catch (IllegalArgumentException iae) {
                    Log.d("classify", "unlockCanvasAndPost failed: " + iae.getMessage());
                    break;
                } catch (Exception e1) {
                    e1.printStackTrace();
                    break;
                }
                try {
                    sleep(1000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }

            }


        }

//        private void prepareClassifiers() {
//            try {
//                InputStream is = getResources().openRawResource(R.raw.haarcascade_frontalface_alt);
//                File cascadeDir = getActivity().getDir("cascade", Context.MODE_PRIVATE);
//                File mCascadeFile = new File(cascadeDir, "haarcascade_frontalface_default.xml");
//                FileOutputStream os = new FileOutputStream(mCascadeFile);
//
//                byte[] buffer = new byte[4096];
//                int bytesRead;
//                while ((bytesRead = is.read(buffer)) != -1) {
//                    os.write(buffer, 0, bytesRead);
//                }
//                is.close();
//                os.close();
//                haar_faceClassifier = new CascadeClassifier(mCascadeFile.getAbsolutePath());
//                if (haar_faceClassifier.empty()) {
//                    Log.e("classify", "Failed to load face cascade classifier");
//                    haar_faceClassifier = null;
//                } else
//                    Log.i(TAG, "Loaded cascade classifier from " + mCascadeFile.getAbsolutePath());
//
//            }
//            catch(Exception e){
//                e.printStackTrace();
//            }
//
//            try {
//                InputStream is = getResources().openRawResource(R.raw.haarcascade_profileface);
//                File cascadeDir = getActivity().getDir("cascade", Context.MODE_PRIVATE);
//                File mCascadeFile = new File(cascadeDir, "haarcascade_profileface.xml");
//                FileOutputStream os = new FileOutputStream(mCascadeFile);
//
//                byte[] buffer = new byte[4096];
//                int bytesRead;
//                while ((bytesRead = is.read(buffer)) != -1) {
//                    os.write(buffer, 0, bytesRead);
//                }
//                is.close();
//                os.close();
//                haar_sideClassifier = new CascadeClassifier(mCascadeFile.getAbsolutePath());
//                if (haar_sideClassifier.empty()) {
//                    Log.e("classify", "Failed to load sideprof cascade classifier");
//                    haar_sideClassifier = null;
//                } else
//                    Log.i(TAG, "Loaded cascade classifier from " + mCascadeFile.getAbsolutePath());
//            }
//            catch(Exception e){
//                e.printStackTrace();
//            }
//        }
//
//

        /**
         * Tells the thread to stop running.
         */
        public void halt() {
            synchronized (mLock) {
                mDone = true;
                mLock.notify();
            }
        }

        @Override   // will be called on UI thread
        public void onSurfaceTextureAvailable(SurfaceTexture st, int width, int height) {
            //Log.d("bitmap", "onSurfaceTextureAvailable(" + width + "x" + height + ")");
            Log.i("bitmap", "surface tex2: " + st.toString());
            mWidth = width;
            mHeight = height;
            synchronized (mLock) {
                mSurfaceTexture = st;
                mLock.notify();
            }
        }

        @Override   // will be called on UI thread
        public void onSurfaceTextureSizeChanged(SurfaceTexture st, int width, int height) {
            //Log.d("bitmap", "onSurfaceTextureSizeChanged(" + width + "x" + height + ")");
            mWidth = width;
            mHeight = height;
        }

        @Override   // will be called on UI thread
        public boolean onSurfaceTextureDestroyed(SurfaceTexture st) {
            //Log.d("bitmap", "onSurfaceTextureDestroyed");

            synchronized (mLock) {
                mSurfaceTexture = null;
            }
            return true;
        }

        @Override   // will be called on UI thread
        public void onSurfaceTextureUpdated(SurfaceTexture st) {
            //Log.d(TAG, "onSurfaceTextureUpdated");
        }
    }


}