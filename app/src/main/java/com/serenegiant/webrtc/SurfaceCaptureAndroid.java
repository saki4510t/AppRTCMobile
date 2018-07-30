package com.serenegiant.webrtc;

import android.content.Context;
import android.graphics.SurfaceTexture;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.view.Surface;

import com.serenegiant.glutils.IRendererHolder;
import com.serenegiant.glutils.RendererHolder;

import org.webrtc.Logging;
import org.webrtc.RendererCommon;
import org.webrtc.SurfaceTextureHelper;
import org.webrtc.ThreadUtils;
import org.webrtc.VideoFrame;

import javax.annotation.Nullable;

public class SurfaceCaptureAndroid
	implements SurfaceVideoCapture,
		SurfaceTextureHelper.OnTextureFrameAvailableListener {

	private static final boolean DEBUG = true; // set false on production
	private static final String TAG = CameraSurfaceCapture.class.getSimpleName();

	protected final Object stateLock = new Object();
	@Nullable
	private SurfaceTextureHelper surfaceHelper;
	protected Context applicationContext;
	@Nullable
	protected CapturerObserver capturerObserver;
	@Nullable
	private Handler captureThreadHandler;
	private long numCapturedFrames = 0L;
	private boolean isDisposed = false;
	private int width;
	private int height;
	private int framerate;
	private IRendererHolder mRendererHolder;
	private int mCaptureSurfaceId;

	public SurfaceCaptureAndroid() {
		mRendererHolder = new RendererHolder(width, height, null);
	}

	@Override
	public void initialize(final SurfaceTextureHelper surfaceTextureHelper,
		final Context applicationContext, final CapturerObserver capturerObserver) {

		synchronized (stateLock) {
			checkNotDisposed();
			if (capturerObserver == null) {
				throw new RuntimeException("capturerObserver not set.");
			} else {
				this.applicationContext = applicationContext;
				this.capturerObserver = capturerObserver;
				if (surfaceTextureHelper == null) {
					throw new RuntimeException("surfaceTextureHelper not set.");
				} else {
					this.surfaceHelper = surfaceTextureHelper;
					this.captureThreadHandler = surfaceTextureHelper.getHandler();
				}
			}
		}
	}
	
	@Override
	public void startCapture(final int width, final int height, final int framerate) {
		synchronized (stateLock) {
			checkNotDisposed();
			this.width = width;
			this.height = height;
			this.framerate = framerate;
			capturerObserver.onCapturerStarted(true);
			surfaceHelper.startListening(this);
			resize(width, height);
			setSurface();
		}
	}
	
	@Override
	public void stopCapture() {
		synchronized (stateLock) {
			checkNotDisposed();
			ThreadUtils.invokeAtFrontUninterruptibly(this.surfaceHelper.getHandler(), new Runnable() {
				public void run() {
					if (mRendererHolder != null) {
						mRendererHolder.removeSurface(mCaptureSurfaceId);
					}
					mCaptureSurfaceId = 0;
					SurfaceCaptureAndroid.this.surfaceHelper.stopListening();
					SurfaceCaptureAndroid.this.capturerObserver.onCapturerStopped();
				}
			});
		}
	}
	
	@Override
	public void changeCaptureFormat(int width, int height, int framerate) {
		synchronized (stateLock) {
			checkNotDisposed();
			this.width = width;
			this.height = height;
			this.framerate = framerate;
			resize(width, height);
			setSurface();
		}
	}
	
	@Override
	public void dispose() {
		stopCapture();
		synchronized (stateLock) {
			isDisposed = true;
			if (mRendererHolder != null) {
				mRendererHolder.release();
				mRendererHolder = null;
			}
		}
	}
	
	@Override
	public boolean isScreencast() {
		return false;
	}

	@Override
	public void onTextureFrameAvailable(final int oesTextureId, final float[] transformMatrix, final long timestampNs) {
		++numCapturedFrames;
		final VideoFrame.Buffer buffer = this.surfaceHelper.createTextureBuffer(this.width, this.height,
			RendererCommon.convertMatrixToAndroidGraphicsMatrix(onUpdateTexMatrix(transformMatrix)));
		final VideoFrame frame = new VideoFrame(buffer, getFrameRotation(), timestampNs);
		try {
			capturerObserver.onFrameCaptured(frame);
		} finally {
			frame.release();
		}
	}

	@NonNull
	protected float[] onUpdateTexMatrix(@NonNull final float[] transformMatrix) {
		return transformMatrix;
	}
	
	protected int getFrameRotation() {
		return 0;
	}

	public long getNumCapturedFrames() {
		return numCapturedFrames;
	}

	@Override
	@Nullable
	public Surface getInputSurface() {
		synchronized (stateLock) {
			return mRendererHolder != null ? mRendererHolder.getSurface() : null;
		}
	}

	@Override
	@Nullable
	public SurfaceTexture getInputSurfaceTexture() {
		synchronized (stateLock) {
			return mRendererHolder != null ? mRendererHolder.getSurfaceTexture() : null;
		}
	}

	@Override
	public void addSurface(final int id, final Object surface, final boolean isRecordable) {
		synchronized (stateLock) {
			checkNotDisposed();
			final IRendererHolder rendererHolder = mRendererHolder;
			if (!isDisposed && rendererHolder != null) {
				rendererHolder.addSurface(id, surface, isRecordable);
			}
		}
	}
	
	@Override
	public void addSurface(final int id, final Object surface, final boolean isRecordable, final int maxFps) {
		synchronized (stateLock) {
			checkNotDisposed();
			final IRendererHolder rendererHolder = mRendererHolder;
			if (!isDisposed && rendererHolder != null) {
				rendererHolder.addSurface(id, surface, isRecordable, maxFps);
			}
		}
	}

	@Override
	public void removeSurface(final int id) {
		synchronized (stateLock) {
			final IRendererHolder rendererHolder = mRendererHolder;
			if (!isDisposed && rendererHolder != null) {
				rendererHolder.removeSurface(id);
			}
		}
	}

	protected void checkNotDisposed() {
		if (isDisposed) {
			throw new RuntimeException("capturer is disposed.");
		}
	}

	private void resize(final int width, final int height) {
		if (mRendererHolder != null) {
			mRendererHolder.resize(width, height);
		}
	}
	
	private void setSurface() {
		if ((mCaptureSurfaceId != 0) && (mRendererHolder != null)) {
			mRendererHolder.removeSurface(mCaptureSurfaceId);
		}
		final SurfaceTexture st = surfaceHelper.getSurfaceTexture();
		st.setDefaultBufferSize(width, height);
		final Surface surface = new Surface(st);
		mCaptureSurfaceId = surface.hashCode();
		mRendererHolder.addSurface(mCaptureSurfaceId, surface, false);
	}
	
	public void printStackTrace() {
		Thread cameraThread = null;
		if (this.captureThreadHandler != null) {
			cameraThread = this.captureThreadHandler.getLooper().getThread();
		}
		
		if (cameraThread != null) {
			StackTraceElement[] cameraStackTrace = cameraThread.getStackTrace();
			if (cameraStackTrace.length > 0) {
				Logging.d("CameraCapturer", "CameraCapturer stack trace:");
				StackTraceElement[] var3 = cameraStackTrace;
				int var4 = cameraStackTrace.length;
				
				for (int var5 = 0; var5 < var4; ++var5) {
					StackTraceElement traceElem = var3[var5];
					Logging.d("CameraCapturer", traceElem.toString());
				}
			}
		}
		
	}

	protected void checkIsOnCaptureThread() {
		if (Thread.currentThread() != this.captureThreadHandler.getLooper().getThread()) {
			Logging.e("CameraCapturer", "Check is on camera thread failed.");
			throw new RuntimeException("Not on camera thread.");
		}
	}
	

	protected void postDelayed(final Runnable task, final long delayMs) {
		captureThreadHandler.postDelayed(task, delayMs);
	}

	protected void post(final Runnable task) {
		captureThreadHandler.post(task);
	}
	
	protected Handler getCaptureHandler() {
		return captureThreadHandler;
	}
	
	@Nullable
	protected SurfaceTextureHelper getSurfaceHelper() {
		return surfaceHelper;
	}
	
	protected int width() {
		synchronized (stateLock) {
			return width;
		}
	}

	protected int height() {
		synchronized (stateLock) {
			return height;
		}
	}

	protected int framerate() {
		synchronized (stateLock) {
			return framerate;
		}
	}
	
}
