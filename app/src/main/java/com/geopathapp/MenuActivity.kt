package com.geopathapp

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.database.*
import com.google.firebase.ktx.Firebase
import com.google.firebase.database.DataSnapshot

class MenuActivity : AppCompatActivity() {
    private lateinit var auth: FirebaseAuth
    private lateinit var dbref: DatabaseReference

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        //appbar setup, enable up nav
        setContentView(R.layout.activity_menu)
        setSupportActionBar(findViewById(R.id.MenuToolbar))
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        //get auth token
        auth = Firebase.auth
        //gets email value from Firebase auth
        val email = auth.currentUser?.email
        val uid = auth.currentUser?.uid

        //sets text at top of screen to user email
        val emailText = findViewById<TextView>(R.id.menuEmailText)
        emailText.text = "$email"

        //sets text at top of screen to user score
        val scoreText = findViewById<TextView>(R.id.menuScoreText)
        dbref = FirebaseDatabase.getInstance("https://geopathapp-default-rtdb.europe-west1.firebasedatabase.app/").getReference("userdata/".plus(uid))
        var userdata: UserData?

        dbref.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if(snapshot.exists()){
                    userdata = snapshot.getValue(UserData::class.java)
                    if (userdata != null) {
                        scoreText.text = "${userdata!!.score}"
                    }
                }
            }

            override fun onCancelled(error: DatabaseError) {
                TODO("Not yet implemented")
            }
        })

        //nav buttons
        val inventoryButton = findViewById<Button>(R.id.menuInventoryButton)
        inventoryButton.setOnClickListener {
            val intent = Intent(this, InventoryActivity::class.java)
            startActivity(intent)
        }

        val logOutButton = findViewById<Button>(R.id.menuLogOutButton)
        logOutButton.setOnClickListener {
            auth.signOut()
            val intent = Intent(this, LoginActivity::class.java)
            startActivity(intent)
            finish()
        }
    }

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

}