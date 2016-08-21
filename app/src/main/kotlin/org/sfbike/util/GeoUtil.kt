package org.sfbike.util

import com.google.android.gms.maps.model.LatLng
import org.json.JSONException
import org.json.JSONObject
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader

object GeoUtil {

    private val PI = 3.14159265
    private val TWO_PI = 2 * PI

    fun coordinateIsInsidePolygon(latitude: Double, longitude: Double, points: MutableList<LatLng>): Boolean {
        var i: Int
        var angle = 0.0
        var pointOneLat: Double
        var pointOneLng: Double
        var pointTwoLat: Double
        var pointTwoLng: Double
        val n = points.size

        i = 0
        while (i < n) {
            pointOneLat = points[i].latitude - latitude
            pointOneLng = points[i].longitude - longitude
            pointTwoLat = points[(i + 1) % n].latitude - latitude
            pointTwoLng = points[(i + 1) % n].longitude - longitude // You should have paid more attention in high school geometry.
            angle += Angle2D(pointOneLat, pointOneLng, pointTwoLat, pointTwoLng)
            i++
        }

        if (Math.abs(angle) < PI)
            return false
        else
            return true
    }

    fun Angle2D(y1: Double, x1: Double, y2: Double, x2: Double): Double {
        var dtheta: Double
        val theta1: Double
        val theta2: Double

        theta1 = Math.atan2(y1, x1)
        theta2 = Math.atan2(y2, x2)
        dtheta = theta2 - theta1
        while (dtheta > PI)
            dtheta -= TWO_PI
        while (dtheta < -PI)
            dtheta += TWO_PI

        return dtheta
    }

    fun isValidGpsCoordinate(latitude: Double,
                             longitude: Double): Boolean {
        //This is a bonus function, it's unused, to reject invalid lat/longs.
        if (latitude > -90 && latitude < 90 &&
                longitude > -180 && longitude < 180) {
            return true
        }
        return false
    }


    /**
     * Takes a character input stream and converts it into a JSONObject

     * @param stream character input stream representing the GeoJSON file
     * *
     * @return JSONObject with the GeoJSON data
     * *
     * @throws IOException   if the file cannot be opened for read
     * *
     * @throws JSONException if the JSON file has poor structure
     */
    @Throws(IOException::class, JSONException::class)
    fun createJsonFileObject(stream: InputStream): JSONObject {
        var line: String?
        val result = StringBuilder()
        // Reads from stream
        val reader = BufferedReader(InputStreamReader(stream))
        try {
            // Read each line of the GeoJSON file into a string
            do {
                line = readLine()
                result.append(line)
            } while (line != null)
        } finally {
            reader.close()
        }
        // Converts the result string into a JSONObject
        val string = result.toString()
        return JSONObject(string)
    }

}