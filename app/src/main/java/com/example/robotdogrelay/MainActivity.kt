package com.example.robotdogrelay

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.net.Inet4Address
import java.net.NetworkInterface

class MainActivity : AppCompatActivity() {

    private lateinit var etPiIp: EditText
    private lateinit var tvInfo: TextView
    private lateinit var webView: WebView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        requestNotificationPermissionIfNeeded()

        etPiIp = findViewById(R.id.etPiIp)
        tvInfo = findViewById(R.id.tvInfo)
        webView = findViewById(R.id.webView)

        val prefs = getSharedPreferences("relay_prefs", Context.MODE_PRIVATE)
        etPiIp.setText(prefs.getString("pi_ip", "192.168.43.100"))

        setupWebView()

        findViewById<Button>(R.id.btnStart).setOnClickListener {
            val piIp = etPiIp.text.toString().trim()
            if (piIp.isEmpty()) return@setOnClickListener

            prefs.edit().putString("pi_ip", piIp).apply()

            val intent = Intent(this, RelayForegroundService::class.java).apply {
                action = RelayForegroundService.ACTION_START
                putExtra(RelayForegroundService.EXTRA_PI_IP, piIp)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }

            val localIp = getLocalIpAddress() ?: "127.0.0.1"
            tvInfo.text = "服務運行中！外部瀏覽器請開: http://$localIp:8080"
            webView.postDelayed({
                webView.loadUrl("http://127.0.0.1:8080")
            }, 500)
        }

        findViewById<Button>(R.id.btnStop).setOnClickListener {
            val intent = Intent(this, RelayForegroundService::class.java).apply {
                action = RelayForegroundService.ACTION_STOP
            }
            startService(intent)
            tvInfo.text = "狀態：已停止中繼服務"
            webView.loadUrl("about:blank")
        }
    }

    private fun setupWebView() {
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            useWideViewPort = true
            loadWithOverviewMode = true
        }
        webView.webViewClient = WebViewClient()
        webView.webChromeClient = WebChromeClient()
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    1001
                )
            }
        }
    }

    private fun getLocalIpAddress(): String? {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            for (intf in interfaces) {
                val addrs = intf.inetAddresses
                for (addr in addrs) {
                    if (!addr.isLoopbackAddress && addr is Inet4Address) {
                        return addr.hostAddress
                    }
                }
            }
        } catch (_: Exception) {
        }
        return null
    }
}
