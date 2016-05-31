package com.atakmap.android.UasDJI.plugin;

import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedList;

import com.atakmap.android.UasDJI.HelloWorldDropDownReceiver;
import com.atakmap.android.UasDJI.HelloWorldMapComponent;
import com.atakmap.android.UasDJI.HelloWorldWidget;
import com.atakmap.android.maps.MapComponent;
import com.atakmap.android.maps.MapView;

import transapps.maps.plugin.lifecycle.Lifecycle;
import android.app.Activity;
import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.res.Configuration;
import android.os.CountDownTimer;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.Messenger;
import android.os.ParcelFileDescriptor;
import android.os.RemoteException;
import android.util.Log;
import android.widget.Toast;

public class HelloWorldLifecycle implements Lifecycle {


    static final int MSG_SAY_HELLO = 1;
    static final int MSG_MESSENGER_DJIAPP = 21;
    static final int MSG_MESSENGER_ATAKAPP = 22;
    static final int MSG_STREAM = 3;
    static final int MSG_IS_DJIAPP_CONNECTED = 4;
    static final int MSG_IS_ATAKAPP_CONNECTED = 5;
    static final int MSG_START_BRIDGE = 6;
    static final int MSG_STOP_BRIDGE = 7;
    static final int MSG_HELLO_FROM_ATAK = 8;
    static final int MSG_HELLO_FROM_DJI = 9;
    static final int MSG_HELLO_FROM_SERVICE = 10;
    static final int FALSE = 000;
    static final int TRUE = 111;

    private CountDownTimer helloTimer;
//    public IATAKService ATAKService;
    ServiceConnection serviceConnection;
    boolean mBound = false;
    Messenger mService = null;
    private Handler mHandler = null;
    
    private final Context pluginContext;
    private final Collection<MapComponent> overlays;
    private MapView mapView;
    private Activity mainActivity;
    public InputStream inStream;

    private final static String TAG = "HelloWorldLifecycle";

    private static HelloWorldLifecycle _self;

    public static HelloWorldLifecycle getInstance(){
        return _self;
    }

    public Context getPluginContext(){
        return HelloWorldLifecycle.getInstance().pluginContext;
    }

    public Activity getActivity(){
        return HelloWorldLifecycle.getInstance().mainActivity;
    }


    public HelloWorldLifecycle(Context ctx) {
        this.pluginContext = ctx;
        this.overlays = new LinkedList<MapComponent>();
        this.mapView = null;

        _self = this;
    }

    @Override
    public void onConfigurationChanged(Configuration arg0) {
        for(MapComponent c : this.overlays)
            c.onConfigurationChanged(arg0);
    }

    @Override
    public void onCreate(final Activity arg0, final transapps.mapi.MapView arg1) {
        if(arg1 == null || !(arg1.getView() instanceof MapView)) {
            Log.w(TAG, "This plugin is only compatible with ATAK MapView");
            return;
        }
        initConnection();
        this.mainActivity = arg0;
        this.mapView = (MapView)arg1.getView();
        HelloWorldLifecycle.this.overlays.add(new HelloWorldMapComponent());
        HelloWorldLifecycle.this.overlays.add(new HelloWorldWidget());

        // create components
        Iterator<MapComponent> iter = HelloWorldLifecycle.this.overlays.iterator();
        MapComponent c;
        while(iter.hasNext()) {
            c = iter.next();
            try {
                c.onCreate(HelloWorldLifecycle.this.pluginContext, 
                           arg0.getIntent(), 
                           HelloWorldLifecycle.this.mapView);
            } catch(Exception e) {
               Log.w(TAG, "Unhandled exception trying to create overlays MapComponent", e);
               iter.remove();
            }
        }

    }

