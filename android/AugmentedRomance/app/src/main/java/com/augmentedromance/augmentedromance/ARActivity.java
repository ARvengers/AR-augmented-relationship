/*
 * PixLive SDK Sample for Android
 * Copyright (C) 2012-2015 PixLive SDK 
 *
 */

package com.augmentedromance.augmentedromance;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.content.res.Resources;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup.LayoutParams;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.os.SystemClock;

import android.support.v4.app.ActivityCompat;
import android.Manifest;
import android.location.LocationManager;
import java.util.List;

import com.google.firebase.database.FirebaseDatabase;
import com.vidinoti.android.vdarsdk.camera.DeviceCameraImageSender;
import com.vidinoti.android.vdarsdk.VDARAnnotationView;
import com.vidinoti.android.vdarsdk.VDARCode;
import com.vidinoti.android.vdarsdk.VDARContext;
import com.vidinoti.android.vdarsdk.VDARPrior;
import com.vidinoti.android.vdarsdk.VDARTagPrior;
import com.vidinoti.android.vdarsdk.VDARRemoteController;
import com.vidinoti.android.vdarsdk.VDARRemoteController.ObserverUpdateInfo;
import com.vidinoti.android.vdarsdk.VDARRemoteControllerListener;
import com.vidinoti.android.vdarsdk.VDARSDKController;
import com.vidinoti.android.vdarsdk.VDARSDKControllerEventReceiver;
import com.vidinoti.android.vdarsdk.VDARLocalizationManager;
import com.vidinoti.android.vdarsdk.VDARLocalizationManagerEventReceiver;
import com.vidinoti.android.vdarsdk.geopoint.GeoPointManager;

import com.vidinoti.android.vdarsdk.geopoint.VDARGPSPoint;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Observable;
import java.util.Observer;

import com.google.firebase.database.ChildEventListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.MutableData;
import com.google.firebase.database.ServerValue;
import com.google.firebase.database.Transaction;
import com.google.firebase.database.ValueEventListener;

import android.location.Location;

/**
 * Is a sample code of an Android activity demonstrating the integration of the
 * VDARSDK.
 */
