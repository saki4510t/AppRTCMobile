package com.serenegiant.webrtc;
/*
 *  Copyright 2018 saki t_saki@serenegiant.com　All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.support.annotation.NonNull;

import org.webrtc.CameraEnumerator;
import org.webrtc.Logging;
import org.webrtc.RendererCommon;
import org.webrtc.SurfaceTextureHelper;

import java.util.Arrays;
import java.util.Locale;

import javax.annotation.Nullable;

/**
 * 内蔵カメラ映像をSurface/SurfaceTextureとIRendererHolderを経由して
 * WebRTCへ流すためのCameraSurfaceVideoCaptureインターフェースの実装
 *
 * WebRTCオフィシャルライブラリの内蔵カメラアクセスクラスCameraCapture.javaを参考に作成
 */
public abstract class CameraSurfaceCapture extends SurfaceCaptureAndroid
	implements CameraSurfaceVideoCapture {

	private static final boolean DEBUG = false; // set false on production
	private static final String TAG = CameraSurfaceCapture.class.getSimpleName();
	
	static enum SwitchState {
		IDLE,
		PENDING,
		IN_PROGRESS
	}
	
	/**
	 * デフォルトのCameraCaptureListener実装
	 * 特に何もしない
	 */
	public static final CameraCaptureListener DEFAULT_CAPTURE_LISTENER
		= new CameraCaptureListener() {

		public void onCameraError(final String errorDescription) {
		}
		
		public void onCameraDisconnected() {
		}
		
		public void onFailure(final String reason) {
		}
		
		public void onCameraOpening(final String cameraName) {
		}
		
		public void onFirstFrameAvailable() {
		}
		
		public void onCameraClosed() {
		}
	};
		
	private final CameraEnumerator cameraEnumerator;
	@NonNull
	private final CameraCaptureListener captureListener;
	private final Handler uiThreadHandler;

	private boolean sessionOpening;
	@Nullable
	private SurfaceCameraSession currentSession;
	private String cameraName;
	private int openAttemptsRemaining;
	private CameraSurfaceCapture.SwitchState switchState;
	@Nullable
	private CameraSwitchListener switchEventsHandler;
	
	/**
	 * コンストラクタ
	 * @param cameraName
	 * @param captureListener
	 * @param cameraEnumerator
	 */
	public CameraSurfaceCapture(final String cameraName,
		@Nullable CameraCaptureListener captureListener,
		final CameraEnumerator cameraEnumerator) {

		super(captureListener != null ? captureListener : DEFAULT_CAPTURE_LISTENER);
		switchState = CameraSurfaceCapture.SwitchState.IDLE;
		this.captureListener = captureListener != null ? captureListener : DEFAULT_CAPTURE_LISTENER;
		this.cameraEnumerator = cameraEnumerator;
		this.cameraName = cameraName;
		this.uiThreadHandler = new Handler(Looper.getMainLooper());
		final String[] deviceNames = cameraEnumerator.getDeviceNames();
		if (deviceNames.length == 0) {
			throw new RuntimeException("No cameras attached.");
		} else if (!Arrays.asList(deviceNames).contains(cameraName)) {
			throw new IllegalArgumentException("Camera name " + cameraName + " does not match any known camera device.");
		}
	}
	
	@Override
	public void startCapture(final int width, final int height, final int framerate) {
		super.startCapture(width, height, framerate);
		Logging.d(TAG, "startCapture: " + width + "x" + height + "@" + framerate);
		synchronized (stateLock) {
			if (!sessionOpening && currentSession == null) {
				sessionOpening = true;
				openAttemptsRemaining = 3;
				createSessionInternal(0);
			} else {
				Logging.w(TAG, "Session already open");
			}
		}
	}
	
	private void createSessionInternal(int delayMs) {
		uiThreadHandler.postDelayed(openCameraTimeoutRunnable, (long) (delayMs + 10000));
		postDelayed(() -> {
			createCameraSession(createSessionCallback,
				cameraSessionEventsHandler,
				applicationContext, getSurfaceHelper(),
				cameraName,
				width(), height(), framerate());
		}, (long) delayMs);
	}
	
	@Override
	public void stopCapture() {
		Logging.d(TAG, "Stop capture");
		synchronized (stateLock) {
			while (sessionOpening) {
				Logging.d(TAG, "Stop capture: Waiting for session to open");
				
				try {
					stateLock.wait(1000);
				} catch (final InterruptedException e) {
					Logging.w(TAG, "Stop capture interrupted while waiting for the session to open.");
					Thread.currentThread().interrupt();
					return;
				}
			}
			
			if (currentSession != null) {
				Logging.d(TAG, "Stop capture: Nulling session");
				final SurfaceCameraSession oldSession = currentSession;
				post(() -> {
					oldSession.stop();
				});
				currentSession = null;
				capturerObserver.onCapturerStopped();
			} else {
				Logging.d(TAG, "Stop capture: No session open");
			}
		}
		super.stopCapture();
		Logging.d(TAG, "Stop capture done");
	}
	
	@Override
	public void changeCaptureFormat(final int width, final int height, final int framerate) {
		Logging.d(TAG,
			String.format(Locale.US, "changeCaptureFormat:(%dx%d@%d)",
				width, height, framerate));
		super.changeCaptureFormat(width, height, framerate);
		synchronized (stateLock) {
			stopCapture();
			startCapture(width, height, framerate);
		}
	}
	
	@Override
	public void switchCamera(final CameraSwitchListener listener) {
		Logging.d(TAG, "switchCamera");
		post(() -> {
			switchCameraInternal(listener);
		});
	}
	
	@Override
	@NonNull
	protected float[] onUpdateTexMatrix(@NonNull final float[] transformMatrix) {
		float[] matrix = transformMatrix;
		if ((currentSession != null) && (currentSession.getFace() == 1)) {
			matrix = RendererCommon.multiplyMatrices(matrix, RendererCommon.horizontalFlipMatrix());
		}
		return matrix;
	}

	@Override
	protected int getFrameRotation() {
		return currentSession != null ? currentSession.getRotation() : 0;
	}

	private void reportCameraSwitchError(String error, @Nullable CameraSwitchListener listener) {
		Logging.e(TAG, error);
		if (listener != null) {
			listener.onCameraSwitchError(error);
		}
	}
	
	private void switchCameraInternal(@Nullable CameraSwitchListener listener) {
		Logging.d(TAG, "switchCamera internal");
		final String[] deviceNames = cameraEnumerator.getDeviceNames();
		if (deviceNames.length < 2) {
			if (listener != null) {
				listener.onCameraSwitchError("No camera to switch to.");
			}
			
		} else {
			synchronized (stateLock) {
				if (switchState != CameraSurfaceCapture.SwitchState.IDLE) {
					reportCameraSwitchError("Camera switch already in progress.", listener);
					return;
				}
				
				if (!sessionOpening && currentSession == null) {
					reportCameraSwitchError("switchCamera: camera is not running.", listener);
					return;
				}
				
				switchEventsHandler = listener;
				if (sessionOpening) {
					switchState = CameraSurfaceCapture.SwitchState.PENDING;
					return;
				}
				
				switchState = CameraSurfaceCapture.SwitchState.IN_PROGRESS;
				Logging.d(TAG, "switchCamera: Stopping session");
				final SurfaceCameraSession oldSession = currentSession;
				post(() -> {
					oldSession.stop();
				});
				currentSession = null;
				final int cameraNameIndex = Arrays.asList(deviceNames).indexOf(cameraName);
				cameraName = deviceNames[(cameraNameIndex + 1) % deviceNames.length];
				sessionOpening = true;
				openAttemptsRemaining = 1;
				createSessionInternal(0);
			}
			
			Logging.d(TAG, "switchCamera done");
		}
	}
	
	protected String getCameraName() {
		synchronized (stateLock) {
			return cameraName;
		}
	}
	
	/**
	 * SurfaceCameraSessionを生成する
	 * @param createSessionCallback
	 * @param cameraSessionEventsHandler
	 * @param applicationContext
	 * @param surfaceHelper
	 * @param cameraName
	 * @param width
	 * @param height
	 * @param framerate
	 */
	protected abstract void createCameraSession(
		final SurfaceCameraSession.CreateSessionCallback createSessionCallback,
		final SurfaceCameraSession.Events cameraSessionEventsHandler,
		@NonNull final Context applicationContext,
		final SurfaceTextureHelper surfaceHelper, String cameraName,
		final int width, final int height, final int framerate);
	
	private final Runnable openCameraTimeoutRunnable = new Runnable() {
		public void run() {
			captureListener.onCameraError("Camera failed to start within timeout.");
		}
	};

	private final SurfaceCameraSession.CreateSessionCallback createSessionCallback
		= new SurfaceCameraSession.CreateSessionCallback() {

		public void onDone(final SurfaceCameraSession session) {
			checkIsOnCaptureThread();
			Logging.d(TAG, "Create session done. Switch state: " + switchState);
			uiThreadHandler.removeCallbacks(openCameraTimeoutRunnable);
			synchronized (stateLock) {
				capturerObserver.onCapturerStarted(true);
				sessionOpening = false;
				currentSession = session;
				stateLock.notifyAll();
				if (switchState == CameraSurfaceCapture.SwitchState.IN_PROGRESS) {
					if (switchEventsHandler != null) {
						switchEventsHandler.onCameraSwitchDone(cameraEnumerator.isFrontFacing(cameraName));
						switchEventsHandler = null;
					}
					
					switchState = CameraSurfaceCapture.SwitchState.IDLE;
				} else if (switchState == CameraSurfaceCapture.SwitchState.PENDING) {
					switchState = CameraSurfaceCapture.SwitchState.IDLE;
					switchCameraInternal(switchEventsHandler);
				}
				
			}
		}
		
		public void onFailure(final SurfaceCameraSession.FailureType failureType, final String error) {
			checkIsOnCaptureThread();
			uiThreadHandler.removeCallbacks(openCameraTimeoutRunnable);
			synchronized (stateLock) {
				capturerObserver.onCapturerStarted(false);
				openAttemptsRemaining--;
				if (openAttemptsRemaining <= 0) {
					Logging.w(TAG, "Opening camera failed, passing: " + error);
					sessionOpening = false;
					stateLock.notifyAll();
					if (switchState != CameraSurfaceCapture.SwitchState.IDLE) {
						if (switchEventsHandler != null) {
							switchEventsHandler.onCameraSwitchError(error);
							switchEventsHandler = null;
						}
						
						switchState = CameraSurfaceCapture.SwitchState.IDLE;
					}
					
					if (failureType == SurfaceCameraSession.FailureType.DISCONNECTED) {
						captureListener.onCameraDisconnected();
					} else {
						captureListener.onCameraError(error);
					}
				} else {
					Logging.w(TAG, "Opening camera failed, retry: " + error);
					createSessionInternal(500);
				}
				
			}
		}
	};

	private final SurfaceCameraSession.Events cameraSessionEventsHandler
		= new SurfaceCameraSession.Events() {

		@Override
		public void onCameraOpening() {
			checkIsOnCaptureThread();
			synchronized (stateLock) {
				if (currentSession != null) {
					Logging.w(TAG, "onCameraOpening while session was open.");
				} else {
					captureListener.onCameraOpening(cameraName);
				}
			}
		}
		
		@Override
		public void onCameraError(final SurfaceCameraSession session, final String error) {
			checkIsOnCaptureThread();
			synchronized (stateLock) {
				if (session != currentSession) {
					Logging.w(TAG, "onCameraError from another session: " + error);
				} else {
					captureListener.onCameraError(error);
					stopCapture();
				}
			}
		}
		
		@Override
		public void onCameraDisconnected(final SurfaceCameraSession session) {
			checkIsOnCaptureThread();
			synchronized (stateLock) {
				if (session != currentSession) {
					Logging.w(TAG, "onCameraDisconnected from another session.");
				} else {
					captureListener.onCameraDisconnected();
					stopCapture();
				}
			}
		}
		
		@Override
		public void onCameraClosed(final SurfaceCameraSession session) {
			checkIsOnCaptureThread();
			synchronized (stateLock) {
				if (session != currentSession && currentSession != null) {
					Logging.d(TAG, "onCameraClosed from another session.");
				} else {
					captureListener.onCameraClosed();
				}
			}
		}
		
//		public void onFrameCaptured(final SurfaceCameraSession session, final VideoFrame frame) {
//			checkIsOnCaptureThread();
//			synchronized (stateLock) {
//				if (session != currentSession) {
//					Logging.w(TAG, "onFrameCaptured from another session.");
//				} else {
//					if (!firstFrameObserved) {
//						captureListener.onFirstFrameAvailable();
//						firstFrameObserved = true;
//					}
//
//					cameraStatistics.addFrame();
//					capturerObserver.onFrameCaptured(frame);
//				}
//			}
//		}
	};

}
