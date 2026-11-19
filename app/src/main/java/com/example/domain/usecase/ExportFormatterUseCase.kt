package com.example.domain.usecase

import com.example.model.ConfigItem
import com.example.model.HitDbItem
import com.example.model.ProxyItem
import com.example.model.ProxyStatus
import com.example.model.ResultTab
import com.example.network.ConfigExportFormat
import com.example.network.ConfigFileCodec

class ExportFormatterUseCase {

    fun formatConfig(config: ConfigItem, format: ConfigExportFormat): String {
        return when (format) {
            ConfigExportFormat.LOLI -> ConfigFileCodec.serializeToLoli(config)
            ConfigExportFormat.JSON -> ConfigFileCodec.serializeToJson(config)
        }
    }

    fun formatConfigListJson(configs: List<ConfigItem>): String {
        if (configs.isEmpty()) return ""
        return ConfigFileCodec.serializeListToJson(configs)
    }

    fun formatAliveProxies(proxies: List<ProxyItem>): String {
        if (proxies.isEmpty()) return ""
        val alive = proxies.filter { it.status == ProxyStatus.AVAILABLE }
        val target = alive.ifEmpty { proxies }
        return target.joinToString("\n") { "${it.host}:${it.port}" }
    }

    fun filterHits(
        hits: List<HitDbItem>,
        typeFilter: ResultTab?,
        searchQuery: String
    ): List<HitDbItem> {
        val query = searchQuery.trim()
        return hits.filter { item ->
            val matchesType = typeFilter == null || item.type == typeFilter
            val matchesQuery = query.isEmpty() ||
                item.data.contains(query, ignoreCase = true) ||
                item.configName.contains(query, ignoreCase = true) ||
                item.wordlistName.contains(query, ignoreCase = true) ||
                item.capturedData.contains(query, ignoreCase = true)
            matchesType && matchesQuery
        }
    }

    fun formatFilteredHits(
        hits: List<HitDbItem>,
        typeFilter: ResultTab?,
        searchQuery: String
    ): String {
        val filtered = filterHits(hits, typeFilter, searchQuery)
        if (filtered.isEmpty()) return ""
        return filtered.joinToString("\n") { "${it.data} | ${it.capturedData} | [${it.configName}]" }
    }
}
