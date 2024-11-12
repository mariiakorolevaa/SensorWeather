package com.example.weather

import SensorData
import android.os.Bundle
import android.widget.TextView
import androidx.activity.ComponentActivity
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet

class ExternalActivity : ComponentActivity() {

    private lateinit var roomName: TextView
    private lateinit var temperatureText: TextView
    private lateinit var humidityText: TextView
    private lateinit var pressureText: TextView
    private lateinit var lineChart: LineChart

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_external)

        roomName = findViewById(R.id.room_name_text)
        temperatureText = findViewById(R.id.temperature_value_text)
        humidityText = findViewById(R.id.humidity_value_text)
        pressureText = findViewById(R.id.pressure_value_text)
        lineChart = findViewById(R.id.sensor_data_chart)

        val sensorData: List<SensorData>? = intent.getParcelableArrayListExtra("sensorData")

        sensorData?.let {
            displaySensorData(it)
            plotGraph(it)
        }
    }

    private fun displaySensorData(sensorData: List<SensorData>) {
        val latestTemperature = sensorData.find { it.measurementName == "Temperature" }?.value?.toString() ?: "N/A"
        val latestHumidity = sensorData.find { it.measurementName == "Humidity" }?.value?.toString() ?: "N/A"
        val latestPressure = sensorData.find { it.measurementName == "Pressure" }?.value?.toString() ?: "N/A"

        temperatureText.text = "Temperature: $latestTemperature °C"
        humidityText.text = "Humidity: $latestHumidity %"
        pressureText.text = "Pressure: $latestPressure hPa"
    }

    private fun plotGraph(sensorData: List<SensorData>) {
        val temperatureData = sensorData.filter { it.measurementName == "Temperature" }

        val entries = temperatureData.mapIndexed { index, data ->
            Entry(index.toFloat(), data.value.toFloat())
        }

        val dataSet = LineDataSet(entries, "Temperature")
        dataSet.color = resources.getColor(android.R.color.holo_blue_dark)
        dataSet.valueTextColor = resources.getColor(android.R.color.black)

        val lineData = LineData(dataSet)

        lineChart.data = lineData
        lineChart.invalidate()
    }
}