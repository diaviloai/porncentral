package com.yourname.aggregator.data.providers

class ProviderRegistry(
    providers: List<SiteProvider>
) {
    private val providerMap = providers.associateBy { it.id }

    fun getAll(): List<SiteProvider> = providerMap.values.toList()

    fun getById(id: String): SiteProvider? = providerMap[id]

    fun getActive(enabledIds: Set<String>): List<SiteProvider> =
        if (enabledIds.isEmpty()) getAll()
        else providerMap.values.filter { it.id in enabledIds }
}
