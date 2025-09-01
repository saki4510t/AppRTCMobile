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

import android.content.Context
import android.os.Build
import kotlin.jvm.Synchronized
import com.serenegiant.nio.CharsetsUtils
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.SystemClock
import android.util.Log
import com.serenegiant.apprtcmobile.BuildConfig
import java.io.*
import java.lang.AssertionError
import java.lang.Exception
import java.lang.NumberFormatException
import java.lang.RuntimeException
import java.lang.StringBuilder
import java.lang.ref.WeakReference
import java.util.*
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

/**
 * Simple CPU monitor.  The caller creates a org.appspot.apprtc.CpuMonitor object which can then
 * be used via sampleCpuUtilization() to collect the percentual use of the
 * cumulative CPU capacity for all CPUs running at their nominal frequency.  3
 * values are generated: (1) getCpuCurrent() returns the use since the last
 * sampleCpuUtilization(), (2) getCpuAvg3() returns the use since 3 prior
 * calls, and (3) getCpuAvgAll() returns the use over all SAMPLE_SAVE_NUMBER
 * calls.
 *
 *
 * CPUs in Android are often "offline", and while this of course means 0 Hz
 * as current frequency, in this state we cannot even get their nominal
 * frequency.  We therefore tread carefully, and allow any CPU to be missing.
 * Missing CPUs are assumed to have the same nominal frequency as any close
 * lower-numbered CPU, but as soon as it is online, we'll get their proper
 * frequency and remember it.  (Since CPU 0 in practice always seem to be
 * online, this unidirectional frequency inheritance should be no problem in
 * practice.)
 *
 *
 * Caveats:
 * o No provision made for zany "turbo" mode, common in the x86 world.
 * o No provision made for ARM big.LITTLE; if CPU n can switch behind our
 * back, we might get incorrect estimates.
 * o This is not thread-safe.  To call asynchronously, create different
 * org.appspot.apprtc.CpuMonitor objects.
 *
 *
 * If we can gather enough info to generate a sensible result,
 * sampleCpuUtilization returns true.  It is designed to never throw an
 * exception.
 *
 *
 * sampleCpuUtilization should not be called too often in its present form,
 * since then deltas would be small and the percent values would fluctuate and
 * be unreadable. If it is desirable to call it more often than say once per
 * second, one would need to increase SAMPLE_SAVE_NUMBER and probably use
 * Queue<Integer> to avoid copying overhead.
 *
</Integer> *
 * Known problems:
 * 1. Nexus 7 devices running Kitkat have a kernel which often output an
 * incorrect 'idle' field in /proc/stat.  The value is close to twice the
 * correct value, and then returns to back to correct reading.  Both when
 * jumping up and back down we might create faulty CPU load readings.
 *
 * @param context
 */
class CpuMonitor(context: Context) {
	/**
	 * Should not hold strong reference of (app) context!!
	 */
	private val mWeakAppContext: WeakReference<Context>

	// User CPU usage at current frequency.
	private val userCpuUsage: MovingAverage

	// System CPU usage at current frequency.
	private val systemCpuUsage: MovingAverage

	// Total CPU usage relative to maximum frequency.
	private val totalCpuUsage: MovingAverage

	// CPU frequency in percentage from maximum.
	private val frequencyScale: MovingAverage
	private var executor: ScheduledExecutorService? = null
	private var lastStatLogTimeMs: Long
	private lateinit var cpuFreqMax: LongArray
	private var cpusPresent = 0
	private var actualCpusPresent = 0
	private var initialized = false
	private var cpuOveruse = false
	private lateinit var maxPath: Array<String?>
	private lateinit var curPath: Array<String?>
	private lateinit var curFreqScales: DoubleArray
	private var lastProcStat: ProcStat? = null
	private var mActiveFuture: Future<*>? = null
	private var mReleased = false

	private class ProcStat constructor(
		val userTime: Long,
		val systemTime: Long,
		val idleTime: Long
	)

