package com.ejl191.driveplayer

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.ejl191.driveplayer.ui.AppRoot
import com.ejl191.driveplayer.ui.AppTheme
import com.ejl191.driveplayer.ui.PlayerConnection

class MainActivity : ComponentActivity() {
    private lateinit var connection: PlayerConnection

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // The retro theme is always dark, so keep the system bar icons light.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        connection = PlayerConnection(this)
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 0)
        }
        setContent {
            AppTheme {
                AppRoot(connection)
            }
        }
    }

    override fun onStart() {
        super.onStart()
        connection.connect()
    }

    override fun onStop() {
        connection.release()
        super.onStop()
    }
}
