package com.es.API_REST_SEGURA.service

import com.es.API_REST_SEGURA.dto.ReservaDTO
import com.es.API_REST_SEGURA.dto.ReservaFullDTO
import com.es.API_REST_SEGURA.dto.ReservaRegisterDTO
import com.es.API_REST_SEGURA.error.exception.BadRequestException
import com.es.API_REST_SEGURA.error.exception.ForbiddenException
import com.es.API_REST_SEGURA.error.exception.NotFoundException
import com.es.API_REST_SEGURA.error.exception.UnauthorizedException
import com.es.API_REST_SEGURA.model.EstadoReserva
import com.es.API_REST_SEGURA.repository.ReservaRepository
import com.es.API_REST_SEGURA.util.DtoMapper
import org.bson.types.ObjectId
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.security.core.Authentication
import org.springframework.stereotype.Service

@Service
class ReservaService {

    @Autowired
    private lateinit var reservaRepository: ReservaRepository

    @Autowired
    private lateinit var tallerService: TallerService

    @Autowired
    private lateinit var usuarioService: UsuarioService

    private val dtoMapper = DtoMapper()

    fun getReservaByUsername(username: String, authentication: Authentication): List<ReservaFullDTO> {
        val reservas =  if (authentication.authorities.any { it.authority == "ROLE_ADMIN" }) {
            reservaRepository.getAll()
        } else {
            reservaRepository.getReservaByUsername(username.lowercase())
        }

        return reservas.map { reserva ->
            // Obtener taller para incluir su título
            val taller = tallerService.getTallerById(reserva.tallerID)
            taller.let {
                ReservaFullDTO(
                    id = reserva.id.toString(),
                    username = reserva.username,
                    tituloTaller = it.titulo,
                    tallerID = it.id.toString(),
                    estado = reserva.estado,
                    fechaTaller = it.fecha
                )
            }
        }.sortedBy { it.fechaTaller }
    }

    fun getAll(): List<ReservaDTO> {
        val reservas = reservaRepository.getAll().map { reserva ->
            dtoMapper.reservaEntityToDTO(reserva)
        }
        return reservas
    }

    fun insertReserva(reservaDTO: ReservaRegisterDTO, authentication: Authentication): ReservaFullDTO? {
        // Verifica que el usuario esté autenticado (ya que Authentication está presente)
        val username = authentication.name

        // Mapea DTO a entidad y asegura que el username venga del token para evitar spoofing
        val reservaEntity = dtoMapper.reservaDTOToEntity(reservaDTO).copy(username = username)

        val usuario = usuarioService.getUserEntity(username)
        if (usuario.bono == 0) {
            throw BadRequestException("Bono insuficiente")
        }

        // Busca el taller para validar plazas disponibles antes de guardar
        val tallerEntity = tallerService.getTallerById(reservaEntity.tallerID)

        // Validar que aún haya plazas disponibles
        if (tallerEntity.plazas <= 0) {
            throw RuntimeException("No hay plazas disponibles en este taller.")
        }

        // Restar uno al bono
        val usuarioActualizado = usuario.copy(bono = usuario.bono - 1)
        usuarioService.updateUser(username, usuarioActualizado)

        // Guarda la reserva
        val reservaGuardada = reservaRepository.save(reservaEntity)

        // Actualiza la lista de reservas del taller
        val reservasActualizadas = tallerEntity.reservas.toMutableList().apply {
            add(reservaGuardada)
        }

        // Calcular nuevas plazas y estado
        var plazasRestantes = (tallerEntity.plazas - 1).coerceAtLeast(0)
        if (tallerEntity.reservas.size == 6) {
            plazasRestantes = 0
        }

        // Actualiza estado del taller
        val estadoActualizado = tallerService.cambiarEstadoTaller(plazasRestantes)

        // Guarda los cambios en el taller
        val tallerActualizado = tallerEntity.copy(
            reservas = reservasActualizadas,
            plazas = plazasRestantes,
            estado = estadoActualizado
        )

        tallerEntity.id?.let { tallerService.addReservaTaller(it, tallerActualizado) }

        return dtoMapper.reservaEntityToFullDTO(reservaGuardada, tallerActualizado)
    }


    fun deleteReservaById(id: ObjectId, tallerID: ObjectId, authentication: Authentication) {
        val reserva = reservaRepository.getReservaById(id)
            ?: throw NotFoundException("No se ha encontrado ninguna reserva con este id: $id")

        // Cambiar estado a CANCELADA
        val reservaCancelada = reserva.copy(estado = EstadoReserva.CANCELADA)
        reservaRepository.save(reservaCancelada)

        reserva.id?.let { tallerService.updateReservasTaller(it, reserva.tallerID) }
        reserva.id?.let { reservaRepository.deleteReservaById(it) }

        val usuario = usuarioService.getUserEntity(authentication.name)

        // Sumar uno al bono
        val usuarioActualizado = usuario.copy(bono = usuario.bono + 1)
        usuarioService.updateUser(usuario.username, usuarioActualizado)
    }

    fun deleteReservaByIdTaller(tallerID: ObjectId, authentication: Authentication) {
        val reservas = reservaRepository.getReservaByIdTaller(tallerID)

        if(reservas.isNotEmpty()) {
            reservas.forEach { reserva ->
                reserva.id?.let { reservaRepository.deleteReservaById(it) }
                val usuario = usuarioService.getUserEntity(reserva.username)

                // Sumar uno al bono
                val usuarioActualizado = usuario.copy(bono = usuario.bono + 1)
                usuarioService.updateUser(usuario.username, usuarioActualizado)
            }
        } else {
            throw ForbiddenException("No había reservas para el taller.")
        }
    }

    fun deleteAll(username: String, authentication: Authentication) {
        val reservas = if (authentication.name == username || authentication.authorities.any { it.authority == "ROLE_ADMIN" }) {
            getReservaByUsername(username, authentication)
        } else {
            throw UnauthorizedException("No tiene permiso para eliminar las reservas")
        }

        reservas.forEach { reserva ->
            try {
                tallerService.updateReservasTaller(ObjectId(reserva.id), ObjectId(reserva.tallerID))
                reserva.id.let { reservaRepository.deleteReservaById(ObjectId(reserva.id)) }
            } catch (e: Exception) {
                throw BadRequestException("Error al eliminar reservas")
            }
        }
    }



}