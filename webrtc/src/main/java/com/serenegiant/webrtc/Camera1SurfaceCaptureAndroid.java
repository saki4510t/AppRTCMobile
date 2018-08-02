package com.serenegiant.webrtc;

import android.content.Context;
import android.support.annotation.NonNull;
import android.util.Log;

import org.webrtc.Camera1Enumerator;
import org.webrtc.SurfaceTextureHelper;

public class Camera1SurfaceCaptureAndroid extends CameraSurfaceCapture {
	private static final boolean DEBUG = true; // set false on production
	private static final String TAG = Camera1SurfaceCaptureAndroid.class.getSimpleName();
	
	public Camera1SurfaceCaptureAndroid(final String cameraName) {
		super(cameraName, null, new Camera1Enumerator(true));
	}
	
	@Override
	protected void createCameraSession(
		final SurfaceCameraSession.CreateSessionCallback createSessionCallback,
		final SurfaceCameraSession.Events cameraSessionEventsHandler,
		@NonNull final Context applicationContext,
		final SurfaceTextureHelper surfaceHelper, String cameraName,
		final int width, final int height, final int framerate) {
		
		if (DEBUG) Log.v(TAG, "createCameraSession:");
		SurfaceCamera1Session.create(createSessionCallback, cameraSessionEventsHandler,
			applicationContext, getInputSurfaceTexture(),
			SurfaceCamera1Session.getCameraIndex(cameraName),
			width, height, framerate);
	}
	
}
