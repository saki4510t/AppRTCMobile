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
 * 内蔵カメラ映像をSurface/SurfaceTextureを経由してWebRTCへ流し込むための
 * SurfaceVideoCaptureインターフェース
 *
 * WebRTCオフィシャルライブラリの内蔵カメラアクセスクラスCameraVideoCapture.javaを参考に作成
 */
public interface CameraSurfaceVideoCapture extends SurfaceVideoCapture {

	/**
	 * 内蔵カメラを切り替え
	 * @param listener
	 */
	public void switchCamera(final CameraSwitchListener listener);
	
	public interface CameraSwitchListener {
		public void onCameraSwitchDone(final boolean var1);
		
		public void onCameraSwitchError(final String errorDescription);
	}
	
	public interface CameraCaptureListener extends CaptureListener {
		public void onCameraError(String errorDescription);
		
		public void onCameraDisconnected();
		
		public void onCameraOpening(final String cameraName);
		
		public void onCameraClosed();
	}
}
