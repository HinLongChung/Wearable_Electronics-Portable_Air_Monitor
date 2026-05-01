import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.achlvv.application1.R
import com.achlvv.application1.SharedViewModel
import com.github.mikephil.charting.data.Entry
import android.graphics.Color
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter

class TrendFragment : Fragment(R.layout.fragment_trend) {
    private val sharedViewModel by lazy {
        androidx.lifecycle.ViewModelProvider(requireActivity())[SharedViewModel::class.java]
    }
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Link the UI elements
        val dropdownDataType = view.findViewById<AutoCompleteTextView>(R.id.dropdown_data_type)
        val dropdownTimeSpan = view.findViewById<AutoCompleteTextView>(R.id.dropdown_time_span)

        // Create the exact lists of options you asked for
        val dataTypes = arrayOf("AQI", "NO2", "O3", "SO2", "PM2.5", "PM10", "CO2")
        val timeSpans = arrayOf("Past Hour", "Past Day", "Past Week")

        // Create adapters (This translates Kotlin Arrays into visual UI rows)
        val dataAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, dataTypes)
        val timeAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, timeSpans)

        // Attach the adapters to the Dropdowns
        dropdownDataType.setAdapter(dataAdapter)
        dropdownTimeSpan.setAdapter(timeAdapter)

        // Set default selections (The 'false' stops it from triggering a fake click event on boot)
        dropdownDataType.setText(dataTypes[0], false) // Defaults to "AQI"
        dropdownTimeSpan.setText(timeSpans[0], false) // Defaults to "Past Hour"

        // Listeners: What happens when the user clicks an option?
        dropdownDataType.setOnItemClickListener { _, _, position, _ ->
            val selectedData = dataTypes[position]
            Toast.makeText(requireContext(), "Selected: $selectedData", Toast.LENGTH_SHORT).show()
        }

        dropdownTimeSpan.setOnItemClickListener { _, _, position, _ ->
            val selectedTime = timeSpans[position]
            Toast.makeText(requireContext(), "Selected: $selectedTime", Toast.LENGTH_SHORT).show()
        }
        // Find the physical chart canvas
        val lineChart = view.findViewById<LineChart>(R.id.lineChart_trends)

        dropdownDataType.setOnItemClickListener { _, _, _, _ ->
            val selectedData = dropdownDataType.text.toString()
            val selectedTime = dropdownTimeSpan.text.toString()
            updateChart(lineChart, selectedTime, selectedData)
        }

        dropdownTimeSpan.setOnItemClickListener { _, _, _, _ ->
            val selectedData = dropdownDataType.text.toString()
            val selectedTime = dropdownTimeSpan.text.toString()
            updateChart(lineChart, selectedTime, selectedData)
        }
        val initialData = dropdownDataType.text.toString() // Grabs "AQI"
        val initialTime = dropdownTimeSpan.text.toString() // Grabs "Past Hour"
        updateChart(lineChart, initialTime, initialData)
        sharedViewModel.overallAqi.observe(viewLifecycleOwner) {
            val currentData = dropdownDataType.text.toString()
            val currentTime = dropdownTimeSpan.text.toString()
            updateChart(lineChart, currentTime, currentData)
        }
    }

    private fun getAveragedChartData(timeSpan: String, dataType: String): Pair<List<Entry>, List<String>> {
        val history = sharedViewModel.readingHistory
        val entries = mutableListOf<Entry>()
        val labels = mutableListOf<String>()
        val now = System.currentTimeMillis()

        val bucketSizeMillis: Long
        val totalWindowMillis: Long
        val timeFormatPattern: String

        when (timeSpan) {
            "Past Hour" -> {
                bucketSizeMillis = 10L * 60 * 1000
                totalWindowMillis = 60L * 60 * 1000
                timeFormatPattern = "HH:mm" // e.g., "13:10"
            }
            "Past Day" -> {
                bucketSizeMillis = 4L * 60 * 60 * 1000
                totalWindowMillis = 24L * 60 * 60 * 1000
                timeFormatPattern = "HH:mm" // e.g., "16:00"
            }
            else -> { // "Past Week"
                bucketSizeMillis = 24L * 60 * 60 * 1000
                totalWindowMillis = 6L * 24 * 60 * 60 * 1000
                timeFormatPattern = "MMM dd" // e.g., "Oct 24"
            }
        }

        val sdf = SimpleDateFormat(timeFormatPattern, Locale.getDefault())
        val startTime = now - totalWindowMillis
        var xAxisPosition = 0f

        // Chop time into buckets
        for (bucketStart in startTime until now step bucketSizeMillis) {
            val bucketEnd = bucketStart + bucketSizeMillis
            val readingsInBucket = history.filter { it.timestamp in bucketStart until bucketEnd }

            val timeString = sdf.format(Date(bucketStart))
            labels.add(timeString)

            if (readingsInBucket.isNotEmpty()) {
                var sum = 0f
                for (reading in readingsInBucket) {
                    sum += when (dataType) {
                        "AQI" -> reading.aqi
                        "NO2" -> reading.no2
                        "SO2" -> reading.so2
                        "O3" -> reading.o3
                        "PM2.5" -> reading.pm25
                        "PM10" -> reading.pm10
                        "CO2" -> reading.co2
                        else -> 0f
                    }
                }
                val average = sum / readingsInBucket.size
                entries.add(Entry(xAxisPosition, average))
            } else {
                entries.add(Entry(xAxisPosition, 0f))
            }
            xAxisPosition += 1f
        }

        // Return BOTH the points and the labels together
        return Pair(entries, labels)
    }
    private fun updateChart(chart: LineChart, timeSpan: String, dataType: String) {
        // Grab both the points and the labels
        val chartData = getAveragedChartData(timeSpan, dataType)
        val entries = chartData.first
        val timeLabels = chartData.second // Our new dictionary!

        // Create the line and style it (Keep your existing styling here!)
        val dataSet = LineDataSet(entries, "$dataType ($timeSpan)")
        dataSet.color = Color.parseColor("#2196F3")
        dataSet.valueTextColor = Color.BLACK
        dataSet.valueTextSize = 10f
        dataSet.lineWidth = 3f
        dataSet.setCircleColor(Color.parseColor("#1976D2"))
        dataSet.circleRadius = 5f
        dataSet.setDrawCircleHole(true)
        dataSet.mode = LineDataSet.Mode.CUBIC_BEZIER
        dataSet.setDrawFilled(true)
        dataSet.fillColor = Color.parseColor("#BBDEFB")

        // Clean up the Chart UI and apply the labels
        chart.description.isEnabled = false
        chart.axisRight.isEnabled = false
        chart.axisLeft.setDrawGridLines(true)
        chart.xAxis.position = XAxis.XAxisPosition.BOTTOM
        chart.xAxis.setDrawGridLines(false)
        chart.xAxis.valueFormatter = IndexAxisValueFormatter(timeLabels) // Apply the dictionary!
        chart.xAxis.granularity = 1f // Force it to step by exactly 1 to match our labels
        chart.xAxis.isGranularityEnabled = true

        // 4. Push the painted line to the canvas
        val lineData = LineData(dataSet)
        chart.data = lineData
        chart.invalidate()
        chart.animateX(500)
    }
}