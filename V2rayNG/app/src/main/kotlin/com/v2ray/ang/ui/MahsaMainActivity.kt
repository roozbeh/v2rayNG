package com.v2ray.ang.ui

import android.Manifest
import android.content.*
import android.net.Uri
import android.net.VpnService
import androidx.recyclerview.widget.LinearLayoutManager
import android.view.Menu
import android.view.MenuItem
import com.tbruyelle.rxpermissions.RxPermissions
import com.v2ray.ang.R
import android.os.Bundle
import android.text.TextUtils
import android.view.KeyEvent
import com.v2ray.ang.AppConfig
import android.content.res.ColorStateList
import com.google.android.material.navigation.NavigationView
import androidx.core.content.ContextCompat
import androidx.core.view.GravityCompat
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.recyclerview.widget.ItemTouchHelper
import android.util.Log
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import com.google.gson.Gson
import com.tencent.mmkv.MMKV
import com.v2ray.ang.AppConfig.ANG_PACKAGE
import com.v2ray.ang.BuildConfig
import com.v2ray.ang.databinding.ActivityMahsaMainBinding
import com.v2ray.ang.databinding.ActivityMainBinding
import com.v2ray.ang.dto.EConfigType
import com.v2ray.ang.dto.ServerConfig
import com.v2ray.ang.dto.V2rayConfig
import com.v2ray.ang.dto.VmessQRCode
import com.v2ray.ang.extension.toast
import rx.Observable
import rx.android.schedulers.AndroidSchedulers
import java.util.concurrent.TimeUnit
import com.v2ray.ang.helper.SimpleItemTouchHelperCallback
import com.v2ray.ang.service.V2RayServiceManager
import com.v2ray.ang.util.*
import com.v2ray.ang.viewmodel.MainViewModel
import kotlinx.coroutines.*
import me.drakeet.support.toast.ToastCompat
import java.io.File
import java.io.FileOutputStream

class MahsaMainActivity : BaseActivity() {
    private lateinit var binding: ActivityMahsaMainBinding

    private val mainStorage by lazy { MMKV.mmkvWithID(MmkvManager.ID_MAIN, MMKV.MULTI_PROCESS_MODE) }
    private val settingsStorage by lazy { MMKV.mmkvWithID(MmkvManager.ID_SETTING, MMKV.MULTI_PROCESS_MODE) }
    private val requestVpnPermission = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (it.resultCode == RESULT_OK) {
            startV2Ray()
        }
    }

    private var isConnected = false;

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMahsaMainBinding.inflate(layoutInflater)
        val view = binding.root
        setContentView(view)

        binding.btnConnect.setOnClickListener {
            onConnectClicked();
        }

        //TODO: this should come from server

//        var json_str = "{\"configType\":\"VMESS\",\"outboundBean\":{\"mux\":{\"concurrency\":8,\"enabled\":false},\"protocol\":\"vmess\",\"settings\":{\"vnext\":[{\"address\":\"192.248.173.113\",\"users\":[{\"alterId\":0,\"encryption\":\"\",\"flow\":\"\",\"id\":\"01a37cbd-8eb2-4ecc-a52f-0814dcab177d\",\"security\":\"auto\",\"level\":8}],\"port\":38637}]},\"streamSettings\":{\"network\":\"ws\",\"security\":\"none\",\"wsSettings\":{\"headers\":{\"Host\":\"\"},\"path\":\"/\"}},\"tag\":\"proxy\"},\"remarks\":\"5685297461\",\"subscriptionId\":\"\",\"configVersion\":3,\"addedTime\":1667535304448}"
        var str = "vmess://ewogICJ2IjogIjIiLAogICJwcyI6ICJ0ZXN0X2FuZHJvaWQiLAogICJhZGQiOiAiOTUuMTc5LjIyNi45OCIsCiAgInBvcnQiOiAyMzk4MSwKICAiaWQiOiAiNmZkYmIzOGMtNjkxNi00Y2QyLWE5ZTQtNzc5YWIxM2FhODA0IiwKICAiYWlkIjogMCwKICAibmV0IjogIndzIiwKICAidHlwZSI6ICJub25lIiwKICAiaG9zdCI6ICIiLAogICJwYXRoIjogIi8iLAogICJ0bHMiOiAibm9uZSIKfQ=="
        AngConfigManager.importBatchConfig(str, "", false);
        var serverList = MmkvManager.decodeServerList()
        if (serverList.size > 0) {
            var guid = serverList.get(0)
            mainStorage?.encode(MmkvManager.KEY_SELECTED_SERVER, guid)
        }

