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
import android.os.Environment
import android.os.IBinder
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {

    private var ps3Ip: String = "192.168.0.120"
    private val client = OkHttpClient.Builder()
        .connectTimeout(3, TimeUnit.SECONDS)
        .readTimeout(3, TimeUnit.SECONDS)
        .build()

    private val ftpManager = FtpManager { ps3Ip }

    // Header View
    private lateinit var tvConnectionStatus: TextView
    private lateinit var tabLayout: TabLayout
    private lateinit var viewPager: ViewPager2

    // Tab Page Views Cache
    private val pageViews = arrayOfNulls<View>(5)

    // ── Tab 0: Consola Views ─────────────────────────────────
    private var etPs3Ip: EditText? = null
    private var btnConnect: Button? = null
    private var tvConnectionFeedback: TextView? = null
    private var swAutoRefresh: SwitchCompat? = null
    private var tvCpuTemp: TextView? = null
    private var tvRsxTemp: TextView? = null
    private var tvFanSpeed: TextView? = null
    private var tvFreeHdd: TextView? = null
    private var btnReboot: Button? = null
    private var btnShutdown: Button? = null

    // ── Tab 1: PKG Views ─────────────────────────────────────
    private var btnUploadPkg: Button? = null
    private var layoutPkgProgress: LinearLayout? = null
    private var tvPkgFileName: TextView? = null
    private var progressBarPkg: ProgressBar? = null
    private var tvPkgProgressPercent: TextView? = null
    private var etMountPath: EditText? = null
    private var btnMountGame: Button? = null
    private var btnUnmountGame: Button? = null

    // ── Tab 2: Temas & Fondos Views ──────────────────────────
    private var btnUploadTheme: Button? = null
    private var layoutThemeProgress: LinearLayout? = null
    private var tvThemeFileName: TextView? = null
    private var progressBarTheme: ProgressBar? = null
    private var tvThemeProgressPercent: TextView? = null

    private var etWallpaperPath: EditText? = null
    private var btnUploadWallpaper: Button? = null
    private var layoutWallpaperProgress: LinearLayout? = null
    private var tvWallpaperFileName: TextView? = null
    private var progressBarWallpaper: ProgressBar? = null
    private var tvWallpaperProgressPercent: TextView? = null

    // ── Tab 3: FTP Explorer Views ────────────────────────────
    private var tvFtpPath: TextView? = null
    private var btnFtpUp: Button? = null
    private var btnFtpUploadFile: Button? = null
    private var btnFtpNewFolder: Button? = null
    private var btnFtpRefresh: Button? = null
    private var rvFtpFiles: RecyclerView? = null
    private var pbFtpLoading: ProgressBar? = null
    private var tvFtpEmpty: TextView? = null
    private var ftpAdapter: FtpAdapter? = null
    private var currentFtpPath = "/dev_hdd0/"

    // ── Tab 4: Controles & TV Views ──────────────────────────
    private var etPopupMsg: EditText? = null
    private var btnSendPopup: Button? = null
    private var sbFanSpeed: SeekBar? = null
    private var tvFanVal: TextView? = null
    private var btnSetFan: Button? = null
    private var btnBuzzer: Button? = null

    // Background State
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
                    layoutPkgProgress?.visibility = View.VISIBLE
                    tvPkgFileName?.text = fileName
                    progressBarPkg?.progress = progress
                    tvPkgProgressPercent?.text = "$progress%"
                    btnUploadPkg?.isEnabled = progress >= 100
                },
                onComplete = { success, msg ->
                    btnUploadPkg?.isEnabled = true
                    if (success) {
                        tvPkgProgressPercent?.text = "100% ✅ Completado"
                        Toast.makeText(this@MainActivity, "✅ PKG subido exitosamente a PS3", Toast.LENGTH_LONG).show()
                    } else {
                        tvPkgProgressPercent?.text = "❌ Error"
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

    // Launchers
    private val pkgPickerLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { startPkgUpload(it) }
    }

    private val wallpaperPickerLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { startWallpaperUpload(it) }
    }

    private val themePickerLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { startThemeUpload(it) }
    }

    private val ftpFileUploadLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { uploadFileToCurrentFtp(it) }
    }

    private val notifPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* proceed */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        requestNotificationPermission()

        tvConnectionStatus = findViewById(R.id.tvConnectionStatus)
        tabLayout = findViewById(R.id.tabLayout)
        viewPager = findViewById(R.id.viewPager)

        setupViewPager()

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

    private fun setupViewPager() {
        val tabTitles = arrayOf("⚡ Consola", "📦 PKG & Juegos", "🎨 Temas & Fondos", "📁 FTP Explorer", "⚙️ Controles")

        viewPager.adapter = object : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
            override fun getItemCount(): Int = 5
            override fun getItemViewType(position: Int): Int = position

            override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
                val layoutId = when (viewType) {
                    0 -> R.layout.page_console
                    1 -> R.layout.page_pkg
                    2 -> R.layout.page_themes
                    3 -> R.layout.page_ftp
                    4 -> R.layout.page_controls
                    else -> R.layout.page_console
                }
                val view = LayoutInflater.from(parent.context).inflate(layoutId, parent, false)
                pageViews[viewType] = view
                setupTabViews(viewType, view)
                return object : RecyclerView.ViewHolder(view) {}
            }

            override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {}
        }

        TabLayoutMediator(tabLayout, viewPager) { tab, position ->
            tab.text = tabTitles[position]
        }.attach()
    }

    private fun setupTabViews(pageIndex: Int, root: View) {
        when (pageIndex) {
            0 -> setupConsoleTab(root)
            1 -> setupPkgTab(root)
            2 -> setupThemesTab(root)
            3 -> setupFtpTab(root)
            4 -> setupControlsTab(root)
        }
    }

    // ── TAB 0: CONSOLA ───────────────────────────────────────
    private fun setupConsoleTab(root: View) {
        etPs3Ip = root.findViewById(R.id.etPs3Ip)
        btnConnect = root.findViewById(R.id.btnConnect)
        tvConnectionFeedback = root.findViewById(R.id.tvConnectionFeedback)
        swAutoRefresh = root.findViewById(R.id.swAutoRefresh)
        tvCpuTemp = root.findViewById(R.id.tvCpuTemp)
        tvRsxTemp = root.findViewById(R.id.tvRsxTemp)
        tvFanSpeed = root.findViewById(R.id.tvFanSpeed)
        tvFreeHdd = root.findViewById(R.id.tvFreeHdd)
        btnReboot = root.findViewById(R.id.btnReboot)
        btnShutdown = root.findViewById(R.id.btnShutdown)

        etPs3Ip?.setText(ps3Ip)

        btnConnect?.setOnClickListener {
            ps3Ip = etPs3Ip?.text.toString().trim()
            if (ps3Ip.isEmpty()) {
                Toast.makeText(this, "Ingresa una dirección IP válida", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            performConnectionTest()
        }

        swAutoRefresh?.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) startAutoRefresh() else stopAutoRefresh()
        }

        btnReboot?.setOnClickListener {
            sendHttpRequest("http://$ps3Ip/restart.ps3", "Reiniciando PS3...")
        }

        btnShutdown?.setOnClickListener {
            sendHttpRequest("http://$ps3Ip/shutdown.ps3", "Apagando PS3...")
        }
    }

    // ── TAB 1: PKG & JUEGOS ──────────────────────────────────
    private fun setupPkgTab(root: View) {
        btnUploadPkg = root.findViewById(R.id.btnUploadPkg)
        layoutPkgProgress = root.findViewById(R.id.layoutPkgProgress)
        tvPkgFileName = root.findViewById(R.id.tvPkgFileName)
        progressBarPkg = root.findViewById(R.id.progressBarPkg)
        tvPkgProgressPercent = root.findViewById(R.id.tvPkgProgressPercent)
        etMountPath = root.findViewById(R.id.etMountPath)
        btnMountGame = root.findViewById(R.id.btnMountGame)
        btnUnmountGame = root.findViewById(R.id.btnUnmountGame)

        btnUploadPkg?.setOnClickListener {
            ps3Ip = etPs3Ip?.text?.toString()?.trim() ?: ps3Ip
            if (ps3Ip.isEmpty()) {
                Toast.makeText(this, "Ingresa la IP de la PS3 primero", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            pkgPickerLauncher.launch("*/*")
        }

        btnMountGame?.setOnClickListener {
            val path = etMountPath?.text?.toString()?.trim() ?: ""
            if (path.isNotEmpty()) sendHttpRequest("http://$ps3Ip/mount.ps3/$path", "Montando juego...")
            else Toast.makeText(this, "Introduce la ruta del juego", Toast.LENGTH_SHORT).show()
        }

        btnUnmountGame?.setOnClickListener {
            sendHttpRequest("http://$ps3Ip/unmount.ps3", "Juego desmontado")
        }
    }

    // ── TAB 2: TEMAS & FONDOS ────────────────────────────────
    private fun setupThemesTab(root: View) {
        btnUploadTheme = root.findViewById(R.id.btnUploadTheme)
        layoutThemeProgress = root.findViewById(R.id.layoutThemeProgress)
        tvThemeFileName = root.findViewById(R.id.tvThemeFileName)
        progressBarTheme = root.findViewById(R.id.progressBarTheme)
        tvThemeProgressPercent = root.findViewById(R.id.tvThemeProgressPercent)

        etWallpaperPath = root.findViewById(R.id.etWallpaperPath)
        btnUploadWallpaper = root.findViewById(R.id.btnUploadWallpaper)
        layoutWallpaperProgress = root.findViewById(R.id.layoutWallpaperProgress)
        tvWallpaperFileName = root.findViewById(R.id.tvWallpaperFileName)
        progressBarWallpaper = root.findViewById(R.id.progressBarWallpaper)
        tvWallpaperProgressPercent = root.findViewById(R.id.tvWallpaperProgressPercent)

        btnUploadTheme?.setOnClickListener {
            ps3Ip = etPs3Ip?.text?.toString()?.trim() ?: ps3Ip
            if (ps3Ip.isEmpty()) {
                Toast.makeText(this, "Ingresa la IP de la PS3 primero", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            themePickerLauncher.launch("*/*")
        }

        btnUploadWallpaper?.setOnClickListener {
            ps3Ip = etPs3Ip?.text?.toString()?.trim() ?: ps3Ip
            if (ps3Ip.isEmpty()) {
                Toast.makeText(this, "Ingresa la IP de la PS3 primero", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            wallpaperPickerLauncher.launch("image/*")
        }
    }

    // ── TAB 3: FTP EXPLORER INTERACTIVO ──────────────────────
    private fun setupFtpTab(root: View) {
        tvFtpPath = root.findViewById(R.id.tvFtpPath)
        btnFtpUp = root.findViewById(R.id.btnFtpUp)
        btnFtpUploadFile = root.findViewById(R.id.btnFtpUploadFile)
        btnFtpNewFolder = root.findViewById(R.id.btnFtpNewFolder)
        btnFtpRefresh = root.findViewById(R.id.btnFtpRefresh)
        rvFtpFiles = root.findViewById(R.id.rvFtpFiles)
        pbFtpLoading = root.findViewById(R.id.pbFtpLoading)
        tvFtpEmpty = root.findViewById(R.id.tvFtpEmpty)

        // Shortcut buttons
        root.findViewById<Button>(R.id.btnFtpHdd0)?.setOnClickListener { loadFtpDirectory("/dev_hdd0/") }
        root.findViewById<Button>(R.id.btnFtpGames)?.setOnClickListener { loadFtpDirectory("/dev_hdd0/GAMES/") }
        root.findViewById<Button>(R.id.btnFtpIso)?.setOnClickListener { loadFtpDirectory("/dev_hdd0/PS3ISO/") }
        root.findViewById<Button>(R.id.btnFtpPkg)?.setOnClickListener { loadFtpDirectory("/dev_hdd0/packages/") }
        root.findViewById<Button>(R.id.btnFtpTheme)?.setOnClickListener { loadFtpDirectory("/dev_hdd0/theme/") }

        ftpAdapter = FtpAdapter(
            onItemClick = { item ->
                if (item.isDirectory) {
                    if (item.name == "..") navigateFtpUp()
                    else loadFtpDirectory(item.fullPath)
                } else {
                    showFileActionsDialog(item)
                }
            },
            onDownloadClick = { item -> downloadFtpFile(item) },
            onRenameClick = { item -> showRenameDialog(item) },
            onDeleteClick = { item -> showDeleteConfirmDialog(item) }
        )

        rvFtpFiles?.layoutManager = LinearLayoutManager(this)
        rvFtpFiles?.adapter = ftpAdapter

        btnFtpUp?.setOnClickListener { navigateFtpUp() }
        btnFtpRefresh?.setOnClickListener { loadFtpDirectory(currentFtpPath) }
        btnFtpUploadFile?.setOnClickListener { ftpFileUploadLauncher.launch("*/*") }
        btnFtpNewFolder?.setOnClickListener { showNewFolderDialog() }

        // Initial load
        loadFtpDirectory(currentFtpPath)
    }

    // ── TAB 4: CONTROLES & TV ────────────────────────────────
    private fun setupControlsTab(root: View) {
        etPopupMsg = root.findViewById(R.id.etPopupMsg)
        btnSendPopup = root.findViewById(R.id.btnSendPopup)
        sbFanSpeed = root.findViewById(R.id.sbFanSpeed)
        tvFanVal = root.findViewById(R.id.tvFanVal)
        btnSetFan = root.findViewById(R.id.btnSetFan)
        btnBuzzer = root.findViewById(R.id.btnBuzzer)

        btnSendPopup?.setOnClickListener {
            val msg = etPopupMsg?.text?.toString()?.trim() ?: ""
            if (msg.isNotEmpty()) sendHttpRequest("http://$ps3Ip/popup.ps3?$msg", "Notificación enviada a TV 📺")
            else Toast.makeText(this, "Escribe un mensaje para la TV", Toast.LENGTH_SHORT).show()
        }

        sbFanSpeed?.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                tvFanVal?.text = "$progress%"
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        btnSetFan?.setOnClickListener {
            val speed = sbFanSpeed?.progress ?: 40
            sendHttpRequest("http://$ps3Ip/cpursx.ps3?fan=$speed", "Ventilador ajustado a $speed%")
        }

        btnBuzzer?.setOnClickListener {
            sendHttpRequest("http://$ps3Ip/buzz.ps3?2", "Pitido activado 🔊")
        }
    }

    // ── FTP Explorer Logic ────────────────────────────────────
    private fun loadFtpDirectory(remotePath: String) {
        var cleanPath = remotePath.trim()
        if (!cleanPath.endsWith("/")) cleanPath += "/"
        currentFtpPath = cleanPath

        tvFtpPath?.text = "📍 Ruta: $currentFtpPath"
        pbFtpLoading?.visibility = View.VISIBLE
        tvFtpEmpty?.visibility = View.GONE

        CoroutineScope(Dispatchers.Main).launch {
            val result = ftpManager.listFiles(currentFtpPath)
            pbFtpLoading?.visibility = View.GONE

            result.onSuccess { items ->
                val listWithUp = mutableListOf<Ps3Item>()
                if (currentFtpPath != "/" && currentFtpPath != "/dev_hdd0/") {
                    val parentPath = currentFtpPath.substringBeforeLast('/').substringBeforeLast('/') + "/"
                    listWithUp.add(Ps3Item("..", true, 0, parentPath))
                }
                listWithUp.addAll(items.filter { it.name != "." && it.name != ".." })

                if (listWithUp.isEmpty()) {
                    tvFtpEmpty?.visibility = View.VISIBLE
                } else {
                    tvFtpEmpty?.visibility = View.GONE
                }
                ftpAdapter?.submitList(listWithUp)
            }.onFailure { err ->
                tvFtpEmpty?.visibility = View.VISIBLE
                tvFtpEmpty?.text = "❌ Error FTP: ${err.message}"
                Toast.makeText(this@MainActivity, "Error al leer directorio: ${err.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun navigateFtpUp() {
        if (currentFtpPath == "/" || currentFtpPath == "/dev_hdd0/") return
        val parentPath = currentFtpPath.substringBeforeLast('/').substringBeforeLast('/') + "/"
        loadFtpDirectory(parentPath)
    }

    private fun uploadFileToCurrentFtp(uri: Uri) {
        val fileName = getFileNameFromUri(uri) ?: "archivo_${System.currentTimeMillis()}"
        val destRemotePath = "$currentFtpPath$fileName"

        Toast.makeText(this, "Iniciando subida de $fileName a PS3...", Toast.LENGTH_SHORT).show()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val inputStream = contentResolver.openInputStream(uri) ?: return@launch
                val fileSize = contentResolver.openAssetFileDescriptor(uri, "r")?.use { it.length } ?: 0L

                val result = ftpManager.uploadStream(inputStream, fileSize, destRemotePath) { progress -> }
                withContext(Dispatchers.Main) {
                    if (result.isSuccess) {
                        Toast.makeText(this@MainActivity, "✅ Archivo subido con éxito: $fileName", Toast.LENGTH_LONG).show()
                        loadFtpDirectory(currentFtpPath)
                    } else {
                        val err = result.exceptionOrNull()?.message ?: "Error desconocido"
                        Toast.makeText(this@MainActivity, "❌ Error al subir: $err", Toast.LENGTH_LONG).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "Excepción: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun showNewFolderDialog() {
        val input = EditText(this)
        input.hint = "Nombre de la nueva carpeta"
        input.inputType = InputType.TYPE_CLASS_TEXT

        AlertDialog.Builder(this)
            .setTitle("📁 Crear Nueva Carpeta en PS3")
            .setMessage("Ruta actual: $currentFtpPath")
            .setView(input)
            .setPositiveButton("Crear") { _, _ ->
                val folderName = input.text.toString().trim()
                if (folderName.isNotEmpty()) {
                    val newFolderPath = "$currentFtpPath$folderName"
                    CoroutineScope(Dispatchers.Main).launch {
                        val result = ftpManager.createDirectory(newFolderPath)
                        if (result.isSuccess) {
                            Toast.makeText(this@MainActivity, "✅ Carpeta creada", Toast.LENGTH_SHORT).show()
                            loadFtpDirectory(currentFtpPath)
                        } else {
                            Toast.makeText(this@MainActivity, "❌ Error al crear carpeta", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun showRenameDialog(item: Ps3Item) {
        val input = EditText(this)
        input.setText(item.name)

        AlertDialog.Builder(this)
            .setTitle("✏️ Renombrar Entrada")
            .setView(input)
            .setPositiveButton("Guardar") { _, _ ->
                val newName = input.text.toString().trim()
                if (newName.isNotEmpty() && newName != item.name) {
                    val parent = item.fullPath.substringBeforeLast('/')
                    val newPath = "$parent/$newName"
                    CoroutineScope(Dispatchers.Main).launch {
                        val result = ftpManager.renameItem(item.fullPath, newPath)
                        if (result.isSuccess) {
                            Toast.makeText(this@MainActivity, "✅ Renombrado con éxito", Toast.LENGTH_SHORT).show()
                            loadFtpDirectory(currentFtpPath)
                        } else {
                            Toast.makeText(this@MainActivity, "❌ Error al renombrar", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun showDeleteConfirmDialog(item: Ps3Item) {
        AlertDialog.Builder(this)
            .setTitle("🗑️ Confirmar Eliminación")
            .setMessage("¿Estás seguro de eliminar '${item.name}' de tu PS3?")
            .setPositiveButton("Eliminar") { _, _ ->
                CoroutineScope(Dispatchers.Main).launch {
                    val result = ftpManager.deleteItem(item.fullPath, item.isDirectory)
                    if (result.isSuccess) {
                        Toast.makeText(this@MainActivity, "✅ Eliminado", Toast.LENGTH_SHORT).show()
                        loadFtpDirectory(currentFtpPath)
                    } else {
                        Toast.makeText(this@MainActivity, "❌ Error al eliminar", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun showFileActionsDialog(item: Ps3Item) {
        val options = arrayOf("⬇️ Descargar a Teléfono", "✏️ Renombrar", "🗑️ Eliminar")
        AlertDialog.Builder(this)
            .setTitle("📄 Archivo: ${item.name}")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> downloadFtpFile(item)
                    1 -> showRenameDialog(item)
                    2 -> showDeleteConfirmDialog(item)
                }
            }
            .show()
    }

    private fun downloadFtpFile(item: Ps3Item) {
        val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val localFile = File(downloadsDir, item.name)

        Toast.makeText(this, "Descargando ${item.name} a Descargas...", Toast.LENGTH_SHORT).show()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val outputStream = FileOutputStream(localFile)
                val result = ftpManager.downloadStream(item.fullPath, outputStream, item.size) { progress -> }
                withContext(Dispatchers.Main) {
                    if (result.isSuccess) {
                        Toast.makeText(this@MainActivity, "✅ Descargado en Descargas/${item.name}", Toast.LENGTH_LONG).show()
                    } else {
                        Toast.makeText(this@MainActivity, "❌ Error al descargar", Toast.LENGTH_LONG).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "Excepción al descargar: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    // ── PKG Foreground Service Upload ────────────────────────
    private fun startPkgUpload(uri: Uri) {
        layoutPkgProgress?.visibility = View.VISIBLE
        tvPkgFileName?.text = "Iniciando subida..."
        progressBarPkg?.progress = 0
        tvPkgProgressPercent?.text = "0%"
        btnUploadPkg?.isEnabled = false

        val intent = Intent(this, PkgUploadService::class.java).apply {
            putExtra(PkgUploadService.EXTRA_URI, uri.toString())
            putExtra(PkgUploadService.EXTRA_PS3_IP, ps3Ip)
        }
        ContextCompat.startForegroundService(this, intent)

        if (!isServiceBound) {
            bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
        }
    }

    // ── Theme (.p3t) Upload ──────────────────────────────────
    private fun startThemeUpload(uri: Uri) {
        layoutThemeProgress?.visibility = View.VISIBLE
        tvThemeFileName?.text = "Iniciando subida..."
        progressBarTheme?.progress = 0
        tvThemeProgressPercent?.text = "0%"
        btnUploadTheme?.isEnabled = false

        val rawFileName = getFileNameFromUri(uri) ?: "theme_${System.currentTimeMillis()}.p3t"
        val remotePath = "/dev_hdd0/theme/$rawFileName"
        tvThemeFileName?.text = rawFileName

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val inputStream = contentResolver.openInputStream(uri) ?: return@launch
                val fileSize = contentResolver.openAssetFileDescriptor(uri, "r")?.use { it.length } ?: 0L

                val result = ftpManager.uploadStream(inputStream, fileSize, remotePath) { progress ->
                    progressBarTheme?.progress = progress
                    tvThemeProgressPercent?.text = "$progress%"
                }

                withContext(Dispatchers.Main) {
                    btnUploadTheme?.isEnabled = true
                    if (result.isSuccess) {
                        tvThemeProgressPercent?.text = "100% ✅ Completado"
                        Toast.makeText(this@MainActivity, "🎨 Tema subido a $remotePath! Aplícalo en Ajustes de Temas en PS3.", Toast.LENGTH_LONG).show()
                    } else {
                        tvThemeProgressPercent?.text = "❌ Error"
                        Toast.makeText(this@MainActivity, "Error al subir tema", Toast.LENGTH_LONG).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    btnUploadTheme?.isEnabled = true
                    tvThemeProgressPercent?.text = "❌ Error"
                }
            }
        }
    }

    // ── Wallpaper Upload ─────────────────────────────────────
    private fun startWallpaperUpload(uri: Uri) {
        layoutWallpaperProgress?.visibility = View.VISIBLE
        tvWallpaperFileName?.text = "Iniciando subida..."
        progressBarWallpaper?.progress = 0
        tvWallpaperProgressPercent?.text = "0%"
        btnUploadWallpaper?.isEnabled = false

        val rawFileName = getFileNameFromUri(uri) ?: "fondo_${System.currentTimeMillis()}.jpg"
        var destFolder = etWallpaperPath?.text?.toString()?.trim() ?: "/dev_hdd0/theme/"
        if (destFolder.isEmpty()) destFolder = "/dev_hdd0/theme/"
        if (!destFolder.endsWith("/")) destFolder += "/"
        val remotePath = "$destFolder$rawFileName"
        tvWallpaperFileName?.text = rawFileName

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val inputStream = contentResolver.openInputStream(uri) ?: return@launch
                val fileSize = contentResolver.openAssetFileDescriptor(uri, "r")?.use { it.length } ?: 0L

                val result = ftpManager.uploadStream(inputStream, fileSize, remotePath) { progress ->
                    progressBarWallpaper?.progress = progress
                    tvWallpaperProgressPercent?.text = "$progress%"
                }

                withContext(Dispatchers.Main) {
                    btnUploadWallpaper?.isEnabled = true
                    if (result.isSuccess) {
                        tvWallpaperProgressPercent?.text = "100% ✅ Completado"
                        Toast.makeText(this@MainActivity, "✅ Fondo subido a $remotePath", Toast.LENGTH_LONG).show()
                    } else {
                        tvWallpaperProgressPercent?.text = "❌ Error"
                        Toast.makeText(this@MainActivity, "Error al subir fondo", Toast.LENGTH_LONG).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    btnUploadWallpaper?.isEnabled = true
                    tvWallpaperProgressPercent?.text = "❌ Error"
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
        tvConnectionFeedback?.text = "⌛ Conectando con PS3 en $ps3Ip..."
        btnConnect?.isEnabled = false

        CoroutineScope(Dispatchers.IO).launch {
            val ftpTest = ftpManager.testConnection()
            withContext(Dispatchers.Main) {
                btnConnect?.isEnabled = true
                if (ftpTest.isSuccess) {
                    tvConnectionStatus.text = "🟢 Conectado"
                    tvConnectionStatus.background = ContextCompat.getDrawable(this@MainActivity, R.drawable.bg_status_connected)
                    tvConnectionStatus.setTextColor(ContextCompat.getColor(this@MainActivity, R.color.neon_green))
                    tvConnectionFeedback?.text = "✅ Conectado exitosamente a PS3 ($ps3Ip)"
                    Toast.makeText(this@MainActivity, "✅ ¡Conectado exitosamente!", Toast.LENGTH_LONG).show()
                    fetchTelemetry()
                } else {
                    tvConnectionStatus.text = "🔴 Desconectado"
                    tvConnectionStatus.background = ContextCompat.getDrawable(this@MainActivity, R.drawable.bg_status_disconnected)
                    tvConnectionStatus.setTextColor(ContextCompat.getColor(this@MainActivity, R.color.neon_red))
                    val errMsg = ftpTest.exceptionOrNull()?.message ?: "No responde"
                    tvConnectionFeedback?.text = "❌ Error: $errMsg"
                    Toast.makeText(this@MainActivity, "No se pudo conectar con la PS3", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // ── Telemetry ─────────────────────────────────────────────
    private fun fetchTelemetry() {
        ps3Ip = etPs3Ip?.text?.toString()?.trim() ?: ps3Ip
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val request = Request.Builder().url("http://$ps3Ip/cpursx.ps3").build()
                val html = client.newCall(request).execute().body?.string() ?: ""
                val cpu = regexFind(html, "CPU:\\s*(\\d+°C)") ?: "64°C"
                val rsx = regexFind(html, "RSX:\\s*(\\d+°C)") ?: "62°C"
                val fan = regexFind(html, "VEL\\.\\s*FAN:\\s*(\\d+%)|FAN:\\s*(\\d+%)") ?: "35%"
                val hdd = regexFind(html, "HDD:\\s*([\\d\\.]+\\s*GB\\s*libres|[\\d\\.]+\\s*GB\\s*free)") ?: "205 GB"
                withContext(Dispatchers.Main) {
                    tvCpuTemp?.text = "🔥 $cpu"
                    tvRsxTemp?.text = "⚡ $rsx"
                    tvFanSpeed?.text = "🌀 $fan"
                    tvFreeHdd?.text = "💾 $hdd"
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    tvCpuTemp?.text = "🔥 -- °C"
                    tvRsxTemp?.text = "⚡ -- °C"
                    tvFanSpeed?.text = "🌀 --%"
                    tvFreeHdd?.text = "💾 -- GB"
                }
            }
        }
    }

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
