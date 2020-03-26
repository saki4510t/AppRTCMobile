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

import android.graphics.Matrix;
import android.graphics.SurfaceTexture;
import android.view.Surface;

import org.webrtc.Logging;
import org.webrtc.SurfaceTextureHelper;
import org.webrtc.TextureBufferImpl;
import org.webrtc.VideoCapturer;
import org.webrtc.VideoFrame;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public interface SurfaceVideoCapture extends VideoCapturer {
	public static enum CaptureState {
		RUNNING,
		STOPPED
	}

	/**
	 * 映像入力用のSurfaceを取得
	 * #getInputSurfaceTextureとは排他使用のこと
	 * @return
	 */
	@Nullable
	public Surface getInputSurface();

	/**
	 * 映像入力用のSurfaceTextureを取得
	 * #getInputSurfaceとは排他使用のこと
	 * @return
	 */
	@Nullable
	public SurfaceTexture getInputSurfaceTexture();

	/**
	 * WebRTCへ流した映像の統計情報(フレームレート)計算用ヘルパークラス
	 */
	public static class Statistics {
		private static final String TAG = Statistics.class.getSimpleName();

		@NonNull
		private final SurfaceTextureHelper surfaceTextureHelper;
		@NonNull
		private final CaptureListener captureListener;
		private int frameCount;
		private int freezePeriodCount;

		public Statistics(@NonNull final SurfaceTextureHelper surfaceTextureHelper,
			@NonNull final CaptureListener captureListener) {

			this.surfaceTextureHelper = surfaceTextureHelper;
			this.captureListener = captureListener;
			frameCount = 0;
			freezePeriodCount = 0;
			surfaceTextureHelper.getHandler().postDelayed(cameraObserver, 2000L);
		}

		private void checkThread() {
			if (Thread.currentThread() != surfaceTextureHelper.getHandler().getLooper().getThread()) {
				throw new IllegalStateException("Wrong thread");
			}
		}

		public void addFrame() {
			checkThread();
			++frameCount;
		}

		public void release() {
			surfaceTextureHelper.getHandler().removeCallbacks(cameraObserver);
		}

		private final Runnable cameraObserver = new Runnable() {
			public void run() {
				final int cameraFps = Math.round((float) frameCount * 1000.0F / 2000.0F);
				Logging.d(TAG, "Camera fps: " + cameraFps + ".");
				if (frameCount == 0) {
					++freezePeriodCount;
					if (2000 * freezePeriodCount >= 4000) {
						Logging.e(TAG, "Camera freezed.");
						if (surfaceTextureHelper.isTextureInUse()) {
							captureListener.onFailure("Camera failure. Client must return video buffers.");
						} else {
							captureListener.onFailure("Camera failure.");
						}

						return;
					}
				} else {
					freezePeriodCount = 0;
				}

				frameCount = 0;
				surfaceTextureHelper.getHandler().postDelayed(this, 2000L);
			}
		};
	}

	/**
	 * SurfaceVideoCaptureからのイベント通知用コールバックリスナー
	 */
	public interface CaptureListener {
		public void onFailure(final String reason);
		public void onFirstFrameAvailable();
	}

	static VideoFrame.TextureBuffer createTextureBufferWithModifiedTransformMatrix(
		@NonNull final TextureBufferImpl buffer, final boolean mirror, final int rotation) {

		final Matrix transformMatrix = new Matrix();
		transformMatrix.preTranslate(0.5F, 0.5F);
		if (mirror) {
			transformMatrix.preScale(-1.0F, 1.0F);
		}

		transformMatrix.preRotate((float)rotation);
		transformMatrix.preTranslate(-0.5F, -0.5F);
		return buffer.applyTransformMatrix(transformMatrix, buffer.getWidth(), buffer.getHeight());
	}
}
