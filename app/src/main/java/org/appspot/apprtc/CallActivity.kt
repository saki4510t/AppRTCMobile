package org.appspot.apprtc
/*
*  Copyright 2015 The WebRTC Project Authors. All rights reserved.
*
*  Use of this source code is governed by a BSD-style license
*  that can be found in the LICENSE file in the root of the source
*  tree. An additional intellectual property rights grant can be found
*  in the file PATENTS.  All contributing project authors may
*  be found in the AUTHORS file in the root of the source tree.
*/

import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.os.Handler
import android.util.DisplayMetrics
import android.util.Log
import android.view.View
import android.view.Window
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.annotation.UiThread
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.FragmentTransaction
import com.serenegiant.apprtcmobile.R
import com.serenegiant.webrtc.Camera1SurfaceCaptureAndroid
import org.appspot.apprtc.AppRTCAudioManager.AudioDevice
import org.appspot.apprtc.AppRTCClient.RoomConnectionParameters
import org.appspot.apprtc.AppRTCClient.SignalingEvents
import org.appspot.apprtc.AppRTCClient.SignalingParameters
import org.appspot.apprtc.CallFragment.OnCallEvents
import org.appspot.apprtc.PeerConnectionClient.DataChannelParameters
import org.appspot.apprtc.PeerConnectionClient.PeerConnectionEvents
import org.appspot.apprtc.PeerConnectionClient.PeerConnectionParameters
import org.webrtc.Camera1Enumerator
import org.webrtc.Camera2Enumerator
import org.webrtc.CameraEnumerator
import org.webrtc.EglBase
import org.webrtc.FileVideoCapturer
import org.webrtc.IceCandidate
import org.webrtc.Logging
import org.webrtc.PeerConnectionFactory
import org.webrtc.RendererCommon.ScalingType
import org.webrtc.ScreenCapturerAndroid
import org.webrtc.SessionDescription
import org.webrtc.StatsReport
import org.webrtc.SurfaceViewRenderer
import org.webrtc.VideoCapturer
import org.webrtc.VideoFileRenderer
import org.webrtc.VideoFrame
import org.webrtc.VideoSink
import java.io.IOException

/**
 * Activity for peer connection call setup, call waiting
 * and call view.
 */
class CallActivity : BaseActivity(), SignalingEvents, PeerConnectionEvents, OnCallEvents {
	private class ProxyVideoSink : VideoSink {
		private var target: VideoSink? = null

		@Synchronized
		override fun onFrame(frame: VideoFrame) {
			if (target == null) {
				Logging.d(TAG, "Dropping frame in proxy because target is null.")
				return
			}

			target!!.onFrame(frame)
		}

		@Synchronized
		fun setTarget(target: VideoSink?) {
			this.target = target
		}
	}

	private val remoteProxyRenderer = ProxyVideoSink()
	private val localProxyVideoSink = ProxyVideoSink()
	private lateinit var pipRenderer: SurfaceViewRenderer
	private lateinit var fullscreenRenderer: SurfaceViewRenderer
	private var videoFileRenderer: VideoFileRenderer? = null

	private var peerConnectionClient: PeerConnectionClient? = null
	private var appRtcClient: AppRTCClient? = null
	private var signalingParameters: SignalingParameters? = null
	private var audioManager: AppRTCAudioManager? = null
	private val remoteSinks: MutableList<VideoSink> = ArrayList()
	private var logToast: Toast? = null
	private var commandLineRun = false
	private var activityRunning = false
	private var roomConnectionParameters: RoomConnectionParameters? = null
	private var peerConnectionParameters: PeerConnectionParameters? = null
	private var iceConnected = false
	private var isError = false
	private var callControlFragmentVisible = true
	private var callStartedTimeMs: Long = 0
	private var micEnabled = true
	private var surfaceCameraCaptureEnabled = false
	private var screencaptureEnabled = false

	// True if local view is in the fullscreen renderer.
	private var isSwappedFeeds = false

	// Controls
	private var callFragment: CallFragment? = null
	private var hudFragment: HudFragment? = null
	private var cpuMonitor: CpuMonitor? = null

	// TODO(bugs.webrtc.org/8580): LayoutParams.FLAG_TURN_SCREEN_ON and
	// LayoutParams.FLAG_SHOW_WHEN_LOCKED are deprecated.
	public override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		if (DEBUG) Log.v(TAG, "onCreate:")
		Thread.setDefaultUncaughtExceptionHandler(UnhandledExceptionHandler(this))