public class ARActivity extends Activity implements
		VDARSDKControllerEventReceiver,
		VDARRemoteControllerListener {

	private DeviceCameraImageSender imageSender = null;

	private VDARAnnotationView annotationView = null;

	private static final String TAG = "ARActivity";

	/** Your SDK license key available from the ARManager */
	private static final String MY_SDK_LICENSE_KEY = "q8xey9jh7w0xaiq8fbfe";

	/** Your Project ID in Google APIs Console for Push Notification (GCM) */
	private static final String GOOGLE_API_PROJECT_ID_FOR_NOTIFICATIONS = "0000000000";

	private static boolean syncInProgress = false;

	private ProgressBar progressSync;

	private RelativeLayout rl;

	private String MyUserID = "UserB";

	public void showCrumbsList(View view)
	{
		Intent intent = new Intent(this, BreadcrumbListActivity.class);
		startActivity(intent);
	}
	private  VDARLocalizationManager localization = new VDARLocalizationManager();

	public void showLogin(View view) {
		Intent intent = new Intent(this, AuthUiActivity.class);
		startActivity(intent);
	}
	private GeoPointManager geoPoints = new  GeoPointManager();

	/** Initiates the sample activity */
	@Override
	public void onCreate(Bundle savedInstanceState) {

		super.onCreate(savedInstanceState);

		/*
		 * Start the AR SDK. We need to create a static method for this so that
		 * the SDK can be also started from the background when a beacon is
		 * detected
		 */
		startSDK(this);

		/* Activate the SDK for this activity */
		VDARSDKController.getInstance().setActivity(this);

		/* Register ourself to receive detection events */
		VDARSDKController.getInstance().registerEventReceiver(this);

		/* Setup the camera for the PixLive SDK */
		try {
			imageSender = new DeviceCameraImageSender();
		} catch (IOException e) {
			Log.e(TAG, Log.getStackTraceString(e));
		}

		/* Setup the views for the application */
		setContentView(R.layout.ar_activity);

		annotationView = (VDARAnnotationView)findViewById(R.id.arview);

		annotationView.setDarkScreenMode(false);
		annotationView.setAnimationSpeed(1.0f);

		progressSync = (ProgressBar)findViewById(R.id.progressbar);

		/* Process any pending notification */

		final Intent intent = getIntent();

		/*
		 * If the activity has been launched in response to a notification, we
		 * have to tell the PixLive SDK to process this notification
		 */
		if (intent != null && intent.getExtras() != null
				&& intent.getExtras().getString("nid") != null) {

			final String nid = intent.getExtras().getString("nid");

			VDARSDKController.getInstance().addNewAfterLoadingTask(
					new Runnable() {
						@Override
						public void run() {
							VDARSDKController.getInstance()
									.processNotification(
											nid,
											intent.getExtras().getBoolean(
													"remote"));
						}
					});
		}

		// Request permission for location
		ActivityCompat.requestPermissions(this,new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 1);


		MyInterfaceImpl receiver = new MyInterfaceImpl();

		localization.registerEventReceiver(receiver);

		this.localization.startLocalization();

		double lat = this.localization.getCurrentBestLocationEstimate().getLatitude();
		double lon = this.localization.getCurrentBestLocationEstimate().getLongitude();
		Log.d("LOCATION", "Latitue: " + Double.toString(lat) + "Londitude: " + Double.toString(lon));


		// Write a message to the database
		FirebaseDatabase database = FirebaseDatabase.getInstance();
		DatabaseReference myRef = database.getReference();

		DatabaseReference matchesRef = myRef.child("matches/" + MyUserID);

		// Read from the database
		matchesRef.addValueEventListener(new ValueEventListener() {
			@Override
			public void onDataChange(DataSnapshot dataSnapshot) {
				// This method is called once with the initial value and again
				// whenever data at this location is updated.
				String value = dataSnapshot.getValue(String.class);
				Log.d(TAG, "Value is: " + value);
			}

			@Override
			public void onCancelled(DatabaseError error) {
				// Failed to read value
				Log.w(TAG, "Failed to read value.", error.toException());
			}
		});




	}

	/**
	 * Start the SDK on the context c. Doesn't do anything if already started.
	 * @param c The Android context to start the SDK on.
	 */
	static void startSDK(final Context c) {

		if (VDARSDKController.getInstance() != null) {
			return;
		}

		/* Start the PixLive SDK on the below path (the data will be stored there) */
		String modelPath = c.getApplicationContext().getFilesDir()
				.getAbsolutePath()
				+ "/arcontent";

		VDARSDKController.startSDK(c, modelPath, MY_SDK_LICENSE_KEY);

		/* Comment out to disable QR code detection */
		//VDARSDKController.getInstance().setEnableCodesRecognition(true);

		/* Enable push notifications */
		/* ------------------------- */

		/*
		 * See the documentation at
		 * http://doc.vidinoti.com/vdarsdk/web/android/latest for instructions
		 * on how to setup it
		 */
		/*
		 * You need your app project ID from the Google APIs Console at
		 * https://code.google.com/apis/console
		 */
		VDARSDKController.getInstance().setNotificationsSupport(true,
				GOOGLE_API_PROJECT_ID_FOR_NOTIFICATIONS);

	}

	/**
	 * Method that adds a progress bar for synchronization progress
	 */
	private void addProgSync() {

		progressSync = new ProgressBar(this, null,
				android.R.style.Widget_ProgressBar_Horizontal);
		progressSync.setProgressDrawable(Resources.getSystem().getDrawable(
				android.R.drawable.progress_horizontal));
		progressSync.setMax(1000);
		progressSync.setVisibility(View.INVISIBLE);
		progressSync.setIndeterminate(false);
		Resources r = getResources();
		RelativeLayout.LayoutParams layout = new RelativeLayout.LayoutParams(
				LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
		float px = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 5,
				r.getDisplayMetrics());
		float py = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 5,
				r.getDisplayMetrics());

		layout.leftMargin = (int) px;
		layout.rightMargin = (int) px;
		layout.bottomMargin = (int) py;
		layout.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM);
		progressSync.setLayoutParams(layout);
		rl.addView(progressSync);

		progressSync.setProgress(0);
	}


	/**
	 * Start a new PixLive SDK content synchronization.
	 */
	private void synchronizeTag(final ArrayList<VDARPrior> priors) {

		//We have to make sure not to synchronized twice at the same time.
		synchronized (this) {
			if (syncInProgress)
				return;

			syncInProgress = true;
		}

		// Synchronization has to be started after the SDK is loaded. The
		// addNewAfterLoadingTask method allows that.
		VDARSDKController.getInstance().addNewAfterLoadingTask(new Runnable() {

			@Override
			public void run() {
				ArrayList<VDARPrior> priors_list = new ArrayList<VDARPrior>();

				if (priors != null) {
					priors_list.addAll(priors);
				}

				// You can add a tag this way to do tag based synchronization.
				// Leaving will synchronize all the models you have created and
				// that are published on PixLive Maker.
				//priors_list.add(new VDARTagPrior("TagA"));

				//priors_list.add(new VDARTagPrior("Geo"));

				Log.v(TAG, "Starting Tag sync");

				// Launch sync.
				VDARRemoteController.getInstance()
						.syncRemoteContextsAsynchronouslyWithPriors(priors_list,
								new Observer() {

									@Override
									public void update(Observable observable,
													   Object data) {
										ObserverUpdateInfo info = (ObserverUpdateInfo) data;

										if (info.isCompleted()) {
											Log.v(TAG, "Done syncing. Tag Synced "
													+ info.getFetchedContexts()
													.size()
													+ " models.");
											synchronized (ARActivity.this) {
												syncInProgress = false;

											}
										}

									}
								});
			}
		});
	}


	/**
	 * Start a new PixLive SDK content synchronization.
	 */
	private void synchronizeGeo(final ArrayList<VDARPrior> priors) {
		
		//We have to make sure not to synchronized twice at the same time.
		synchronized (this) {
			if (syncInProgress)
				return;

			syncInProgress = true;
		}

		// Synchronization has to be started after the SDK is loaded. The
		// addNewAfterLoadingTask method allows that.
		VDARSDKController.getInstance().addNewAfterLoadingTask(new Runnable() {

			@Override
			public void run() {
				ArrayList<VDARPrior> priors_list = new ArrayList<VDARPrior>();

				if (priors != null) {
					priors_list.addAll(priors);
				}

				// You can add a tag this way to do tag based synchronization.
				// Leaving will synchronize all the models you have created and
				// that are published on PixLive Maker.
				//priors_list.add(new VDARTagPrior("TagA"));

				priors_list.add(new VDARTagPrior("Geo"));

				Log.v(TAG, "Starting Geo sync");

				// Launch sync.
				VDARRemoteController.getInstance()
						.syncRemoteContextsAsynchronouslyWithPriors(priors_list,
								new Observer() {

									@Override
									public void update(Observable observable,
													   Object data) {
										ObserverUpdateInfo info = (ObserverUpdateInfo) data;

										if (info.isCompleted()) {
											Log.v(TAG, "Done syncing. Geo Synced "
													+ info.getFetchedContexts()
													.size()
													+ " models.");
											synchronized (ARActivity.this) {
												syncInProgress = false;

											}

											double lat = ARActivity.this.localization.getCurrentBestLocationEstimate().getLatitude();
											double lon = ARActivity.this.localization.getCurrentBestLocationEstimate().getLongitude();

											Log.d("LOCATION", "Latitue: " + Double.toString(lat) + "Londitude: " + Double.toString(lon));
											Log.d("GEO_POINTS", "Getting nearby geo points");
											List<VDARGPSPoint> nearby = geoPoints.getNearbyGPSPoints(Float.valueOf(String.valueOf(lat)),Float.valueOf(String.valueOf(lon)));
											Log.d("GEO_POINTS Found: ", String.valueOf(nearby.size()));

											List<VDARGPSPoint> bounding = geoPoints.getGPSPointsInBoundingBox(40,0,50,10);
											Log.d("GEO_POINTS Found: ", String.valueOf(bounding.size()));

											ArrayList<VDARPrior> priors_list = new ArrayList<VDARPrior>();
											for (VDARGPSPoint member : nearby){
												Log.d("GEO_POINTS name: ", member.getLabel());
												priors_list.add(new VDARTagPrior(member.getLabel()));
											}

											if(priors_list.size() > 0){
												Log.d("here","here");
												synchronizeTag(priors_list);
											}


										}

									}
								});
			}
		});




	}

	/** Is called when the activity is paused. */
	@Override
	public void onPause() {
		super.onPause();
		
		//Pause our AR View. Mandatory.
		annotationView.onPause();

		//Remove ourself from the listener list
		VDARRemoteController.getInstance().removeProgressListener(this);
	}


	/** Is called when the activity is resumed. */
	@Override
	public void onResume() {
		super.onResume();

		VDARSDKController.getInstance().setActivity(this);

		annotationView.onResume();

		// Add ourself to the listener list
		VDARRemoteController.getInstance().addProgressListener(this);

		/*
		 * Trigger a synchronization so that every time we load the app,
		 * everything is up to date.
		 */
		synchronizeGeo(null);


	}

	@Override
	protected void onNewIntent(Intent intent) {
		/* Process the notification if needed. */
		if (intent != null && intent.getExtras() != null
				&& intent.getExtras().getString("nid") != null) {
			VDARSDKController.getInstance().processNotification(
					intent.getExtras().getString("nid"),
					intent.getExtras().getBoolean("remote"));
		}
	}

	@Override
	public void onRequestPermissionsResult (int requestCode, String[] permissions, int[] grantResults) {
		/* Forward permission request results to PixLive SDK */
		VDARSDKController.getInstance().onRequestPermissionsResult(requestCode, permissions, grantResults);
	}

	/**
	 * Is called when the overall system is running low on memory.
	 *
	 */
	@Override
	public void onLowMemory() {
		super.onLowMemory();
		/* Tell the system to release as much memory as it can */
		if (VDARSDKController.getInstance() != null)
			VDARSDKController.getInstance().releaseMemory();
	}

	@Override
	public void onCodesRecognized(ArrayList<VDARCode> codes) {
		Log.v(TAG, "Code recongized:");
		Log.v(TAG, "" + codes);

		for (final VDARCode c : codes) {
			if (c.isSpecialCode())
				continue; // Ignore special code handled by the SDK

			final Uri u = Uri.parse(c.getCodeData());

			// Open URL
			if (u != null) {
				this.runOnUiThread(new Runnable() {

					@Override
					public void run() {
						try {
							Intent browserIntent = new Intent(
									Intent.ACTION_VIEW, u);
							startActivity(browserIntent);
						} catch (Exception e) {
							new AlertDialog.Builder(
									ARActivity.this)
									.setTitle("QR Code")
									.setMessage(
											"Invalid URL in recognized QR Code: "
													+ c.getCodeData())
									.setNeutralButton("OK",
											new OnClickListener() {

												@Override
												public void onClick(
														DialogInterface dialog,
														int which) {
													dialog.dismiss();
												}
											}).show();
						}
					}
				});

			}
		}
	}

	@Override
	public void onFatalError(final String errorDescription) {
		this.runOnUiThread(new Runnable() {

			@Override
			public void run() {
				try {
					new AlertDialog.Builder(ARActivity.this)
							.setTitle("Augmented reality system error")
							.setMessage(errorDescription)
							.setNeutralButton("OK", new OnClickListener() {

								@Override
								public void onClick(DialogInterface dialog,
													int which) {
									dialog.dismiss();
								}
							}).show();
				} catch (Exception e) {
				}
			}
		});
	}

	@Override
	public void onPresentAnnotations() {
		// Hide overlay
	}

	@Override
	public void onAnnotationsHidden() {
		// Show overlay
	}

	@Override
	public void onSyncProgress(VDARRemoteController controller, float progress,
			boolean isReady, String folder) {

		if (progress < 100) {

			progressSync.setProgress((int) (progress * 10));
			if (progressSync.getVisibility() != View.VISIBLE) {
				progressSync.setVisibility(View.VISIBLE);
				progressSync.bringToFront();
			}

		} else {

			if (progressSync.getProgress() < 1000) {
				progressSync.setProgress(1000);
				progressSync.setVisibility(View.INVISIBLE);
			}
		}

	}

	@Override
	public void onTrackingStarted(int imageWidth, int imageHeight) {
		// Empty - not needed
	}

	@Override
	public void onEnterContext(VDARContext context) {

		// Write a message to the database
		FirebaseDatabase database = FirebaseDatabase.getInstance();
		DatabaseReference myRef = database.getReference();

		DatabaseReference matchesRef = myRef.child("matches/" + context.getName());

		matchesRef.setValue(MyUserID);
	}

	@Override
	public void onExitContext(VDARContext context) {
		Log.v(TAG,"Context "+context+" lost.");
	}

	@Override
	public void onRequireSynchronization(ArrayList<VDARPrior> priors) {
		synchronizeGeo(priors);
	}
}
