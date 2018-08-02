package com.serenegiant.webrtc;

import android.content.Context;
import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.util.Log;
import android.view.Surface;
import android.view.WindowManager;

import org.webrtc.CameraEnumerationAndroid;
import org.webrtc.Logging;
import org.webrtc.Size;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.TimeUnit;

import javax.annotation.Nullable;

public class SurfaceCamera1Session implements SurfaceCameraSession {
	private static final boolean DEBUG = true; // set false on production
	private static final String TAG = SurfaceCamera1Session.class.getSimpleName();

	private static final int NUMBER_OF_CAPTURE_BUFFERS = 3;

	@NonNull
	private final Handler cameraThreadHandler;
	@NonNull
	private final SurfaceCameraSession.Events events;
	@NonNull
	private final Context applicationContext;
	@NonNull
	private final int cameraId;
	@NonNull
	private final Camera camera;
	@NonNull
	private final Camera.CameraInfo info;
	private SessionState state;
	
	public static void create(final SurfaceCameraSession.CreateSessionCallback callback,
		@NonNull final SurfaceCameraSession.Events events,
		final Context applicationContext,
		@NonNull final SurfaceTexture inputSurface,
		final int cameraId, final int width, final int height, final int framerate) {

		if (DEBUG) Log.v(TAG, "create:");
		Logging.d(TAG, "Open camera " + cameraId);
		events.onCameraOpening();
		
		Camera camera;
		try {
			camera = Camera.open(cameraId);
		} catch (RuntimeException e) {
			callback.onFailure(SurfaceCameraSession.FailureType.ERROR, e.getMessage());
			return;
		}
		
		if (camera == null) {
			callback.onFailure(SurfaceCameraSession.FailureType.ERROR, "android.hardware.Camera.open returned null for camera id = " + cameraId);
		} else {
			try {
				camera.setPreviewTexture(inputSurface);
			} catch (final RuntimeException | IOException e) {
				camera.release();
				callback.onFailure(SurfaceCameraSession.FailureType.ERROR, e.getMessage());
				return;
			}
			
			Camera.CameraInfo info = new Camera.CameraInfo();
			Camera.getCameraInfo(cameraId, info);
			final Camera.Parameters parameters = camera.getParameters();
			final CameraEnumerationAndroid.CaptureFormat captureFormat
				= findClosestCaptureFormat(parameters, width, height, framerate);
			final Size pictureSize = findClosestPictureSize(parameters, width, height);
			updateCameraParameters(camera, parameters, captureFormat, pictureSize);
			
			camera.setDisplayOrientation(0);
			callback.onDone(new SurfaceCamera1Session(events, applicationContext, cameraId, camera, info));
		}
	}
	
	private static void updateCameraParameters(Camera camera, Camera.Parameters parameters,
		CameraEnumerationAndroid.CaptureFormat captureFormat, Size pictureSize) {
	
		if (DEBUG) Log.v(TAG, "updateCameraParameters:");
		final List<String> focusModes = parameters.getSupportedFocusModes();
		parameters.setPreviewFpsRange(captureFormat.framerate.min, captureFormat.framerate.max);
		parameters.setPreviewSize(captureFormat.width, captureFormat.height);
		parameters.setPictureSize(pictureSize.width, pictureSize.height);
		
		if (parameters.isVideoStabilizationSupported()) {
			parameters.setVideoStabilization(true);
		}
		
		if (focusModes.contains("continuous-video")) {
			parameters.setFocusMode("continuous-video");
		}
		
		camera.setParameters(parameters);
	}
	
	@NonNull
	private static CameraEnumerationAndroid.CaptureFormat findClosestCaptureFormat(
		@NonNull final Camera.Parameters parameters,
		final int width, final int height, final int framerate) {
		
		if (DEBUG) Log.v(TAG, "findClosestCaptureFormat:");
		final List<CameraEnumerationAndroid.CaptureFormat.FramerateRange> supportedFramerates
			= convertFramerates(parameters.getSupportedPreviewFpsRange());
		Logging.d(TAG, "Available fps ranges: " + supportedFramerates);
		final CameraEnumerationAndroid.CaptureFormat.FramerateRange fpsRange
			= CameraEnumerationAndroid.getClosestSupportedFramerateRange(supportedFramerates, framerate);
		final Size previewSize = CameraEnumerationAndroid.getClosestSupportedSize(
			convertSizes(parameters.getSupportedPreviewSizes()), width, height);
		return new CameraEnumerationAndroid.CaptureFormat(previewSize.width, previewSize.height, fpsRange);
	}
	
	private static Size findClosestPictureSize(@NonNull final Camera.Parameters parameters,
		final int width, final int height) {

		return CameraEnumerationAndroid.getClosestSupportedSize(
			convertSizes(parameters.getSupportedPictureSizes()), width, height);
	}
	
	private SurfaceCamera1Session(@NonNull final SurfaceCameraSession.Events events,
		@NonNull final Context applicationContext,
		final int cameraId, @NonNull final Camera camera, @NonNull final Camera.CameraInfo info) {

		Logging.d(TAG, "Create new camera1 session on camera " + cameraId);
		cameraThreadHandler = new Handler();
		this.events = events;
		this.applicationContext = applicationContext;
		this.cameraId = cameraId;
		this.camera = camera;
		this.info = info;
		startCapturing();
	}
	
