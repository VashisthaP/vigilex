package com.vigilex.core.model

data class User(
    val uid: String = "",
    val name: String = "",
    val email: String = "",
    val phone: String = "",
    val role: Role = Role.DRIVER,
    val companyId: String = "",
    val fcmToken: String = "",
    /** Owner-set 6-digit exit PIN for the driver's monitoring lock screen. */
    val exitPin: String = ""
)
