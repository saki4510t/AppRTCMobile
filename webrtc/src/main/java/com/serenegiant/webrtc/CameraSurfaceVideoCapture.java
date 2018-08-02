package com.serenegiant.webrtc;

/**
 * 内蔵カメラ映像をSurface/SurfaceTextureを経由してWebRTCへ流し込むための
 * SurfaceVideoCaptureインターフェース
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
