package com.solobolo.floatmate.service

//noinspection SuspiciousImport
import android.R
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.util.DisplayMetrics
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.core.animation.doOnEnd
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
import com.solobolo.floatmate.features.home.HomeViewModel
import com.solobolo.floatmate.service.bubble.BubbleView
import com.solobolo.floatmate.service.bubble.DeleteZoneView
import com.solobolo.floatmate.service.bubble.ExpandedBubbleView
import com.solobolo.floatmate.ui.theme.FloatMateTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
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
        private const val EDGE_MARGIN = 8
    }

    @Inject
    lateinit var sharedPrefs: FloatMateSharedPrefs

    private lateinit var windowManager: WindowManager
    private var bubbleView: View? = null
    private var expandedView: ComposeView? = null
    private var deleteZoneView: ComposeView? = null
    private var bubbleParams: WindowManager.LayoutParams? = null
    private var screenWidth = 0
    private var screenHeight = 0
    private var bubbleSize = 60
    private var edgeMarginPx = 0
    private var deleteZoneHeight = 120
    private var deleteButtonSize = 100

    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val overlayLifecycleOwner = OverlayLifecycleOwner()

    private var initialX = 0
    private var initialY = 0
    private var initialTouchX = 0f
    private var initialTouchY = 0f
    private var lastActionDownTime = 0L
    private var isDragging = false

    private var snapAnimator: ValueAnimator? = null
    private var isInDeleteZoneState = false

    override fun onCreate() {
        super.onCreate()
        isRunning = true

        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        initializeScreenDimensions()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification())

        overlayLifecycleOwner.onCreate()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (bubbleView == null) {
            createBubbleView()
        }
        overlayLifecycleOwner.onStart()
        return START_STICKY
    }

    private fun initializeScreenDimensions() {
        val displayMetrics = DisplayMetrics()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val display = windowManager.defaultDisplay
            display.getRealMetrics(displayMetrics)
        } else {
            @Suppress("DEPRECATION")
            windowManager.defaultDisplay.getMetrics(displayMetrics)
        }
        screenWidth = displayMetrics.widthPixels
        screenHeight = displayMetrics.heightPixels
        val density = displayMetrics.density
        edgeMarginPx = (EDGE_MARGIN * density).toInt()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        snapAnimator?.cancel()
        serviceScope.cancel()
        removeBubbleView()
        removeExpandedView()
        overlayLifecycleOwner.onDestroy()
        bubbleView = null
        expandedView = null
        deleteZoneView = null
        bubbleParams = null
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
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("FloatMate Active")
            .setContentText("Tap to open settings")
            .setSmallIcon(R.drawable.ic_dialog_info)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun createBubbleView() {
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
            x = constrainToScreenBounds(sharedPrefs.bubbleX, true)
            y = constrainToScreenBounds(sharedPrefs.bubbleY, false)
        }

        bubbleParams = params

        val container = FrameLayout(this)
        val composeView = ComposeView(this).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                FloatMateTheme {
                    BubbleView(
                        isDragging = isDragging
                    )
                }
            }
        }

        container.addView(composeView)
        bubbleView = container

        container.setViewTreeLifecycleOwner(overlayLifecycleOwner)
        container.setViewTreeSavedStateRegistryOwner(overlayLifecycleOwner)

        container.setOnTouchListener { view, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    snapAnimator?.cancel()
                    lastActionDownTime = System.currentTimeMillis()
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    isDragging = false
                    updateBubbleUI()
                    true
                }

                MotionEvent.ACTION_MOVE -> {
                    val deltaX = event.rawX - initialTouchX
                    val deltaY = event.rawY - initialTouchY

                    if (!isDragging && (abs(deltaX) > 10 || abs(deltaY) > 10)) {
                        isDragging = true
                        showDeleteZone()
                        updateBubbleUI()
                    }

                    if (isDragging) {
                        val newX = constrainToScreenBounds(initialX + deltaX.toInt(), true)
                        val newY = constrainToScreenBounds(initialY + deltaY.toInt(), false)
                        params.x = newX
                        params.y = newY

                        val wasInDeleteZone = isInDeleteZoneState
                        isInDeleteZoneState = isInDeleteZone(newX, newY)

                        if (wasInDeleteZone != isInDeleteZoneState) {
                            updateDeleteZone()
                        }

                        try {
                            windowManager.updateViewLayout(container, params)
                        } catch (e: Exception) {
                            Log.e(TAG, "ERROR updating layout: ${e.message}", e)
                        }
                    }
                    true
                }

                MotionEvent.ACTION_UP -> {
                    val clickDuration = System.currentTimeMillis() - lastActionDownTime
                    val deltaX = event.rawX - initialTouchX
                    val deltaY = event.rawY - initialTouchY
                    val moved = abs(deltaX) > 10 || abs(deltaY) > 10

                    if (clickDuration < 200 && !moved && !isDragging) {
                        showExpandedView()
                    } else if (isDragging) {
                        if (isInDeleteZone(params.x, params.y)) {
                            hideDeleteZone()
                            HomeViewModel.instance?.onBubbleDeleted()
                            stopSelf()
                            return@setOnTouchListener true
                        } else {
                            snapToNearestEdge()
                        }
                        hideDeleteZone()
                    }
                    isDragging = false
                    isInDeleteZoneState = false
                    updateBubbleUI()
                    true
                }

                else -> {
                    false
                }
            }
        }

        try {
            windowManager.addView(container, params)
            overlayLifecycleOwner.onResume()
        } catch (e: Exception) {
            Log.e(TAG, "FAILED TO ADD BUBBLE VIEW", e)
        }
    }

    private fun constrainToScreenBounds(position: Int, isX: Boolean): Int {
        return if (isX) {
            val bubbleWidthPx = (bubbleSize * resources.displayMetrics.density).toInt()
            position.coerceIn(edgeMarginPx, screenWidth - bubbleWidthPx - edgeMarginPx)
        } else {
            val bubbleHeightPx = (bubbleSize * resources.displayMetrics.density).toInt()
            position.coerceIn(0, screenHeight - bubbleHeightPx)
        }
    }

    private fun snapToNearestEdge() {
        bubbleParams?.let { params ->
            val bubbleWidthPx = (bubbleSize * resources.displayMetrics.density).toInt()
            val bubbleCenterX = params.x + bubbleWidthPx / 2
            val screenCenterX = screenWidth / 2
            val targetX = if (bubbleCenterX < screenCenterX) {
                edgeMarginPx
            } else {
                screenWidth - bubbleWidthPx - edgeMarginPx
            }
            animateToPosition(params.x, targetX, params.y)
        }
    }

    private fun isInDeleteZone(bubbleX: Int, bubbleY: Int): Boolean {
        val bubbleWidthPx = (bubbleSize * resources.displayMetrics.density).toInt()
        val bubbleHeightPx = (bubbleSize * resources.displayMetrics.density).toInt()
        val deleteButtonSizePx = (deleteButtonSize * resources.displayMetrics.density).toInt()
        val deleteZoneHeightPx = (deleteZoneHeight * resources.displayMetrics.density).toInt()

        val bubbleCenterX = bubbleX + bubbleWidthPx / 2
        val bubbleCenterY = bubbleY + bubbleHeightPx / 2

        val deleteButtonCenterX = screenWidth / 2
        val deleteButtonCenterY = screenHeight - deleteZoneHeightPx / 2

        val deltaX = bubbleCenterX - deleteButtonCenterX
        val deltaY = bubbleCenterY - deleteButtonCenterY
        val distance = kotlin.math.sqrt((deltaX * deltaX + deltaY * deltaY).toDouble())
        val triggerRadius = (deleteButtonSizePx / 2) * 1.5

        return distance <= triggerRadius
    }

    private fun showDeleteZone() {
        if (deleteZoneView != null) {
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
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.BOTTOM
            height = (deleteZoneHeight * resources.displayMetrics.density).toInt()
        }

        val composeView = ComposeView(this).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                FloatMateTheme {
                    DeleteZoneView(
                        isHighlighted = isInDeleteZoneState
                    )
                }
            }
        }

        deleteZoneView = composeView
        composeView.setViewTreeLifecycleOwner(overlayLifecycleOwner)
        composeView.setViewTreeSavedStateRegistryOwner(overlayLifecycleOwner)

        try {
            windowManager.addView(composeView, params)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to show delete zone", e)
        }
    }

    private fun updateDeleteZone() {
        deleteZoneView?.setContent {
            FloatMateTheme {
                DeleteZoneView(
                    isHighlighted = isInDeleteZoneState
                )
            }
        }
    }

    private fun hideDeleteZone() {
        deleteZoneView?.let {
            try {
                windowManager.removeView(it)
                deleteZoneView = null
            } catch (e: Exception) {
                Log.e(TAG, "Failed to hide delete zone", e)
            }
        }
    }

    private fun animateToPosition(fromX: Int, toX: Int, y: Int) {
        snapAnimator?.cancel()
        snapAnimator = ValueAnimator.ofInt(fromX, toX).apply {
            duration = 300
            interpolator = DecelerateInterpolator()

            addUpdateListener { animator ->
                val currentX = animator.animatedValue as Int
                bubbleParams?.let { params ->
                    params.x = currentX
                    params.y = y

                    try {
                        bubbleView?.let { view ->
                            windowManager.updateViewLayout(view, params)
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "ERROR during snap animation: ${e.message}", e)
                        cancel()
                    }
                }
            }
            doOnEnd {
                bubbleParams?.let { params ->
                    sharedPrefs.bubbleX = params.x
                    sharedPrefs.bubbleY = params.y
                }
            }
            start()
        }
    }

    private fun updateBubbleUI() {
        (bubbleView as? FrameLayout)?.let { container ->
            val composeView = container.getChildAt(0) as? ComposeView
            composeView?.setContent {
                FloatMateTheme {
                    BubbleView(
                        isDragging = isDragging
                    )
                }
            }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun showExpandedView() {
        if (expandedView != null) {
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
                FloatMateTheme {
                    ExpandedBubbleView(
                        onDismiss = { hideExpandedView() },
                        sharedPrefs = sharedPrefs
                    )
                }
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
        } catch (e: Exception) {
            Log.e(TAG, "Failed to show expanded view", e)
        }
    }

    private fun hideExpandedView() {
        expandedView?.let {
            try {
                windowManager.removeView(it)
                expandedView = null
            } catch (e: Exception) {
                Log.e(TAG, "Failed to hide expanded view", e)
            }
        }
        showBubble()
    }

    private fun hideBubble() {
        bubbleView?.visibility = View.GONE
    }

    private fun showBubble() {
        bubbleView?.visibility = View.VISIBLE
    }

    private fun removeBubbleView() {
        snapAnimator?.cancel()
        hideDeleteZone()

        bubbleView?.let { view ->
            try {
                (view as? FrameLayout)?.let { container ->
                    val composeView = container.getChildAt(0) as? ComposeView
                    composeView?.setContent { /* Empty content */ }
                    container.removeAllViews()
                }

                windowManager.removeView(view)
                bubbleView = null
                bubbleParams = null

            } catch (e: Exception) {
                Log.e(TAG, "Failed to remove bubble view", e)
            }
        }
    }

    private fun removeExpandedView() {
        expandedView?.let { view ->
            try {
                view.setContent { /* Empty content */ }
                windowManager.removeView(view)
                expandedView = null

            } catch (e: Exception) {
                Log.e(TAG, "Failed to remove expanded view", e)
            }
        }
    }

    private class OverlayLifecycleOwner : LifecycleOwner, SavedStateRegistryOwner {
        private val lifecycleRegistry = LifecycleRegistry(this)
        private val savedStateRegistryController = SavedStateRegistryController.create(this)

        override val lifecycle: Lifecycle = lifecycleRegistry
        override val savedStateRegistry: SavedStateRegistry =
            savedStateRegistryController.savedStateRegistry

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
