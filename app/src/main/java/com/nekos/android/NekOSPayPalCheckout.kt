package com.nekos.android

import android.app.Activity
import android.content.Context
import android.content.Intent
import com.paypal.android.corepayments.CoreConfig
import com.paypal.android.corepayments.CoreEnvironment
import com.paypal.android.corepayments.model.TokenType
import com.paypal.android.paypalpayments.PayPalClient
import com.paypal.android.paypalpayments.PayPalPresentAuthChallengeResult
import com.paypal.android.paypalpayments.PayPalUserAction
import com.paypal.android.paypalpayments.ReturnToAppUrlConfig

/**
 * PayPal SDK configuration contains public merchant identifiers only.
 * Client secrets remain on the Supabase Edge Function.
 */
internal class NekOSPayPalCheckout(context: Context) {
    private val client = PayPalClient(
        context.applicationContext,
        CoreConfig(
            clientId = CLIENT_ID,
            merchantId = MERCHANT_ID,
            coreEnvironment = CoreEnvironment.SANDBOX
        )
    )

    fun restore(instanceState: String?) {
        if (!instanceState.isNullOrBlank()) client.restore(instanceState)
    }

    val instanceState: String
        get() = client.instanceState

    fun prepareSession() {
        client.createPayPalSession(
            tokenType = TokenType.ORDER_ID,
            userIdentity = null,
            urlConfig = ReturnToAppUrlConfig(
                returnAppUrl = RETURN_URL,
                cancelAppUrl = CANCEL_URL,
                fallbackSchemeUrl = "nekos"
            ),
            userAction = PayPalUserAction.PAY_NOW
        )
    }

    fun start(
        activity: Activity,
        providerOrderId: String,
        onResult: (PayPalPresentAuthChallengeResult) -> Unit
    ) {
        // Create the SDK session immediately before starting checkout, on the same client.
        // This guarantees the session call is not skipped by a Compose Context cast.
        prepareSession()
        client.start(activity, providerOrderId) { result ->
            onResult(result)
        }
    }

    fun finishStart(intent: Intent) = client.finishStart(intent)

    private companion object {
        const val CLIENT_ID = "AdXDufTF04dGQmn-fEcfdFCS4HqzP3EgZZdd7Zd4WgY-Tc3DSRVagl6kBE9lOBvoB6zOTpAksDqHupCT"
        const val MERCHANT_ID = "V84JYBDZ5VMC2"
        const val RETURN_URL = "nekos://paypal-finish"
        const val CANCEL_URL = "nekos://paypal-cancel"
    }
}
