package com.vigilex

import com.vigilex.core.util.OtpGenerator
import org.junit.Assert.*
import org.junit.Test

class OtpGeneratorTest {

    private val generator = OtpGenerator()

    @Test fun `generated OTP is exactly 6 digits`() {
        val otp = generator.generate()
        assertEquals(6, otp.length)
        assertTrue("OTP must be all digits", otp.all { it.isDigit() })
    }

    @Test fun `generated OTP is always between 100000 and 999999`() {
        repeat(100) {
            val otp = generator.generate().toInt()
            assertTrue(otp in 100_000..999_999)
        }
    }

    @Test fun `expiry timestamp is 5 minutes in the future`() {
        val before  = System.currentTimeMillis()
        val expiry  = generator.expiryTimestamp()
        val after   = System.currentTimeMillis()
        val fiveMin = 5 * 60 * 1_000L
        assertTrue(expiry >= before + fiveMin)
        assertTrue(expiry <= after  + fiveMin + 100)   // 100ms tolerance
    }

    @Test fun `consecutive OTPs are not all the same`() {
        val otps = (1..20).map { generator.generate() }.toSet()
        assertTrue("Should generate varied OTPs", otps.size > 1)
    }
}
