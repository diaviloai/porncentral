package com.yourname.aggregator

import android.content.Context
import com.yourname.aggregator.core.network.NetworkClient
import com.yourname.aggregator.data.local.AppDatabase
import com.yourname.aggregator.data.providers.ProviderRegistry
import com.yourname.aggregator.data.providers.impl.BellesaProvider
import com.yourname.aggregator.data.providers.impl.HentaiTvProvider
import com.yourname.aggregator.data.providers.impl.PornhatProvider
import com.yourname.aggregator.data.providers.impl.PornhubProvider
import com.yourname.aggregator.data.providers.impl.TsuminoProvider
import com.yourname.aggregator.data.providers.impl.XHamsterProvider
import com.yourname.aggregator.data.providers.impl.XVideosProvider
import com.yourname.aggregator.data.providers.impl.XnxxProvider
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
                PornhubProvider(),
                XVideosProvider(),
                XHamsterProvider(),
                XnxxProvider(),
                BellesaProvider(),
                PornhatProvider(),
                HentaiTvProvider(),
                TsuminoProvider()
                // Add new *Provider() instances here anytime
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
