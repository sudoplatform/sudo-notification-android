/*
 * Copyright © 2025 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudonotification

import com.sudoplatform.sudoapiclient.ApiClientManager
import com.sudoplatform.sudologging.AndroidUtilsLogDriver
import com.sudoplatform.sudologging.LogLevel
import com.sudoplatform.sudologging.Logger
import com.sudoplatform.sudonotification.types.NotifiableClient
import com.sudoplatform.sudonotification.types.NotificationConfiguration
import com.sudoplatform.sudonotification.types.NotificationFilterItem
import com.sudoplatform.sudonotification.types.NotificationMetaData
import com.sudoplatform.sudonotification.types.NotificationSettingsInput
import com.sudoplatform.sudonotification.types.UserNotificationSettingsInput
import io.kotlintest.matchers.numerics.shouldBeExactly
import io.kotlintest.shouldBe
import io.kotlintest.shouldNotThrowAny
import io.kotlintest.shouldThrow
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assume
import org.junit.Before
import org.junit.Test
import timber.log.Timber
import java.util.logging.Level

/**
 * Test the operation of the [SudoNotificationClient].
 */
class SudoNotificationClientIntegrationTest : BaseIntegrationTest() {
    private val verbose = false
    private val logLevel = if (verbose) LogLevel.VERBOSE else LogLevel.INFO
    private val logger = Logger("notification-test", AndroidUtilsLogDriver(logLevel))

    private lateinit var notificationClient: SudoNotificationClient
    private lateinit var deviceInfoProvider: DefaultNotificationDeviceInputProvider
    private lateinit var sampleNotifiableClient: NotifiableClient

    companion object {
        private const val TEST_SERVICE_NAME = "sampleService"
        private const val TEST_ALT_SERVICE_NAME = "test-alternative-service"
        private const val TEST_RULE = "{\"==\" : [{\"var\" : \"meta.f1\"}, \"sudo123@sudomail.com\"]}"
        private const val TEST_DEVICE_ID = "test-notification-deviceID"
        private const val INVALID_DEVICE_ID = "not-test-notification-deviceID"
        private const val TEST_TOKEN = "StandardToken-test-push-token"
    }

    @Before
    fun init() {
        Timber.plant(Timber.DebugTree())

        if (verbose) {
            java.util.logging.Logger.getLogger("com.amazonaws").level = Level.FINEST
            java.util.logging.Logger.getLogger("org.apache.http").level = Level.FINEST
        }

        sampleNotifiableClient = TestNotifiableClient()

        notificationClient = SudoNotificationClient.builder()
            .setContext(context)
            .setSudoUserClient(userClient)
            .setLogger(logger)
            .setNotifiableClients(listOf(sampleNotifiableClient))
            .build()

        deviceInfoProvider = DefaultNotificationDeviceInputProvider(context, TEST_DEVICE_ID, TEST_TOKEN)
    }

    @After
    fun fini(): Unit = runBlocking {
        userClient.reset()
        Timber.uprootAll()
    }

    @Test
    fun shouldThrowIfRequiredItemsNotProvidedToBuilder() {
        // Can only run if client config files are present
        Assume.assumeTrue(clientConfigFilesPresent())

        // All required items not provided
        shouldThrow<NullPointerException> {
            SudoNotificationClient.builder().build()
        }

        // Context not provided
        shouldThrow<NullPointerException> {
            SudoNotificationClient.builder()
                .setSudoUserClient(userClient)
                .build()
        }

        // SudoUserClient not provided
        shouldThrow<NullPointerException> {
            SudoNotificationClient.builder()
                .setContext(context)
                .build()
        }
    }

    @Test
    fun shouldNotThrowIfTheRequiredItemsAreProvidedToBuilder() {
        // Can only run if client config files are present
        Assume.assumeTrue(clientConfigFilesPresent())

        SudoNotificationClient.builder()
            .setContext(context)
            .setSudoUserClient(userClient)
            .setNotifiableClients(listOf(sampleNotifiableClient))
            .build()
    }