	/**
	 * 移動平均を計算するためのヘルパークラス
	 */
	private class MovingAverage(size: Int) {
		private val size: Int
		private var sum = 0.0
		var current = 0.0
			private set
		private val circularBuffer: DoubleArray
		private var circularBufferIndex = 0

		init {
			if (size <= 0) {
				throw AssertionError("Size value in MovingAverage ctor should be positive.")
			}
			this.size = size
			circularBuffer = DoubleArray(size)
		}

		fun reset() {
			Arrays.fill(circularBuffer, 0.0)
			circularBufferIndex = 0
			sum = 0.0
			current = 0.0
		}

		fun addValue(value: Double) {
			sum -= circularBuffer[circularBufferIndex]
			circularBuffer[circularBufferIndex++] = value
			current = value
			sum += value
			if (circularBufferIndex >= size) {
				circularBufferIndex = 0
			}
		}

		val average: Double
			get() = sum / size.toDouble()
	}

	init {
		if (!isSupported) {
			throw RuntimeException("org.appspot.apprtc.CpuMonitor is not supported on this Android version.")
		}
		if (DEBUG) Log.d(TAG, "org.appspot.apprtc.CpuMonitor ctor.")
		mWeakAppContext = WeakReference(context.applicationContext)
		userCpuUsage = MovingAverage(MOVING_AVERAGE_SAMPLES)
		systemCpuUsage = MovingAverage(MOVING_AVERAGE_SAMPLES)
		totalCpuUsage = MovingAverage(MOVING_AVERAGE_SAMPLES)
		frequencyScale = MovingAverage(MOVING_AVERAGE_SAMPLES)
		lastStatLogTimeMs = SystemClock.elapsedRealtime()
		scheduleCpuUtilizationTask()
	}

	/**
	 * 関連するリソースを破棄する
	 */
	@Synchronized
	fun release() {
		if (!mReleased) {
			mReleased = true
			if (DEBUG) Log.d(TAG, "release:")
			releaseExecutor()
		}
	}

	@Synchronized
	fun pause() {
		if (DEBUG) Log.d(TAG, "pause:")
		releaseExecutor()
	}

	fun resume() {
		if (DEBUG) Log.d(TAG, "resume:")
		resetStat()
		scheduleCpuUtilizationTask()
	}

	// TODO(bugs.webrtc.org/8491): Remove NoSynchronizedMethodCheck suppression.
	@Synchronized
	fun reset() {
		if (executor != null) {
			if (DEBUG) Log.d(TAG, "reset")
			resetStat()
			cpuOveruse = false
		}
	}

	// TODO(bugs.webrtc.org/8491): Remove NoSynchronizedMethodCheck suppression.
	@get:Synchronized
	val cpuUsageCurrent: Int
		get() = doubleToPercent(userCpuUsage.current + systemCpuUsage.current)

	// TODO(bugs.webrtc.org/8491): Remove NoSynchronizedMethodCheck suppression.
	@get:Synchronized
	val cpuUsageAverage: Int
		get() = doubleToPercent(userCpuUsage.average + systemCpuUsage.average)

	// TODO(bugs.webrtc.org/8491): Remove NoSynchronizedMethodCheck suppression.
	@get:Synchronized
	val frequencyScaleAverage: Int
		get() = doubleToPercent(frequencyScale.average)
	//--------------------------------------------------------------------------------
	/**
	 * 実行中のタスクがあれば終了させる
	 * Executorがあればシャットダウンして破棄する
	 */
	@Synchronized
	private fun releaseExecutor() {
		if (mActiveFuture != null) {
			mActiveFuture!!.cancel(true)
			mActiveFuture = null
		}
		val exc = executor
		executor = null
		if (exc != null && !exc.isShutdown) {
			exc.shutdownNow()
		}
	}

	@Synchronized
	private fun scheduleCpuUtilizationTask() {
		releaseExecutor()
		if (!mReleased) {
			executor = Executors.newSingleThreadScheduledExecutor()
			mActiveFuture = executor!!.scheduleAtFixedRate(
				Runnable { cpuUtilizationTask() },
				0,
				CPU_STAT_SAMPLE_PERIOD_MS.toLong(),
				TimeUnit.MILLISECONDS
			)
		}
	}

