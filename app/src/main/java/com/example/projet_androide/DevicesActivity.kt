package com.example.projet_androide

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.ArrayAdapter
import android.widget.CheckBox
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.browser.customtabs.CustomTabsClient
import androidx.browser.customtabs.CustomTabsIntent
import androidx.browser.customtabs.CustomTabsServiceConnection
import androidx.browser.customtabs.CustomTabsSession
import androidx.core.widget.NestedScrollView
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.switchmaterial.SwitchMaterial
import com.example.projet_androide.data.api.Api
import com.example.projet_androide.data.api.ApiRoutes
import com.example.projet_androide.data.model.Device
import com.example.projet_androide.data.model.DevicesResponse
import com.example.projet_androide.data.model.HouseAccessUser
import com.example.projet_androide.data.model.HouseSummary
import com.example.projet_androide.data.model.HouseUserPayload
import com.example.projet_androide.data.storage.TokenStore
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

class DevicesActivity : AppCompatActivity() {

    companion object {
        private const val FILTER_ALL = "Tous"
        private const val FILTER_ON = "Allumés / Ouverts"
        private const val FILTER_OFF = "Éteints / Fermés"

        private const val TYPE_LIGHT = "light"
        private const val TYPE_SHUTTER = "shutter"
        private const val TYPE_DOOR = "door"
        private const val TYPE_GARAGE = "garage"

        private val DEVICE_STATES = listOf(FILTER_ALL, FILTER_ON, FILTER_OFF)
        private val COMMAND_ON_CANDIDATES = listOf("on", "open", "up", "turn on", "turn_on")
        private val COMMAND_OFF_CANDIDATES = listOf("off", "close", "down", "turn off", "turn_off")
    }

    private enum class FloorZone { GROUND, FIRST }

    private data class CommandPayload(val command: String)

    private data class CommandAttempt(
        val url: String,
        val method: String,
        val payload: CommandPayload? = null
    )

    private var houseId: Int = -1
    private var token: String? = null
    private var currentUsername: String = ""
    private lateinit var houseUrl: String

    private lateinit var webHouse: WebView
    private lateinit var scrollDevices: NestedScrollView
    private lateinit var spinnerHouse: Spinner
    private lateinit var spinnerType: Spinner
    private lateinit var spinnerState: Spinner
    private lateinit var containerDevices: LinearLayout

    private lateinit var tvHouseId: TextView
    private lateinit var tvOwner: TextView
    private lateinit var tvLightsOn: TextView
    private lateinit var tvShuttersOpen: TextView
    private lateinit var tvDoorsOpen: TextView
    private lateinit var tvGarageOpen: TextView
    private lateinit var ivHouseQr: ImageView
    private var houseQrBitmap: Bitmap? = null

    private lateinit var panelComponents: View
    private lateinit var panelGroup: View
    private lateinit var panelUsers: View

    private lateinit var btnSelectAll: MaterialButton
    private lateinit var btnBatchOn: MaterialButton
    private lateinit var btnBatchOff: MaterialButton
    private lateinit var btnClearSelection: MaterialButton
    private lateinit var btnGroupLightsOn: MaterialButton
    private lateinit var btnGroupLightsOff: MaterialButton
    private lateinit var btnGroupShuttersOpen: MaterialButton
    private lateinit var btnGroupShuttersClose: MaterialButton
    private lateinit var btnGroupGarageOpen: MaterialButton
    private lateinit var btnGroupGarageClose: MaterialButton
    private lateinit var btnGroupLightsGroundOn: MaterialButton
    private lateinit var btnGroupLightsGroundOff: MaterialButton
    private lateinit var btnGroupLightsFirstOn: MaterialButton
    private lateinit var btnGroupLightsFirstOff: MaterialButton
    private lateinit var btnGroupShuttersGroundOpen: MaterialButton
    private lateinit var btnGroupShuttersGroundClose: MaterialButton
    private lateinit var btnGroupShuttersFirstOpen: MaterialButton
    private lateinit var btnGroupShuttersFirstClose: MaterialButton
    private lateinit var etUserLogin: EditText
    private lateinit var btnAddUser: MaterialButton
    private lateinit var btnRemoveUser: MaterialButton
    private lateinit var btnRefreshUsers: MaterialButton
    private lateinit var containerHouseUsers: LinearLayout

    private val allDevices = arrayListOf<Device>()
    private val filteredDevices = arrayListOf<Device>()
    private val houseUsers = arrayListOf<HouseAccessUser>()
    private val houses = arrayListOf<HouseSummary>()
    private val selectedDeviceIds = linkedSetOf<String>()
    private val pendingDeviceIds = linkedSetOf<String>()

    private var selectedType: String = FILTER_ALL
    private var selectedState: String = FILTER_ALL

    private var isLoadingDevices = false
    private var isBatchRunning = false
    private var devicesLoadedAtLeastOnce = false

    private var pendingBrowserInitRetry = false
    private var alreadyOpenedCustomTabForInit = false
    private var selectedHouseOwner = false
    private var houseSpinnerReady = false