//        var config = ServerConfig.create(EConfigType.VMESS)
//        val streamSetting = config.outboundBean?.streamSettings
//        var fingerprint = streamSetting?.tlsSettings?.fingerprint
//        if (!AngConfigManager.tryParseNewVmess(str, config, true)) {
//            if (str.indexOf("?") > 0) {
//                if (!AngConfigManager.tryResolveVmess4Kitsunebi(str, config)) {
//                    return R.string.toast_incorrect_protocol
//                }
//            } else {
//                var result = str.replace(EConfigType.VMESS.protocolScheme, "")
//                result = Utils.decode(result)
//                if (TextUtils.isEmpty(result)) {
//                    return R.string.toast_decoding_failed
//                }
//                val vmessQRCode = Gson().fromJson(result, VmessQRCode::class.java)
//                // Although VmessQRCode fields are non null, looks like Gson may still create null fields
//                if (TextUtils.isEmpty(vmessQRCode.add)
//                    || TextUtils.isEmpty(vmessQRCode.port)
//                    || TextUtils.isEmpty(vmessQRCode.id)
//                    || TextUtils.isEmpty(vmessQRCode.net)
//                ) {
//                    return R.string.toast_incorrect_protocol
//                }
//
//                config.remarks = vmessQRCode.ps
//                config.outboundBean?.settings?.vnext?.get(0)?.let { vnext ->
//                    vnext.address = vmessQRCode.add
//                    vnext.port = Utils.parseInt(vmessQRCode.port)
//                    vnext.users[0].id = vmessQRCode.id
//                    vnext.users[0].security = if (TextUtils.isEmpty(vmessQRCode.scy)) V2rayConfig.DEFAULT_SECURITY else vmessQRCode.scy
//                    vnext.users[0].alterId = Utils.parseInt(vmessQRCode.aid)
//                }
//                val sni = streamSetting.populateTransportSettings(vmessQRCode.net, vmessQRCode.type, vmessQRCode.host,
//                    vmessQRCode.path, vmessQRCode.path, vmessQRCode.host, vmessQRCode.path, vmessQRCode.type, vmessQRCode.path)
//
//
//                streamSetting.populateTlsSettings(vmessQRCode.tls, true,
//                    if (TextUtils.isEmpty(vmessQRCode.sni)) sni else vmessQRCode.sni, fingerprint, vmessQRCode.alpn)
//            }
//        }

//        var json_str = "{\"configType\":\"VMESS\",\"outboundBean\":{\"mux\":{\"concurrency\":8,\"enabled\":false},\"protocol\":\"vmess\",\"settings\":{\"vnext\":[{\"address\":\"192.248.173.113\",\"users\":[{\"alterId\":0,\"encryption\":\"\",\"flow\":\"\",\"id\":\"01a37cbd-8eb2-4ecc-a52f-0814dcab177d\",\"security\":\"auto\",\"level\":8}],\"port\":38637}]},\"streamSettings\":{\"network\":\"ws\",\"security\":\"none\",\"wsSettings\":{\"headers\":{\"Host\":\"\"},\"path\":\"/\"}},\"tag\":\"proxy\"},\"remarks\":\"5685297461\",\"subscriptionId\":\"\",\"configVersion\":3,\"addedTime\":1667535304448}"
//        var config: ServerConfig? = null
//        config = ServerConfig.create(EConfigType.VMESS)
//        var guid = MmkvManager.encodeServerConfig("", config)
//        mainStorage.encode(MmkvManager.KEY_SELECTED_SERVER, guid)
    }


    fun startV2Ray() {
        if (mainStorage?.decodeString(MmkvManager.KEY_SELECTED_SERVER).isNullOrEmpty()) {
            return
        }
//        toast(R.string.toast_services_start)
        V2RayServiceManager.startV2Ray(this)

    }

    fun onConnectClicked() {

        binding.btnConnect.isEnabled = false
        isConnected = !isConnected;

        if (isConnected) {
            val intent = VpnService.prepare(this)
            if (intent == null) {
                startV2Ray()
            } else {
                requestVpnPermission.launch(intent)
            }
            binding.btnConnect.text = "Disconnect"
        } else {
            Utils.stopVService(this)
            binding.btnConnect.text = "Connect"
        }

        binding.btnConnect.isEnabled = true
    }


}