		enableEdgeToEdge()
		// Set window styles for fullscreen-window size. Needs to be done before
		// adding content.
		requestWindowFeature(Window.FEATURE_NO_TITLE)
		window.addFlags(
			(WindowManager.LayoutParams.FLAG_FULLSCREEN or WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
				or WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
		)
		window.decorView.systemUiVisibility = systemUiVisibility
		setContentView(R.layout.activity_call)
		findViewById<View>(R.id.frame)?.let {
			optimizeEdgeToEdge(it)
		}

		iceConnected = false
		signalingParameters = null


		// Create UI controls.
		pipRenderer = findViewById(R.id.pip_video_view)
		fullscreenRenderer = findViewById(R.id.fullscreen_video_view)
		callFragment = CallFragment()
		hudFragment = HudFragment()


		// Show/hide call control fragment on view click.
		val listener = View.OnClickListener { toggleCallControlFragmentVisibility() }


		// Swap feeds on pip view click.
		pipRenderer.setOnClickListener(View.OnClickListener { setSwappedFeeds(!isSwappedFeeds) })

		fullscreenRenderer.setOnClickListener(listener)
		remoteSinks.add(remoteProxyRenderer)

		val intent = intent
		val eglBase = EglBase.create()

		// Create video renderers.
		pipRenderer.init(eglBase.eglBaseContext, null)
		pipRenderer.setScalingType(ScalingType.SCALE_ASPECT_FIT)
		val saveRemoteVideoToFile = intent.getStringExtra(EXTRA_SAVE_REMOTE_VIDEO_TO_FILE)

		// When saveRemoteVideoToFile is set we save the video from the remote to a file.
		if (saveRemoteVideoToFile != null) {
			val videoOutWidth = intent.getIntExtra(EXTRA_SAVE_REMOTE_VIDEO_TO_FILE_WIDTH, 0)
			val videoOutHeight = intent.getIntExtra(EXTRA_SAVE_REMOTE_VIDEO_TO_FILE_HEIGHT, 0)
			try {
				videoFileRenderer = VideoFileRenderer(
					saveRemoteVideoToFile, videoOutWidth, videoOutHeight, eglBase.eglBaseContext
				)
				remoteSinks.add(videoFileRenderer!!)
			} catch (e: IOException) {
				throw RuntimeException(
					"Failed to open video file for output: $saveRemoteVideoToFile", e
				)
			}
		}
		fullscreenRenderer.init(eglBase.eglBaseContext, null)
		fullscreenRenderer.setScalingType(ScalingType.SCALE_ASPECT_FILL)

		pipRenderer.setZOrderMediaOverlay(true)
		pipRenderer.setEnableHardwareScaler(true /* enabled */)
		fullscreenRenderer.setEnableHardwareScaler(false /* enabled */)
		// Start with local feed in fullscreen and swap it to the pip when the call is connected.
		setSwappedFeeds(true /* isSwappedFeeds */)


		// Check for mandatory permissions.
		for (permission in MANDATORY_PERMISSIONS) {
			if (checkCallingOrSelfPermission(permission) != PackageManager.PERMISSION_GRANTED) {
				logAndToast("Permission $permission is not granted")
				setResult(RESULT_CANCELED)
				finish()
				return
			}
		}

		val roomUri = intent.data
		if (roomUri == null) {
			logAndToast(getString(R.string.missing_url))
			Log.e(TAG, "Didn't get any URL in intent!")
			setResult(RESULT_CANCELED)
			finish()
			return
		}


		// Get Intent parameters.
		val roomId = intent.getStringExtra(EXTRA_ROOMID)
		Log.d(TAG, "Room ID: $roomId")
		if (roomId == null || roomId.length == 0) {
			logAndToast(getString(R.string.missing_url))
			Log.e(TAG, "Incorrect room ID in intent!")
			setResult(RESULT_CANCELED)
			finish()
			return
		}

		val loopback = intent.getBooleanExtra(EXTRA_LOOPBACK, false)
		val tracing = intent.getBooleanExtra(EXTRA_TRACING, false)

		var videoWidth = intent.getIntExtra(EXTRA_VIDEO_WIDTH, 0)
		var videoHeight = intent.getIntExtra(EXTRA_VIDEO_HEIGHT, 0)

		surfaceCameraCaptureEnabled = intent.getBooleanExtra(EXTRA_SURFACE_CAMERA_CAPTURE, false)
		screencaptureEnabled = intent.getBooleanExtra(EXTRA_SCREENCAPTURE, false)
		// If capturing format is not specified for screencapture, use screen resolution.
		if (screencaptureEnabled && videoWidth == 0 && videoHeight == 0) {
			val displayMetrics = displayMetrics
			videoWidth = displayMetrics.widthPixels
			videoHeight = displayMetrics.heightPixels
		}
		var dataChannelParameters: DataChannelParameters? = null
		if (intent.getBooleanExtra(EXTRA_DATA_CHANNEL_ENABLED, false)) {
			dataChannelParameters = DataChannelParameters(
				intent.getBooleanExtra(EXTRA_ORDERED, true),
				intent.getIntExtra(EXTRA_MAX_RETRANSMITS_MS, -1),
				intent.getIntExtra(EXTRA_MAX_RETRANSMITS, -1),
				intent.getStringExtra(EXTRA_PROTOCOL),
				intent.getBooleanExtra(EXTRA_NEGOTIATED, false),
				intent.getIntExtra(EXTRA_ID, -1)
			)
		}
		peerConnectionParameters =
			PeerConnectionParameters(
				intent.getBooleanExtra(EXTRA_VIDEO_CALL, true), loopback,
				tracing, videoWidth, videoHeight, intent.getIntExtra(EXTRA_VIDEO_FPS, 0),
				intent.getIntExtra(EXTRA_VIDEO_BITRATE, 0), intent.getStringExtra(EXTRA_VIDEOCODEC),
				intent.getBooleanExtra(EXTRA_HWCODEC_ENABLED, true),
				intent.getBooleanExtra(EXTRA_FLEXFEC_ENABLED, false),
				intent.getIntExtra(EXTRA_AUDIO_SOURCE, 7),
				intent.getIntExtra(EXTRA_AUDIO_FORMAT, 2),
				intent.getIntExtra(EXTRA_AUDIO_BITRATE, 0), intent.getStringExtra(EXTRA_AUDIOCODEC),
				intent.getBooleanExtra(EXTRA_NOAUDIOPROCESSING_ENABLED, false),
				intent.getBooleanExtra(EXTRA_AECDUMP_ENABLED, false),
				intent.getBooleanExtra(EXTRA_SAVE_INPUT_AUDIO_TO_FILE_ENABLED, false),
				intent.getBooleanExtra(EXTRA_OPENSLES_ENABLED, false),
				intent.getBooleanExtra(EXTRA_DISABLE_BUILT_IN_AEC, false),
				intent.getBooleanExtra(EXTRA_DISABLE_BUILT_IN_AGC, false),
				intent.getBooleanExtra(EXTRA_DISABLE_BUILT_IN_NS, false),
				intent.getBooleanExtra(EXTRA_DISABLE_WEBRTC_AGC_AND_HPF, false),
				intent.getBooleanExtra(EXTRA_ENABLE_RTCEVENTLOG, false),
				dataChannelParameters
			)
		commandLineRun = intent.getBooleanExtra(EXTRA_CMDLINE, false)
		val runTimeMs = intent.getIntExtra(EXTRA_RUNTIME, 0)

		Log.d(TAG, "VIDEO_FILE: '" + intent.getStringExtra(EXTRA_VIDEO_FILE_AS_CAMERA) + "'")


		// Create connection client. Use org.appspot.apprtc.DirectRTCClient if room name is an IP otherwise use the
		// standard org.appspot.apprtc.WebSocketRTCClient.
		if (loopback || !DirectRTCClient.IP_PATTERN.matcher(roomId).matches()) {
			appRtcClient = WebSocketRTCClient(this)
		} else {
			Log.i(TAG, "Using org.appspot.apprtc.DirectRTCClient because room name looks like an IP.$roomId")
			appRtcClient = DirectRTCClient(this)
		}
		// Create connection parameters.
		val urlParameters = intent.getStringExtra(EXTRA_URLPARAMETERS)
		roomConnectionParameters =
			RoomConnectionParameters(roomUri.toString(), roomId, loopback, urlParameters)

		// Create CPU monitor
		if (CpuMonitor.isSupported) {
			cpuMonitor = CpuMonitor(this)
			hudFragment!!.setCpuMonitor(cpuMonitor)
		}

		// Send intent arguments to fragments.
		callFragment!!.arguments = intent.extras
		hudFragment!!.arguments = intent.extras
		// Activate call and HUD fragments and start the call.
		val ft = supportFragmentManager.beginTransaction()
		ft.add(R.id.call_fragment_container, callFragment!!)
		ft.add(R.id.hud_fragment_container, hudFragment!!)
		ft.commit()

		// For command line execution run connection for <runTimeMs> and exit.
		if (commandLineRun && runTimeMs > 0) {
			(Handler()).postDelayed({ disconnect() }, runTimeMs.toLong())
		}

		// Create peer connection client.
		peerConnectionClient = PeerConnectionClient(
			applicationContext, eglBase, peerConnectionParameters, this@CallActivity
		)
		val options = PeerConnectionFactory.Options()
		if (loopback) {
			options.networkIgnoreMask = 0
		}
		peerConnectionClient!!.createPeerConnectionFactory(options)

		if (screencaptureEnabled) {
			startScreenCapture()
		} else {
			startCall()
		}
	}

