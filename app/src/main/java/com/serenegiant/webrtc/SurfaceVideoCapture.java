package com.serenegiant.webrtc;

import android.graphics.SurfaceTexture;
import android.view.Surface;

import org.webrtc.VideoCapturer;

import javax.annotation.Nullable;

public interface SurfaceVideoCapture extends VideoCapturer {
	@Nullable
	public Surface getInputSurface();

	@Nullable
	public SurfaceTexture getInputSurfaceTexture();

	public void addSurface(final int id, final Object surface, final boolean isRecordable);
	
	public void addSurface(final int id, final Object surface, final boolean isRecordable, final int maxFps);

	public void removeSurface(final int id);
}
