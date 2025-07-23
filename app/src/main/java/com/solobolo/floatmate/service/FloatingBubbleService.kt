package com.solobolo.floatmate.service

import android.R
import android.app.*
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.util.DisplayMetrics
import android.util.Log
import android.view.*
import android.widget.FrameLayout
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.core.app.NotificationCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.solobolo.floatmate.MainActivity
import com.solobolo.floatmate.service.bubble.BubbleView
import com.solobolo.floatmate.service.bubble.ExpandedBubbleView
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import javax.inject.Inject
import kotlin.math.abs

@AndroidEntryPoint
class FloatingBubbleService : Service() {

    companion object {
        const val NOTIFICATION_ID = 1001
        const val CHANNEL_ID = "floatmate_service"
        var isRunning = false
            private set
        private const val TAG = "FloatMateDrag"
    }

    @Inject
    lateinit var sharedPrefs: FloatMateSharedPrefs

    private lateinit var windowManager: WindowManager
    private var bubbleView: View? = null
    private var expandedView: ComposeView? = null

    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    // Custom lifecycle owner for overlay windows
    private val overlayLifecycleOwner = OverlayLifecycleOwner()

    // Touch handling variables
    private var initialX = 0
    private var initialY = 0
    private var initialTouchX = 0f
    private var initialTouchY = 0f
    private var lastActionDownTime = 0L
    private var isDragging = false

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "=== SERVICE ONCREATE CALLED ===")
        isRunning = true

        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification())

        overlayLifecycleOwner.onCreate()
        Log.d(TAG, "Service setup complete")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "=== ON START COMMAND ===")
        if (bubbleView == null) {
            Log.d(TAG, "Creating new bubble view")
            createBubbleView()
        } else {
            Log.d(TAG, "Bubble view already exists")
        }
        overlayLifecycleOwner.onStart()
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "=== SERVICE DESTROY ===")
        isRunning = false
        serviceScope.cancel()
        removeBubbleView()
        removeExpandedView()
        overlayLifecycleOwner.onDestroy()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "FloatMate Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps FloatMate bubble active"
            }

            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("FloatMate Active")
            .setContentText("Tap to open settings")
            .setSmallIcon(R.drawable.ic_dialog_info)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun createBubbleView() {
        Log.d(TAG, "=== CREATING BUBBLE VIEW ===")

        // Get screen dimensions for debugging
        val displayMetrics = DisplayMetrics()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val display = windowManager.defaultDisplay
            display.getRealMetrics(displayMetrics)
        } else {
            @Suppress("DEPRECATION")
            windowManager.defaultDisplay.getMetrics(displayMetrics)
        }
        Log.d(TAG, "Screen size: ${displayMetrics.widthPixels} x ${displayMetrics.heightPixels}")

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                WindowManager.LayoutParams.TYPE_PHONE
            },
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = sharedPrefs.bubbleX
            y = sharedPrefs.bubbleY
            Log.d(TAG, "Initial bubble position: ($x, $y)")
        }

        val container = FrameLayout(this)
        val composeView = ComposeView(this).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)

            // REMOVED THE CLICKABLE FROM COMPOSE - handle all touches natively
            setContent {
                BubbleView(
                    isDragging = isDragging
                )
            }
        }

        container.addView(composeView)
        bubbleView = container

        container.setViewTreeLifecycleOwner(overlayLifecycleOwner)
        container.setViewTreeSavedStateRegistryOwner(overlayLifecycleOwner)

        // Now the touch listener should work properly
        container.setOnTouchListener { view, event ->
            Log.d(TAG, "=== TOUCH EVENT: ${event.action} ===")
            Log.d(TAG, "Raw coordinates: (${event.rawX}, ${event.rawY})")
            Log.d(TAG, "Current bubble position: (${params.x}, ${params.y})")

            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    Log.d(TAG, "ACTION_DOWN detected")
                    lastActionDownTime = System.currentTimeMillis()
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    isDragging = false

                    // Update UI to show dragging state
                    updateBubbleUI()

                    Log.d(TAG, "Stored initial values - X: $initialX, Y: $initialY, TouchX: $initialTouchX, TouchY: $initialTouchY")
                    true
                }

                MotionEvent.ACTION_MOVE -> {
                    Log.d(TAG, "ACTION_MOVE detected")
                    val deltaX = event.rawX - initialTouchX
                    val deltaY = event.rawY - initialTouchY
                    Log.d(TAG, "Delta - X: $deltaX, Y: $deltaY")

                    if (!isDragging && (abs(deltaX) > 10 || abs(deltaY) > 10)) {
                        isDragging = true
                        Log.d(TAG, "DRAGGING STARTED!")
                        updateBubbleUI()
                    }

                    if (isDragging) {
                        val newX = initialX + deltaX.toInt()
                        val newY = initialY + deltaY.toInt()

                        Log.d(TAG, "Moving bubble to: ($newX, $newY)")

                        params.x = newX
                        params.y = newY

                        try {
                            windowManager.updateViewLayout(container, params)
                            Log.d(TAG, "Layout updated successfully")
                        } catch (e: Exception) {
                            Log.e(TAG, "ERROR updating layout: ${e.message}", e)
                        }
                    }
                    true
                }

                MotionEvent.ACTION_UP -> {
                    Log.d(TAG, "ACTION_UP detected")
                    val clickDuration = System.currentTimeMillis() - lastActionDownTime
                    val deltaX = event.rawX - initialTouchX
                    val deltaY = event.rawY - initialTouchY
                    val moved = abs(deltaX) > 10 || abs(deltaY) > 10

                    Log.d(TAG, "Click duration: ${clickDuration}ms, moved: $moved, isDragging: $isDragging")

                    if (clickDuration < 200 && !moved && !isDragging) {
                        Log.d(TAG, "CLICK DETECTED - showing expanded view")
                        showExpandedView()
                    } else if (isDragging) {
                        Log.d(TAG, "DRAG ENDED - saving position")
                        sharedPrefs.bubbleX = params.x
                        sharedPrefs.bubbleY = params.y
                        Log.d(TAG, "Position saved: (${params.x}, ${params.y})")
                    }

                    isDragging = false
                    updateBubbleUI()
                    true
                }

                else -> {
                    Log.d(TAG, "OTHER touch event: ${event.action}")
                    false
                }
            }
        }

        try {
            windowManager.addView(container, params)
            Log.d(TAG, "BUBBLE VIEW ADDED TO WINDOW MANAGER SUCCESSFULLY")
            overlayLifecycleOwner.onResume()
        } catch (e: Exception) {
            Log.e(TAG, "FAILED TO ADD BUBBLE VIEW", e)
        }
    }

    private fun updateBubbleUI() {
        // Update the Compose UI to reflect current dragging state
        (bubbleView as? FrameLayout)?.let { container ->
            val composeView = container.getChildAt(0) as? ComposeView
            composeView?.setContent {
                BubbleView(
                    isDragging = isDragging
                )
            }
        }
    }

    private fun showExpandedView() {
        Log.d(TAG, "=== SHOWING EXPANDED VIEW ===")
        if (expandedView != null) {
            Log.d(TAG, "Expanded view already visible")
            return
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                WindowManager.LayoutParams.TYPE_PHONE
            },
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.CENTER
            width = (resources.displayMetrics.widthPixels * 0.9).toInt()
        }

        val composeView = ComposeView(this).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)

            setContent {
                ExpandedBubbleView(
                    onDismiss = { hideExpandedView() },
                    sharedPrefs = sharedPrefs
                )
            }
        }

        expandedView = composeView
        composeView.setViewTreeLifecycleOwner(overlayLifecycleOwner)
        composeView.setViewTreeSavedStateRegistryOwner(overlayLifecycleOwner)

        composeView.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_OUTSIDE) {
                hideExpandedView()
                true
            } else {
                false
            }
        }

        try {
            windowManager.addView(composeView, params)
            hideBubble()
            Log.d(TAG, "Expanded view shown")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to show expanded view", e)
        }
    }

    private fun hideExpandedView() {
        Log.d(TAG, "Hiding expanded view")
        expandedView?.let {
            try {
                windowManager.removeView(it)
                expandedView = null
                Log.d(TAG, "Expanded view hidden")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to hide expanded view", e)
            }
        }
        showBubble()
    }

    private fun hideBubble() {
        bubbleView?.visibility = View.GONE
        Log.d(TAG, "Bubble hidden")
    }

    private fun showBubble() {
        bubbleView?.visibility = View.VISIBLE
        Log.d(TAG, "Bubble shown")
    }

    private fun removeBubbleView() {
        Log.d(TAG, "Removing bubble view")
        bubbleView?.let {
            try {
                windowManager.removeView(it)
                bubbleView = null
                Log.d(TAG, "Bubble view removed")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to remove bubble view", e)
            }
        }
    }

    private fun removeExpandedView() {
        Log.d(TAG, "Removing expanded view")
        expandedView?.let {
            try {
                windowManager.removeView(it)
                expandedView = null
                Log.d(TAG, "Expanded view removed")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to remove expanded view", e)
            }
        }
    }

    // Custom lifecycle owner for overlay windows
    private class OverlayLifecycleOwner : LifecycleOwner, SavedStateRegistryOwner {
        private val lifecycleRegistry = LifecycleRegistry(this)
        private val savedStateRegistryController = SavedStateRegistryController.create(this)

        override val lifecycle: Lifecycle = lifecycleRegistry
        override val savedStateRegistry: SavedStateRegistry = savedStateRegistryController.savedStateRegistry

        init {
            savedStateRegistryController.performRestore(null)
        }

        fun onCreate() {
            lifecycleRegistry.currentState = Lifecycle.State.CREATED
        }

        fun onStart() {
            lifecycleRegistry.currentState = Lifecycle.State.STARTED
        }

        fun onResume() {
            lifecycleRegistry.currentState = Lifecycle.State.RESUMED
        }

        fun onDestroy() {
            lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
        }
    }
}