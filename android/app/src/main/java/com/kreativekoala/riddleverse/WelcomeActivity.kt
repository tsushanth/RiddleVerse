package com.kreativekoala.riddleverse

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth

/**
 * Kept as a redirect for existing entry points (sign-out, old intents). The Get Started -> mandatory sign-in screen
 * is gone: signed-in users go to Home, everyone else to MainActivity, which shows the first-run Play screen
 * (or Home for a returning guest). Sign-in is a link on that screen and in Profile.
 */
class WelcomeActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val target = if (FirebaseAuth.getInstance().currentUser != null) HomeActivity::class.java else MainActivity::class.java
        startActivity(Intent(this, target).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        })
        finish()
    }
}
