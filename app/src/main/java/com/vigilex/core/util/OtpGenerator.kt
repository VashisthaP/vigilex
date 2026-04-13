package com.vigilex.core.util

import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random

@Singleton
class OtpGenerator @Inject constructor() {

    /** Generates a 6-digit numeric OTP string, zero-padded. */
    fun generate(): String = Random.nextInt(100_000, 999_999).toString()

    /** Returns the expiry timestamp 5 minutes from now. */
    fun expiryTimestamp(): Long = System.currentTimeMillis() + 5 * 60 * 1000L
}
