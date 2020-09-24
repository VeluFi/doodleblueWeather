package com.example.weatherdoodleblue.activities

import android.Manifest
import android.app.ProgressDialog
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.net.ConnectivityManager
import android.os.AsyncTask
import android.os.Bundle
import android.preference.PreferenceManager
import android.provider.Settings
import android.text.format.DateFormat
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.Toolbar
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentTransaction
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout.OnRefreshListener
import androidx.viewpager.widget.ViewPager
import com.example.weatherdoodleblue.AlarmReceiver
import com.example.weatherdoodleblue.Constants
import com.example.weatherdoodleblue.R
import com.example.weatherdoodleblue.adapters.ViewPagerAdapter
import com.example.weatherdoodleblue.adapters.WeatherRecyclerAdapter
import com.example.weatherdoodleblue.fragments.AmbiguousLocationDialogFragment
import com.example.weatherdoodleblue.fragments.RecyclerViewFragment
import com.example.weatherdoodleblue.models.Weather
import com.example.weatherdoodleblue.tasks.GenericRequestTask
import com.example.weatherdoodleblue.tasks.ParseResult
import com.example.weatherdoodleblue.tasks.TaskOutput
import com.example.weatherdoodleblue.utils.Formatting
import com.example.weatherdoodleblue.utils.TimeUtils
import com.example.weatherdoodleblue.utils.UI
import com.example.weatherdoodleblue.utils.UnitConvertor
import com.example.weatherdoodleblue.widgets.AbstractWidgetProvider
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.appbar.AppBarLayout.OnOffsetChangedListener
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.tabs.TabLayout
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.text.DecimalFormat
import java.util.*

class MainActivity : BaseActivity(), LocationListener {
    private val todayWeather = Weather()
    private var todayTemperature: TextView? = null
    private var todayDescription: TextView? = null
    private var todayWind: TextView? = null
    private var todayPressure: TextView? = null
    private var todayHumidity: TextView? = null
    private var todaySunrise: TextView? = null
    private var todaySunset: TextView? = null
    private var todayUvIndex: TextView? = null
    private var lastUpdate: TextView? = null
    private var todayIcon: TextView? = null
    private val tapGraph: TextView? = null
    private var viewPager: ViewPager? = null
    private var tabLayout: TabLayout? = null
    private var swipeRefreshLayout: SwipeRefreshLayout? = null
    private var appView: View? = null
    private var locationManager: LocationManager? = null
    private var progressDialog: ProgressDialog? = null
    private val theme = 0
    private var widgetTransparent = false
    private var destroyed = false
    private var firstRun = false
    private var longTermWeather: MutableList<Weather>? = ArrayList()
    private var longTermTodayWeather: MutableList<Weather> = ArrayList()
    private var longTermTomorrowWeather: MutableList<Weather> = ArrayList()

