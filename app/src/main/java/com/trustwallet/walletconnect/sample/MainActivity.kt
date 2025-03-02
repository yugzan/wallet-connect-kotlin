package com.trustwallet.walletconnect.sample

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.result.ActivityResultCallback
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.gson.GsonBuilder
import com.trustwallet.walletconnect.WCClient
import com.trustwallet.walletconnect.exceptions.InvalidSessionException
import com.trustwallet.walletconnect.models.WCAccount
import com.trustwallet.walletconnect.models.WCPeerMeta
import com.trustwallet.walletconnect.models.WCSignTransaction
import com.trustwallet.walletconnect.models.binance.WCBinanceCancelOrder
import com.trustwallet.walletconnect.models.binance.WCBinanceTradeOrder
import com.trustwallet.walletconnect.models.binance.WCBinanceTransferOrder
import com.trustwallet.walletconnect.models.binance.WCBinanceTxConfirmParam
import com.trustwallet.walletconnect.models.ethereum.WCEthereumSignMessage
import com.trustwallet.walletconnect.models.ethereum.WCEthereumTransaction
import com.trustwallet.walletconnect.models.session.WCSession
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.activity_main.view.*
import okhttp3.OkHttpClient
import com.journeyapps.barcodescanner.ScanContract

import com.journeyapps.barcodescanner.ScanOptions

import androidx.activity.result.ActivityResultLauncher
import com.google.zxing.client.android.Intents
import com.journeyapps.barcodescanner.ScanIntentResult
import org.web3j.protocol.Web3j


//import wallet.core.jni.CoinType
//import wallet.core.jni.PrivateKey

class MainActivity : AppCompatActivity() {

    private val wcClient by lazy {
        WCClient(GsonBuilder(), OkHttpClient())
    }

//    val privateKey = PrivateKey("ba005cd605d8a02e3d5dfd04234cef3a3ee4f76bfbad2722d1fb5af8e12e6764".decodeHex())
//    val address = CoinType.ETHEREUM.deriveAddress(privateKey)
    val privateKey="";
    val address="0x87c309b999b9c15db773fcf371539537b130791e";
    private val peerMeta = WCPeerMeta(name = "Example", url = "https://example.com")

    private lateinit var wcSession: WCSession

    private var remotePeerMeta: WCPeerMeta? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        addressInput.editText?.setText(address)
        wcClient.onDisconnect = { _, _ -> onDisconnect() }
        wcClient.onFailure = { t -> onFailure(t) }
        wcClient.onSessionRequest = { _, peer -> onSessionRequest(peer) }
        wcClient.onGetAccounts = { id -> onGetAccounts(id) }

        wcClient.onEthSign = { id, message -> onEthSign(id, message) }
        wcClient.onEthSignTransaction = { id, transaction -> onEthTransaction(id, transaction) }
        wcClient.onEthSendTransaction = { id, transaction -> onEthTransaction(id, transaction, send = true) }

        wcClient.onBnbTrade = { id, order -> onBnbTrade(id, order) }
        wcClient.onBnbCancel = { id, order -> onBnbCancel(id, order) }
        wcClient.onBnbTransfer = { id, order -> onBnbTransfer(id, order) }
        wcClient.onBnbTxConfirm = { _, param -> onBnbTxConfirm(param) }
        wcClient.onSignTransaction = { id, transaction -> onSignTransaction(id, transaction) }

