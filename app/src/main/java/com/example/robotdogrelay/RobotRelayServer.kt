package com.example.robotdogrelay

import android.content.Context
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.cio.*
import io.ktor.server.engine.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.utils.io.*
import io.ktor.websocket.*
import kotlinx.coroutines.*
import java.io.BufferedReader
import java.io.DataInputStream
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.Socket
import java.nio.ByteBuffer
import java.nio.ByteOrder

class RobotRelayServer(
    private val context: Context,
    private var piIp: String,
    private val cmdPort: Int = 5001,
    private val videoPort: Int = 8001
) {
    private var server: ApplicationEngine? = null
    private var cmdSocket: Socket? = null
    private var cmdWriter: PrintWriter? = null
    private var activeWsSession: DefaultWebSocketServerSession? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    fun start(localPort: Int = 8080) {
        connectToPiCommandSocket()

        server = embeddedServer(CIO, port = localPort) {
            install(WebSockets)

            routing {
                get("/") {
                    // 明確指定外部的 Android Context，避免與 Ktor 的 call.application.context 衝突
                    val html = this@RobotRelayServer.context.assets.open("index.html")
                        .bufferedReader()
                        .use { it.readText() }
                    call.respondText(html, ContentType.Text.Html)
                }

                webSocket("/ws") {
                    activeWsSession = this
                    send("CURRENT_IP#$piIp")

                    if (cmdSocket?.isConnected == true && cmdSocket?.isClosed == false) {
                        send("CONN_STATE#CONNECTED#機器狗已連線 ($piIp)")
                    } else {
                        send("CONN_STATE#DISCONNECTED#尚未連線至 Pi ($piIp)")
                    }

                    try {
                        for (frame in incoming) {
                            if (frame is Frame.Text) {
                                val text = frame.readText()
                                when {
                                    text.startsWith("SET_IP#") -> {
                                        val newIp = text.removePrefix("SET_IP#").trim()
                                        if (newIp.isNotEmpty()) {
                                            piIp = newIp
                                            connectToPiCommandSocket()
                                        }
                                    }
                                    text == "RECONNECT" -> {
                                        connectToPiCommandSocket()
                                    }
                                    else -> {
                                        sendToPi(text)
                                    }
                                }
                            }
                        }
                    } finally {
                        sendToPi("CMD_MOVE#0#0")
                        if (activeWsSession == this) activeWsSession = null
                    }
                }

                get("/video_feed") {
                    val boundary = "frame"
                    call.respondBytesWriter(
                        contentType = ContentType.parse("multipart/x-mixed-replace; boundary=$boundary")
                    ) {
                        var vSocket: Socket? = null
                        try {
                            vSocket = Socket(piIp, videoPort)
                            val input = DataInputStream(vSocket.getInputStream())
                            val lenBuf = ByteArray(4)

                            while (isActive) {
                                input.readFully(lenBuf)
                                val imgLen = ByteBuffer.wrap(lenBuf).order(ByteOrder.LITTLE_ENDIAN).int
                                if (imgLen <= 0 || imgLen > 2_000_000) break

                                val imgBytes = ByteArray(imgLen)
                                input.readFully(imgBytes)

                                writeStringUtf8("--$boundary\r\nContent-Type: image/jpeg\r\nContent-Length: $imgLen\r\n\r\n")
                                writeFully(imgBytes, 0, imgLen)
                                writeStringUtf8("\r\n")
                                flush()
                            }
                        } catch (_: Exception) {
                        } finally {
                            vSocket?.close()
                        }
                    }
                }
            }
        }.start(wait = false)
    }

    private fun connectToPiCommandSocket() {
        scope.launch {
            try {
                activeWsSession?.send("CONN_STATE#DISCONNECTED#正在連線至 $piIp...")
                cmdSocket?.close()

                val socket = Socket(piIp, cmdPort)
                cmdSocket = socket
                cmdWriter = PrintWriter(socket.getOutputStream(), true)

                activeWsSession?.send("CONN_STATE#CONNECTED#機器狗已連線 ($piIp)")

                val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
                while (isActive) {
                    val line = reader.readLine() ?: break
                    activeWsSession?.send("DATA#$line")
                }

                activeWsSession?.send("CONN_STATE#DISCONNECTED#Pi 連線已中斷")
            } catch (e: Exception) {
                activeWsSession?.send("CONN_STATE#DISCONNECTED#Pi 連線失敗: ${e.message}")
            }
        }
    }

    private fun sendToPi(cmd: String) {
        scope.launch {
            try {
                if (cmdSocket == null || cmdSocket?.isClosed == true) {
                    activeWsSession?.send("CONN_STATE#DISCONNECTED#未連線，無法發送指令")
                    return@launch
                }
                cmdWriter?.println(cmd)
            } catch (_: Exception) {
                activeWsSession?.send("CONN_STATE#DISCONNECTED#指令發送失敗（連線中斷）")
            }
        }
    }

    fun stop() {
        sendToPi("CMD_MOVE#0#0")
        scope.cancel()
        cmdSocket?.close()
        server?.stop(500, 1000)
    }
}

