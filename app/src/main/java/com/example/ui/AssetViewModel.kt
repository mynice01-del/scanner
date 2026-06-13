package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AssetDatabase
import com.example.data.AssetEntity
import com.example.data.AssetRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class AssetViewModel(application: Application) : AndroidViewModel(application) {
    private val repository: AssetRepository

    init {
        val database = AssetDatabase.getDatabase(application)
        repository = AssetRepository(database.assetDao())
    }

    val allAssets: StateFlow<List<AssetEntity>> = repository.allAssets
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()

    private val _selectedCategoryFilter = MutableStateFlow("전체")
    val selectedCategoryFilter = _selectedCategoryFilter.asStateFlow()

    enum class SortOrder(val label: String) {
        TIME_DESC("등록순 ▲"),
        TIME_ASC("등록순 ▼"),
        TAG_ASC("일련번호 ▲"),
        TAG_DESC("일련번호 ▼"),
        CATEGORY_ASC("자산분류 ▲"),
        CATEGORY_DESC("자산분류 ▼")
    }

    private val _selectedSortOrder = MutableStateFlow(SortOrder.TIME_DESC)
    val selectedSortOrder = _selectedSortOrder.asStateFlow()

    fun setSortOrder(order: SortOrder) {
        _selectedSortOrder.value = order
    }

    val filteredAssets: StateFlow<List<AssetEntity>> = combine(
        allAssets,
        _searchQuery,
        _selectedCategoryFilter,
        _selectedSortOrder
    ) { assets, query, category, sortOrder ->
        val filtered = assets.filter { asset ->
            val matchesQuery = asset.tag.contains(query, ignoreCase = true) ||
                    asset.name.contains(query, ignoreCase = true) ||
                    asset.memo.contains(query, ignoreCase = true)
            val matchesCategory = category == "전체" || asset.category == category
            matchesQuery && matchesCategory
        }

        when (sortOrder) {
            SortOrder.TIME_DESC -> filtered.sortedByDescending { it.timestamp }
            SortOrder.TIME_ASC -> filtered.sortedBy { it.timestamp }
            SortOrder.TAG_ASC -> filtered.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.tag })
            SortOrder.TAG_DESC -> filtered.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.tag }).reversed()
            SortOrder.CATEGORY_ASC -> filtered.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.category })
            SortOrder.CATEGORY_DESC -> filtered.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.category }).reversed()
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    private val _lastScannedTag = MutableStateFlow<String?>(null)
    val lastScannedTag = _lastScannedTag.asStateFlow()

    private val _isScanningActive = MutableStateFlow(true)
    val isScanningActive = _isScanningActive.asStateFlow()

    private val _editingAsset = MutableStateFlow<AssetEntity?>(null)
    val editingAsset = _editingAsset.asStateFlow()

    private val recentlyScanned = mutableSetOf<String>()

    fun getCategoryByTag(tag: String): String {
        if (tag.length < 2) return "미정"
        return when (tag.substring(0, 2).uppercase()) {
            "CM" -> "미니PC"
            "CZ" -> "단말기"
            "OM" -> "모니터"
            "OD" -> "데스크탑"
            "OP" -> "프린터"
            "ON" -> "노트북"
            "OE" -> "OA기타"
            else -> "미정"
        }
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setCategoryFilter(category: String) {
        _selectedCategoryFilter.value = category
    }

    fun setScanningActive(active: Boolean) {
        _isScanningActive.value = active
    }

    fun selectAssetForEdit(asset: AssetEntity?) {
        _editingAsset.value = asset
    }

    fun clearLastScannedTag() {
        _lastScannedTag.value = null
    }

    fun onTagScanned(tag: String, onNewTagAdded: (String) -> Unit) {
        if (!_isScanningActive.value) return
        
        if (recentlyScanned.contains(tag)) return
        recentlyScanned.add(tag)
        viewModelScope.launch {
            kotlinx.coroutines.delay(4000) // 4 seconds delay before allowing scanning same tag again
            recentlyScanned.remove(tag)
        }

        viewModelScope.launch {
            val existing = repository.getAssetByTag(tag)
            val matchedCategory = getCategoryByTag(tag)
            if (existing == null) {
                val currentCount = allAssets.value.size + 1
                val newAsset = AssetEntity(
                    tag = tag,
                    name = "자산 #${currentCount}",
                    category = matchedCategory,
                    memo = "카메라 자동 인식",
                    status = "정상"
                )
                repository.insert(newAsset)
                _lastScannedTag.value = tag
                onNewTagAdded(tag)
            } else {
                // Soft update scanned timestamp and category mapping
                val updated = existing.copy(
                    category = matchedCategory,
                    timestamp = System.currentTimeMillis()
                )
                repository.update(updated)
                _lastScannedTag.value = tag
                onNewTagAdded(tag)
            }
        }
    }

    fun addManualAsset(tag: String, name: String, memo: String): Boolean {
        if (!Regex("[A-Za-z]{2}\\d{10}").matches(tag)) {
            return false
        }
        val upperTag = tag.uppercase()
        val matchedCategory = getCategoryByTag(upperTag)
        
        viewModelScope.launch {
            val existing = repository.getAssetByTag(upperTag)
            if (existing == null) {
                val newAsset = AssetEntity(
                    tag = upperTag,
                    name = name.ifEmpty { "수동 등록 자산" },
                    category = matchedCategory,
                    memo = memo,
                    status = "정상"
                )
                repository.insert(newAsset)
            } else {
                val updated = existing.copy(
                    name = name.ifEmpty { existing.name },
                    category = matchedCategory,
                    memo = memo,
                    timestamp = System.currentTimeMillis()
                )
                repository.update(updated)
            }
        }
        return true
    }

    fun updateAssetDetails(asset: AssetEntity) {
        viewModelScope.launch {
            val updatedWithAutoCategory = asset.copy(
                category = getCategoryByTag(asset.tag)
            )
            repository.update(updatedWithAutoCategory)
            if (_editingAsset.value?.id == asset.id) {
                _editingAsset.value = null
            }
        }
    }

    fun deleteAsset(asset: AssetEntity) {
        viewModelScope.launch {
            repository.delete(asset)
            if (_editingAsset.value?.id == asset.id) {
                _editingAsset.value = null
            }
        }
    }

    fun deleteAll() {
        viewModelScope.launch {
            repository.deleteAll()
        }
    }

    fun getExportText(assetsToExport: List<AssetEntity>, sortOrder: SortOrder = _selectedSortOrder.value): String {
        if (assetsToExport.isEmpty()) return "기록된 자산이 없습니다."
        val sortedList = when (sortOrder) {
            SortOrder.TIME_DESC -> assetsToExport.sortedByDescending { it.timestamp }
            SortOrder.TIME_ASC -> assetsToExport.sortedBy { it.timestamp }
            SortOrder.TAG_ASC -> assetsToExport.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.tag })
            SortOrder.TAG_DESC -> assetsToExport.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.tag }).reversed()
            SortOrder.CATEGORY_ASC -> assetsToExport.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.category })
            SortOrder.CATEGORY_DESC -> assetsToExport.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.category }).reversed()
        }
        val sb = java.lang.StringBuilder()
        sortedList.forEach { asset ->
            val dateStr = android.text.format.DateFormat.format("yyyy-MM-dd HH:mm", asset.timestamp).toString()
            sb.append("${asset.tag}  ${asset.category}  $dateStr\n")
        }
        return sb.toString().trimEnd()
    }
}
