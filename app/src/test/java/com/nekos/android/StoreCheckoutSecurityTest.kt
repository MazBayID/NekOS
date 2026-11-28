package com.nekos.android

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StoreCheckoutSecurityTest {
    @Test
    fun acceptsOnlyProviderSpecificHttpsCheckoutHosts() {
        assertTrue(isTrustedStoreCheckout("https://app.midtrans.com/snap/v2/vtweb/token", "midtrans"))
        assertTrue(isTrustedStoreCheckout("https://app.sandbox.midtrans.com/snap/v2/vtweb/token", "midtrans"))
        assertTrue(isTrustedStoreCheckout("https://www.paypal.com/checkoutnow?token=abc", "paypal"))
        assertTrue(isTrustedStoreCheckout("https://www.sandbox.paypal.com/checkoutnow?token=abc", "paypal"))
    }

    @Test
    fun rejectsProviderHostConfusionAndUnknownProviders() {
        assertFalse(isTrustedStoreCheckout("https://www.paypal.com/checkoutnow?token=abc", "midtrans"))
        assertFalse(isTrustedStoreCheckout("https://app.midtrans.com/snap/token", "paypal"))
        assertFalse(isTrustedStoreCheckout("https://www.paypal.com.evil.example/checkoutnow", "paypal"))
        assertFalse(isTrustedStoreCheckout("https://paypal.com.evil.example/checkoutnow", "paypal"))
        assertFalse(isTrustedStoreCheckout("https://www.paypal.com/checkoutnow", "unknown"))
    }

    @Test
    fun rejectsUnsafeSchemesCredentialsAndPorts() {
        assertFalse(isTrustedStoreCheckout("http://www.sandbox.paypal.com/checkoutnow", "paypal"))
        assertFalse(isTrustedStoreCheckout("javascript:alert(1)", "paypal"))
        assertFalse(isTrustedStoreCheckout("https://user@www.paypal.com/checkoutnow", "paypal"))
        assertFalse(isTrustedStoreCheckout("https://www.paypal.com:8443/checkoutnow", "paypal"))
        assertFalse(isTrustedStoreCheckout("https://app.midtrans.com:8443/snap/token", "midtrans"))
    }
}
