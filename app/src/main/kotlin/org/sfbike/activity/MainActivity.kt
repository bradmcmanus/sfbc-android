package org.sfbike.activity

import android.Manifest
import android.content.DialogInterface
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.location.Location
import android.net.Uri
import android.os.Bundle
import android.os.PersistableBundle
import android.support.v4.app.ActivityCompat
import android.support.v4.app.FragmentActivity
import android.support.v4.content.ContextCompat
import android.view.KeyEvent
import android.view.View
import com.github.salomonbrys.kotson.fromJson
import com.google.android.gms.common.api.GoogleApiClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.LocationSource
import com.google.android.gms.maps.MapView
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import com.google.gson.Gson
import com.google.maps.android.geojson.*
import org.json.JSONObject
import org.sfbike.R
import org.sfbike.data.District
import org.sfbike.data.Station
import org.sfbike.util.*
import org.sfbike.view.DashboardView
import java.io.BufferedReader
import java.io.InputStreamReader

class MainActivity : FragmentActivity() {

    //region Members

    companion object {
        private val DEFAULT_ZOOM = 13.88.toFloat()
        private val TAP_ZOOM = 14.7.toFloat()

        private val REQUEST_CODE_READ_EXTERNAL_STORAGE = 2345
    }

    val mapView: MapView by bindView(R.id.map_view)
    val dashboardView: DashboardView by bindView(R.id.dashboard_view)

    private var map: GoogleMap? = null
    private var imageUri: Uri? = null

    //endregion

    //region Overrides

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        mapView.onCreate(savedInstanceState)

