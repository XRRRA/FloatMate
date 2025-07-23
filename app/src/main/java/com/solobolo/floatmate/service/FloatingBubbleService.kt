package com.solobolo.floatmate.service

import android.R
import android.app.*
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
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
        private const val TAG = "zebugger"
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

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service onCreate() called")
        isRunning = true

        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification())
        Log.d(TAG, "Foreground notification started")

        overlayLifecycleOwner.onCreate()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand() received")
        if (bubbleView == null) {
            Log.d(TAG, "bubbleView is null, creating bubble view")
            createBubbleView()
        } else {
            Log.d(TAG, "bubbleView already exists")
        }
        overlayLifecycleOwner.onStart()
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "Service onDestroy() called")
        isRunning = false
        serviceScope.cancel()
        removeBubbleView()
        removeExpandedView()
        overlayLifecycleOwner.onDestroy()
    }

    private fun createNotificationChannel() {
        Log.d(TAG, "Creating notification channel")
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
            Log.d(TAG, "Notification channel created")
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
            .setSmallIcon(R.drawable.ic_dialog_info) // Using system icon temporarily
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun createBubbleView() {
        Log.d(TAG, "Creating bubble view")

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
        }

        val container = FrameLayout(this)
        val composeView = ComposeView(this).apply {
            // Set composition strategy to prevent memory leaks
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)

            setContent {
                BubbleView(
                    onBubbleClick = {
                        Log.d(TAG, "Bubble clicked")
                        showExpandedView()
                    }
                )
            }
        }

        container.addView(composeView)
        bubbleView = container

        // Set lifecycle owner and saved state registry owner for Compose
        container.setViewTreeLifecycleOwner(overlayLifecycleOwner)
        container.setViewTreeSavedStateRegistryOwner(overlayLifecycleOwner)

        // Add touch listener for dragging
        container.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    lastActionDownTime = System.currentTimeMillis()
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    params.x = initialX + (event.rawX - initialTouchX).toInt()
                    params.y = initialY + (event.rawY - initialTouchY).toInt()
                    windowManager.updateViewLayout(container, params)
                    true
                }
                MotionEvent.ACTION_UP -> {
                    val clickDuration = System.currentTimeMillis() - lastActionDownTime
                    val moved = abs(event.rawX - initialTouchX) > 10 ||
                            abs(event.rawY - initialTouchY) > 10

                    if (clickDuration < 200 && !moved) {
                        // It's a click
                        Log.d(TAG, "Detected click on bubble")
                        composeView.performClick()
                    } else {
                        // Save position after drag
                        Log.d(TAG, "Bubble dragged to new position")
                        sharedPrefs.bubbleX = params.x
                        sharedPrefs.bubbleY = params.y
                    }
                    true
                }
                else -> false
            }
        }

        try {
            windowManager.addView(container, params)
            Log.d(TAG, "Bubble view added to window manager")
            overlayLifecycleOwner.onResume()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to add bubble view", e)
        }
    }

    private fun showExpandedView() {
        Log.d(TAG, "Showing expanded view")
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
            // Set composition strategy to prevent memory leaks
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)

            setContent {
                ExpandedBubbleView(
                    onDismiss = { hideExpandedView() },
                    sharedPrefs = sharedPrefs
                )
            }
        }

        expandedView = composeView

        // Set lifecycle owner and saved state registry owner
        composeView.setViewTreeLifecycleOwner(overlayLifecycleOwner)
        composeView.setViewTreeSavedStateRegistryOwner(overlayLifecycleOwner)

        // Handle outside touches
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