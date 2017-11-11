package com.augmentedromance.augmentedromance;


import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.vidinoti.android.vdarsdk.VDARSDKController;




public class StartSDKReceiver extends BroadcastReceiver {
	 private static final String TAG = "StartSDKReceiver";

		@Override
	    public void onReceive(Context context, Intent intent) {
			/* Called when the SDK has to be started. */
			VDARSDKController.log(Log.VERBOSE,TAG,"Starting PixLive SDK in background...");
			if(VDARSDKController.getInstance()==null) {
				ARActivity.startSDK(context.getApplicationContext());
			}
		}
}
