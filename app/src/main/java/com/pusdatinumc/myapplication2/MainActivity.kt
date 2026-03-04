package com.pusdatinumc.myapplication2

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color
import android.location.Address
import android.location.Geocoder
import android.location.Location
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.gms.maps.model.Polyline
import com.google.android.gms.maps.model.PolylineOptions
import java.util.Locale

class MainActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var mMap: GoogleMap? = null
    private var currentPolyline: Polyline? = null

    // Koordinat Target: Kampus 2 UMC Watubelah
    private val targetLat = -7.5567196
    private val targetLng = 110.7684391

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // Initialize Map
        val mapFragment = supportFragmentManager
            .findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        val tvLocationInfo = findViewById<TextView>(R.id.tvLocationInfo)
        val tvDistanceInfo = findViewById<TextView>(R.id.tvDistanceInfo)
        val btnCheckLocation = findViewById<Button>(R.id.btnCheckLocation)
        val btnSubmit = findViewById<Button>(R.id.button_submit)

        val requestPermissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { permissions ->
            if (permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
            ) {
                enableMyLocation()
            } else {
                Toast.makeText(this, "Izin lokasi ditolak", Toast.LENGTH_SHORT).show()
            }
        }

        btnCheckLocation.setOnClickListener {
            checkPermissionsAndRun(requestPermissionLauncher) {
                getCurrentLocationName(tvLocationInfo)
            }
        }

        btnSubmit.setOnClickListener {
            checkPermissionsAndRun(requestPermissionLauncher) {
                calculateDistanceToTarget(tvDistanceInfo)
            }
        }
    }

    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap
        
        // Aktifkan kontrol zoom UI (tombol + dan -)
        mMap?.uiSettings?.isZoomControlsEnabled = true
        // Pastikan gerakan zoom (pinch to zoom) aktif
        mMap?.uiSettings?.isZoomGesturesEnabled = true
        // Aktifkan kompas
        mMap?.uiSettings?.isCompassEnabled = true

        val targetLocation = LatLng(targetLat, targetLng)
        mMap?.addMarker(MarkerOptions().position(targetLocation).title("Target: Kampus 2 UMC"))
        mMap?.moveCamera(CameraUpdateFactory.newLatLngZoom(targetLocation, 15f))
        enableMyLocation()
    }

    private fun enableMyLocation() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            mMap?.isMyLocationEnabled = true
        }
    }

    private fun checkPermissionsAndRun(launcher: androidx.activity.result.ActivityResultLauncher<Array<String>>, action: () -> Unit) {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            launcher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
        } else {
            action()
        }
    }

    private fun getCurrentLocationName(textView: TextView) {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) return

        fusedLocationClient.lastLocation.addOnSuccessListener { location: Location? ->
            if (location != null) {
                val userLatLng = LatLng(location.latitude, location.longitude)
                mMap?.animateCamera(CameraUpdateFactory.newLatLngZoom(userLatLng, 15f))
                
                val geocoder = Geocoder(this, Locale.getDefault())
                try {
                    val addresses: List<Address>? = geocoder.getFromLocation(location.latitude, location.longitude, 1)
                    val addressName = addresses?.firstOrNull()?.getAddressLine(0) ?: "Nama lokasi tidak ditemukan"
                    textView.text = "Lokasi: $addressName\nKoordinat: ${location.latitude}, ${location.longitude}"
                } catch (e: Exception) {
                    textView.text = "Error mendapatkan nama lokasi\nKoordinat: ${location.latitude}, ${location.longitude}"
                }
            } else {
                Toast.makeText(this, "Gagal mendapatkan lokasi. Pastikan GPS aktif.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun calculateDistanceToTarget(textView: TextView) {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) return

        fusedLocationClient.lastLocation.addOnSuccessListener { location: Location? ->
            if (location != null) {
                val userLatLng = LatLng(location.latitude, location.longitude)
                val targetLatLng = LatLng(targetLat, targetLng)
                
                val results = FloatArray(1)
                Location.distanceBetween(location.latitude, location.longitude, targetLat, targetLng, results)
                val distance = results[0].toInt()
                textView.text = "Anda berada $distance meter dari lokasi"
                
                // Gambar garis penanda (Polyline)
                currentPolyline?.remove()
                currentPolyline = mMap?.addPolyline(
                    PolylineOptions()
                        .add(userLatLng, targetLatLng)
                        .width(10f)
                        .color(Color.BLUE)
                        .geodesic(true)
                )
                
                // Zoom out sedikit untuk menunjukkan kedua posisi
                mMap?.animateCamera(CameraUpdateFactory.newLatLngZoom(targetLatLng, 13f))
            } else {
                Toast.makeText(this, "Gagal mendapatkan lokasi.", Toast.LENGTH_SHORT).show()
            }
        }
    }
}