    private void sayHelloToService(){
        Log.d(TAG,"hello to service!");
        Message msg = Message.obtain(null, MSG_HELLO_FROM_ATAK, 0, 0);
        try {
            mService.send(msg);
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }


    void initConnection() {

         mActivityMessenger = new Messenger(new ActivityHandler());

        serviceConnection = new ServiceConnection() {
            public void onServiceConnected(ComponentName className, IBinder service) {
                Log.d(TAG,"Connected to DJI service!");
                mService = new Messenger(service);
                mBound = true;
                helloTimer = new CountDownTimer(5000,4000) {
                    @Override
                    public void onTick(long l) {
                        //
                    }

                    @Override
                    public void onFinish() {
                        sayHelloToService();
                        helloTimer.cancel();
                        helloTimer.start();
                    }
                }.start();

            }

            public void onServiceDisconnected(ComponentName className) {
                Log.d(TAG,"Disconnected from service!");
                mService = null;
                mBound = false;
                inStream = null;
            }
        };

        if (!mBound) {
            Log.d(TAG,"binding to service");
            Intent intent = new Intent("com.example.chueh.skynetdji.ATAKServiceHandler");
            intent.setPackage("com.example.chueh.skynetdji");
            // intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            getPluginContext().bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE);

        }


    }

    private Messenger mActivityMessenger;

    private class ActivityHandler extends Handler {
//        private final WeakReference<MainActivity> mActivity;

//        public ActivityHandler(MainActivity activity) {
//            mActivity = new WeakReference<MainActivity>(activity);
//        }

        @Override
        public void handleMessage(Message msg) {
            //Log.d(TAG,"msg from service");
            switch (msg.what) {
                case MSG_STREAM: {

                    ParcelFileDescriptor pfd = msg.getData().getParcelable("pfd");
                    if (pfd!=null)setReadPfd(pfd);
                    String status = msg.getData().getString("status");
                    HelloWorldDropDownReceiver.getInstance().updateProductStatus(status);
                }

                case MSG_HELLO_FROM_SERVICE:{
                    String status = msg.getData().getString("status");
                    HelloWorldDropDownReceiver.getInstance().updateProductStatus(status);
                }
            }
        }

    }

    public void setReadPfd(ParcelFileDescriptor readPfd){
        Log.d(TAG,"pipe created, registering outputstream");
        inStream = new ParcelFileDescriptor.AutoCloseInputStream(readPfd);
        Log.d(TAG,"in pfd is: " + readPfd.toString());

    }

    public void initConnectionWithService() {
        //Log.d(TAG,"atakapp --HELLO--> service");
        if (!mBound) return;
        // Create and send a message to the service, using a supported 'what' value
        Message msg = Message.obtain(null, MSG_MESSENGER_ATAKAPP, 0, 0);
        msg.replyTo = mActivityMessenger;
        try {
            mService.send(msg);
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }

    public void startStream() {
        Log.d(TAG,"atakapp --STARTSTREAM--> service");
        if (!mBound) return;
        // Create and send a message to the service, using a supported 'what' value
        Message msg = Message.obtain(null, MSG_START_BRIDGE, 0, 0);
        try {
            mService.send(msg);
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }



    @Override
    public void onDestroy() {
        for(MapComponent c : this.overlays)
            c.onDestroy(this.pluginContext, this.mapView);

        if (mBound) {
            getActivity().unbindService(serviceConnection);
            mBound = false;
        }


    }

    @Override
    public void onFinish() {
        // XXX - no corresponding MapComponent method
    }

    @Override
    public void onPause() {
        for(MapComponent c : this.overlays)
            c.onPause(this.pluginContext, this.mapView);
    }

    @Override
    public void onResume() {
        for(MapComponent c : this.overlays)
            c.onResume(this.pluginContext, this.mapView);
    }

    @Override
    public void onStart() {
        for(MapComponent c : this.overlays)
            c.onStart(this.pluginContext, this.mapView);

    }

    @Override
    public void onStop() {
        for(MapComponent c : this.overlays)
            c.onStop(this.pluginContext, this.mapView);
    }
}

