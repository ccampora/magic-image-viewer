package com.magicimageviewer.app.ui

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.view.Menu
import android.view.MenuItem
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.viewpager2.widget.ViewPager2
import com.magicimageviewer.app.R
import com.magicimageviewer.app.data.PrefsRepository
import com.magicimageviewer.app.databinding.ActivityMainBinding
import com.magicimageviewer.app.network.PcDiscovery
import com.magicimageviewer.app.network.UploadClient
import java.io.ByteArrayOutputStream
import kotlin.concurrent.thread

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var prefs: PrefsRepository
    private lateinit var discovery: PcDiscovery

    private val requestPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> if (granted) loadPhotos() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prefs = PrefsRepository(this)
        discovery = PcDiscovery(this)

        binding.photoPager.orientation = ViewPager2.ORIENTATION_VERTICAL

        ensurePermissionThenLoad()
        discovery.start { host, port ->
            runOnUiThread { prefs.pcHost = "$host:$port" }
        }
    }

    override fun onDestroy() {
        discovery.stop()
        super.onDestroy()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == R.id.action_settings) {
            startActivity(Intent(this, SettingsActivity::class.java))
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    private fun ensurePermissionThenLoad() {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_IMAGES
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
        requestPermission.launch(permission)
    }

    private fun loadPhotos() {
        val photos = queryPhotos()
        binding.photoPager.adapter = PhotoPagerAdapter(photos) { uri -> transfer(uri) }
    }

    private fun queryPhotos(): List<Uri> {
        val photos = mutableListOf<Uri>()
        val collection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(MediaStore.Images.Media._ID)
        val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC"

        contentResolver.query(collection, projection, null, null, sortOrder)?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)
                photos.add(Uri.withAppendedPath(collection, id.toString()))
            }
        }
        return photos
    }

    private fun transfer(uri: Uri) {
        val hostPort = prefs.pcHost
        if (hostPort.isNullOrBlank()) {
            showStatus(getString(R.string.no_pc_configured))
            return
        }

        thread {
            val bytes = ByteArrayOutputStream().use { out ->
                contentResolver.openInputStream(uri)?.use { it.copyTo(out) }
                out.toByteArray()
            }
            val mimeType = contentResolver.getType(uri) ?: "image/jpeg"
            val fileName = "photo_${System.currentTimeMillis()}.${mimeType.substringAfter("/")}"

            val result = UploadClient.upload(hostPort, fileName, bytes, mimeType)
            runOnUiThread {
                when (result) {
                    is UploadClient.Result.Success -> showStatus(getString(R.string.transfer_sent))
                    is UploadClient.Result.Failure -> showStatus(
                        getString(R.string.transfer_failed, result.message)
                    )
                }
            }
        }
    }

    private fun showStatus(message: String) {
        binding.statusText.text = message
        binding.statusText.visibility = android.view.View.VISIBLE
        binding.statusText.postDelayed({ binding.statusText.visibility = android.view.View.GONE }, 2000)
    }
}
