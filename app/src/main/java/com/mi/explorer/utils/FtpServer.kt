package com.mi.explorer.utils

import android.util.Log
import java.io.*
import java.net.*
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Lightweight, standard RFC 959 compliant FTP Server for Android.
 * Enables wireless file management between Phone and PC (Windows Explorer, FileZilla, Mac Finder, Browsers).
 */
class FtpServer(
    val rootDir: File,
    val port: Int = 2121,
    val onClientConnected: ((String) -> Unit)? = null
) {
    private var controlServerSocket: ServerSocket? = null
    private val isRunning = AtomicBoolean(false)
    private var serverThread: Thread? = null

    val isServerRunning: Boolean get() = isRunning.get()

    fun start(): Boolean {
        if (isRunning.get()) return true
        try {
            controlServerSocket = ServerSocket(port)
            isRunning.set(true)

            serverThread = Thread {
                while (isRunning.get()) {
                    try {
                        val clientSocket = controlServerSocket?.accept() ?: break
                        handleClientConnection(clientSocket)
                    } catch (e: Exception) {
                        if (!isRunning.get()) break
                    }
                }
            }.apply {
                isDaemon = true
                name = "MiFtpServerThread"
                start()
            }
            return true
        } catch (e: Exception) {
            Log.e("MiFtpServer", "Failed to start FTP server on port $port", e)
            stop()
            return false
        }
    }

    fun stop() {
        isRunning.set(false)
        try {
            controlServerSocket?.close()
        } catch (ignored: Exception) {}
        controlServerSocket = null
        serverThread = null
    }

    private fun handleClientConnection(socket: Socket) {
        Thread {
            val clientIp = socket.inetAddress?.hostAddress ?: "Unknown"
            onClientConnected?.invoke(clientIp)

            var currentDirectory = rootDir
            var isPassive = false
            var passiveServerSocket: ServerSocket? = null
            var activeDataSocket: Socket? = null
            var transferType = "I" // Image (binary) by default

            try {
                val reader = BufferedReader(InputStreamReader(socket.getInputStream(), Charsets.UTF_8))
                val writer = BufferedWriter(OutputStreamWriter(socket.getOutputStream(), Charsets.UTF_8))

                fun sendResponse(code: Int, text: String) {
                    writer.write("$code $text\r\n")
                    writer.flush()
                }

                sendResponse(220, "Mi Explorer FTP Server ready.")

                while (isRunning.get() && !socket.isClosed) {
                    val line = reader.readLine() ?: break
                    val trimmed = line.trim()
                    if (trimmed.isEmpty()) continue

                    val spaceIdx = trimmed.indexOf(' ')
                    val command = if (spaceIdx != -1) trimmed.substring(0, spaceIdx).uppercase(Locale.US) else trimmed.uppercase(Locale.US)
                    val argument = if (spaceIdx != -1) trimmed.substring(spaceIdx + 1).trim() else ""

                    when (command) {
                        "USER" -> sendResponse(230, "User logged in, proceed.")
                        "PASS" -> sendResponse(230, "User logged in, proceed.")
                        "AUTH" -> sendResponse(502, "TLS/SSL not supported.")
                        "SYST" -> sendResponse(215, "UNIX Type: L8")
                        "FEAT" -> {
                            writer.write("211-Features:\r\n PASV\r\n EPSV\r\n UTF8\r\n SIZE\r\n MLSD\r\n211 End\r\n")
                            writer.flush()
                        }
                        "OPTS" -> {
                            if (argument.uppercase(Locale.US).startsWith("UTF8")) {
                                sendResponse(200, "UTF8 mode ON.")
                            } else {
                                sendResponse(200, "OK.")
                            }
                        }
                        "PWD", "XPWD" -> {
                            val relPath = getRelativePath(rootDir, currentDirectory)
                            sendResponse(257, "\"$relPath\" is current directory.")
                        }
                        "TYPE" -> {
                            transferType = argument.uppercase(Locale.US)
                            sendResponse(200, "Type set to $transferType.")
                        }
                        "PASV" -> {
                            try {
                                passiveServerSocket?.close()
                                passiveServerSocket = ServerSocket(0)
                                isPassive = true
                                val localAddr = socket.localAddress
                                val ipParts = localAddr.address.map { (it.toInt() and 0xFF).toString() }
                                val p = passiveServerSocket!!.localPort
                                val p1 = p / 256
                                val p2 = p % 256
                                val pasvStr = "${ipParts.joinToString(",")},$p1,$p2"
                                sendResponse(227, "Entering Passive Mode ($pasvStr).")
                            } catch (e: Exception) {
                                sendResponse(425, "Cannot open passive data connection.")
                            }
                        }
                        "EPSV" -> {
                            try {
                                passiveServerSocket?.close()
                                passiveServerSocket = ServerSocket(0)
                                isPassive = true
                                val p = passiveServerSocket!!.localPort
                                sendResponse(229, "Entering Extended Passive Mode (|||$p|).")
                            } catch (e: Exception) {
                                sendResponse(425, "Cannot open extended passive connection.")
                            }
                        }
                        "PORT" -> {
                            try {
                                val parts = argument.split(",").map { it.trim().toInt() }
                                if (parts.size == 6) {
                                    val ip = "${parts[0]}.${parts[1]}.${parts[2]}.${parts[3]}"
                                    val dataPort = (parts[4] shl 8) + parts[5]
                                    activeDataSocket = Socket(ip, dataPort)
                                    isPassive = false
                                    sendResponse(200, "PORT command successful.")
                                } else {
                                    sendResponse(501, "Syntax error in parameters.")
                                }
                            } catch (e: Exception) {
                                sendResponse(425, "Cannot connect to specified port.")
                            }
                        }
                        "CWD", "XCWD" -> {
                            val target = resolveTargetFile(rootDir, currentDirectory, argument)
                            if (target.exists() && target.isDirectory) {
                                currentDirectory = target
                                val rel = getRelativePath(rootDir, currentDirectory)
                                sendResponse(250, "Directory changed to $rel.")
                            } else {
                                sendResponse(550, "Directory not found.")
                            }
                        }
                        "CDUP", "XCUP" -> {
                            val parent = currentDirectory.parentFile
                            if (parent != null && isChildOrSame(rootDir, parent)) {
                                currentDirectory = parent
                                val rel = getRelativePath(rootDir, currentDirectory)
                                sendResponse(250, "Directory changed to $rel.")
                            } else {
                                sendResponse(250, "Already at root.")
                            }
                        }
                        "LIST", "NLST" -> {
                            sendResponse(150, "Opening data connection for file list.")
                            val dataSocket = getDataSocket(isPassive, passiveServerSocket, activeDataSocket)
                            if (dataSocket != null) {
                                try {
                                    val dataWriter = BufferedWriter(OutputStreamWriter(dataSocket.getOutputStream(), Charsets.UTF_8))
                                    val targetDir = if (argument.isNotEmpty()) resolveTargetFile(rootDir, currentDirectory, argument) else currentDirectory
                                    val files = targetDir.listFiles() ?: emptyArray()

                                    val dateFormat = SimpleDateFormat("MMM dd HH:mm", Locale.US)
                                    for (f in files) {
                                        if (f.name.startsWith(".")) continue
                                        val isDir = f.isDirectory
                                        val perms = if (isDir) "drwxr-xr-x" else "-rw-r--r--"
                                        val size = if (isDir) 0L else f.length()
                                        val dateStr = dateFormat.format(Date(f.lastModified()))
                                        dataWriter.write("$perms 1 owner group $size $dateStr ${f.name}\r\n")
                                    }
                                    dataWriter.flush()
                                } finally {
                                    dataSocket.close()
                                    passiveServerSocket?.close()
                                    passiveServerSocket = null
                                }
                                sendResponse(226, "Transfer complete.")
                            } else {
                                sendResponse(425, "Cannot open data connection.")
                            }
                        }
                        "MLSD" -> {
                            sendResponse(150, "Opening data connection for MLSD.")
                            val dataSocket = getDataSocket(isPassive, passiveServerSocket, activeDataSocket)
                            if (dataSocket != null) {
                                try {
                                    val dataWriter = BufferedWriter(OutputStreamWriter(dataSocket.getOutputStream(), Charsets.UTF_8))
                                    val files = currentDirectory.listFiles() ?: emptyArray()
                                    val fmt = SimpleDateFormat("yyyyMMddHHmmss", Locale.US)
                                    for (f in files) {
                                        if (f.name.startsWith(".")) continue
                                        val type = if (f.isDirectory) "dir" else "file"
                                        val size = if (f.isDirectory) 0L else f.length()
                                        val modify = fmt.format(Date(f.lastModified()))
                                        dataWriter.write("type=$type;size=$size;modify=$modify; ${f.name}\r\n")
                                    }
                                    dataWriter.flush()
                                } finally {
                                    dataSocket.close()
                                    passiveServerSocket?.close()
                                    passiveServerSocket = null
                                }
                                sendResponse(226, "MLSD complete.")
                            } else {
                                sendResponse(425, "Cannot open data connection.")
                            }
                        }
                        "SIZE" -> {
                            val target = resolveTargetFile(rootDir, currentDirectory, argument)
                            if (target.exists() && target.isFile) {
                                sendResponse(213, "${target.length()}")
                            } else {
                                sendResponse(550, "File not found.")
                            }
                        }
                        "RETR" -> {
                            val target = resolveTargetFile(rootDir, currentDirectory, argument)
                            if (target.exists() && target.isFile) {
                                sendResponse(150, "Opening binary connection for ${target.name} (${target.length()} bytes).")
                                val dataSocket = getDataSocket(isPassive, passiveServerSocket, activeDataSocket)
                                if (dataSocket != null) {
                                    try {
                                        val output = dataSocket.getOutputStream()
                                        FileInputStream(target).use { input ->
                                            val buffer = ByteArray(64 * 1024)
                                            var bytesRead: Int
                                            while (input.read(buffer).also { bytesRead = it } != -1) {
                                                output.write(buffer, 0, bytesRead)
                                            }
                                            output.flush()
                                        }
                                    } finally {
                                        dataSocket.close()
                                        passiveServerSocket?.close()
                                        passiveServerSocket = null
                                    }
                                    sendResponse(226, "File transfer complete.")
                                } else {
                                    sendResponse(425, "Data connection failed.")
                                }
                            } else {
                                sendResponse(550, "File not found.")
                            }
                        }
                        "STOR" -> {
                            val target = resolveTargetFile(rootDir, currentDirectory, argument)
                            sendResponse(150, "Ready to receive data for ${target.name}.")
                            val dataSocket = getDataSocket(isPassive, passiveServerSocket, activeDataSocket)
                            if (dataSocket != null) {
                                try {
                                    val input = dataSocket.getInputStream()
                                    FileOutputStream(target).use { output ->
                                        val buffer = ByteArray(64 * 1024)
                                        var bytesRead: Int
                                        while (input.read(buffer).also { bytesRead = it } != -1) {
                                            output.write(buffer, 0, bytesRead)
                                        }
                                        output.flush()
                                    }
                                } finally {
                                    dataSocket.close()
                                    passiveServerSocket?.close()
                                    passiveServerSocket = null
                                }
                                sendResponse(226, "Transfer complete.")
                            } else {
                                sendResponse(425, "Data connection failed.")
                            }
                        }
                        "DELE" -> {
                            val target = resolveTargetFile(rootDir, currentDirectory, argument)
                            if (target.exists() && target.isFile && target.delete()) {
                                sendResponse(250, "File deleted.")
                            } else {
                                sendResponse(550, "Delete failed.")
                            }
                        }
                        "MKD", "XMKD" -> {
                            val target = resolveTargetFile(rootDir, currentDirectory, argument)
                            if (target.mkdirs()) {
                                sendResponse(257, "\"$argument\" created.")
                            } else {
                                sendResponse(550, "Create directory failed.")
                            }
                        }
                        "RMD", "XRMD" -> {
                            val target = resolveTargetFile(rootDir, currentDirectory, argument)
                            if (target.exists() && target.isDirectory && target.deleteRecursively()) {
                                sendResponse(250, "Directory removed.")
                            } else {
                                sendResponse(550, "Remove directory failed.")
                            }
                        }
                        "QUIT" -> {
                            sendResponse(221, "Goodbye.")
                            break
                        }
                        "NOOP" -> sendResponse(200, "NOOP OK.")
                        else -> sendResponse(502, "Command not implemented.")
                    }
                }
            } catch (e: Exception) {
                // Connection closed or broken
            } finally {
                try { socket.close() } catch (ignored: Exception) {}
                try { passiveServerSocket?.close() } catch (ignored: Exception) {}
            }
        }.apply {
            isDaemon = true
            name = "MiFtpClientSession"
            start()
        }
    }

    private fun getDataSocket(
        isPassive: Boolean,
        passiveServer: ServerSocket?,
        activeSocket: Socket?
    ): Socket? {
        return if (isPassive) {
            try {
                passiveServer?.accept()
            } catch (e: Exception) {
                null
            }
        } else {
            activeSocket
        }
    }

    private fun resolveTargetFile(root: File, current: File, pathArg: String): File {
        var clean = pathArg.trim()
        if (clean.startsWith("\"") && clean.endsWith("\"") && clean.length >= 2) {
            clean = clean.substring(1, clean.length - 1)
        }
        val target = if (clean.startsWith("/")) {
            File(root, clean.removePrefix("/"))
        } else {
            File(current, clean)
        }
        val canonical = try { target.canonicalFile } catch (e: Exception) { target }
        return if (isChildOrSame(root, canonical)) canonical else root
    }

    private fun isChildOrSame(parent: File, child: File): Boolean {
        var p: File? = child
        while (p != null) {
            if (p.absolutePath == parent.absolutePath) return true
            p = p.parentFile
        }
        return false
    }

    private fun getRelativePath(root: File, current: File): String {
        val rootPath = root.absolutePath
        val curPath = current.absolutePath
        if (curPath == rootPath) return "/"
        return if (curPath.startsWith(rootPath)) {
            curPath.removePrefix(rootPath).replace('\\', '/')
        } else {
            "/"
        }
    }
}
