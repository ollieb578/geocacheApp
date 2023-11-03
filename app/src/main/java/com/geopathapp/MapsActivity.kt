package com.geopathapp

import android.Manifest
import android.content.ContentValues.TAG
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Looper
import android.util.Log
import android.view.View.GONE
import android.view.View.VISIBLE
import android.widget.RelativeLayout
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AlertDialog
import androidx.core.app.ActivityCompat
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.geopathapp.databinding.ActivityMapsBinding
import com.google.android.gms.location.*
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.database.*
import com.google.firebase.ktx.Firebase
import java.util.*
import java.util.concurrent.TimeUnit
import com.google.android.gms.maps.model.LatLngBounds

import com.google.maps.android.SphericalUtil
import kotlin.math.sqrt

// this is a really big, bloated class.
// it will likely massively hurt performance of my app.
// this is because i am still fairly inexperienced with kotlin, and had to resort to a lot
// of awful, hacky solutions to get things to work. i hope it doesn't break your phone.
// UPDATE - in practise, it seems to function OK - tested on an LG G6 H870

//TODO: Swap all DB operations to use transaction methods

class MapsActivity : AppCompatActivity(), OnMapReadyCallback {
    //firebase
    private lateinit var auth: FirebaseAuth
    private lateinit var dbref: DatabaseReference
    private lateinit var db: FirebaseDatabase
    private lateinit var routeDataRef: DatabaseReference

    //location
    private lateinit var fusedLocationProviderClient: FusedLocationProviderClient
    private lateinit var locationRequest: LocationRequest
    private lateinit var locationCallback: LocationCallback
    private var currentLocation: Location? = null

    //maps
    private lateinit var mMap: GoogleMap
    private lateinit var binding: ActivityMapsBinding

    //game
    private lateinit var routeList: MutableList<PathNode>
    private lateinit var lastNodeLatLng: LatLngBounds
    private lateinit var currentNodeLatLng: LatLngBounds
    // index of current node
    private var currentNode = 0
    private var followingRoute = false
    private var currentRouteID = "null"
    // max index of nodes (2 if there are 3 nodes on the route)
    private var currentRouteNumNodes = 0

    @RequiresApi(Build.VERSION_CODES.Q)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMapsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(findViewById(R.id.MapToolbar))
        supportActionBar?.hide()

