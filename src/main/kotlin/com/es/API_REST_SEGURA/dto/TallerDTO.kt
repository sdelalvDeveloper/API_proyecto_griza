package com.es.API_REST_SEGURA.dto

import com.es.API_REST_SEGURA.model.EstadoTaller
import java.util.Date

data class TallerDTO(
    val titulo: String,
    val descripcion: String,
    val fecha: Date,
    val estado: EstadoTaller
)
