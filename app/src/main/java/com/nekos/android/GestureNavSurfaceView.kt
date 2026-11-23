package com.nekos.android

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.RectF
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.os.Messenger
import android.view.SurfaceHolder
import android.view.SurfaceView

/**
 * A transparent SurfaceView that exposes the Favorite icon as a SurfaceControl
 * target for Android's gesture-navigation return animation.
 */
internal class GestureNavSurfaceView(
    context: Context,
    bitmap: Bitmap,
    contract: GestureNavContractData,
    private val onFinished: () -> Unit
) : SurfaceView(context), SurfaceHolder.Callback2 {

    private var iconBitmap: Bitmap = bitmap
    private var gestureContract: GestureNavContractData = contract

    private val finishMessenger = Messenger(
        Handler(Looper.getMainLooper()) { message ->
            if (message.what == FINISH_MESSAGE) {
                onFinished()
                true
            } else {
                false
            }
        }
    )
    private val finishCallback = Message.obtain().apply {
        what = FINISH_MESSAGE
        replyTo = finishMessenger
    }

    init {
        setZOrderOnTop(true)
        holder.setFormat(PixelFormat.TRANSLUCENT)
        holder.addCallback(this)
    }

    fun updateBitmap(bitmap: Bitmap) {
        if (iconBitmap !== bitmap) {
            iconBitmap = bitmap
            drawIcon()
        }
    }

    fun updateContract(contract: GestureNavContractData) {
        gestureContract = contract
        sendIconInfo()
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        if (changed) {
            drawIcon()
            sendIconInfo()
        }
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        drawIcon()
        sendIconInfo()
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        drawIcon()
        sendIconInfo()
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) = Unit

    override fun surfaceRedrawNeeded(holder: SurfaceHolder) {
        drawIcon()
        sendIconInfo()
    }

    private fun drawIcon() {
        if (!holder.surface.isValid || width <= 0 || height <= 0) return
        val canvas: Canvas = try {
            holder.lockHardwareCanvas()
        } catch (_: IllegalArgumentException) {
            null
        } ?: return

        try {
            canvas.drawColor(android.graphics.Color.TRANSPARENT, android.graphics.PorterDuff.Mode.CLEAR)
            canvas.drawBitmap(
                iconBitmap,
                null,
                Rect(0, 0, width, height),
                Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
            )
        } finally {
            holder.unlockCanvasAndPost(canvas)
        }
    }

    private fun sendIconInfo() {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.R ||
            !holder.surface.isValid || width <= 0 || height <= 0
        ) return

        val location = IntArray(2)
        getLocationInWindow(location)
        val position = RectF(
            location[0].toFloat(),
            location[1].toFloat(),
            (location[0] + width).toFloat(),
            (location[1] + height).toFloat()
        )
        gestureContract.sendEndPosition(position, surfaceControl, finishCallback)
    }

    private companion object {
        const val FINISH_MESSAGE = 0
    }
}
