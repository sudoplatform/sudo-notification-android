/*
 * Copyright © 2023 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudonotification

import com.sudoplatform.sudologging.LogDriverInterface
import com.sudoplatform.sudologging.LogLevel
import com.sudoplatform.sudologging.Logger
import com.sudoplatform.sudonotification.rules.ActualPropertyResetter
import com.sudoplatform.sudonotification.rules.PropertyResetRule
import com.sudoplatform.sudonotification.rules.PropertyResetter
import com.sudoplatform.sudonotification.rules.TimberLogRule
import org.junit.Rule
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.stub

/**
 * Base class that sets up:
 * - [TimberLogRule]
 * - [PropertyResetRule]
 *
 * And provides convenient access to the [PropertyResetRule.before] via [PropertyResetter.before].
 */
abstract class BaseTests : PropertyResetter by ActualPropertyResetter() {
    @Rule
    @JvmField
    val timberLogRule = TimberLogRule()

    private val mockLogDriver by before {
        mock<LogDriverInterface>().stub {
            on { logLevel } doReturn LogLevel.VERBOSE
        }
    }

    protected val mockLogger by before {
        Logger("mock", mockLogDriver)
    }
}
