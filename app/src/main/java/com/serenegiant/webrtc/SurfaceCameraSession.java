package com.serenegiant.webrtc;

public interface SurfaceCameraSession {
	void stop();
	int getFace();
	int getRotation();
	
	public interface Events {
		void onCameraOpening();
		
		void onCameraError(SurfaceCameraSession session, String error);
		
		void onCameraDisconnected(SurfaceCameraSession session);
		
		void onCameraClosed(SurfaceCameraSession session);
	}
	
	public interface CreateSessionCallback {
		void onDone(SurfaceCameraSession session);
		
		void onFailure(SurfaceCameraSession.FailureType failureType, String error);
	}
	
	public static enum FailureType {
		ERROR,
		DISCONNECTED;
		
		private FailureType() {
		}
	}
}
