package com.example.weather

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.Legend
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.ValueFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.ParseException
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.*

class SensorRoomActivity : ComponentActivity() {
    private lateinit var roomName: TextView
    private lateinit var temperatureText: TextView
    private lateinit var humidityText: TextView
    private lateinit var pressureText: TextView
    private lateinit var lineChart: LineChart
    private lateinit var startDateTime: EditText
    private lateinit var endDateTime: EditText
    private lateinit var applyFilterButton: Button
    private lateinit var influxDBService: InfluxDBService
    private lateinit var sensorData: List<SensorData>
    private lateinit var graphTypeRadioGroup: RadioGroup
    private var selectedGraphType: String = "Temperature"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_sensor_room)

        roomName = findViewById(R.id.room_name_text)
        temperatureText = findViewById(R.id.temperature_value_text)
        humidityText = findViewById(R.id.humidity_value_text)
        pressureText = findViewById(R.id.pressure_value_text)
        lineChart = findViewById(R.id.sensor_data_chart)
        startDateTime = findViewById(R.id.start_date_time)
        endDateTime = findViewById(R.id.end_date_time)
        applyFilterButton = findViewById(R.id.apply_filter_button)
        graphTypeRadioGroup = findViewById(R.id.graph_type_radio_group)
        influxDBService = InfluxDBService() // Init influxDBService

        val roomTitle = intent.getStringExtra("roomTitle") ?: "Room"
        sensorData = intent.getParcelableArrayListExtra("sensorData") ?: listOf()

        roomName.text = roomTitle

        // Set default date/time values
        val currentTime = Calendar.getInstance()
        val endTime = currentTime.time
        currentTime.add(Calendar.HOUR_OF_DAY, -2)
        val startTime = currentTime.time

        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
        startDateTime.setText(dateFormat.format(startTime))
        endDateTime.setText(dateFormat.format(endTime))

        // Set date/time pickers
        startDateTime.setOnClickListener { showDateTimePicker(startDateTime) }
        endDateTime.setOnClickListener { showDateTimePicker(endDateTime) }

        // Apply filter button click listener
        applyFilterButton.setOnClickListener {
            applyFilters()
        }

        // Set up radio buttons for selecting graph type
        graphTypeRadioGroup.setOnCheckedChangeListener { _, checkedId ->
            when (checkedId) {
                R.id.radio_temperature -> selectedGraphType = "Temperature"
                R.id.radio_humidity -> selectedGraphType = "Humidity"
                R.id.radio_pressure -> selectedGraphType = "Pressure"
            }
            plotGraph(sensorData)
        }

        displaySensorData(sensorData)
        plotGraph(sensorData)
    }

    private fun showDateTimePicker(editText: EditText) {
        val calendar = Calendar.getInstance()
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())

        DatePickerDialog(this, { _, year, month, dayOfMonth ->
            calendar.set(year, month, dayOfMonth)
            TimePickerDialog(this, { _, hourOfDay, minute ->
                calendar.set(Calendar.HOUR_OF_DAY, hourOfDay)
                calendar.set(Calendar.MINUTE, minute)
                editText.setText(dateFormat.format(calendar.time))
            }, calendar.get(Calendar.HOUR_OF_DAY), calendar.get(Calendar.MINUTE), true).show()
        }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH)).show()
    }

    private fun applyFilters() {
        val startDateTimeText = startDateTime.text.toString()
        val endDateTimeText = endDateTime.text.toString()

        if (startDateTimeText.isEmpty() || endDateTimeText.isEmpty()) {
            Toast.makeText(this, "Please fill in all date and time fields", Toast.LENGTH_SHORT).show()
            return
        }

        // Ensure the date parsing is consistent
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
        val startMillis: Long
        val endMillis: Long
        try {
            startMillis = dateFormat.parse(startDateTimeText)?.time ?: throw ParseException("Unparseable date", 0)
            endMillis = dateFormat.parse(endDateTimeText)?.time ?: throw ParseException("Unparseable date", 0)
        } catch (e: ParseException) {
            e.printStackTrace()
            Toast.makeText(this, "Invalid date format", Toast.LENGTH_SHORT).show()
            return
        }

        lifecycleScope.launch(Dispatchers.IO) {
            val roomNameText = roomName.text.toString()
            val formattedStartTime = startDateTimeText.replace(" ", "T")
            val formattedEndTime = endDateTimeText.replace(" ", "T")

            val temperatureData = influxDBService.queryDataWithinTimeRange(listOf(roomNameText), "Temperature", formattedStartTime, formattedEndTime)
            val humidityData = influxDBService.queryDataWithinTimeRange(listOf(roomNameText), "Humidity", formattedStartTime, formattedEndTime)
            val pressureData = influxDBService.queryDataWithinTimeRange(listOf(roomNameText), "Pressure", formattedStartTime, formattedEndTime)

            val filteredData = temperatureData + humidityData + pressureData

            launch(Dispatchers.Main) {
                displaySensorData(filteredData)
                plotGraph(filteredData)
            }
        }
    }

    private fun displaySensorData(sensorData: List<SensorData>) {
        val latestTemperature = sensorData.find { it.measurementName == "Temperature" }?.value?.let { String.format("%.2f", it) } ?: "N/A"
        val latestHumidity = sensorData.find { it.measurementName == "Humidity" }?.value?.let { String.format("%.2f", it) } ?: "N/A"
        val latestPressure = sensorData.find { it.measurementName == "Pressure" }?.value?.let { String.format("%.2f", it) } ?: "N/A"
        val latestPressureMmHg = sensorData.find { it.measurementName == "Pressure" }?.value?.let { String.format("%.2f", it * 0.0075006) } ?: "N/A"

        temperatureText.text = "Temperature: $latestTemperature °C"
        humidityText.text = "Humidity: $latestHumidity %"
        pressureText.text = "Pressure: $latestPressure hPa ($latestPressureMmHg mmHg)"
    }

    private fun plotGraph(sensorData: List<SensorData>) {
        val graphData = when (selectedGraphType) {
            "Temperature" -> sensorData.filter { it.measurementName == "Temperature" }
            "Humidity" -> sensorData.filter { it.measurementName == "Humidity" }
            "Pressure" -> sensorData.filter { it.measurementName == "Pressure" }
            else -> emptyList()
        }

        val formatter = DateTimeFormatter.ISO_OFFSET_DATE_TIME

        val timestampCount = mutableMapOf<Long, Int>()

        val entries = graphData.mapNotNull {
            try {
                val instant = Instant.from(formatter.parse(it.time))
                val timestamp = instant.toEpochMilli()
                val offset = (timestampCount[timestamp] ?: 0).toFloat() / 1000 // Add a small offset
                timestampCount[timestamp] = (timestampCount[timestamp] ?: 0) + 1

                // Convert pressure to mm Hg if measurement is "Pressure"
                val value = if (it.measurementName == "Pressure") {
                    it.value * 0.00750062f // hPa to mm Hg conversion
                } else {
                    it.value
                }

                Entry(timestamp.toFloat() + offset, String.format("%.2f", value).toFloat())
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }

        val dataSet = LineDataSet(entries, selectedGraphType)
        dataSet.color = resources.getColor(android.R.color.holo_blue_dark)
        dataSet.valueTextColor = resources.getColor(android.R.color.white)

        val lineData = LineData(dataSet)
        lineChart.data = lineData

        // Customize X axis with time format
        val xAxis = lineChart.xAxis
        xAxis.valueFormatter = object : ValueFormatter() {
            private val dateFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

            override fun getFormattedValue(value: Float): String {
                val date = Date(value.toLong())
                return dateFormat.format(date)
            }
        }
        xAxis.textColor = resources.getColor(android.R.color.white)
        xAxis.setLabelCount(5, true)
        xAxis.granularity = 1f
        xAxis.isGranularityEnabled = true
        xAxis.position = XAxis.XAxisPosition.BOTTOM

        // Dynamically calculate Y axis range
        val yAxis = lineChart.axisLeft
        yAxis.textColor = resources.getColor(android.R.color.white)

        val values = entries.map { it.y }
        val minY = values.minOrNull() ?: 0f
        val maxY = values.maxOrNull() ?: 1f

        // Add some margin for better visualization
        val margin = (maxY - minY) * 0.1f // 10% margin

        yAxis.axisMinimum = minY - margin
        yAxis.axisMaximum = maxY + margin
        yAxis.labelCount = 5
        yAxis.setDrawGridLines(true)
        lineChart.axisRight.isEnabled = false

        // Add units dynamically to the Y axis labels
        yAxis.valueFormatter = object : ValueFormatter() {
            override fun getFormattedValue(value: Float): String {
                return when (selectedGraphType) {
                    "Temperature" -> String.format("%.2f °C", value)
                    "Humidity" -> String.format("%.2f %%", value)
                    "Pressure" -> String.format("%.2f mm Hg", value) // Correct pressure unit display
                    else -> value.toString()
                }
            }
        }

        // Customize legend
        val legend = lineChart.legend
        legend.textColor = resources.getColor(android.R.color.white)
        legend.verticalAlignment = Legend.LegendVerticalAlignment.BOTTOM
        legend.horizontalAlignment = Legend.LegendHorizontalAlignment.CENTER
        legend.orientation = Legend.LegendOrientation.HORIZONTAL
        legend.setDrawInside(false)
        legend.yOffset = 10f

        lineChart.description.isEnabled = false
        lineChart.invalidate()
    }


}
