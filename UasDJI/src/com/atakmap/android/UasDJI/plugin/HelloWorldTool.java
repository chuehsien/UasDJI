package com.atakmap.android.UasDJI.plugin;


import com.atakmap.android.UasDJI.R;
import com.atakmap.android.ipc.AtakBroadcast;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.view.ViewGroup;
import transapps.mapi.MapView;
import transapps.maps.plugin.tool.Group;
import transapps.maps.plugin.tool.Tool;
import transapps.maps.plugin.tool.ToolDescriptor;

public class HelloWorldTool extends Tool implements ToolDescriptor {

    private final Context context;

    public HelloWorldTool(Context context) {
        this.context = context;
    }

    @Override
    public String getDescription() {
        return "HelloWorld Tool";
    }

    @Override
    public Drawable getIcon() {
        return (context == null) ? null : context.getResources().getDrawable(R.drawable.ic_launcher);
    }

    @Override
    public Group[] getGroups() {
        return new Group[] {Group.GENERAL};
    }

    @Override
    public String getShortDescription() {
        return "HelloWorld Tool";
    }

    @Override
    public Tool getTool() {
        return this;
    }

    @Override
    public void onActivate(Activity arg0, MapView arg1, ViewGroup arg2, Bundle arg3,
            ToolCallback arg4) {

        // Hack to close the dropdown that automatically opens when a tool
        // plugin is activated.
        if (arg4 != null) {
            arg4.onToolDeactivated(this);
        }
        

        // Intent to launch the dropdown or tool

        //arg2.setVisibility(ViewGroup.INVISIBLE);
        Intent i = new Intent("com.atakmap.android.helloworld.SHOW_HELLO_WORLD");
        AtakBroadcast.getInstance().sendBroadcast(i);


    }

    @Override
    public void onDeactivate(ToolCallback arg0) {}

}
