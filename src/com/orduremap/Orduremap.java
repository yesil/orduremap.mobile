package com.orduremap;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.DialogInterface.OnMultiChoiceClickListener;
import android.graphics.PixelFormat;
import android.hardware.Camera;
import android.hardware.Camera.AutoFocusCallback;
import android.hardware.Camera.Parameters;
import android.hardware.Camera.PictureCallback;
import android.hardware.Camera.ShutterCallback;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.location.GpsStatus.Listener;
import android.media.ExifInterface;
import android.os.Bundle;
import android.os.Environment;
import android.os.IBinder;
import android.provider.Settings;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.Window;
import android.view.View.OnClickListener;
import android.widget.ImageButton;

public class Orduremap extends Activity implements SurfaceHolder.Callback, ShutterCallback, PictureCallback, OnClickListener, Listener,
		LocationListener, AutoFocusCallback {
	Camera camera;
	SurfaceView view;
	ImageButton btnCam;
	LocationManager localManager;
	UploadService uploadService;
	Intent service;
	private boolean mIsBound = false;

	/** Called when the activity is first created. */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		requestWindowFeature(Window.FEATURE_NO_TITLE);
		setContentView(R.layout.main);
		view = (SurfaceView) findViewById(R.id.camView);
		btnCam = (ImageButton) findViewById(R.id.btnCam);
		btnCam.setEnabled(false);
		btnCam.setOnClickListener(this);
		SurfaceHolder mSurfaceHolder = view.getHolder();
		mSurfaceHolder.addCallback(this);
		mSurfaceHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
		localManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
		localManager.addGpsStatusListener(this);
		localManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, this);
		doBindService();
	}

	private ServiceConnection mConnection = new ServiceConnection() {
		public void onServiceConnected(ComponentName className, IBinder service) {
			uploadService = ((UploadService.LocalBinder) service).getService();
		}

		public void onServiceDisconnected(ComponentName className) {
			uploadService = null;
		}
	};

	void doBindService() {
		bindService(new Intent(this, UploadService.class), mConnection, Context.BIND_AUTO_CREATE);
		mIsBound = true;
	}

	void doUnbindService() {
		if (mIsBound) {
			unbindService(mConnection);
			mIsBound = false;
		}
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();
		doUnbindService();
	}

	@Override
	protected void onResume() {
		super.onResume();
		btnCam.setEnabled(false);
	}

	@Override
	public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
		Parameters parameters = camera.getParameters();
		parameters.set("rotation", "90");
		parameters.setPictureSize(1024, 768);
		parameters.set("jpeg-quality", "80");
		parameters.setPictureFormat(PixelFormat.JPEG);
		camera.setParameters(parameters);
		camera.startPreview();
		camera.autoFocus(this);
	}

	@Override
	public void surfaceCreated(SurfaceHolder holder) {
		camera = Camera.open();
		try {
			camera.setPreviewDisplay(holder);
		} catch (IOException e) {
			Log.e("Orduremap", "Camera n'est pas disponible", e);
			camera.release();
			camera = null;
		}
	}

	@Override
	public void surfaceDestroyed(SurfaceHolder holder) {
		camera.stopPreview();
		camera.release();
		camera = null;
	}

	@Override
	public void onClick(View v) {
		camera.takePicture(this, null, this);
	}

	@Override
	public void onLocationChanged(Location location) {
		btnCam.setEnabled(true);
	}

	@Override
	public void onGpsStatusChanged(int event) {
	}

	@Override
	public void onProviderDisabled(String provider) {
		if (provider.equals(LocationManager.GPS_PROVIDER))
			buildAlertMessageNoGps();
	}

	@Override
	public void onProviderEnabled(String provider) {

	}

	@Override
	public void onStatusChanged(String provider, int status, Bundle extras) {
	}

	private void buildAlertMessageNoGps() {
		final AlertDialog.Builder builder = new AlertDialog.Builder(this);
		builder.setMessage(getString(R.string.txt_gps_dialog)).setCancelable(false).setPositiveButton(getString(R.string.btn_yes),
				new DialogInterface.OnClickListener() {
					public void onClick(final DialogInterface dialog, final int id) {
						Intent intent = new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS);
						startActivityForResult(intent, 0);
					}
				}).setNegativeButton(getString(R.string.btn_exit), new DialogInterface.OnClickListener() {
			public void onClick(final DialogInterface dialog, final int id) {
				Orduremap.this.finish();
			}
		});
		final AlertDialog alert = builder.create();
		alert.show();
	}

	@Override
	public void onAutoFocus(boolean success, Camera camera) {

	}

	@Override
	public void onShutter() {
	}

	@Override
	public void onPictureTaken(final byte[] data, Camera camera) {
		boolean mExternalStorageWriteable = !(Environment.getExternalStorageState() == Environment.MEDIA_MOUNTED_READ_ONLY);
		if (mExternalStorageWriteable) {
			final CharSequence[] itemLabels = { getString(R.string.plastic), getString(R.string.chemical), getString(R.string.glass),
					getString(R.string.metal), getString(R.string.paper) };
			final CharSequence[] items = { "plastic", "chemical", "glass", "metal", "paper" };
			final List<CharSequence> tags = new ArrayList<CharSequence>();
			AlertDialog.Builder builder = new AlertDialog.Builder(this);
			builder.setTitle(getString(R.string.ordure_type));
			builder.setPositiveButton(getString(R.string.btn_ok), new DialogInterface.OnClickListener() {
				public void onClick(DialogInterface dialog, int which) {
					File root = Environment.getExternalStorageDirectory();
					File file = null;
					try {
						File dir = new File(root, "/Android/data/org.orduremap/").getAbsoluteFile();
						Log.d("Orduremap", dir.getAbsolutePath() + " disponible:" + dir.mkdirs());
						file = new File(dir, String.format("orduremap-%s-%d.jpg", arrayToString(tags.toArray(), '+'), System
								.currentTimeMillis())).getAbsoluteFile();
						FileOutputStream outStream = null;
						outStream = new FileOutputStream(file);
						outStream.write(data);
						outStream.close();
						Location location = localManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
						uploadService.uploadFile(file, String.valueOf(location.getLatitude()), String.valueOf(location.getLongitude()));
					} catch (FileNotFoundException e) {
						Log.e("Orduremap", file.getAbsolutePath() + "problème lors du sauvegarde de l'ordure", e);
					} catch (IOException e) {
						Log.e("Orduremap", file.getAbsolutePath() + "problème lors du sauvegarde de l'ordure", e);
					}
				}
			});
			builder.setMultiChoiceItems(itemLabels, new boolean[] { false, false, false, false, false }, new OnMultiChoiceClickListener() {
				@Override
				public void onClick(DialogInterface dialog, int which, boolean isChecked) {
					if (isChecked)
						tags.add(items[which]);
					else if (tags.contains(items[which]))
						tags.remove(items[which]);
				}
			});
			AlertDialog alert = builder.create();
			alert.show();
		} else
			Log.e("Orduremap", "Ecriture impossible sur la carte externale");
		camera.startPreview();
	}

	public String arrayToString(Object[] objects, char c) {
		StringBuffer result = new StringBuffer();
		if (objects.length > 0) {
			result.append(objects[0]);
			for (int i = 1; i < objects.length; i++) {
				result.append(c);
				result.append(objects[i]);
			}
		}
		return result.toString();
	}
}