package com.geopathapp

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.database.*
import com.google.firebase.ktx.Firebase

// issue with this not displaying all items in the recyclerview? --- specifically emeralds

class InventoryActivity : AppCompatActivity() {
    private lateinit var auth: FirebaseAuth
    private lateinit var dbref: DatabaseReference
    private lateinit var userRecyclerView : RecyclerView
    private lateinit var itemArrayList: ArrayList<GameItem>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_inventory)

        //appbar setup, enable up nav
        setSupportActionBar(findViewById(R.id.inventoryToolbar))
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        //get auth token
        auth = Firebase.auth

        userRecyclerView = findViewById(R.id.inventoryRecyclerview)
        userRecyclerView.layoutManager = LinearLayoutManager(this)
        userRecyclerView.setHasFixedSize(true)

        itemArrayList = arrayListOf()
        getGameItemData()
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

    private fun getGameItemData() {
        val currentUser = auth.currentUser

        if (currentUser != null) {
            dbref = FirebaseDatabase.getInstance("https://geopathapp-default-rtdb.europe-west1.firebasedatabase.app/").getReference("inventory/".plus(currentUser.uid))
        }

        dbref.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if(snapshot.exists()) {
                    for (itemSnapshot in snapshot.children) {
                        val item = itemSnapshot.getValue(GameItem::class.java)
                        itemArrayList.add(item!!)
                    }
                }

                userRecyclerView.adapter = InventoryItemAdapter(itemArrayList)
            }

            override fun onCancelled(error: DatabaseError) {
                TODO("Not yet implemented")
            }
        })
    }

}