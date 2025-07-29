/*
 * Copyright © 2025 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.sudoplatform.sudonotification.types

/**
 * Output of getUserAndDeviceNotificationConfiguration method
 */
data class UserAndDeviceNotificationConfiguration(
    /**
     * User level notification configuration or null if none set.
     */
    val user: NotificationConfiguration?,

    /**
     * Device level notification configuration or null if none set.
     */
    val device: NotificationConfiguration?,
)