    @Test
    fun shouldNotThrowIfAllItemsAreProvidedToBuilder() {
        // Can only run if client config files are present
        Assume.assumeTrue(clientConfigFilesPresent())

        val appSyncClient = ApiClientManager.getClient(context, userClient)

        SudoNotificationClient.builder()
            .setContext(context)
            .setSudoUserClient(userClient)
            .setGraphQLClient(appSyncClient)
            .setLogger(logger)
            .setNotifiableClients(listOf(sampleNotifiableClient))
            .build()
    }

    @Test
    fun registerDeviceShouldNotThrow(): Unit = runBlocking {
        // Can only run if client config files are present
        Assume.assumeTrue(clientConfigFilesPresent())

        signInAndRegister()

        shouldNotThrowAny {
            notificationClient.registerNotification(deviceInfoProvider)
        }

        deRegister()
    }

    @Test
    fun registerDevicesAgainShouldThrow(): Unit = runBlocking {
        // Can only run if client config files are present
        Assume.assumeTrue(clientConfigFilesPresent())

        signInAndRegister()

        notificationClient.registerNotification(deviceInfoProvider)

        shouldThrow<SudoNotificationClient.NotificationException.AlreadyRegisteredNotificationException> {
            notificationClient.registerNotification(deviceInfoProvider)
        }

        deRegister()
    }

    @Test
    fun deRegisterDevicesShouldNotThrow(): Unit = runBlocking {
        // Can only run if client config files are present
        Assume.assumeTrue(clientConfigFilesPresent())

        signInAndRegister()

        notificationClient.registerNotification(deviceInfoProvider)

        shouldNotThrowAny {
            notificationClient.deRegisterNotification(deviceInfoProvider)
        }

        deRegister()
    }

    @Test
    fun deRegisterInvalidDevicesShouldThrow(): Unit = runBlocking {
        // Can only run if client config files are present
        Assume.assumeTrue(clientConfigFilesPresent())

        signInAndRegister()

        notificationClient.registerNotification(deviceInfoProvider)

        val deviceInfoUpdate = DefaultNotificationDeviceInputProvider(context, INVALID_DEVICE_ID, "")

        shouldThrow<SudoNotificationClient.NotificationException.NoDeviceNotificationException> {
            notificationClient.deRegisterNotification(deviceInfoUpdate)
        }

        deRegister()
    }

    @Test
    fun updateDevicesShouldNotThrow(): Unit = runBlocking {
        // Can only run if client config files are present
        Assume.assumeTrue(clientConfigFilesPresent())

        signInAndRegister()

        notificationClient.registerNotification(deviceInfoProvider)

        val deviceInfoUpdate = DefaultNotificationDeviceInputProvider(context, TEST_DEVICE_ID, "")

        shouldNotThrowAny {
            notificationClient.updateNotificationRegistration(deviceInfoUpdate)
        }

        deRegister()
    }

    @Test
    fun updateDeviceWithoutRegisterShouldThrow(): Unit = runBlocking {
        // Can only run if client config files are present
        Assume.assumeTrue(clientConfigFilesPresent())

        signInAndRegister()

        shouldThrow<SudoNotificationClient.NotificationException.NoDeviceNotificationException> {
            notificationClient.updateNotificationRegistration(deviceInfoProvider)
        }

        deRegister()
    }

    @Test
    fun updateInvalidDeviceShouldThrow(): Unit = runBlocking {
        // Can only run if client config files are present
        Assume.assumeTrue(clientConfigFilesPresent())

        signInAndRegister()

        notificationClient.registerNotification(deviceInfoProvider)

        deRegister()

        // Now we are with a different user despite same device ID.
        signInAndRegister()

        shouldThrow<SudoNotificationClient.NotificationException.NoDeviceNotificationException> {
            notificationClient.updateNotificationRegistration(deviceInfoProvider)
        }

        deRegister()
    }

    @Test
    fun getNotificationConfigListAfterRegisterShouldReturnEmpty(): Unit = runBlocking {
        // Can only run if client config files are present
        Assume.assumeTrue(clientConfigFilesPresent())

        signInAndRegister()

        notificationClient.registerNotification(deviceInfoProvider)

        val notifConf = notificationClient.getNotificationConfiguration(deviceInfoProvider)
        notifConf.configs.size shouldBeExactly 0

        notificationClient.deRegisterNotification(deviceInfoProvider)

        deRegister()
    }

