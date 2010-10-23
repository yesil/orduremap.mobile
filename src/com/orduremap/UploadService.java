package com.orduremap;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.apache.http.HttpResponse;
import org.apache.http.HttpVersion;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.mime.MultipartEntity;
import org.apache.http.entity.mime.content.ContentBody;
import org.apache.http.entity.mime.content.FileBody;
import org.apache.http.entity.mime.content.StringBody;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.params.CoreProtocolPNames;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpParams;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.os.Binder;
import android.os.IBinder;
import android.util.Log;
import android.widget.Toast;

public class UploadService extends Service {
	private HttpClient httpclient = new DefaultHttpClient();
	private ExecutorService executor = Executors.newSingleThreadExecutor();
	private final String postURL = "http://orduremap.appspot.com/upload";

	/**
	 * Class for clients to access. Because we know this service always runs in
	 * the same process as its clients, we don't need to deal with IPC.
	 */
	public class LocalBinder extends Binder {
		UploadService getService() {
			return UploadService.this;
		}
	}

	@Override
	public void onCreate() {
		HttpParams params = httpclient.getParams();
		HttpConnectionParams.setConnectionTimeout(params, 60000);
		HttpConnectionParams.setSoTimeout(params, 60000);
	}

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		showFinishNotification(R.string.service_start);
		return START_STICKY;
	}

	@Override
	public IBinder onBind(Intent intent) {
		return mBinder;
	}

	private final IBinder mBinder = new LocalBinder();

	public void showFinishNotification(int message) {
		Context context = getApplicationContext();
		CharSequence text = getString(message);
		int duration = Toast.LENGTH_LONG;
		Toast toast = Toast.makeText(context, text, duration);
		toast.show();
	}

	public final void uploadFile(final File file, final String lt, final String lg, final String ts) {
		executor.submit(new Runnable() {
			@Override
			public void run() {
				final ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
				while (true) {
					boolean connected = false;
					try {
						connected = cm.getActiveNetworkInfo().isConnected();
						if (connected) {
							try {
								HttpPost post = new HttpPost(postURL);
								httpclient.getParams().setParameter(CoreProtocolPNames.PROTOCOL_VERSION, HttpVersion.HTTP_1_1);
								MultipartEntity mpEntity = new MultipartEntity();
								StringBody name = new StringBody(file.getName());
								StringBody latitude = new StringBody(lt);
								StringBody longitude = new StringBody(lg);
								StringBody tags = new StringBody(ts);
								ContentBody cbFile = new FileBody(file, "image/jpeg");
								mpEntity.addPart("name", name);
								mpEntity.addPart("latitude", latitude);
								mpEntity.addPart("longitude", longitude);
								mpEntity.addPart("tags", tags);
								mpEntity.addPart("file", cbFile);
								post.setEntity(mpEntity);
								HttpResponse response = httpclient.execute(post);
								byte[] responseData = new byte[response.getEntity().getContent().available()];
								response.getEntity().getContent().read(responseData);
								Log.d("Orduremap", new String(responseData));
								file.delete();
							} catch (ClientProtocolException e) {
								Log.e("Orduremap Upload Service", "échec du chargement de l'ordure", e);
							} catch (IOException e) {
								Log.e("Orduremap Upload Service", "échec du chargement de l'ordure", e);
							}
							break;
						}
					} catch (Exception e) {
						Log.e("Orduremap Upload Service", "Problème de connexion", e);
						try {
							Thread.sleep(60000);
						} catch (InterruptedException e1) {
							e1.printStackTrace();
						}
					}
				}
			}
		});
	}
}
