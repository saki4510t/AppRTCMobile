package org.appspot.apprtc

import android.content.SharedPreferences
import android.content.SharedPreferences.OnSharedPreferenceChangeListener
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.preference.CheckBoxPreference
import androidx.preference.ListPreference
import androidx.preference.Preference
import com.serenegiant.apprtcmobile.R
import org.webrtc.Camera2Enumerator
import org.webrtc.audio.JavaAudioDeviceModule

/*
*  Copyright 2014 The WebRTC Project Authors. All rights reserved.
*
*  Use of this source code is governed by a BSD-style license
*  that can be found in the LICENSE file in the root of the source
*  tree. An additional intellectual property rights grant can be found
*  in the file PATENTS.  All contributing project authors may
*  be found in the AUTHORS file in the root of the source tree.
*/

/**
 * Settings activity for AppRTC.
 */
class SettingsActivity : BaseActivity(), OnSharedPreferenceChangeListener {
	private lateinit var settingsFragment: SettingsFragment
	private lateinit var keyprefVideoCall: String
	private lateinit var keyprefScreenCapture: String
	private lateinit var keyprefCamera2: String
	private lateinit var keyprefResolution: String
	private lateinit var keyprefFps: String
	private lateinit var keyprefCaptureQualitySlider: String
	private lateinit var keyprefMaxVideoBitrateType: String
	private lateinit var keyprefMaxVideoBitrateValue: String
	private lateinit var keyPrefVideoCodec: String
	private lateinit var keyprefHwCodec: String
	private lateinit var keyprefCaptureToTexture: String
	private lateinit var keyprefFlexfec: String
	private lateinit var keyprefStartAudioBitrateType: String
	private lateinit var keyprefStartAudioBitrateValue: String
	private lateinit var keyPrefAudioCodec: String
	private lateinit var keyprefNoAudioProcessing: String
	private lateinit var keyprefAecDump: String
	private lateinit var keyprefEnableSaveInputAudioToFile: String
	private lateinit var keyprefOpenSLES: String
	private lateinit var keyprefDisableBuiltInAEC: String
	private lateinit var keyprefDisableBuiltInAGC: String
	private lateinit var keyprefDisableBuiltInNS: String
	private lateinit var keyprefDisableWebRtcAGCAndHPF: String
	private lateinit var keyprefSpeakerphone: String
	private lateinit var keyPrefRoomServerUrl: String
	private lateinit var keyPrefDisplayHud: String
	private lateinit var keyPrefTracing: String
	private lateinit var keyprefEnabledRtcEventLog: String
	private lateinit var keyprefEnableDataChannel: String
	private lateinit var keyprefOrdered: String
	private lateinit var keyprefMaxRetransmitTimeMs: String
	private lateinit var keyprefMaxRetransmits: String
	private lateinit var keyprefDataProtocol: String
	private lateinit var keyprefNegotiated: String
	private lateinit var keyprefDataId: String
	private lateinit var keyprefUseLegacyAudioDevice: String

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		enableEdgeToEdge()
		keyprefVideoCall = getString(R.string.pref_videocall_key)
		keyprefScreenCapture = getString(R.string.pref_screencapture_key)
		keyprefCamera2 = getString(R.string.pref_camera2_key)
		keyprefResolution = getString(R.string.pref_resolution_key)
		keyprefFps = getString(R.string.pref_fps_key)
		keyprefCaptureQualitySlider = getString(R.string.pref_capturequalityslider_key)
		keyprefMaxVideoBitrateType = getString(R.string.pref_maxvideobitrate_key)
		keyprefMaxVideoBitrateValue = getString(R.string.pref_maxvideobitratevalue_key)
		keyPrefVideoCodec = getString(R.string.pref_videocodec_key)
		keyprefHwCodec = getString(R.string.pref_hwcodec_key)
		keyprefCaptureToTexture = getString(R.string.pref_capturetotexture_key)
		keyprefFlexfec = getString(R.string.pref_flexfec_key)

