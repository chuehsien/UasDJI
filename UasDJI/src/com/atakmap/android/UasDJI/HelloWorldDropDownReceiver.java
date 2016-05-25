
package com.atakmap.android.UasDJI;

import android.content.Context;
import android.content.Intent;
import android.graphics.SurfaceTexture;
import android.os.ParcelFileDescriptor;
import android.os.RemoteException;
import android.view.LayoutInflater;
import android.view.TextureView;
import android.view.View;
import android.app.AlertDialog;
import android.widget.ArrayAdapter;
import android.widget.Button;

import android.widget.TextView;
import android.widget.Toast;

import com.atakmap.android.UasDJI.DJI.DJIBackgroundApp;
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
import java.io.IOException;
import java.io.InputStream;
import java.io.ByteArrayOutputStream;


import com.atakmap.android.routes.Route;
import com.atakmap.android.routes.RouteMapComponent;
import com.atakmap.android.routes.RouteMapReceiver;

import android.util.Log;

import java.io.InputStreamReader;
import java.util.UUID;

import dji.sdk.Codec.DJICodecManager;


public class HelloWorldDropDownReceiver extends DropDownReceiver implements
        OnStateListener {

    public static final String TAG = "HelloWorldDropDownReceiver";

    public static final String SHOW_HELLO_WORLD = "com.atakmap.android.helloworld.SHOW_HELLO_WORLD";
    private final View helloView;
    private static Context pluginContext;

    private  Route r;
    private TextureView mVideoSurface;
    protected DJICodecManager mCodecManager = null;
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
        tv = (TextView) helloView.findViewById(R.id.textview);
        Button connectB = (Button) helloView.findViewById(R.id.connect);
        connectB.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                HelloWorldLifecycle.getInstance().sayHello();
            }
        });

        Button streamB = (Button) helloView.findViewById(R.id.stream);
        streamB.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                Log.d(TAG,"service: Started receiverThread");
                mReceiver = new ReceiverThread(HelloWorldLifecycle.getInstance().inStream,tv);
                mReceiver.start();

                HelloWorldLifecycle.getInstance().startStream();
            }
        });


        if (tv == null) Log.d(TAG,"tv is null!");
        else Log.d(TAG,"tv: " + tv.getText().toString());
