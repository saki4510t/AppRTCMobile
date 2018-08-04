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
import android.graphics.SurfaceTexture;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.util.Log;
import android.view.Surface;

import com.serenegiant.glutils.IRendererHolder;
import com.serenegiant.glutils.RenderHolderCallback;
import com.serenegiant.glutils.RendererHolder;

import org.webrtc.Logging;
import org.webrtc.RendererCommon;
import org.webrtc.SurfaceTextureHelper;
import org.webrtc.ThreadUtils;
import org.webrtc.VideoFrame;

import javax.annotation.Nullable;

/**
 * Surface/SurfaceTextureから映像入力してWebRTCへ流すための
 * SurfaceVideoCaptureインターフェースの実装
 * WebRTCのオフィシャル実装では直接カメラ映像をWebRTCへ引き渡すので
 * 途中で映像効果を付与したり内蔵カメラ以外の映像を流すのが面倒なので
 * Surface/SurfaceTextureから映像を入力してWebRTCへ流すための
 * 汎用インターフェースとして作成
 */
public class SurfaceCaptureAndroid implements SurfaceVideoCapture {

	private static final boolean DEBUG = false; // set false on production
	private static final String TAG = SurfaceCaptureAndroid.class.getSimpleName();

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
	private IRendererHolder mRendererHolder;
	private int mCaptureSurfaceId;
	@Nullable
	private Statistics mStatistics;
	private boolean firstFrameObserved;

