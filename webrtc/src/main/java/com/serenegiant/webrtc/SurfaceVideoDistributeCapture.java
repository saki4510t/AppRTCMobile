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
 * Surface/SurfaceTextureからIRendererHolderへ映像入力して
 * WebRTCへ流すためのVideoCapturerインターフェース
 * WebRTCのオフィシャル実装では直接カメラ映像をWebRTCへ引き渡すので
 * 途中で映像効果を付与したり内蔵カメラ以外の映像を流すのが面倒なので
 * Surface/SurfaceTextureから映像を入力してWebRTCへ流すための
 * 汎用インターフェースとして作成
 *
 * WebRTCオフィシャルライブラリの内蔵カメラアクセスクラスCameraVideoCapture.javaを参考に作成
 */
public interface SurfaceVideoDistributeCapture extends SurfaceVideoCapture {
	/**
	 * 分配描画用の描画先Surfaceをセット
	 * @param id
	 * @param surface　Surface/SurfaceTexture/SurfaceHolderのいずれか
	 * @param isRecordable
	 */
	public void addSurface(final int id, final Object surface, final boolean isRecordable);
	
	/**
	 * 分配描画用の描画先Surfaceをセット
	 * @param id
	 * @param surface　Surface/SurfaceTexture/SurfaceHolderのいずれか
	 * @param isRecordable
	 * @param maxFps
	 */
	public void addSurface(final int id, final Object surface, final boolean isRecordable, final int maxFps);

	/**
	 * 分配描画先Surfaceを削除
	 * @param id
	 */
	public void removeSurface(final int id);

	public boolean isEnabled();

	public void setEnabled(final boolean enabled);
}
