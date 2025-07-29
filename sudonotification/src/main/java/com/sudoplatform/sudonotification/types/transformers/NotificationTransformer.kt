/*
 * Copyright © 2025 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudonotification.types.transformers

import com.sudoplatform.sudonotification.graphql.fragment.NotificationSettingsOutput
import com.sudoplatform.sudonotification.types.NotificationConfiguration
import com.sudoplatform.sudonotification.types.NotificationFilterItem
import java.util.*

internal object NotificationTransformer {
    /**
     * Transform a [NotificationSettingsOutput] from GraphQL queries to the publicly visible type.
     *
     * @param notificationSettingsOutput The result of the GraphQL query.
     * @return The [NotificationConfiguration] entity type.
     */
    fun toEntityFromNotificationSettingsOutput(
        notificationSettingsOutput: NotificationSettingsOutput,
    ): NotificationConfiguration {
        val configs: List<NotificationFilterItem> = notificationSettingsOutput.filter
            .map {
                NotificationFilterItem(
                    UUID.randomUUID(),
                    name = it.serviceName,
                    status = it.actionType.toString().uppercase(),
                    rules = it.rule,
                    meta = it.enableMeta,
                )
            }
        return NotificationConfiguration(configs = configs)
    }
}