    private var customTabsSession: CustomTabsSession? = null
    private var serviceConnection: CustomTabsServiceConnection? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private val liveRefreshRunnable = object : Runnable {
        override fun run() {
            if (!isFinishing && !isDestroyed) {
                loadDevices(silent = true)
                mainHandler.postDelayed(this, 5000L)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_devices)

        houseId = intent.getIntExtra("houseId", -1)
        val tokenStore = TokenStore(this)
        token = tokenStore.getToken()
        currentUsername = tokenStore.getUsername()?.trim().orEmpty()

        if (token.isNullOrBlank()) {
            Toast.makeText(this, "Token manquant", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        houseUrl = if (houseId > 0) ApiRoutes.HOUSE_BROWSER(houseId) else ApiRoutes.BASE

        val toolbar = findViewById<MaterialToolbar>(R.id.toolbarDevices)
        toolbar.title = "Maison #$houseId"
        toolbar.menu.findItem(R.id.action_username)?.title = tokenStore.getUsername()?.ifBlank { "Utilisateur" } ?: "Utilisateur"
        toolbar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_logout -> {
                    doLogout()
                    true
                }
                else -> false
            }
        }

        webHouse = findViewById(R.id.webHouse)
        scrollDevices = findViewById(R.id.scrollDevices)
        spinnerHouse = findViewById(R.id.spinnerHouse)
        spinnerType = findViewById(R.id.spinnerType)
        spinnerState = findViewById(R.id.spinnerState)
        containerDevices = findViewById(R.id.containerDevices)

        tvHouseId = findViewById(R.id.tvHouseId)
        tvOwner = findViewById(R.id.tvOwner)
        tvLightsOn = findViewById(R.id.tvLightsOn)
        tvShuttersOpen = findViewById(R.id.tvShuttersOpen)
        tvDoorsOpen = findViewById(R.id.tvDoorsOpen)
        tvGarageOpen = findViewById(R.id.tvGarageOpen)
        ivHouseQr = findViewById(R.id.ivHouseQr)

        panelComponents = findViewById(R.id.panelComponents)
        panelGroup = findViewById(R.id.panelGroup)
        panelUsers = findViewById(R.id.panelUsers)

        btnSelectAll = findViewById(R.id.btnSelectAll)
        btnBatchOn = findViewById(R.id.btnBatchOn)
        btnBatchOff = findViewById(R.id.btnBatchOff)
        btnClearSelection = findViewById(R.id.btnClearSelection)
        btnGroupLightsOn = findViewById(R.id.btnGroupLightsOn)
        btnGroupLightsOff = findViewById(R.id.btnGroupLightsOff)
        btnGroupShuttersOpen = findViewById(R.id.btnGroupShuttersOpen)
        btnGroupShuttersClose = findViewById(R.id.btnGroupShuttersClose)
        btnGroupGarageOpen = findViewById(R.id.btnGroupGarageOpen)
        btnGroupGarageClose = findViewById(R.id.btnGroupGarageClose)
        btnGroupLightsGroundOn = findViewById(R.id.btnGroupLightsGroundOn)
        btnGroupLightsGroundOff = findViewById(R.id.btnGroupLightsGroundOff)
        btnGroupLightsFirstOn = findViewById(R.id.btnGroupLightsFirstOn)
        btnGroupLightsFirstOff = findViewById(R.id.btnGroupLightsFirstOff)
        btnGroupShuttersGroundOpen = findViewById(R.id.btnGroupShuttersGroundOpen)
        btnGroupShuttersGroundClose = findViewById(R.id.btnGroupShuttersGroundClose)
        btnGroupShuttersFirstOpen = findViewById(R.id.btnGroupShuttersFirstOpen)
        btnGroupShuttersFirstClose = findViewById(R.id.btnGroupShuttersFirstClose)
        etUserLogin = findViewById(R.id.etUserLogin)
        btnAddUser = findViewById(R.id.btnAddUser)
        btnRemoveUser = findViewById(R.id.btnRemoveUser)
        btnRefreshUsers = findViewById(R.id.btnRefreshUsers)
        containerHouseUsers = findViewById(R.id.containerHouseUsers)

        // Demande utilisateur : onglet composants fermé au démarrage
        panelComponents.visibility = View.GONE
        panelGroup.visibility = View.GONE
        panelUsers.visibility = View.GONE

        tvHouseId.text = "Maison : #$houseId"
        tvOwner.text = "Propriétaire : (à venir)"
        renderHouseQrCode()
        ivHouseQr.setOnClickListener { showQrDialog() }

        setupAccordion(findViewById(R.id.btnToggleComponents), panelComponents)
        setupAccordion(findViewById(R.id.btnToggleGroup), panelGroup)
        setupAccordion(findViewById(R.id.btnToggleUsers), panelUsers)
        setupGroupButtons()

        btnAddUser.setOnClickListener { addUserAccess() }
        btnRemoveUser.setOnClickListener { removeUserAccess() }
        btnRefreshUsers.setOnClickListener { loadHouseUsers(silent = false) }

        btnSelectAll.setOnClickListener {
            selectedDeviceIds.clear()
            selectedDeviceIds.addAll(filteredDevices.map { it.id })
            renderDeviceRows()
            updateBatchButtonsState()
        }

        btnClearSelection.setOnClickListener {
            selectedDeviceIds.clear()
            renderDeviceRows()
            updateBatchButtonsState()
        }

        btnBatchOn.setOnClickListener { executeBatchCommand(targetOn = true) }
        btnBatchOff.setOnClickListener { executeBatchCommand(targetOn = false) }

        setupStateSpinner()
        setupTypeSpinner(listOf(FILTER_ALL))
        setupHouseSpinner(emptyList())

        setupWebView(webHouse)
        warmupChromeAndPrefetch(houseUrl)
        loadHouses()
    }

    override fun onResume() {
        super.onResume()
        mainHandler.postDelayed(liveRefreshRunnable, 1200L)
        if (pendingBrowserInitRetry) {
            pendingBrowserInitRetry = false
            loadDevices()
        }
    }

    override fun onPause() {
        super.onPause()
        mainHandler.removeCallbacks(liveRefreshRunnable)
    }

    override fun onDestroy() {
        super.onDestroy()
        mainHandler.removeCallbacks(liveRefreshRunnable)
        serviceConnection?.let {
            runCatching { unbindService(it) }
        }
    }

    private fun doLogout() {
        TokenStore(this).clearActiveSession()
        Toast.makeText(this, "Déconnecté", Toast.LENGTH_SHORT).show()
        val i = Intent(this, MainActivity::class.java)
        i.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(i)
        finish()
    }

    private fun setupAccordion(button: MaterialButton, panel: View) {
        button.setOnClickListener {
            val willShow = panel.visibility != View.VISIBLE
            panel.visibility = if (willShow) View.VISIBLE else View.GONE
            if (panel.id == R.id.panelComponents && willShow) applyFiltersAndRender()
            if (panel.id == R.id.panelUsers && willShow) loadHouseUsers(silent = true)
            if (willShow) {
                mainHandler.post { scrollDevices.smoothScrollTo(0, panel.bottom) }
            }
        }
    }

    private fun setupWebView(webView: WebView) {
        val s = webView.settings
        s.javaScriptEnabled = true
        s.domStorageEnabled = true
        s.cacheMode = WebSettings.LOAD_DEFAULT
        s.useWideViewPort = true
        s.loadWithOverviewMode = true
        s.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
        webView.isNestedScrollingEnabled = false

        webView.webChromeClient = WebChromeClient()
        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean = false
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                view?.let { hideLargeQrInWebView(it) }
            }
        }
    }

    private fun warmupChromeAndPrefetch(url: String) {
        serviceConnection?.let {
            runCatching { unbindService(it) }
        }
        serviceConnection = object : CustomTabsServiceConnection() {
            override fun onCustomTabsServiceConnected(name: android.content.ComponentName, client: CustomTabsClient) {
                client.warmup(0L)
                customTabsSession = client.newSession(null)
                customTabsSession?.mayLaunchUrl(Uri.parse(url), null, null)
            }

            override fun onServiceDisconnected(name: android.content.ComponentName) {
                customTabsSession = null
            }
        }

        try {
            CustomTabsClient.bindCustomTabsService(this, "com.android.chrome", serviceConnection!!)
        } catch (e: Exception) {
            Log.d("API", "CustomTabs bind failed: ${e.message}")
        }
    }

    private fun openHouseInCustomTabForInit(url: String) {
        if (alreadyOpenedCustomTabForInit) return
        alreadyOpenedCustomTabForInit = true

        pendingBrowserInitRetry = true
        openHouseInCustomTab(url)
    }

    private fun openHouseInCustomTab(url: String) {
        val intent = CustomTabsIntent.Builder(customTabsSession).setShowTitle(true).build()
        intent.launchUrl(this, Uri.parse(url))
    }

    private fun setupHouseSpinner(items: List<String>) {
        spinnerHouse.adapter = createSpinnerAdapter(items.ifEmpty { listOf("Chargement...") })
        spinnerHouse.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (!houseSpinnerReady || position !in houses.indices) return
                val picked = houses[position]
                if (picked.houseId != houseId) switchToHouse(picked)
            }

            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) = Unit
        }
    }

    private fun loadHouses() {
        val t = token ?: return
        Api().get<List<HouseSummary>>(
            ApiRoutes.HOUSES,
            onSuccess = { code, body ->
                if (code != 200 || body.isNullOrEmpty()) {
                    Toast.makeText(this, "Impossible de charger les maisons ($code)", Toast.LENGTH_SHORT).show()
                    return@get
                }

                houses.clear()
                houses.addAll(body)

                val labels = houses.map {
                    val access = if (it.owner) "Propriétaire" else "Accès partagé"
                    "Maison #${it.houseId} • $access"
                }
                setupHouseSpinner(labels)

                val initialIndex = houses.indexOfFirst { it.houseId == houseId }.takeIf { it >= 0 }
                    ?: houses.indexOfFirst { it.owner }.takeIf { it >= 0 }
                    ?: 0

                houseSpinnerReady = false
                spinnerHouse.setSelection(initialIndex, false)
                val picked = houses[initialIndex]
                switchToHouse(picked)
                houseSpinnerReady = true
            },
            securityToken = t
        )
    }

    private fun switchToHouse(house: HouseSummary) {
        houseId = house.houseId
        selectedHouseOwner = house.owner
        houseUrl = ApiRoutes.HOUSE_BROWSER(houseId)

        findViewById<MaterialToolbar>(R.id.toolbarDevices).title = "Maison #$houseId"
        tvHouseId.text = "Maison : #$houseId"
        renderHouseQrCode()
        webHouse.loadUrl(houseUrl)
        warmupChromeAndPrefetch(houseUrl)

        selectedDeviceIds.clear()
        pendingDeviceIds.clear()
        allDevices.clear()
        filteredDevices.clear()
        houseUsers.clear()
        containerDevices.removeAllViews()
        containerHouseUsers.removeAllViews()
        panelComponents.visibility = View.GONE
        panelGroup.visibility = View.GONE
        panelUsers.visibility = View.GONE
        updateUsersAccessState()
        loadHouseUsers(silent = true)
        loadDevices()
    }

    private fun setupGroupButtons() {
        btnGroupLightsOn.setOnClickListener { executeGroupCommand(TYPE_LIGHT, true) }
        btnGroupLightsOff.setOnClickListener { executeGroupCommand(TYPE_LIGHT, false) }
        btnGroupShuttersOpen.setOnClickListener { executeGroupCommand(TYPE_SHUTTER, true) }
        btnGroupShuttersClose.setOnClickListener { executeGroupCommand(TYPE_SHUTTER, false) }
        btnGroupGarageOpen.setOnClickListener { executeGroupCommand(TYPE_GARAGE, true) }
        btnGroupGarageClose.setOnClickListener { executeGroupCommand(TYPE_GARAGE, false) }

        btnGroupLightsGroundOn.setOnClickListener { executeGroupCommandByFloor(TYPE_LIGHT, FloorZone.GROUND, true) }
        btnGroupLightsGroundOff.setOnClickListener { executeGroupCommandByFloor(TYPE_LIGHT, FloorZone.GROUND, false) }
        btnGroupLightsFirstOn.setOnClickListener { executeGroupCommandByFloor(TYPE_LIGHT, FloorZone.FIRST, true) }
        btnGroupLightsFirstOff.setOnClickListener { executeGroupCommandByFloor(TYPE_LIGHT, FloorZone.FIRST, false) }
        btnGroupShuttersGroundOpen.setOnClickListener { executeGroupCommandByFloor(TYPE_SHUTTER, FloorZone.GROUND, true) }
        btnGroupShuttersGroundClose.setOnClickListener { executeGroupCommandByFloor(TYPE_SHUTTER, FloorZone.GROUND, false) }
        btnGroupShuttersFirstOpen.setOnClickListener { executeGroupCommandByFloor(TYPE_SHUTTER, FloorZone.FIRST, true) }
        btnGroupShuttersFirstClose.setOnClickListener { executeGroupCommandByFloor(TYPE_SHUTTER, FloorZone.FIRST, false) }
    }

    private fun executeGroupCommand(typeKey: String, targetOn: Boolean, excludeGarage: Boolean = false) {
        if (isBatchRunning) return
        val targets = allDevices.filter { d ->
            val typeMatch = d.isType(typeKey)
            typeMatch && (!excludeGarage || !d.isType(TYPE_GARAGE))
        }

        if (targets.isEmpty()) {
            Toast.makeText(this, "Aucun composant compatible", Toast.LENGTH_SHORT).show()
            return
        }

        isBatchRunning = true
        updateBatchButtonsState()
        renderDeviceRows()
        executeCommandAtIndex(targets, 0, targetOn, successCount = 0)
    }

    private fun executeGroupCommandByFloor(typeKey: String, floor: FloorZone, targetOn: Boolean) {
        if (isBatchRunning) return
        val floorTargets = allDevices.filter { d ->
            d.isType(typeKey) && resolveFloorZone(d) == floor
        }
        if (floorTargets.isEmpty()) {
            val floorLabel = if (floor == FloorZone.GROUND) "rez-de-chaussée" else "1er étage"
            Toast.makeText(this, "Aucun ${typeKey} détecté pour $floorLabel", Toast.LENGTH_SHORT).show()
            return
        }

        isBatchRunning = true
        updateBatchButtonsState()
        renderDeviceRows()
        executeCommandAtIndex(floorTargets, 0, targetOn, successCount = 0)
    }

    private fun setupTypeSpinner(types: List<String>) {
        spinnerType.adapter = createSpinnerAdapter(types)
        val idx = types.indexOf(selectedType)
        if (idx >= 0) spinnerType.setSelection(idx)

        spinnerType.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
                selectedType = types[position]
                applyFiltersAndRender()
            }

            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) = Unit
        }
    }

    private fun setupStateSpinner() {
        spinnerState.adapter = createSpinnerAdapter(DEVICE_STATES)
        spinnerState.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
                selectedState = DEVICE_STATES[position]
                applyFiltersAndRender()
            }

            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) = Unit
        }
    }

    private fun createSpinnerAdapter(items: List<String>): ArrayAdapter<String> {
        return ArrayAdapter(this, R.layout.item_spinner_selected, items).also {
            it.setDropDownViewResource(R.layout.item_spinner_dropdown)
        }
    }

    private fun loadDevices(silent: Boolean = false) {
        if (houseId <= 0) return
        if (isLoadingDevices) return
        val t = token ?: return
        isLoadingDevices = true

        Api().get<DevicesResponse>(
            ApiRoutes.DEVICES(houseId),
            onSuccess = { code, body ->
                isLoadingDevices = false
                if (code == 200 && body != null) {
                    devicesLoadedAtLeastOnce = true
                    alreadyOpenedCustomTabForInit = false
                    allDevices.clear()
                    allDevices.addAll(body.devices)
                    pendingDeviceIds.clear()

                    val types = mutableListOf(FILTER_ALL)
                    types.addAll(allDevices.map { it.type }.distinct().sorted())
                    setupTypeSpinner(types)

                    updateInfoPanel()
                    applyFiltersAndRender()
                } else {
                    if (code == 500 && !devicesLoadedAtLeastOnce) {
                        Toast.makeText(this, "Initialisation maison…", Toast.LENGTH_SHORT).show()
                        openHouseInCustomTabForInit(houseUrl)
                    } else if (!silent) {
                        Toast.makeText(this, "Erreur devices ($code)", Toast.LENGTH_SHORT).show()
                    }
                }
            },
            securityToken = t
        )
    }

    private fun loadHouseUsers(silent: Boolean = false) {
        if (houseId <= 0) return
        val t = token ?: return
        Api().get<List<HouseAccessUser>>(
            ApiRoutes.HOUSE_USERS(houseId),
            onSuccess = { code, body ->
                if (code == 200 && body != null) {
                    houseUsers.clear()
                    houseUsers.addAll(body.sortedBy { it.userLogin.lowercase() })
                    renderHouseUsers()
                } else if (!silent) {
                    Toast.makeText(this, "Erreur utilisateurs ($code)", Toast.LENGTH_SHORT).show()
                }
            },
            securityToken = t
        )
    }

    private fun addUserAccess() {
        if (!selectedHouseOwner) {
            Toast.makeText(this, "Seul le propriétaire peut donner un accès", Toast.LENGTH_SHORT).show()
            return
        }
        val login = etUserLogin.text.toString().trim()
        if (login.isBlank()) {
            Toast.makeText(this, "Saisis un login utilisateur", Toast.LENGTH_SHORT).show()
            return
        }
        val t = token ?: return
        Api().post<HouseUserPayload>(
            ApiRoutes.HOUSE_USERS(houseId),
            HouseUserPayload(login),
            onSuccess = { code ->
                when (code) {
                    200 -> {
                        Toast.makeText(this, "Accès accordé", Toast.LENGTH_SHORT).show()
                        etUserLogin.text?.clear()
                        loadHouseUsers(silent = true)
                    }
                    409 -> Toast.makeText(this, "Utilisateur déjà associé", Toast.LENGTH_SHORT).show()
                    403 -> Toast.makeText(this, "Action non autorisée", Toast.LENGTH_SHORT).show()
                    else -> Toast.makeText(this, "Erreur ajout ($code)", Toast.LENGTH_SHORT).show()
                }
            },
            securityToken = t
        )
    }

    private fun removeUserAccess() {
        if (!selectedHouseOwner) {
            Toast.makeText(this, "Seul le propriétaire peut retirer un accès", Toast.LENGTH_SHORT).show()
            return
        }
        val login = etUserLogin.text.toString().trim()
        if (login.isBlank()) {
            Toast.makeText(this, "Saisis un login utilisateur", Toast.LENGTH_SHORT).show()
            return
        }

        val targetUser = houseUsers.firstOrNull { it.userLogin.equals(login, ignoreCase = true) }
        if (targetUser != null && targetUser.owner > 0) {
            Toast.makeText(this, "Impossible: un propriétaire ne peut pas perdre son accès", Toast.LENGTH_SHORT).show()
            return
        }
        if (selectedHouseOwner && currentUsername.equals(login, ignoreCase = true)) {
            Toast.makeText(this, "Impossible de supprimer ton propre accès propriétaire", Toast.LENGTH_SHORT).show()
            return
        }

        val t = token ?: return
        Api().delete(
            ApiRoutes.HOUSE_USERS(houseId),
            HouseUserPayload(login),
            onSuccess = { code ->
                when (code) {
                    200 -> {
                        Toast.makeText(this, "Accès supprimé", Toast.LENGTH_SHORT).show()
                        etUserLogin.text?.clear()
                        loadHouseUsers(silent = true)
                    }
                    403 -> Toast.makeText(this, "Action non autorisée", Toast.LENGTH_SHORT).show()
                    else -> Toast.makeText(this, "Erreur suppression ($code)", Toast.LENGTH_SHORT).show()
                }
            },
            securityToken = t
        )
    }

    private fun renderHouseUsers() {
        containerHouseUsers.removeAllViews()
        if (houseUsers.isEmpty()) {
            val emptyView = TextView(this).apply {
                text = "Aucun utilisateur associé."
                setTextColor(getColor(R.color.app_text_secondary))
                setPadding(8, 8, 8, 8)
            }
            containerHouseUsers.addView(emptyView)
            return
        }
        houseUsers.forEach { user ->
            val row = TextView(this).apply {
                val role = if (user.owner > 0) "Propriétaire" else "Invité"
                text = "• ${user.userLogin} ($role)"
                setTextColor(getColor(R.color.app_text_primary))
                setPadding(8, 6, 8, 6)
            }
            containerHouseUsers.addView(row)
        }
    }

    private fun updateInfoPanel() {
        val lightsOn = allDevices.count { it.isType(TYPE_LIGHT) && (it.power ?: 0) > 0 }
        val shuttersOpen = allDevices.count { it.isType(TYPE_SHUTTER) && (it.opening ?: 0) > 0 }
        val doorsOpen = allDevices.count { it.isType(TYPE_DOOR) && !it.isType(TYPE_GARAGE) && (it.opening ?: 0) > 0 }
        val garageOpen = allDevices.count { it.isType(TYPE_GARAGE) && (it.opening ?: 0) > 0 }

        tvOwner.text = if (selectedHouseOwner) "Accès : Propriétaire" else "Accès : Partagé"
        tvLightsOn.text = "Lumières allumées : $lightsOn"
        tvShuttersOpen.text = "Volets ouverts : $shuttersOpen"
        tvDoorsOpen.text = "Portes ouvertes : $doorsOpen"
        tvGarageOpen.text = "Garage ouvert : $garageOpen"
    }

    private fun renderHouseQrCode() {
        val qrSizePx = (150 * resources.displayMetrics.density).toInt()
        val qrBitmap = createStyledHouseQrBitmap(houseUrl, qrSizePx)
        houseQrBitmap = qrBitmap
        if (qrBitmap != null) {
            ivHouseQr.setImageBitmap(qrBitmap)
        } else {
            ivHouseQr.setImageDrawable(null)
        }
    }

    private fun showQrDialog() {
        val bitmap = houseQrBitmap ?: return
        val content = FrameLayout(this).apply {
            val pad = (16 * resources.displayMetrics.density).toInt()
            setPadding(pad, pad, pad, pad)
            setBackgroundColor(Color.WHITE)
        }
        val image = ImageView(this).apply {
            setImageBitmap(bitmap)
            adjustViewBounds = true
            scaleType = ImageView.ScaleType.FIT_CENTER
            minimumHeight = (280 * resources.displayMetrics.density).toInt()
        }
        content.addView(
            image,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )

        MaterialAlertDialogBuilder(this)
            .setTitle("QR Maison")
            .setView(content)
            .setNegativeButton("Fermer", null)
            .setPositiveButton("Piloter la maison") { _, _ ->
                openHouseInCustomTab(houseUrl)
            }
            .show()
    }

    private fun createStyledHouseQrBitmap(content: String, qrSizePx: Int): Bitmap? {
        val qrBitmap = createQrCode(content, qrSizePx) ?: return null
        val density = resources.displayMetrics.density
        val padding = (10 * density).toInt()
        val captionHeight = (28 * density).toInt()
        val radius = 18f * density

        val width = qrBitmap.width + (padding * 2)
        val height = qrBitmap.height + (padding * 2) + captionHeight

        val result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)

        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.FILL
        }
        canvas.drawRoundRect(RectF(0f, 0f, width.toFloat(), height.toFloat()), radius, radius, bgPaint)
        canvas.drawBitmap(qrBitmap, padding.toFloat(), padding.toFloat(), null)

        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = 18f * density
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val textBaseline = (qrBitmap.height + (padding * 2) + (captionHeight * 0.72f))
        canvas.drawText("Pilotez moi !", width / 2f, textBaseline, textPaint)
        return result
    }

    private fun hideLargeQrInWebView(webView: WebView) {
        val js = """
            (function () {
              function hide(el) {
                if (!el || !el.style) return;
                el.style.display = 'none';
                el.style.visibility = 'hidden';
                el.style.opacity = '0';
                el.style.pointerEvents = 'none';
              }

              var nodes = Array.prototype.slice.call(document.querySelectorAll('*'));
              nodes.forEach(function (el) {
                var id = (el.id || '').toLowerCase();
                var cls = ((el.className || '') + '').toLowerCase();
                var alt = ((el.alt || '') + '').toLowerCase();
                var src = ((el.src || '') + '').toLowerCase();
                var txt = ((el.textContent || '') + '').toLowerCase();
                var qrHint = id.indexOf('qr') >= 0
                  || cls.indexOf('qr') >= 0
                  || alt.indexOf('qr') >= 0
                  || src.indexOf('qr') >= 0
                  || txt.indexOf('scan me') >= 0;
                var logoHint = id.indexOf('polytech') >= 0
                  || cls.indexOf('polytech') >= 0
                  || alt.indexOf('polytech') >= 0
                  || src.indexOf('polytech') >= 0
                  || txt.indexOf('polytech') >= 0
                  || txt.indexOf('dijon') >= 0;
                if (qrHint) hide(el);
                if (logoHint) hide(el);
              });

              ['img', 'canvas', 'svg'].forEach(function (tag) {
                var els = document.querySelectorAll(tag);
                els.forEach(function (el) {
                  var r = el.getBoundingClientRect();
                  if (!r || r.width < 120 || r.height < 120) return;
                  var ratio = r.width / r.height;
                  var isSquare = ratio > 0.8 && ratio < 1.25;
                  var inTopArea = r.top < window.innerHeight * 0.85;
                  var inLeftSide = r.left < window.innerWidth * 0.65;
                  if (isSquare && inTopArea && inLeftSide) hide(el);
                });
              });
            })();
        """.trimIndent()
        webView.evaluateJavascript(js, null)
    }

    private fun createQrCode(content: String, sizePx: Int): Bitmap? {
        return try {
            val hints = mapOf(
                EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.H,
                EncodeHintType.MARGIN to 1
            )
            val matrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, sizePx, sizePx, hints)
            val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
            for (x in 0 until sizePx) {
                for (y in 0 until sizePx) {
                    bitmap.setPixel(x, y, if (matrix[x, y]) Color.BLACK else Color.WHITE)
                }
            }
            bitmap
        } catch (_: Exception) {
            null
        }
    }

    private fun applyFiltersAndRender() {
        val filtered = allDevices.filter { d ->
            val okType = selectedType == FILTER_ALL || d.type == selectedType
            val isOn = (d.power ?: 0) > 0 || (d.opening ?: 0) > 0
            val okState = when (selectedState) {
                FILTER_ON -> isOn
                FILTER_OFF -> !isOn
                else -> true
            }
            okType && okState
        }

        filteredDevices.clear()
        filteredDevices.addAll(filtered)
        selectedDeviceIds.retainAll(filteredDevices.map { it.id }.toSet())
        renderDeviceRows()
        updateBatchButtonsState()
    }

    private fun renderDeviceRows() {
        containerDevices.removeAllViews()
        if (filteredDevices.isEmpty()) {
            val emptyView = TextView(this).apply {
                text = "Aucun composant pour ce filtre"
                setTextColor(getColor(R.color.app_text_secondary))
                textSize = 14f
                setPadding(8, 12, 8, 12)
            }
            containerDevices.addView(emptyView)
            return
        }

        filteredDevices.forEach { device ->
            val row = layoutInflater.inflate(R.layout.item_device, containerDevices, false)
            val cb = row.findViewById<CheckBox>(R.id.cbSelectDevice)
            val tvName = row.findViewById<TextView>(R.id.tvDeviceName)
            val tvState = row.findViewById<TextView>(R.id.tvDeviceState)
            val sw = row.findViewById<SwitchMaterial>(R.id.swDeviceState)

            val on = deviceIsOn(device)
            val hasAction = resolveCommand(device, true) != null || resolveCommand(device, false) != null
            val isPending = pendingDeviceIds.contains(device.id)

            tvName.text = "${device.type} (#${device.id})"
            tvState.text = when {
                device.opening != null -> "Ouverture: ${device.opening}%"
                device.power != null -> "Puissance: ${device.power}%"
                else -> "État: ${if (on) "1" else "0"}"
            }

            cb.setOnCheckedChangeListener(null)
            cb.isChecked = selectedDeviceIds.contains(device.id)
            cb.isEnabled = !isBatchRunning && !isPending
            cb.setOnCheckedChangeListener { _, checked ->
                if (checked) selectedDeviceIds.add(device.id) else selectedDeviceIds.remove(device.id)
                updateBatchButtonsState()
            }

            sw.setOnCheckedChangeListener(null)
            sw.isEnabled = hasAction && !isBatchRunning && !isPending
            sw.isChecked = on
            sw.text = if (on) "1" else "0"
            sw.setOnCheckedChangeListener { _, isChecked ->
                if (!hasAction || pendingDeviceIds.contains(device.id)) return@setOnCheckedChangeListener

                pendingDeviceIds.add(device.id)
                renderDeviceRows()
                sendCommandToDevice(device, isChecked) { success ->
                    runOnUiThread {
                        pendingDeviceIds.remove(device.id)
                        if (success) {
                            applyInstantDeviceState(device.id, isChecked)
                            refreshDevicesSoon()
                        } else {
                            renderDeviceRows()
                        }
                    }
                }
            }

            containerDevices.addView(row)
        }
    }

    private fun executeBatchCommand(targetOn: Boolean) {
        if (isBatchRunning) return
        val targets = filteredDevices.filter { selectedDeviceIds.contains(it.id) }
        if (targets.isEmpty()) {
            Toast.makeText(this, "Sélectionne au moins un composant", Toast.LENGTH_SHORT).show()
            return
        }

        isBatchRunning = true
        updateBatchButtonsState()
        renderDeviceRows()
        executeCommandAtIndex(targets, 0, targetOn, successCount = 0)
    }

    private fun executeCommandAtIndex(targets: List<Device>, index: Int, targetOn: Boolean, successCount: Int) {
        if (index >= targets.size) {
            isBatchRunning = false
            selectedDeviceIds.clear()
            Toast.makeText(this, "$successCount/${targets.size} commandes exécutées", Toast.LENGTH_SHORT).show()
            refreshDevicesSoon()
            renderDeviceRows()
            updateBatchButtonsState()
            return
        }

        sendCommandToDevice(targets[index], targetOn) { success ->
            if (success) applyInstantDeviceState(targets[index].id, targetOn)
            executeCommandAtIndex(targets, index + 1, targetOn, if (success) successCount + 1 else successCount)
        }
    }

    private fun sendCommandToDevice(device: Device, targetOn: Boolean, onDone: (Boolean) -> Unit) {
        val t = token
        if (t.isNullOrBlank()) {
            onDone(false)
            return
        }

        val command = resolveCommand(device, targetOn)
        if (command == null) {
            onDone(false)
            return
        }

        val encodedCommand = Uri.encode(command)
        val payload = CommandPayload(command)
        val attempts = listOf(
            CommandAttempt(ApiRoutes.DEVICE_COMMAND_PATH(houseId, device.id, encodedCommand), "PUT"),
            CommandAttempt(ApiRoutes.DEVICE_COMMANDS_PATH(houseId, device.id, encodedCommand), "PUT"),
            CommandAttempt(ApiRoutes.DEVICE_COMMAND_QUERY(houseId, device.id, encodedCommand), "PUT"),
            CommandAttempt(ApiRoutes.DEVICE_COMMAND(houseId, device.id), "PUT", payload),
            CommandAttempt(ApiRoutes.DEVICE_COMMANDS(houseId, device.id), "PUT", payload),
            CommandAttempt(ApiRoutes.DEVICE_COMMAND(houseId, device.id), "POST", payload),
            CommandAttempt(ApiRoutes.DEVICE_COMMANDS(houseId, device.id), "POST", payload),
            CommandAttempt(ApiRoutes.DEVICE(houseId, device.id), "PUT", payload)
        )

        tryCommandWithFallback(
            attempts = attempts,
            index = 0,
            tokenValue = t,
            onResult = { success, lastCode ->
                if (!success) {
                    Toast.makeText(this, "Commande ${device.id} refusée (${lastCode ?: -1})", Toast.LENGTH_SHORT).show()
                }
                onDone(success)
            }
        )
    }

    private fun tryCommandWithFallback(
        attempts: List<CommandAttempt>,
        index: Int,
        tokenValue: String,
        onResult: (Boolean, Int?) -> Unit
    ) {
        if (index >= attempts.size) {
            onResult(false, null)
            return
        }

        val attempt = attempts[index]
        if (attempt.payload != null) {
            Api().request<Unit, CommandPayload>(
                attempt.url,
                method = attempt.method,
                data = attempt.payload,
                onSuccess = { code, _ ->
                    if (code in 200..299) {
                        onResult(true, code)
                    } else if (code in listOf(400, 404, 405) && index < attempts.lastIndex) {
                        tryCommandWithFallback(attempts, index + 1, tokenValue, onResult)
                    } else {
                        onResult(false, code)
                    }
                },
                securityToken = tokenValue
            )
        } else {
            Api().request<Unit>(
                attempt.url,
                method = attempt.method,
                onSuccess = { code ->
                    if (code in 200..299) {
                        onResult(true, code)
                    } else if (code in listOf(400, 404, 405) && index < attempts.lastIndex) {
                        tryCommandWithFallback(attempts, index + 1, tokenValue, onResult)
                    } else {
                        onResult(false, code)
                    }
                },
                securityToken = tokenValue
            )
        }
    }

    private fun resolveCommand(device: Device, targetOn: Boolean): String? {
        val candidates = if (targetOn) COMMAND_ON_CANDIDATES else COMMAND_OFF_CANDIDATES
        val normalizedCommands = device.availableCommands.associateBy { normalizeCommand(it) }
        for (candidate in candidates) {
            val found = normalizedCommands[normalizeCommand(candidate)]
            if (found != null) return found
        }
        return null
    }

    private fun updateBatchButtonsState() {
        val hasSelection = selectedDeviceIds.isNotEmpty()
        btnBatchOn.isEnabled = hasSelection && !isBatchRunning
        btnBatchOff.isEnabled = hasSelection && !isBatchRunning
        btnSelectAll.isEnabled = !isBatchRunning && filteredDevices.isNotEmpty()
        btnClearSelection.isEnabled = hasSelection && !isBatchRunning

        val groupEnabled = !isBatchRunning && allDevices.isNotEmpty()
        btnGroupLightsOn.isEnabled = groupEnabled
        btnGroupLightsOff.isEnabled = groupEnabled
        btnGroupShuttersOpen.isEnabled = groupEnabled
        btnGroupShuttersClose.isEnabled = groupEnabled
        btnGroupGarageOpen.isEnabled = groupEnabled
        btnGroupGarageClose.isEnabled = groupEnabled
        btnGroupLightsGroundOn.isEnabled = groupEnabled
        btnGroupLightsGroundOff.isEnabled = groupEnabled
        btnGroupLightsFirstOn.isEnabled = groupEnabled
        btnGroupLightsFirstOff.isEnabled = groupEnabled
        btnGroupShuttersGroundOpen.isEnabled = groupEnabled
        btnGroupShuttersGroundClose.isEnabled = groupEnabled
        btnGroupShuttersFirstOpen.isEnabled = groupEnabled
        btnGroupShuttersFirstClose.isEnabled = groupEnabled
    }

    private fun updateUsersAccessState() {
        val ownerActionsEnabled = selectedHouseOwner
        btnAddUser.isEnabled = ownerActionsEnabled
        btnRemoveUser.isEnabled = ownerActionsEnabled
        etUserLogin.isEnabled = ownerActionsEnabled
    }

    private fun refreshDevicesSoon(delayMs: Long = 350L) {
        mainHandler.postDelayed({ loadDevices(silent = true) }, delayMs)
    }

    private fun applyInstantDeviceState(deviceId: String, targetOn: Boolean) {
        val index = allDevices.indexOfFirst { it.id == deviceId }
        if (index < 0) return

        val current = allDevices[index]
        val updated = when {
            current.power != null -> current.copy(power = if (targetOn) 100 else 0)
            current.opening != null -> current.copy(opening = if (targetOn) 100 else 0)
            else -> current
        }
        allDevices[index] = updated
        updateInfoPanel()
        applyFiltersAndRender()
    }

    private fun normalizeCommand(value: String): String = value.lowercase().replace("_", " ").trim()

    private fun Device.isType(typeKey: String): Boolean {
        val haystack = "${type.lowercase()} ${id.lowercase()}"
        return when (typeKey) {
            TYPE_LIGHT -> listOf("light", "lumi", "lampe").any { haystack.contains(it) }
            TYPE_SHUTTER -> listOf("shutter", "volet", "blind").any { haystack.contains(it) }
            TYPE_DOOR -> listOf("door", "porte", "entry", "gate").any { haystack.contains(it) }
            TYPE_GARAGE -> listOf("garage").any { haystack.contains(it) }
            else -> haystack.contains(typeKey.lowercase())
        }
    }

    private fun resolveFloorZone(device: Device): FloorZone? {
        val text = "${device.id} ${device.type}".lowercase()

        val groundKeywords = listOf("rdc", "rez", "rez-de-chauss", "ground")
        val firstKeywords = listOf("1er", "etage", "étage", "first")
        if (groundKeywords.any { text.contains(it) }) return FloorZone.GROUND
        if (firstKeywords.any { text.contains(it) }) return FloorZone.FIRST

        // Common naming pattern: #Shutter 1.x (RDC), #Shutter 2.x (1er étage)
        val levelPrefix = Regex("""([12])\s*[._-]\s*\d+""").find(text)?.groupValues?.getOrNull(1)?.toIntOrNull()
        if (levelPrefix == 1) return FloorZone.GROUND
        if (levelPrefix == 2) return FloorZone.FIRST

        // Fallback: single level digit near shutter/light naming (1 => RDC, 2 => 1er)
        val namedLevel = Regex("""(?:shutter|light|volet|lumi[eè]re)\D*([12])""").find(text)
            ?.groupValues?.getOrNull(1)?.toIntOrNull()
        if (namedLevel == 1) return FloorZone.GROUND
        if (namedLevel == 2) return FloorZone.FIRST

        // Last fallback: last number in id/type (0 => RDC, 1 => 1er)
        val lastNumber = Regex("""(\d+)""").findAll(text)
            .mapNotNull { it.groupValues.getOrNull(1)?.toIntOrNull() }
            .lastOrNull()
        return when (lastNumber) {
            0 -> FloorZone.GROUND
            1 -> FloorZone.FIRST
            else -> null
        }
    }

    private fun deviceIsOn(device: Device): Boolean = (device.power ?: 0) > 0 || (device.opening ?: 0) > 0
}
