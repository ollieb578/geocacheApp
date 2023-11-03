package com.geopathapp

import android.content.ContentValues.TAG
import android.content.Context
import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.ktx.Firebase
import java.util.*

class SignupActivity : AppCompatActivity() {
    private lateinit var auth: FirebaseAuth
    private lateinit var dbref: DatabaseReference

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_signup2)
        setSupportActionBar(findViewById(R.id.signupToolbar))

        //get auth token
        auth = Firebase.auth

        val email = findViewById<EditText>(R.id.signupEmail)
        val password = findViewById<EditText>(R.id.signupPassword)

        // huge mess to create all user related entries on firebase
        // includes auth stuff, realtime user score data, and inventory.
        //
        // there's a marginally worse version of this in the main class.
        val signupSubmit = findViewById<Button>(R.id.signupSubmit)
        signupSubmit.setOnClickListener { view ->
            auth.createUserWithEmailAndPassword(email.text.toString(), password.text.toString())
                .addOnCompleteListener(this)
                { task ->
                    if (task.isSuccessful) {
                        dbref = FirebaseDatabase.getInstance("https://geopathapp-default-rtdb.europe-west1.firebasedatabase.app/").reference

                        val uid = auth.currentUser?.uid
                        val score = 0
                        //objects created for DB entry
                        val userdata = UserData(email.text.toString(), score)
                        val lootUUID = UUID.randomUUID().toString()
                        val initLoot = GameItem(lootUUID, 1, "Treasure Chest", 3)

                        if (uid != null) {
                            //horrible nesting to avoid weird issues i was having
                            //adds userdata to table
                            dbref.child("userdata").child(uid).setValue(userdata)
                                .addOnCompleteListener(this)
                                { task2 ->
                                    if (task2.isSuccessful) {
                                        //adds first inventory items
                                        dbref.child("inventory").child(uid).child(lootUUID).setValue(initLoot)
                                            .addOnCompleteListener(this)
                                            { task3 ->
                                                if (task3.isSuccessful) {
                                                    //pass user on if successful
                                                    val intent = Intent(this, MapsActivity::class.java)

                                                    startActivity(intent)
                                                    finish()
                                                } else {
                                                    closeKeyboard()
                                                    Log.w(TAG, "createInventory:failure", task.exception)
                                                    Snackbar.make(view, "Please contact administrator", Snackbar.LENGTH_LONG).show()
                                                }
                                            }

                                    } else {
                                        closeKeyboard()
                                        Log.w(TAG, "createUserdata:failure", task.exception)
                                        Snackbar.make(view, "Please contact administrator", Snackbar.LENGTH_LONG).show()
                                    }
                                }
                        }

                    } else {
                        closeKeyboard()
                        Log.w(TAG, "signInWithCustomToken:failure", task.exception)
                        Snackbar.make(view, "Failed to create user", Snackbar.LENGTH_LONG).show()
                    }

                }
        }

        val login = findViewById<Button>(R.id.signupLogin)
        login.setOnClickListener {
            val intent = Intent(this, LoginActivity::class.java)
            startActivity(intent)
            finish()
        }
    }

    public override fun onStart() {
        super.onStart()

        // check if user is signed in
        val currentUser = auth.currentUser

        // if they are, redirect to main activity
        if (currentUser != null) {
            val intent = Intent(this, MapsActivity::class.java)
            startActivity(intent)
            finish()
        }
    }

    private fun closeKeyboard() {
        val view = this.currentFocus
        if (view != null){
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.hideSoftInputFromWindow(view.windowToken, 0)
        }
    }

}