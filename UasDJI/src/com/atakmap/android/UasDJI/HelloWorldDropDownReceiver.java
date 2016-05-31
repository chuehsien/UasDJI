
package com.atakmap.android.UasDJI;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.SurfaceTexture;
import android.os.Environment;
import android.os.ParcelFileDescriptor;
import android.os.RemoteException;
import android.view.LayoutInflater;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.app.AlertDialog;
import android.widget.ArrayAdapter;
import android.widget.Button;

import android.widget.TextView;
import android.widget.Toast;

import com.atakmap.android.UasDJI.plugin.HelloWorldLifecycle;
import com.atakmap.android.maps.MapActivity;
import com.atakmap.android.maps.MapComponent;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.maps.Marker;
import com.atakmap.android.maps.MapGroup;
import com.atakmap.android.dropdown.DropDown.OnStateListener;
import com.atakmap.android.dropdown.DropDownReceiver;
import com.atakmap.coremap.maps.coords.Altitude;
import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.coremap.maps.coords.GeoPointSource;
import android.view.View.OnClickListener;

import android.content.ComponentName;

import android.content.DialogInterface;

import android.graphics.Color;

import android.util.Base64;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ByteArrayOutputStream;


import com.atakmap.android.routes.Route;
import com.atakmap.android.routes.RouteMapComponent;
import com.atakmap.android.routes.RouteMapReceiver;

import android.util.Log;

import org.opencv.android.OpenCVLoader;
import org.opencv.android.Utils;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfRect;
import org.opencv.core.Size;
import org.opencv.imgproc.CLAHE;
import org.opencv.imgproc.Imgproc;
import org.opencv.objdetect.CascadeClassifier;

import java.io.InputStreamReader;
import java.util.UUID;

import static org.opencv.core.Core.flip;


public class HelloWorldDropDownReceiver extends DropDownReceiver implements
        OnStateListener {

    public static final String TAG = "HelloWorldDropDownReceiver";

    public static final String SHOW_HELLO_WORLD = "com.atakmap.android.helloworld.SHOW_HELLO_WORLD";
    private final View helloView;
    private static Context pluginContext;

    private  Route r;

    /**************************** CONSTRUCTOR *****************************/
    public static HelloWorldDropDownReceiver _self;
    public static HelloWorldDropDownReceiver getInstance(){
        return _self;
    }
    public static Context getPluginContext(){
        return pluginContext;
    }
    public TextView tv;
    ReceiverThread mReceiver;
    public TextureView djiVideoSurface;
    public TextureView djiOverlaySurface;
    private static final int VIDEO_HEIGHT = 720;
    private static final int VIDEO_WIDTH = 960;

    Renderer mRenderer;
    MediaCodecDecoder mDecoder;


    public HelloWorldDropDownReceiver(final MapView mapView, final Context context) {
        super(mapView);
        Log.d(TAG,"inflating helloworld DDR");
        this.pluginContext = context;
        _self = this;

        LayoutInflater inflater = 
               (LayoutInflater)context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        helloView = inflater.inflate(R.layout.djivid, null);
//        mVideoSurface = (TextureView)helloView.findViewById(R.id.dji_video_surface);
//        if (null != mVideoSurface) {
//            mVideoSurface.setSurfaceTextureListener(this);
//        }

        Button connectB = (Button) helloView.findViewById(R.id.connect);
        connectB.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                HelloWorldLifecycle.getInstance().initConnectionWithService();
            }
        });

        Button streamB = (Button) helloView.findViewById(R.id.stream);
        streamB.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                if (mReceiver == null) {
                    Log.d(TAG, "service: Started receiverThread");
                    mReceiver = new ReceiverThread(HelloWorldLifecycle.getInstance().inStream);
                    mReceiver.start();
                }

                HelloWorldLifecycle.getInstance().startStream();
            }
        });



        djiVideoSurface = (TextureView) helloView.findViewById(R.id.dji_video_surface);
//        mRenderer = new Renderer();
//        mRenderer.start();
        mDecoder = new MediaCodecDecoder();
        mDecoder.Start();

        if (null != djiVideoSurface) {
            djiVideoSurface.setSurfaceTextureListener(mDecoder);
        }

        if (!OpenCVLoader.initDebug()) {
            Log.e(TAG,"opencv init error!");// Handle initialization error
        }



        djiOverlaySurface = (TextureView) helloView.findViewById(R.id.dji_overlay_surface);