	private fun cpuUtilizationTask() {
		if (!mReleased) {
			val cpuMonitorAvailable = sampleCpuUtilization()
			if (cpuMonitorAvailable
				&& SystemClock.elapsedRealtime() - lastStatLogTimeMs >= CPU_STAT_LOG_PERIOD_MS
			) {
				lastStatLogTimeMs = SystemClock.elapsedRealtime()
				val statString = statString
				if (BuildConfig.DEBUG) Log.d(TAG, statString)
			}
		}
	}

	private fun init() {
		try {
			FileInputStream("/sys/devices/system/cpu/present").use { fin ->
				InputStreamReader(fin, CharsetsUtils.UTF8).use { streamReader ->
					BufferedReader(streamReader).use { reader ->
						Scanner(reader).useDelimiter("[-\n]").use { scanner ->
							scanner.nextInt() // Skip leading number 0.
							cpusPresent = 1 + scanner.nextInt()
						}
					}
				}
			}
		} catch (e: FileNotFoundException) {
			if (DEBUG) Log.e(TAG, "Cannot do CPU stats since /sys/devices/system/cpu/present is missing")
		} catch (e: IOException) {
			if (DEBUG) Log.e(TAG, "Error closing file")
		} catch (e: Exception) {
			if (DEBUG) Log.e(
				TAG,
				"Cannot do CPU stats due to /sys/devices/system/cpu/present parsing problem"
			)
		}
		cpuFreqMax = LongArray(cpusPresent)
		maxPath = arrayOfNulls(cpusPresent)
		curPath = arrayOfNulls(cpusPresent)
		curFreqScales = DoubleArray(cpusPresent)
		for (i in 0 until cpusPresent) {
			cpuFreqMax[i] = 0 // Frequency "not yet determined".
			curFreqScales[i] = 0.0
			maxPath[i] = "/sys/devices/system/cpu/cpu$i/cpufreq/cpuinfo_max_freq"
			curPath[i] = "/sys/devices/system/cpu/cpu$i/cpufreq/scaling_cur_freq"
		}
		lastProcStat = ProcStat(0, 0, 0)
		resetStat()
		initialized = true
	}

	@Synchronized
	private fun resetStat() {
		userCpuUsage.reset()
		systemCpuUsage.reset()
		totalCpuUsage.reset()
		frequencyScale.reset()
		lastStatLogTimeMs = SystemClock.elapsedRealtime()
	}/* receiver */

	// Use sticky broadcast with null receiver to read battery level once only.
	private val batteryLevel: Int
		get() {
			// Use sticky broadcast with null receiver to read battery level once only.
			var batteryLevel = 0
			val appContext = mWeakAppContext.get()
			if (appContext != null) {
				val intent = appContext.registerReceiver(
					null /* receiver */, IntentFilter(Intent.ACTION_BATTERY_CHANGED)
				)
				val batteryScale = intent!!.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
				if (batteryScale > 0) {
					batteryLevel = (100f * intent.getIntExtra(
						BatteryManager.EXTRA_LEVEL,
						0
					) / batteryScale).toInt()
				}
			}
			return batteryLevel
		}

