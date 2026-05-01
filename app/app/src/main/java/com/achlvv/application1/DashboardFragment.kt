package com.achlvv.application1

import TrendFragment
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.github.anastr.speedviewlib.SpeedView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import com.github.anastr.speedviewlib.components.Section
import androidx.lifecycle.ViewModelProvider
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale


class DashboardFragment : Fragment(R.layout.fragment_dashboard) {
    private val sharedViewModel by lazy { ViewModelProvider(requireActivity())[SharedViewModel::class.java] }
    private lateinit var tvDeviceStatus: TextView
    private lateinit var speedViewCity: SpeedView
    private lateinit var tvCityLabel: TextView

    private fun getPersonalizedAdvice(
        aqi: Int,
        co2: Int,
        pm25: Float,
        pm10: Float,
        no2: Float,
        o3: Float,
        so2: Float,
        age: Int,
        weight: Float,
        height: Float
    ): String {
        // Establish the user's demographic risk profile
        val isVulnerableAge = age < 12 || age > 65
        val heightMeters = height / 100f
        val bmi = if (heightMeters > 0) weight / (heightMeters * heightMeters) else 22f

        // Create a personalized prefix based on their data
        val userContext = when {
            isVulnerableAge -> "Given your age bracket, your respiratory system is highly sensitive. "
            bmi > 25f -> "To protect your cardiovascular health, "
            else -> "To protect your lung health, "
        }

        // Evaluate the Gases (Ranked by Immediate Danger Level)

        if (so2 > 524f) {
            return "⚠️ ALARM: Sulfur Dioxide (SO2) is abnormally high (${so2.toInt()} µg/m³). This is a severe respiratory irritant. ${userContext}please leave the room, ventilate immediately, and check for burning materials."
        }

        if (o3 > 140f) {
            return "⚠️ WARNING: Ozone levels are elevated (${o3.toInt()} µg/m³). This can cause chest pain and throat irritation. ${userContext}please turn off any nearby laser printers or ionizers and open a window."
        }

        if (no2 > 188f) {
            return "Nitrogen Dioxide is high (${no2.toInt()} µg/m³), which usually comes from unvented gas stoves or heaters. ${userContext}prolonged exposure triggers asthma-like symptoms. Please turn on an exhaust fan."
        }

        if (pm25 > 35f) {
            return "Fine particulate pollution (PM2.5) is dangerously high (${pm25.toInt()} µg/m³). ${userContext}please completely avoid exercising indoors right now and turn on a HEPA air purifier."
        }

        if (pm10 > 50f) {
            return "Coarse dust or pollen (PM10) is elevated. If you have allergies, ${userContext}we recommend closing outside windows and running an air filter."
        }

        if (aqi >= 7) {
            return "The overall Air Quality Index is very poor (AQI $aqi). The combined effect of the indoor air is unhealthy. ${userContext}please limit physical exertion, avoid burning anything, and run an air purifier if available."
        }

        if (co2 > 1200) {
            return "The CO2 level is very high ($co2 ppm), which actively causes drowsiness and lowers productivity. We highly recommend opening a window to cycle in fresh air."
        }

        if (aqi in 4..6) {
            return "Air quality is currently moderate. It is fine to go about your day, but if you experience unexpected coughing or fatigue, consider taking a break."
        }

        return "Great! The air is exceptionally clean and healthy to breathe right now."

    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val dashboardAqiChart = view.findViewById<LineChart>(R.id.lineChart_dashboard_aqi)
        val tvHealthAdvice = view.findViewById<TextView>(R.id.tv_health_advice)
        // Link variables to the XML
        tvDeviceStatus = view.findViewById(R.id.tv_device_status)
        speedViewCity = view.findViewById(R.id.speedView_city)
        tvCityLabel = view.findViewById(R.id.tv_city_label)
        val speedViewLocal = view.findViewById<SpeedView>(R.id.speedView_local)
        sharedViewModel.overallAqi.observe(viewLifecycleOwner) { aqiValue ->
            speedViewLocal.speedTo(aqiValue.toFloat())
            setDeviceStatus(true)
            updateDashboardChart(dashboardAqiChart)

            // Grab EVERY gas value from the bulletin board
            val currentCo2 = sharedViewModel.co2.value ?: 0
            val currentPm25 = sharedViewModel.pm25.value ?: 0f
            val currentPm10 = sharedViewModel.pm10.value ?: 0f
            val currentNo2 = sharedViewModel.no2.value ?: 0f
            val currentO3 = sharedViewModel.o3.value ?: 0f
            val currentSo2 = sharedViewModel.so2.value ?: 0f

            // Retrieve User Data
            val userAge = 21
            val userWeight = 82f
            val userHeight = 176f

            // Run the comprehensive engine!
            val personalizedMessage = getPersonalizedAdvice(
                aqiValue, currentCo2, currentPm25, currentPm10,
                currentNo2, currentO3, currentSo2,
                userAge, userWeight, userHeight
            )

            tvHealthAdvice.text = personalizedMessage
        }
        // Link the new warning banner from the XML
        val tvWarmupWarning = view.findViewById<TextView>(R.id.tv_warmup_warning)

        // Watch the Arduino's warmup status! (0 = Warming Up, 1 = Ready)
        sharedViewModel.status.observe(viewLifecycleOwner) { statusValue ->
            if (statusValue == 0) {
                // The heaters are still cold. Show the warning
                tvWarmupWarning.visibility = View.VISIBLE
            } else {
                // It has been 20 minutes! The data is accurate. Hide the warning
                tvWarmupWarning.visibility = View.GONE
            }
        }
        // Make the local Arduino gauge clickable
        speedViewLocal.setOnClickListener {
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, GasDetailsFragment())
                .addToBackStack(null) // Allows the physical back button to work
                .commit()
        }
        applyUKColorBands(speedViewCity)
        applyUKColorBands(speedViewLocal)
        val cardTrend = view.findViewById<View>(R.id.card_trend)

