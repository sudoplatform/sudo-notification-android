/*
 * Copyright © 2025 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudonotification

import android.content.Context
import androidx.annotation.VisibleForTesting
import com.amazonaws.services.cognitoidentity.model.NotAuthorizedException
import com.amplifyframework.api.graphql.GraphQLResponse
import com.apollographql.apollo3.api.Optional
import com.google.firebase.messaging.RemoteMessage
import com.sudoplatform.sudoapiclient.ApiClientManager
import com.sudoplatform.sudoconfigmanager.DefaultSudoConfigManager
import com.sudoplatform.sudologging.AndroidUtilsLogDriver
import com.sudoplatform.sudologging.LogLevel
import com.sudoplatform.sudologging.Logger
import com.sudoplatform.sudonotification.graphql.DeleteAppFromDeviceMutation
import com.sudoplatform.sudonotification.graphql.GetNotificationSettingsQuery
import com.sudoplatform.sudonotification.graphql.GetUserAndDeviceNotificationSettingsQuery
import com.sudoplatform.sudonotification.graphql.RegisterAppOnDeviceMutation
import com.sudoplatform.sudonotification.graphql.UpdateDeviceInfoMutation
import com.sudoplatform.sudonotification.graphql.UpdateNotificationSettingsMutation
import com.sudoplatform.sudonotification.graphql.type.BuildType
import com.sudoplatform.sudonotification.graphql.type.ClientEnvType
import com.sudoplatform.sudonotification.graphql.type.DeleteAppFromDeviceInput
import com.sudoplatform.sudonotification.graphql.type.Filter
import com.sudoplatform.sudonotification.graphql.type.FilterAction
import com.sudoplatform.sudonotification.graphql.type.GetSettingsInput
import com.sudoplatform.sudonotification.graphql.type.GetUserAndDeviceSettingsInput
import com.sudoplatform.sudonotification.graphql.type.NotifiableServiceSchema
import com.sudoplatform.sudonotification.graphql.type.RegisterAppOnDeviceInput
import com.sudoplatform.sudonotification.graphql.type.SchemaEntry
import com.sudoplatform.sudonotification.graphql.type.UpdateInfoInput
import com.sudoplatform.sudonotification.graphql.type.UpdateSettingsInput
import com.sudoplatform.sudonotification.logging.LogConstants
import com.sudoplatform.sudonotification.types.NotifiableClient
import com.sudoplatform.sudonotification.types.NotificationConfiguration
import com.sudoplatform.sudonotification.types.NotificationFilterItem
import com.sudoplatform.sudonotification.types.NotificationMetaData
import com.sudoplatform.sudonotification.types.NotificationSettingsInput
import com.sudoplatform.sudonotification.types.UserAndDeviceNotificationConfiguration
import com.sudoplatform.sudonotification.types.UserNotificationSettingsInput
import com.sudoplatform.sudonotification.types.transformers.NotificationTransformer
import com.sudoplatform.sudouser.SudoUserClient
import com.sudoplatform.sudouser.amplify.GraphQLClient
import com.sudoplatform.sudouser.exceptions.GRAPHQL_ERROR_TYPE
import com.sudoplatform.sudouser.exceptions.HTTP_STATUS_CODE_KEY
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.util.concurrent.CancellationException

/**
 * Default implementation of the [SudoNotificationClient] interface.
 *
 * @property context [Context] Application context.
 * @property sudoUserClient [SudoUserClient] Instance required to issue authentication tokens
 * @property graphQLClient [GraphQLClient] Optional GraphQL client to use. Mainly used for unit testing.
 * @property logger [Logger] Errors and warnings will be logged here.
 */