    @Test
    fun getNotificationConfigListOnInvalidDeviceShouldThrow(): Unit = runBlocking {
        // Can only run if client config files are present
        Assume.assumeTrue(clientConfigFilesPresent())

        signInAndRegister()

        shouldThrow<SudoNotificationClient.NotificationException.NoDeviceNotificationException> {
            notificationClient.getNotificationConfiguration(deviceInfoProvider)
        }
        deRegister()
    }

    @Test
    fun setNotificationConfigShouldReturnSuccess(): Unit = runBlocking {
        // Can only run if client config files are present
        Assume.assumeTrue(clientConfigFilesPresent())

        signInAndRegister()

        notificationClient.registerNotification(deviceInfoProvider)

        val confList = listOf(NotificationFilterItem(name = TEST_SERVICE_NAME))
        val schema = listOf<NotificationMetaData>(TestNotificationMetaData(TEST_SERVICE_NAME))
        val config = NotificationConfiguration(configs = confList)
        val input = NotificationSettingsInput(deviceInfoProvider.bundleIdentifier, deviceInfoProvider.deviceIdentifier, confList, schema)

        val ret = notificationClient.setNotificationConfiguration(input)
        ret shouldBe config

        notificationClient.deRegisterNotification(deviceInfoProvider)

        deRegister()
    }

    @Test
    fun setNotificationConfigOnInvalidDeviceShouldThrow(): Unit = runBlocking {
        // Can only run if client config files are present
        Assume.assumeTrue(clientConfigFilesPresent())

        signInAndRegister()
        logger.warning("user is ${userClient.getUserName()}")

        val confList = listOf(NotificationFilterItem(name = TEST_SERVICE_NAME))
        val schema = listOf<NotificationMetaData>(TestNotificationMetaData(TEST_SERVICE_NAME))
        val input = NotificationSettingsInput(deviceInfoProvider.bundleIdentifier, deviceInfoProvider.deviceIdentifier, confList, schema)

        shouldThrow<SudoNotificationClient.NotificationException.NoDeviceNotificationException> {
            notificationClient.setNotificationConfiguration(input)
        }

        deRegister()
    }

    @Test
    fun setAndGetNotificationConfigShouldMatch(): Unit = runBlocking {
        // Can only run if client config files are present
        Assume.assumeTrue(clientConfigFilesPresent())

        signInAndRegister()

        logger.warning("user is ${userClient.getUserName()}")
        notificationClient.registerNotification(deviceInfoProvider)

        val confList = listOf(
            NotificationFilterItem(name = TEST_SERVICE_NAME),
            NotificationFilterItem(name = TEST_SERVICE_NAME, rules = TEST_RULE),
            NotificationFilterItem(name = TEST_ALT_SERVICE_NAME),
        )
        val config = NotificationConfiguration(configs = confList)
        config.updateConfig(uuid = confList.first { it.name == TEST_ALT_SERVICE_NAME }.uuid!!, status = false)

        val schema = listOf<NotificationMetaData>(TestNotificationMetaData(TEST_SERVICE_NAME), TestNotificationMetaData(TEST_ALT_SERVICE_NAME))
        val input = NotificationSettingsInput(deviceInfoProvider.bundleIdentifier, deviceInfoProvider.deviceIdentifier, config.configs, schema)
        notificationClient.setNotificationConfiguration(input)
        val ret = notificationClient.getNotificationConfiguration(deviceInfoProvider)
        ret shouldBe config
        ret.configs shouldBe config.configs

        notificationClient.deRegisterNotification(deviceInfoProvider)

        deRegister()
    }

    @Test
    fun getUserNotificationConfigListBeforeBeingSetShouldReturnNull(): Unit = runBlocking {
        // Can only run if client config files are present
        Assume.assumeTrue(clientConfigFilesPresent())

        signInAndRegister()

        val notifConf = notificationClient.getUserNotificationConfiguration()
        notifConf shouldBe null

        deRegister()
    }

