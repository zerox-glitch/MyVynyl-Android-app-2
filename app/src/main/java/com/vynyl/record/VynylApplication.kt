package com.vynyl.record

import android.app.Application
import com.vynyl.record.data.RecordStore
import com.vynyl.record.pro.Pro

class VynylApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        RecordStore.init(this)
        Pro.init(this)
    }
}
