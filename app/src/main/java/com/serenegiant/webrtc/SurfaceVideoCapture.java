package com.serenegiant.webrtc;

import android.graphics.SurfaceTexture;
import android.support.annotation.NonNull;
import android.view.Surface;

import org.webrtc.Logging;
import org.webrtc.SurfaceTextureHelper;
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

	public static class Statistics {
		private static final String TAG = Statistics.class.getSimpleName();

		@NonNull
		private final SurfaceTextureHelper surfaceTextureHelper;
		@NonNull
		private final EventsHandler eventsHandler;
		private int frameCount;
		private int freezePeriodCount;
		
		public Statistics(@NonNull final SurfaceTextureHelper surfaceTextureHelper,
			@NonNull final EventsHandler eventsHandler) {

			this.surfaceTextureHelper = surfaceTextureHelper;
			this.eventsHandler = eventsHandler;
			frameCount = 0;
			freezePeriodCount = 0;
			surfaceTextureHelper.getHandler().postDelayed(cameraObserver, 2000L);
		}
		
		private void checkThread() {
			if (Thread.currentThread() != surfaceTextureHelper.getHandler().getLooper().getThread()) {
				throw new IllegalStateException("Wrong thread");
			}
		}
		
		public void addFrame() {
			checkThread();
			++frameCount;
		}
		
		public void release() {
			surfaceTextureHelper.getHandler().removeCallbacks(cameraObserver);
		}

		private final Runnable cameraObserver = new Runnable() {
			public void run() {
				final int cameraFps = Math.round((float) frameCount * 1000.0F / 2000.0F);
				Logging.d(TAG, "Camera fps: " + cameraFps + ".");
				if (frameCount == 0) {
					++freezePeriodCount;
					if (2000 * freezePeriodCount >= 4000) {
						Logging.e(TAG, "Camera freezed.");
						if (surfaceTextureHelper.isTextureInUse()) {
							eventsHandler.onFailure("Camera failure. Client must return video buffers.");
						} else {
							eventsHandler.onFailure("Camera failure.");
						}
						
						return;
					}
				} else {
					freezePeriodCount = 0;
				}
				
				frameCount = 0;
				surfaceTextureHelper.getHandler().postDelayed(this, 2000L);
			}
		};
	}
	
	public interface EventsHandler {
		public void onFailure(final String reason);
	}
}
