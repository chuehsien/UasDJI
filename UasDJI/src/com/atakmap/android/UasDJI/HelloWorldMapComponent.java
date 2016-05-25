
package com.atakmap.android.UasDJI;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;

import com.atakmap.android.maps.MapView;
import com.atakmap.android.dropdown.DropDownMapComponent;

import android.util.Log;

public class HelloWorldMapComponent extends DropDownMapComponent {

    public static final String TAG = "HelloWorldMapComponent";

    public Context pluginContext;

    public void onCreate(final Context context, Intent intent, final MapView view) {
        super.onCreate(context, intent, view);
        pluginContext = context;

        HelloWorldDropDownReceiver ddr = new HelloWorldDropDownReceiver(view,context);

        Log.d(TAG, "registering the show hello world filter");
        IntentFilter ddFilter = new IntentFilter();
        ddFilter.addAction(HelloWorldDropDownReceiver.SHOW_HELLO_WORLD);
        this.registerReceiver(context, ddr, ddFilter);
        Log.d(TAG, "registered the show hello world filter");

    }

    @Override
    protected void onDestroyImpl(Context context, MapView view) {
        super.onDestroyImpl(context, view);


    }

}
