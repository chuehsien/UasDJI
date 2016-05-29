package com.atakmap.android.UasDJI;

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
    byte[] firstArr = new byte[77];
    boolean firstNALUsent = false;
    byte[] workerArr = new byte[1024];
    int workerArrI = 0;
    private boolean findingAUD;
    ByteArrayOutputStream byteCollected,AUDCollected;

    private int findAUD(byte[] arr, int length){
        byte[] b = new byte[4];
        for (int i = 0; i <= length - 5; i ++){
            if (arr[i] == 0 && arr[i+1] == 0 && arr[i+2] == 0 && arr[i+3] == 0x01 && arr[i+4] == 0x09) {
                Log.d(TAG,"found AUD");
                return i;
            }
        }

        return -1;

    }

    public void OnRecvEncodedData(byte[] encodedData, int bytesRead) {
        if (!m_bConfigured) {
            Configure(mSurface, width, height);
        }
        if (m_bConfigured) {
//            Log.d(TAG,bytesRead + ": " + HelloWorldDropDownReceiver.bytesToHexString(encodedData,bytesRead));
            //Log.d(TAG,bytesRead + ": ");
            decodeData(encodedData,bytesRead);
        }
    }

    // this is my callback where I am receiving encoded streams from native layer
    public void OnRecvEncodedData_findAUD(byte[] encodedData, int bytesRead) {
        if(!m_bConfigured) {
            Configure(mSurface, width, height);
        }
        if(m_bConfigured) {


            if (findingAUD){
                byteCollected.write(encodedData,0,bytesRead);
                byte[] b = byteCollected.toByteArray();
                int i = findAUD(b,b.length);
                if ( i >= 0 ){
                    //AUD found
                    AUDCollected.reset();
                    AUDCollected.write(b,i,b.length-i);
                    byteCollected.reset();
                    findingAUD = false;
                }else{
//                    Log.d(TAG,"Still looking for AUD");
                    return;
                }

            }else{
//                Log.d(TAG,"Collecting AUD");
                //last 4 bytes on boundary:
                byte[] b = AUDCollected.toByteArray();
                byte[] b1 = new byte[8];
                if (b.length<4){
                    System.arraycopy(b,b.length,b1,0,b.length);
                }else {
                    System.arraycopy(b, b.length - 4, b1, 0, 4); //move last 4 bytes.
                }
                if (bytesRead<4){
                    if (b.length<4)  {
                        System.arraycopy(encodedData,0,b1,b.length,bytesRead);
                        int length = b.length + bytesRead;
                        for (int i = length; i < 8; i++){
                            b1[i] = -1;
                        }
                    }
                    else {
                        System.arraycopy(encodedData,0,b1,4,bytesRead);
                        for (int i = bytesRead + 4; i < 8; i++){
                            b1[i] = -1;
                        }
                    }
                }else{
                    if (b.length<4) {
                        System.arraycopy(encodedData,0,b1,b.length,4); //move first 4 bytes.
                        int length = b.length + 4;
                        for (int i = length; i < 8; i++){
                            b1[i] = -1;
                        }
                    }
                    else System.arraycopy(encodedData,0,b1,4,4);
                }

                // check if AUD was at boundary
                int j = findAUD(b1,8);
                if (j >= 0){
                    Log.d(TAG,"Found AUD at boundary");
                    //seperate the chunk after the AUD (including the n1ew AUD header)
                    for (int k = 0; k < j; k++){
                        AUDCollected.write(b1[k]);
                    }
                    byte[] toSend = AUDCollected.toByteArray();
                    Log.d(TAG,"AUD: " + toSend.length);
                    decodeData(toSend,toSend.length);
                    AUDCollected.reset();
                    for (int k = j; k < 8; k++){
                        AUDCollected.write(b1[k]);
                    }
                    AUDCollected.write(encodedData,0,bytesRead);
                }else{
                    int i = findAUD(encodedData,bytesRead);
                    if (i >= 0){
                        //everything before i goes into AUDcollected
                        for (int k = 0; k < i; k++){
                            AUDCollected.write(encodedData[k]);
                        }
                        byte[] toSend = AUDCollected.toByteArray();
                        Log.d(TAG,"AUD: " + toSend.length);
                        Log.d(TAG,"AUD: " + HelloWorldDropDownReceiver.bytesToHexString(toSend,toSend.length));
                        decodeData(toSend,toSend.length);
                        AUDCollected.reset();

                        for (int k = i; k < bytesRead; k++){
                            AUDCollected.write(encodedData[k]);
                        }
                    }
                }

            }
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

//         byte[] sps = {0x00,0x00,0x00,0x01 ,0x27 ,0x64 ,0x00 ,0x28,0xAC, 0xb4 ,0x07 ,0x80, 0xb7 ,0x60 ,0x2d ,0x40 ,0x40 ,0x40 ,0x50 ,0x00 ,0x00 ,0x3e ,0x90 ,0x00 ,0x0e ,0xa6 ,0x0e ,0x86 ,0x00 ,0x7a ,0x10 ,0x00 ,0x7a ,0x12 ,0xbb ,0xcb ,0x8d ,0x0c ,0x00 ,0xf4 ,0x20 ,0x00 ,0xf4 ,0x25 ,0x77 ,0x97 ,0x0f ,0x84 ,0x42 ,0x28 ,0xf0};
//

        //byte[] csd_0 = { 0, 0, 0, 1, 39, 100, 0, 40, 172, 180, 2, 128, 45, 216, 11, 80, 16, 16, 20, 0 ,0, 15, 164, 0, 3, 169, 131, 161, 128, 30, 132, 0, 30, 132, 174, 242, 227, 67, 0, 61, 8 ,0 ,61, 93, 229, 195, 225, 16, 138, 60};
        byte[] buffer = new byte[800]; // Adjust if you want
        int bytesRead;
        workerArrI = 0;
        byte[] csd_0 = new byte[59];
//        byte[] firstFrame = new byte[781];

        ByteArrayOutputStream firstFrame = new ByteArrayOutputStream();

        InputStream inputStream = HelloWorldLifecycle.getInstance().getPluginContext().getResources().openRawResource(R.raw.iframe_960x720_3s);
        try {
            int i = 0;
            while ((bytesRead = inputStream.read(buffer)) != -1) {
//                Log.d(TAG,"from file: "  + bytesRead);
                firstFrame.write(buffer,0,bytesRead);

            }
//            Log.d(TAG,i + " read from IDR file");
        }
        catch (IOException e) {
            e.printStackTrace();
        }


//        char[] csd_0 = {0x00, 0x00, 0x00,0x01 ,0x27 ,0x64 ,0x00 ,0x28 ,0xac ,0xb4 ,0x02 ,0x80 ,0x2d ,0xd8 ,0x0b ,0x50 ,0x10 ,0x10 ,0x14 ,0x00 ,0x00 ,0x0f ,0xa4 ,0x00 ,0x03 ,0xa9 ,0x83 ,0xa1 ,0x80 ,0x1e ,0x84 ,0x00 ,0x1e ,0x84 ,0xae ,0xf2 ,0xe3 ,0x43 ,0x00 ,0x3d ,0x08 ,0x00 ,0x3d ,0x09 ,0x5d ,0xe5 ,0xc3 ,0xe1 ,0x10 ,0x8a ,0x3c, 0x00,0x00 ,0x00 ,0x01 ,0x28 ,0xee ,0x38 ,0x30};

        //Log.d(TAG,"csd_0: " + HelloWorldDropDownReceiver.bytesToHexString(csd_0,59));
        //Log.d(TAG,"firstFrame : " + HelloWorldDropDownReceiver.bytesToHexString(firstFrame,722));
//        format.setByteBuffer("csd-0", ByteBuffer.wrap(csd_0));
//        format.setByteBuffer("csd-1", ByteBuffer.wrap(new String(csd_1).getBytes()));

        try {
            mMediaCodec = MediaCodec.createDecoderByType(VIDEO_FORMAT);
        } catch (IOException e) {
            Log.d(LOG_TAG, "Failed to create codec: " + e.getMessage());
        }

        startMs = System.currentTimeMillis();
        mMediaCodec.configure(format, surface, null, 0);
        if (VERBOSE) Log.d(LOG_TAG, "Decoder configured.");

        mMediaCodec.start();
        Log.d(LOG_TAG, "Decoder initialized.");

        m_bConfigured = true;
        byteCollected = new ByteArrayOutputStream();
        AUDCollected = new ByteArrayOutputStream();
        findingAUD = true;
        byte[] b = firstFrame.toByteArray();
        decodeData(b,b.length);
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


    @SuppressWarnings("deprecation")
    private void decodeData(byte[] data, int bytesRead) {
        if (!m_bConfigured) {
            Log.e(LOG_TAG, "Decoder is not configured yet.");
            return;
        }
//        Log.d(TAG,"In decode Data");
//        if (firstNALUsent == false){
//            stage = STILL_LOOKING;
//            for (int j = 0; j < bytesRead; j++){
//
//                workerArr[workerArrI++] = data[j];
//
//                if  (checkState(data[j])){
//                    //first sps+pps+next header. need to decide whether to add in iframe.
//                }
//
//
//            }
//        }

        int inIndex = mMediaCodec.dequeueInputBuffer(mTimeoutUs);
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

    private static boolean bKeyFrame(byte[] frameData) {
        return ( ( (frameData[4] & 0xFF) & 0x0F) == 0x07);
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
}