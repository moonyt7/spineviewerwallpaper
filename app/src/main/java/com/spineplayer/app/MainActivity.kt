package com.spineplayer.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.spineplayer.app.ui.ModelListFragment
import com.spineplayer.app.ui.PlayerFragment
import com.spineplayer.app.ui.SettingsFragment

/**
 * 主 Activity
 * 底部导航切换：模型列表 / 播放器 / 设置
 */
class MainActivity : AppCompatActivity() {

    private val modelListFragment = ModelListFragment()
    private val playerFragment = PlayerFragment()
    private val settingsFragment = SettingsFragment()

    private val importZipLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            modelListFragment.handleZipImport(it)
        }
    }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.values.all { it }
        if (allGranted) {
            openZipPicker()
        } else {
            Toast.makeText(this, "需要存储权限才能导入模型", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        if (savedInstanceState == null) {
            switchFragment(modelListFragment)
        }

        setupBottomNavigation()
    }

    private fun setupBottomNavigation() {
        val bottomNav = findViewById<BottomNavigationView>(R.id.bottom_nav)
        bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_models -> {
                    switchFragment(modelListFragment)
                    true
                }
                R.id.nav_player -> {
                    switchFragment(playerFragment)
                    true
                }
                R.id.nav_settings -> {
                    switchFragment(settingsFragment)
                    true
                }
                else -> false
            }
        }
    }

    fun switchFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment)
            .commit()
    }

    /**
     * 切换到播放器页（模型列表点击模型时调用）
     */
    fun switchToPlayer() {
        switchFragment(playerFragment)
        val bottomNav = findViewById<BottomNavigationView>(R.id.bottom_nav)
        bottomNav.selectedItemId = R.id.nav_player
    }

    /**
     * 启动 ZIP 文件选择器
     */
    fun requestImportZip() {
        if (hasStoragePermission()) {
            openZipPicker()
        } else {
            requestStoragePermission()
        }
    }

    private fun openZipPicker() {
        importZipLauncher.launch(arrayOf("application/zip", "application/x-zip-compressed", "*/*"))
    }

    private fun hasStoragePermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                this, Manifest.permission.READ_MEDIA_IMAGES
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(
                this, Manifest.permission.READ_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun requestStoragePermission() {
        val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO)
        } else {
            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
        permissionLauncher.launch(permissions)
    }

    /**
     * 打开系统动态壁纸设置
     */
    fun openWallpaperSettings() {
        val intent = Intent(Intent.ACTION_SET_WALLPAPER)
        startActivity(Intent.createChooser(intent, "选择动态壁纸"))
    }

    fun getPlayerFragment(): PlayerFragment = playerFragment
    fun getModelListFragment(): ModelListFragment = modelListFragment
    fun getSettingsFragment(): SettingsFragment = settingsFragment
}
