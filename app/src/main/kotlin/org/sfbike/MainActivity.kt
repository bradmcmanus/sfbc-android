package org.sfbike

import android.content.DialogInterface
import android.location.Location
import android.os.Bundle
import android.os.Handler
import android.support.v4.app.FragmentActivity
import android.view.KeyEvent
import com.github.salomonbrys.kotson.fromJson
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.api.GoogleApiClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.*
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import com.google.gson.Gson
import com.google.maps.android.geojson.GeoJsonLayer
import org.sfbike.data.SfpdStation
import org.sfbike.util.GooglePlayUtil
import java.io.BufferedReader
import java.io.InputStreamReader

class MainActivity : FragmentActivity(), OnMapReadyCallback {

    companion object {
        private val DEFAULT_ZOOM = 13.88.toFloat()
    }

    val mHandler = Handler()

    private var mMap: GoogleMap? = null


    // Location
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_maps)
        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        val mapFragment = supportFragmentManager.findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)

        mHandler.postDelayed({ if (mMap != null) { mMap!!.setLocationSource(locationSource) } }, 10000)
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

    // Maps

    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap

        val point = LatLng(37.777998, -122.409411)
        zoom(point, addMarker = true, zoom = DEFAULT_ZOOM)

        loadSfpdStationInfo()
        loadSfpdShapes()
    }

    private fun zoom(latLng: LatLng, addMarker: Boolean, zoom: Float) {

        // Animate camera
        if (addMarker) {
            mMap?.clear()
            mMap?.addMarker(MarkerOptions().position(latLng))
        }
        mMap?.animateCamera(CameraUpdateFactory.newCameraPosition(
                CameraPosition.Builder()
                        .target(latLng)
                        .zoom(zoom)
                        .build()))
    }

    // Station data

    fun loadSfpdShapes() {
        if (mMap == null) {
            return
        }



        val sfpdLayer = GeoJsonLayer(mMap, R.raw.sfpd_shapes, this)
        for (feature in sfpdLayer.features) {
           log("${feature.getProperty("objectid")}")

        }
        sfpdLayer.addLayerToMap()
    }

    fun loadSfpdStationInfo() {
        val gson = Gson()
        val raw = resources.openRawResource(R.raw.sfpd_stations)
        val reader = BufferedReader(InputStreamReader(raw))
        val stations = gson.fromJson<List<SfpdStation>>(reader)
        for (station in stations) {
            log("${station.id} + ${station.email}")
        }
    }

    // Google Play Services

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
}
