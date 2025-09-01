/*
 * Copyright © 2024 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudonotification

import android.content.Context
import com.sudoplatform.sudologging.AndroidUtilsLogDriver
import com.sudoplatform.sudologging.LogLevel
import com.sudoplatform.sudologging.Logger
import com.sudoplatform.sudonotification.logging.LogConstants
import com.sudoplatform.sudouser.SudoUserClient
import io.kotlintest.shouldThrow
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.robolectric.RobolectricTestRunner

/**
 * Test the handling of the JSON config items.
 */
@RunWith(RobolectricTestRunner::class)
class SudoNotificationClientConfigTest : BaseTests() {
    private val mockContext: Context = mock()
    private val mockUserClient: SudoUserClient = mock()

    @Test
    fun shouldThrowIfConfigMissing() {
        val logger =
            Logger(
                LogConstants.SUDOLOG_TAG,
                AndroidUtilsLogDriver(LogLevel.INFO),
            )

        shouldThrow<NullPointerException> {
            SudoNotificationClient
                .builder()
                .setContext(mockContext)
                .setSudoUserClient(mockUserClient)
                .setLogger(logger)
                .build()
        }
    }
}
