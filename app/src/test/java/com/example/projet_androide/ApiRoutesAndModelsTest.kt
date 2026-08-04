package com.example.projet_androide

import com.example.projet_androide.data.api.ApiRoutes
import com.example.projet_androide.data.model.Device
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ApiRoutesAndModelsTest {

    @Test
    fun deviceRoutesUseConfiguredBaseAndHouseId() {
        assertEquals(
            "${ApiRoutes.BASE}/api/houses/42/devices",
            ApiRoutes.DEVICES(42)
        )
        assertEquals(
            "${ApiRoutes.BASE}/api/houses/42/users",
            ApiRoutes.HOUSE_USERS(42)
        )
    }

    @Test
    fun deviceCommandRoutePreservesDeviceAndCommandSegments() {
        assertEquals(
            "${ApiRoutes.BASE}/api/houses/7/devices/light-1/command/turn%20on",
            ApiRoutes.DEVICE_COMMAND_PATH(7, "light-1", "turn%20on")
        )
    }

    @Test
    fun deviceModelReportsPowerState() {
        val device = Device(
            id = "light-1",
            type = "light",
            availableCommands = listOf("on", "off"),
            power = 100
        )

        assertEquals("light (light-1) power=100", device.toString())
        assertTrue(device.availableCommands.contains("off"))
    }
}
