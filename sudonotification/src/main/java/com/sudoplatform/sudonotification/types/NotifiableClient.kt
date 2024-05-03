/*
 * Copyright © 2023 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudonotification.types

import com.google.firebase.messaging.RemoteMessage

interface NotifiableClient {
    /*
     * String representation of the service name
     */
    val serviceName: String

    /*
     * The function to take notification payload and further process it within the client service
     * module SDK
     */
    fun processPayload(message: RemoteMessage)

    /*
     * the function to provide notification meta schema for filtering of the client service
     */
    fun getSchema(): NotificationMetaData
}

interface NotificationMetaData {
    /*
     * String representation of the service name
     */
    val serviceName: String

    /*
     * The actual notification meta schema of client service
     */
    val schema: List<NotificationSchemaEntry>
}

interface NotificationSchemaEntry {
    /*
     * Description of the schema
     */
    val description: String

    /*
     * Schema field name, e.g, emailAdddress
     */
    val fieldName: String

    /*
     * Schema field type, e.g., string
     */
    val type: String
}
