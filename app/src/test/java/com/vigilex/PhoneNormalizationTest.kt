package com.vigilex

import org.junit.Assert.*
import org.junit.Test

class PhoneNormalizationTest {

    private fun normalizePhone(raw: String): String {
        val cleaned = raw.trim().replace(" ", "").replace("-", "")
        return when {
            cleaned.startsWith("+") -> cleaned
            cleaned.startsWith("0") -> "+91${cleaned.drop(1)}"
            cleaned.length == 10    -> "+91$cleaned"
            else                    -> "+$cleaned"
        }
    }

    @Test fun `10-digit number gets +91 prefix`() {
        assertEquals("+919876543210", normalizePhone("9876543210"))
    }

    @Test fun `number with leading 0 gets +91 and drops 0`() {
        assertEquals("+919876543210", normalizePhone("09876543210"))
    }

    @Test fun `number already with +91 unchanged`() {
        assertEquals("+919876543210", normalizePhone("+919876543210"))
    }

    @Test fun `spaces and dashes stripped`() {
        assertEquals("+919876543210", normalizePhone("98765 43210"))
    }

    @Test fun `super admin phone normalized correctly`() {
        assertEquals("+918587089545", normalizePhone("8587089545"))
    }
}
