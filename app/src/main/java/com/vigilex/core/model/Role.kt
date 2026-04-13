package com.vigilex.core.model

enum class Role {
    SUPER_ADMIN,
    OWNER,
    DRIVER;

    companion object {
        fun from(value: String): Role = when (value.lowercase()) {
            "superadmin" -> SUPER_ADMIN
            "owner" -> OWNER
            "driver" -> DRIVER
            else -> DRIVER
        }
    }

    fun toFirestoreValue(): String = when (this) {
        SUPER_ADMIN -> "superadmin"
        OWNER -> "owner"
        DRIVER -> "driver"
    }
}
