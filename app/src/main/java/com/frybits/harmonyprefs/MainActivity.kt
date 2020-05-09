package com.frybits.harmonyprefs

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import com.frybits.harmonyprefs.test.singleentry.apply.HarmonyPrefsApplyActivity
import com.frybits.harmonyprefs.test.singleentry.commit.HarmonyPrefsCommitActivity
import com.tencent.mmkv.MMKV

/**
 * Created by Pablo Baxter (Github: pablobaxter)
 */

class MainActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        MMKV.initialize(this)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        findViewById<Button>(R.id.singleCommitButton).setOnClickListener {
            startActivity(Intent(this, HarmonyPrefsCommitActivity::class.java))
        }

        findViewById<Button>(R.id.singleApplyButton).setOnClickListener {
            startActivity(Intent(this, HarmonyPrefsApplyActivity::class.java))
        }
    }
}