	public void stop() {
		Logging.d(TAG, "Stop camera1 session on camera " + this.cameraId);
		checkIsOnCameraThread();
		if (this.state != SessionState.STOPPED) {
			final long stopStartTime = System.nanoTime();
			stopInternal();
			final int stopTimeMs = (int) TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - stopStartTime);
//			camera1StopTimeMsHistogram.addSample(stopTimeMs);
		}
		
	}
	
	public int getFace() {
		return info.facing;
	}
	
	public int getRotation() {
		return getFrameOrientation();
	}

	private void startCapturing() {
		Logging.d(TAG, "Start capturing");
		checkIsOnCameraThread();
		state = SessionState.RUNNING;
		camera.setErrorCallback(new Camera.ErrorCallback() {
			public void onError(int error, Camera camera) {
				String errorMessage;
				if (error == 100) {
					errorMessage = "Camera server died!";
				} else {
					errorMessage = "Camera error: " + error;
				}
				
				Logging.e(TAG, errorMessage);
				stopInternal();
				if (error == 2) {
					events.onCameraDisconnected(SurfaceCamera1Session.this);
				} else {
					events.onCameraError(SurfaceCamera1Session.this, errorMessage);
				}
				
			}
		});
		
		try {
			camera.startPreview();
		} catch (RuntimeException e) {
			stopInternal();
			events.onCameraError(this, e.getMessage());
		}
		
	}
	
	private void stopInternal() {
		Logging.d(TAG, "Stop internal");
		checkIsOnCameraThread();
		if (state == SessionState.STOPPED) {
			Logging.d(TAG, "Camera is already stopped");
		} else {
			state = SessionState.STOPPED;
			camera.stopPreview();
			camera.release();
			events.onCameraClosed(this);
			Logging.d(TAG, "Stop done");
		}
	}
	
	private int getDeviceOrientation() {
		int orientation;
		// XXX 毎フレーム呼ばれるからこれ毎回呼ぶよりもConfigChangedをひらってチェックするほうが負荷低減にいい気がする
		WindowManager wm = (WindowManager) applicationContext.getSystemService(Context.WINDOW_SERVICE);
		switch (wm.getDefaultDisplay().getRotation()) {
		case Surface.ROTATION_90:
			orientation = 90;
			break;
		case Surface.ROTATION_180:
			orientation = 180;
			break;
		case Surface.ROTATION_270:
			orientation = 270;
			break;
		case Surface.ROTATION_0:
		default:
			orientation = 0;
			break;
		}
		
		return orientation;
	}
	
	private int getFrameOrientation() {
		int rotation = getDeviceOrientation();
		if (info.facing == 0) {
			rotation = 360 - rotation;
		}
		
		return (info.orientation + rotation) % 360;
	}
	
	private void checkIsOnCameraThread() {
		if (Thread.currentThread() != this.cameraThreadHandler.getLooper().getThread()) {
			throw new IllegalStateException("Wrong thread");
		}
	}
	
	private static enum SessionState {
		RUNNING,
		STOPPED;
		
		private SessionState() {
		}
	}
	
//================================================================================
	static int getCameraIndex(final String deviceName) {
		Logging.d("Camera1Enumerator", "getCameraIndex: " + deviceName);
		
		for (int i = 0; i < Camera.getNumberOfCameras(); ++i) {
			if (deviceName.equals(getDeviceName(i))) {
				return i;
			}
		}
		
		throw new IllegalArgumentException("No such camera: " + deviceName);
	}
	
	@Nullable
	static String getDeviceName(int index) {
		Camera.CameraInfo info = getCameraInfo(index);
		if (info == null) {
			return null;
		} else {
			String facing = info.facing == 1 ? "front" : "back";
			return "Camera " + index + ", Facing " + facing + ", Orientation " + info.orientation;
		}
	}
	
	@Nullable
	static Camera.CameraInfo getCameraInfo(int index) {
		Camera.CameraInfo info = new Camera.CameraInfo();
		
		try {
			Camera.getCameraInfo(index, info);
			return info;
		} catch (Exception var3) {
			Logging.e("Camera1Enumerator", "getCameraInfo failed on index " + index, var3);
			return null;
		}
	}
	
	static List<CameraEnumerationAndroid.CaptureFormat.FramerateRange> convertFramerates(List<int[]> arrayRanges) {
		List<CameraEnumerationAndroid.CaptureFormat.FramerateRange> ranges = new ArrayList<>();
		Iterator var2 = arrayRanges.iterator();
		
		while (var2.hasNext()) {
			int[] range = (int[]) var2.next();
			ranges.add(new CameraEnumerationAndroid.CaptureFormat.FramerateRange(range[0], range[1]));
		}
		
		return ranges;
	}
	
	static List<org.webrtc.Size> convertSizes(List<Camera.Size> cameraSizes) {
		List<org.webrtc.Size> sizes = new ArrayList<>();
		Iterator var2 = cameraSizes.iterator();
		
		while (var2.hasNext()) {
			Camera.Size size = (Camera.Size) var2.next();
			sizes.add(new org.webrtc.Size(size.width, size.height));
		}
		
		return sizes;
	}
}