		keyprefStartAudioBitrateType = getString(R.string.pref_startaudiobitrate_key)
		keyprefStartAudioBitrateValue = getString(R.string.pref_startaudiobitratevalue_key)
		keyPrefAudioCodec = getString(R.string.pref_audiocodec_key)
		keyprefNoAudioProcessing = getString(R.string.pref_noaudioprocessing_key)
		keyprefAecDump = getString(R.string.pref_aecdump_key)
		keyprefEnableSaveInputAudioToFile =
			getString(R.string.pref_enable_save_input_audio_to_file_key)
		keyprefOpenSLES = getString(R.string.pref_opensles_key)
		keyprefDisableBuiltInAEC = getString(R.string.pref_disable_built_in_aec_key)
		keyprefDisableBuiltInAGC = getString(R.string.pref_disable_built_in_agc_key)
		keyprefDisableBuiltInNS = getString(R.string.pref_disable_built_in_ns_key)
		keyprefDisableWebRtcAGCAndHPF = getString(R.string.pref_disable_webrtc_agc_and_hpf_key)
		keyprefSpeakerphone = getString(R.string.pref_speakerphone_key)

		keyprefEnableDataChannel = getString(R.string.pref_enable_datachannel_key)
		keyprefOrdered = getString(R.string.pref_ordered_key)
		keyprefMaxRetransmitTimeMs = getString(R.string.pref_max_retransmit_time_ms_key)
		keyprefMaxRetransmits = getString(R.string.pref_max_retransmits_key)
		keyprefDataProtocol = getString(R.string.pref_data_protocol_key)
		keyprefNegotiated = getString(R.string.pref_negotiated_key)
		keyprefDataId = getString(R.string.pref_data_id_key)

		keyPrefRoomServerUrl = getString(R.string.pref_room_server_url_key)
		keyPrefDisplayHud = getString(R.string.pref_displayhud_key)
		keyPrefTracing = getString(R.string.pref_tracing_key)
		keyprefEnabledRtcEventLog = getString(R.string.pref_enable_rtceventlog_key)
		keyprefUseLegacyAudioDevice = getString(R.string.pref_use_legacy_audio_device_key)

