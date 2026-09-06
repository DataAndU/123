package com.minijarvis.app.control

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.provider.Settings

enum class VolumeStream(val audioStream: Int) {
    MEDIA(AudioManager.STREAM_MUSIC),
    ALARM(AudioManager.STREAM_ALARM),
    RING(AudioManager.STREAM_RING)
}

/**
 * Direct, permission-gated control over phone-wide settings that don't need
 * the Accessibility Service: volume, brightness, flashlight, and
 * Do-Not-Disturb. WiFi/Bluetooth are handled separately (see
 * [JarvisAccessibilityService]) since Android 10+ no longer lets a regular
 * app toggle radios silently — only open the settings UI for them.
 */
class SystemControlManager(private val context: Context) {

    private val audioManager: AudioManager
        get() = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private val notificationManager: NotificationManager
        get() = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    private val cameraManager: CameraManager
        get() = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager

    // ---------------- Volume ----------------

    fun adjustVolume(stream: VolumeStream, raise: Boolean): Boolean {
        if (stream == VolumeStream.RING && !hasNotificationPolicyAccess()) return false
        return try {
            val direction = if (raise) AudioManager.ADJUST_RAISE else AudioManager.ADJUST_LOWER
            audioManager.adjustStreamVolume(stream.audioStream, direction, AudioManager.FLAG_SHOW_UI)
            true
        } catch (e: SecurityException) {
            false
        }
    }

    fun setMuted(stream: VolumeStream, muted: Boolean): Boolean {
        if (stream == VolumeStream.RING && !hasNotificationPolicyAccess()) return false
        return try {
            audioManager.adjustStreamVolume(
                stream.audioStream,
                if (muted) AudioManager.ADJUST_MUTE else AudioManager.ADJUST_UNMUTE,
                AudioManager.FLAG_SHOW_UI
            )
            true
        } catch (e: SecurityException) {
            false
        }
    }

    // ---------------- Do Not Disturb ----------------

    fun hasNotificationPolicyAccess(): Boolean = notificationManager.isNotificationPolicyAccessGranted

    fun requestNotificationPolicyAccessIntent(): Intent =
        Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)

    fun setDoNotDisturb(enabled: Boolean): Boolean {
        if (!hasNotificationPolicyAccess()) return false
        notificationManager.setInterruptionFilter(
            if (enabled) NotificationManager.INTERRUPTION_FILTER_PRIORITY else NotificationManager.INTERRUPTION_FILTER_ALL
        )
        return true
    }

    // ---------------- Brightness ----------------

    fun hasWriteSettingsPermission(): Boolean = Settings.System.canWrite(context)

    fun requestWriteSettingsIntent(): Intent =
        Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:${context.packageName}"))

    fun adjustBrightness(raise: Boolean): Boolean {
        if (!hasWriteSettingsPermission()) return false
        val current = try {
            Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS)
        } catch (e: Settings.SettingNotFoundException) {
            128
        }
        val delta = if (raise) 40 else -40
        val updated = (current + delta).coerceIn(10, 255)
        Settings.System.putInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS, updated)
        return true
    }

    // ---------------- Flashlight ----------------

    fun setFlashlight(on: Boolean): Boolean {
        return try {
            val cameraId = cameraManager.cameraIdList.firstOrNull { id ->
                cameraManager.getCameraCharacteristics(id).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            } ?: return false
            cameraManager.setTorchMode(cameraId, on)
            true
        } catch (e: Exception) {
            false
        }
    }

    // ---------------- WiFi / Bluetooth (panel only — see caveat above) ----------------

    fun wifiPanelIntent(): Intent =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) Intent(Settings.Panel.ACTION_WIFI)
        else Intent(Settings.ACTION_WIFI_SETTINGS)

    fun bluetoothSettingsIntent(): Intent = Intent(Settings.ACTION_BLUETOOTH_SETTINGS)
}
