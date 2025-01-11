package com.example.weather

import SensorData
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.*
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.Response
import org.json.JSONObject
import java.io.IOException
import java.time.ZonedDateTime
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.resume

class InfluxDBService() : CoroutineScope {

    override val coroutineContext: CoroutineContext = Dispatchers.Main

    val url = "https://192.168.0.8:8086/api/v2/query"
    private val token = "YjZH9DXxoGa5GQMn0RCNE4y6eSo_KRXTwgmBe9L2NDHFSUJJx0Lbtk3hFjXjk7Q8-PmuZWqIfy2QD9bhR4TyWg=="
    private val org = "SmartHouse"
    private val bucket = "SmartHouse"
    private val client: OkHttpClient = getUnsafeOkHttpClient()

    private fun getUnsafeOkHttpClient(): OkHttpClient {
        return try {
            val trustAllCerts = arrayOf<TrustManager>(
                object : X509TrustManager {
                    override fun checkClientTrusted(chain: Array<java.security.cert.X509Certificate?>?, authType: String?) = Unit
                    override fun checkServerTrusted(chain: Array<java.security.cert.X509Certificate?>?, authType: String?) = Unit
                    override fun getAcceptedIssuers(): Array<java.security.cert.X509Certificate> = arrayOf()
                }
            )

            val sslContext = SSLContext.getInstance("SSL")
            sslContext.init(null, trustAllCerts, java.security.SecureRandom())

            OkHttpClient.Builder()
                .sslSocketFactory(sslContext.socketFactory, trustAllCerts[0] as X509TrustManager)
                .hostnameVerifier { _, _ -> true }
                .build()
        } catch (e: Exception) {
            throw RuntimeException(e)
        }
    }

    suspend fun queryDataWithFilters(sensorNames: List<String>, measurementName: String): List<SensorData> {
        return withContext(Dispatchers.IO) {
            val query = """
                from(bucket: "$bucket")
                    |> range(start: -10m)
                    |> filter(fn: (r) => r["_measurement"] == "Sensors")
                    |> filter(fn: (r) => r["MeasurementName"] == "PHT")
                    |> filter(fn: (r) => ${sensorNames.joinToString(" or ") { """r["SensorName"] == "$it"""" } })
                    |> filter(fn: (r) => r["_field"] == "$measurementName")
                    |> aggregateWindow(every: 1m, fn: mean, createEmpty: false)
                    |> yield(name: "mean")
            """
            sendQuery(query)
        }
    }

    suspend fun queryDataWithinTimeRange(
        sensorNames: List<String>, measurementName: String, startTime: String, endTime: String
    ): List<SensorData> {
        return withContext(Dispatchers.IO) {
            var formattedStartTime = startTime.replace(" ", "T") + ":00Z"
            var formattedEndTime = endTime.replace(" ", "T") + ":00Z"

            // Create the Flux query with the valid time format
            val query = """
                from(bucket: "$bucket")
                    |> range(start: $formattedStartTime, stop: $formattedEndTime)
                    |> filter(fn: (r) => r["_measurement"] == "Sensors")
                    |> filter(fn: (r) => r["MeasurementName"] == "PHT")
                    |> filter(fn: (r) => ${sensorNames.joinToString(" or ") { """r["SensorName"] == "$it"""" } })
                    |> filter(fn: (r) => r["_field"] == "$measurementName")
                    |> aggregateWindow(every: 1m, fn: mean, createEmpty: false)
                    |> yield(name: "mean")
            """
            sendQuery(query)
        }
    }

    private suspend fun sendQuery(query: String): List<SensorData> = suspendCancellableCoroutine { continuation ->
        val json = JSONObject().apply {
            put("query", query)
            put("org", org)
            put("type", "flux")
        }

        val request = Request.Builder()
            .url("$url?org=$org&bucket=$bucket")
            .header("Content-Type", "application/json")
            .header("Authorization", "Bearer $token")
            .post(RequestBody.create("application/json".toMediaTypeOrNull(), json.toString()))
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e("InfluxDBService", "Error while querying data: ${e.message}", e)
                continuation.resumeWith(Result.failure(e))
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    if (!response.isSuccessful) {
                        continuation.resumeWith(Result.failure(IOException("Unexpected code $response")))
                        return
                    }
                    val responseBody = response.body?.string()
                    val sensorData = parseResponse(responseBody)
                    continuation.resume(sensorData)
                }
            }
        })
    }

    private fun parseResponse(responseBody: String?): List<SensorData> {
        if (responseBody.isNullOrEmpty()) {
            return emptyList()
        }

        val rows = responseBody.split("\n")
        val sensorDataList = mutableListOf<SensorData>()

        for (row in rows.drop(1)) {
            val columns = row.split(",")

            if (columns.size >= 8) {
                val time = columns[5]  // _time
                val value = columns[6].toDoubleOrNull() ?: 0.0  // _value
                val measurementName = columns[9]  // MeasurementName
                val sensorName = columns[8]  // SensorName
                val field = columns[10]  // _field

                val sensorData = SensorData(time, value, measurementName, sensorName, field)
                sensorDataList.add(sensorData)
            }
        }

        return sensorDataList
    }

    fun close() {
        client.dispatcher.executorService.shutdown()
    }
}
