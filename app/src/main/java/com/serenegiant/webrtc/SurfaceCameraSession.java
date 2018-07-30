package com.serenegiant.webrtc;

public interface SurfaceCameraSession {
	public void stop();
	public int getFace();
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
