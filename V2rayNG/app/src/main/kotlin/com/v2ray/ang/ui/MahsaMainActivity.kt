package com.v2ray.ang.ui

import android.content.*
import android.os.Bundle
import android.util.Log
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.MutableLiveData
import com.tencent.mmkv.MMKV
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.databinding.ActivityMahsaMainBinding
import com.v2ray.ang.extension.toast
import com.v2ray.ang.service.V2RayServiceManager
import com.v2ray.ang.util.*
import kotlinx.coroutines.*


class MahsaMainActivity : BaseActivity() {
    private lateinit var binding: ActivityMahsaMainBinding

    private val TAG = "MahsaMainActivity"

    private val mainStorage by lazy { MMKV.mmkvWithID(MmkvManager.ID_MAIN, MMKV.MULTI_PROCESS_MODE) }
    private val settingsStorage by lazy { MMKV.mmkvWithID(MmkvManager.ID_SETTING, MMKV.MULTI_PROCESS_MODE) }
    private val requestVpnPermission = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (it.resultCode == RESULT_OK) {
            connectToVPN()
        }
    }

    private var isConnected = false;
    val isRunning by lazy { MutableLiveData<Boolean>() }
    var delaysMap =  mutableMapOf<String, Long>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMahsaMainBinding.inflate(layoutInflater)
        val view = binding.root
        setContentView(view)

        binding.btnConnect.setOnClickListener {
            onConnectClicked();
        }

        //TODO: this should come from server

//        var str = "vmess://ewogICJ2IjogIjIiLAogICJwcyI6ICJ0ZXN0X2FuZHJvaWQiLAogICJhZGQiOiAiOTUuMTc5LjIyNi45OCIsCiAgInBvcnQiOiAyMzk4MSwKICAiaWQiOiAiNmZkYmIzOGMtNjkxNi00Y2QyLWE5ZTQtNzc5YWIxM2FhODA0IiwKICAiYWlkIjogMCwKICAibmV0IjogIndzIiwKICAidHlwZSI6ICJub25lIiwKICAiaG9zdCI6ICIiLAogICJwYXRoIjogIi8iLAogICJ0bHMiOiAibm9uZSIKfQ=="
//        AngConfigManager.importBatchConfig(str, "", false);
//        var serverList = MmkvManager.decodeServerList()
//        if (serverList.size > 0) {
//            var guid = serverList.get(0)
//            mainStorage?.encode(MmkvManager.KEY_SELECTED_SERVER, guid)
//        }



//        application.registerReceiver(mMsgReceiver, IntentFilter(AppConfig.BROADCAST_ACTION_ACTIVITY))
//        mMsgReceiver.mainActivity = this
//        MessageUtil.sendMsg2Service(application, AppConfig.MSG_REGISTER_CLIENT, "")
    }

    override fun onDestroy() {
        super.onDestroy()
        application.unregisterReceiver(mMsgReceiver)
    }

    private val mMsgReceiver = object : BroadcastReceiver() {

        public var mainActivity : MahsaMainActivity? = null

        override fun onReceive(ctx: Context?, intent: Intent?) {
            when (intent?.getIntExtra("key", 0)) {
                AppConfig.MSG_STATE_RUNNING -> {
                    isRunning.value = true
                }
                AppConfig.MSG_STATE_NOT_RUNNING -> {
                    isRunning.value = false
                }
                AppConfig.MSG_STATE_START_SUCCESS -> {
                    getApplication().toast(R.string.toast_services_success)
                    isRunning.value = true
                    binding.btnConnect.isEnabled = true
                }
                AppConfig.MSG_STATE_START_FAILURE -> {
                    getApplication().toast(R.string.toast_services_failure)
                    isRunning.value = false
                    binding.btnConnect.isEnabled = true
                }
                AppConfig.MSG_STATE_STOP_SUCCESS -> {
                    isRunning.value = false
                }
                AppConfig.MSG_MEASURE_DELAY_SUCCESS -> {
//                    updateTestResultAction.value = intent.getStringExtra("content")
                }
                AppConfig.MSG_MEASURE_CONFIG_SUCCESS -> {
                    val resultPair = intent.getSerializableExtra("content") as Pair<String, Long>
                    var guid = resultPair.first
                    var delay = resultPair.second
                    Log.i(TAG, "delay ready fig config ($guid): $delay")
                    mainActivity?.delaysMap?.put(guid.toString(),  delay.toLong())
                    binding.editTextTextPersonName.text = "code responded in " + delay.toLong() + "ms"
                    mainActivity?.checkDelaysCompleted(true)
                }
            }
        }
    }


