package com.example.swarmhunter.platform

import android.os.Bundle
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.pico.spatial.ui.platform.stub.SpatialLaunchActivity
import java.lang.ref.WeakReference

class LaunchActivity : SpatialLaunchActivity() {
    override fun onCreate(savedInstanceState:Bundle?){super.onCreate(savedInstanceState);current=WeakReference(this)}
    fun requestTrackingPermissions(){
        val missing=PERMISSIONS.filter{ContextCompat.checkSelfPermission(this,it)!=PackageManager.PERMISSION_GRANTED}
        if(missing.isNotEmpty())ActivityCompat.requestPermissions(this,missing.toTypedArray(),42)
    }
    companion object {
        val PERMISSIONS=listOf("com.picovr.permission.HAND_TRACKING","com.picovr.permission.SPATIAL_DATA")
        var current=WeakReference<LaunchActivity>(null)
    }
}
