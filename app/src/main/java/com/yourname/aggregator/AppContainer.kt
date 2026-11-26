package com.yourname.aggregator

import android.content.Context
import com.yourname.aggregator.core.network.NetworkClient
import com.yourname.aggregator.data.local.AppDatabase
import com.yourname.aggregator.data.providers.ProviderRegistry
import com.yourname.aggregator.data.providers.impl.SiteAProvider
import com.yourname.aggregator.data.repository.VideoRepository
import okhttp3.OkHttpClient

class AppContainer(context: Context) {

    val okHttpClient: OkHttpClient = NetworkClient.okHttpClient

    val database: AppDatabase by lazy {
        AppDatabase.getInstance(context)
    }

    val providerRegistry: ProviderRegistry by lazy {
        ProviderRegistry(
            providers = listOf(
                SiteAProvider()
                // Register additional SiteProvider implementations here
            )
        )
    }

    val videoRepository: VideoRepository by lazy {
        VideoRepository(
            registry = providerRegistry,
            favoriteDao = database.favoriteDao(),
            preferenceDao = database.preferenceDao()
        )
    }
}
