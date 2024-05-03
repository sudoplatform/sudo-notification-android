/*
 * Copyright © 2023 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudonotification.samples

import android.content.Context
import com.sudoplatform.sudonotification.BaseTests
import org.junit.Test
import org.mockito.kotlin.mock

/**
 * These are sample snippets of code that are included in the generated documentation. They are
 * placed here in the test code to ensure they will compile.
 */
@Suppress("UNUSED_VARIABLE")
class Samples : BaseTests() {

    private val context by before { mock<Context>() }

    @Test
    fun mockTest() {
        // Just to keep junit happy
    }

    /*
    fun sudoNotificationClient(sudoUserClient: SudoUserClient) {
        val client = SudoNotificationClient.builder()
            .setContext(context)
            .setSudoUserClient(sudoUserClient)
            .build()
    }

     */
}
