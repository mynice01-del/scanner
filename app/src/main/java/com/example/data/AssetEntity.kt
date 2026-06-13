package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "assets")
data class AssetEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val tag: String,
    val name: String = "",
    val category: String = "기타",
    val memo: String = "",
    val status: String = "정상", // 정상, 점검필요, 분실, 폐기
    val timestamp: Long = System.currentTimeMillis()
)
