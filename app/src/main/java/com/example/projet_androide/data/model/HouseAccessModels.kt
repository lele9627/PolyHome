package com.example.projet_androide.data.model

data class HouseUserPayload(
    val userLogin: String
)

data class HouseAccessUser(
    val userLogin: String,
    val owner: Int = 0
)
