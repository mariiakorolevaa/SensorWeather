package com.example.weather

import SensorData
import android.content.Intent
import android.os.Bundle
import android.graphics.Color
import android.text.Spannable
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch


class MainActivity : AppCompatActivity() {
    private lateinit var influxDBService: InfluxDBService
    private lateinit var progressBar: ProgressBar  // Reference for the progress bar

    private lateinit var livingRoomData: List<SensorData>
    private lateinit var diningRoomData: List<SensorData>
    private lateinit var externalRoomData: List<SensorData>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        progressBar = findViewById(R.id.progressBar)
        influxDBService = InfluxDBService()

        val livingRoomMetrics = findViewById<TextView>(R.id.livingRoomMetrics)
        val diningRoomMetrics = findViewById<TextView>(R.id.diningRoomMetrics)
        val externalRoomMetrics = findViewById<TextView>(R.id.externalRoomMetrics)

        lifecycleScope.launch(Dispatchers.IO) {
            fetchDataAndNavigate(livingRoomMetrics, diningRoomMetrics, externalRoomMetrics)
        }


        // Set up button listeners
        val btnBedroomTemperature = findViewById<Button>(R.id.btnBedroomTemperature)
        val btnDiningRoomTemperature = findViewById<Button>(R.id.btnDiningRoomTemperature)
        val btnExternalTemperature = findViewById<Button>(R.id.btnExternalTemperature)

        btnBedroomTemperature.setOnClickListener {
            navigateToActivity("Living_Room_PHT", livingRoomData)
        }

        btnDiningRoomTemperature.setOnClickListener {
            navigateToActivity("Dining_Room_PHT", diningRoomData)
        }

        btnExternalTemperature.setOnClickListener {
            navigateToActivity("External_PHT", externalRoomData)
        }
    }

    // Function to fetch data for all rooms as soon as the app opens
    private fun formatMetrics(data: List<SensorData>): CharSequence {
        val temperature = data.find { it.measurementName == "Temperature" }?.value ?: "-"
        val humidity = data.find { it.measurementName == "Humidity" }?.value ?: "-"
        val pressure = data.find { it.measurementName == "Pressure" }?.value ?: "-"

        val formattedString = getString(
            R.string.formatted_metrics,
            temperature,
            humidity,
            pressure
        )

        val spannable = SpannableString(formattedString)

        val tempStart = formattedString.indexOf("Temperature:")
        val tempEnd = tempStart + "Temperature:".length
        spannable.setSpan(ForegroundColorSpan(Color.parseColor("#007DBF")), tempStart, tempEnd, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)

        val humidityStart = formattedString.indexOf("Humidity:")
        val humidityEnd = humidityStart + "Humidity:".length
        spannable.setSpan(ForegroundColorSpan(Color.parseColor("#007DBF")), humidityStart, humidityEnd, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)

        val pressureStart = formattedString.indexOf("Pressure:")
        val pressureEnd = pressureStart + "Pressure:".length
        spannable.setSpan(ForegroundColorSpan(Color.parseColor("#007DBF")), pressureStart, pressureEnd, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)

        return spannable
    }

    // Function to navigate to the activity and pass the data
    private fun navigateToActivity(room: String, roomData: List<SensorData>) {
        val intent = Intent(this, SensorRoomActivity::class.java)
        if (room == "Living_Room_PHT") {
            intent.putExtra("roomTitle", "Living_Room_PHT")
            intent.putParcelableArrayListExtra("sensorData", ArrayList(roomData))
            startActivity(intent)
        } else if (room == "Dining_Room_PHT") {
            intent.putExtra("roomTitle", "Dining_Room_PHT")
            intent.putParcelableArrayListExtra("sensorData", ArrayList(roomData))
            startActivity(intent)
        } else {
            intent.putExtra("roomTitle", "External_PHT")
            intent.putParcelableArrayListExtra("sensorData", ArrayList(roomData))
            startActivity(intent)
        }
    }

    private suspend fun fetchDataAndNavigate(
        livingRoomMetrics: TextView,
        diningRoomMetrics: TextView,
        externalRoomMetrics: TextView
    ) {
        lifecycleScope.launch(Dispatchers.Main) {
            progressBar.visibility = ProgressBar.VISIBLE
        }

        // Fetch data for all rooms
        val livingRoom = fetchRoomData("Living_Room_PHT")
        val diningRoom = fetchRoomData("Dining_Room_PHT")
        val externalRoom = fetchRoomData("External_PHT")

        // Store data
        livingRoomData = livingRoom
        diningRoomData = diningRoom
        externalRoomData = externalRoom

        // Обновление текстовых полей с показателями
        val livingRoomText = formatMetrics(livingRoom)
        val diningRoomText = formatMetrics(diningRoom)
        val externalRoomText = formatMetrics(externalRoom)

        lifecycleScope.launch(Dispatchers.Main) {
            livingRoomMetrics.text = "Living Room:\n$livingRoomText"
            diningRoomMetrics.text = "Dining Room:\n$diningRoomText"
            externalRoomMetrics.text = "External:\n$externalRoomText"
            progressBar.visibility = ProgressBar.GONE
        }
    }

    // Function to fetch room data asynchronously
    private suspend fun fetchRoomData(room: String): List<SensorData> {
        val temperatureData = influxDBService.queryDataWithFilters(listOf(room), "Temperature")
        val humidityData = influxDBService.queryDataWithFilters(listOf(room), "Humidity")
        val pressureData = influxDBService.queryDataWithFilters(listOf(room), "Pressure")

        val combinedData = mutableListOf<SensorData>()
        combinedData.addAll(temperatureData)
        combinedData.addAll(humidityData)
        combinedData.addAll(pressureData)

        return combinedData
    }

    override fun onDestroy() {
        super.onDestroy()
        influxDBService.close()
    }
}
