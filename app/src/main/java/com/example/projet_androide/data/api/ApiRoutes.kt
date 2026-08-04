package com.example.projet_androide.data.api

import com.example.projet_androide.BuildConfig

object ApiRoutes {
    val BASE = BuildConfig.POLYHOME_API_BASE_URL
    val REGISTER = "$BASE/api/users/register"
    val AUTH = "$BASE/api/users/auth"

    val HOUSES = "$BASE/api/houses"
    fun HOUSE_USERS(houseId: Int) = "$BASE/api/houses/$houseId/users"
    fun DEVICES(houseId: Int) = "$BASE/api/houses/$houseId/devices"
    fun DEVICE_COMMAND_PATH(houseId: Int, deviceId: String, command: String) =
        "$BASE/api/houses/$houseId/devices/$deviceId/command/$command"

    fun DEVICE_COMMANDS_PATH(houseId: Int, deviceId: String, command: String) =
        "$BASE/api/houses/$houseId/devices/$deviceId/commands/$command"

    fun DEVICE_COMMAND_QUERY(houseId: Int, deviceId: String, command: String) =
        "$BASE/api/houses/$houseId/devices/$deviceId?command=$command"

    fun DEVICE_COMMAND(houseId: Int, deviceId: String) =
        "$BASE/api/houses/$houseId/devices/$deviceId/command"

    fun DEVICE_COMMANDS(houseId: Int, deviceId: String) =
        "$BASE/api/houses/$houseId/devices/$deviceId/commands"

    fun DEVICE(houseId: Int, deviceId: String) =
        "$BASE/api/houses/$houseId/devices/$deviceId"

    fun HOUSE_BROWSER(houseId: Int) = "$BASE?houseId=$houseId"
}