class DefaultSudoNotificationClient(
    private val context: Context,
    private val sudoUserClient: SudoUserClient,
    override val notifiableServices: List<NotifiableClient>,
    graphQLClient: GraphQLClient? = null,
    private val logger: Logger = Logger(LogConstants.SUDOLOG_TAG, AndroidUtilsLogDriver(LogLevel.INFO)),
) : SudoNotificationClient {

    init {
        val configManager = DefaultSudoConfigManager(context)
        if (configManager.getConfigSet("apiService") == null) {
            throw SudoNotificationClient.NotificationException.InvalidConfigException("apiService not found")
        }
        val notificationConfig = configManager.getConfigSet("notificationService")
            ?: throw SudoNotificationClient.NotificationException.NoNotificationConfigException()

        // The old way of identifying notifiable services was via a notifiableServices
        // item in notificationService sudoplatformconfig.json stanza.
        //
        // The new way looks for a "notifiable: true" element in the service's own
        // sudoplatformconfig.json stanza
        //
        // For backwards compatibility we look for notifiableServices defined the old
        // way as well as the new way.

        val nApps = notificationConfig.optJSONArray("notifiableServices")
        val notifyingApps = if (nApps == null) {
            mutableListOf<String>()
        } else {
            MutableList(nApps.length()) { nApps.getString(it) }
        }

        for (ns in notifiableServices) {
            // Old way
            if (notifyingApps.contains(ns.serviceName)) {
                continue
            }

            // New way
            val config = configManager.getConfigSet(ns.serviceName)
            if (config?.getBoolean("notifiable") == true) {
                continue
            }

            throw SudoNotificationClient.NotificationException.InvalidConfigException("${ns.serviceName} is not notifiable")
        }
    }

    companion object {
        private const val SUDO_PLATFORM_KEY = "sudoplatform"

        /** Exception messages */
        private const val SERVICE_ERROR_MSG = "Service error"
        private const val DEVICE_ALREADY_REG_ERROR_MSG = "Device already registered"
        private const val DEVICE_NOT_FOUND_ERROR_MSG = "Device not found"
        private const val DEVICE_DELETE_ERROR_MSG = "Device deletion error"
        private const val DEVICE_UPDATE_ERROR_MSG = "Device update error"
        private const val DEVICE_READ_ERROR_MSG = "Device read error"
        private const val DEVICE_CREATE_ERROR_MSG = "Device create error"
        private const val USERINFO_UPDATE_ERROR_MSG = "User info update error"
        private const val USERINFO_READ_ERROR_MSG = "User info read error"
        private const val PAYLOAD_ERROR_MSG = "notification payload error"

        /** Errors returned from the service */
        private const val ERROR_SERVICE = "sudoplatform.ServiceError"
        private const val ERROR_DEVICE_ALREADY_REG = "sudoplatform.ns.DeviceExist"
        private const val ERROR_DEVICE_NOT_FOUND = "sudoplatform.ns.DeviceNotFound"
        private const val ERROR_DEVICE_DELETE = "sudoplatform.ns.DeviceDelete"
        private const val ERROR_DEVICE_UPDATE = "sudoplatform.ns.DeviceUpdate"
        private const val ERROR_DEVICE_READ = "sudoplatform.ns.DeviceRead"
        private const val ERROR_DEVICE_CREATE = "sudoplatform.ns.DeviceCreate"
        private const val ERROR_USERINFO_UPDATE = "sudoplatform.ns.UserInfoUpdate"
        private const val ERROR_USERINFO_READ = "sudoplatform.ns.UserInfoRead"
        private const val ERROR_INVALID_ARGUMENT = "sudoplatform.InvalidArgumentError"
    }

    override val version: String = "3.0.0"

    private val graphQLClient: GraphQLClient =
        graphQLClient ?: ApiClientManager.getClient(
            context,
            this.sudoUserClient,
            "notificationService",
        )

    override suspend fun reset() {
    }

    override fun process(message: RemoteMessage) {
        if (message.data.isEmpty()) {
            throw SudoNotificationClient.NotificationException.NotificationPayloadException(PAYLOAD_ERROR_MSG)
        }
        val sudoplatform = message.data[SUDO_PLATFORM_KEY]
            ?: throw SudoNotificationClient.NotificationException.NotificationPayloadException(PAYLOAD_ERROR_MSG)

        val serviceName = JSONObject(sudoplatform).getString("servicename")
        val service = notifiableServices.find { it.serviceName == serviceName }
        if (service == null) {
            logger.info("notifiable service [$serviceName] not found")
        } else {
            service.processPayload(message)
        }
    }

    override suspend fun getNotificationConfiguration(device: NotificationDeviceInputProvider): NotificationConfiguration {
        if (!this.sudoUserClient.isSignedIn()) {
            throw SudoNotificationClient.NotificationException.NotSignedInException()
        }

        try {
            val input = GetSettingsInput(bundleId = device.bundleIdentifier, deviceId = device.deviceIdentifier)

            val queryResponse = this.graphQLClient.query<GetNotificationSettingsQuery, GetNotificationSettingsQuery.Data>(
                GetNotificationSettingsQuery.OPERATION_DOCUMENT,
                mapOf("input" to input),
            )

            if (queryResponse.hasErrors()) {
                logger.warning("errors = ${queryResponse.errors}")
                throw interpretNotificationError(queryResponse.errors.first())
            }
            val result = queryResponse.data?.getNotificationSettings
            return result?.let { NotificationTransformer.toEntityFromNotificationSettingsOutput(it.notificationSettingsOutput) }!!
        } catch (e: Throwable) {
            logger.debug("unexpected getNotificationConfiguration error $e")
            throw recognizeError(e)
        }
    }

    override suspend fun getUserNotificationConfiguration(): NotificationConfiguration? {
        if (!this.sudoUserClient.isSignedIn()) {
            throw SudoNotificationClient.NotificationException.NotSignedInException()
        }

        try {
            val input = GetUserAndDeviceSettingsInput(
                bundleId = this.context.packageName,
                deviceId = Optional.absent(),
            )

            val queryResponse = this.graphQLClient.query<GetUserAndDeviceNotificationSettingsQuery, GetUserAndDeviceNotificationSettingsQuery.Data>(
                GetUserAndDeviceNotificationSettingsQuery.OPERATION_DOCUMENT,
                mapOf("input" to input),
            )

            if (queryResponse.hasErrors()) {
                logger.warning("errors = ${queryResponse.errors}")
                throw interpretNotificationError(queryResponse.errors.first())
            }
            val result = queryResponse.data.getUserAndDeviceNotificationSettings.userAndDeviceNotificationSettingsOutput.user
            return result?.let { NotificationTransformer.toEntityFromNotificationSettingsOutput(it.notificationSettingsOutput) }
        } catch (e: Throwable) {
            logger.debug("unexpected getUserAndDeviceNotificationConfiguration error $e")
            throw recognizeError(e)
        }
    }

    override suspend fun getUserAndDeviceNotificationConfiguration(device: NotificationDeviceInputProvider): UserAndDeviceNotificationConfiguration {
        if (!this.sudoUserClient.isSignedIn()) {
            throw SudoNotificationClient.NotificationException.NotSignedInException()
        }

        try {
            val input = GetUserAndDeviceSettingsInput(
                bundleId = device.bundleIdentifier,
                deviceId = Optional.present(device.deviceIdentifier),
            )

            val queryResponse = this.graphQLClient.query<GetUserAndDeviceNotificationSettingsQuery, GetUserAndDeviceNotificationSettingsQuery.Data>(
                GetUserAndDeviceNotificationSettingsQuery.OPERATION_DOCUMENT,
                mapOf("input" to input),
            )

            if (queryResponse.hasErrors()) {
                logger.warning("errors = ${queryResponse.errors}")
                throw interpretNotificationError(queryResponse.errors.first())
            }

            val result = queryResponse.data.getUserAndDeviceNotificationSettings.userAndDeviceNotificationSettingsOutput
            return UserAndDeviceNotificationConfiguration(
                user = result.user?.let { NotificationTransformer.toEntityFromNotificationSettingsOutput(it.notificationSettingsOutput) },
                device = result.device?.let { NotificationTransformer.toEntityFromNotificationSettingsOutput(it.notificationSettingsOutput) },
            )
        } catch (e: Throwable) {
            logger.debug("unexpected getUserAndDeviceNotificationConfiguration error $e")
            throw recognizeError(e)
        }
    }

    override suspend fun deRegisterNotification(device: NotificationDeviceInputProvider) {
        if (!this.sudoUserClient.isSignedIn()) {
            throw SudoNotificationClient.NotificationException.NotSignedInException()
        }

        try {
            val input = DeleteAppFromDeviceInput(bundleId = device.bundleIdentifier, deviceId = device.deviceIdentifier)
            val mutationResponse = this.graphQLClient.mutate<DeleteAppFromDeviceMutation, DeleteAppFromDeviceMutation.Data>(
                DeleteAppFromDeviceMutation.OPERATION_DOCUMENT,
                mapOf("input" to input),
            )

            if (mutationResponse.hasErrors()) {
                logger.warning("errors = ${mutationResponse.errors}")
                throw interpretNotificationError(mutationResponse.errors.first())
            }
        } catch (e: Throwable) {
            logger.debug("unexpected deRegisterNotification error $e")
            throw recognizeError(e)
        }
    }

    override suspend fun registerNotification(device: NotificationDeviceInputProvider) {
        if (!this.sudoUserClient.isSignedIn()) {
            throw SudoNotificationClient.NotificationException.NotSignedInException()
        }

        try {
            val input = RegisterAppOnDeviceInput(
                deviceId = device.deviceIdentifier,
                clientEnv = ClientEnvType.valueOf(device.clientEnv.uppercase()),
                bundleId = device.bundleIdentifier,
                build = BuildType.valueOf(device.buildType.uppercase()),
                locale = Optional.presentIfNotNull(device.locale),
                version = Optional.presentIfNotNull(device.appVersion),
                standardToken = Optional.presentIfNotNull(device.pushToken),
            )
            val mutationResponse = this.graphQLClient.mutate<RegisterAppOnDeviceMutation, RegisterAppOnDeviceMutation.Data>(
                RegisterAppOnDeviceMutation.OPERATION_DOCUMENT,
                mapOf("input" to input),
            )

            if (mutationResponse.hasErrors()) {
                logger.warning("errors = ${mutationResponse.errors}")
                throw interpretNotificationError(mutationResponse.errors.first())
            }
        } catch (e: Throwable) {
            logger.debug("unexpected registerNotification error $e")
            throw recognizeError(e)
        }
    }

    override suspend fun setNotificationConfiguration(
        config: NotificationSettingsInput,
    ): NotificationConfiguration {
        return this.setNotificationConfiguration(
            bundleId = config.bundleId,
            deviceId = config.deviceId,
            filter = config.filter,
            services = config.services,
        )
    }

    override suspend fun setUserNotificationConfiguration(
        config: UserNotificationSettingsInput,
    ): NotificationConfiguration {
        return this.setNotificationConfiguration(
            bundleId = this.context.packageName,
            deviceId = null,
            filter = config.filter,
            services = config.services,
        )
    }

    private suspend fun setNotificationConfiguration(
        bundleId: String,
        deviceId: String?,
        filter: List<NotificationFilterItem>,
        services: List<NotificationMetaData>,
    ): NotificationConfiguration {
        if (!this.sudoUserClient.isSignedIn()) {
            throw SudoNotificationClient.NotificationException.NotSignedInException()
        }

        try {
            val mappedFilter = filter.map {
                Filter(
                    serviceName = it.name,
                    actionType = FilterAction.valueOf(it.status!!),
                    rule = it.rules!!,
                    enableMeta = Optional.presentIfNotNull(it.meta!!),
                )
            }
            val mappedServices = services.map { service ->
                NotifiableServiceSchema(
                    serviceName = service.serviceName,
                    schema = Optional.presentIfNotNull(
                        service.schema.map { schema ->
                            SchemaEntry(
                                description = schema.description,
                                type = schema.type,
                                fieldName = schema.fieldName,
                            )
                        },
                    ),
                )
            }

            val input = UpdateSettingsInput(
                deviceId = Optional.presentIfNotNull(deviceId),
                bundleId = bundleId,
                services = mappedServices,
                filter = mappedFilter,
            )
            val mutationResponse = this.graphQLClient.mutate<UpdateNotificationSettingsMutation, UpdateNotificationSettingsMutation.Data>(
                UpdateNotificationSettingsMutation.OPERATION_DOCUMENT,
                mapOf("input" to input),
            )

            if (mutationResponse.hasErrors()) {
                logger.warning("errors = ${mutationResponse.errors}")
                throw interpretNotificationError(mutationResponse.errors.first())
            }
            return NotificationConfiguration(configs = filter)
        } catch (e: Throwable) {
            logger.debug("unexpected setNotificationConfiguration error $e")
            throw recognizeError(e)
        }
    }

    override suspend fun updateNotificationRegistration(device: NotificationDeviceInputProvider) {
        if (!this.sudoUserClient.isSignedIn()) {
            throw SudoNotificationClient.NotificationException.NotSignedInException()
        }

        try {
            val input = UpdateInfoInput(
                bundleId = device.bundleIdentifier,
                deviceId = device.deviceIdentifier,
                build = BuildType.valueOf(device.buildType.uppercase()),
                locale = Optional.presentIfNotNull(device.locale),
                version = Optional.presentIfNotNull(device.appVersion),
                standardToken = Optional.presentIfNotNull(device.pushToken),
            )
            val mutationResponse = this.graphQLClient.mutate<UpdateDeviceInfoMutation, UpdateDeviceInfoMutation.Data>(
                UpdateDeviceInfoMutation.OPERATION_DOCUMENT,
                mapOf("input" to input),
            )

            if (mutationResponse.hasErrors()) {
                logger.warning("errors = ${mutationResponse.errors}")
                throw interpretNotificationError(mutationResponse.errors.first())
            }
        } catch (e: Throwable) {
            logger.debug("unexpected registerNotification error $e")
            throw recognizeError(e)
        }
    }

    private fun interpretNotificationError(e: GraphQLResponse.Error): SudoNotificationClient.NotificationException {
        val httpStatusCode = e.extensions?.get(HTTP_STATUS_CODE_KEY) as Int?
        val error = e.extensions?.get(GRAPHQL_ERROR_TYPE) as String? ?: "Missing error type"
        if (httpStatusCode == HttpURLConnection.HTTP_UNAUTHORIZED) {
            return SudoNotificationClient.NotificationException.NotAuthorizedException(e.message)
        } else if (httpStatusCode != null && httpStatusCode >= HttpURLConnection.HTTP_INTERNAL_ERROR) {
            return SudoNotificationClient.NotificationException.RequestFailedException(e.message)
        }
        if (error.contains(ERROR_SERVICE)) {
            return SudoNotificationClient.NotificationException.ServiceException(SERVICE_ERROR_MSG)
        }
        if (error.contains(ERROR_DEVICE_ALREADY_REG)) {
            return SudoNotificationClient.NotificationException.AlreadyRegisteredNotificationException(DEVICE_ALREADY_REG_ERROR_MSG)
        }
        if (error.contains(ERROR_DEVICE_NOT_FOUND)) {
            return SudoNotificationClient.NotificationException.NoDeviceNotificationException(
                DEVICE_NOT_FOUND_ERROR_MSG,
            )
        }
        if (error.contains(ERROR_DEVICE_UPDATE)) {
            return SudoNotificationClient.NotificationException.RequestFailedException(
                DEVICE_UPDATE_ERROR_MSG,
            )
        }
        if (error.contains(ERROR_DEVICE_DELETE)) {
            return SudoNotificationClient.NotificationException.RequestFailedException(
                DEVICE_DELETE_ERROR_MSG,
            )
        }
        if (error.contains(ERROR_DEVICE_READ)) {
            return SudoNotificationClient.NotificationException.RequestFailedException(
                DEVICE_READ_ERROR_MSG,
            )
        }
        if (error.contains(ERROR_DEVICE_CREATE)) {
            return SudoNotificationClient.NotificationException.RequestFailedException(
                DEVICE_CREATE_ERROR_MSG,
            )
        }
        if (error.contains(ERROR_USERINFO_UPDATE)) {
            return SudoNotificationClient.NotificationException.RequestFailedException(
                USERINFO_UPDATE_ERROR_MSG,
            )
        }
        if (error.contains(ERROR_USERINFO_READ)) {
            return SudoNotificationClient.NotificationException.RequestFailedException(
                USERINFO_READ_ERROR_MSG,
            )
        }
        return SudoNotificationClient.NotificationException.RequestFailedException(e.toString())
    }
}

@VisibleForTesting
fun recognizeError(e: Throwable): Throwable {
    return recognizeRootCause(e) ?: SudoNotificationClient.NotificationException.UnknownException(e)
}

private fun recognizeRootCause(e: Throwable?): Throwable? {
    // If we find a Sudo Platform exception, return that
    if (e?.javaClass?.`package`?.name?.startsWith("com.sudoplatform.") == true) {
        return e
    }

    return when (e) {
        is SudoNotificationClient.NotificationException -> e
        is NotAuthorizedException -> SudoNotificationClient.NotificationException.NotAuthorizedException(cause = e)
        is CancellationException -> e
        is IOException -> SudoNotificationClient.NotificationException.UnknownException(cause = e)
        is RuntimeException -> SudoNotificationClient.NotificationException.UnknownException(cause = e)
        else -> null
    }
}
