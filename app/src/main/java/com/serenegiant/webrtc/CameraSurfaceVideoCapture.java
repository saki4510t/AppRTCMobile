package com.serenegiant.webrtc;

public interface CameraSurfaceVideoCapture extends SurfaceVideoCapture {

	void switchCamera(CameraSwitchHandler handler);
	
	public interface CameraSwitchHandler {
		void onCameraSwitchDone(boolean var1);
		
		void onCameraSwitchError(String var1);
	}
	
	public interface CameraEventsHandler extends EventsHandler {
		void onCameraError(String var1);
		
		void onCameraDisconnected();
		
		void onCameraOpening(String var1);
		
		void onFirstFrameAvailable();
		
		void onCameraClosed();
	}
}
