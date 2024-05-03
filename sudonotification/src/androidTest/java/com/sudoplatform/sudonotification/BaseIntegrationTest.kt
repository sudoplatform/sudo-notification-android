/*
 * Copyright © 2023 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudonotification

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.sudoplatform.sudoconfigmanager.DefaultSudoConfigManager
import com.sudoplatform.sudokeymanager.KeyManagerFactory
import com.sudoplatform.sudouser.JWT
import com.sudoplatform.sudouser.SudoUserClient
import com.sudoplatform.sudouser.TESTAuthenticationProvider
import io.kotlintest.shouldBe
import io.kotlintest.shouldNotBe
import timber.log.Timber

/**
 * Test the operation of the [SudoNotificationClient].
 */
abstract class BaseIntegrationTest {

    protected val context: Context = ApplicationProvider.getApplicationContext<Context>()

    protected val userClient by lazy {
        SudoUserClient.builder(context)
            .setNamespace("ns-client-test")
            .build()
    }

    protected val keyManager by lazy {
        KeyManagerFactory(context).createAndroidKeyManager()
    }

    protected val configManager by lazy {
        DefaultSudoConfigManager(context)
    }

    private suspend fun register() {
        userClient.isRegistered() shouldBe false

        userClient.reset()
        val privateKey = readTextFile("register_key.private")
        val keyId = readTextFile("register_key.id")

        val authProvider = TESTAuthenticationProvider(
            name = "ns-client-test",
            privateKey = privateKey,
            publicKey = null,
            keyManager = keyManager,
            keyId = keyId,
        )

        userClient.registerWithAuthenticationProvider(authProvider, "ns-client-test")
    }

    private fun readTextFile(fileName: String): String {
        return context.assets.open(fileName).bufferedReader().use {
            it.readText().trim()
        }
    }

    private fun identityTokenHasAttribute(name: String): Boolean {
        userClient.isSignedIn() shouldBe true

        val encodedIdentityToken = userClient.getIdToken()
        encodedIdentityToken shouldNotBe null

        val identityToken = JWT.decode(encodedIdentityToken!!)
        identityToken shouldNotBe null

        return identityToken!!.payload.has(name)
    }

    protected suspend fun signInAndRegister() {
        if (!userClient.isRegistered()) {
            register()
        }
        userClient.isRegistered() shouldBe true
        if (userClient.isSignedIn()) {
            userClient.getRefreshToken()?.let { userClient.refreshTokens(it) }
        } else {
            userClient.signInWithKey()
        }
        userClient.isSignedIn() shouldBe true
    }

    protected suspend fun deRegister() {
        if (!userClient.isRegistered()) {
            return
        }
        userClient.deregister()
    }

    protected fun clientConfigFilesPresent(): Boolean {
        val configFiles = context.assets.list("")?.filter { fileName ->
            fileName == "sudoplatformconfig.json" ||
                fileName == "register_key.private" ||
                fileName == "register_key.id"
        } ?: emptyList()
        Timber.d("config files present ${configFiles.size}")
        return configFiles.size == 3
    }
}
