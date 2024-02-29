package com.example.mevo_maps
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle

import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import okhttp3.Request
import androidx.lifecycle.lifecycleScope

import com.mapbox.bindgen.Value
import com.mapbox.geojson.FeatureCollection
import com.mapbox.geojson.Point
import com.mapbox.maps.CameraOptions
import com.mapbox.maps.MapView
import com.mapbox.maps.MapboxMap
import com.mapbox.maps.Style
import com.mapbox.maps.extension.style.layers.addLayer
import com.mapbox.maps.extension.style.layers.generated.lineLayer
import com.mapbox.maps.extension.style.layers.generated.symbolLayer
import com.mapbox.maps.extension.style.layers.properties.generated.IconAnchor
import com.mapbox.maps.extension.style.sources.generated.GeoJsonSource
import org.json.JSONObject


class MainActivity : AppCompatActivity() {

    private lateinit var mapboxMap: MapboxMap


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val mapView = MapView(this)

        setContentView(mapView)
        mapboxMap = mapView.mapboxMap.apply {
            setCamera(
                CameraOptions.Builder()
                    .center(Point.fromLngLat(174.7762, -41.2865)) // Coordinates of Wellington
                    .zoom(12.0)
                    .build()
            )
            loadStyle(Style.STANDARD) { style ->


                lifecycleScope.launch {
                    val featureCollection = fetchData()
                    // Check if the feature collection is empty
                    if (featureCollection.features()?.isEmpty() == true) {
                        Log.d("EMPTY", "Feature collection empty at Load Style")
                    }
                    else if(featureCollection.features() == null){
                        Log.d("Null", "Feature collection empty at Load Style")
                    }
                    else {
                        Log.d("Successful Vehicle", "Calling addGeo")
                        addGeoJsonSource(style, featureCollection)
                    }

                    val featureCollectionPark = fetchDataPark()
                    // Check if the feature collection is empty
                    if (featureCollectionPark.features()?.isEmpty() == true) {
                        Log.d("EMPTY", "Feature collection empty at Load Style")
                    }
                    else if(featureCollectionPark.features() == null){
                        Log.d("Null", "Feature collection empty at Load Style")
                    }
                    else {
                        Log.d("Successful Park", "Calling addGeo")
                        //addGeoJsonSourcePoly(style, featureCollectionPark)
                    }

                }

            }
        }
    }




    private fun addGeoJsonSource(style: Style, featureCollection: FeatureCollection) {
        featureCollection.features()?.forEachIndexed { index, feature ->
            val sourceId = "vehicleID_$index"


            val singleFeatureCollection = FeatureCollection.fromFeatures(listOf(feature))
            val singleFeatureCollectionJson = Value.fromJson(singleFeatureCollection.toJson())

            if (singleFeatureCollectionJson.isError) {
                throw RuntimeException("Invalid GeoJson:" + singleFeatureCollectionJson.error)
            }

            val sourceParams = hashMapOf<String, Value>("type" to Value("geojson"), "data" to singleFeatureCollectionJson.value!!)
            val addSourceResult = style.addStyleSource(sourceId, Value(sourceParams))

            if (addSourceResult.isError) {
                throw RuntimeException("Failed to add GeoJson source: ${addSourceResult.error}")
            }

            val iconUrl = feature.getStringProperty("iconUrl")

            // Fetch the image asynchronously
            fetchImageAsync(iconUrl) { bitmap ->
                bitmap?.let {
                    val iconId = "icon_$index"

                    // Add the fetched image as an icon to the map's style
                    style.addImage(iconId, it)

                    // Use the dynamically added icon for the symbol layer
                    style.addLayer(symbolLayer("circle_$index", sourceId) {
                        iconImage(iconId)
                        iconAnchor(IconAnchor.BOTTOM)
                    })
                }
            }
        }
    }

    private fun addGeoJsonSourcePoly(style: Style, featureCollection: FeatureCollection) {
        featureCollection.features()?.forEachIndexed { index, feature ->
            val sourceId = "parkID_$index"


            val singleFeatureCollection = FeatureCollection.fromFeatures(listOf(feature))
            val singleFeatureCollectionJson = Value.fromJson(singleFeatureCollection.toJson())

            if (singleFeatureCollectionJson.isError) {
                throw RuntimeException("Invalid GeoJson:" + singleFeatureCollectionJson.error)
            }

            val sourceParams = hashMapOf<String, Value>("type" to Value("geojson"), "data" to singleFeatureCollectionJson.value!!)
            val addSourceResult = style.addStyleSource(sourceId, Value(sourceParams))


            if (addSourceResult.isError) {
                throw RuntimeException("Failed to add GeoJson source: ${addSourceResult.error}")
            }





            style.addLayer(symbolLayer("poly_$index", sourceId) {
                lineLayer("poly_$index", sourceId) {
                    lineWidth(3.0)
                }

            })
        }
    }

    private suspend fun fetchData(): FeatureCollection {
        val client = OkHttpClient()
        val request = Request.Builder()
            .url("https://api.mevo.co.nz/public/vehicles/Wellington")
            .build()

        val response = withContext(Dispatchers.IO) { client.newCall(request).execute() }

        if (!response.isSuccessful) {
            Log.e("MainActivity", "Failed to fetch data: ${response.code}")
            return FeatureCollection.fromFeatures(emptyList()) // Return an empty collection in case of failure
        }

        val responseBody = response.body?.string()

// Check if the responseBody is not null, otherwise log and return an empty FeatureCollection
        if (responseBody == null || responseBody.isEmpty()) {
            Log.d("FAIL", "No data received or empty response.----------------------------------")
            return FeatureCollection.fromFeatures(emptyList())
        }else {
            // If there is a response body, log it with the tag "Successful"
            Log.d("Successful", responseBody)


            val jsonObject = JSONObject(responseBody)
            val geoJsonString = jsonObject.getJSONObject("data").toString()

            // Parse the extracted GeoJSON string into a FeatureCollection
            val featCol = FeatureCollection.fromJson(geoJsonString)

            Log.d("Successful", featCol.toString())
            return featCol
        }

    }

    private suspend fun fetchDataPark(): FeatureCollection {
        val client = OkHttpClient()
        val request = Request.Builder()
            .url("https://api.mevo.co.nz/public/parking/Wellington")
            .build()

        val response = withContext(Dispatchers.IO) { client.newCall(request).execute() }

        if (!response.isSuccessful) {
            Log.e("MainActivity", "Failed to fetch data: ${response.code}")
            return FeatureCollection.fromFeatures(emptyList()) // Return an empty collection in case of failure
        }

        val responseBody = response.body?.string()

        // Check if the responseBody is not null, otherwise log and return an empty FeatureCollection
        if (responseBody == null || responseBody.isEmpty()) {
            Log.d("FAIL", "No data received or empty response")
            return FeatureCollection.fromFeatures(emptyList())
        }else {
            // If there is a response body, log it with the tag "Successful"
            Log.d("Successful", responseBody)


            val jsonObject = JSONObject(responseBody)
            val geoJsonString = jsonObject.getJSONObject("data").toString()

            // Now parse the extracted GeoJSON string into a FeatureCollection
            val featCol = FeatureCollection.fromJson(geoJsonString)

            Log.d("Successful", featCol.toString())
            return featCol
        }
    }


    private fun fetchImageAsync(url: String, callback: (Bitmap?) -> Unit) {
        // Launching a new coroutine in an IO-optimized dispatcher
        lifecycleScope.launch(Dispatchers.IO) {
            val client = OkHttpClient()
            val request = Request.Builder().url(url).build()

            try {
                // Execute the request
                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        // Convert the InputStream to a Bitmap
                        response.body?.byteStream()?.let { inputStream ->
                            val bitmap = BitmapFactory.decodeStream(inputStream)
                            withContext(Dispatchers.Main) {
                                // Return the Bitmap on the main thread
                                callback(bitmap)
                            }
                        }
                    } else {
                        // Handle the case of a failed request
                        withContext(Dispatchers.Main) {
                            callback(null)
                        }
                    }
                }
            } catch (e: Exception) {
                // Handle exceptions, such as network errors
                withContext(Dispatchers.Main) {
                    callback(null)
                }
            }
        }
    }
}