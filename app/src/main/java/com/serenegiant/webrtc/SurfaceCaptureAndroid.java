package com.serenegiant.webrtc;

import android.content.Context;
import android.graphics.SurfaceTexture;
import android.view.Surface;

import com.serenegiant.glutils.IRendererHolder;
import com.serenegiant.glutils.RendererHolder;

import org.webrtc.RendererCommon;
import org.webrtc.SurfaceTextureHelper;
import org.webrtc.ThreadUtils;
import org.webrtc.VideoFrame;

import javax.annotation.Nullable;

public class SurfaceCaptureAndroid
	implements SurfaceVideoCapture,
		SurfaceTextureHelper.OnTextureFrameAvailableListener {

	@Nullable
	private SurfaceTextureHelper surfaceTextureHelper;
	@Nullable
	private CapturerObserver capturerObserver;
	private long numCapturedFrames = 0L;
	private boolean isDisposed = false;
	private int width;
	private int height;
	private IRendererHolder mRendererHolder;
	private int mCaptureSurfaceId;

	public SurfaceCaptureAndroid() {
		mRendererHolder = new RendererHolder(width, height, null);
	}

	@Override
	public synchronized void initialize(final SurfaceTextureHelper surfaceTextureHelper,
		final Context applicationContext, final CapturerObserver capturerObserver) {

		checkNotDisposed();
		if (capturerObserver == null) {
			throw new RuntimeException("capturerObserver not set.");
		} else {
			this.capturerObserver = capturerObserver;
			if (surfaceTextureHelper == null) {
				throw new RuntimeException("surfaceTextureHelper not set.");
			} else {
				this.surfaceTextureHelper = surfaceTextureHelper;
			}
		}
	}
	
	@Override
	public synchronized void startCapture(final int width, final int height, final int ignoredFramerate) {
		checkNotDisposed();
		this.width = width;
		this.height = height;
		capturerObserver.onCapturerStarted(true);
		surfaceTextureHelper.startListening(this);
		resize(width, height);
		setSurface();
	}
	
	@Override
	public synchronized void stopCapture() throws InterruptedException {
		checkNotDisposed();
		ThreadUtils.invokeAtFrontUninterruptibly(this.surfaceTextureHelper.getHandler(), new Runnable() {
			public void run() {
				if (mRendererHolder != null) {
					mRendererHolder.removeSurface(mCaptureSurfaceId);
				}
				mCaptureSurfaceId = 0;
				SurfaceCaptureAndroid.this.surfaceTextureHelper.stopListening();
				SurfaceCaptureAndroid.this.capturerObserver.onCapturerStopped();
			}
		});
	}
	
	@Override
	public synchronized void changeCaptureFormat(int width, int height, int ignoredFramerate) {
		checkNotDisposed();
		this.width = width;
		this.height = height;
		resize(width, height);
		setSurface();
	}
	
	@Override
	public synchronized void dispose() {
		if (mRendererHolder != null) {
			mRendererHolder.release();
			mRendererHolder = null;
		}
		isDisposed = true;
	}
	
	@Override
	public boolean isScreencast() {
		return false;
	}

	@Override
	public void onTextureFrameAvailable(final int oesTextureId, final float[] transformMatrix, final long timestampNs) {
		++numCapturedFrames;
		final VideoFrame.Buffer buffer = this.surfaceTextureHelper.createTextureBuffer(this.width, this.height,
			RendererCommon.convertMatrixToAndroidGraphicsMatrix(transformMatrix));
		final VideoFrame frame = new VideoFrame(buffer, 0, timestampNs);
		this.capturerObserver.onFrameCaptured(frame);
		frame.release();
	}

	public long getNumCapturedFrames() {
		return this.numCapturedFrames;
	}

	@Nullable
	public synchronized Surface getInputSurface() {
		return mRendererHolder != null ? mRendererHolder.getSurface() : null;
	}

	@Nullable
	public synchronized SurfaceTexture getInputSurfaceTexture() {
		return mRendererHolder != null ? mRendererHolder.getSurfaceTexture() : null;
	}

	public synchronized void addSurface(final int id, final Object surface, final boolean isRecordable) {
		checkNotDisposed();
		final IRendererHolder rendererHolder = mRendererHolder;
		if (!isDisposed && rendererHolder != null) {
			rendererHolder.addSurface(id, surface, isRecordable);
		}
	}
	
	public synchronized void addSurface(final int id, final Object surface, final boolean isRecordable, final int maxFps) {
		checkNotDisposed();
		final IRendererHolder rendererHolder = mRendererHolder;
		if (!isDisposed && rendererHolder != null) {
			rendererHolder.addSurface(id, surface, isRecordable, maxFps);
		}
	}

	public synchronized void removeSurface(final int id) {
		final IRendererHolder rendererHolder = mRendererHolder;
		if (!isDisposed && rendererHolder != null) {
			rendererHolder.removeSurface(id);
		}
	}

	private void checkNotDisposed() {
		if (isDisposed) {
			throw new RuntimeException("capturer is disposed.");
		}
	}

	private void resize(final int width, final int height) {
		if (mRendererHolder != null) {
			mRendererHolder.resize(width, height);
		}
	}
	
	private void setSurface() {
		if ((mCaptureSurfaceId != 0) && (mRendererHolder != null)) {
			mRendererHolder.removeSurface(mCaptureSurfaceId);
		}
		final SurfaceTexture st = surfaceTextureHelper.getSurfaceTexture();
		st.setDefaultBufferSize(width, height);
		final Surface surface = new Surface(st);
		mCaptureSurfaceId = surface.hashCode();
		mRendererHolder.addSurface(mCaptureSurfaceId, surface, false);
	}
}
