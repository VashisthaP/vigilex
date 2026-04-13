package com.vigilex.navigation

object Routes {
    const val SPLASH = "splash"
    const val LOGIN = "login"

    // Driver
    const val DRIVER_HOME = "driver_home"

    // Owner
    const val OWNER_DASHBOARD = "owner_dashboard"
    const val DRIVER_DETAIL = "driver_detail/{driverId}/{tripId}"
    const val TRIP_HISTORY = "trip_history"
    const val TRIP_DETAIL = "trip_detail/{tripId}"
    const val DRIVERS_MANAGEMENT = "drivers_management"
    const val OWNER_SETTINGS = "owner_settings"

    // Super Admin
    const val SUPER_ADMIN_DASHBOARD = "super_admin_dashboard"
    const val ADD_COMPANY = "add_company"
    const val COMPANY_DETAIL = "company_detail/{companyId}"

    // Helpers
    fun driverDetail(driverId: String, tripId: String) = "driver_detail/$driverId/$tripId"
    fun tripDetail(tripId: String) = "trip_detail/$tripId"
    fun companyDetail(companyId: String) = "company_detail/$companyId"
}