//    fun startV2Ray() {
//        // 1. Test All configs in storage
//        // 2. If any of them worked
//        //        Choose the fastest one if any of them worked
//        //    Else
//        // 3.     get fresh set of keys
//        // 4.     Test All configs in storage
//        // 5.     If any of them worked
//        // 6.          Choose the fastest one if any of them worked
//        binding.btnConnect.isEnabled = false
//        testAllConnectionsInStorage(true)
//
////        if (mainStorage?.decodeString(MmkvManager.KEY_SELECTED_SERVER).isNullOrEmpty()) {
////            return
////        }
//////        toast(R.string.toast_services_start)
////        V2RayServiceManager.startV2Ray(this)
//    }

    private val tcpingTestScope by lazy { CoroutineScope(Dispatchers.IO) }

    fun onConnectClicked() {
        val myIntent = Intent(this@MahsaMainActivity, CodeCompareActivity::class.java)
        this@MahsaMainActivity.startActivity(myIntent)
//        binding.btnConnect.isEnabled = false
//        isConnected = !isConnected;
//
//        if (isConnected) {
//            val intent = VpnService.prepare(this)
//            if (intent == null) {
//                connectToVPN()
//            } else {
//                requestVpnPermission.launch(intent) /* will call connectToVPN() */
//            }
//            binding.btnConnect.text = "Disconnect"
//        } else {
//            Utils.stopVService(this)
//            binding.btnConnect.text = "Connect"
//        }

//        binding.btnConnect.isEnabled = true
    }

    fun connectToVPN() {
        binding.btnConnect.isEnabled = false
        // 1. Test All configs in storage
        // 2. If any of them worked
        //        Choose the fastest one if any of them worked
        //    Else
        // 3.     get fresh set of keys
        // 4.     Test All configs in storage
        // 5.     If any of them worked
        // 6.          Choose the fastest one if any of them worked
        testAllConnectionsInStorage(true)
    }

    fun testAllConnectionsInStorage(getCodesFromServerIfFail : Boolean) {
        tcpingTestScope.coroutineContext[Job]?.cancelChildren()
        delaysMap.clear()

        var serverList = MmkvManager.decodeServerList()
        if (serverList.size == 0 && getCodesFromServerIfFail) {
            getNewConfigsFromServer()
        }

        this@MahsaMainActivity.runOnUiThread(java.lang.Runnable {
            binding.editTextTextPersonName.text = "Testing " + serverList.size + " saved configs ..."
        })
        for (guid in serverList) {
            delaysMap.put(guid, -1)
            val config = V2rayConfigUtil.getV2rayConfig(application, guid)
            if (config.status) {
                MessageUtil.sendMsg2TestService(application, AppConfig.MSG_MEASURE_CONFIG, Pair(guid, config.content))
            }
        }
        tcpingTestScope.launch {
            delay(5000)

            this@MahsaMainActivity.runOnUiThread(java.lang.Runnable {
                binding.editTextTextPersonName.text = "5 Seconds timeout completed, using the best one"
            })
            if (!checkDelaysCompleted(false)) {
                this@MahsaMainActivity.runOnUiThread(java.lang.Runnable {
                    binding.editTextTextPersonName.text = "Timeout! Getting new codes from server"
                })

                if (getCodesFromServerIfFail) {
                    getNewConfigsFromServer()
                } else {
                    this@MahsaMainActivity.runOnUiThread(java.lang.Runnable {
                        binding.btnConnect.isEnabled = true
                    })
                }
            }
        }
    }

    fun getNewConfigsFromServer() {
        MmkvManager.removeAllServer()
        var str = "vmess://eyJ2IjoiMiIsInBzIjoiNTY4NTI5NzQ2MSIsImFkZCI6IjE1OS4xMDAuMzAuMTY0IiwicG9ydCI6MzE0MjEsImlkIjoiMTc5YmJhMDktZmQ4Yy00NmI0LTk1NmMtNWE3ZmVlMzAxY2I4IiwiYWlkIjowLCJuZXQiOiJ0Y3AiLCJ0eXBlIjoiaHR0cCIsImhvc3QiOiIiLCJwYXRoIjoiLyIsInRscyI6Im5vbmUifQ==\n"+
                "trojan://9Y705ZMUKy@mahsa212ktkh.mahsaaminivpn.com:48470#5685297461\n" +
                "vless://8b151e98-000e-4ff9-a68f-fa0d5902f943@mahsa212ktkh.mahsaaminivpn.com:47660?type=tcp&security=xtls&flow=xtls-rprx-direct#5685297461\n" +
                "vmess://eyJ2IjoiMiIsInBzIjoiNTY4NTI5NzQ2MSIsImFkZCI6IjE4OC4xMjEuMTE1LjEwMyIsInBvcnQiOjM0NTAxLCJpZCI6IjY2ZDJlODdkLTAxZjQtNDJhMS05OGFhLWZiNGFlOGVkYWFlMyIsImFpZCI6MCwibmV0IjoidGNwIiwidHlwZSI6Imh0dHAiLCJob3N0IjoiIiwicGF0aCI6Ii8iLCJ0bHMiOiJub25lIn0=\n" +
                "trojan://497JVzIb0H@mahsa155dvug.mahsaaminivpn.com:30503#5685297461\n" +
                "vless://3d861810-c90a-4c2d-9644-1c1f7ace6acd@mahsa155dvug.mahsaaminivpn.com:36023?type=tcp&security=xtls&flow=xtls-rprx-direct#5685297461\n" +
                "vmess://eyJ2IjoiMiIsInBzIjoiNTY4NTI5NzQ2MSIsImFkZCI6Im1haHNhMjEzMnNicS5zbmFwcGZvb2Qud29yayIsInBvcnQiOjIwODIsImlkIjoiZjE3OTk4YjYtMjgwMC00OTMxLWEzODEtODJmZDJmODY4OTE5IiwiYWlkIjowLCJuZXQiOiJ3cyIsInR5cGUiOiJub25lIiwiaG9zdCI6IiIsInBhdGgiOiIvIiwidGxzIjoibm9uZSJ9\n" +
                "vless://b6a217ac-bad6-4f73-b971-a456d34c0980@mahsa2132sbq.snappfood.work:8443?type=ws&security=tls&path=%2F#5685297461";


        AngConfigManager.importBatchConfig(str, "", false);

        testAllConnectionsInStorage(false)
    }

    fun checkDelaysCompleted(waitForAll : Boolean) : Boolean {
        var minDelay : Long = 5000
        var minGUID = ""
        for (entry in delaysMap.entries.iterator()) {
            val guid = entry.key
            val delay = entry.value
            if (delay<0 && waitForAll) {
                Log.i(TAG, "at least one server is not completed (guid: $guid)")
                return false
            }
            if (delay != -1L && delay < minDelay) {
                minDelay = delay
                minGUID = guid
            }
        }

        if (minGUID == "") {
            return false
        }

        mainStorage?.encode(MmkvManager.KEY_SELECTED_SERVER, minGUID)

        toast(R.string.toast_services_start)
        binding.editTextTextPersonName.text = "Connecting to fastest ..."
        V2RayServiceManager.startV2Ray(this)
        return true
    }
}