package com.serenegiant.webrtc;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.support.annotation.NonNull;

import org.webrtc.CameraEnumerator;
import org.webrtc.Logging;
import org.webrtc.RendererCommon;
import org.webrtc.SurfaceTextureHelper;

import java.util.Arrays;

import javax.annotation.Nullable;

public abstract class CameraSurfaceCapture extends SurfaceCaptureAndroid
	implements CameraSurfaceVideoCapture {

	private static final boolean DEBUG = true; // set false on production
	private static final String TAG = CameraSurfaceCapture.class.getSimpleName();
	
	private static final int MAX_OPEN_CAMERA_ATTEMPTS = 3;
	private static final int OPEN_CAMERA_DELAY_MS = 500;
	private static final int OPEN_CAMERA_TIMEOUT = 10000;
	
	static enum SwitchState {
		IDLE,
		PENDING,
		IN_PROGRESS
	}
	
	private final CameraEnumerator cameraEnumerator;
	@NonNull
	private final CameraSurfaceVideoCapture.CameraEventsHandler eventsHandler;
	private final Handler uiThreadHandler;

	private boolean sessionOpening;
	@Nullable
	private SurfaceCameraSession currentSession;
	private String cameraName;
	private int openAttemptsRemaining;
	private CameraSurfaceCapture.SwitchState switchState;
	@Nullable
	private CameraSurfaceVideoCapture.CameraSwitchHandler switchEventsHandler;
	@Nullable
	private CameraSurfaceVideoCapture.CameraStatistics cameraStatistics;
	
	public CameraSurfaceCapture(final String cameraName,
		@Nullable CameraSurfaceVideoCapture.CameraEventsHandler eventsHandler,
		final CameraEnumerator cameraEnumerator) {

		this.switchState = CameraSurfaceCapture.SwitchState.IDLE;
		if (eventsHandler == null) {
			eventsHandler = new CameraSurfaceVideoCapture.CameraEventsHandler() {
				public void onCameraError(String errorDescription) {
				}
				
				public void onCameraDisconnected() {
				}
				
				public void onCameraFreezed(String errorDescription) {
				}
				
				public void onCameraOpening(String cameraName) {
				}
				
				public void onFirstFrameAvailable() {
				}
				
				public void onCameraClosed() {
				}
			};
		}
		
		this.eventsHandler = eventsHandler;
		this.cameraEnumerator = cameraEnumerator;
		this.cameraName = cameraName;
		this.uiThreadHandler = new Handler(Looper.getMainLooper());
		final String[] deviceNames = cameraEnumerator.getDeviceNames();
		if (deviceNames.length == 0) {
			throw new RuntimeException("No cameras attached.");
		} else if (!Arrays.asList(deviceNames).contains(this.cameraName)) {
			throw new IllegalArgumentException("Camera name " + this.cameraName + " does not match any known camera device.");
		}
	}
	
	@Override
	public void startCapture(final int width, final int height, final int framerate) {
		super.startCapture(width, height, framerate);
		Logging.d(TAG, "startCapture: " + width + "x" + height + "@" + framerate);
		synchronized (stateLock) {
			if (!this.sessionOpening && this.currentSession == null) {
				sessionOpening = true;
				openAttemptsRemaining = 3;
				createSessionInternal(0);
			} else {
				Logging.w(TAG, "Session already open");
			}
		}
	}
	
	private void createSessionInternal(int delayMs) {
		this.uiThreadHandler.postDelayed(openCameraTimeoutRunnable, (long) (delayMs + 10000));
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
				cameraStatistics.release();
				cameraStatistics = null;
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
		Logging.d(TAG, "changeCaptureFormat: " + width + "x" + height + "@" + framerate);
		super.changeCaptureFormat(width, height, framerate);
		synchronized (stateLock) {
			stopCapture();
			startCapture(width, height, framerate);
		}
	}
	
	@Override
	public void switchCamera(final CameraSurfaceVideoCapture.CameraSwitchHandler switchEventsHandler) {
		Logging.d(TAG, "switchCamera");
		post(() -> {
			switchCameraInternal(switchEventsHandler);
		});
	}
	
	@Override
	public boolean isScreencast() {
		return false;
	}
	
	@Override
	@NonNull
	protected float[] onUpdateTexMatrix(@NonNull final float[] transformMatrix) {
		float[] matrix = transformMatrix;
		if (currentSession.getFace() == 1) {
			matrix = RendererCommon.multiplyMatrices(matrix, RendererCommon.horizontalFlipMatrix());
		}
		return matrix;
	}

	@Override
	protected int getFrameRotation() {
		return currentSession.getRotation();
	}

	private void reportCameraSwitchError(String error, @Nullable CameraSurfaceVideoCapture.CameraSwitchHandler switchEventsHandler) {
		Logging.e(TAG, error);
		if (switchEventsHandler != null) {
			switchEventsHandler.onCameraSwitchError(error);
		}
	}
	
	private void switchCameraInternal(@Nullable CameraSurfaceVideoCapture.CameraSwitchHandler switchEventsHandler) {
		Logging.d(TAG, "switchCamera internal");
		final String[] deviceNames = this.cameraEnumerator.getDeviceNames();
		if (deviceNames.length < 2) {
			if (switchEventsHandler != null) {
				switchEventsHandler.onCameraSwitchError("No camera to switch to.");
			}
			
		} else {
			synchronized (stateLock) {
				if (switchState != CameraSurfaceCapture.SwitchState.IDLE) {
					this.reportCameraSwitchError("Camera switch already in progress.", switchEventsHandler);
					return;
				}
				
				if (!sessionOpening && currentSession == null) {
					this.reportCameraSwitchError("switchCamera: camera is not running.", switchEventsHandler);
					return;
				}
				
				this.switchEventsHandler = switchEventsHandler;
				if (sessionOpening) {
					switchState = CameraSurfaceCapture.SwitchState.PENDING;
					return;
				}
				
				switchState = CameraSurfaceCapture.SwitchState.IN_PROGRESS;
				Logging.d(TAG, "switchCamera: Stopping session");
				this.cameraStatistics.release();
				this.cameraStatistics = null;
				final SurfaceCameraSession oldSession = currentSession;
				post(() -> {
					oldSession.stop();
				});
				currentSession = null;
				int cameraNameIndex = Arrays.asList(deviceNames).indexOf(this.cameraName);
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
			return this.cameraName;
		}
	}
	
	protected abstract void createCameraSession(
		final SurfaceCameraSession.CreateSessionCallback createSessionCallback,
		final SurfaceCameraSession.Events cameraSessionEventsHandler,
		final Context applicationContext,
		final SurfaceTextureHelper surfaceHelper, String cameraName,
		final int width, final int height, final int framerate);
	
	private final Runnable openCameraTimeoutRunnable = new Runnable() {
		public void run() {
			eventsHandler.onCameraError("Camera failed to start within timeout.");
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
				cameraStatistics = new CameraSurfaceVideoCapture.CameraStatistics(getSurfaceHelper(), eventsHandler);
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
						eventsHandler.onCameraDisconnected();
					} else {
						eventsHandler.onCameraError(error);
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
					eventsHandler.onCameraOpening(cameraName);
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
					eventsHandler.onCameraError(error);
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
					eventsHandler.onCameraDisconnected();
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
					eventsHandler.onCameraClosed();
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
//						eventsHandler.onFirstFrameAvailable();
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
