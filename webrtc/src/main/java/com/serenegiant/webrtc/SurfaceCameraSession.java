package com.serenegiant.webrtc;

/**
 * 内蔵カメラをSurfaceVideoCaptureで使うためのヘルパーインターフェース
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
