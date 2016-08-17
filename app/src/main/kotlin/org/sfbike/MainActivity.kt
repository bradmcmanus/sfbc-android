package org.sfbike

import android.content.DialogInterface
import android.location.Location
import android.os.Bundle
import android.os.Handler
import android.support.v4.app.FragmentActivity
import android.view.KeyEvent
import android.widget.Toast
import com.github.salomonbrys.kotson.fromJson
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.api.GoogleApiClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.LocationSource
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import com.google.gson.Gson
import com.google.maps.android.geojson.GeoJsonFeature
import com.google.maps.android.geojson.GeoJsonLayer
import com.google.maps.android.geojson.GeoJsonMultiPolygon
import com.google.maps.android.geojson.GeoJsonParser
import org.sfbike.data.SfpdStation
import org.sfbike.util.Geo
import org.sfbike.util.GooglePlayUtil
import java.io.BufferedReader
import java.io.InputStreamReader

class MainActivity : FragmentActivity() {

    //region Members

    companion object {
        private val bearing = LatLng(37.777998, -122.409411)
        private val DEFAULT_ZOOM = 13.88.toFloat()
    }

    private val mHandler = Handler()
    private var map: GoogleMap? = null

    lateinit private var stations: MutableList<SfpdStation>
    lateinit private var sfpdFeatureParser: GeoJsonParser


    //endregion

    //region Overrides

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_maps)

        val mapFragment = supportFragmentManager.findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync { googleMap ->
            map = googleMap
            zoom(bearing, addMarker = true, zoom = DEFAULT_ZOOM)

            loadSfpdStationInfo()
            loadSfpdShapes()
            showStationInfo(bearing)
        }

        mHandler.postDelayed({ if (map != null) { map!!.setLocationSource(locationSource) } }, 10000)
    }

    override fun onStart() {
        super.onStart()
        checkGooglePlayServicesAvailability()
        locationClient.connect()
    }

    override fun onStop() {
        locationClient.disconnect()
        super.onStop()
    }

    //endregion

    //region Helpers

    private fun zoom(latLng: LatLng, addMarker: Boolean, zoom: Float) {
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

    private fun showStationInfo(location: LatLng) {
        for (feature in sfpdFeatureParser.features) {
            if (feature.geometry is GeoJsonMultiPolygon) {
                for (poly in (feature.geometry as GeoJsonMultiPolygon).polygons) {
                    if (Geo.coordinateIsInsidePolygon(location.latitude, location.longitude, poly.coordinates[0])) {
                        stationToast(feature)
                    }
                }
            }
        }
    }

    private fun stationToast(feature: GeoJsonFeature) {
        val id = feature.getProperty("objectid").toInt()
        val station = stations.first { it.id == id }
        Toast.makeText(this@MainActivity, "${station.name}", Toast.LENGTH_LONG).show()
    }

    //endregion

    //region JSON / GeoJSON parsing

    private fun loadSfpdShapes() {
        if (map == null) { return }

        // Load features to find point inside a (multi)-polygon
        val stream = resources.openRawResource(R.raw.sfpd_shapes)
        val json = GeoJsonLayer.createJsonFileObject(stream)
        sfpdFeatureParser = GeoJsonParser(json)

        // Add SFPD district shapes to the map
        val sfpdLayer = GeoJsonLayer(map, json)
        sfpdLayer.addLayerToMap()

        // Feature click listener
        sfpdLayer.setOnFeatureClickListener { feature ->  if (feature != null) { stationToast(feature) } }
    }

    private fun loadSfpdStationInfo() {
        val gson = Gson()
        val raw = resources.openRawResource(R.raw.sfpd_stations)
        val reader = BufferedReader(InputStreamReader(raw))
        stations = gson.fromJson<List<SfpdStation>>(reader).toMutableList()
        for (station in stations) { log("${station.id} + ${station.email}") }
    }

    //endregion

    //region Current Location

    private var mLocation: Location? = null
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
        GoogleApiClient.Builder(this,
                object : GoogleApiClient.ConnectionCallbacks {
                    override fun onConnected(p0: Bundle?) {
                        if (!locationClient.isConnected) {
                            return
                        }
                        val location = LocationServices.FusedLocationApi.getLastLocation(locationClient)
                        if (location == null || (location.latitude == 0.0 && location.longitude == 0.0)) {
                            log.e("LocationClient - Received bogus location, not using", RuntimeException("bad loc"))
                            return
                        }

                        mLocation = location
                        if (locationListener != null) {
                            locationListener!!.onLocationChanged(mLocation!!)
                        }
//                        if (mState != State.TRIP_DETAILS) {
//                            centerMapOnMyLocation()
//                        }
//                        mSearchView.setLocation(mLocation!!)
                        log("LocationClient - delivered loc=" + location.latitude + ", " + location.longitude)
                        locationClient.disconnect()
                    }

                    override fun onConnectionSuspended(p0: Int) {
                        log.w("LocationClient - onConnectionSuspended code=${p0}")
                    }
                },
                object : GoogleApiClient.OnConnectionFailedListener {
                    override fun onConnectionFailed(result: ConnectionResult) {
                        log.w("LocationClient - onConnectionFailed($result)")
                    }
                })
                .addApi(LocationServices.API)
                .build()
    }

    //endregion

    //region Google Play Services

    private fun checkGooglePlayServicesAvailability() {
        GooglePlayUtil.checkGooglePlayServicesAvailability(this, object : DialogInterface.OnCancelListener {
            override fun onCancel(dialog: DialogInterface) {
                finish()
            }
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
