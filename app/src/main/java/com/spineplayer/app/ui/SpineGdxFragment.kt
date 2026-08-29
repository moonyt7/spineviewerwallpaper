package com.spineplayer.app.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.badlogic.gdx.ApplicationListener
import com.badlogic.gdx.backends.android.AndroidApplicationConfiguration
import com.badlogic.gdx.backends.android.AndroidFragmentApplication

/**
 * 命名的 LibGDX Fragment（必须是 public static class，否则 FragmentManager 无法重建）
 *
 * 使用方式：
 * ```
 * val fragment = SpineGdxFragment().apply {
 *     listener = myGameAdapter
 *     config = myConfig
 * }
 * childFragmentManager.beginTransaction().replace(containerId, fragment).commit()
 * ```
 */
class SpineGdxFragment : AndroidFragmentApplication() {

    var gameListener: ApplicationListener? = null
    var gameConfig: AndroidApplicationConfiguration? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val listener = gameListener
            ?: throw IllegalStateException("SpineGdxFragment: gameListener must be set before onCreateView")
        val config = gameConfig ?: AndroidApplicationConfiguration()
        return initializeForView(listener, config)
    }
}