        mapView.getMapAsync { googleMap ->
            map = googleMap

            googleMap.setOnMapClickListener { loc ->
                googleMap.clear()
                googleMap.addMarker(MarkerOptions().position(loc))
                bindLocation(loc)
                zoom(loc, false, TAP_ZOOM)
            }

            configureMap()
            loadIntentImageExtra(intent)
        }
    }

    override fun onStart() {
        super.onStart()
        checkGooglePlayServicesAvailability()
        locationClient.connect()
    }

    override fun onResume() {
        super.onResume()
        mapView.onResume()
    }

    override fun onPause() {
        mapView.onPause()
        super.onPause()
    }

    override fun onStop() {
        locationClient.disconnect()
        super.onStop()
    }

    override fun onDestroy() {
        mapView.onDestroy()
        super.onDestroy()
    }

    override fun onSaveInstanceState(outState: Bundle?, outPersistentState: PersistableBundle?) {
        super.onSaveInstanceState(outState, outPersistentState)
        mapView.onSaveInstanceState(outState)
    }

    override fun onLowMemory() {
        super.onLowMemory()
        mapView.onLowMemory()
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        if (intent != null) {
            // TODO show loading?
            loadIntentImageExtra(intent)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        configureMap()

        when (requestCode) {
            REQUEST_CODE_READ_EXTERNAL_STORAGE -> {
                // If request is cancelled, the result arrays are empty.
                if (grantResults.size > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {

                    // permission was granted, yay! Do the
                    // contacts-related task you need to do.

                    readImageLocationData()
                } else {

                    // permission denied, boo! Disable the
                    // functionality that depends on this permission.
                }
                return
            }
        }
    }

    //endregion

    //region Helpers

    val locationGranted: Boolean
        get() = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) ==
                        PackageManager.PERMISSION_GRANTED

    private fun configureMap() {
        if (locationGranted) {
            map?.mapType = GoogleMap.MAP_TYPE_NORMAL
            map?.isMyLocationEnabled = true
            map?.uiSettings?.isCompassEnabled = true
            map?.uiSettings?.isMyLocationButtonEnabled = true
            map?.clear()
        } else {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), 371)
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION), 371)
        }
    }

    private fun zoom(latLng: LatLng, addMarker: Boolean = false, zoom: Float = DEFAULT_ZOOM) {
        if (addMarker) {
            map?.clear()
            map?.addMarker(MarkerOptions().position(latLng))
        }

        map?.animateCamera(CameraUpdateFactory.newCameraPosition(
                CameraPosition.Builder()
                        .target(latLng)
                        .zoom(zoom)
                        .build()))
    }

    private fun bindLocation(location: LatLng) {
        val station = getStation(location)
        val district = getDistrict(location)

        dashboardView.visibility = View.VISIBLE
        dashboardView.bindStation(station.first)
        dashboardView.bindDistrict(district.first)
        dashboardView.imageUri = imageUri

        val style = GeoJsonPolygonStyle()
        style.fillColor = adjustAlpha(getColor(R.color.material_blue_grey_800), .7f)
        station.second?.polygonStyle = style

        val lineStyle = GeoJsonLineStringStyle()
        lineStyle.color = getColor(android.R.color.white)
        station.second?.lineStringStyle = lineStyle

        val stationLayer = GeoJsonLayer(map, station.second)
        stationLayer.addLayerToMap()

        val supeStyle = GeoJsonPolygonStyle()
        supeStyle.fillColor = adjustAlpha(getColor(R.color.material_deep_teal_500), .7f)
        district.second?.polygonStyle = supeStyle
        val districtLayer = GeoJsonLayer(map, district.second)
        districtLayer.addLayerToMap()
    }

    fun adjustAlpha(color: Int, factor: Float): Int {
        val alpha = Math.round(Color.alpha(color) * factor)
        val red = Color.red(color)
        val green = Color.green(color)
        val blue = Color.blue(color)
        return Color.argb(alpha, red, green, blue)
    }

    private fun getStation(location: LatLng): Pair<Station?, GeoJsonFeature?> {
        for (feature in sfpdFeatureParser.features) {
            if (feature.geometry is GeoJsonMultiPolygon) {
                for (poly in (feature.geometry as GeoJsonMultiPolygon).polygons) {
                    if (GeoUtil.coordinateIsInsidePolygon(location.latitude, location.longitude, poly.coordinates[0])) {
                        return Pair(stationForFeature(feature), feature)
                    }
                }
            }
        }
        return Pair(null, null)
    }

    private fun stationForFeature(feature: GeoJsonFeature): Station? {
        val id = feature.getProperty("objectid").toInt()
        return sfpdInfo.firstOrNull { it.id == id }
    }

    private fun getDistrict(location: LatLng): Pair<District?, GeoJsonFeature?> {
        for (feature in supeParser.features) {
            if (feature.geometry is GeoJsonMultiPolygon) {
                for (poly in (feature.geometry as GeoJsonMultiPolygon).polygons) {
                    if (GeoUtil.coordinateIsInsidePolygon(location.latitude, location.longitude, poly.coordinates[0])) {
                        return Pair(districtForFeature(feature), feature)
                    }
                }
            }
        }
        return Pair(null, null)
    }

    private fun districtForFeature(feature: GeoJsonFeature): District? {
        val id = feature.getProperty("supervisor").toInt()
        return supeInfo.firstOrNull { it.id == id }
    }

    //endregion

    //region SFPD JSON / GeoJSON parsing

    val gson: Gson by lazy {
        Gson()
    }

    val sfpdShapeJson: JSONObject by lazy {
        val stream = resources.openRawResource(R.raw.sfpd_shapes)
        GeoJsonLayer.createJsonFileObject(stream)
    }

    val sfpdFeatureParser: GeoJsonParser by lazy {
        GeoJsonParser(sfpdShapeJson)
    }

    val sfpdInfo: MutableList<Station> by lazy {
        val raw = resources.openRawResource(R.raw.sfpd_stations)
        val reader = BufferedReader(InputStreamReader(raw))
        gson.fromJson<List<Station>>(reader).toMutableList()
    }

    private fun displaySfpdShapes() {
        // Add SFPD district shapes to the map
        val sfpdLayer = GeoJsonLayer(map, sfpdShapeJson)
        sfpdLayer.addLayerToMap()

        // Feature click listener
        // sfpdLayer.setOnFeatureClickListener { feature ->  if (feature != null) { stationToast(feature) } }
    }

    //endregion

    //region Supervisor District JSON GeoJSON parsing

    val supeJson: JSONObject by lazy {
        val stream = resources.openRawResource(R.raw.supervisor_shapes)
        GeoJsonLayer.createJsonFileObject(stream)
    }

    val supeParser: GeoJsonParser by lazy {
        GeoJsonParser(supeJson)
    }

    val supeInfo: MutableList<District> by lazy {
        val raw = resources.openRawResource(R.raw.supervisor_districts)
        val reader = BufferedReader(InputStreamReader(raw))
        gson.fromJson<List<District>>(reader).toMutableList()
    }

    //endregion

    //region Image

    var hasPendingImage: Boolean = false

    private fun loadIntentImageExtra(intent: Intent) {
        imageUri = ImageUtil.uriFromIntent(intent)

        if (imageUri != null) {
            if (checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                hasPendingImage = true

                // Should we show an explanation?
                if (shouldShowRequestPermissionRationale(Manifest.permission.READ_EXTERNAL_STORAGE)) {
                    // Explain to the user why we need to read the contacts
                }

                requestPermissions(arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE), REQUEST_CODE_READ_EXTERNAL_STORAGE)
            } else {
                readImageLocationData()
            }
        }
    }

    private fun readImageLocationData() {
        val imageLatLng: LatLng? = ImageUtil.latLngFromUri(this, imageUri)

        if (imageLatLng != null) {
            zoom(imageLatLng, addMarker = true)
            bindLocation(imageLatLng)
        }
        if (imageUri != null) dashboardView.bindImage(imageUri)
    }

    //endregion

    //region Current Location

    private fun onLocation(location: Location) {
        this@MainActivity.location = location
        locationListener?.onLocationChanged(location)

        if (imageUri == null) {
            val latLng = LatLng(location.latitude, location.longitude)

            zoom(latLng)
            bindLocation(latLng)
        }
    }

    private var location: Location? = null
    var locationListener: LocationSource.OnLocationChangedListener? = null
    val locationSource = object : LocationSource {
        override fun activate(listener: LocationSource.OnLocationChangedListener?) {
            locationListener = listener
        }

        override fun deactivate() {
            locationListener = null
        }
    }
    val locationClient: GoogleApiClient by lazy {
        GoogleApiClient.Builder(this, object : GoogleApiClient.ConnectionCallbacks {
            override fun onConnected(p0: Bundle?) {
                if (!locationClient.isConnected) {
                    return
                }
                val location = LocationServices.FusedLocationApi.getLastLocation(locationClient)
                if (location == null || (location.latitude == 0.0 && location.longitude == 0.0)) {
                    log.e("LocationClient - Received bogus location, not using", RuntimeException("bad loc"))
                    return
                }

                onLocation(location)

                log("LocationClient - delivered loc=" + location.latitude + ", " + location.longitude)
                locationClient.disconnect()
            }

            override fun onConnectionSuspended(p0: Int) = log.w("LocationClient - onConnectionSuspended code=${p0}")
        }, { result -> log.w("LocationClient - onConnectionFailed($result)") })
                .addApi(LocationServices.API)
                .build()
    }

    //endregion

    //region Google Play Services

    private fun checkGooglePlayServicesAvailability() {
        GooglePlayUtil.checkGooglePlayServicesAvailability(this, object : DialogInterface.OnCancelListener {
            override fun onCancel(dialog: DialogInterface) = finish()
        }, object : DialogInterface.OnKeyListener {
            override fun onKey(dialog: DialogInterface, keyCode: Int, event: KeyEvent): Boolean {
                if (keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_UP) {
                    finish()
                    return true
                }
                return false
            }
        })
    }

    //endregion
}
