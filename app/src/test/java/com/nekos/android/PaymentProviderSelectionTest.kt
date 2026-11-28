package com.nekos.android

import org.junit.Assert.assertEquals
import org.junit.Test

class PaymentProviderSelectionTest {
    @Test
    fun defaultsToPaypalWhenUsdPriceIsAvailable() {
        assertEquals("paypal", preferredPaymentProvider(1.49, null))
    }

    @Test
    fun selectsMidtransWhenSwitchingAwayFromPaypal() {
        assertEquals("midtrans", preferredPaymentProvider(1.49, "paypal"))
    }

    @Test
    fun selectsPaypalWhenSwitchingAwayFromMidtransAndUsdPriceExists() {
        assertEquals("paypal", preferredPaymentProvider(1.49, "midtrans"))
    }

    @Test
    fun fallsBackToMidtransWhenPaypalPriceIsUnavailable() {
        assertEquals("midtrans", preferredPaymentProvider(0.0, null))
        assertEquals("midtrans", preferredPaymentProvider(-1.0, "midtrans"))
    }

    @Test
    fun rejectsNonFinitePaypalPricesForProviderSelection() {
        assertEquals("midtrans", preferredPaymentProvider(Double.NaN, null))
        assertEquals("midtrans", preferredPaymentProvider(Double.POSITIVE_INFINITY, null))
    }
}
