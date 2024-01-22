package com.example.updateapplicationtest

import android.Manifest
import android.content.DialogInterface
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.view.View
import android.widget.ProgressBar
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.ResponseBody
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Streaming
import retrofit2.http.Url
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL


class MainActivity : AppCompatActivity() {
    val downloadLink =
        "https://d.apkpure.net/b/APK/com.internet.speed.meter.lite?versionCode=53&nc=&sv=28"
    private val APK_URL = "com.internet.speed.meter.lite?versionCode=53&nc=&sv=28"


    private val permission = arrayOf(
        Manifest.permission.READ_EXTERNAL_STORAGE,
        Manifest.permission.WRITE_EXTERNAL_STORAGE
    )

    val storageCode = 101

    val APK_NAME = "Apkpure.apk"

    private lateinit   var builder : AlertDialog.Builder
    private lateinit var alertDialog : AlertDialog
    private lateinit var progress : ProgressBar

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
       // checkPermission()
        progress = findViewById<ProgressBar>(R.id.progress)
        if (checkPermission()) {
            showUpdateDialog()
        }

    }


    private fun checkPermission() : Boolean{
        if (ContextCompat.checkSelfPermission(
                this@MainActivity,
                permission[0]
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(this, arrayOf(permission[0]), storageCode)
            return false
        } else if (ContextCompat.checkSelfPermission(
                this@MainActivity,
                permission[0]
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(this, arrayOf(permission[1]), storageCode)
            return false
        } else {
            return true
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        if (requestCode == storageCode){
            // Check if the permission is granted
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Permission granted, perform your actions
                // e.g., start the camera
                showUpdateDialog()
            } else {
                // Permission denied, show a message or take appropriate action
                checkPermission()
            }
        }
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
    }

    private fun showUpdateDialog() {
        builder = AlertDialog.Builder(this)
        builder.setTitle("Update Available")
        builder.setMessage("A new version is available. Do you want to update now?")
        builder.setCancelable(false)
        builder.setPositiveButton("Update"
        ) { dialog, which -> // Download and install the update
            downloadFile()
        }
        builder.setNegativeButton("Cancel"
        ) { dialog, which -> // User canceled the update
            Toast.makeText(this@MainActivity, "Update canceled", Toast.LENGTH_SHORT).show()
        }
        alertDialog = builder.create()
        alertDialog.show()
    }

    private fun installApk(apkFile: File) {
        runOnUiThread {
            progress.visibility = View.GONE
        }
        val apkUri: Uri =
            FileProvider.getUriForFile(
                this@MainActivity,
                "${this@MainActivity.packageName}.fileprovider",
                apkFile
            )

        val installIntent = Intent(Intent.ACTION_INSTALL_PACKAGE)
        installIntent.data = apkUri
        installIntent.flags = Intent.FLAG_GRANT_READ_URI_PERMISSION



        this@MainActivity.startActivity(installIntent)
    }


    private fun downloadFile(){
        val apiService = RetrofitClient.retrofit.create(ApiService::class.java)
        progress.visibility = View.VISIBLE
        apiService.downloadFile(APK_URL).enqueue(object :Callback<ResponseBody>{
            override fun onResponse(call: Call<ResponseBody>, response: Response<ResponseBody>) {
                println(">>>>>>>>>response ${response.body()}")
                CoroutineScope(Dispatchers.IO).launch {
                    downloadApkFile(response.body())
                }
            }

            override fun onFailure(call: Call<ResponseBody>, t: Throwable) {
                println(">>>>>>>>>>>>error ${t.stackTrace}")
            }

        })
    }

    private suspend fun downloadApkFile(body: ResponseBody?) {
        withContext(Dispatchers.IO) {
            try {
                val apkFile = File(this@MainActivity.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), APK_NAME)

                var inputStream: InputStream? = null
                var outputStream: OutputStream? = null

                try {
                    val fileReader = ByteArray(4096)

                    var fileSize = body?.contentLength() ?: 0
                    var fileSizeDownloaded: Long = 0

                    inputStream = body?.byteStream()
                    outputStream = FileOutputStream(apkFile)

                    while (true) {
                        val read = inputStream?.read(fileReader) ?: -1

                        if (read == -1) {
                            break
                        }

                        outputStream.write(fileReader, 0, read)

                        fileSizeDownloaded += read
                    }

                    outputStream.flush()

                    installApk(apkFile)
                } catch (e: IOException) {
                    e.printStackTrace()
                    Toast.makeText(this@MainActivity, "Error downloading APK", Toast.LENGTH_SHORT).show()
                } finally {
                    inputStream?.close()
                    outputStream?.close()
                }
            } catch (e: IOException) {
                e.printStackTrace()
                Toast.makeText(this@MainActivity, "Error saving APK", Toast.LENGTH_SHORT).show()
            }
        }

    }
}



object RetrofitClient {
    private const val BASE_URL = "https://d.apkpure.net/b/APK/"
    private val client = OkHttpClient.Builder()
        .addInterceptor(HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY // Set the desired log level
        })
        .build()

    val retrofit: Retrofit = Retrofit.Builder()
        .baseUrl(BASE_URL)
        .client(client)
        .addConverterFactory(GsonConverterFactory.create())
        .build()
}

interface ApiService {
    @GET
    @Streaming
    fun downloadFile(@Url fileUrl: String): Call<ResponseBody>
}