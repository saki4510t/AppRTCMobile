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
import android.util.Log;
import android.view.Surface;

import com.serenegiant.glutils.IRendererHolder;
import com.serenegiant.glutils.RendererHolder;
import com.serenegiant.math.Fraction;

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
public class SurfaceCaptureAndroid implements SurfaceVideoDistributeCapture {

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
	private int frameRotation;
	/**
	 * このクラス内でIRendererHolderを生成したかどうか
	 */
	private boolean mOwnRendererHolder;
	@Nullable
	private IRendererHolder mRendererHolder;
	private int mCaptureSurfaceId;
	@Nullable
	private Statistics mStatistics;
	private boolean firstFrameObserved;
	private volatile CaptureState state;

	/**
	 * コンストラクタ
	 * @param rendererHolder
	 * @param captureListener
	 */
	public SurfaceCaptureAndroid(
		@Nullable final IRendererHolder rendererHolder,
		@NonNull final CaptureListener captureListener) {

		mOwnRendererHolder = rendererHolder == null;
		mRendererHolder = rendererHolder;
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
		captureListener.onInitialized(this);
	}
	
	@Override
	public void startCapture(final int width, final int height, final int framerate) {
		if (DEBUG) Log.v(TAG, "startCapture:");
		synchronized (stateLock) {
			checkNotDisposed();
			if (surfaceHelper != null) {
				mStatistics = new Statistics(this, surfaceHelper, captureListener, CAMERA_FAILURE_COUNTS);
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
			setSurface(false);
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
			setSurface(false);
		}
	}
	
	@Override
	public void dispose() {
		if (DEBUG) Log.v(TAG, "dispose:");
		stopCapture();
		synchronized (stateLock) {
			isDisposed = true;
			surfaceHelper = null;
			releaseRendererHolder();
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
			if (DEBUG && ((numCapturedFrames % 100) == 0))
				Log.v(TAG, "onFrame:" + numCapturedFrames);

			final VideoFrame modifiedFrame = new VideoFrame(
				SurfaceVideoCapture.createTextureBufferWithModifiedTransformMatrix(
					(TextureBufferImpl)frame.getBuffer(), isMirror(), 0),
				getFrameRotation(), frame.getTimestampNs());
			capturerObserver.onFrameCaptured(modifiedFrame);
			modifiedFrame.release();
			if (!firstFrameObserved) {
				captureListener.onFirstFrameAvailable(SurfaceCaptureAndroid.this);
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
			mRendererHolder.addSurface(id, surface, isRecordable, new Fraction(maxFps));
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

	@Override
	public boolean isEnabled() {
		synchronized (stateLock) {
			return (mRendererHolder != null)
				&& mRendererHolder.isEnabled(mCaptureSurfaceId);
		}
	}

	@Override
	public void setEnabled(final boolean enabled) {
		synchronized (stateLock) {
			if (mRendererHolder != null) {
				mRendererHolder.setEnabled(mCaptureSurfaceId, enabled);
			}
		}
	}

	/**
	 * オフスクリーン描画・分配描画用のIRendererHolderを生成
	 * @return
	 */
	@NonNull
	protected IRendererHolder createRendererHolder() {
		return new RendererHolder(width, height, DEBUG ? mRenderHolderCallback : null);
	}
	
	/**
	 * オフスクリーン描画・分配描画用のIRendererHolderを破棄
	 */
	protected void releaseRendererHolder() {
		if (mOwnRendererHolder && (mRendererHolder != null)) {
			mRendererHolder.release();
		}
		mRendererHolder = null;
	}

	/**
	 * 映像フレームの回転角を設定
	 * カメラアクセスする下位クラスはここで設定した値よりもカメラから取得した値が優先される
	 * @param rotation
	 */
	public void setFrameRotation(final int rotation) {
		frameRotation = ((rotation / 90) * 90) % 360;
	}

	/**
	 * 映像フレームの回転角を取得
	 * @return
	 */
	protected int getFrameRotation() {
		return frameRotation;
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
	
	/**
	 * IRendererHolderがなければ生成する
	 */
	@Nullable
	public IRendererHolder getRendererHolder() {
		synchronized (stateLock) {
			if (!isDisposed && (mRendererHolder == null)) {
				mOwnRendererHolder = true;
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
				mOwnRendererHolder = true;
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
			if (surfaceHelper != null) {
				surfaceHelper.setTextureSize(width, height);
			}
		}
	}
	
	/**
	 * オフスクリーン描画/分配描画用のIRendererHolderへ
	 * WebRTCへの映像入力用SurfaceTextureをセット
	 */
	protected void setSurface(final boolean isRecordable) {
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
			mRendererHolder.addSurface(mCaptureSurfaceId, surface, isRecordable);
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
	
	private final IRendererHolder.RenderHolderCallback mRenderHolderCallback
		= new IRendererHolder.RenderHolderCallback() {
		@Override
		public void onCreate(final Surface surface) {
			if (DEBUG) Log.v(TAG, "onCreate:");
		}
		
		@Override
		public void onFrameAvailable() {
//			if (DEBUG) Log.v(TAG, "onFrameAvailable:");
		}
		
		@Override
		public void onDestroy() {
			if (DEBUG) Log.v(TAG, "onDestroy:");
		}
	};
}