        // Listen for clicks
        cardTrend.setOnClickListener {
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, TrendFragment())
                .addToBackStack(null) // This lets the phone's physical back button work too!
                .commit()
        }

        // Set initial device status
        setDeviceStatus(false)

        // Fetch the Government AQI from the internet
        fetchGovernmentAQI()
        // Watch for dropped connections!
        sharedViewModel.isConnected.observe(viewLifecycleOwner) { connected ->
            if (connected) {
                setDeviceStatus(true)
            } else {
                setDeviceStatus(false) // Turns your status text back to "Offline" or "Disconnected"

                // Also hide the warmup banner if we aren't connected
                tvWarmupWarning.visibility = View.GONE
            }
        }

    }

    fun setDeviceStatus(isConnected: Boolean) {
        if (isConnected) {
            tvDeviceStatus.text = "🟢 Device Status: Connected"
            tvDeviceStatus.setTextColor(Color.parseColor("#388E3C"))
        } else {
            tvDeviceStatus.text = "🔴 Device Status: Disconnected"
            tvDeviceStatus.setTextColor(Color.parseColor("#D32F2F"))
        }
    }

    private fun fetchGovernmentAQI() {
        // Launch a background task (Dispatchers.IO is for network/database work)
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            try {
                val url = "https://api.waqi.info/feed/here/?token=6d372752256633fe71d4ad90ff89854ce82b4b42"

                val client = OkHttpClient()
                val request = Request.Builder().url(url).build()

                // Execute the request
                val response = client.newCall(request).execute()
                val responseData = response.body?.string()

                if (responseData != null) {
                    val jsonObject = JSONObject(responseData)
                    val status = jsonObject.getString("status")

                    if (status == "ok") {
                        val data = jsonObject.getJSONObject("data")
                        val aqi = data.getInt("aqi")
                        // Get the city name from the data to personalize the text!
                        val city = data.getJSONObject("city").getString("name")

                        // Jump back to the Main UI thread to update the screen
                        withContext(Dispatchers.Main) {
                            // speedTo() animates the needle moving to the number
                            val ukAqi = convertToUKScale(aqi)
                            speedViewCity.speedTo(ukAqi, 1000)
                            tvCityLabel.text = "The Air Quality Index in $city is:"
                        }
                    }
                }
            } catch (e: Exception) {
                // If the internet is down, we just print the error and do nothing
                e.printStackTrace()
            }
        }
    }
    // Applies the official UK DAQI color bands to any speedometer
    private fun applyUKColorBands(speedView: SpeedView) {
        speedView.clearSections() // Remove the default 3 colors

        val thickness = speedView.speedometerWidth

        speedView.addSections(
            Section(0f, 0.3f, Color.parseColor("#4CAF50"), thickness),
            Section(0.3f, 0.5f, Color.parseColor("#FFEB3B"), thickness),
            Section(0.5f, 0.6f, Color.parseColor("#FF9800"), thickness),
            Section(0.6f, 0.9f, Color.parseColor("#F44336"), thickness),
            Section(0.9f, 1.0f, Color.parseColor("#9C27B0"), thickness)
        )
    }


    private fun convertToUKScale(globalAqi: Int): Float {
        return when (globalAqi) {
            in 0..16 -> 1f
            in 17..33 -> 2f
            in 34..50 -> 3f   // UK "Low" ends around US 50
            in 51..66 -> 4f
            in 67..83 -> 5f
            in 84..100 -> 6f  // UK "Moderate" ends around US 100
            in 101..133 -> 7f
            in 134..166 -> 8f
            in 167..200 -> 9f // UK "High" ends around US 200
            else -> 10f       // UK "Very High"
        }
    }

    private fun updateDashboardChart(chart: LineChart) {
        val history = sharedViewModel.readingHistory
        val entries = mutableListOf<Entry>()
        val labels = mutableListOf<String>()
        val now = System.currentTimeMillis()

        // Hardcoded for "Past Hour" (10-minute buckets)
        val bucketSizeMillis = 10L * 60 * 1000
        val totalWindowMillis = 60L * 60 * 1000
        val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())

        val startTime = now - totalWindowMillis
        var xAxisPosition = 0f

        // Crunch the numbers!
        for (bucketStart in startTime until now step bucketSizeMillis) {
            val bucketEnd = bucketStart + bucketSizeMillis
            val readingsInBucket = history.filter { it.timestamp in bucketStart until bucketEnd }

            labels.add(sdf.format(Date(bucketStart)))

            if (readingsInBucket.isNotEmpty()) {
                // Calculate average AQI for this 10-minute bucket
                val average = readingsInBucket.map { it.aqi }.average().toFloat()
                entries.add(Entry(xAxisPosition, average))
            } else {
                entries.add(Entry(xAxisPosition, 0f))
            }
            xAxisPosition += 1f
        }

        // Style the Line (Let's use a nice Purple/Indigo for the master AQI!)
        val dataSet = LineDataSet(entries, "AQI")
        dataSet.color = Color.parseColor("#6200EE")
        dataSet.valueTextColor = Color.BLACK
        dataSet.valueTextSize = 9f
        dataSet.lineWidth = 2.5f
        dataSet.setCircleColor(Color.parseColor("#3700B3"))
        dataSet.circleRadius = 4f
        dataSet.setDrawCircleHole(true)
        dataSet.mode = LineDataSet.Mode.CUBIC_BEZIER // Smooth curves!
        dataSet.setDrawFilled(true)
        dataSet.fillColor = Color.parseColor("#D1C4E9")

        // Clean up the Chart UI
        chart.description.isEnabled = false
        chart.legend.isEnabled = false // Hide the legend since the title explains it
        chart.axisRight.isEnabled = false
        chart.axisLeft.setDrawGridLines(true)
        chart.xAxis.position = XAxis.XAxisPosition.BOTTOM
        chart.xAxis.setDrawGridLines(false)
        chart.xAxis.valueFormatter = IndexAxisValueFormatter(labels)
        chart.xAxis.granularity = 1f
        chart.xAxis.isGranularityEnabled = true

        // Push to canvas
        chart.data = LineData(dataSet)
        chart.invalidate()
    }
}