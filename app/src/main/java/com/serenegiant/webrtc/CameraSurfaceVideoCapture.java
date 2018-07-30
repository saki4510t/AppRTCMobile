package com.serenegiant.webrtc;

public interface CameraSurfaceVideoCapture extends SurfaceVideoCapture {

	public void switchCamera(final CameraSwitchHandler handler);
	
	public interface CameraSwitchHandler {
		public void onCameraSwitchDone(final boolean var1);
		
		public void onCameraSwitchError(final String errorDescription);
	}
	
	public interface CameraEventsHandler extends EventsHandler {
		public void onCameraError(String errorDescription);
		
		public void onCameraDisconnected();
		
		public void onCameraOpening(final String cameraName);
		
		public void onCameraClosed();
	}
}
