package com.example.skoltechmapmeasurements.device

import android.os.Build
import com.example.skoltechmapmeasurements.model.DeviceInfo

class DeviceInfoManager {
    
    fun getDeviceInfo(): DeviceInfo {
        return DeviceInfo(
            model = "${Build.MANUFACTURER} ${Build.MODEL}"
        )
    }
}