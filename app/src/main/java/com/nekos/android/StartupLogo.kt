package com.nekos.android

import android.graphics.drawable.AnimatedImageDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.widget.ImageView
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView

@Composable
internal fun StartupLogo(modifier: Modifier = Modifier) {
    AndroidView(
        modifier = modifier,
        factory = { context ->
            ImageView(context).apply {
                scaleType = ImageView.ScaleType.FIT_CENTER
                setImageResource(R.drawable.startup_logo)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    (drawable as? AnimatedImageDrawable)?.start()
                }
            }
        },
        update = { view ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                (view.drawable as? AnimatedImageDrawable)?.start()
            }
        }
    )
}
