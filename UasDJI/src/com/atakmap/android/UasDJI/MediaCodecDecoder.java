package com.atakmap.android.UasDJI;

import android.graphics.Matrix;
import android.graphics.SurfaceTexture;
import android.media.MediaCodec;
import android.media.MediaFormat;
import android.os.Build;
import android.util.Log;
import android.view.Surface;
import android.view.TextureView;

import com.atakmap.android.UasDJI.plugin.HelloWorldLifecycle;
import com.atakmap.android.fires.HostileManagerDropDownReceiver;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;

/**
 * Created by chue on 5/25/16.
 */
public class MediaCodecDecoder extends Thread implements TextureView.SurfaceTextureListener {

    private static final String TAG = MediaCodecDecoder.class.getName();

    private static final int STILL_LOOKING = 0;
    private static final int SPS_HEADER = 1;
    private static final int SPS_BODY = 2;
    private static final int PPS_BODY = 3;
    private static final int RANDOM_HEADER = 4;
    private static final int LOOK_FOR_IDR = 5;
    private static final int IDR_BODY =6;
    private static final int RANDOMBODY = 7;

    private static final byte IDR_TAG = 37;//0x25 in decimal
    private static final byte PPS_TAG = 40;//0x28 in decimal
    private static final byte SPS_TAG = 39;//0x27 in decimal

    private int stage; //track if SPS/PPS found etc



    private static final boolean VERBOSE = true;
    private static final String LOG_TAG = MediaCodecDecoder.class.getSimpleName();
    private static final String VIDEO_FORMAT = "video/avc"; // h.264
    private static final long mTimeoutUs = 10000l;

    private MediaCodec mMediaCodec;
    Surface mSurface;
    volatile boolean m_bConfigured;
    volatile boolean m_bRunning;
    long startMs;
    private boolean firstRun;


    private int width,height;

    byte[] workerArr = new byte[1024];
    int workerArrI = 0;

    ByteArrayOutputStream byteCollected,AUDCollected;

    public void OnRecvEncodedData(byte[] encodedData, int bytesRead) {
        if (!m_bConfigured) {
            Configure(mSurface, width, height);
        }
        if (m_bConfigured) {
            decodeData(encodedData,bytesRead);
        }
    }

    public void setSurface(Surface surface) {
        if (mSurface == null) {
            mSurface = surface;
        }
    }

    public void Start() {
        if(m_bRunning)
            return;
        m_bRunning = true;
        start();
    }

    public void Stop() {
        if(!m_bRunning)
            return;
        m_bRunning = false;
        mMediaCodec.stop();
        mMediaCodec.release();
    }

    private void Configure(Surface surface, int width, int height) {
        if (m_bConfigured) {
            Log.e(LOG_TAG, "Decoder is already configured");
            return;
        }
        if (mSurface == null) {
            Log.d(LOG_TAG, "Surface is not available/set yet.");
            return;
        }
        MediaFormat format = MediaFormat.createVideoFormat(VIDEO_FORMAT, width, height);

        byte[] buffer = new byte[800];
        int bytesRead;
        workerArrI = 0;

        ByteArrayOutputStream firstFrame = new ByteArrayOutputStream();

        // need to send the custom iframe provided by dji
        InputStream inputStream = HelloWorldLifecycle.getInstance().getPluginContext().getResources().openRawResource(R.raw.iframe_960x720_3s);
        try {
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                firstFrame.write(buffer,0,bytesRead);
            }
        }
        catch (IOException e) {
            e.printStackTrace();
        }

        try {
            mMediaCodec = MediaCodec.createDecoderByType(VIDEO_FORMAT);
        } catch (IOException e) {
            Log.d(LOG_TAG, "Failed to create codec: " + e.getMessage());
        }

        startMs = System.currentTimeMillis();

        byte[] b = firstFrame.toByteArray();
        format.setByteBuffer("csd-0", ByteBuffer.wrap(b));

        mMediaCodec.configure(format, surface, null, 0);
        if (VERBOSE) Log.d(LOG_TAG, "Decoder configured.");

        mMediaCodec.start();
        Log.d(LOG_TAG, "Decoder initialized.");

