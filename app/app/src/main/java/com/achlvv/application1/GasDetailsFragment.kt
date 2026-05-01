package com.achlvv.application1


import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.Button
import androidx.fragment.app.Fragment
import com.github.anastr.speedviewlib.SpeedView
import com.github.anastr.speedviewlib.components.Section
import androidx.lifecycle.ViewModelProvider
import android.widget.TextView

class GasDetailsFragment : Fragment(R.layout.fragment_gas_details) {
    private val sharedViewModel by lazy { ViewModelProvider(requireActivity())[SharedViewModel::class.java] }
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val btnBack = view.findViewById<Button>(R.id.btn_back_to_dash)

        // Link all 6 of gauges
        val gaugeNo2 = view.findViewById<SpeedView>(R.id.gauge_no2)
        val gaugeSo2 = view.findViewById<SpeedView>(R.id.gauge_so2)
        val gaugeO3 = view.findViewById<SpeedView>(R.id.gauge_o3)
        val gaugePm25 = view.findViewById<SpeedView>(R.id.gauge_pm25)
        val gaugePm10 = view.findViewById<SpeedView>(R.id.gauge_pm10)
        val gaugeCo2 = view.findViewById<SpeedView>(R.id.gauge_co2)

        // Apply the UK color bands to every single one!
        applyUKColorBands(gaugeNo2)
        applyUKColorBands(gaugeSo2)
        applyUKColorBands(gaugeO3)
        applyUKColorBands(gaugePm25)
        applyUKColorBands(gaugePm10)
        applyUKColorBands(gaugeCo2)

        sharedViewModel.no2.observe(viewLifecycleOwner) { value -> gaugeNo2.speedTo(value) }
        sharedViewModel.so2.observe(viewLifecycleOwner) { value -> gaugeSo2.speedTo(value) }
        sharedViewModel.o3.observe(viewLifecycleOwner) { value -> gaugeO3.speedTo(value) }
        sharedViewModel.pm25.observe(viewLifecycleOwner) { value -> gaugePm25.speedTo(value) }
        sharedViewModel.pm10.observe(viewLifecycleOwner) { value -> gaugePm10.speedTo(value) }
        sharedViewModel.co2.observe(viewLifecycleOwner) { value -> gaugeCo2.speedTo(value.toFloat()) }
        // Return to Dashboard when clicked
        btnBack.setOnClickListener {
            parentFragmentManager.popBackStack()
        }
        // Link the warning banner from the XML
        val tvWarmupWarning = view.findViewById<TextView>(R.id.tv_details_warmup_warning)

        // Watch the Arduino's warmup status from the Shared ViewModel
        sharedViewModel.status.observe(viewLifecycleOwner) { statusValue ->
            if (statusValue == 0) {
                tvWarmupWarning.visibility = View.VISIBLE
            } else {
                tvWarmupWarning.visibility = View.GONE
            }
        }
    }

    private fun applyUKColorBands(speedView: SpeedView) {
        speedView.clearSections()

        val thickness = speedView.speedometerWidth

        speedView.addSections(
            Section(0f, 0.3f, Color.parseColor("#4CAF50"), thickness),   // Green
            Section(0.3f, 0.5f, Color.parseColor("#FFEB3B"), thickness), // Yellow
            Section(0.5f, 0.6f, Color.parseColor("#FF9800"), thickness), // Orange
            Section(0.6f, 0.9f, Color.parseColor("#F44336"), thickness), // Red
            Section(0.9f, 1.0f, Color.parseColor("#9C27B0"), thickness)  // Purple
        )
    }
}