        //get auth token
        auth = Firebase.auth
        val uid = auth.currentUser?.uid

        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        val mapFragment = supportFragmentManager
            .findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)

        //requesting location updates
        fusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(this)

        locationRequest = LocationRequest.create().apply {
            // interval for active location updates
            interval = TimeUnit.SECONDS.toMillis(4)

            // fastest rate for active location updates
            fastestInterval = TimeUnit.SECONDS.toMillis(1)

            // maximum time when batched location updates are delivered
            maxWaitTime = TimeUnit.MINUTES.toMillis(2)

            priority = LocationRequest.PRIORITY_HIGH_ACCURACY
        }

        // i'm pretty sure i wasn't meant to implement this in the way I did
        // but Google deprecated all the reasonable ways of doing it, and
        // everything I looked at was really badly documented.
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                super.onLocationResult(locationResult)
                currentLocation = locationResult.lastLocation

                if (followingRoute){

                    // DB
                    db = FirebaseDatabase.getInstance("https://geopathapp-default-rtdb.europe-west1.firebasedatabase.app/")
                    routeDataRef = db.getReference("route/".plus(currentRouteID))

                    // initial user location (updated later)
                    val currentLatLng = LatLng(currentLocation!!.latitude, currentLocation!!.longitude)

                    // if the user has visited all the nodes on the route,
                    // this will fire, and the event will end.

                    if (currentNodeLatLng.contains(currentLatLng)){
                        mMap.clear()
                        currentNode += 1

                        if (currentNode > currentRouteNumNodes){
                            decreaseRouteUse(currentRouteID)
                            onRouteCompleted()
                        } else {

                            // adds next route marker
                            routeDataRef.addValueEventListener(object : ValueEventListener {
                                override fun onDataChange(snapshot: DataSnapshot) {
                                    val nodeLat = snapshot.child("nodes").child(currentNode.toString()).child("latLng/latitude").value
                                    val nodeLong = snapshot.child("nodes").child(currentNode.toString()).child("latLng/longitude").value
                                    val nextNode = LatLng(nodeLat as Double, nodeLong as Double)

                                    mMap.addMarker(MarkerOptions().position(nextNode)
                                        .title((currentNode+1).toString()))

                                    currentNodeLatLng = toBounds(nextNode)
                                }

                                override fun onCancelled(databaseError: DatabaseError) {
                                    TODO("Not yet implemented")
                                }
                            })

                        }
                    }
                }
            }
        }

        //setting up menu button
        val menuButton = findViewById<FloatingActionButton>(R.id.mapsMenuButton)
        menuButton.setOnClickListener {
            val intent = Intent(this, MenuActivity::class.java)
            startActivity(intent)
        }

        //enters create route mode
        val createRouteButton = findViewById<FloatingActionButton>(R.id.createRouteButton)
        createRouteButton.setOnClickListener {
            createRoute()
        }

        //allows exit from route follow
        val exitFollowButton = findViewById<FloatingActionButton>(R.id.exitFollowRoute)
        exitFollowButton.setOnClickListener {
            followRouteExit()
        }

        //calls function to add node with location to route structure
        val createRouteNodeButton = findViewById<FloatingActionButton>(R.id.addRouteNodeButton)
        createRouteNodeButton.setOnClickListener { view ->
            val currentLatLng: LatLng = currentLocation?.let { LatLng(it.latitude, it.longitude) }!!
            val newNode = PathNode(currentLatLng)

            if (routeList.size == 0) {
                routeList.add(newNode)
                Snackbar.make(view, "Node added!", Snackbar.LENGTH_LONG).show()
                lastNodeLatLng = toBounds(currentLatLng)
                mMap.addMarker(MarkerOptions().position(currentLatLng)
                    .title("Test Node!"))
            } else {
                if (!lastNodeLatLng.contains(currentLatLng)){
                    routeList.add(newNode)
                    Snackbar.make(view, "Node added!", Snackbar.LENGTH_LONG).show()
                    lastNodeLatLng = toBounds(currentLatLng)
                    mMap.addMarker(MarkerOptions().position(currentLatLng)
                        .title(("Test Node!").toString()))
                } else {
                    Snackbar.make(view, "Last node was placed here!", Snackbar.LENGTH_LONG).show()
                }
            }

        }

        //confirms route created is complete
        val createRouteFinishButton = findViewById<FloatingActionButton>(R.id.submitRouteButton)
        createRouteFinishButton.setOnClickListener {
            createRouteFinish()
        }

        //allows exit from route creation interface
        val exitCreateRouteButton = findViewById<FloatingActionButton>(R.id.exitCreateRoute)
        exitCreateRouteButton.setOnClickListener {
            createRouteExit()
        }

        //custom MyLocationButton
        val currentLocationButton = findViewById<FloatingActionButton>(R.id.showCurrentLocation)

        currentLocationButton.setOnClickListener {
            if (checkPermission()) {
                val currentLatLng = currentLocation?.let { LatLng(it.latitude, it.longitude) }
                currentLatLng?.let { CameraUpdateFactory.newLatLngZoom(it, 16F) }?.let {
                    mMap.animateCamera(
                        it
                    )
                }
            }
        }

        createRouteButton.setImageResource(R.drawable.numten)
        dbref = FirebaseDatabase.getInstance("https://geopathapp-default-rtdb.europe-west1.firebasedatabase.app/").getReference("inventory/".plus(uid))

        // takes the user's UID, and checks how many treasure chests they have so that it can be
        // displayed on the map screen.
        dbref.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if(snapshot.exists()){
                    for(itemSnapshot in snapshot.children) {
                        val item = itemSnapshot.getValue(GameItem::class.java)
                        if (item != null) {
                            if (item.gameItemId == 1) {
                                when (item.gameItemQuant) {
                                    0 -> createRouteButton.visibility = GONE
                                    1 -> createRouteButton.setImageResource(R.drawable.num1)
                                    2 -> createRouteButton.setImageResource(R.drawable.num2)
                                    3 -> createRouteButton.setImageResource(R.drawable.num3)
                                    4 -> createRouteButton.setImageResource(R.drawable.num4)
                                    5 -> createRouteButton.setImageResource(R.drawable.num5)
                                    6 -> createRouteButton.setImageResource(R.drawable.num6)
                                    7 -> createRouteButton.setImageResource(R.drawable.num7)
                                    8 -> createRouteButton.setImageResource(R.drawable.num8)
                                    9 -> createRouteButton.setImageResource(R.drawable.num9)
                                }
                            }
                        }
                    }
                }
            }

            override fun onCancelled(error: DatabaseError) {
                TODO("Not yet implemented")
            }
        })
    }

    // onStart callback. gets user from auth, redirects user if not logged in.
    public override fun onStart() {
        super.onStart()

        val currentUser = auth.currentUser

        // throws user back to login if not logged in
        if (currentUser == null) {
            val intent = Intent(this, LoginActivity::class.java)
            startActivity(intent)
            finish()
        }
    }

    // onPause callback. removes location callback.
    public override fun onPause() {
        super.onPause()

        val removeTask = fusedLocationProviderClient.removeLocationUpdates(locationCallback)
        removeTask.addOnCompleteListener { task ->
            if (task.isSuccessful) {
                Log.d(TAG, "Location Callback removed.")
            } else {
                Log.d(TAG, "Failed to remove Location Callback.")
            }
        }

    }

    /**
     * Manipulates the map once available.
     * This callback is triggered when the map is ready to be used.
     * This is where we can add markers or lines, add listeners or move the camera.
     * If Google Play services is not installed on the device, the user will be prompted to install
     * it inside the SupportMapFragment. This method will only be triggered once the user has
     * installed Google Play services and returned to the app.
     */
    @RequiresApi(Build.VERSION_CODES.Q)
    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap

        // subscribe to location updates
        if (checkPermission()) {
            fusedLocationProviderClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper())
        }


        // Add a marker in Sydney and move the camera
        val sydney = LatLng(-34.0, 151.0)
        mMap.addMarker(MarkerOptions().position(sydney)
                                        .title("Marker in Sydney")
                                        .snippet("this is a test"))

        populateMap()

        mMap.setOnMarkerClickListener {
            mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(it.position, 16F))
            //it.showInfoWindow()
            // will get the route's UID from the marker tag
            val routeID = it.tag

            startRouteFollow(routeID as String)
            true
        }
    }

    // populates the overworld map with markers for routes
    private fun populateMap(){
        dbref = FirebaseDatabase.getInstance("https://geopathapp-default-rtdb.europe-west1.firebasedatabase.app/").getReference("route")
        dbref.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (snapshot.exists()) {
                    for (itemSnapshot in snapshot.children) {
                        val nodeLat = itemSnapshot.child("nodes/0/latLng/latitude").value
                        val nodeLong = itemSnapshot.child("nodes/0/latLng/longitude").value
                        val routeUID = itemSnapshot.child("routeUID").value

                        val nodeLoc = LatLng(nodeLat as Double, nodeLong as Double)

                        val newMarker = nodeLoc.let {
                            MarkerOptions().position(
                                it
                            )
                        }.let { mMap.addMarker(it) }

                        if (newMarker != null) {
                            newMarker.tag = routeUID
                        }
                    }
                }
            }

            override fun onCancelled(error: DatabaseError) {
                TODO("Not yet implemented")
            }
        })
    }

    //opens route follow interface and event
    // this is much messier than I intended, given more time this could've been cleaned up
    private fun startRouteFollow(routeID: String){
        val dialogBuilder = AlertDialog.Builder(this)

        dialogBuilder.setMessage("")
            .setCancelable(true)
            .setPositiveButton("Yes") { _, _ ->
                // DB
                db = FirebaseDatabase.getInstance("https://geopathapp-default-rtdb.europe-west1.firebasedatabase.app/")
                routeDataRef = db.getReference("route/".plus(routeID))

                //setting up UI
                supportActionBar?.show()
                supportActionBar?.title = "Following Route..."

                findViewById<FloatingActionButton>(R.id.exitFollowRoute).visibility = VISIBLE
                findViewById<FloatingActionButton>(R.id.mapsMenuButton).visibility = GONE
                findViewById<FloatingActionButton>(R.id.createRouteButton).visibility = GONE

                // map is cleared so only markers for this route are displayed
                mMap.clear()

                // initial data fetched, need value for numNodes
                routeDataRef.addValueEventListener(object : ValueEventListener {
                    override fun onDataChange(snapshot: DataSnapshot) {
                        val numNodes = snapshot.child("numNodes").value as Long
                        currentRouteNumNodes = numNodes.toInt()
                    }
                    override fun onCancelled(databaseError: DatabaseError) {
                        TODO("Not yet implemented")
                    }
                })

                // adds first route marker
                routeDataRef.addValueEventListener(object : ValueEventListener {
                    override fun onDataChange(snapshot: DataSnapshot) {
                        val nodeLat = snapshot.child("nodes/0/latLng/latitude").value
                        val nodeLong = snapshot.child("nodes/0/latLng/longitude").value
                        val nextNode = LatLng(nodeLat as Double, nodeLong as Double)
                        val nextBounds = toBounds(nextNode)

                        currentNodeLatLng = nextBounds

                        mMap.addMarker(MarkerOptions().position(nextNode)
                            .title((currentNode+1).toString()))
                    }

                    override fun onCancelled(databaseError: DatabaseError) {
                        TODO("Not yet implemented")
                    }
                })

                mMap.setOnMarkerClickListener { true }

                currentRouteID = routeID
                followingRoute = true

            }

        // makes dialog appear
        val confirmDialog = dialogBuilder.create()
        confirmDialog.setTitle("Follow this route?")
        confirmDialog.show()
    }

    //reward user, return to overworld
    fun onRouteCompleted(){
        userReward()
        followRouteExit()
    }

    // exit follow route interaction
    // resets UI
    private fun followRouteExit(){
        currentNode = 0
        followingRoute = false

        // reset UI
        supportActionBar?.hide()

        findViewById<FloatingActionButton>(R.id.exitFollowRoute).visibility = GONE
        findViewById<FloatingActionButton>(R.id.mapsMenuButton).visibility = VISIBLE
        findViewById<FloatingActionButton>(R.id.createRouteButton).visibility = VISIBLE

        mMap.clear()
        populateMap()

        mMap.setOnMarkerClickListener {
            mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(it.position, 16F))
            //it.showInfoWindow()
            // will get the route's UID from the marker tag
            val routeID = it.tag

            startRouteFollow(routeID as String)
            true
        }
    }

    //opens route creation interface, allows user to place nodes
    private fun createRoute(){
        supportActionBar?.show()
        supportActionBar?.title = "Create Route"

        findViewById<FloatingActionButton>(R.id.mapsMenuButton).visibility = GONE
        findViewById<FloatingActionButton>(R.id.createRouteButton).visibility = GONE
        findViewById<FloatingActionButton>(R.id.exitCreateRoute).visibility = VISIBLE
        findViewById<FloatingActionButton>(R.id.addRouteNodeButton).visibility = VISIBLE
        findViewById<FloatingActionButton>(R.id.submitRouteButton).visibility = VISIBLE
        mMap.clear()

        // empty current node list
        routeList = mutableListOf()

        // change on click interaction
        mMap.setOnMarkerClickListener { true }
    }

    //return user to overworld screen from route creation
    private fun createRouteExit(){
        supportActionBar?.hide()
        findViewById<FloatingActionButton>(R.id.exitCreateRoute).visibility = GONE
        findViewById<FloatingActionButton>(R.id.addRouteNodeButton).visibility = GONE
        findViewById<FloatingActionButton>(R.id.submitRouteButton).visibility = GONE

        findViewById<FloatingActionButton>(R.id.mapsMenuButton).visibility = VISIBLE
        findViewById<FloatingActionButton>(R.id.createRouteButton).visibility = VISIBLE

        mMap.setOnMarkerClickListener {
            mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(it.position, 16F))
            //it.showInfoWindow()
            // will get the route's UID from the marker tag
            val routeID = it.tag

            startRouteFollow(routeID as String)
            true
        }

        mMap.clear()
        populateMap()
    }

    //send created route to server, reward user, and return user to overworld
    private fun createRouteFinish(){
        // DB
        db = FirebaseDatabase.getInstance("https://geopathapp-default-rtdb.europe-west1.firebasedatabase.app/")
        routeDataRef = db.getReference("route")

        // for creating confirmation dialog
        val view = findViewById<RelativeLayout>(R.id.mapLayout)
        val dialogBuilder = AlertDialog.Builder(this)
        val lootActions = LootActions()
        val uid = auth.uid

        // dialog actions
        dialogBuilder.setMessage("Confirm Route?")
                        .setCancelable(true)
                        .setPositiveButton("Yes") { _, _ ->
                            //this sends the route data to the server
                            routeDataRef.runTransaction(object: Transaction.Handler {
                                override fun doTransaction(currentData: MutableData): Transaction.Result {
                                    val routeUUID = UUID.randomUUID().toString()
                                    val newRoute = PathRoute(routeList, routeUUID, (routeList.size -1))

                                    currentData.child(routeUUID).value = newRoute

                                    return (Transaction.success(currentData))
                                }

                                override fun onComplete(
                                    error: DatabaseError?,
                                    committed: Boolean,
                                    currentData: DataSnapshot?
                                ) {

                                }
                            })

                            //these methods perform all the other route finish actions
                            createRouteExit()
                            userReward()
                            lootActions.removeChest(uid)
                            lootActions.updateScore(uid, 30)
                        }

        // makes dialog appear
        val confirmDialog = dialogBuilder.create()
        confirmDialog.setTitle("ARE YOU SURE?")

        // user cannot submit route with fewer than 3 nodes
        if (routeList.size >= 3) {
            confirmDialog.show()
        } else {
            Snackbar.make(view, "Route too short. Add more nodes!", Snackbar.LENGTH_LONG).show()
        }
    }

    // reward event
    // database actions are handled in LootActions class
    private fun userReward(){
        // for all loot operations
        val uid = auth.uid
        val lootActions = LootActions()
        val lootId = lootActions.generateLoot()

        // for dialog boxes
        val dialogBuilder = AlertDialog.Builder(this)
        val dialogBuilder2 = AlertDialog.Builder(this)

        // tells the user what loot they received
        lootActions.generateReward(uid, lootId)
        dialogBuilder.setMessage("You received: " + lootActions.translateID(lootId))
            .setCancelable(false)
            .setPositiveButton("Nice!") { dialog, _ ->
                dialog.cancel()
                lootActions.updateScore(uid, 10)

                // tells the user if they got a chest
                if (lootActions.generateChest(uid)) {
                    dialogBuilder2.setMessage("You also received a treasure chest!")
                        .setCancelable(false)
                        .setPositiveButton("Nice!") { dialog2, _ ->
                            dialog2.cancel()
                        }
                    val rewardDialog2 = dialogBuilder2.create()
                    rewardDialog2.setTitle("YOUR REWARDS")
                    rewardDialog2.show()
                }
            }

        // makes dialog appear
        val rewardDialog = dialogBuilder.create()
        rewardDialog.setTitle("YOUR REWARDS")
        rewardDialog.show()
    }

    // decreases the amount of uses a route has by 1 on completion.
    // will delete the whole route entry if uses == 0
    fun decreaseRouteUse(routeID: String){
        // DB
        db = FirebaseDatabase.getInstance("https://geopathapp-default-rtdb.europe-west1.firebasedatabase.app/")
        routeDataRef = db.getReference("route").child(routeID)

        routeDataRef.runTransaction(object: Transaction.Handler {
            override fun doTransaction(currentData: MutableData): Transaction.Result {
                val newUses = currentData.child("uses").toString().toInt() - 1

                if (newUses <= 0) {
                    currentData.value = null
                } else {
                    currentData.child("uses").value = newUses
                }
                return (Transaction.success(currentData))
            }

            override fun onComplete(
                error: DatabaseError?,
                committed: Boolean,
                currentData: DataSnapshot?
            ) {

            }
        })
    }

    // converts a latLng to a set of latLng bounds
    fun toBounds(center: LatLng?): LatLngBounds {
        val radiusInMeters = 10
        val distanceFromCenterToCorner = radiusInMeters * sqrt(2.0)
        val southwestCorner = SphericalUtil.computeOffset(center, distanceFromCenterToCorner, 225.0)
        val northeastCorner = SphericalUtil.computeOffset(center, distanceFromCenterToCorner, 45.0)
        return LatLngBounds(southwestCorner, northeastCorner)
    }

    //checks that user has location permissions
    @RequiresApi(Build.VERSION_CODES.Q)
    private fun checkPermission(): Boolean {
        return if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED){
            mMap.isMyLocationEnabled = true
            mMap.uiSettings.isMyLocationButtonEnabled = false

            true
        } else {
            val permissionid = 24
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_BACKGROUND_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION),
                permissionid )
            false
        }
    }
}