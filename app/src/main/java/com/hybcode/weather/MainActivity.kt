package com.hybcode.weather

import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.location.Location
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.provider.UserDictionary.Words.APP_ID
import android.widget.Toast
import androidx.preference.PreferenceManager
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.hybcode.weather.core.data.api.ApiConstants.GEO_COORDINATES_URL
import com.hybcode.weather.databinding.ActivityMainBinding
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.text.DateFormat
import java.util.*

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var sharedPreferences: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)
        binding.fab.setOnClickListener {
            getLocation()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int,
                                            permissions: Array<out String>,
                                            grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 1 && grantResults[0] == PackageManager.PERMISSION_GRANTED) getLocation()
        else if (requestCode == 1 && grantResults[0] != PackageManager.PERMISSION_GRANTED) Toast.makeText(
            this,
            getString(R.string.permission_required),
            Toast.LENGTH_LONG
        ).show()
    }

    private fun getLocation() {
        if (ContextCompat.checkSelfPermission(applicationContext,
                android.Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED ) {
            ActivityCompat.requestPermissions(this,
                arrayOf(android.Manifest.permission.ACCESS_COARSE_LOCATION), 1)
        }else {
            fusedLocationClient.lastLocation.addOnSuccessListener { location: Location? ->
                location?.let {
                    val apiCall = GEO_COORDINATES_URL + location.latitude + "&lon=" + location.longitude
                    updateWeatherData(apiCall)
                    sharedPreferences.edit().apply {
                        putString("location", "currentLocation")
                        apply()
                    }
                }
            }
        }
    }

    private fun updateWeatherData(apiCall: String) {
        object : Thread() {
            override fun run() {
                val jsonObject = getJSON(apiCall)
                runOnUiThread {
                    if (jsonObject != null) renderWeather(jsonObject)
                    else Toast.makeText(this@MainActivity, getString(R.string.data_not_found),
                        Toast.LENGTH_LONG).show()
                }
            }
        }.start()
    }

    private fun getJSON(apiCall: String): JSONObject? {
        try {
            val con = URL("$apiCall&appid=$APP_ID&units=metric").openConnection() as HttpURLConnection
            con.apply {
                doOutput = true
                connect()
            }
            val inputStream = con.inputStream
            val br = BufferedReader(InputStreamReader(inputStream!!))
            var line: String?
            val buffer = StringBuffer()
            while (br.readLine().also { line = it } != null) buffer.append(line + "\n")
            inputStream.close()
            con.disconnect()
            val jsonObject = JSONObject(buffer.toString())
            return if (jsonObject.getInt("cod") != 200) null
            else jsonObject
        } catch (t: Throwable) {
            return null
        }
    }

    private fun renderWeather(json: JSONObject) {
        try {
            val city = json.getString("name").uppercase(Locale.US)
            val country = json.getJSONObject("sys").getString("country")
            binding.textCity.text = resources.getString(R.string.city_field, city, country)
            val weatherDetails = json.optJSONArray("weather")?.getJSONObject(0)
            val main = json.getJSONObject("main")
            val description = weatherDetails?.getString("description")
            val humidity = main.getString("humidity")
            val pressure = main.getString("pressure")
            binding.textDetails.text = resources.getString(R.string.details_field, description, humidity, pressure)
            val iconID = weatherDetails?.getString("icon") ?: "03d"
            val urlString = "https://openweathermap.org/img/wn/$iconID@2x.png"
            Glide.with(this)
                .load(urlString)
                .transition(DrawableTransitionOptions.withCrossFade())
                .centerCrop()
                .into(binding.imgWeatherIcon)
            val temperature = main.getDouble("temp")
            binding.textTemperature.text = resources.getString(R.string.temperature_field, temperature)
            val df = DateFormat.getDateTimeInstance()
            val lastUpdated = df.format(Date(json.getLong("dt") * 1000))
            binding.textUpdated.text = resources.getString(R.string.updated_field, lastUpdated)
        } catch (ignore: Exception) { }
    }

}