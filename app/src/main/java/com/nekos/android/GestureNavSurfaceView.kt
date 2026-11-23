package com.nekos.android

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.PorterDuff
import android.graphics.Rect
import android.graphics.RectF
import android.os.Bundle
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.os.Messenger
import java.lang.ref.WeakReference
import android.os.RemoteException
import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.annotation.RequiresApi

/**
 * Supplies the icon surface expected by Android's gesture-to-Home contract.
 *
 * The view is placed over the Favorite icon in Compose coordinates. System UI
 * receives the same destination rectangle plus this SurfaceView's SurfaceControl.
 */
@RequiresApi(Build.VERSION_CODES.Q)
internal class GestureNavSurfaceView(context: Context) : SurfaceView(context), SurfaceHolder.Callback {
    private var iconBitmap: Bitmap? = null
    private var destination: RectF? = null
    private var contract: GestureNavContractData? = null
    private var onFinished: (() -> Unit)? = null
    private var lastSentPosition: RectF? = null

    init {
        setZOrderOnTop(true)
        holder.setFormat(PixelFormat.TRANSLUCENT)
        holder.addCallback(this)
        isClickable = false
        isFocusable = false
    }

    fun configure(
        bitmap: Bitmap,
        destination: RectF,
        contract: GestureNavContractData,
        onFinished: () -> Unit
    ) {
        val changed = iconBitmap !== bitmap ||
            this.destination != destination ||
            this.contract !== contract
        iconBitmap = bitmap
        this.destination = RectF(destination)
        this.contract = contract
        this.onFinished = onFinished
        if (changed && holder.surface.isValid) {
            drawIcon()
            sendContract()
        }
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        lastSentPosition = null
        drawIcon()
        sendContract()
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        drawIcon()
        sendContract()
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        lastSentPosition = null
    }

    private fun drawIcon() {
        val bitmap = iconBitmap ?: return
        val surfaceHolder = holder
        if (!surfaceHolder.surface.isValid) return

        val canvas = try {
            surfaceHolder.lockHardwareCanvas()
        } catch (_: IllegalStateException) {
            null
        } ?: return

        try {
            canvas.drawColor(android.graphics.Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
            canvas.drawBitmap(
                bitmap,
                null,
                Rect(0, 0, width.coerceAtLeast(1), height.coerceAtLeast(1)),
                Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
            )
        } finally {
            surfaceHolder.unlockCanvasAndPost(canvas)
        }
    }

    internal fun dispatchContractFinished() {
        onFinished?.invoke()
    }

    private fun sendContract() {
        val request = contract ?: return
        val position = destination ?: return
        if (!holder.surface.isValid || position.isEmpty) return
        if (lastSentPosition == position) return

        val result = Bundle().apply {
            putParcelable(GestureNavContractData.EXTRA_ICON_POSITION, RectF(position))
            putParcelable(GestureNavContractData.EXTRA_ICON_SURFACE, surfaceControl)
            putParcelable(
                GestureNavContractData.EXTRA_ON_FINISH_CALLBACK,
                FinishCallbackReceiver.messageFor(this@GestureNavSurfaceView)
            )
        }
        val response = Message.obtain().apply {
            copyFrom(request.callback)
            data = result
        }

        try {
            response.replyTo?.send(response)
            lastSentPosition = RectF(position)
        } catch (_: RemoteException) {
            // The system may cancel the gesture before the surface is ready.
        }
    }
}


/** Shared finish receiver, mirroring Launcher3's weak-reference lifecycle. */
private object FinishCallbackReceiver : Handler.Callback {
    private var currentTarget = WeakReference<GestureNavSurfaceView>(null)
    val messenger = Messenger(Handler(Looper.getMainLooper(), this))

    fun messageFor(target: GestureNavSurfaceView): Message {
        currentTarget = WeakReference(target)
        return Message.obtain().apply { replyTo = messenger }
    }

    override fun handleMessage(message: Message): Boolean {
        currentTarget.get()?.dispatchContractFinished()
        currentTarget.clear()
        return true
    }
}
