package com.example.data.backup

import android.app.Activity
import android.content.Context
import android.content.Intent
import com.example.GymApplication
import com.example.MainActivity

object AppRelaunch {

    fun afterRestore(context: Context) {
        (context.applicationContext as GymApplication).invalidateAfterRestore()
        val intent = Intent(context, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        }
        context.startActivity(intent)
        if (context is Activity) {
            context.finish()
        }
    }
}
