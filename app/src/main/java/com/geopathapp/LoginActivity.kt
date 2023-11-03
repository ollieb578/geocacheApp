package com.geopathapp

import android.content.Context
import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase

class LoginActivity : AppCompatActivity() {
    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login2)
        setSupportActionBar(findViewById(R.id.loginToolbar))

        // firebase auth init
        auth = Firebase.auth;

        val email = findViewById<EditText>(R.id.loginEmail)
        val password = findViewById<EditText>(R.id.loginPassword)

        val loginSubmit = findViewById<Button>(R.id.loginSubmit)
        loginSubmit.setOnClickListener { view ->
            auth.signInWithEmailAndPassword(email.text.toString(), password.text.toString())
                .addOnCompleteListener(this)
                { task ->
                    if (task.isSuccessful) {
                        val intent = Intent(this, MapsActivity::class.java)
                        startActivity(intent)
                        finish()
                    } else {
                        closeKeyboard()
                        Snackbar.make(view, "Auth Failed", Snackbar.LENGTH_LONG).show()
                    }

                }
        }

        val signUp = findViewById<Button>(R.id.loginSignup)
        signUp.setOnClickListener { view ->
            val intent = Intent(this, SignupActivity::class.java)
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