	/**
	 * Re-measure CPU use.  Call this method at an interval of around 1/s.
	 * This method returns true on success.  The fields
	 * cpuCurrent, cpuAvg3, and cpuAvgAll are updated on success, and represents:
	 * cpuCurrent: The CPU use since the last sampleCpuUtilization call.
	 * cpuAvg3: The average CPU over the last 3 calls.
	 * cpuAvgAll: The average CPU over the last SAMPLE_SAVE_NUMBER calls.
	 */
	@Synchronized
	private fun sampleCpuUtilization(): Boolean {
		var lastSeenMaxFreq: Long = 0
		var cpuFreqCurSum: Long = 0
		var cpuFreqMaxSum: Long = 0
		if (!initialized) {
			init()
		}
		if (cpusPresent == 0) {
			return false
		}
		actualCpusPresent = 0
		for (i in 0 until cpusPresent) {
			/*
			 * For each CPU, attempt to first read its max frequency, then its
			 * current frequency.  Once as the max frequency for a CPU is found,
			 * save it in cpuFreqMax[].
			 */
			curFreqScales[i] = 0.0
			if (cpuFreqMax[i] == 0L) {
				// We have never found this CPU's max frequency.  Attempt to read it.
				val cpufreqMax = readFreqFromFile(maxPath[i])
				if (cpufreqMax > 0) {
					if (DEBUG) Log.d(TAG, "Core $i. Max frequency: $cpufreqMax")
					lastSeenMaxFreq = cpufreqMax
					cpuFreqMax[i] = cpufreqMax
					maxPath[i] = null // Kill path to free its memory.
				}
			} else {
				lastSeenMaxFreq = cpuFreqMax[i] // A valid, previously read value.
			}
			val cpuFreqCur = readFreqFromFile(curPath[i])
			if (cpuFreqCur == 0L && lastSeenMaxFreq == 0L) {
				// No current frequency information for this CPU core - ignore it.
				continue
			}
			if (cpuFreqCur > 0) {
				actualCpusPresent++
			}
			cpuFreqCurSum += cpuFreqCur

			/* Here, lastSeenMaxFreq might come from
			 * 1. cpuFreq[i], or
			 * 2. a previous iteration, or
			 * 3. a newly read value, or
			 * 4. hypothetically from the pre-loop dummy.
			 */cpuFreqMaxSum += lastSeenMaxFreq
			if (lastSeenMaxFreq > 0) {
				curFreqScales[i] = cpuFreqCur.toDouble() / lastSeenMaxFreq
			}
		}
		if (cpuFreqCurSum == 0L || cpuFreqMaxSum == 0L) {
			if (DEBUG) Log.e(TAG, "Could not read max or current frequency for any CPU")
			return false
		}

		/*
		 * Since the cycle counts are for the period between the last invocation
		 * and this present one, we average the percentual CPU frequencies between
		 * now and the beginning of the measurement period.  This is significantly
		 * incorrect only if the frequencies have peeked or dropped in between the
		 * invocations.
		 */
		var currentFrequencyScale = cpuFreqCurSum / cpuFreqMaxSum.toDouble()
		if (frequencyScale.current > 0) {
			currentFrequencyScale = (frequencyScale.current + currentFrequencyScale) * 0.5
		}
		val procStat = readProcStat() ?: return false
		val diffUserTime = procStat.userTime - lastProcStat!!.userTime
		val diffSystemTime = procStat.systemTime - lastProcStat!!.systemTime
		val diffIdleTime = procStat.idleTime - lastProcStat!!.idleTime
		val allTime = diffUserTime + diffSystemTime + diffIdleTime
		if (currentFrequencyScale == 0.0 || allTime == 0L) {
			return false
		}

		// Update statistics.
		frequencyScale.addValue(currentFrequencyScale)
		val currentUserCpuUsage = diffUserTime / allTime.toDouble()
		userCpuUsage.addValue(currentUserCpuUsage)
		val currentSystemCpuUsage = diffSystemTime / allTime.toDouble()
		systemCpuUsage.addValue(currentSystemCpuUsage)
		val currentTotalCpuUsage =
			(currentUserCpuUsage + currentSystemCpuUsage) * currentFrequencyScale
		totalCpuUsage.addValue(currentTotalCpuUsage)

		// Save new measurements for next round's deltas.
		lastProcStat = procStat
		return true
	}

	private fun doubleToPercent(d: Double): Int {
		return (d * 100 + 0.5).toInt()
	}

