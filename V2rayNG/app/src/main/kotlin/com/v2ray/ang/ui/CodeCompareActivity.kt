package com.v2ray.ang.ui

import android.content.BroadcastReceiver
import android.content.ContentValues.TAG
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.VpnService
import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.tencent.mmkv.MMKV
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.databinding.ActivityCodeCompareBinding
import com.v2ray.ang.databinding.ActivityMahsaMainBinding
import com.v2ray.ang.extension.toast
import com.v2ray.ang.service.V2RayServiceManager
import com.v2ray.ang.util.*
import kotlinx.coroutines.*
import java.io.BufferedInputStream
import java.io.BufferedReader
import java.io.File.separator
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.Vector

class CodeCompareActivity : AppCompatActivity() {
    private val serverConnection by lazy { CoroutineScope(Dispatchers.IO) }
    private lateinit var binding: ActivityCodeCompareBinding

    var serverCodes =  Vector<String>();
    lateinit var adapterCodes: ArrayList<CodeDetail>
    var delaysMap =  mutableMapOf<String, Long>()
    lateinit var adapter : RecyclerView.Adapter<CodeRecyclerAdapter.ViewHolder>

    private val mainStorage by lazy { MMKV.mmkvWithID(MmkvManager.ID_MAIN, MMKV.MULTI_PROCESS_MODE) }
    private var isConnected = false;
    private val requestVpnPermission = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (it.resultCode == RESULT_OK) {
            startV2Ray()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_code_compare)

        binding = ActivityCodeCompareBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnContinue.setOnClickListener {
            onContinueClicked();
        }

        // Lookup the recyclerview in activity layout
        val rvContacts = findViewById<View>(R.id.rvCodes) as RecyclerView
        // Create adapter passing in the sample user data
        adapterCodes = CodeDetail.createEmptyCodeList()
        adapter = CodeRecyclerAdapter(adapterCodes)

        // Attach the adapter to the recyclerview to populate items
        rvContacts.adapter = adapter
        // Set layout manager to position the items
        rvContacts.layoutManager = LinearLayoutManager(this)


        binding.spinner.visibility = View.VISIBLE
        binding.btnContinue.isEnabled = false

        application.registerReceiver(mMsgReceiver, IntentFilter(AppConfig.BROADCAST_ACTION_ACTIVITY))
        mMsgReceiver.mainActivity = this
        MessageUtil.sendMsg2Service(application, AppConfig.MSG_REGISTER_CLIENT, "")

        serverConnection.launch {
            getCodesFromServer()

            withContext (Dispatchers.Main) {
                showCodesInRecycleView()
            }

            testCodesForConnectivity()
        }
    }

    private val mMsgReceiver = object : BroadcastReceiver() {

        public var mainActivity : CodeCompareActivity? = null

        override fun onReceive(ctx: Context?, intent: Intent?) {
            when (intent?.getIntExtra("key", 0)) {
                AppConfig.MSG_MEASURE_DELAY_SUCCESS -> {
                    println("MSG_MEASURE_DELAY_SUCCESS")
//                    updateTestResultAction.value = intent.getStringExtra("content")
                }
                AppConfig.MSG_MEASURE_CONFIG_SUCCESS -> {
                    println("MSG_MEASURE_CONFIG_SUCCESS")

                    val resultPair = intent.getSerializableExtra("content") as Pair<String, Long>
                    var guid = resultPair.first
                    var delay = resultPair.second
                    Log.i(TAG, "delay ready fig config ($guid): $delay")
                    mainActivity?.updateDelayFor(guid.toString(), delay.toLong())
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()

        serverConnection.cancel()
    }

    suspend fun getCodesFromServer() {
        val url = URL("http://android.mahsaaminivpn.com/codes/")
        val urlConnection: HttpURLConnection = url.openConnection() as HttpURLConnection
        try {
            val inputStream = BufferedInputStream(urlConnection.getInputStream())
            val content = StringBuilder()
            val reader = BufferedReader(inputStream.reader())
            try {
                var line = reader.readLine()
                while (line != null) {
                    println("line received: $line")
                    if (line.isNotEmpty()) {
                        content.append(line)
                        serverCodes.add(line)
                    }
                    line = reader.readLine()
                }
            } finally {
                reader.close()
            }

            println("Content received: $content")
        } finally {
            urlConnection.disconnect()
        }
    }

    fun showCodesInRecycleView() {
        binding.spinner.visibility = View.INVISIBLE

        for (i in 1..adapter.itemCount) {
            adapterCodes.removeAt(0)
            adapter.notifyItemRemoved(0)
        }
        for ((index, value) in serverCodes.iterator().withIndex()) {
            var displayLabel = value
            if (displayLabel.length > 15) {
                displayLabel = displayLabel.take(15)
            }

            adapterCodes.add(CodeDetail(displayLabel, "", "loading"))
            adapter.notifyItemInserted(index)
        }
    }

    fun testCodesForConnectivity() {
        MmkvManager.removeAllServer()

        val codesStr = serverCodes.joinToString(separator="\n" )
        AngConfigManager.importBatchConfig(codesStr, "", false);

        var serverList = MmkvManager.decodeServerList()
        for ((index, value) in serverList.iterator().withIndex()) {
            adapterCodes[index].guid = value
        }
        for (guid in serverList) {
            delaysMap.put(guid, -1)
            val config = V2rayConfigUtil.getV2rayConfig(application, guid)
            if (config.status) {
                println("OnCreate finished")
                MessageUtil.sendMsg2TestService(application, AppConfig.MSG_MEASURE_CONFIG, Pair(guid, config.content))
            }
        }
    }

    fun updateDelayFor(guid: String, delay: Long) {
        var allDone = true

        for ((index, value) in adapterCodes.iterator().withIndex()) {
            if (value.guid == guid) {
                adapterCodes[index].code_ping = delay.toString()
                adapter.notifyItemChanged(index)
            }
            if (adapterCodes[index].code_ping == CodeDetail.kLoading) {
                allDone = false
            }
        }

        if (allDone) {
            MessageUtil.sendMsg2TestService(getApplication(), AppConfig.MSG_MEASURE_CONFIG_CANCEL, "")

            var minDelay : Long = 100000
            var minGUID = ""

            for ((index, value) in adapterCodes.iterator().withIndex()) {
                var delay = adapterCodes[index].code_ping.toLong()
                if ((delay != (-1).toLong()) && (delay < minDelay)) {
                    minDelay = delay
                    minGUID = adapterCodes[index].guid
                }
            }

            if (minGUID != "") {
                mainStorage?.encode(MmkvManager.KEY_SELECTED_SERVER, minGUID)
                binding.btnContinue.isEnabled = true
            }
        }
    }

    fun onContinueClicked() {
        isConnected = !isConnected;

        if (isConnected) {
            val intent = VpnService.prepare(this)
            if (intent == null) {
                startV2Ray()
            } else {
                requestVpnPermission.launch(intent) /* will call connectToVPN() */
            }
            binding.btnContinue.text = "Disconnect"
        } else {
            Utils.stopVService(this)
            binding.btnContinue.text = "Connect"
        }

    }

    fun startV2Ray() {
        if (mainStorage?.decodeString(MmkvManager.KEY_SELECTED_SERVER).isNullOrEmpty()) {
            return
        }
        binding.btnContinue.isEnabled = false
        V2RayServiceManager.startV2Ray(this)
        binding.btnContinue.isEnabled = true
    }

}