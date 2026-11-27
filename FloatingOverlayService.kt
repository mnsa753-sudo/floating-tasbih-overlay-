package com.floatingtasbih.app

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import android.util.TypedValue
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import kotlin.math.abs

class FloatingOverlayService : Service() {

    private lateinit var windowManager: WindowManager
    private lateinit var prefs: TasbihPrefs
    private var overlayView: View? = null
    private lateinit var layoutParams: WindowManager.LayoutParams

    private lateinit var bubbleRoot: LinearLayout
    private lateinit var tvDhikr: TextView
    private lateinit var tvCount: TextView
    private lateinit var tvTarget: TextView
    private lateinit var panelQuickActions: LinearLayout
    private lateinit var btnToggleMenu: ImageButton

    private var isMenuExpanded = false

    private val configReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == ACTION_UPDATE_CONFIG) {
                applyOverlayAppearance()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        prefs = TasbihPrefs(this)
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification())

        val filter = IntentFilter(ACTION_UPDATE_CONFIG)
        ContextCompat.registerReceiver(
            this,
            configReceiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED
        )

        if (Settings.canDrawOverlays(this)) {
            createOverlayBubble()
            prefs.isOverlayActive = true
        } else {
            stopSelf()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_UPDATE_CONFIG -> {
                applyOverlayAppearance()
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    @SuppressLint("InflateParams", "ClickableViewAccessibility")
    private fun createOverlayBubble() {
        if (overlayView != null) return

        val view = LayoutInflater.from(this).inflate(R.layout.overlay_tasbih_bubble, null)
        overlayView = view

        bubbleRoot = view.findViewById(R.id.bubbleRoot)
        tvDhikr = view.findViewById(R.id.tvOverlayDhikr)
        tvCount = view.findViewById(R.id.tvOverlayCount)
        tvTarget = view.findViewById(R.id.tvOverlayTarget)
        panelQuickActions = view.findViewById(R.id.panelQuickActions)
        btnToggleMenu = view.findViewById(R.id.btnToggleMenu)

        layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = prefs.overlayX
            y = prefs.overlayY
        }

        setupTouchAndDrag(view)
        setupQuickActionButtons(view)
        applyOverlayAppearance()

        windowManager.addView(view, layoutParams)
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupTouchAndDrag(root: View) {
        val touchSlop = ViewConfiguration.get(this).scaledTouchSlop
        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f
        var isDragging = false

        val tapArea = root.findViewById<View>(R.id.tapCountArea)
        tapArea.setOnTouchListener { v, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = layoutParams.x
                    initialY = layoutParams.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    isDragging = false
                    v.animate().scaleX(0.96f).scaleY(0.96f).setDuration(80).start()
                    true
                }

                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - initialTouchX).toInt()
                    val dy = (event.rawY - initialTouchY).toInt()
                    if (!isDragging && (abs(dx) > touchSlop || abs(dy) > touchSlop)) {
                        isDragging = true
                        v.animate().scaleX(1f).scaleY(1f).setDuration(80).start()
                    }
                    if (isDragging) {
                        val metrics = resources.displayMetrics
                        val maxX = (metrics.widthPixels - root.width).coerceAtLeast(0)
                        val maxY = (metrics.heightPixels - root.height).coerceAtLeast(0)
                        layoutParams.x = (initialX + dx).coerceIn(0, maxX)
                        layoutParams.y = (initialY + dy).coerceIn(0, maxY)
                        windowManager.updateViewLayout(root, layoutParams)
                    }
                    true
                }

                MotionEvent.ACTION_UP -> {
                    v.animate().scaleX(1f).scaleY(1f).setDuration(110).start()
                    if (isDragging) {
                        prefs.overlayX = layoutParams.x
                        prefs.overlayY = layoutParams.y
                    } else {
                        incrementTasbih()
                    }
                    true
                }

                MotionEvent.ACTION_CANCEL -> {
                    v.animate().scaleX(1f).scaleY(1f).setDuration(100).start()
                    true
                }

                else -> false
            }
        }
    }

    private fun setupQuickActionButtons(root: View) {
        btnToggleMenu.setOnClickListener {
            isMenuExpanded = !isMenuExpanded
            panelQuickActions.visibility = if (isMenuExpanded) View.VISIBLE else View.GONE
            btnToggleMenu.rotation = if (isMenuExpanded) 90f else 0f
        }

        // Next Dhikr
        root.findViewById<View>(R.id.btnOverlayNextDhikr).setOnClickListener {
            prefs.nextDhikr()
            applyOverlayAppearance()
            broadcastCountUpdated()
        }

        // Cycle Font Style
        root.findViewById<View>(R.id.btnOverlayFontStyle).setOnClickListener {
            prefs.cycleFontStyle()
            applyOverlayAppearance()
            broadcastCountUpdated()
        }

        // Cycle Font Size
        root.findViewById<View>(R.id.btnOverlayFontSize).setOnClickListener {
            prefs.cycleFontSize()
            applyOverlayAppearance()
            broadcastCountUpdated()
        }

        // Cycle Bubble Scale
        root.findViewById<View>(R.id.btnOverlayBubbleSize).setOnClickListener {
            prefs.cycleBubbleScale()
            applyOverlayAppearance()
            broadcastCountUpdated()
        }

        // Reset Counter
        root.findViewById<View>(R.id.btnOverlayReset).setOnClickListener {
            prefs.count = 0
            applyOverlayAppearance()
            broadcastCountUpdated()
        }

        // Open MainActivity
        root.findViewById<View>(R.id.btnOverlayOpenApp).setOnClickListener {
            val launchIntent = Intent(this, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            }
            startActivity(launchIntent)
        }

        // Close Overlay
        root.findViewById<View>(R.id.btnOverlayClose).setOnClickListener {
            stopSelf()
        }
    }

    private fun incrementTasbih() {
        prefs.count += 1
        prefs.totalCount += 1

        val reachedTarget = prefs.target > 0 && prefs.count % prefs.target == 0
        triggerHapticFeedback(isMilestone = reachedTarget)

        tvCount.animate()
            .scaleX(1.18f)
            .scaleY(1.18f)
            .setDuration(75)
            .withEndAction {
                tvCount.animate().scaleX(1f).scaleY(1f).setDuration(110).start()
            }
            .start()

        applyOverlayAppearance()
        broadcastCountUpdated()
    }

    private fun applyOverlayAppearance() {
        val view = overlayView ?: return

        // Update text & counter values
        tvDhikr.text = prefs.currentDhikrText()
        tvCount.text = prefs.count.toString()
        tvTarget.text = if (prefs.target > 0) "/ ${prefs.target}" else "∞"

        // Apply Font Style & Font Size
        tvDhikr.typeface = prefs.currentTypeface()
        val scaleFactor = prefs.bubbleScalePercent / 100f
        tvDhikr.setTextSize(TypedValue.COMPLEX_UNIT_SP, prefs.fontSizeSp * scaleFactor)
        tvCount.setTextSize(TypedValue.COMPLEX_UNIT_SP, 26f * scaleFactor)
        tvTarget.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f * scaleFactor)

        // Apply Bubble Scale via padding and minimum width
        val padH = (16 * resources.displayMetrics.density * scaleFactor).toInt()
        val padV = (12 * resources.displayMetrics.density * scaleFactor).toInt()
        bubbleRoot.setPadding(padH, padV, padH, padV)
        bubbleRoot.minimumWidth = (156 * resources.displayMetrics.density * scaleFactor).toInt()

        // Apply Dark Opacity (Background translucency while keeping text crisp)
        val alphaFloat = prefs.opacityPercent / 100f
        bubbleRoot.background?.mutate()?.alpha = (alphaFloat * 255).toInt().coerceIn(45, 255)
        view.alpha = (0.55f + (alphaFloat * 0.45f)).coerceIn(0.45f, 1.0f)
    }

    private fun triggerHapticFeedback(isMilestone: Boolean) {
        if (!prefs.vibrateEnabled) return
        val duration = if (isMilestone) 70L else 22L

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val manager = getSystemService(VIBRATOR_MANAGER_SERVICE) as VibratorManager
            manager.defaultVibrator.vibrate(
                VibrationEffect.createOneShot(duration, VibrationEffect.DEFAULT_AMPLITUDE)
            )
        } else {
            @Suppress("DEPRECATION")
            val vibrator = getSystemService(VIBRATOR_SERVICE) as Vibrator
            vibrator.vibrate(
                VibrationEffect.createOneShot(duration, VibrationEffect.DEFAULT_AMPLITUDE)
            )
        }
    }

    private fun broadcastCountUpdated() {
        val intent = Intent(ACTION_COUNT_UPDATED).apply {
            setPackage(packageName)
        }
        sendBroadcast(intent)
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = getString(R.string.notification_channel_desc)
            setShowBadge(false)
        }
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        val openAppIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, FloatingOverlayService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_tasbih)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(R.string.notification_active_text))
            .setContentIntent(openAppIntent)
            .setOngoing(true)
            .addAction(0, getString(R.string.stop_action), stopIntent)
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        prefs.isOverlayActive = false
        overlayView?.let { view ->
            try {
                windowManager.removeViewImmediate(view)
            } catch (_: Exception) {
            }
            overlayView = null
        }
        try {
            unregisterReceiver(configReceiver)
        } catch (_: IllegalArgumentException) {
        }
        sendBroadcast(Intent(ACTION_SERVICE_STOPPED).apply { setPackage(packageName) })
    }

    companion object {
        const val ACTION_START = "com.floatingtasbih.app.ACTION_START"
        const val ACTION_STOP = "com.floatingtasbih.app.ACTION_STOP"
        const val ACTION_UPDATE_CONFIG = "com.floatingtasbih.app.ACTION_UPDATE_CONFIG"
        const val ACTION_COUNT_UPDATED = "com.floatingtasbih.app.ACTION_COUNT_UPDATED"
        const val ACTION_SERVICE_STOPPED = "com.floatingtasbih.app.ACTION_SERVICE_STOPPED"

        private const val CHANNEL_ID = "floating_tasbih_overlay_channel"
        private const val NOTIFICATION_ID = 1001
    }
}