//        mRenderer = new Renderer();
//        mRenderer.start();
        mRenderer = new Renderer();
        mRenderer.start();

        if (null != djiOverlaySurface) {
            djiOverlaySurface.setSurfaceTextureListener(mRenderer);
            djiOverlaySurface.setAlpha(0.99f);
        }

    }


    /**************************** PUBLIC METHODS *****************************/

    public void updateProductStatus(String s){
        Button streamB = (Button) helloView.findViewById(R.id.stream);
        if (!s.contains("Connected")){
            streamB.setEnabled(false);
        }else{
            streamB.setEnabled(true);
        }
        TextView status = (TextView) helloView.findViewById(R.id.statustext);
        status.setText(s);
    }
    public static String bytesToHexString(byte[] bytes, int count){
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < count; i++){

            sb.append(String.format("%02x ", (bytes[i])&0xff));
        }
        return sb.toString();
    }



    public void disposeImpl() {
    }

    class ReceiverThread extends Thread{
        byte[] buffer = new byte[2048]; // Adjust if you want
        int bytesRead;

        private final int STATE_INIT = 0;
        private final int STATE_09 = 9;
        private final int STATE_0906 = 96;
        private final int STATE_XX = 5;
        private int STATE;



        int b;
        private byte[] AUDheader = new byte[]{0,0,0,1,9};

        private byte[] delimiter = {0,0,1,0,0,0,0}; //last 2 bytes are for size.

        ByteArrayOutputStream collectedAUD;
        ByteArrayOutputStream collectedStripedPacket;

        private InputStream is;
        ReceiverThread(InputStream is){
            super();
            this.is = is;
            STATE = STATE_XX;
            collectedAUD = new ByteArrayOutputStream(2048);
            collectedStripedPacket = new ByteArrayOutputStream();
        }
        private boolean contains(int marker, byte[] buf){
            if (buf.length < 5) return false;
            else{
                return (buf[0] == 0 && buf[1] == 0 && buf[2] == 0 && buf[3] == 1 && buf[4] == marker);
            }
        }


        private void newStripedPacket(byte[] buffer, int len){

            //check first 5 bytes
            if (len >= 5){
                switch (STATE){
                    case STATE_INIT:
                        if (contains(9,buffer)){
                            STATE = STATE_09;
                            collectedAUD.write(buffer,0,len);
                        }else{
                            STATE = STATE_INIT;
                        }
                        break;
                    case STATE_09:
                        if (contains(6,buffer)){
                            STATE = STATE_0906;
                            collectedAUD.write(buffer,0,len);
                        }
                        else if (contains(9,buffer)){
                            STATE = STATE_09;
                            collectedAUD.reset();
                            collectedAUD.write(buffer,0,len);
                        }
                        break;
                    case STATE_0906:
                        if (contains(9,buffer)){
                            STATE = STATE_09;
                            byte[] b = collectedAUD.toByteArray();
                            mDecoder.OnRecvEncodedData(b, b.length);
                            collectedAUD.reset();
                          //  collectedAUD.write(buffer,0,len); -- throw away the AUD that delimits packets (this is the packet 6 in between large packets
                        }else {
                            collectedAUD.write(buffer,0,len);
                        }
                        break;
                    case STATE_XX:
                        if (contains(9,buffer)){
                            STATE = STATE_09;
                            collectedAUD.write(buffer,0,len);
                        }
                        else{
                            STATE = STATE_INIT;
                        }
                        break;
                }
               // Log.d(TAG,"STATE: " + STATE + " bytesread: " + len);
            }else{
                Log.e(TAG,"stripped packet less than 5 bytes long!");
            }



        }
        @Override
        public void run(){
            Log.d(TAG, "service: Thread running");

            ByteArrayOutputStream delimBuilder = new ByteArrayOutputStream(7);
            while (true) {
                try {
//                    int br;
//                    while ((br = is.read(buffer)) != -1) {
//                        newStripedPacket(buffer, br);
//                    }

                    //find delim
                    delimBuilder.reset();
                    int b;
                    collectedStripedPacket.reset();

                    while ((b = is.read()) != -1) {
                       // Log.d(TAG, "Searching for delimL: " + b);
                        delimBuilder.write(b);
                        byte[] test = delimBuilder.toByteArray();
                        int len = test.length;
                        if (len < 7) continue;
                        if (test[len - 7] == 0 && test[len - 6] == 0 && test[len - 5] == 1 && test[len - 4] == 0 && test[len - 3] == 0) {
                            delimBuilder.reset();
                            int packetsize = (((test[len - 2]) & 0xFF) << 8) + ((test[len - 1]) & 0xFF);//look at last 7 bytes
                            // Log.d(TAG,"size from delim: " + packetsize);
                            int br;
                            int totalsize = 0;
                            int sizeToRead = packetsize;
                            while ((br = is.read(buffer, 0, sizeToRead)) != -1) {
                                totalsize += br;
                                collectedStripedPacket.write(buffer, 0, br);
                                if (totalsize != packetsize) {
                                    sizeToRead = packetsize - totalsize;
                                    continue;
                                } else {
                                    byte[] stripedPacket = collectedStripedPacket.toByteArray();
                                    //send buffer for next stage processing
                                    //Log.d(TAG,"Todecoder: " + stripedPacket.length + "- " + bytesToHexString(stripedPacket,stripedPacket.length));
                                    newStripedPacket(stripedPacket, stripedPacket.length);

                                    break;
                                }

                            }
                            break;
                        } else {
                            //delim not found
                        }
                    }

                } catch (IOException e) {
                    e.printStackTrace();
                    Log.d(TAG, "service: copyStream failed!");
                    return;
                }
            }
        }
    }
    /**************************** INHERITED METHODS *****************************/

    @Override
    public void onReceive(Context context, Intent intent) {
        Log.d(TAG, "showing hello world drop down");
        if (intent.getAction().equals(SHOW_HELLO_WORLD)) {
            showDropDown(helloView, HALF_WIDTH, FULL_HEIGHT,
                    FULL_WIDTH, HALF_HEIGHT, false);


//            Intent startServiceIntent =
//                 new Intent("com.atakmap.android.helloworld.notification.NotificationService");
//            ComponentName name = getMapView().getContext().startService(startServiceIntent);
//
//            manipulateFakeContentProvider();
        }
    }

    @Override
    public void onDropDownSelectionRemoved() {
    }

    @Override
    public void onDropDownVisible(boolean v) {
    }

    @Override
    public void onDropDownSizeChanged(double width, double height) {
    }

    @Override
    public void onDropDownClose() {
    }

    /************************* Helper Methods *************************/

    private RouteMapReceiver getRouteMapReceiver() {

        // TODO: this code was copied from another plugin.
        // Not sure why we can't just callRouteMapReceiver.getInstance();
        MapActivity activity = (MapActivity) getMapView().getContext();
        MapComponent mc = activity.getMapComponent(RouteMapComponent.class);
        if (mc == null || !(mc instanceof RouteMapComponent)) {
            Log.w(TAG, "Unable to find route without RouteMapComponent");
            return null;
        }

        RouteMapComponent routeComponent = (RouteMapComponent) mc;
        return routeComponent.getRouteMapReceiver();
    }


    public void createUnit() { 

            Marker m = new Marker(getMapView().getPointWithElevation(), UUID.randomUUID().toString());
            Log.d(TAG, "creating a new unit marker for: " + m.getUID());
            m.setType("a-f-G-U-C-I");
            m.setMetaBoolean("readiness", true);
            m.setMetaBoolean("archive", true);
            m.setMetaString("how", "h-g-i-g-o");
            m.setMetaBoolean("editable", true);
            m.setMetaBoolean("movable", true);
            m.setMetaBoolean("removable", true);
            m.setMetaString("entry", "user");
            m.setMetaString("callsign", "Test Marker");
            m.setTitle("Test Marker");
            m.setMetaString("menu", getMenu());

            MapGroup _mapGroup = getMapView().getRootGroup()
                .findMapGroup("Cursor on Target")
                .findMapGroup("Friendly");
            _mapGroup.addItem(m);

            m.persist(getMapView().getMapEventDispatcher(), null,
                    this.getClass());

            Intent new_cot_intent = new Intent();
            new_cot_intent.setAction("com.atakmap.android.maps.COT_PLACED");
            new_cot_intent.putExtra("uid", m.getUID());
            com.atakmap.android.ipc.AtakBroadcast.getInstance().sendBroadcast(new_cot_intent);

    }
    private String getMenu() { 
        return "<menu buttonRadius='70' buttonSpan='36' buttonWidth='90' buttonBg='bgs/button.xml'>" + 

               "<button angle='-90' icon='icons/close.png' onClick='actions/cancel.xml' />" + 

               "<button icon='"+getItem("remove.png")+"' onClick='"+getItem("remove.xml")+"' disabled='!{${removable}}' />" + 

               "</menu>";


    }

    public String getMenu2() {
        return "<menu buttonWidth='90' buttonSpan='90' buttonRadius='70' buttonBg='bgs/dark_button.xml'>"+
                "<button angle='0' disabled='!{${removable}}' onClick='"
                + getItem("actions/atsk/atsk_delete_obs.xml")
                + "' icon='icons/delete.png' /> <button onClick='"
                + getItem("actions/atsk/atsk_edit_obs.xml")
                + "' icon='icons/obstruction_edit.png'/> "
                + "<button onClick='actions/cancel.xml' icon='icons/close.png'/> <button onClick='"
                + getItem("actions/atsk/atsk_obs_info.xml") + "' icon='icons/info.png'/> </menu>";
    }




    public String getItem(final String file) {
        try { 
            InputStream is = pluginContext.getAssets().open(file);
            ByteArrayOutputStream outputStream=new ByteArrayOutputStream();
            int size = 0;
            byte[] buffer = new byte[1024];
            
            while((size=is.read(buffer,0,1024))>=0){
                outputStream.write(buffer,0,size);
            }
            is.close();
            buffer=outputStream.toByteArray();

            String data = new String(Base64.encode(buffer, Base64.URL_SAFE | Base64.NO_WRAP));

            return "base64:/" + data;
         } catch (Exception e) { 
            return "";
         }
    }














    public class Renderer extends Thread implements TextureView.SurfaceTextureListener {
        private Object mLock = new Object();        // guards mSurfaceTexture, mDone
        private boolean mDone;

        private int mWidth;     // from SurfaceTexture
        private int mHeight;
        //haar
        private CascadeClassifier haar_faceClassifier = null;
        private CascadeClassifier haar_sideClassifier = null;

        private CascadeClassifier lbp_faceClassifier = null;
        private CascadeClassifier lbp_sideClassifier = null;

        private Mat mat = null;
        private Mat mat1 = null;
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
            myPaint.setStrokeWidth(8);
            myPaint.setStyle(Paint.Style.STROKE);


            Paint borderPaint = new Paint();
            borderPaint.setColor(Color.rgb(0x29, 0x80, 0xb9));
            borderPaint.setStrokeWidth(10);
            borderPaint.setStyle(Paint.Style.STROKE);


            prepareClassifiers();

            CLAHE clahe = Imgproc.createCLAHE(2.0, new Size(8, 8));
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

                    if (mAbsoluteFaceSize == 0) {
                        int height = 0;
                        if (bitmapH != 0) {
                            height = bitmapH / 2;
                        } else {
                            height = canvas.getHeight() / 2;
                        }
                        if (Math.round(height * mRelativeFaceSize) > 0) {
                            mAbsoluteFaceSize = Math.round(height * mRelativeFaceSize);
                        }
                    }


                    if (bitmapW == 0 || bitmapH == 0) {
                        Bitmap b0 = djiVideoSurface.getBitmap();
                        bitmapW = b0.getWidth();
                        bitmapH = b0.getHeight();
                    }

                    Bitmap b = Bitmap.createScaledBitmap(djiVideoSurface.getBitmap(), bitmapW / 2, bitmapH / 2, false);
//                    Bitmap b = djiVideoSurface.getBitmap();
                    if (mat == null) mat = new Mat(b.getWidth(), b.getHeight(), CvType.CV_8UC1);
                    if (mat1 == null) mat1 = new Mat(b.getWidth(), b.getHeight(), CvType.CV_8UC1);
                    Mat grayMat = new Mat();


                    Utils.bitmapToMat(b, mat);
                    Imgproc.cvtColor(mat, mat1, Imgproc.COLOR_BGR2GRAY);
                    clahe.apply(mat1, grayMat);

                    Mat grayMat_flipped = new Mat();
                    flip(grayMat, grayMat_flipped, 1);

                    MatOfRect faces0 = new MatOfRect();
                    haar_faceClassifier.detectMultiScale(grayMat, faces0, 1.1, 4, 2, new Size(mAbsoluteFaceSize, mAbsoluteFaceSize), new Size());
                    MatOfRect faces1 = new MatOfRect();
                    haar_sideClassifier.detectMultiScale(grayMat, faces1, 1.1, 4, 2, new Size(mAbsoluteFaceSize, mAbsoluteFaceSize), new Size());
                    MatOfRect faces2 = new MatOfRect();
                    haar_sideClassifier.detectMultiScale(grayMat_flipped, faces2, 1.1, 4, 2, new Size(mAbsoluteFaceSize, mAbsoluteFaceSize), new Size());

                    //Log.d(TAG,"num faces: " + faces0.size());
                    for (org.opencv.core.Rect face : faces0.toArray()) {
                        int top = (int) (face.tl().y *2) ;
                        int left = (int) (face.tl().x*2) ;
                        int bottom = (int) (face.br().y*2) ;
                        int right = (int) (face.br().x*2) ;
//                        Log.d(TAG,"Drawing: " + left + " , " + top + " , " + right + " , " + bottom);
                        canvas.drawRect(left, top, right, bottom, myPaint);
                    }
                    for (org.opencv.core.Rect face : faces1.toArray()) {
                        int top = (int) (face.tl().y*2);
                        int left = (int) (face.tl().x*2);
                        int bottom = (int) (face.br().y*2);
                        int right = (int) (face.br().x*2);
                        canvas.drawRect(left, top, right, bottom, myPaint);
                    }
                    for (org.opencv.core.Rect face : faces2.toArray()) {
                        int top = (int) (face.tl().y*2);
                        int left = (int) (face.tl().x*2);
                        int bottom = (int) (face.br().y*2);
                        int right = (int) (face.br().x*2);
                        canvas.drawRect(canvas.getWidth() - left, top, canvas.getWidth() - right, bottom, myPaint);
                    }

                    canvas.drawRect(0, 0, canvas.getWidth(), canvas.getHeight(), borderPaint);

                    Log.d(TAG,"Still drawing");

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

        private void prepareClassifiers() {
            try {
                InputStream is = getPluginContext().getResources().openRawResource(R.raw.haarcascade_frontalface_alt);

                File sdCard = Environment.getExternalStorageDirectory();
                File cascadeDir = new File (sdCard.getAbsolutePath() + "/skynet/cascade");
                if (!cascadeDir.isDirectory())cascadeDir.mkdirs();
                File mCascadeFile = new File(cascadeDir, "haarcascade_frontalface_default.xml");
                FileOutputStream os = new FileOutputStream(mCascadeFile);

                byte[] buffer = new byte[4096];
                int bytesRead;
                while ((bytesRead = is.read(buffer)) != -1) {
                    os.write(buffer, 0, bytesRead);
                }
                is.close();
                os.close();
                haar_faceClassifier = new CascadeClassifier(mCascadeFile.getAbsolutePath());
                if (haar_faceClassifier.empty()) {
                    Log.e("classify", "Failed to load face cascade classifier");
                    haar_faceClassifier = null;
                } else
                    Log.i(TAG, "Loaded cascade classifier from " + mCascadeFile.getAbsolutePath());

            }
            catch(Exception e){
                e.printStackTrace();
            }

            try {
                InputStream is = getPluginContext().getResources().openRawResource(R.raw.haarcascade_profileface);
                File sdCard = Environment.getExternalStorageDirectory();
                File cascadeDir = new File (sdCard.getAbsolutePath() + "/skynet/cascade");
                if (!cascadeDir.isDirectory())cascadeDir.mkdirs();
                File mCascadeFile = new File(cascadeDir, "haarcascade_profileface.xml");
                FileOutputStream os = new FileOutputStream(mCascadeFile);

                byte[] buffer = new byte[4096];
                int bytesRead;
                while ((bytesRead = is.read(buffer)) != -1) {
                    os.write(buffer, 0, bytesRead);
                }
                is.close();
                os.close();
                haar_sideClassifier = new CascadeClassifier(mCascadeFile.getAbsolutePath());
                if (haar_sideClassifier.empty()) {
                    Log.e("classify", "Failed to load sideprof cascade classifier");
                    haar_sideClassifier = null;
                } else
                    Log.i(TAG, "Loaded cascade classifier from " + mCascadeFile.getAbsolutePath());
            }
            catch(Exception e){
                e.printStackTrace();
            }
        }



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
           // Log.i("bitmap", "surface tex2: " + st.toString());
            mWidth = width;
            mHeight = height;
            synchronized (mLock) {
                mSurfaceTexture = st;
                mLock.notify();
            }
            //adjustAspectRatio();
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

        private void adjustAspectRatio() {
            Log.i(TAG,"changing aspect ratio");
            int viewWidth = djiVideoSurface.getWidth();
            int viewHeight = djiVideoSurface.getHeight();
            Log.i(TAG,"viewWidth :" + viewWidth + " viewHeight: " +viewHeight);
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
            djiVideoSurface.getTransform(txform);
            txform.setScale((float) newWidth / viewWidth, (float) newHeight / viewHeight);
            //txform.postRotate(10);          // just for fun
            txform.postTranslate(xoff, yoff);
            djiVideoSurface.setTransform(txform);
        }
    }




}
