package com.es.API_REST_SEGURA.dto

import com.es.API_REST_SEGURA.model.EstadoReserva
import java.util.*

data class ReservaDTO(
    val id: String,
    val tallerID: String,
    val estado: EstadoReserva,
    val fechaTaller: Date
)