	private val displayMetrics: DisplayMetrics
		get() {
			val displayMetrics = DisplayMetrics()
			val windowManager =
				application.getSystemService(WINDOW_SERVICE) as WindowManager
			windowManager.defaultDisplay.getRealMetrics(displayMetrics)
			return displayMetrics
		}

	private fun startScreenCapture() {
		if (DEBUG) Log.v(TAG, "startScreenCapture:")
		val mediaProjectionManager =
			application.getSystemService(
				MEDIA_PROJECTION_SERVICE
			) as MediaProjectionManager
		startActivityForResult(
			mediaProjectionManager.createScreenCaptureIntent(), CAPTURE_PERMISSION_REQUEST_CODE
		)
	}

	public override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
		super.onActivityResult(requestCode, resultCode, data)
		if (DEBUG) Log.v(TAG, "onActivityResult:")
		if (requestCode != CAPTURE_PERMISSION_REQUEST_CODE) return
		mediaProjectionPermissionResultCode = resultCode
		mediaProjectionPermissionResultData = data
		startCall()
	}

	private fun useCamera2(): Boolean {
		return Camera2Enumerator.isSupported(this) && intent.getBooleanExtra(EXTRA_CAMERA2, true)
	}

	private fun captureToTexture(): Boolean {
		return intent.getBooleanExtra(EXTRA_CAPTURETOTEXTURE_ENABLED, false)
	}

	private fun createCameraCapturer(enumerator: CameraEnumerator): VideoCapturer? {
		if (DEBUG) Log.v(TAG, "createCameraCapturer:")
		val deviceNames = enumerator.deviceNames
		// First, try to find front facing camera
		Logging.d(TAG, "Looking for front facing cameras.")
		for (deviceName in deviceNames) {
			if (enumerator.isFrontFacing(deviceName)) {
				Logging.d(TAG, "Creating front facing camera capturer.")
				val videoCapturer: VideoCapturer? = enumerator.createCapturer(deviceName, null)

				if (videoCapturer != null) {
					return videoCapturer
				}
			}
		}


		// Front facing camera not found, try something else
		Logging.d(TAG, "Looking for other cameras.")
		for (deviceName in deviceNames) {
			if (!enumerator.isFrontFacing(deviceName)) {
				Logging.d(TAG, "Creating other camera capturer.")
				val videoCapturer: VideoCapturer? = enumerator.createCapturer(deviceName, null)

				if (videoCapturer != null) {
					return videoCapturer
				}
			}
		}

		return null
	}

	private fun createScreenCapturer(): VideoCapturer? {
		if (DEBUG) Log.v(TAG, "createScreenCapturer:")
		if (mediaProjectionPermissionResultCode != RESULT_OK) {
			reportError("User didn't give permission to capture the screen.")
			return null
		}
		return ScreenCapturerAndroid(
			mediaProjectionPermissionResultData, object : MediaProjection.Callback() {
				override fun onStop() {
					reportError("User revoked permission to capture the screen.")
				}
			})
	}

	private fun createSurfaceCameraCapture(enumerator: CameraEnumerator): VideoCapturer? {
		if (DEBUG) Log.v(TAG, "createSurfaceCameraCapture:")
		val deviceNames = enumerator.deviceNames
		// First, try to find front facing camera
		Logging.d(TAG, "Looking for front facing cameras.")
		for (deviceName in deviceNames) {
			if (enumerator.isFrontFacing(deviceName)) {
				Logging.d(TAG, "Creating front facing camera capturer.")
				val videoCapturer: VideoCapturer = Camera1SurfaceCaptureAndroid(deviceName)

				return videoCapturer
			}
		}


		// Front facing camera not found, try something else
		Logging.d(TAG, "Looking for other cameras.")
		for (deviceName in deviceNames) {
			if (!enumerator.isFrontFacing(deviceName)) {
				Logging.d(TAG, "Creating other camera capturer.")
				val videoCapturer: VideoCapturer = Camera1SurfaceCaptureAndroid(deviceName)

				return videoCapturer
			}
		}

		return null
	}

	// Activity interfaces
	public override fun onStop() {
		super.onStop()
		if (DEBUG) Log.v(TAG, "onStop:")
		activityRunning = false
		// Don't stop the video when using screen capture to allow user to show other apps to the remote
		// end.
		if (peerConnectionClient != null && !screencaptureEnabled) {
			peerConnectionClient!!.stopVideoSource()
		}
		if (cpuMonitor != null) {
			cpuMonitor!!.pause()
		}
	}

	public override fun onStart() {
		super.onStart()
		if (DEBUG) Log.v(TAG, "onStart:")
		activityRunning = true
		// Video is not paused for screen capture. See onPause.
		if (peerConnectionClient != null && !screencaptureEnabled) {
			peerConnectionClient!!.startVideoSource()
		}
		if (cpuMonitor != null) {
			cpuMonitor!!.resume()
		}
	}

	override fun onDestroy() {
		if (DEBUG) Log.v(TAG, "onDestroy:")
		Thread.setDefaultUncaughtExceptionHandler(null)
		disconnect()
		if (logToast != null) {
			logToast!!.cancel()
		}
		activityRunning = false
		super.onDestroy()
	}

	// org.appspot.apprtc.CallFragment.OnCallEvents interface implementation.
	override fun onCallHangUp() {
		if (DEBUG) Log.v(TAG, "onCallHangUp:")
		disconnect()
	}

	override fun onCameraSwitch() {
		if (DEBUG) Log.v(TAG, "onCameraSwitch:")
		peerConnectionClient?.switchCamera()
	}

	override fun onVideoScalingSwitch(scalingType: ScalingType) {
		fullscreenRenderer.setScalingType(scalingType)
	}

	override fun onCaptureFormatChange(width: Int, height: Int, framerate: Int) {
		if (DEBUG) Log.v(TAG, "onCaptureFormatChange:")
		peerConnectionClient?.changeCaptureFormat(width, height, framerate)
	}

	override fun onToggleMic(): Boolean {
		if (DEBUG) Log.v(TAG, "onToggleMic:")
		if (peerConnectionClient != null) {
			micEnabled = !micEnabled
			peerConnectionClient!!.setAudioEnabled(micEnabled)
		}
		return micEnabled
	}

	// Helper functions.
	private fun toggleCallControlFragmentVisibility() {
		if (DEBUG) Log.v(TAG, "toggleCallControlFragmentVisibility:")
		if (!iceConnected || !callFragment!!.isAdded) {
			return
		}
		// Show/hide call control fragment
		callControlFragmentVisible = !callControlFragmentVisible
		val ft = supportFragmentManager.beginTransaction()
		if (callControlFragmentVisible) {
			ft.show(callFragment!!)
			ft.show(hudFragment!!)
		} else {
			ft.hide(callFragment!!)
			ft.hide(hudFragment!!)
		}
		ft.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_FADE)
		ft.commit()
	}

	private fun startCall() {
		if (DEBUG) Log.v(TAG, "startCall:")
		if (appRtcClient == null) {
			Log.e(TAG, "AppRTC client is not allocated for a call.")
			return
		}
		callStartedTimeMs = System.currentTimeMillis()

		// Start room connection.
		logAndToast(getString(R.string.connecting_to, roomConnectionParameters!!.roomUrl))
		appRtcClient!!.connectToRoom(roomConnectionParameters)

		// Create and audio manager that will take care of audio routing,
		// audio modes, audio device enumeration etc.
		audioManager = AppRTCAudioManager.create(applicationContext)
		// Store existing audio settings and change audio mode to
		// MODE_IN_COMMUNICATION for best possible VoIP performance.
		Log.d(TAG, "Starting the audio manager...")
		audioManager?.start { selected, availables ->
			// This method will be called each time the number of available audio
			// devices has changed.
			onAudioManagerDevicesChanged(selected, availables)
		}
	}

	// Should be called from UI thread
	@UiThread
	private fun callConnected() {
		val delta = System.currentTimeMillis() - callStartedTimeMs
		Log.i(TAG, "Call connected: delay=" + delta + "ms")
		if (peerConnectionClient == null || isError) {
			Log.w(TAG, "Call is connected in closed or error state")
			return
		}
		// Enable statistics callback.
		peerConnectionClient!!.enableStatsEvents(true, STAT_CALLBACK_PERIOD)
		setSwappedFeeds(false /* isSwappedFeeds */)
	}

	// This method is called when the audio manager reports audio device change,
	// e.g. from wired headset to speakerphone.
	private fun onAudioManagerDevicesChanged(
		device: AudioDevice, availableDevices: Set<AudioDevice>
	) {
		Log.d(TAG, ("onAudioManagerDevicesChanged: $availableDevices, selected: $device"))
		// TODO(henrika): add callback handler.
	}

	// Disconnect from remote resources, dispose of local resources, and exit.
	private fun disconnect() {
		if (DEBUG) Log.v(TAG, "disconnect:")
		activityRunning = false
		remoteProxyRenderer.setTarget(null)
		localProxyVideoSink.setTarget(null)
		if (appRtcClient != null) {
			appRtcClient!!.disconnectFromRoom()
			appRtcClient = null
		}
		pipRenderer.release()
		fullscreenRenderer.release()
		videoFileRenderer?.release()
		videoFileRenderer = null
		if (peerConnectionClient != null) {
			peerConnectionClient!!.close()
			peerConnectionClient = null
		}
		if (audioManager != null) {
			audioManager!!.stop()
			audioManager = null
		}
		if (iceConnected && !isError) {
			setResult(RESULT_OK)
		} else {
			setResult(RESULT_CANCELED)
		}
		finish()
	}

	private fun disconnectWithErrorMessage(errorMessage: String) {
		if (DEBUG) Log.v(TAG, "disconnectWithErrorMessage:$errorMessage")
		if (commandLineRun || !activityRunning) {
			Log.e(TAG, "Critical error: $errorMessage")
			disconnect()
		} else {
			AlertDialog.Builder(this)
				.setTitle(getText(R.string.channel_error_title))
				.setMessage(errorMessage)
				.setCancelable(false)
				.setNeutralButton(
					R.string.ok
				) { dialog, id ->
					dialog.cancel()
					disconnect()
				}
				.create()
				.show()
		}
	}

	// Log |msg| and Toast about it.
	private fun logAndToast(msg: String) {
		Log.d(TAG, msg)
		if (logToast != null) {
			logToast!!.cancel()
		}
		logToast = Toast.makeText(this, msg, Toast.LENGTH_SHORT)
		logToast?.show()
	}

	private fun reportError(description: String) {
		runOnUiThread {
			if (!isError) {
				isError = true
				disconnectWithErrorMessage(description)
			}
		}
	}

	private fun createVideoCapturer(): VideoCapturer? {
		if (DEBUG) Log.v(TAG, "createVideoCapturer:")
		val videoCapturer: VideoCapturer?
		val videoFileAsCamera = intent.getStringExtra(EXTRA_VIDEO_FILE_AS_CAMERA)
		if (videoFileAsCamera != null) {
			try {
				videoCapturer = FileVideoCapturer(videoFileAsCamera)
			} catch (e: IOException) {
				reportError("Failed to open video file for emulated camera")
				return null
			}
		} else if (screencaptureEnabled) {
			return createScreenCapturer()
		} else if (surfaceCameraCaptureEnabled) {
			return createSurfaceCameraCapture(Camera1Enumerator(true))
		} else if (useCamera2()) {
			if (!captureToTexture()) {
				reportError(getString(R.string.camera2_texture_only_error))
				return null
			}

			Logging.d(TAG, "Creating capturer using camera2 API.")
			videoCapturer = createCameraCapturer(Camera2Enumerator(this))
		} else {
			Logging.d(TAG, "Creating capturer using camera1 API.")
			videoCapturer = createCameraCapturer(Camera1Enumerator(captureToTexture()))
		}
		if (videoCapturer == null) {
			reportError("Failed to open camera")
			return null
		}
		return videoCapturer
	}

	private fun setSwappedFeeds(isSwappedFeeds: Boolean) {
		Logging.d(TAG, "setSwappedFeeds: $isSwappedFeeds")
		this.isSwappedFeeds = isSwappedFeeds
		localProxyVideoSink.setTarget(if (isSwappedFeeds) fullscreenRenderer else pipRenderer)
		remoteProxyRenderer.setTarget(if (isSwappedFeeds) pipRenderer else fullscreenRenderer)
		fullscreenRenderer.setMirror(isSwappedFeeds)
		pipRenderer.setMirror(!isSwappedFeeds)
	}

	// -----Implementation of org.appspot.apprtc.AppRTCClient.AppRTCSignalingEvents ---------------
	// All callbacks are invoked from websocket signaling looper thread and
	// are routed to UI thread.
	private fun onConnectedToRoomInternal(params: SignalingParameters) {
		if (DEBUG) Log.v(TAG, "onConnectedToRoomInternal:")
		val delta = System.currentTimeMillis() - callStartedTimeMs
		signalingParameters = params
		logAndToast("Creating peer connection, delay=" + delta + "ms")
		var videoCapturer: VideoCapturer? = null
		if (peerConnectionParameters!!.videoCallEnabled) {
			videoCapturer = createVideoCapturer()
		}
		peerConnectionClient!!.createPeerConnection(
			localProxyVideoSink, remoteSinks, videoCapturer, signalingParameters
		)

		if (signalingParameters!!.initiator) {
			logAndToast("Creating OFFER...")
			// Create offer. Offer SDP will be sent to answering client in
			// PeerConnectionEvents.onLocalDescription event.
			peerConnectionClient!!.createOffer()
		} else {
			if (params.offerSdp != null) {
				peerConnectionClient!!.setRemoteDescription(params.offerSdp)
				logAndToast("Creating ANSWER...")
				// Create answer. Answer SDP will be sent to offering client in
				// PeerConnectionEvents.onLocalDescription event.
				peerConnectionClient!!.createAnswer()
			}
			if (params.iceCandidates != null) {
				// Add remote ICE candidates from room.
				for (iceCandidate in params.iceCandidates) {
					peerConnectionClient!!.addRemoteIceCandidate(iceCandidate)
				}
			}
		}
	}

	override fun onConnectedToRoom(params: SignalingParameters) {
		if (DEBUG) Log.v(TAG, "onConnectedToRoom:")
		runOnUiThread { onConnectedToRoomInternal(params) }
	}

	override fun onRemoteDescription(sdp: SessionDescription) {
		if (DEBUG) Log.v(TAG, "onRemoteDescription:")
		val delta = System.currentTimeMillis() - callStartedTimeMs
		runOnUiThread(Runnable {
			if (peerConnectionClient == null) {
				Log.e(TAG, "Received remote SDP for non-initilized peer connection.")
				return@Runnable
			}
			logAndToast("Received remote " + sdp.type + ", delay=" + delta + "ms")
			peerConnectionClient!!.setRemoteDescription(sdp)
			if (!signalingParameters!!.initiator) {
				logAndToast("Creating ANSWER...")
				// Create answer. Answer SDP will be sent to offering client in
				// PeerConnectionEvents.onLocalDescription event.
				peerConnectionClient!!.createAnswer()
			}
		})
	}

	override fun onRemoteIceCandidate(candidate: IceCandidate) {
		if (DEBUG) Log.v(TAG, "onRemoteIceCandidate:")
		runOnUiThread(Runnable {
			if (peerConnectionClient == null) {
				Log.e(
					TAG,
					"Received ICE candidate for a non-initialized peer connection."
				)
				return@Runnable
			}
			peerConnectionClient!!.addRemoteIceCandidate(candidate)
		})
	}

	override fun onRemoteIceCandidatesRemoved(candidates: Array<IceCandidate>) {
		if (DEBUG) Log.v(TAG, "onRemoteIceCandidatesRemoved:")
		runOnUiThread(Runnable {
			if (peerConnectionClient == null) {
				Log.e(TAG, "Received ICE candidate removals for a non-initialized peer connection.")
				return@Runnable
			}
			peerConnectionClient!!.removeRemoteIceCandidates(candidates)
		})
	}

	override fun onChannelClose() {
		if (DEBUG) Log.v(TAG, "onChannelClose:")
		runOnUiThread {
			logAndToast("Remote end hung up; dropping PeerConnection")
			disconnect()
		}
	}

	override fun onChannelError(description: String) {
		if (DEBUG) Log.v(TAG, "onChannelError:$description")
		reportError(description)
	}

	// -----Implementation of org.appspot.apprtc.PeerConnectionClient.PeerConnectionEvents.---------
	// Send local peer connection SDP and ICE candidates to remote party.
	// All callbacks are invoked from peer connection client looper thread and
	// are routed to UI thread.
	override fun onLocalDescription(sdp: SessionDescription) {
		if (DEBUG) Log.v(TAG, "onLocalDescription:")
		val delta = System.currentTimeMillis() - callStartedTimeMs
		runOnUiThread {
			if (appRtcClient != null) {
				logAndToast("Sending " + sdp.type + ", delay=" + delta + "ms")
				if (signalingParameters!!.initiator) {
					appRtcClient!!.sendOfferSdp(sdp)
				} else {
					appRtcClient!!.sendAnswerSdp(sdp)
				}
			}
			if (peerConnectionParameters!!.videoMaxBitrate > 0) {
				Log.d(TAG, "Set video maximum bitrate: " + peerConnectionParameters!!.videoMaxBitrate)
				peerConnectionClient!!.setVideoMaxBitrate(peerConnectionParameters!!.videoMaxBitrate)
			}
		}
	}

	override fun onIceCandidate(candidate: IceCandidate) {
		if (DEBUG) Log.v(TAG, "onIceCandidate:")
		runOnUiThread {
			if (appRtcClient != null) {
				appRtcClient!!.sendLocalIceCandidate(candidate)
			}
		}
	}

	override fun onIceCandidatesRemoved(candidates: Array<IceCandidate>) {
		if (DEBUG) Log.v(TAG, "onIceCandidatesRemoved:")
		runOnUiThread {
			if (appRtcClient != null) {
				appRtcClient!!.sendLocalIceCandidateRemovals(candidates)
			}
		}
	}

	override fun onIceConnected() {
		if (DEBUG) Log.v(TAG, "onIceConnected:")
		val delta = System.currentTimeMillis() - callStartedTimeMs
		runOnUiThread {
			logAndToast("ICE connected, delay=" + delta + "ms")
			iceConnected = true
			callConnected()
		}
	}

	override fun onIceDisconnected() {
		if (DEBUG) Log.v(TAG, "onIceDisconnected:")
		runOnUiThread {
			logAndToast("ICE disconnected")
			iceConnected = false
			disconnect()
		}
	}

	override fun onPeerConnectionClosed() {
		if (DEBUG) Log.v(TAG, "onPeerConnectionClosed:")
	}

	override fun onPeerConnectionStatsReady(reports: Array<StatsReport>) {
		if (DEBUG) Log.v(TAG, "onPeerConnectionStatsReady:")
		runOnUiThread {
			if (!isError && iceConnected) {
				hudFragment!!.updateEncoderStatistics(reports)
			}
		}
	}

	override fun onPeerConnectionError(description: String) {
		if (DEBUG) Log.v(TAG, "onPeerConnectionError:$description")
		reportError(description)
	}

	private fun optimizeEdgeToEdge(rootView: View) {
		if (DEBUG) Log.v(TAG, "optimizeEdgeToEdge:")
		ViewCompat.setOnApplyWindowInsetsListener(
			rootView
		) { v, windowInsets ->
			val insets = windowInsets.getInsets( // システムバー＝ステータスバー、ナビゲーションバー
				WindowInsetsCompat.Type.systemBars() // ディスプレイカットアウト
					or WindowInsetsCompat.Type.displayCutout()
			)
			rootView.setPadding(insets.left, insets.top, insets.right, insets.bottom)
			// このActivityでWindowInsetsを消費する(子Viewには伝播しない)
			WindowInsetsCompat.CONSUMED
		}
	}

	companion object {
		private const val DEBUG = true	// set false on production
		private val TAG = CallActivity::class.java.simpleName

		const val EXTRA_ROOMID: String = "org.appspot.apprtc.ROOMID"
		const val EXTRA_URLPARAMETERS: String = "org.appspot.apprtc.URLPARAMETERS"
		const val EXTRA_LOOPBACK: String = "org.appspot.apprtc.LOOPBACK"
		const val EXTRA_VIDEO_CALL: String = "org.appspot.apprtc.VIDEO_CALL"
		const val EXTRA_SURFACE_CAMERA_CAPTURE: String = "org.appspot.apprtc.SURFACE_CAMERA_CAPTURE"
		const val EXTRA_SCREENCAPTURE: String = "org.appspot.apprtc.SCREENCAPTURE"
		const val EXTRA_CAMERA2: String = "org.appspot.apprtc.CAMERA2"
		const val EXTRA_VIDEO_WIDTH: String = "org.appspot.apprtc.VIDEO_WIDTH"
		const val EXTRA_VIDEO_HEIGHT: String = "org.appspot.apprtc.VIDEO_HEIGHT"
		const val EXTRA_VIDEO_FPS: String = "org.appspot.apprtc.VIDEO_FPS"
		const val EXTRA_VIDEO_CAPTUREQUALITYSLIDER_ENABLED: String =
			"org.appsopt.apprtc.VIDEO_CAPTUREQUALITYSLIDER"
		const val EXTRA_VIDEO_BITRATE: String = "org.appspot.apprtc.VIDEO_BITRATE"
		const val EXTRA_VIDEOCODEC: String = "org.appspot.apprtc.VIDEOCODEC"
		const val EXTRA_HWCODEC_ENABLED: String = "org.appspot.apprtc.HWCODEC"
		const val EXTRA_CAPTURETOTEXTURE_ENABLED: String = "org.appspot.apprtc.CAPTURETOTEXTURE"
		const val EXTRA_FLEXFEC_ENABLED: String = "org.appspot.apprtc.FLEXFEC"
		const val EXTRA_AUDIO_SOURCE: String = "org.appspot.apprtc.AUDIO_SOURCE"
		const val EXTRA_AUDIO_FORMAT: String = "org.appspot.apprtc.AUDIO_FORMAT"
		const val EXTRA_AUDIO_BITRATE: String = "org.appspot.apprtc.AUDIO_BITRATE"
		const val EXTRA_AUDIOCODEC: String = "org.appspot.apprtc.AUDIOCODEC"
		const val EXTRA_NOAUDIOPROCESSING_ENABLED: String = "org.appspot.apprtc.NOAUDIOPROCESSING"
		const val EXTRA_AECDUMP_ENABLED: String = "org.appspot.apprtc.AECDUMP"
		const val EXTRA_SAVE_INPUT_AUDIO_TO_FILE_ENABLED: String =
			"org.appspot.apprtc.SAVE_INPUT_AUDIO_TO_FILE"
		const val EXTRA_OPENSLES_ENABLED: String = "org.appspot.apprtc.OPENSLES"
		const val EXTRA_DISABLE_BUILT_IN_AEC: String = "org.appspot.apprtc.DISABLE_BUILT_IN_AEC"
		const val EXTRA_DISABLE_BUILT_IN_AGC: String = "org.appspot.apprtc.DISABLE_BUILT_IN_AGC"
		const val EXTRA_DISABLE_BUILT_IN_NS: String = "org.appspot.apprtc.DISABLE_BUILT_IN_NS"
		const val EXTRA_DISABLE_WEBRTC_AGC_AND_HPF: String =
			"org.appspot.apprtc.DISABLE_WEBRTC_GAIN_CONTROL"
		const val EXTRA_DISPLAY_HUD: String = "org.appspot.apprtc.DISPLAY_HUD"
		const val EXTRA_TRACING: String = "org.appspot.apprtc.TRACING"
		const val EXTRA_CMDLINE: String = "org.appspot.apprtc.CMDLINE"
		const val EXTRA_RUNTIME: String = "org.appspot.apprtc.RUNTIME"
		const val EXTRA_VIDEO_FILE_AS_CAMERA: String = "org.appspot.apprtc.VIDEO_FILE_AS_CAMERA"
		const val EXTRA_SAVE_REMOTE_VIDEO_TO_FILE: String =
			"org.appspot.apprtc.SAVE_REMOTE_VIDEO_TO_FILE"
		const val EXTRA_SAVE_REMOTE_VIDEO_TO_FILE_WIDTH: String =
			"org.appspot.apprtc.SAVE_REMOTE_VIDEO_TO_FILE_WIDTH"
		const val EXTRA_SAVE_REMOTE_VIDEO_TO_FILE_HEIGHT: String =
			"org.appspot.apprtc.SAVE_REMOTE_VIDEO_TO_FILE_HEIGHT"
		const val EXTRA_USE_VALUES_FROM_INTENT: String = "org.appspot.apprtc.USE_VALUES_FROM_INTENT"
		const val EXTRA_DATA_CHANNEL_ENABLED: String = "org.appspot.apprtc.DATA_CHANNEL_ENABLED"
		const val EXTRA_ORDERED: String = "org.appspot.apprtc.ORDERED"
		const val EXTRA_MAX_RETRANSMITS_MS: String = "org.appspot.apprtc.MAX_RETRANSMITS_MS"
		const val EXTRA_MAX_RETRANSMITS: String = "org.appspot.apprtc.MAX_RETRANSMITS"
		const val EXTRA_PROTOCOL: String = "org.appspot.apprtc.PROTOCOL"
		const val EXTRA_NEGOTIATED: String = "org.appspot.apprtc.NEGOTIATED"
		const val EXTRA_ID: String = "org.appspot.apprtc.ID"
		const val EXTRA_ENABLE_RTCEVENTLOG: String = "org.appspot.apprtc.ENABLE_RTCEVENTLOG"

		private const val CAPTURE_PERMISSION_REQUEST_CODE = 1

		// List of mandatory application permissions.
		private val MANDATORY_PERMISSIONS = arrayOf(
			"android.permission.MODIFY_AUDIO_SETTINGS",
			"android.permission.RECORD_AUDIO", "android.permission.INTERNET"
		)

		// Peer connection statistics callback period in ms.
		private const val STAT_CALLBACK_PERIOD = 1000

		private var mediaProjectionPermissionResultData: Intent? = null
		private var mediaProjectionPermissionResultCode = 0

		private val systemUiVisibility: Int
			get() {
				return (View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_FULLSCREEN
						or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY)
			}
	}
}
