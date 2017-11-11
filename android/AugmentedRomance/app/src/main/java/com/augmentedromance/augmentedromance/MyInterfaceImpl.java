package com.augmentedromance.augmentedromance;

import android.location.Location;
import android.util.Log;

import com.vidinoti.android.vdarsdk.VDARLocalizationManagerEventReceiver;

/**
 * Created by root on 11/11/17.
 */

public class MyInterfaceImpl implements VDARLocalizationManagerEventReceiver {

    public void onLocalizationUpdate(float longitude, float latitude, float searchDistance) {
        Log.d("LOCATION", "Latitue: " + Float.toString(longitude) + " Londitude: " + Float.toString(latitude));
        }
}