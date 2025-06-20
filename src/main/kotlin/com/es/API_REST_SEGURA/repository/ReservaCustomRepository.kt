package com.es.API_REST_SEGURA.repository

import com.es.API_REST_SEGURA.model.Reserva
import org.bson.types.ObjectId

interface ReservaCustomRepository {

    fun getReservaByUsername(username: String): List<Reserva>

    fun getAll(): List<Reserva>

    fun getReservaById(id: ObjectId): Reserva?

    fun getReservaByIdTaller(idTaller: ObjectId): List<Reserva>

    fun updateReserva(id: ObjectId, nuevaReserva: Reserva): Boolean

    fun deleteReservaById(id: ObjectId): Boolean
}