	@get:Synchronized
	private val statString: String
		get() {
			val stat = StringBuilder()
			stat.append("CPU User: ")
				.append(doubleToPercent(userCpuUsage.current))
				.append("/")
				.append(doubleToPercent(userCpuUsage.average))
				.append(". System: ")
				.append(doubleToPercent(systemCpuUsage.current))
				.append("/")
				.append(doubleToPercent(systemCpuUsage.average))
				.append(". Freq: ")
				.append(doubleToPercent(frequencyScale.current))
				.append("/")
				.append(doubleToPercent(frequencyScale.average))
				.append(". Total usage: ")
				.append(doubleToPercent(totalCpuUsage.current))
				.append("/")
				.append(doubleToPercent(totalCpuUsage.average))
				.append(". Cores: ")
				.append(actualCpusPresent)
			stat.append("( ")
			for (i in 0 until cpusPresent) {
				stat.append(doubleToPercent(curFreqScales[i])).append(" ")
			}
			stat.append("). Battery: ").append(batteryLevel)
			if (cpuOveruse) {
				stat.append(". Overuse.")
			}
			return stat.toString()
		}

	/**
	 * Read a single integer value from the named file.  Return the read value
	 * or if an error occurs return 0.
	 */
	private fun readFreqFromFile(fileName: String?): Long {
		var number: Long = 0
		try {
			FileInputStream(fileName).use { stream ->
				InputStreamReader(stream, CharsetsUtils.UTF8).use { streamReader ->
					BufferedReader(streamReader).use { reader ->
						val line = reader.readLine()
						number = parseLong(line)
					}
				}
			}
		} catch (e: FileNotFoundException) {
			// CPU core is off, so file with its scaling frequency .../cpufreq/scaling_cur_freq
			// is not present. This is not an error.
		} catch (e: IOException) {
			// CPU core is off, so file with its scaling frequency .../cpufreq/scaling_cur_freq
			// is empty. This is not an error.
		}
		return number
	}

	/*
	 * Read the current utilization of all CPUs using the cumulative first line
	 * of /proc/stat.
	 */
	private fun readProcStat(): ProcStat? {
		var userTime: Long = 0
		var systemTime: Long = 0
		var idleTime: Long = 0
		try {
			FileInputStream("/proc/stat").use { stream ->
				InputStreamReader(stream, CharsetsUtils.UTF8).use { streamReader ->
					BufferedReader(streamReader).use { reader ->
						// line should contain something like this:
						// cpu  5093818 271838 3512830 165934119 101374 447076 272086 0 0 0
						//       user    nice  system     idle   iowait  irq   softirq
						val line = reader.readLine()
						val lines = line.split("\\s+".toRegex()).toTypedArray()
						val length = lines.size
						if (length >= 5) {
							userTime = parseLong(lines[1]) // user
							userTime += parseLong(lines[2]) // nice
							systemTime = parseLong(lines[3]) // system
							idleTime = parseLong(lines[4]) // idle
						}
						if (length >= 8) {
							userTime += parseLong(lines[5]) // iowait
							systemTime += parseLong(lines[6]) // irq
							systemTime += parseLong(lines[7]) // softirq
						}
					}
				}
			}
		} catch (e: FileNotFoundException) {
			if (DEBUG) Log.e(TAG, "Cannot open /proc/stat for reading", e)
			return null
		} catch (e: Exception) {
			if (DEBUG) Log.e(TAG, "Problems parsing /proc/stat", e)
			return null
		}
		return ProcStat(userTime, systemTime, idleTime)
	}

	companion object {
		private const val DEBUG = false // set false on production
		private val TAG = CpuMonitor::class.java.simpleName
		private const val MOVING_AVERAGE_SAMPLES = 5
		private const val CPU_STAT_SAMPLE_PERIOD_MS = 2000
		private const val CPU_STAT_LOG_PERIOD_MS = 6000

		@JvmStatic
		val isSupported: Boolean
			get() = Build.VERSION.SDK_INT < Build.VERSION_CODES.N

		private fun parseLong(value: String): Long {
			var number: Long = 0
			try {
				number = value.toLong()
			} catch (e: NumberFormatException) {
				if (DEBUG) Log.e(TAG, "parseLong error.", e)
			}
			return number
		}
	}
}