	public SurfaceCaptureAndroid(@NonNull final CaptureListener captureListener) {
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
			capturerObserver.onCapturerStarted(true);
			surfaceHelper.startListening(mOnTextureFrameAvailableListener);
			resize(width, height);
			setSurface();
		}
	}
	
	@Override
	public void stopCapture() {
		if (DEBUG) Log.v(TAG, "stopCapture:");
		synchronized (stateLock) {
			checkNotDisposed();
			if (mStatistics != null) {
				mStatistics.release();
				mStatistics = null;
			}
			firstFrameObserved = false;
			ThreadUtils.invokeAtFrontUninterruptibly(surfaceHelper.getHandler(), new Runnable() {
				public void run() {
					if (mRendererHolder != null) {
						mRendererHolder.removeSurface(mCaptureSurfaceId);
					}
					mCaptureSurfaceId = 0;
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
			setSurface();
		}
	}
	
	@Override
	public void dispose() {
		if (DEBUG) Log.v(TAG, "dispose:");
		stopCapture();
		synchronized (stateLock) {
			isDisposed = true;
			releaseRendererHolder();
		}
	}
	
	@Override
	public boolean isScreencast() {
		return false;
	}

	private final SurfaceTextureHelper.OnTextureFrameAvailableListener
		mOnTextureFrameAvailableListener = new SurfaceTextureHelper.OnTextureFrameAvailableListener() {
		@Override
		public void onTextureFrameAvailable(
			final int oesTextureId, final float[] transformMatrix,
			final long timestampNs) {
	
			++numCapturedFrames;
			if (DEBUG && ((numCapturedFrames % 100) == 0)) Log.v(TAG, "onTextureFrameAvailable:" + numCapturedFrames);
			final VideoFrame.Buffer buffer = surfaceHelper.createTextureBuffer(width, height,
				RendererCommon.convertMatrixToAndroidGraphicsMatrix(onUpdateTexMatrix(transformMatrix)));
			final VideoFrame frame = new VideoFrame(buffer, getFrameRotation(), timestampNs);
			try {
				capturerObserver.onFrameCaptured(frame);
			} finally {
				frame.release();
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
	@Override
	@Nullable
	public Surface getInputSurface() {
		synchronized (stateLock) {
			getRendererHolder();
			return mRendererHolder != null ? mRendererHolder.getSurface() : null;
		}
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
			getRendererHolder();
			return mRendererHolder != null ? mRendererHolder.getSurfaceTexture() : null;
		}
	}

	/**
	 * 分配描画用の描画先Surfaceをセット
	 * @param id
	 * @param surface　Surface/SurfaceTexture/SurfaceHolderのいずれか
	 * @param isRecordable
	 */
	@Override
	public void addSurface(final int id, final Object surface,
		final boolean isRecordable) throws IllegalStateException {

		synchronized (stateLock) {
			checkNotDisposed();
			requireRendererHolder();
			mRendererHolder.addSurface(id, surface, isRecordable);
		}
	}
	
	/**
	 * 分配描画用の描画先Surfaceをセット
	 * @param id
	 * @param surface　Surface/SurfaceTexture/SurfaceHolderのいずれか
	 * @param isRecordable
	 * @param maxFps
	 */
	@Override
	public void addSurface(final int id, final Object surface,
		final boolean isRecordable, final int maxFps) throws IllegalStateException {

		synchronized (stateLock) {
			checkNotDisposed();
			requireRendererHolder();
			mRendererHolder.addSurface(id, surface, isRecordable, maxFps);
		}
	}

	/**
	 * 分配描画先Surfaceを削除
	 * @param id
	 */
	@Override
	public void removeSurface(final int id) {
		synchronized (stateLock) {
			getRendererHolder();
			if (mRendererHolder != null) {
				mRendererHolder.removeSurface(id);
			}
		}
	}

	/**
	 * オフスクリーン描画・分配描画用のIRendererHolderを生成
	 * @return
	 */
	@NonNull
	protected IRendererHolder createRendererHolder() {
		return new RendererHolder(width, height, mRenderHolderCallback);
	}
	
	/**
	 * オフスクリーン描画・分配描画用のIRendererHolderを破棄
	 */
	protected void releaseRendererHolder() {
		if (mRendererHolder != null) {
			mRendererHolder.release();
			mRendererHolder = null;
		}
	}

	/**
	 * 映像回転用のモデルビュー変換行列を取得
	 * @param transformMatrix
	 * @return
	 */
	@NonNull
	protected float[] onUpdateTexMatrix(@NonNull final float[] transformMatrix) {
		return transformMatrix;
	}
	
	/**
	 * 映像ブレームの回転角を取得
	 * @return
	 */
	protected int getFrameRotation() {
		return 0;
	}

	protected void checkNotDisposed() throws IllegalStateException {
		if (isDisposed) {
			throw new IllegalStateException("capturer is disposed.");
		}
	}
	
	/**
	 * IRendererHolderがなければ生成する
	 */
	@Nullable
	public IRendererHolder getRendererHolder() {
		synchronized (stateLock) {
			if (!isDisposed && (mRendererHolder == null)) {
				mRendererHolder = createRendererHolder();
			}
			return mRendererHolder;
		}
	}
	
	/**
	 * IRendererHolderがなければ生成する
	 * @return
	 * @throws IllegalStateException
	 */
	@NonNull
	public IRendererHolder requireRendererHolder() throws IllegalStateException {
		synchronized (stateLock) {
			if (isDisposed) {
				throw new IllegalStateException();
			}
			if (mRendererHolder == null) {
				mRendererHolder = createRendererHolder();
			}
			return mRendererHolder;
		}
	}

	private void resize(final int width, final int height) {
		synchronized (stateLock) {
			checkNotDisposed();
			getRendererHolder();
			if (mRendererHolder != null) {
				mRendererHolder.resize(width, height);
			}
		}
	}
	
	/**
	 * オフスクリーン描画/分配描画用のIRendererHolderへ
	 * WebRTCへの映像入力用SurfaceTextureをセット
	 */
	private void setSurface() {
		synchronized (stateLock) {
			if ((mCaptureSurfaceId != 0) && (mRendererHolder != null)) {
				mRendererHolder.removeSurface(mCaptureSurfaceId);
			}
			mCaptureSurfaceId = 0;
			final SurfaceTexture st = surfaceHelper.getSurfaceTexture();
			st.setDefaultBufferSize(width, height);
			final Surface surface = new Surface(st);
			requireRendererHolder();
			mCaptureSurfaceId = surface.hashCode();
			mRendererHolder.addSurface(mCaptureSurfaceId, surface, false);
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
	
	private final RenderHolderCallback mRenderHolderCallback
		= new RenderHolderCallback() {
		@Override
		public void onCreate(final Surface surface) {
			if (DEBUG) Log.v(TAG, "onCreate:");
		}
		
		@Override
		public void onFrameAvailable() {
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
		
		@Override
		public void onDestroy() {
			if (DEBUG) Log.v(TAG, "onDestroy:");
		}
	};
}
