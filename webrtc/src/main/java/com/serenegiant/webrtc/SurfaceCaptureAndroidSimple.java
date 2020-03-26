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

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.SurfaceTexture;
import android.os.Handler;
import android.util.Log;
import android.view.Surface;

import org.webrtc.CapturerObserver;
import org.webrtc.Logging;
import org.webrtc.SurfaceTextureHelper;
import org.webrtc.TextureBufferImpl;
import org.webrtc.ThreadUtils;
import org.webrtc.VideoFrame;
import org.webrtc.VideoSink;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Surface/SurfaceTextureから映像入力してWebRTCへ流すための
 * SurfaceVideoCaptureインターフェースの実装
 * WebRTCのオフィシャル実装では直接カメラ映像をWebRTCへ引き渡すので
 * 途中で映像効果を付与したり内蔵カメラ以外の映像を流すのが面倒なので
 * Surface/SurfaceTextureから映像を入力してWebRTCへ流すための
 * 汎用インターフェースとして作成
 */
public class SurfaceCaptureAndroidSimple implements SurfaceVideoCapture {

	private static final boolean DEBUG = false; // set false on production
	private static final String TAG = SurfaceCaptureAndroidSimple.class.getSimpleName();

	protected final Object stateLock = new Object();
	@NonNull
	private final CaptureListener captureListener;
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
	@Nullable
	private Statistics mStatistics;
	private boolean firstFrameObserved;
	private volatile CaptureState state;

	public SurfaceCaptureAndroidSimple(@NonNull final CaptureListener captureListener) {
		this.captureListener = captureListener;
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
					captureThreadHandler = surfaceTextureHelper.getHandler();
				}
			}
		}
	}
	
	@Override
	public void startCapture(final int width, final int height, final int framerate) {
		if (DEBUG) Log.v(TAG, "startCapture:");
		synchronized (stateLock) {
			checkNotDisposed();
			if (surfaceHelper != null) {
				mStatistics = new Statistics(surfaceHelper, captureListener);
			} else {
				throw new IllegalStateException("not initialized");
			}
			this.width = width;
			this.height = height;
			this.framerate = framerate;
			firstFrameObserved = false;
			state = CaptureState.RUNNING;
			capturerObserver.onCapturerStarted(true);
			surfaceHelper.startListening(mVideoSink);
			resize(width, height);
		}
	}
	
	@Override
	public void stopCapture() {
		if (DEBUG) Log.v(TAG, "stopCapture:");
		synchronized (stateLock) {
			state = CaptureState.STOPPED;
			checkNotDisposed();
			if (mStatistics != null) {
				mStatistics.release();
				mStatistics = null;
			}
			firstFrameObserved = false;
			ThreadUtils.invokeAtFrontUninterruptibly(surfaceHelper.getHandler(), new Runnable() {
				public void run() {
					surfaceHelper.stopListening();
					capturerObserver.onCapturerStopped();
				}
			});
		}
	}
	
	@Override
	public void changeCaptureFormat(final int width, final int height, final int framerate) {
		if (DEBUG) Log.v(TAG, "changeCaptureFormat:");
		synchronized (stateLock) {
			checkNotDisposed();
			this.width = width;
			this.height = height;
			this.framerate = framerate;
			resize(width, height);
		}
	}
	
	@Override
	public void dispose() {
		if (DEBUG) Log.v(TAG, "dispose:");
		stopCapture();
		synchronized (stateLock) {
			isDisposed = true;
			if (surfaceHelper != null) {
				surfaceHelper.dispose();
				surfaceHelper = null;
			}
		}
	}
	
	@Override
	public boolean isScreencast() {
		return false;
	}

	private final VideoSink mVideoSink = new VideoSink() {
		@Override
		public void onFrame(final VideoFrame frame) {
			++numCapturedFrames;
			if (DEBUG && ((numCapturedFrames % 100) == 0)) Log.v(TAG, "onFrame:" + numCapturedFrames);

			final VideoFrame modifiedFrame = new VideoFrame(
				SurfaceVideoCapture.createTextureBufferWithModifiedTransformMatrix(
					(TextureBufferImpl)frame.getBuffer(), isMirror(), 0),
				getFrameRotation(), frame.getTimestampNs());
			capturerObserver.onFrameCaptured(modifiedFrame);
			modifiedFrame.release();
			if (!firstFrameObserved) {
				captureListener.onFirstFrameAvailable();
				firstFrameObserved = true;
			}
			try {
				if (mStatistics != null) {
					mStatistics.addFrame();
				}
			} catch (final Exception e) {
				// ignore
			}
		}
	};

	public long getNumCapturedFrames() {
		return numCapturedFrames;
	}

	/**
	 * 映像入力用のSurfaceを取得
	 * #getInputSurfaceTextureとは排他使用のこと
	 * @return
	 */
	@SuppressLint("Recycle")
	@Override
	@Nullable
	public Surface getInputSurface() {
		final SurfaceTexture st = getInputSurfaceTexture();
		return st != null ? new Surface(st) : null;
	}

	/**
	 * 映像入力用のSurfaceTextureを取得
	 * #getInputSurfaceとは排他使用のこと
	 * @return
	 */
	@Override
	@Nullable
	public SurfaceTexture getInputSurfaceTexture() {
		synchronized (stateLock) {
			final SurfaceTexture st = surfaceHelper.getSurfaceTexture();
			st.setDefaultBufferSize(width, height);
			return st;
		}
	}

	/**
	 * 映像フレームの回転角を取得
	 * @return
	 */
	protected int getFrameRotation() {
		return 0;
	}
	
	/**
	 * 映像を左右反転させるかどうか
	 * @return false: 反転させない, true:左右反転させる
	 */
	protected boolean isMirror() {
		return false;
	}

	protected void checkNotDisposed() throws IllegalStateException {
		if (isDisposed) {
			throw new IllegalStateException("capturer is disposed.");
		}
	}
	
	private void resize(final int width, final int height) {
		synchronized (stateLock) {
			checkNotDisposed();
			if (surfaceHelper != null) {
				surfaceHelper.setTextureSize(width, height);
			}
		}
	}
	
	protected void checkIsOnCaptureThread() {
		if (Thread.currentThread() != captureThreadHandler.getLooper().getThread()) {
			Logging.e(TAG, "Check is on capture thread failed.");
			throw new RuntimeException("Not on capture thread.");
		}
	}
	
	/**
	 * Run specific task on capture thread with delay in ms
 	 * @param task
	 * @param delayMs
	 */
	protected void postDelayed(final Runnable task, final long delayMs) {
		captureThreadHandler.postDelayed(task, delayMs);
	}

	/**
	 * Run specific task on capture thread
	 * @param task
	 */
	protected void post(final Runnable task) {
		captureThreadHandler.post(task);
	}
	
	/**
	 * get handler for capture thread
	 * @return
	 */
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
