package com.atakmap.android.UasDJI.plugin;

import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedList;

import com.atakmap.android.UasDJI.HelloWorldMapComponent;
import com.atakmap.android.UasDJI.HelloWorldWidget;
import com.atakmap.android.maps.MapComponent;
import com.atakmap.android.maps.MapView;
import com.example.chueh.aidl.atakservice.IATAKService;


import dji.sdk.Codec.DJICodecManager;
import transapps.maps.plugin.lifecycle.Lifecycle;
import android.app.Activity;
import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.res.Configuration;
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
    static final int FALSE = 000;
    static final int TRUE = 111;


    public IATAKService ATAKService;
    ServiceConnection serviceConnection;
    boolean mBound = false;
    Messenger mService = null;

    
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
        Looper.prepare();
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

    public int callservice(int a, int b){
        try {
            return ATAKService.add(a,b);
        } catch (RemoteException e) {
            return -1;
        }
    }
    void initConnection() {

         mActivityMessenger = new Messenger(
                new ActivityHandler());
//        AddServiceConnection = new ServiceConnection() {
//
//            @Override
//            public void onServiceDisconnected(ComponentName name) {
//                // TODO Auto-generated method stub
//                ATAKService = null;
//                Toast.makeText(pluginContext, "Service Disconnected",
//                        Toast.LENGTH_SHORT).show();
//                //Log.d("IRemote", "Binding - Service disconnected");
//            }
//
//            @Override
//            public void onServiceConnected(ComponentName name, IBinder service) {
//                // TODO Auto-generated method stub
//                ATAKService = IATAKService.Stub.asInterface((IBinder) service);
//                Toast.makeText(pluginContext,
//                        "ATAK Service Connected", Toast.LENGTH_SHORT)
//                        .show();
//               // Log.d("IRemote", "Binding is done - Service connected");
//            }
//        };
//        if (ATAKService == null) {
//            Intent it = new Intent();
//            it.setAction("service.ATAKService");
//          //  pluginContext.startService(it);
//            // binding to remote service
//            pluginContext.bindService(it, AddServiceConnection, Service.BIND_AUTO_CREATE);
//        }
        serviceConnection = new ServiceConnection() {
            public void onServiceConnected(ComponentName className, IBinder service) {
                // This is called when the connection with the service has been
                // established, giving us the object we can use to
                // interact with the service.  We are communicating with the
                // service using a Messenger, so here we get a client-side
                // representation of that from the raw IBinder object.
                Log.d(TAG,"Connected to service!");
                mService = new Messenger(service);
                mBound = true;

            }

            public void onServiceDisconnected(ComponentName className) {
                // This is called when the connection with the service has been
                // unexpectedly disconnected -- that is, its process crashed.
                Log.d(TAG,"Disconnected from service!");
                mService = null;
                mBound = false;
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
            Log.d(TAG,"msg from service");
            switch (msg.what) {
                case MSG_STREAM: {
                    Log.d(TAG,"Received pfd from service");
                    ParcelFileDescriptor pfd = msg.getData().getParcelable("pfd");
                    setReadPfd(pfd);
                    Toast.makeText(pluginContext, "Conencted to DJI app", Toast.LENGTH_SHORT).show();
                }
            }
        }

    }

    public void setReadPfd(ParcelFileDescriptor readPfd){
        Log.d(TAG,"pipe created, registering outputstream");
        inStream = new ParcelFileDescriptor.AutoCloseInputStream(readPfd);
        Log.d(TAG,"in pfd is: " + inStream);

    }

    public void sayHello() {
        Log.d(TAG,"atakapp --HELLO--> service");
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


    public ParcelFileDescriptor startDJIVid() {
        Log.d(TAG,"startDJIvid called in client");
        try {
            return ATAKService.startDJIVid();
        } catch (RemoteException e) {
            e.printStackTrace();
            return null;
        }
    }
}

