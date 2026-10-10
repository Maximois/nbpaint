package com.mc.nbpaint.engine

/**
 * Que el historial guarde un punto por gesto y no uno por movimiento.
 *
 * Mover o escalar la imagen puesta se hace con el dedo encima, y cada movimiento genera un
 * evento. Si cada uno guardara su estado, una sola arrastrada dejaría cincuenta pasos de
 * deshacer y el usuario tendría que pulsarlos todos para volver atrás, sin poder deshacer
 * "a medias".
 *
 * La regla es sencilla: el primer movimiento del gesto guarda el estado previo, y los
 * siguientes no. Al soltar el dedo se rearma para el siguiente gesto.
 *
 * No depende de nada de Android, así que se prueba en la JVM.
 */
class GestureUndo {

    private var armed = true

    /**
     * Devuelve si hay que guardar el estado previo, y se desarma.
     *
     * Así el mismo booleano sirve de pregunta y de aviso: el que la llama ya sabe que
     * empuje el historial, y solo al primer movimiento del gesto le sale `true`.
     */
    fun pushOnce(): Boolean {
        if (!armed) return false
        armed = false
        return true
    }

    /** Rearma para el siguiente gesto. Se llama al soltar el dedo. */
    fun rearm() {
        armed = true
    }
}
