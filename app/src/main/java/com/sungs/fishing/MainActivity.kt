package com.sungs.fishing

import android.app.Activity
import android.os.Bundle
import android.view.WindowManager

@Suppress("DEPRECATION")
class MainActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 전체화면
        window.setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_FULLSCREEN
        )
        actionBar?.hide()

        // 작품을 화면에 올린다
        setContentView(FishingView(this))
    }
}