//        final Button fly = (Button)helloView.findViewById(R.id.fly);
//        fly.setOnClickListener(new OnClickListener() {
//
//            @Override
//            public void onClick(View v) {
//                  new Thread(new Runnable() {
//                      public void run() {
//                         getMapView().getMapController().zoomTo(.00001d, false);
//                         for (int i = 0; i < 20; ++i) {
//                              getMapView().getMapController().panTo(new GeoPoint(42, -79 - (double)i/100), false);
//                              try {
//                                  Thread.sleep(1000);
//                              } catch (Exception e) { }
//                         }
//                      }
//                  }).start();
//            }
//        });
//
//        final Button wheel = (Button)helloView.findViewById(R.id.specialWheelMarker);
//        wheel.setOnClickListener(new OnClickListener() {
//            @Override
//            public void onClick(View v) {
//                  createUnit();
//
//            }
//        });
//
//        final Button listRoutes = (Button)helloView.findViewById(R.id.listRoutes);
//        listRoutes.setOnClickListener(new OnClickListener() {
//             @Override
//             public void onClick(View v) {
//                RouteMapReceiver routeMapReceiver = getRouteMapReceiver();
//                if (routeMapReceiver == null)
//                      return;
//
//
//                AlertDialog.Builder builderSingle = new AlertDialog.Builder(
//                        mapView.getContext());
//                builderSingle.setTitle("Select a Route");
//                final ArrayAdapter<String> arrayAdapter = new ArrayAdapter<String>(
//                        pluginContext,
//                        android.R.layout.select_dialog_singlechoice);
//
//                for (Route route : routeMapReceiver.getCompleteRoutes()) {
//                    arrayAdapter.add(route.getTitle());
//                }
//                builderSingle.setNegativeButton("Cancel",
//                        new DialogInterface.OnClickListener() {
//
//                            @Override
//                            public void onClick(DialogInterface dialog,
//                                    int which) {
//                                dialog.dismiss();
//                            }
//                        });
//                builderSingle.setAdapter(arrayAdapter, null);
//                builderSingle.show();
//             }
//
//        });
//
//
//        final Button showSearchIcon = (Button)helloView.findViewById(R.id.showSeachIcon);
//        showSearchIcon.setOnClickListener(new OnClickListener() {
//            @Override
//            public void onClick(View v) {
//                Log.d(TAG, "sending broadcast SHOW_MY_WACKY_SEARCH");
//               Intent intent = new Intent("SHOW_MY_WACKY_SEARCH");
//               com.atakmap.android.ipc.AtakBroadcast.getInstance().sendBroadcast(intent);
//
//            }
//        });
//
//        final Button addRoute = (Button)helloView.findViewById(R.id.addXRoute);
//        addRoute.setOnClickListener(new OnClickListener() {
//            @Override
//            public void onClick(View v) {
//                Log.d(TAG, "creating a quick route");
//                GeoPoint sp = getMapView().getPointWithElevation();
//                r = new Route(getMapView(),
//                                    "My Route",
//                                    Color.WHITE, "CP",
//                                    UUID.randomUUID().toString());
//
//
//                Marker m[] = new Marker[5];
//                for (int i = 0; i < 5; ++i) {
//                     GeoPoint x = new GeoPoint(sp.getLatitude() + (i*.0001),
//                                               sp.getLongitude(),
//                                               Altitude.UNKNOWN,
//                                               GeoPoint.CE90_UNKNOWN, GeoPoint.LE90_UNKNOWN,
//                                               GeoPointSource.UNKNOWN);
//
//                     // the first call will trigger a refresh each time across all of the route points
//                     //r.addMarker(Route.createWayPoint(x, UUID.randomUUID().toString()));
//                     m[i] = Route.createWayPoint(x, UUID.randomUUID().toString());
//                }
//                r.addMarkers(0, m);
//
//                MapGroup _mapGroup = getMapView().getRootGroup()
//                     .findMapGroup("Route");
//                _mapGroup.addItem(r);
//
//                r.persist(getMapView().getMapEventDispatcher(), null,
//                       this.getClass());
//                Log.d(TAG, "route created");
//
//
//
//
//            }
//        });
//
//        final Button reRoute = (Button)helloView.findViewById(R.id.reXRoute);
//        reRoute.setOnClickListener(new OnClickListener() {
//            @Override
//            public void onClick(View v) {
//                if (r == null) {
//                   Toast.makeText(getMapView().getContext(), "No Route added during this run",
//                                        Toast.LENGTH_SHORT).show();
//                   return;
//                }
//
//                GeoPoint sp = getMapView().getPointWithElevation();
//                Marker m[] = new Marker[16];
//                for (int i = 1; i < m.length; ++i) {
//                     if (i % 2 == 0) {
//                         GeoPoint x = new GeoPoint(sp.getLatitude() - (i*.0001),
//                                               sp.getLongitude() + (i*.0001),
//                                               Altitude.UNKNOWN,
//                                               GeoPoint.CE90_UNKNOWN, GeoPoint.LE90_UNKNOWN,
//                                               GeoPointSource.UNKNOWN);
//
//                         // the first call will trigger a refresh each time across all of the route points
//                         //r.addMarker(2, Route.createWayPoint(x, UUID.randomUUID().toString()));
//                         m[i-1] = Route.createWayPoint(x, UUID.randomUUID().toString());
//                     } else {
//                         GeoPoint x = new GeoPoint(sp.getLatitude() + (i*.0002),
//                                               sp.getLongitude() + (i*.0002),
//                                               Altitude.UNKNOWN,
//                                               GeoPoint.CE90_UNKNOWN, GeoPoint.LE90_UNKNOWN,
//                                               GeoPointSource.UNKNOWN);
//                         m[i-1] = Route.createControlPoint(x, UUID.randomUUID().toString());
//                     }
//                }
//                r.addMarkers(2, m);
//            }
//        });


    }

    private void manipulateFakeContentProvider() { 
        return;
        // // delete all the records and the table of the database provider
        // String URL = "content://com.javacodegeeks.provider.Birthday/friends";
        // Uri friends = Uri.parse(URL);
        // int count = pluginContext.getContentResolver().delete(
        //          friends, null, null);
        // String countNum = "Javacodegeeks: "+ count +" records are deleted.";
        // Toast.makeText(getMapView().getContext(),
        //           countNum, Toast.LENGTH_LONG).show();

        // String[] names = new String[] { "Joe", "Bob", "Sam", "Carol" };
        // String[] dates = new String[] { "01/01/2001", "01/01/2002", "01/01/2003", "01/01/2004" };
        // for (int i = 0; i < names.length; ++i) { 
        //     ContentValues values = new ContentValues();
        //     values.put(BirthProvider.NAME, names[i]);
        //     values.put(BirthProvider.BIRTHDAY, dates[i]);
        //     Uri uri = pluginContext.getContentResolver().insert(BirthProvider.CONTENT_URI, values);
        //     Toast.makeText(getMapView().getContext(), 
        //        "Javacodegeeks: " + uri.toString() + " inserted!", Toast.LENGTH_LONG).show();
        // }


        
    }

    /**************************** PUBLIC METHODS *****************************/

    public void disposeImpl() {
    }

    class ReceiverThread extends Thread{
        byte[] buffer = new byte[2050]; // Adjust if you want
        int bytesRead;
        private InputStream is;
        private TextView textv;
        ReceiverThread(InputStream is, TextView tv){

            super();
            this.is = is;
            this.textv = tv;
        }
        @Override
        public void run(){
            try {
                Log.d(TAG,"service: Thread running");
                while ((bytesRead = is.read(buffer)) != -1) {
//                    Log.d(TAG,"service: Thread received " + bytesRead + " bytes");
                    HelloWorldLifecycle.getInstance().getActivity().runOnUiThread(new Runnable() {
                        public void run() {
                            textv.setText(bytesRead+"");
                        }
                    });
                }
                Log.d(TAG,"service: done reading from inputstream");
            }catch (IOException e){
                e.printStackTrace();
                Log.d(TAG,"service: copyStream failed!");
                return;
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



}
