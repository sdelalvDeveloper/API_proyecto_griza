package com.es.API_REST_SEGURA.model

import org.bson.codecs.pojo.annotations.BsonId
import org.bson.types.ObjectId
import org.springframework.data.mongodb.core.mapping.Document

@Document("Usuario")
data class Usuario(
    @BsonId
    val _id : ObjectId? = null,
    val username: String,
    var password: String,
    val email: String,
    val telefono: String,
    val direccion: Direccion,
    val roles: String? = "USER"
)