package com.achlvv.application1

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

// This bundles all the data together with the exact time it was recorded!
data class SensorReading(
    val timestamp: Long, // Time in milliseconds
    val aqi: Float,
    val no2: Float,
    val o3: Float,
    val so2: Float,
    val pm25: Float,
    val pm10: Float,
    val co2: Float
)
class SharedViewModel : ViewModel() {

    val status = MutableLiveData<Int>()
    val overallAqi = MutableLiveData<Int>()
    val pm25 = MutableLiveData<Float>()
    val pm10 = MutableLiveData<Float>()
    val co2 = MutableLiveData<Int>()
    val so2 = MutableLiveData<Float>()
    val no2 = MutableLiveData<Float>()
    val o3 = MutableLiveData<Float>()

    val isConnected = MutableLiveData<Boolean>()

    val readingHistory = mutableListOf<SensorReading>()
}