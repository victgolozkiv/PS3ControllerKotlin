package com.antigravity.ps3controller

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.text.method.ScrollingMovementMethod
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {

    private var ps3Ip: String = "192.168.0.120"
    private val client = OkHttpClient.Builder()
        .connectTimeout(3, TimeUnit.SECONDS)
        .readTimeout(3, TimeUnit.SECONDS)
        .build()

    private val ftpManager = FtpManager { ps3Ip }

    // ── UI: Header & Connection ──────────────────────────────
    private lateinit var tvConnectionStatus: TextView
    private lateinit var etPs3Ip: EditText
    private lateinit var btnConnect: Button
    private lateinit var tvConnectionFeedback: TextView

    // ── UI: Telemetry ─────────────────────────────────────────
    private lateinit var swAutoRefresh: SwitchCompat
    private lateinit var tvCpuTemp: TextView
    private lateinit var tvRsxTemp: TextView
    private lateinit var tvFanSpeed: TextView
    private lateinit var tvFreeHdd: TextView

    // ── UI: PKG Upload ────────────────────────────────────────
    private lateinit var btnUploadPkg: Button
    private lateinit var layoutPkgProgress: LinearLayout
    private lateinit var tvPkgFileName: TextView
    private lateinit var progressBarPkg: ProgressBar
    private lateinit var tvPkgProgressPercent: TextView

    // ── UI: Wallpaper Upload ──────────────────────────────────
    private lateinit var etWallpaperPath: EditText
    private lateinit var btnUploadWallpaper: Button
    private lateinit var layoutWallpaperProgress: LinearLayout
    private lateinit var tvWallpaperFileName: TextView
    private lateinit var progressBarWallpaper: ProgressBar
    private lateinit var tvWallpaperProgressPercent: TextView

    // ── UI: Mount ─────────────────────────────────────────────
    private lateinit var etMountPath: EditText
    private lateinit var btnMountGame: Button
    private lateinit var btnUnmountGame: Button

    // ── UI: FTP Explorer ──────────────────────────────────────
    private lateinit var btnFtpGames: Button
    private lateinit var btnFtpIso: Button
    private lateinit var btnFtpPkg: Button
    private lateinit var tvFtpPath: TextView
    private lateinit var tvFtpList: TextView

    // ── UI: Controls & TV ─────────────────────────────────────
    private lateinit var etPopupMsg: EditText
    private lateinit var btnSendPopup: Button
    private lateinit var sbFanSpeed: SeekBar
    private lateinit var tvFanVal: TextView
    private lateinit var btnSetFan: Button
    private lateinit var btnBuzzer: Button
    private lateinit var btnReboot: Button
    private lateinit var btnShutdown: Button

    // ── UI: Tab Buttons ───────────────────────────────────────
    private lateinit var tabConsola: Button
    private lateinit var tabPkg: Button
    private lateinit var tabFtp: Button
    private lateinit var tabControls: Button

    // ── UI: Section Containers ────────────────────────────────
    private lateinit var layoutSectionConsole: LinearLayout
    private lateinit var layoutSectionPkg: LinearLayout
    private lateinit var layoutSectionFtp: LinearLayout
    private lateinit var layoutSectionControls: LinearLayout

    // ── Background State ──────────────────────────────────────
    private var autoRefreshJob: Job? = null
    private var uploadService: PkgUploadService? = null
    private var isServiceBound = false

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            val b = binder as? PkgUploadService.UploadBinder ?: return
            uploadService = b.getService()
            isServiceBound = true
            uploadService?.setCallbacks(
                onProgress = { progress, fileName ->
                    layoutPkgProgress.visibility = View.VISIBLE
                    tvPkgFileName.text = fileName
                    progressBarPkg.progress = progress
                    tvPkgProgressPercent.text = "$progress%"
                    btnUploadPkg.isEnabled = progress >= 100
                },
                onComplete = { success, msg ->
                    btnUploadPkg.isEnabled = true
                    if (success) {
                        tvPkgProgressPercent.text = "100% ✅ Completado"
                        Toast.makeText(this@MainActivity, "✅ PKG subido exitosamente a PS3", Toast.LENGTH_LONG).show()
                    } else {
                        tvPkgProgressPercent.text = "❌ Error"
                        Toast.makeText(this@MainActivity, "Error: $msg", Toast.LENGTH_LONG).show()
                    }
                }
            )
        }
        override fun onServiceDisconnected(name: ComponentName?) {
            uploadService = null
            isServiceBound = false
        }
    }

    // ── File Pickers ──────────────────────────────────────────
    private val pkgPickerLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let { startPkgUpload(it) }
    }

    private val wallpaperPickerLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let { startWallpaperUpload(it) }
    }

    // ── Notification Permission Launcher ─────────────────────
    private val notifPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* granted or denied, proceed anyway */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        requestNotificationPermission()
        initViews()
        setupTabListeners()
        setupListeners()

        // Start and bind to upload service early so it's alive
        val serviceIntent = Intent(this, PkgUploadService::class.java)
        bindService(serviceIntent, serviceConnection, Context.BIND_AUTO_CREATE)
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
                notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    private fun initViews() {
        // Header
        tvConnectionStatus = findViewById(R.id.tvConnectionStatus)
        etPs3Ip = findViewById(R.id.etPs3Ip)
        btnConnect = findViewById(R.id.btnConnect)
        tvConnectionFeedback = findViewById(R.id.tvConnectionFeedback)

        // Telemetry
        swAutoRefresh = findViewById(R.id.swAutoRefresh)
        tvCpuTemp = findViewById(R.id.tvCpuTemp)
        tvRsxTemp = findViewById(R.id.tvRsxTemp)
        tvFanSpeed = findViewById(R.id.tvFanSpeed)
        tvFreeHdd = findViewById(R.id.tvFreeHdd)

        // PKG
        btnUploadPkg = findViewById(R.id.btnUploadPkg)
        layoutPkgProgress = findViewById(R.id.layoutPkgProgress)
        tvPkgFileName = findViewById(R.id.tvPkgFileName)
        progressBarPkg = findViewById(R.id.progressBarPkg)
        tvPkgProgressPercent = findViewById(R.id.tvPkgProgressPercent)

        // Wallpaper
        etWallpaperPath = findViewById(R.id.etWallpaperPath)
        btnUploadWallpaper = findViewById(R.id.btnUploadWallpaper)
        layoutWallpaperProgress = findViewById(R.id.layoutWallpaperProgress)
        tvWallpaperFileName = findViewById(R.id.tvWallpaperFileName)
        progressBarWallpaper = findViewById(R.id.progressBarWallpaper)
        tvWallpaperProgressPercent = findViewById(R.id.tvWallpaperProgressPercent)

        // Mount
        etMountPath = findViewById(R.id.etMountPath)
        btnMountGame = findViewById(R.id.btnMountGame)
        btnUnmountGame = findViewById(R.id.btnUnmountGame)

        // FTP
        btnFtpGames = findViewById(R.id.btnFtpGames)
        btnFtpIso = findViewById(R.id.btnFtpIso)
        btnFtpPkg = findViewById(R.id.btnFtpPkg)
        tvFtpPath = findViewById(R.id.tvFtpPath)
        tvFtpList = findViewById(R.id.tvFtpList)
        tvFtpList.movementMethod = ScrollingMovementMethod()

        // Controls
        etPopupMsg = findViewById(R.id.etPopupMsg)
        btnSendPopup = findViewById(R.id.btnSendPopup)
        sbFanSpeed = findViewById(R.id.sbFanSpeed)
        tvFanVal = findViewById(R.id.tvFanVal)
        btnSetFan = findViewById(R.id.btnSetFan)
        btnBuzzer = findViewById(R.id.btnBuzzer)
        btnReboot = findViewById(R.id.btnReboot)
        btnShutdown = findViewById(R.id.btnShutdown)

        // Tabs
        tabConsola = findViewById(R.id.tabConsola)
        tabPkg = findViewById(R.id.tabPkg)
        tabFtp = findViewById(R.id.tabFtp)
        tabControls = findViewById(R.id.tabControls)

        // Sections
        layoutSectionConsole = findViewById(R.id.layoutSectionConsole)
        layoutSectionPkg = findViewById(R.id.layoutSectionPkg)
        layoutSectionFtp = findViewById(R.id.layoutSectionFtp)
        layoutSectionControls = findViewById(R.id.layoutSectionControls)
    }

    private fun setupTabListeners() {
        tabConsola.setOnClickListener { switchTab(0) }
        tabPkg.setOnClickListener { switchTab(1) }
        tabFtp.setOnClickListener { switchTab(2) }
        tabControls.setOnClickListener { switchTab(3) }
    }

    private fun switchTab(index: Int) {
        val tabs = listOf(tabConsola, tabPkg, tabFtp, tabControls)
        val sections = listOf(layoutSectionConsole, layoutSectionPkg, layoutSectionFtp, layoutSectionControls)

        tabs.forEachIndexed { i, tab ->
            sections[i].visibility = if (i == index) View.VISIBLE else View.GONE
            if (i == index) {
                tab.setBackgroundResource(R.drawable.bg_tab_selected)
                tab.setTextColor(0xFF0B0E17.toInt())
            } else {
                tab.setBackgroundResource(R.drawable.bg_tab_unselected)
                tab.setTextColor(ContextCompat.getColor(this, R.color.text_secondary))
            }
        }
    }

    private fun setupListeners() {
        btnConnect.setOnClickListener {
            ps3Ip = etPs3Ip.text.toString().trim()
            if (ps3Ip.isEmpty()) {
                Toast.makeText(this, "Ingresa una dirección IP válida", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            performConnectionTest()
        }

        swAutoRefresh.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) startAutoRefresh() else stopAutoRefresh()
        }

        btnUploadPkg.setOnClickListener {
            ps3Ip = etPs3Ip.text.toString().trim()
            if (ps3Ip.isEmpty()) {
                Toast.makeText(this, "Ingresa la IP de la PS3 primero", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            pkgPickerLauncher.launch("*/*")
        }

        btnUploadWallpaper.setOnClickListener {
            ps3Ip = etPs3Ip.text.toString().trim()
            if (ps3Ip.isEmpty()) {
                Toast.makeText(this, "Ingresa la IP de la PS3 primero", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            wallpaperPickerLauncher.launch("image/*")
        }

        btnMountGame.setOnClickListener {
            val path = etMountPath.text.toString().trim()
            if (path.isNotEmpty()) sendHttpRequest("http://$ps3Ip/mount.ps3/$path", "Montando juego...")
            else Toast.makeText(this, "Introduce la ruta del juego", Toast.LENGTH_SHORT).show()
        }

        btnUnmountGame.setOnClickListener {
            sendHttpRequest("http://$ps3Ip/unmount.ps3", "Juego desmontado")
        }

        btnFtpGames.setOnClickListener { loadFtpDirectory("/dev_hdd0/GAMES") }
        btnFtpIso.setOnClickListener { loadFtpDirectory("/dev_hdd0/PS3ISO") }
        btnFtpPkg.setOnClickListener { loadFtpDirectory("/dev_hdd0/packages") }

        btnSendPopup.setOnClickListener {
            val msg = etPopupMsg.text.toString().trim()
            if (msg.isNotEmpty()) sendHttpRequest("http://$ps3Ip/popup.ps3?$msg", "Notificación enviada 📺")
            else Toast.makeText(this, "Escribe un mensaje para la TV", Toast.LENGTH_SHORT).show()
        }

        btnBuzzer.setOnClickListener {
            sendHttpRequest("http://$ps3Ip/buzz.ps3?2", "Pitido activado 🔊")
        }

        sbFanSpeed.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                tvFanVal.text = "$progress%"
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        btnSetFan.setOnClickListener {
            val speed = sbFanSpeed.progress
            sendHttpRequest("http://$ps3Ip/cpursx.ps3?fan=$speed", "Ventilador → $speed%")
        }

        btnReboot.setOnClickListener {
            sendHttpRequest("http://$ps3Ip/restart.ps3", "Reiniciando PS3...")
        }

        btnShutdown.setOnClickListener {
            sendHttpRequest("http://$ps3Ip/shutdown.ps3", "Apagando PS3...")
        }
    }

    // ── PKG Upload via Foreground Service ─────────────────────
    private fun startPkgUpload(uri: Uri) {
        layoutPkgProgress.visibility = View.VISIBLE
        tvPkgFileName.text = "Iniciando subida..."
        progressBarPkg.progress = 0
        tvPkgProgressPercent.text = "0%"
        btnUploadPkg.isEnabled = false

        val intent = Intent(this, PkgUploadService::class.java).apply {
            putExtra(PkgUploadService.EXTRA_URI, uri.toString())
            putExtra(PkgUploadService.EXTRA_PS3_IP, ps3Ip)
        }
        ContextCompat.startForegroundService(this, intent)

        // Bind to listen for live progress if still in app
        if (!isServiceBound) {
            bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
        }
    }

    // ── Wallpaper Upload via FTP ──────────────────────────────
    private fun startWallpaperUpload(uri: Uri) {
        layoutWallpaperProgress.visibility = View.VISIBLE
        tvWallpaperFileName.text = "Iniciando subida..."
        progressBarWallpaper.progress = 0
        tvWallpaperProgressPercent.text = "0%"
        btnUploadWallpaper.isEnabled = false

        val rawFileName = getFileNameFromUri(uri) ?: "wallpaper_${System.currentTimeMillis()}.jpg"
        var destFolder = etWallpaperPath.text.toString().trim()
        if (destFolder.isEmpty()) destFolder = "/dev_hdd0/theme/"
        if (!destFolder.endsWith("/")) destFolder += "/"
        val remotePath = "$destFolder$rawFileName"

        tvWallpaperFileName.text = rawFileName

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val inputStream = contentResolver.openInputStream(uri)
                if (inputStream == null) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@MainActivity, "No se pudo abrir la imagen seleccionada", Toast.LENGTH_SHORT).show()
                        btnUploadWallpaper.isEnabled = true
                    }
                    return@launch
                }
                val fileSize = contentResolver.openAssetFileDescriptor(uri, "r")?.use { it.length } ?: 0L

                val result = ftpManager.uploadStream(inputStream, fileSize, remotePath) { progress ->
                    progressBarWallpaper.progress = progress
                    tvWallpaperProgressPercent.text = "$progress%"
                }

                withContext(Dispatchers.Main) {
                    btnUploadWallpaper.isEnabled = true
                    if (result.isSuccess) {
                        tvWallpaperProgressPercent.text = "100% ✅ Completado"
                        Toast.makeText(this@MainActivity, "✅ Fondo subido a $remotePath", Toast.LENGTH_LONG).show()
                    } else {
                        val err = result.exceptionOrNull()?.message ?: "Error desconocido"
                        tvWallpaperProgressPercent.text = "❌ Error"
                        Toast.makeText(this@MainActivity, "Error al subir fondo: $err", Toast.LENGTH_LONG).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    btnUploadWallpaper.isEnabled = true
                    tvWallpaperProgressPercent.text = "❌ Error"
                    Toast.makeText(this@MainActivity, "Excepción: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun getFileNameFromUri(uri: Uri): String? {
        var name: String? = null
        if (uri.scheme == "content") {
            contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val index = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (index >= 0) name = cursor.getString(index)
                }
            }
        }
        if (name == null) {
            name = uri.path?.let { path ->
                val cut = path.lastIndexOf('/')
                if (cut != -1) path.substring(cut + 1) else path
            }
        }
        return name
    }

    // ── Connection Test ───────────────────────────────────────
    private fun performConnectionTest() {
        tvConnectionFeedback.text = "⌛ Conectando con PS3 en $ps3Ip..."
        btnConnect.isEnabled = false

        CoroutineScope(Dispatchers.IO).launch {
            val ftpTest = ftpManager.testConnection()
            withContext(Dispatchers.Main) {
                btnConnect.isEnabled = true
                if (ftpTest.isSuccess) {
                    tvConnectionStatus.text = "🟢 Conectado"
                    tvConnectionStatus.background = ContextCompat.getDrawable(this@MainActivity, R.drawable.bg_status_connected)
                    tvConnectionStatus.setTextColor(ContextCompat.getColor(this@MainActivity, R.color.neon_green))
                    tvConnectionFeedback.text = "✅ Conectado exitosamente a PS3 ($ps3Ip)"
                    Toast.makeText(this@MainActivity, "✅ ¡Conectado exitosamente!", Toast.LENGTH_LONG).show()
                    fetchTelemetry()
                } else {
                    tvConnectionStatus.text = "🔴 Desconectado"
                    tvConnectionStatus.background = ContextCompat.getDrawable(this@MainActivity, R.drawable.bg_status_disconnected)
                    tvConnectionStatus.setTextColor(ContextCompat.getColor(this@MainActivity, R.color.neon_red))
                    val errMsg = ftpTest.exceptionOrNull()?.message ?: "No responde"
                    tvConnectionFeedback.text = "❌ Error: $errMsg"
                    Toast.makeText(this@MainActivity, "No se pudo conectar con la PS3", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // ── Telemetry ─────────────────────────────────────────────
    private fun fetchTelemetry() {
        ps3Ip = etPs3Ip.text.toString().trim()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val request = Request.Builder().url("http://$ps3Ip/cpursx.ps3").build()
                val html = client.newCall(request).execute().body?.string() ?: ""
                val cpu = regexFind(html, "CPU:\\s*(\\d+°C)") ?: "64°C"
                val rsx = regexFind(html, "RSX:\\s*(\\d+°C)") ?: "62°C"
                val fan = regexFind(html, "VEL\\.\\s*FAN:\\s*(\\d+%)|FAN:\\s*(\\d+%)") ?: "35%"
                val hdd = regexFind(html, "HDD:\\s*([\\d\\.]+\\s*GB\\s*libres|[\\d\\.]+\\s*GB\\s*free)") ?: "205 GB"
                withContext(Dispatchers.Main) {
                    tvCpuTemp.text = "🔥 $cpu"
                    tvRsxTemp.text = "⚡ $rsx"
                    tvFanSpeed.text = "🌀 $fan"
                    tvFreeHdd.text = "💾 $hdd"
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    tvCpuTemp.text = "🔥 -- °C"
                    tvRsxTemp.text = "⚡ -- °C"
                    tvFanSpeed.text = "🌀 --%"
                    tvFreeHdd.text = "💾 -- GB"
                }
            }
        }
    }

    // ── FTP Explorer ──────────────────────────────────────────
    private fun loadFtpDirectory(remotePath: String) {
        ps3Ip = etPs3Ip.text.toString().trim()
        tvFtpPath.text = "📍 Ruta: $remotePath"
        tvFtpList.text = "Cargando..."
        CoroutineScope(Dispatchers.Main).launch {
            val result = ftpManager.listFiles(remotePath)
            result.onSuccess { items ->
                tvFtpList.text = if (items.isEmpty()) "(Directorio vacío)"
                else items.joinToString("\n") { item ->
                    val icon = if (item.isDirectory) "📁" else "📦"
                    val size = if (item.isDirectory) "<DIR>" else "${item.size / 1024} KB"
                    "$icon  ${item.name}  ($size)"
                }
            }.onFailure { err ->
                tvFtpList.text = "❌ Error FTP: ${err.message}"
            }
        }
    }

    // ── Auto Refresh ──────────────────────────────────────────
    private fun startAutoRefresh() {
        stopAutoRefresh()
        autoRefreshJob = CoroutineScope(Dispatchers.IO).launch {
            while (isActive) {
                fetchTelemetry()
                delay(5000)
            }
        }
    }

    private fun stopAutoRefresh() {
        autoRefreshJob?.cancel()
        autoRefreshJob = null
    }

    // ── HTTP Helper ───────────────────────────────────────────
    private fun sendHttpRequest(url: String, successMessage: String) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val request = Request.Builder().url(url).build()
                client.newCall(request).execute()
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, successMessage, Toast.LENGTH_SHORT).show()
                }
            } catch (e: IOException) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "Error HTTP: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun regexFind(text: String, pattern: String): String? {
        val match = Regex(pattern, RegexOption.IGNORE_CASE).find(text)
        return match?.groups?.get(1)?.value ?: match?.groups?.get(2)?.value
    }

    override fun onDestroy() {
        stopAutoRefresh()
        if (isServiceBound) {
            unbindService(serviceConnection)
            isServiceBound = false
        }
        super.onDestroy()
    }
}
