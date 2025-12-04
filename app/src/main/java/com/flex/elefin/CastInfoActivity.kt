package com.flex.elefin

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.flex.elefin.jellyfin.JellyfinApiService
import com.flex.elefin.jellyfin.JellyfinConfig
import com.flex.elefin.jellyfin.JellyfinItem
import com.flex.elefin.jellyfin.Person
import com.flex.elefin.screens.CastInfoScreen

class CastInfoActivity : ComponentActivity() {
    companion object {
        private const val EXTRA_PERSON_ID = "person_id"
        private const val EXTRA_PERSON_NAME = "person_name"
        private const val EXTRA_PERSON_TYPE = "person_type"

        fun createIntent(
            context: Context,
            person: Person
        ): Intent {
            return Intent(context, CastInfoActivity::class.java).apply {
                putExtra(EXTRA_PERSON_ID, person.Id)
                putExtra(EXTRA_PERSON_NAME, person.Name)
                putExtra(EXTRA_PERSON_TYPE, person.Type) // Actor, Director, Writer, etc.
            }
        }

        fun createIntent(
            context: Context,
            personId: String,
            personName: String,
            personType: String? = null
        ): Intent {
            return Intent(context, CastInfoActivity::class.java).apply {
                putExtra(EXTRA_PERSON_ID, personId)
                putExtra(EXTRA_PERSON_NAME, personName)
                putExtra(EXTRA_PERSON_TYPE, personType)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val personId = intent.getStringExtra(EXTRA_PERSON_ID) ?: run {
            finish()
            return
        }
        val personName = intent.getStringExtra(EXTRA_PERSON_NAME) ?: ""
        val personType = intent.getStringExtra(EXTRA_PERSON_TYPE) // Actor, Director, etc.

        // Get Jellyfin configuration and API service
        val config = JellyfinConfig(this)
        val apiService = if (config.isConfigured()) {
            JellyfinApiService(
                baseUrl = config.serverUrl,
                accessToken = config.accessToken,
                userId = config.userId,
                config = config
            )
        } else {
            finish()
            return
        }

        setContent {
            JellyfinAppTheme {
                CastInfoScreen(
                    personId = personId,
                    personName = personName,
                    personType = personType, // Pass the type (Actor, Director, etc.)
                    apiService = apiService,
                    onNavigateToItem = { item ->
                        // Navigate to movie or series details
                        when (item.Type) {
                            "Movie" -> {
                                val intent = MovieDetailsActivity.createIntent(this, item)
                                startActivity(intent)
                            }
                            "Series" -> {
                                val intent = SeriesDetailsActivity.createIntent(this, item)
                                startActivity(intent)
                            }
                        }
                    },
                    onBack = {
                        finish()
                    }
                )
            }
        }
    }
}

