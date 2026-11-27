package com.floatingtasbih.app

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.SeekBar
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.floatingtasbih.app.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var prefs: TasbihPrefs

    private val overlayPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            updatePermissionAndServiceUi()
            if (Settings.canDrawOverlays(this)) {
                Toast.makeText(this, R.string.overlay_permission_granted, Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, R.string.overlay_permission_required_msg, Toast.LENGTH_LONG).show()
            }
        }

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { _ ->
            // Foreground service continues to work even if notification permission is declined
        }

    private val serviceStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                FloatingOverlayService.ACTION_COUNT_UPDATED,
                FloatingOverlayService.ACTION_SERVICE_STOPPED -> {
                    syncUiFromPrefs()
                    updatePermissionAndServiceUi()
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prefs = TasbihPrefs(this)
        setupControls()
        syncUiFromPrefs()
        updatePermissionAndServiceUi()
    }

    override fun onStart() {
        super.onStart()
        val filter = IntentFilter().apply {
            addAction(FloatingOverlayService.ACTION_COUNT_UPDATED)
            addAction(FloatingOverlayService.ACTION_SERVICE_STOPPED)
        }
        ContextCompat.registerReceiver(
            this,
            serviceStateReceiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }

    override fun onResume() {
        super.onResume()
        syncUiFromPrefs()
        updatePermissionAndServiceUi()
    }

    override fun onStop() {
        super.onStop()
        try {
            unregisterReceiver(serviceStateReceiver)
        } catch (_: IllegalArgumentException) {
        }
    }

    private fun setupControls() {
        // Request SYSTEM_ALERT_WINDOW permission button
        binding.btnGrantPermission.setOnClickListener {
            openOverlayPermissionSettings()
        }

        // Toggle Floating Overlay Service
        binding.btnToggleOverlay.setOnClickListener {
            if (!Settings.canDrawOverlays(this)) {
                Toast.makeText(this, R.string.overlay_permission_required_msg, Toast.LENGTH_SHORT).show()
                openOverlayPermissionSettings()
                return@setOnClickListener
            }

            if (prefs.isOverlayActive) {
                stopFloatingService()
            } else {
                startFloatingService()
            }
        }

        // In-app counter increment & reset
        binding.cardPreviewTasbih.setOnClickListener {
            prefs.count += 1
            prefs.totalCount += 1
            syncUiFromPrefs()
            notifyOverlayConfigChanged()
        }

        binding.btnResetCount.setOnClickListener {
            prefs.count = 0
            syncUiFromPrefs()
            notifyOverlayConfigChanged()
        }

        // Cycle Dhikr
        binding.btnNextDhikr.setOnClickListener {
            prefs.nextDhikr()
            binding.etCustomDhikr.setText("")
            syncUiFromPrefs()
            notifyOverlayConfigChanged()
        }

        // Apply custom Dhikr
        binding.btnApplyCustomDhikr.setOnClickListener {
            val custom = binding.etCustomDhikr.text?.toString()?.trim().orEmpty()
            if (custom.isNotEmpty()) {
                prefs.customDhikr = custom
                prefs.useCustomDhikr = true
            } else {
                prefs.useCustomDhikr = false
            }
            syncUiFromPrefs()
            notifyOverlayConfigChanged()
        }

        // Target presets (33, 99, 100, Unlimited)
        binding.chipTarget33.setOnClickListener { updateTarget(33) }
        binding.chipTarget99.setOnClickListener { updateTarget(99) }
        binding.chipTarget100.setOnClickListener { updateTarget(100) }
        binding.chipTargetUnlimited.setOnClickListener { updateTarget(0) }

        // Opacity SeekBar (20% .. 100%)
        binding.seekOpacity.max = 80
        binding.seekOpacity.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val opacity = progress + 20
                binding.tvOpacityValue.text = "$opacity%"
                if (fromUser) {
                    prefs.opacityPercent = opacity
                    applyPreviewVisuals()
                    notifyOverlayConfigChanged()
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
            override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
        })

        // Bubble Size SeekBar (75% .. 145%)
        binding.seekBubbleSize.max = 70
        binding.seekBubbleSize.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val scale = progress + 75
                binding.tvBubbleSizeValue.text = "$scale%"
                if (fromUser) {
                    prefs.bubbleScalePercent = scale
                    notifyOverlayConfigChanged()
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
            override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
        })

        // Font Size SeekBar (11sp .. 24sp)
        binding.seekFontSize.max = 13
        binding.seekFontSize.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val sp = progress + 11
                binding.tvFontSizeValue.text = "${sp}sp"
                if (fromUser) {
                    prefs.fontSizeSp = sp
                    applyPreviewVisuals()
                    notifyOverlayConfigChanged()
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
            override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
        })

        // Font Style Selector
        binding.btnCycleFont.setOnClickListener {
            prefs.cycleFontStyle()
            syncUiFromPrefs()
            notifyOverlayConfigChanged()
        }

        // Vibration Toggle
        binding.switchVibration.setOnCheckedChangeListener { _, isChecked ->
            prefs.vibrateEnabled = isChecked
            notifyOverlayConfigChanged()
        }
    }

    private fun updateTarget(newTarget: Int) {
        prefs.target = newTarget
        syncUiFromPrefs()
        notifyOverlayConfigChanged()
    }

    private fun syncUiFromPrefs() {
        binding.tvPreviewDhikr.text = prefs.currentDhikrText()
        binding.tvPreviewCount.text = prefs.count.toString()
        val targetLabel = if (prefs.target > 0) "/ ${prefs.target}" else "∞"
        binding.tvPreviewTarget.text = targetLabel
        binding.tvTotalLifetime.text = getString(R.string.total_count_format, prefs.totalCount)

        binding.seekOpacity.progress = prefs.opacityPercent - 20
        binding.tvOpacityValue.text = "${prefs.opacityPercent}%"

        binding.seekBubbleSize.progress = prefs.bubbleScalePercent - 75
        binding.tvBubbleSizeValue.text = "${prefs.bubbleScalePercent}%"

        binding.seekFontSize.progress = prefs.fontSizeSp - 11
        binding.tvFontSizeValue.text = "${prefs.fontSizeSp}sp"

        binding.btnCycleFont.text = TasbihPrefs.FONT_STYLE_NAMES[prefs.fontStyleIndex]
        binding.switchVibration.isChecked = prefs.vibrateEnabled

        binding.chipTarget33.isChecked = prefs.target == 33
        binding.chipTarget99.isChecked = prefs.target == 99
        binding.chipTarget100.isChecked = prefs.target == 100
        binding.chipTargetUnlimited.isChecked = prefs.target == 0

        applyPreviewVisuals()
    }

    private fun applyPreviewVisuals() {
        binding.cardPreviewTasbih.alpha = prefs.opacityPercent / 100f
        binding.tvPreviewDhikr.typeface = prefs.currentTypeface()
        binding.tvPreviewDhikr.textSize = prefs.fontSizeSp.toFloat()
    }

    private fun updatePermissionAndServiceUi() {
        val hasPermission = Settings.canDrawOverlays(this)
        if (hasPermission) {
            binding.tvPermissionStatus.text = getString(R.string.permission_granted_label)
            binding.tvPermissionStatus.setTextColor(ContextCompat.getColor(this, R.color.tasbih_emerald))
            binding.btnGrantPermission.isEnabled = false
            binding.btnGrantPermission.alpha = 0.55f
        } else {
            binding.tvPermissionStatus.text = getString(R.string.permission_missing_label)
            binding.tvPermissionStatus.setTextColor(ContextCompat.getColor(this, R.color.tasbih_amber))
            binding.btnGrantPermission.isEnabled = true
            binding.btnGrantPermission.alpha = 1.0f
        }

        if (prefs.isOverlayActive && hasPermission) {
            binding.btnToggleOverlay.text = getString(R.string.stop_floating_tasbih)
            binding.btnToggleOverlay.setBackgroundColor(ContextCompat.getColor(this, R.color.tasbih_danger))
        } else {
            binding.btnToggleOverlay.text = getString(R.string.start_floating_tasbih)
            binding.btnToggleOverlay.setBackgroundColor(ContextCompat.getColor(this, R.color.tasbih_emerald))
        }
    }

    private fun openOverlayPermissionSettings() {
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:$packageName")
        )
        overlayPermissionLauncher.launch(intent)
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    private fun startFloatingService() {
        requestNotificationPermissionIfNeeded()
        val intent = Intent(this, FloatingOverlayService::class.java).apply {
            action = FloatingOverlayService.ACTION_START
        }
        ContextCompat.startForegroundService(this, intent)
        prefs.isOverlayActive = true
        updatePermissionAndServiceUi()
    }

    private fun stopFloatingService() {
        val intent = Intent(this, FloatingOverlayService::class.java)
        stopService(intent)
        prefs.isOverlayActive = false
        updatePermissionAndServiceUi()
    }

    private fun notifyOverlayConfigChanged() {
        val updateIntent = Intent(FloatingOverlayService.ACTION_UPDATE_CONFIG).apply {
            setPackage(packageName)
        }
        sendBroadcast(updateIntent)
    }
}
