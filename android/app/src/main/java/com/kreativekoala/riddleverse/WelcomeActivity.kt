package com.kreativekoala.riddleverse

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth

class WelcomeActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val currentUser = FirebaseAuth.getInstance().currentUser
        if (currentUser != null) {
            Log.d("WelcomeActivity", "✅ User already logged in: ${currentUser.email}")
            // User is logged in, go directly to home
            val intent = Intent(this, HomeActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
            return
        }
        setContentView(R.layout.welcome_activity)

        val getStartedButton = findViewById<Button>(R.id.getStartedButton)
        val loginLink = findViewById<TextView>(R.id.loginLink)

        getStartedButton.setOnClickListener {
            startActivity(
                Intent(this, AuthActivity::class.java).apply {
                    putExtra("from_login", true)
                }
            )
        }

        loginLink.setOnClickListener {
            startActivity(
                Intent(this, AuthActivity::class.java).apply {
                    putExtra("from_login", true)
                }
            )
        }
    }
}