    @Test
    fun setAndGetUserNotificationConfigShouldMatch(): Unit = runBlocking {
        // Can only run if client config files are present
        Assume.assumeTrue(clientConfigFilesPresent())

        signInAndRegister()

        logger.warning("user is ${userClient.getUserName()}")

        val confList = listOf(
            NotificationFilterItem(name = TEST_SERVICE_NAME),
            NotificationFilterItem(name = TEST_SERVICE_NAME, rules = TEST_RULE),
            NotificationFilterItem(name = TEST_ALT_SERVICE_NAME),
        )
        val config = NotificationConfiguration(configs = confList)
        config.updateConfig(uuid = confList.first { it.name == TEST_ALT_SERVICE_NAME }.uuid!!, status = false)

        val schema = listOf<NotificationMetaData>(TestNotificationMetaData(TEST_SERVICE_NAME), TestNotificationMetaData(TEST_ALT_SERVICE_NAME))
        val input = UserNotificationSettingsInput(config.configs, schema)
        notificationClient.setUserNotificationConfiguration(input)
        val ret = notificationClient.getUserNotificationConfiguration()
        ret shouldBe config
        ret?.configs shouldBe config.configs

        deRegister()
    }

    @Test
    fun getUserAndDeviceNotificationConfigBeforeBeingSetShouldReturnEmptyConfig(): Unit = runBlocking {
        // Can only run if client config files are present
        Assume.assumeTrue(clientConfigFilesPresent())

        signInAndRegister()
        notificationClient.registerNotification(deviceInfoProvider)

        val notifConf = notificationClient.getUserAndDeviceNotificationConfiguration(deviceInfoProvider)
        notifConf.user shouldBe null
        notifConf.device shouldBe null

        notificationClient.deRegisterNotification(deviceInfoProvider)
        deRegister()
    }

    @Test
    fun setAndGetUserAndDeviceNotificationConfigShouldMatch(): Unit = runBlocking {
        // Can only run if client config files are present
        Assume.assumeTrue(clientConfigFilesPresent())

        signInAndRegister()

        notificationClient.registerNotification(deviceInfoProvider)

        val userConfList = listOf(
            NotificationFilterItem(name = TEST_SERVICE_NAME),
            NotificationFilterItem(name = TEST_SERVICE_NAME, rules = TEST_RULE),
            NotificationFilterItem(name = TEST_ALT_SERVICE_NAME),
        )
        val userConfig = NotificationConfiguration(configs = userConfList)
        userConfig.updateConfig(uuid = userConfList.first { it.name == TEST_ALT_SERVICE_NAME }.uuid!!, status = false)

        val deviceConfList = listOf(
            NotificationFilterItem(name = TEST_SERVICE_NAME, rules = TEST_RULE),
            NotificationFilterItem(name = TEST_ALT_SERVICE_NAME),
        )
        val deviceConfig = NotificationConfiguration(configs = deviceConfList)
        deviceConfig.updateConfig(uuid = userConfList.first { it.name == TEST_ALT_SERVICE_NAME }.uuid!!, status = false)

        val schema = listOf<NotificationMetaData>(TestNotificationMetaData(TEST_SERVICE_NAME), TestNotificationMetaData(TEST_ALT_SERVICE_NAME))

        val userInput = UserNotificationSettingsInput(userConfig.configs, schema)
        notificationClient.setUserNotificationConfiguration(userInput)

        val ret1 = notificationClient.getUserAndDeviceNotificationConfiguration(deviceInfoProvider)
        ret1.user shouldBe userConfig
        ret1.user?.configs shouldBe userConfig.configs
        ret1.device shouldBe null

        val deviceInput = NotificationSettingsInput(
            deviceId = deviceInfoProvider.deviceIdentifier,
            bundleId = deviceInfoProvider.bundleIdentifier,
            filter = deviceConfig.configs,
            services = schema,
        )
        notificationClient.setNotificationConfiguration(deviceInput)

        val ret2 = notificationClient.getUserAndDeviceNotificationConfiguration(deviceInfoProvider)
        ret2.user shouldBe userConfig
        ret2.user?.configs shouldBe userConfig.configs
        ret2.device shouldBe deviceConfig
        ret2.device?.configs shouldBe deviceConfig.configs

        deRegister()
    }
}
