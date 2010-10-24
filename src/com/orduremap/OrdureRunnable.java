package com.orduremap;

import java.io.File;
import java.io.IOException;

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

import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.util.Log;

public class OrdureRunnable implements Runnable {
	ConnectivityManager cm;
	File file;
	String lt;
	String lg;
	String ts;
	private HttpClient httpclient = new DefaultHttpClient();
	private final String postURL = "http://orduremap.appspot.com/upload";

	public OrdureRunnable(ConnectivityManager cm, File file, String lt, String lg, String ts) {
		super();
		this.cm = cm;
		this.file = file;
		this.lt = lt;
		this.lg = lg;
		this.ts = ts;
	}

	@Override
	public void run() {
		while (true) {
			NetworkInfo info = cm.getActiveNetworkInfo();
			if (info != null && info.isConnected()) {
				try {
					HttpParams params = httpclient.getParams();
					HttpConnectionParams.setConnectionTimeout(params, 60000);
					HttpConnectionParams.setSoTimeout(params, 60000);
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
					httpclient.execute(post);
					file.delete();
				} catch (ClientProtocolException e) {
					Log.e("Orduremap Upload Service", "échec du chargement de l'ordure", e);
				} catch (IOException e) {
					Log.e("Orduremap Upload Service", "échec du chargement de l'ordure", e);
				}
				break;
			}
		}
	}
}
