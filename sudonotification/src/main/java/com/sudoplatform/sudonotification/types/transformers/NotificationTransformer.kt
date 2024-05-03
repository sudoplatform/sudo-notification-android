/*
 * Copyright © 2023 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudonotification.types.transformers

import com.sudoplatform.sudonotification.graphql.GetNotificationSettingsQuery
import com.sudoplatform.sudonotification.types.NotificationConfiguration
import com.sudoplatform.sudonotification.types.NotificationFilterItem
import java.util.*

internal object NotificationTransformer {
    /**
     * Transform the results of [GetNotificationSettingsQuery] to the publicly visible type.
     *
     * @param result The result of the GraphQL query.
     * @return The [GetNotificationSettingsQuery] entity type.
     */
    fun toEntityFromGetNotificationSettingsQueryResult(
        result: GetNotificationSettingsQuery.GetNotificationSettings,
    ): NotificationConfiguration {
        val configs: List<NotificationFilterItem> = result.filter().map {
            NotificationFilterItem(
                UUID.randomUUID(),
                name = it.serviceName(),
                status = it.actionType().toString().uppercase(),
                rules = it.rule(),
                meta = it.enableMeta(),
            )
        }
        return NotificationConfiguration(configs = configs)
    }
}
