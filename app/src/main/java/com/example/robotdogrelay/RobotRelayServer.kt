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
    private val appContext: Context,
    private val piIp: String,
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
                    val html = appContext.assets.open("index.html").bufferedReader().use { it.readText() }
                    call.respondText(html, ContentType.Text.Html)
                }

                webSocket("/ws") {
                    activeWsSession = this
                    send("STATUS#已連上 Android 中繼")

                    try {
                        for (frame in incoming) {
                            if (frame is Frame.Text) {
                                val text = frame.readText()
                                if (text == "RECONNECT") {
                                    connectToPiCommandSocket()
                                } else {
                                    sendToPi(text)
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
                cmdSocket?.close()
                val socket = Socket(piIp, cmdPort)
                cmdSocket = socket
                cmdWriter = PrintWriter(socket.getOutputStream(), true)
                activeWsSession?.send("STATUS#機器狗已連線 ($piIp)")

                val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
                while (isActive) {
                    val line = reader.readLine() ?: break
                    activeWsSession?.send("DATA#$line")
                }
            } catch (e: Exception) {
                activeWsSession?.send("STATUS#Pi 連線失敗: ${e.message}")
            }
        }
    }

    private fun sendToPi(cmd: String) {
        scope.launch {
            try {
                cmdWriter?.println(cmd)
            } catch (_: Exception) {
                activeWsSession?.send("STATUS#指令發送失敗")
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

