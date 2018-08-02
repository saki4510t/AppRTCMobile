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

/**
 * 内蔵カメラをSurfaceVideoCaptureで使うためのヘルパーインターフェース
 *
 * WebRTCオフィシャルライブラリの内蔵カメラアクセスクラスCameraSession.javaを参考に作成
 */
public interface SurfaceCameraSession {
	/**
	 * カメラを停止
	 */
	public void stop();
	
	/**
	 * カメラの方向(フロントカメラ/バックカメラ)を取得
	 * @return
	 */
	public int getFace();
	
	/**
	 * カメラの回転方向を取得
	 * @return
	 */
	public int getRotation();
	
	public interface Events {
		public void onCameraOpening();
		
		public void onCameraError(final SurfaceCameraSession session, String error);
		
		public void onCameraDisconnected(final SurfaceCameraSession session);
		
		public void onCameraClosed(final SurfaceCameraSession session);
	}
	
	public interface CreateSessionCallback {
		public void onDone(final SurfaceCameraSession session);
		
		public void onFailure(final SurfaceCameraSession.FailureType failureType, final String error);
	}
	
	public static enum FailureType {
		ERROR,
		DISCONNECTED
	}
}
