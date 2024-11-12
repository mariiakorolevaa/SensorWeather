package com.example.weather

import SensorData
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch


class MainActivity : AppCompatActivity() {
    private lateinit var influxDBService: InfluxDBService
    private lateinit var editText: EditText  // Reference for displaying debug data
    private lateinit var progressBar: ProgressBar  // Reference for the progress bar

    private lateinit var livingRoomData: List<SensorData>
    private lateinit var diningRoomData: List<SensorData>
    private lateinit var externalRoomData: List<SensorData>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        editText = findViewById(R.id.consoleTextLine)
        progressBar = findViewById(R.id.progressBar)
        influxDBService = InfluxDBService(editText)

        // Fetch data for all three rooms as soon as the app opens
        fetchDataAndNavigate()

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
    private fun fetchDataAndNavigate() {
        lifecycleScope.launch(Dispatchers.IO) {
            // Show the progress bar while fetching data
            launch(Dispatchers.Main) {
                progressBar.visibility = ProgressBar.VISIBLE
            }

            // Fetch data for all rooms
            val livingRoom = fetchRoomData("Living_Room_PHT")
            val diningRoom = fetchRoomData("Dining_Room_PHT")
            val externalRoom = fetchRoomData("External_PHT")

            // Store data for each room
            livingRoomData = livingRoom
            diningRoomData = diningRoom
            externalRoomData = externalRoom

            // Hide progress bar and enable button interactions after data is fetched
            launch(Dispatchers.Main) {
                progressBar.visibility = ProgressBar.GONE
            }
        }
    }

    // Function to navigate to the activity and pass the data
    private fun navigateToActivity(room: String, roomData: List<SensorData>) {
        if (room == "Living_Room_PHT") {
            val intent = Intent(this, BedroomActivity::class.java)
            intent.putParcelableArrayListExtra("sensorData", ArrayList(roomData))
            startActivity(intent)
        } else if (room == "Dining_Room_PHT") {
            val intent = Intent(this, DiningRoomActivity::class.java)
            intent.putParcelableArrayListExtra("sensorData", ArrayList(roomData))
            startActivity(intent)
        } else {
            val intent = Intent(this, ExternalActivity::class.java)
            intent.putParcelableArrayListExtra("sensorData", ArrayList(roomData))
            startActivity(intent)
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
