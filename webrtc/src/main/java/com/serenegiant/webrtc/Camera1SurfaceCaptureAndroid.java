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
import androidx.annotation.NonNull;
import android.util.Log;

import org.webrtc.Camera1Enumerator;
import org.webrtc.SurfaceTextureHelper;

/**
 * 内蔵カメラへCamera APIでアクセスしてSurface/SurfaceTexture経由で
 * WebRTCへ流すためのCameraSurfaceCapture実装
 *
 * WebRTCオフィシャルライブラリの内蔵カメラアクセスクラスCamera1Capture.javaを元に作成
 * FIXME Camera2Captureに対応するクラスをCamera2SurfaceCaptureAndroidを追加する
 */
public class Camera1SurfaceCaptureAndroid extends CameraSurfaceCapture {
	private static final boolean DEBUG = false; // set false on production
	private static final String TAG = Camera1SurfaceCaptureAndroid.class.getSimpleName();
	
	/**
	 * コンストラクタ
	 * @param cameraName
	 */
	public Camera1SurfaceCaptureAndroid(final String cameraName) {
		super(null, cameraName, null, new Camera1Enumerator(true));
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