        setupConnectButton()
    }

    private fun setupConnectButton() {
        runOnUiThread {
            connectButton.text = "Connect"
            connectButton.setOnClickListener {
                connect(uriInput.editText?.text?.toString() ?: return@setOnClickListener)
            }
            scanButton.setOnClickListener {
                barcodeLauncher.launch(ScanOptions())
            }
        }
    }
    private val barcodeLauncher = registerForActivityResult(
        ScanContract()
    ) { result: ScanIntentResult ->
        if (result.contents == null) {
            val originalIntent: Intent? = result.originalIntent
            if (originalIntent == null) {
                Log.d("MainActivity", "Cancelled scan")
                Toast.makeText(this@MainActivity, "Cancelled", Toast.LENGTH_LONG).show()
            } else if (originalIntent.hasExtra(Intents.Scan.MISSING_CAMERA_PERMISSION)) {
                Log.d(
                    "MainActivity",
                    "Cancelled scan due to missing camera permission"
                )
                Toast.makeText(
                    this@MainActivity,
                    "Cancelled due to missing camera permission",
                    Toast.LENGTH_LONG
                ).show()
            }
        } else {
            Log.d("MainActivity", "Scanned")
            val url = result.contents

            uriInput.editText?.setText(url)
//            processWCURL(url)
            Toast.makeText(
                this@MainActivity,
                "Scanned: " + result.contents,
                Toast.LENGTH_LONG
            ).show()
        }
    }

    fun connect(uri: String) {
        disconnect()
        wcSession = WCSession.from(uri) ?: throw InvalidSessionException()
        wcClient.connect(wcSession, peerMeta)
    }

    fun disconnect() {
        if (wcClient.session != null) {
            wcClient.killSession()
        } else {
            wcClient.disconnect()
        }
    }

    fun approveSession() {
        val address = addressInput.editText?.text?.toString() ?: address
        val chainId = chainInput.editText?.text?.toString()?.toIntOrNull() ?: 1
        wcClient.approveSession(listOf(address), chainId)
        connectButton.text = "Kill Session"
        connectButton.setOnClickListener {
            disconnect()
        }
    }

    fun rejectSession() {
        wcClient.rejectSession()
        wcClient.disconnect()
    }

    fun rejectRequest(id: Long) {
        wcClient.rejectRequest(id, "User canceled")
    }

    private fun onDisconnect() {
        setupConnectButton()
    }

    private fun onFailure(throwable: Throwable) {
        throwable.printStackTrace()
    }

    private fun onSessionRequest(peer: WCPeerMeta) {
        runOnUiThread {
            remotePeerMeta = peer
            wcClient.remotePeerId ?: run {
                println("remotePeerId can't be null")
                return@runOnUiThread
            }
            val meta = remotePeerMeta ?: return@runOnUiThread
            AlertDialog.Builder(this)
                .setTitle(meta.name)
                .setMessage("${meta.description}\n${meta.url}")
                .setPositiveButton("Approve") { _, _ ->
                    approveSession()
                }
                .setNegativeButton("Reject") { _, _ ->
                    rejectSession()
                }
                .show()
        }
    }

    private fun onEthSign(id: Long, message: WCEthereumSignMessage) {
        runOnUiThread {
            AlertDialog.Builder(this)
                .setTitle(message.type.name)
                .setMessage(message.data)
                .setPositiveButton("Sign") { _, _ ->
                    Log.e("Sign:", message.raw.toString());
                    Log.e("Sign:", message.data);
                    Log.e("Sign:", message.data.decodeHex().toString());
                    val message = "\\x19Ethereum Signed Message:\n${message.data.length}"

                    Log.e("Sign:", message);
                    val web3j = Web3j.build();
//                    web3j.si
                    //"\u0019Ethereum Signed Message:\n"
//                    val data: ByteArray = ArrayUtils.addAll(message.toByteArray(), messageData)
//                    wcClient.approveRequest(id, privateKey.sign(message.data.decodeHex(), CoinType.ETHEREUM.curve()))
                }
                .setNegativeButton("Cancel") { _, _ ->
                    rejectRequest(id)
                }
                .show()
        }
    }

    private fun onEthTransaction(id: Long, payload: WCEthereumTransaction, send: Boolean = false) { }

    private fun onBnbTrade(id: Long, order: WCBinanceTradeOrder) { }

    private fun onBnbCancel(id: Long, order: WCBinanceCancelOrder) { }

    private fun onBnbTransfer(id: Long, order: WCBinanceTransferOrder) { }

    private fun onBnbTxConfirm(param: WCBinanceTxConfirmParam) { }

    private fun onGetAccounts(id: Long) {
        val account = WCAccount(
            chainInput.editText?.text?.toString()?.toIntOrNull() ?: 1,
            addressInput.editText?.text?.toString() ?: address,
        )
        wcClient.approveRequest(id, account)
    }

    private fun onSignTransaction(id: Long, payload: WCSignTransaction) { }

    fun String.decodeHex(): ByteArray {
        check(length % 2 == 0) { "Must have an even length" }

        return removePrefix("0x")
            .chunked(2)
            .map { it.toInt(16).toByte() }
            .toByteArray()
    }

    companion object {
//        init {
//            System.loadLibrary("TrustWalletCore")
//        }
    }
}
