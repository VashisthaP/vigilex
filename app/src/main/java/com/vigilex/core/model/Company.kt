package com.vigilex.core.model

data class Company(
    val id: String = "",
    val companyName: String = "",
    val ownerUid: String = "",
    val createdAt: Long = 0L,
    val driverCount: Int = 0
)
