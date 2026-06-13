package com.example.data

import kotlinx.coroutines.flow.Flow

class AssetRepository(private val assetDao: AssetDao) {
    val allAssets: Flow<List<AssetEntity>> = assetDao.getAllAssets()

    suspend fun getAssetById(id: Int): AssetEntity? = assetDao.getAssetById(id)

    suspend fun getAssetByTag(tag: String): AssetEntity? = assetDao.getAssetByTag(tag)

    suspend fun insert(asset: AssetEntity): Long = assetDao.insertAsset(asset)

    suspend fun update(asset: AssetEntity) = assetDao.updateAsset(asset)

    suspend fun delete(asset: AssetEntity) = assetDao.deleteAsset(asset)

    suspend fun deleteById(id: Int) = assetDao.deleteAssetById(id)

    suspend fun deleteAll() = assetDao.deleteAllAssets()
}
