/*
 * Copyright © 2024 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudonotification

import com.google.firebase.messaging.RemoteMessage
import com.sudoplatform.sudonotification.types.NotifiableClient
import com.sudoplatform.sudonotification.types.NotificationMetaData
import com.sudoplatform.sudonotification.types.NotificationSchemaEntry

class TestNotifiableClient : NotifiableClient {
    override val serviceName: String
        get() = "SampleService"

    override fun processPayload(message: RemoteMessage) {
        TODO("Not yet implemented")
    }

    override fun getSchema(): NotificationMetaData = TestNotificationMetaData(this.serviceName)
}

class TestNotificationMetaData(
    override var serviceName: String,
) : NotificationMetaData {
    override val schema: List<NotificationSchemaEntry>
        get() = listOf<TestNotificationSchemaEntry>(TestNotificationSchemaEntry("f1"), TestNotificationSchemaEntry("f2"))
}

class TestNotificationSchemaEntry(
    private val field: String,
) : NotificationSchemaEntry {
    override var fieldName: String = "meta.$field"
    override val description: String
        get() = "test service field"

    override val type: String
        get() = "string"
}