		// Display the fragment as the main content.
		settingsFragment = SettingsFragment()
		supportFragmentManager
			.beginTransaction()
			.replace(android.R.id.content, settingsFragment)
			.commit()
	}

	override fun onResume() {
		super.onResume()
		// Set summary to be the user-description for the selected value
		val sharedPreferences = settingsFragment.preferenceScreen.sharedPreferences
		sharedPreferences!!.registerOnSharedPreferenceChangeListener(this)
		updateSummaryB(sharedPreferences, keyprefVideoCall)
		updateSummaryB(sharedPreferences, keyprefScreenCapture)
		updateSummaryB(sharedPreferences, keyprefCamera2)
		updateSummary(sharedPreferences, keyprefResolution)
		updateSummary(sharedPreferences, keyprefFps)
		updateSummaryB(sharedPreferences, keyprefCaptureQualitySlider)
		updateSummary(sharedPreferences, keyprefMaxVideoBitrateType)
		updateSummaryBitrate(sharedPreferences, keyprefMaxVideoBitrateValue)
		setVideoBitrateEnable(sharedPreferences)
		updateSummary(sharedPreferences, keyPrefVideoCodec)
		updateSummaryB(sharedPreferences, keyprefHwCodec)
		updateSummaryB(sharedPreferences, keyprefCaptureToTexture)
		updateSummaryB(sharedPreferences, keyprefFlexfec)

		updateSummary(sharedPreferences, keyprefStartAudioBitrateType)
		updateSummaryBitrate(sharedPreferences, keyprefStartAudioBitrateValue)
		setAudioBitrateEnable(sharedPreferences)
		updateSummary(sharedPreferences, keyPrefAudioCodec)
		updateSummaryB(sharedPreferences, keyprefNoAudioProcessing)
		updateSummaryB(sharedPreferences, keyprefAecDump)
		updateSummaryB(sharedPreferences, keyprefEnableSaveInputAudioToFile)
		updateSummaryB(sharedPreferences, keyprefOpenSLES)
		updateSummaryB(sharedPreferences, keyprefDisableBuiltInAEC)
		updateSummaryB(sharedPreferences, keyprefDisableBuiltInAGC)
		updateSummaryB(sharedPreferences, keyprefDisableBuiltInNS)
		updateSummaryB(sharedPreferences, keyprefDisableWebRtcAGCAndHPF)
		updateSummaryList(sharedPreferences, keyprefSpeakerphone)

		updateSummaryB(sharedPreferences, keyprefEnableDataChannel)
		updateSummaryB(sharedPreferences, keyprefOrdered)
		updateSummary(sharedPreferences, keyprefMaxRetransmitTimeMs)
		updateSummary(sharedPreferences, keyprefMaxRetransmits)
		updateSummary(sharedPreferences, keyprefDataProtocol)
		updateSummaryB(sharedPreferences, keyprefNegotiated)
		updateSummary(sharedPreferences, keyprefDataId)
		setDataChannelEnable(sharedPreferences)

		updateSummary(sharedPreferences, keyPrefRoomServerUrl)
		updateSummaryB(sharedPreferences, keyPrefDisplayHud)
		updateSummaryB(sharedPreferences, keyPrefTracing)
		updateSummaryB(sharedPreferences, keyprefEnabledRtcEventLog)
		updateSummaryB(sharedPreferences, keyprefUseLegacyAudioDevice)

		if (!Camera2Enumerator.isSupported(this)) {
			val camera2Preference = settingsFragment.findPreference<CheckBoxPreference>(keyprefCamera2)
			if (camera2Preference != null) {
				camera2Preference.summary = getString(R.string.pref_camera2_not_supported)
				camera2Preference.isEnabled = false
			}
		}

		if (!JavaAudioDeviceModule.isBuiltInAcousticEchoCancelerSupported()) {
			val disableBuiltInAECPreference =
				settingsFragment.findPreference<CheckBoxPreference>(keyprefDisableBuiltInAEC)
			if (disableBuiltInAECPreference != null) {
				disableBuiltInAECPreference.summary =
					getString(R.string.pref_built_in_aec_not_available)
				disableBuiltInAECPreference.isEnabled = false
			}
		}

		val disableBuiltInAGCPreference =
			settingsFragment.findPreference<CheckBoxPreference>(keyprefDisableBuiltInAGC)

		if (disableBuiltInAGCPreference != null) {
			disableBuiltInAGCPreference.summary =
				getString(R.string.pref_built_in_agc_not_available)
			disableBuiltInAGCPreference.isEnabled = false
		}

		if (!JavaAudioDeviceModule.isBuiltInNoiseSuppressorSupported()) {
			val disableBuiltInNSPreference =
				settingsFragment.findPreference<CheckBoxPreference>(keyprefDisableBuiltInNS)

			if (disableBuiltInNSPreference != null) {
				disableBuiltInNSPreference.summary =
					getString(R.string.pref_built_in_ns_not_available)
				disableBuiltInNSPreference.isEnabled = false
			}
		}
	}

	override fun onPause() {
		super.onPause()
		val sharedPreferences =
			settingsFragment.preferenceScreen.sharedPreferences
		sharedPreferences!!.unregisterOnSharedPreferenceChangeListener(this)
	}

	override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences, key: String?) {
		// clang-format off
		if (key == keyprefResolution
			|| key == keyprefFps
			|| key == keyprefMaxVideoBitrateType
			|| key == keyPrefVideoCodec
			|| key == keyprefStartAudioBitrateType
			|| key == keyPrefAudioCodec
			|| key == keyPrefRoomServerUrl
			|| key == keyprefMaxRetransmitTimeMs
			|| key == keyprefMaxRetransmits
			|| key == keyprefDataProtocol
			|| key == keyprefDataId
		) {
			updateSummary(sharedPreferences, key)
		} else if (key == keyprefMaxVideoBitrateValue
			|| key == keyprefStartAudioBitrateValue
		) {
			updateSummaryBitrate(sharedPreferences, key)
		} else if (key == keyprefVideoCall
			|| key == keyprefScreenCapture
			|| key == keyprefCamera2
			|| key == keyPrefTracing
			|| key == keyprefCaptureQualitySlider
			|| key == keyprefHwCodec
			|| key == keyprefCaptureToTexture
			|| key == keyprefFlexfec
			|| key == keyprefNoAudioProcessing
			|| key == keyprefAecDump
			|| key == keyprefEnableSaveInputAudioToFile
			|| key == keyprefOpenSLES
			|| key == keyprefDisableBuiltInAEC
			|| key == keyprefDisableBuiltInAGC
			|| key == keyprefDisableBuiltInNS
			|| key == keyprefDisableWebRtcAGCAndHPF
			|| key == keyPrefDisplayHud
			|| key == keyprefEnableDataChannel
			|| key == keyprefOrdered
			|| key == keyprefNegotiated
			|| key == keyprefEnabledRtcEventLog
			|| key == keyprefUseLegacyAudioDevice
		) {
			updateSummaryB(sharedPreferences, key)
		} else if (key == keyprefSpeakerphone) {
			updateSummaryList(sharedPreferences, key)
		}
		// clang-format on
		if (key == keyprefMaxVideoBitrateType) {
			setVideoBitrateEnable(sharedPreferences)
		}
		if (key == keyprefStartAudioBitrateType) {
			setAudioBitrateEnable(sharedPreferences)
		}
		if (key == keyprefEnableDataChannel) {
			setDataChannelEnable(sharedPreferences)
		}
	}

	private fun updateSummary(sharedPreferences: SharedPreferences, key: String) {
		val updatedPref = settingsFragment.findPreference<Preference>(key)
		// Set summary to be the user-description for the selected value
		updatedPref?.summary = sharedPreferences.getString(key, "")
	}

	private fun updateSummaryBitrate(sharedPreferences: SharedPreferences, key: String) {
		val updatedPref = settingsFragment.findPreference<Preference>(key)
		updatedPref?.summary = sharedPreferences.getString(key, "") + " kbps"
	}

	private fun updateSummaryB(sharedPreferences: SharedPreferences, key: String) {
		val updatedPref = settingsFragment.findPreference<Preference>(key)
		updatedPref?.summary = if (sharedPreferences.getBoolean(key, true))
			getString(R.string.pref_value_enabled)
		else
			getString(R.string.pref_value_disabled)
	}

	private fun updateSummaryList(sharedPreferences: SharedPreferences, key: String) {
		val updatedPref = settingsFragment.findPreference<ListPreference>(key)
		updatedPref?.summary = updatedPref?.entry
	}

	private fun setVideoBitrateEnable(sharedPreferences: SharedPreferences) {
		val bitratePreferenceValue =
			settingsFragment.findPreference<Preference>(keyprefMaxVideoBitrateValue)
		val bitrateTypeDefault = getString(R.string.pref_maxvideobitrate_default)
		val bitrateType =
			sharedPreferences.getString(keyprefMaxVideoBitrateType, bitrateTypeDefault)!!
		bitratePreferenceValue?.isEnabled = bitrateType != bitrateTypeDefault
	}

	private fun setAudioBitrateEnable(sharedPreferences: SharedPreferences) {
		val bitratePreferenceValue =
			settingsFragment.findPreference<Preference>(keyprefStartAudioBitrateValue)
		val bitrateTypeDefault = getString(R.string.pref_startaudiobitrate_default)
		val bitrateType =
			sharedPreferences.getString(keyprefStartAudioBitrateType, bitrateTypeDefault)!!
		bitratePreferenceValue?.isEnabled = bitrateType != bitrateTypeDefault
	}

	private fun setDataChannelEnable(sharedPreferences: SharedPreferences) {
		val enabled = sharedPreferences.getBoolean(keyprefEnableDataChannel, true)
		settingsFragment.findPreference<Preference>(keyprefOrdered)?.isEnabled = enabled
		settingsFragment.findPreference<Preference>(keyprefMaxRetransmitTimeMs)?.isEnabled = enabled
		settingsFragment.findPreference<Preference>(keyprefMaxRetransmits)?.isEnabled = enabled
		settingsFragment.findPreference<Preference>(keyprefDataProtocol)?.isEnabled = enabled
		settingsFragment.findPreference<Preference>(keyprefNegotiated)?.isEnabled = enabled
		settingsFragment.findPreference<Preference>(keyprefDataId)?.isEnabled = enabled
	}
}