        m_bConfigured = true;
        byteCollected = new ByteArrayOutputStream();
        AUDCollected = new ByteArrayOutputStream();

    }


    private byte[] miniworkerArr = new byte[]{0,0,0,0,0};

    private boolean checkState(byte b){
        if (stage == PPS_BODY||stage == SPS_BODY){
            workerArr[workerArrI++] = b;
        }


            //shift things 1 down (e.g. index 4 goes to index 3)
        System.arraycopy(miniworkerArr, 1, miniworkerArr, 0, 4);
        miniworkerArr[4]=b;

        if (stage == IDR_BODY && miniworkerArr[1]==00 && miniworkerArr[2]==00 && miniworkerArr[3]==00 && miniworkerArr[4]==01) {



        }
        if (stage == LOOK_FOR_IDR){
            if (b == IDR_TAG){
                stage = IDR_BODY;
            }else{
                stage = RANDOMBODY;
            }
            return false;
        }

        if (stage == SPS_BODY && miniworkerArr[1]==00 && miniworkerArr[2]==00 && miniworkerArr[3]==00 && miniworkerArr[4]==01){
            //end of sps+pps, look out for next frame
            stage = LOOK_FOR_IDR;
            return false;
        }

        if (stage == STILL_LOOKING && miniworkerArr[0] == 00 && miniworkerArr[1] == 00 && miniworkerArr[2] == 00 && miniworkerArr[3] == 01 && miniworkerArr[4] == PPS_TAG){
            //record in main worker array, change stage
            for (int k = 0; k < 5; k++) {
                workerArr[workerArrI++] = miniworkerArr[k];
            }
            stage = PPS_BODY;
            return false;
        }

        if (stage == PPS_BODY && miniworkerArr[0] == 00 && miniworkerArr[1] == 00 && miniworkerArr[2] == 00 && miniworkerArr[3] == 01 && miniworkerArr[4] == SPS_TAG) {
            //change stage
            //SPS header already in workerArr due to start of function.
            stage = SPS_BODY;
            return false;
        }
        return false;

    }

    private void decodeData(byte[] data, int bytesRead) {
        if (!m_bConfigured) {
            Log.e(LOG_TAG, "Decoder is not configured yet.");
            return;
        }
        int inIndex = mMediaCodec.dequeueInputBuffer(mTimeoutUs);
        //Log.d(LOG_TAG,"Inputbuffer: " + inIndex);
        if (inIndex >= 0) {
            ByteBuffer buffer;
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
                buffer = mMediaCodec.getInputBuffers()[inIndex];
                buffer.clear();
            } else {
                buffer = mMediaCodec.getInputBuffer(inIndex);
            }
            if (buffer != null) {
                buffer.put(data);
                long presentationTimeUs = System.currentTimeMillis() - startMs;
                mMediaCodec.queueInputBuffer(inIndex, 0, data.length, presentationTimeUs, 0);
            }
        }
    }

    @Override
    public void run() {
        try {
            MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
            while(m_bRunning) {
                if(m_bConfigured) {
                    int outIndex = mMediaCodec.dequeueOutputBuffer(info, mTimeoutUs);
                    if(outIndex >= 0) {
                        mMediaCodec.releaseOutputBuffer(outIndex, true);
                    }
                } else {
                    try {
                        Thread.sleep(10);
                    } catch (InterruptedException ignore) {
                    }
                }
            }
        } finally {
            Stop();
        }
    }

    @Override
    public void onSurfaceTextureAvailable(SurfaceTexture surfaceTexture, int i, int i1) {
        setSurface(new Surface(surfaceTexture));
        Log.d(TAG,"onSurfacetexAvailable: " + i + " , " + i1);
        width = i;
        height = i1;
    }

    @Override
    public void onSurfaceTextureSizeChanged(SurfaceTexture surfaceTexture, int i, int i1) {

    }

    @Override
    public boolean onSurfaceTextureDestroyed(SurfaceTexture surfaceTexture) {
        return false;
    }

    @Override
    public void onSurfaceTextureUpdated(SurfaceTexture surfaceTexture) {

    }


//    private void adjustAspectRatio() {
//        Log.i("FPV","changing aspect ratio");
//        int viewWidth = mVideoSurface.getWidth();
//        int viewHeight = mVideoSurface.getHeight();
//        Log.i("FPV","viewWidth :" + viewWidth + " viewHeight: " +viewHeight);
//        double aspectRatio = (double) VIDEO_HEIGHT / VIDEO_WIDTH;
//
//        int newWidth, newHeight;
//        if (viewHeight > (int) (viewWidth * aspectRatio)) {
//            // limited by narrow width; restrict height
//            newWidth = viewWidth;
//            newHeight = (int) (viewWidth * aspectRatio);
//        } else {
//            // limited by short height; restrict width
//            newWidth = (int) (viewHeight / aspectRatio);
//            newHeight = viewHeight;
//        }
//        int xoff = (viewWidth - newWidth) / 2;
//        int yoff = (viewHeight - newHeight) / 2;
//        Log.v(TAG, "video=" + VIDEO_WIDTH + "x" + VIDEO_HEIGHT +
//                " view=" + viewWidth + "x" + viewHeight +
//                " newView=" + newWidth + "x" + newHeight +
//                " off=" + xoff + "," + yoff);
//
//        Matrix txform = new Matrix();
//        mVideoSurface.getTransform(txform);
//        txform.setScale((float) newWidth / viewWidth, (float) newHeight / viewHeight);
//        //txform.postRotate(10);          // just for fun
//        txform.postTranslate(xoff, yoff);
//        mVideoSurface.setTransform(txform);
//    }
}