    @JvmField
    var recentCityId: String? = ""
    private var formatting: Formatting? = null
    private var prefs: SharedPreferences? = null
    private var linearLayoutTapForGraphs: LinearLayout? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        PreferenceManager.setDefaultValues(this, R.xml.prefs, false)
        prefs = PreferenceManager.getDefaultSharedPreferences(this)
        firstRun = prefs!!.getBoolean("firstRun", true)
        widgetTransparent = prefs!!.getBoolean("transparentWidget", false)
        setTheme(UI.getTheme(prefs!!.getString("theme", "fresh")).also { super.theme = it })
        val darkTheme = super.darkTheme
        val blackTheme = super.blackTheme
        formatting = Formatting(this)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_scrolling)
        appView = findViewById(R.id.viewApp)
        swipeRefreshLayout = findViewById(R.id.swipeRefreshLayout)
        val appBarLayout = findViewById<AppBarLayout>(R.id.appBarLayout)
        progressDialog = ProgressDialog(this@MainActivity)
        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        if (darkTheme) {
            toolbar.popupTheme = R.style.AppTheme_PopupOverlay_Dark
        } else if (blackTheme) {
            toolbar.popupTheme = R.style.AppTheme_PopupOverlay_Black
        }
        todayTemperature = findViewById(R.id.todayTemperature)
        todayDescription = findViewById(R.id.todayDescription)
        todayWind = findViewById(R.id.todayWind)
        todayPressure = findViewById(R.id.todayPressure)
        todayHumidity = findViewById(R.id.todayHumidity)
        todaySunrise = findViewById(R.id.todaySunrise)
        todaySunset = findViewById(R.id.todaySunset)
        todayUvIndex = findViewById(R.id.todayUvIndex)
        lastUpdate = findViewById(R.id.lastUpdate)
        todayIcon = findViewById(R.id.todayIcon)
        linearLayoutTapForGraphs = findViewById(R.id.linearLayout_tap_for_graphs)
        /*        Typeface weatherFont = Typeface.createFromAsset(this.getAssets(), "fonts/weather.ttf");
        todayIcon.setTypeface(weatherFont);*/viewPager = findViewById(R.id.viewPager)
        tabLayout = findViewById(R.id.tabs)
        destroyed = false
        initMappings()

        // Preload data from cache
        preloadWeather()
        preloadUVIndex()
        updateLastUpdateTime()
        cityByLocation
        // Set autoupdater
        AlarmReceiver.setRecurringAlarm(this)
        swipeRefreshLayout!!.setOnRefreshListener(OnRefreshListener {
            refreshWeather()
            swipeRefreshLayout!!.isRefreshing = false
        })
        appBarLayout.addOnOffsetChangedListener(OnOffsetChangedListener { appBarLayout, verticalOffset -> swipeRefreshLayout!!.isEnabled = verticalOffset == 0 })
        val bundle = intent.extras
        if (bundle != null && bundle.getBoolean("shouldRefresh")) {
            refreshWeather()
        }
    }

    fun getAdapter(id: Int): WeatherRecyclerAdapter {
        val weatherRecyclerAdapter: WeatherRecyclerAdapter
        weatherRecyclerAdapter = if (id == 0) {
            WeatherRecyclerAdapter(longTermTodayWeather)
        } else if (id == 1) {
            WeatherRecyclerAdapter(longTermTomorrowWeather)
        } else {
            WeatherRecyclerAdapter(longTermWeather)
        }
        return weatherRecyclerAdapter
    }

    public override fun onStart() {
        super.onStart()
        updateTodayWeatherUI()
        updateLongTermWeatherUI()
        updateUVIndexUI()
    }

    public override fun onResume() {
        super.onResume()
        if (UI.getTheme(prefs!!.getString("theme", "fresh")) != super.theme ||
                PreferenceManager.getDefaultSharedPreferences(this).getBoolean("transparentWidget", false) != widgetTransparent) {
            overridePendingTransition(0, 0)
            prefs!!.edit().putBoolean("firstRun", true).commit()
            finish()
            overridePendingTransition(0, 0)
            startActivity(intent)
        } else if (shouldUpdate() && isNetworkAvailable) {
            getTodayWeather()
            getLongTermWeather()
            todayUVIndex
        }
        if (firstRun) {
            prefs!!.edit().putBoolean("firstRun", false).commit()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        destroyed = true
        if (locationManager != null) {
            try {
                locationManager!!.removeUpdates(this@MainActivity)
            } catch (e: SecurityException) {
                e.printStackTrace()
            }
        }
    }

    private fun preloadUVIndex() {
        val sp = PreferenceManager.getDefaultSharedPreferences(this@MainActivity)
        val lastUVIToday = sp.getString("lastToday", null)
        if (lastUVIToday != null && !lastUVIToday.isEmpty()) {
            val latitude = todayWeather.lat
            val longitude = todayWeather.lon
            if (latitude == 0.0 && longitude == 0.0) {
                return
            }
            TodayUVITask(this, this, progressDialog).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, "coords", java.lang.Double.toString(latitude), java.lang.Double.toString(longitude))
        }
    }

    private fun preloadWeather() {
        val sp = PreferenceManager.getDefaultSharedPreferences(this@MainActivity)
        val lastToday = sp.getString("lastToday", null)
        if (lastToday != null && !lastToday.isEmpty()) {
            TodayWeatherTask(this, this, progressDialog).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, "cachedResponse", lastToday)
        }
        val lastLongterm = sp.getString("lastLongterm", null)
        if (lastLongterm != null && !lastLongterm.isEmpty()) {
            LongTermWeatherTask(this, this, progressDialog).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, "cachedResponse", lastLongterm)
        }
    }

    private val todayUVIndex: Unit
        private get() {
            val latitude = todayWeather.lat
            val longitude = todayWeather.lon
            TodayUVITask(this, this, progressDialog).execute("coords", java.lang.Double.toString(latitude), java.lang.Double.toString(longitude))
        }

    private fun getTodayWeather() {
        TodayWeatherTask(this, this, progressDialog).execute()
    }

    private fun getLongTermWeather() {
        LongTermWeatherTask(this, this, progressDialog).execute()
    }

    private fun saveLocation(result: String) {
        val preferences = PreferenceManager.getDefaultSharedPreferences(this@MainActivity)
        recentCityId = preferences.getString("cityId", Constants.DEFAULT_CITY_ID)
        preferences.edit()
                .putString("cityId", result)
                .commit()
    }

    private fun parseTodayJson(result: String): ParseResult {
        try {
            val reader = JSONObject(result)
            val code = reader.optString("cod")
            if ("404" == code) {
                return ParseResult.CITY_NOT_FOUND
            }
            val city = reader.getString("name")
            var country = ""
            val countryObj = reader.optJSONObject("sys")
            if (countryObj != null) {
                country = countryObj.getString("country")
                todayWeather.setSunrise(countryObj.getString("sunrise"))
                todayWeather.setSunset(countryObj.getString("sunset"))
            }
            todayWeather.city = city
            todayWeather.country = country
            val coordinates = reader.getJSONObject("coord")
            if (coordinates != null) {
                todayWeather.lat = coordinates.getDouble("lat")
                todayWeather.lon = coordinates.getDouble("lon")
                val sp = PreferenceManager.getDefaultSharedPreferences(this)
                sp.edit().putFloat("latitude", todayWeather.lat.toFloat()).putFloat("longitude", todayWeather.lon.toFloat()).commit()
            }
            val main = reader.getJSONObject("main")
            todayWeather.temperature = main.getString("temp")
            todayWeather.description = reader.getJSONArray("weather").getJSONObject(0).getString("description")
            val windObj = reader.getJSONObject("wind")
            todayWeather.wind = windObj.getString("speed")
            if (windObj.has("deg")) {
                todayWeather.windDirectionDegree = windObj.getDouble("deg")
            } else {
                Log.e("parseTodayJson", "No wind direction available")
                todayWeather.windDirectionDegree = null
            }
            todayWeather.pressure = main.getString("pressure")
            todayWeather.humidity = main.getString("humidity")
            val rainObj = reader.optJSONObject("rain")
            val rain: String
            rain = if (rainObj != null) {
                getRainString(rainObj)
            } else {
                val snowObj = reader.optJSONObject("snow")
                snowObj?.let { getRainString(it) } ?: "0"
            }
            todayWeather.rain = rain
            val idString = reader.getJSONArray("weather").getJSONObject(0).getString("id")
            todayWeather.id = idString
            todayWeather.icon = formatting!!.setWeatherIcon(idString.toInt(), TimeUtils.isDayTime(todayWeather, Calendar.getInstance()))
            PreferenceManager.getDefaultSharedPreferences(this@MainActivity)
                    .edit()
                    .putString("lastToday", result)
                    .commit()
        } catch (e: JSONException) {
            Log.e("JSONException Data", result)
            e.printStackTrace()
            return ParseResult.JSON_EXCEPTION
        }
        return ParseResult.OK
    }

    private fun parseTodayUVIJson(result: String): ParseResult {
        try {
            val reader = JSONObject(result)
            val code = reader.optString("cod")
            if ("404" == code) {
                todayWeather.uvIndex = -1.0
                return ParseResult.CITY_NOT_FOUND
            }
            val value = reader.getDouble("value")
            todayWeather.uvIndex = value
            PreferenceManager.getDefaultSharedPreferences(this@MainActivity)
                    .edit()
                    .putString("lastUVIToday", result)
                    .commit()
        } catch (e: JSONException) {
            Log.e("JSONException Data", result)
            e.printStackTrace()
            return ParseResult.JSON_EXCEPTION
        }
        return ParseResult.OK
    }

    private fun updateTodayWeatherUI() {
        try {
            if (todayWeather.country.isEmpty()) {
                preloadWeather()
                return
            }
        } catch (e: Exception) {
            preloadWeather()
            return
        }
        val city = todayWeather.city
        val country = todayWeather.country
        val timeFormat = DateFormat.getTimeFormat(applicationContext)
        val actionBar = supportActionBar
        if (actionBar != null) {
            actionBar.title = city + if (country.isEmpty()) "" else ", $country"
        }
        val sp = PreferenceManager.getDefaultSharedPreferences(this@MainActivity)

        // Temperature
        var temperature = UnitConvertor.convertTemperature(todayWeather.temperature.toFloat(), sp)
        if (sp.getBoolean("temperatureInteger", false)) {
            temperature = Math.round(temperature).toFloat()
        }

        // Rain
        val rain = todayWeather.rain.toDouble()
        val rainString = UnitConvertor.getRainString(rain, sp)

        // Wind
        var wind: Double
        wind = try {
            todayWeather.wind.toDouble()
        } catch (e: Exception) {
            e.printStackTrace()
            0.0
        }
        wind = UnitConvertor.convertWind(wind, sp)

        // Pressure
        val pressure = UnitConvertor.convertPressure(todayWeather.pressure.toDouble().toFloat(), sp).toDouble()
        todayTemperature!!.text = DecimalFormat("0.#").format(temperature.toDouble()) + " " + sp.getString("unit", "°C")
        todayDescription!!.text = todayWeather.description.substring(0, 1).toUpperCase() +
                todayWeather.description.substring(1) + rainString
        if (sp.getString("speedUnit", "m/s") == "bft") {
            todayWind!!.text = getString(R.string.wind) + ": " +
                    UnitConvertor.getBeaufortName(wind.toInt(), this) +
                    if (todayWeather.isWindDirectionAvailable) " " + getWindDirectionString(sp, this, todayWeather) else ""
        } else {
            todayWind!!.text = getString(R.string.wind) + ": " + DecimalFormat("0.0").format(wind) + " " +
                    localize(sp, "speedUnit", "m/s") +
                    if (todayWeather.isWindDirectionAvailable) " " + getWindDirectionString(sp, this, todayWeather) else ""
        }
        todayPressure!!.text = getString(R.string.pressure) + ": " + DecimalFormat("0.0").format(pressure) + " " +
                localize(sp, "pressureUnit", "hPa")
        todayHumidity!!.text = getString(R.string.humidity) + ": " + todayWeather.humidity + " %"
        todaySunrise!!.text = getString(R.string.sunrise) + ": " + timeFormat.format(todayWeather.sunrise)
        todaySunset!!.text = getString(R.string.sunset) + ": " + timeFormat.format(todayWeather.sunset)
        todayIcon!!.text = todayWeather.icon
    }

    private fun updateUVIndexUI() {
        try {
            if (todayWeather.country.isEmpty()) {
                return
            }
        } catch (e: Exception) {
            preloadUVIndex()
            return
        }

        // UV Index
        val uvIndex = todayWeather.uvIndex
        todayUvIndex!!.text = getString(R.string.uvindex) + ": " + UnitConvertor.convertUvIndexToRiskLevel(uvIndex, this)
    }

    fun parseLongTermJson(result: String?): ParseResult {
        var i: Int
        try {
            val reader = JSONObject(result)
            val code = reader.optString("cod")
            if ("404" == code) {
                if (longTermWeather == null) {
                    longTermWeather = ArrayList()
                    longTermTodayWeather = ArrayList()
                    longTermTomorrowWeather = ArrayList()
                }
                return ParseResult.CITY_NOT_FOUND
            }
            longTermWeather = ArrayList()
            longTermTodayWeather = ArrayList()
            longTermTomorrowWeather = ArrayList()
            val list = reader.getJSONArray("list")
            i = 0
            while (i < list.length()) {
                val weather = Weather()
                val listItem = list.getJSONObject(i)
                val main = listItem.getJSONObject("main")
                weather.setDate(listItem.getString("dt"))
                weather.temperature = main.getString("temp")
                weather.description = listItem.optJSONArray("weather").getJSONObject(0).getString("description")
                val windObj = listItem.optJSONObject("wind")
                if (windObj != null) {
                    weather.wind = windObj.getString("speed")
                    weather.windDirectionDegree = windObj.getDouble("deg")
                }
                weather.pressure = main.getString("pressure")
                weather.humidity = main.getString("humidity")
                val rainObj = listItem.optJSONObject("rain")
                var rain = ""
                rain = if (rainObj != null) {
                    getRainString(rainObj)
                } else {
                    val snowObj = listItem.optJSONObject("snow")
                    snowObj?.let { getRainString(it) } ?: "0"
                }
                weather.rain = rain
                val idString = listItem.optJSONArray("weather").getJSONObject(0).getString("id")
                weather.id = idString
                val dateMsString = listItem.getString("dt") + "000"
                val cal = Calendar.getInstance()
                cal.timeInMillis = dateMsString.toLong()
                weather.icon = formatting!!.setWeatherIcon(idString.toInt(), TimeUtils.isDayTime(weather, cal))
                val today = Calendar.getInstance()
                today[Calendar.HOUR_OF_DAY] = 0
                today[Calendar.MINUTE] = 0
                today[Calendar.SECOND] = 0
                today[Calendar.MILLISECOND] = 0
                val tomorrow = today.clone() as Calendar
                tomorrow.add(Calendar.DAY_OF_YEAR, 1)
                val later = today.clone() as Calendar
                later.add(Calendar.DAY_OF_YEAR, 2)
                if (cal.before(tomorrow)) {
                    longTermTodayWeather.add(weather)
                } else if (cal.before(later)) {
                    longTermTomorrowWeather.add(weather)
                } else {
                    longTermWeather!!.add(weather)
                }
                i++
            }
            PreferenceManager.getDefaultSharedPreferences(this@MainActivity)
                    .edit()
                    .putString("lastLongterm", result)
                    .commit()
        } catch (e: JSONException) {
            Log.e("JSONException Data", result)
            e.printStackTrace()
            return ParseResult.JSON_EXCEPTION
        }
        return ParseResult.OK
    }

    private fun updateLongTermWeatherUI() {
        if (destroyed) {
            return
        }
        val viewPagerAdapter = ViewPagerAdapter(supportFragmentManager)
        val bundleToday = Bundle()
        bundleToday.putInt("day", 0)
        val recyclerViewFragmentToday = RecyclerViewFragment()
        recyclerViewFragmentToday.arguments = bundleToday
        viewPagerAdapter.addFragment(recyclerViewFragmentToday, getString(R.string.today))
        val bundleTomorrow = Bundle()
        bundleTomorrow.putInt("day", 1)
        val recyclerViewFragmentTomorrow = RecyclerViewFragment()
        recyclerViewFragmentTomorrow.arguments = bundleTomorrow
        viewPagerAdapter.addFragment(recyclerViewFragmentTomorrow, getString(R.string.tomorrow))
        var currentPage = viewPager!!.currentItem
        viewPagerAdapter.notifyDataSetChanged()
        viewPager!!.adapter = viewPagerAdapter
        tabLayout!!.setupWithViewPager(viewPager)
        if (currentPage == 0 && longTermTodayWeather.isEmpty()) {
            currentPage = 1
        }
        viewPager!!.setCurrentItem(currentPage, false)
    }

    private val isNetworkAvailable: Boolean
        private get() {
            val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val activeNetworkInfo = connectivityManager.activeNetworkInfo
            return activeNetworkInfo != null && activeNetworkInfo.isConnected
        }

    private fun shouldUpdate(): Boolean {
        val lastUpdate = PreferenceManager.getDefaultSharedPreferences(this).getLong("lastUpdate", -1)
        val cityChanged = PreferenceManager.getDefaultSharedPreferences(this).getBoolean("cityChanged", false)
        return cityChanged || lastUpdate < 0 || Calendar.getInstance().timeInMillis - lastUpdate > NO_UPDATE_REQUIRED_THRESHOLD
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        val id = item.itemId
        if (id == R.id.action_refresh) {
            refreshWeather()
            return true
        }
        if (id == R.id.action_location) {
            cityByLocation
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    fun refreshWeather() {
        if (isNetworkAvailable) {
            getTodayWeather()
            getLongTermWeather()
            todayUVIndex
        } else {
            Snackbar.make(appView!!, getString(R.string.msg_connection_not_available), Snackbar.LENGTH_LONG).show()
        }
    }

    private fun localize(sp: SharedPreferences, preferenceKey: String, defaultValueKey: String): String? {
        return localize(sp, this, preferenceKey, defaultValueKey)
    }

    val cityByLocation: Unit
        get() {
            locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                if (ActivityCompat.shouldShowRequestPermissionRationale(this,
                                Manifest.permission.ACCESS_FINE_LOCATION)) {
                    showLocationSettingsDialog()
                } else {
                    ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                            MY_PERMISSIONS_ACCESS_FINE_LOCATION)
                }
            } else if (locationManager!!.isProviderEnabled(LocationManager.NETWORK_PROVIDER) ||
                    locationManager!!.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                progressDialog = ProgressDialog(this)
                progressDialog!!.setMessage(getString(R.string.getting_location))
                progressDialog!!.setCancelable(false)
                progressDialog!!.setButton(DialogInterface.BUTTON_NEGATIVE, getString(R.string.dialog_cancel)) { dialogInterface, i ->
                    try {
                        locationManager!!.removeUpdates(this@MainActivity)
                    } catch (e: SecurityException) {
                        e.printStackTrace()
                    }
                }
                progressDialog!!.show()
                if (locationManager!!.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                    locationManager!!.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 0, 0f, this)
                }
                if (locationManager!!.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                    locationManager!!.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0f, this)
                }
            } else {
                showLocationSettingsDialog()
            }
        }

    private fun showLocationSettingsDialog() {
        val alertDialog = AlertDialog.Builder(this)
        alertDialog.setTitle(R.string.location_settings)
        alertDialog.setMessage(R.string.location_settings_message)
        alertDialog.setPositiveButton(R.string.location_settings_button) { dialog, which ->
            val intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
            startActivity(intent)
        }
        alertDialog.setNegativeButton(R.string.dialog_cancel) { dialog, which -> dialog.cancel() }
        alertDialog.show()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        if (requestCode == MY_PERMISSIONS_ACCESS_FINE_LOCATION) {
            if (grantResults.size > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                cityByLocation
            }
        }
    }

    override fun onLocationChanged(location: Location) {
        progressDialog!!.hide()
        try {
            locationManager!!.removeUpdates(this)
        } catch (e: SecurityException) {
            Log.e("LocationManager", "Error while trying to stop listening for location updates. This is probably a permissions issue", e)
        }
        Log.i("LOCATION (" + location.provider.toUpperCase() + ")", location.latitude.toString() + ", " + location.longitude)
        val latitude = location.latitude
        val longitude = location.longitude
        ProvideCityNameTask(this, this, progressDialog).execute("coords", java.lang.Double.toString(latitude), java.lang.Double.toString(longitude))
    }

    override fun onStatusChanged(provider: String, status: Int, extras: Bundle) {}
    override fun onProviderEnabled(provider: String) {}
    override fun onProviderDisabled(provider: String) {}
    internal inner class TodayWeatherTask(context: Context?, activity: MainActivity?, progressDialog: ProgressDialog?) : GenericRequestTask(context, activity, progressDialog) {
        override fun onPreExecute() {
            loading = 0
            super.onPreExecute()
        }

        override fun onPostExecute(output: TaskOutput) {
            super.onPostExecute(output)
            AbstractWidgetProvider.updateWidgets(context)
        }

        override fun parseResponse(response: String): ParseResult {
            return parseTodayJson(response)
        }

        override fun getAPIName(): String {
            return "weather"
        }

        override fun updateMainUI() {
            updateTodayWeatherUI()
            updateLastUpdateTime()
            updateUVIndexUI()
        }
    }

    internal inner class LongTermWeatherTask(context: Context?, activity: MainActivity?, progressDialog: ProgressDialog?) : GenericRequestTask(context, activity, progressDialog) {
        override fun parseResponse(response: String): ParseResult {
            return parseLongTermJson(response)
        }

        override fun getAPIName(): String {
            return "forecast"
        }

        override fun updateMainUI() {
            updateLongTermWeatherUI()
        }
    }

    private fun launchLocationPickerDialog(cityList: JSONArray) {
        val fragment = AmbiguousLocationDialogFragment()
        val bundle = Bundle()
        val fragmentTransaction = supportFragmentManager.beginTransaction()
        bundle.putString("cityList", cityList.toString())
        fragment.arguments = bundle
        fragmentTransaction.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_OPEN)
        fragmentTransaction.add(android.R.id.content, fragment)
                .addToBackStack(null).commit()
    }

    internal inner class ProvideCityNameTask(context: Context?, activity: MainActivity?, progressDialog: ProgressDialog?) : GenericRequestTask(context, activity, progressDialog) {
        override fun onPreExecute() { /*Nothing*/
        }

        override fun getAPIName(): String {
            return "weather"
        }

        override fun parseResponse(response: String): ParseResult {
            Log.i("RESULT", response)
            try {
                val reader = JSONObject(response)
                val code = reader.optString("cod")
                if ("404" == code) {
                    Log.e("Geolocation", "No city found")
                    return ParseResult.CITY_NOT_FOUND
                }
                saveLocation(reader.getString("id"))
            } catch (e: JSONException) {
                Log.e("JSONException Data", response)
                e.printStackTrace()
                return ParseResult.JSON_EXCEPTION
            }
            return ParseResult.OK
        }

        override fun onPostExecute(output: TaskOutput) {
            /* Handle possible errors only */
            handleTaskOutput(output)
            refreshWeather()
        }
    }

    internal inner class TodayUVITask(context: Context?, activity: MainActivity?, progressDialog: ProgressDialog?) : GenericRequestTask(context, activity, progressDialog) {
        override fun onPreExecute() {
            loading = 0
            super.onPreExecute()
        }

        override fun parseResponse(response: String): ParseResult {
            return parseTodayUVIJson(response)
        }

        override fun getAPIName(): String {
            return "uvi"
        }

        override fun updateMainUI() {
            updateUVIndexUI()
        }
    }

    private fun updateLastUpdateTime(timeInMillis: Long =
                                             PreferenceManager.getDefaultSharedPreferences(this).getLong("lastUpdate", -1)
    ) {
        if (timeInMillis < 0) {
            lastUpdate!!.text = ""
        } else {
            lastUpdate!!.text = getString(R.string.last_update, formatTimeWithDayIfNotToday(this, timeInMillis))
        }
    }

    companion object {
        protected const val MY_PERMISSIONS_ACCESS_FINE_LOCATION = 1

        // Time in milliseconds; only reload weather if last update is longer ago than this value
        private const val NO_UPDATE_REQUIRED_THRESHOLD = 300000
        private val speedUnits: MutableMap<String?, Int> = HashMap(3)
        private val pressUnits: MutableMap<String?, Int> = HashMap(3)
        private var mappingsInitialised = false
        fun getRainString(rainObj: JSONObject?): String {
            var rain = "0"
            if (rainObj != null) {
                rain = rainObj.optString("3h", "fail")
                if ("fail" == rain) {
                    rain = rainObj.optString("1h", "0")
                }
            }
            return rain
        }

        @JvmStatic
        fun initMappings() {
            if (mappingsInitialised) return
            mappingsInitialised = true
            speedUnits["m/s"] = R.string.speed_unit_mps
            speedUnits["kph"] = R.string.speed_unit_kph
            speedUnits["mph"] = R.string.speed_unit_mph
            speedUnits["kn"] = R.string.speed_unit_kn
            pressUnits["hPa"] = R.string.pressure_unit_hpa
            pressUnits["kPa"] = R.string.pressure_unit_kpa
            pressUnits["mm Hg"] = R.string.pressure_unit_mmhg
        }

        @JvmStatic
        fun localize(sp: SharedPreferences, context: Context, preferenceKey: String, defaultValueKey: String?): String? {
            val preferenceValue = sp.getString(preferenceKey, defaultValueKey)
            var result = preferenceValue
            if ("speedUnit" == preferenceKey) {
                if (speedUnits.containsKey(preferenceValue)) {
                    result = context.getString(speedUnits[preferenceValue]!!)
                }
            } else if ("pressureUnit" == preferenceKey) {
                if (pressUnits.containsKey(preferenceValue)) {
                    result = context.getString(pressUnits[preferenceValue]!!)
                }
            }
            return result
        }

        @JvmStatic
        fun getWindDirectionString(sp: SharedPreferences, context: Context?, weather: Weather): String {
            try {
                if (weather.wind.toDouble() != 0.0) {
                    val pref = sp.getString("windDirectionFormat", null)
                    if ("arrow" == pref) {
                        return weather.getWindDirection(8).getArrow(context)
                    } else if ("abbr" == pref) {
                        return weather.windDirection.getLocalizedString(context)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            return ""
        }

        @JvmStatic
        fun saveLastUpdateTime(sp: SharedPreferences): Long {
            val now = Calendar.getInstance()
            sp.edit().putLong("lastUpdate", now.timeInMillis).commit()
            return now.timeInMillis
        }

        @JvmStatic
        fun formatTimeWithDayIfNotToday(context: Context?, timeInMillis: Long): String {
            val now = Calendar.getInstance()
            val lastCheckedCal: Calendar = GregorianCalendar()
            lastCheckedCal.timeInMillis = timeInMillis
            val lastCheckedDate = Date(timeInMillis)
            val timeFormat = DateFormat.getTimeFormat(context).format(lastCheckedDate)
            return if (now[Calendar.YEAR] == lastCheckedCal[Calendar.YEAR] &&
                    now[Calendar.DAY_OF_YEAR] == lastCheckedCal[Calendar.DAY_OF_YEAR]) {
                timeFormat
            } else {
                DateFormat.getDateFormat(context).format(lastCheckedDate) + " " + timeFormat
            }
        }
    }
}