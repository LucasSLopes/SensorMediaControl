package com.ufc.mediacontrol.sensor

import kotlin.math.abs

class GestureEngine(
    val config: Config = Config()
) {

    data class Config(
        val neutralZoneDeg: Float = 20f,
        val tiltThresholdDeg: Float = 35f,
        val tiltMinDurationMs: Long = 100L,
        val doubleTiltWindowMs: Long = 2000L,
        val neutralReturnMs: Long = 150L,
        val globalCooldownMs: Long = 300L
    )

    enum class Command {
        NEXT, PREVIOUS
    }

    private enum class Side {
        LEFT, RIGHT, NEUTRAL
    }

    private enum class TiltState {
        IDLE, FIRST_TILT, WAITING_NEUTRAL, SECOND_TILT
    }

    private var lastAnyFireMs: Long = 0L
    private var tiltState = TiltState.IDLE
    private var currentSide: Side = Side.NEUTRAL
    private var targetSide: Side? = null
    private var firstTiltStartMs: Long = 0L
    private var neutralStartMs: Long = 0L
    private var sequenceStartMs: Long = 0L

    fun onRoll(rollDeg: Float, nowMs: Long): Command? {
        if (nowMs - lastAnyFireMs < config.globalCooldownMs) return null

        val newSide = when {
            rollDeg > config.tiltThresholdDeg -> Side.RIGHT
            rollDeg < -config.tiltThresholdDeg -> Side.LEFT
            abs(rollDeg) < config.neutralZoneDeg -> Side.NEUTRAL
            else -> currentSide
        }

        val previousSide = currentSide
        currentSide = newSide

        return when (tiltState) {
            TiltState.IDLE -> handleIdleState(newSide, previousSide, nowMs)
            TiltState.FIRST_TILT -> handleFirstTiltState(newSide, previousSide, nowMs)
            TiltState.WAITING_NEUTRAL -> handleWaitingNeutralState(newSide, previousSide, nowMs)
            TiltState.SECOND_TILT -> handleSecondTiltState(newSide, previousSide, nowMs)
        }
    }

    private fun handleIdleState(newSide: Side, previousSide: Side, nowMs: Long): Command? {
        if (newSide != Side.NEUTRAL && previousSide == Side.NEUTRAL) {
            tiltState = TiltState.FIRST_TILT
            targetSide = newSide
            firstTiltStartMs = nowMs
            sequenceStartMs = nowMs
        }
        return null
    }

    private fun handleFirstTiltState(newSide: Side, previousSide: Side, nowMs: Long): Command? {
        when {
            newSide == Side.NEUTRAL && previousSide == targetSide -> {
                val tiltDuration = nowMs - firstTiltStartMs
                if (tiltDuration >= config.tiltMinDurationMs) {
                    tiltState = TiltState.WAITING_NEUTRAL
                    neutralStartMs = nowMs
                } else {
                    resetSequence()
                }
            }
            newSide != targetSide && newSide != Side.NEUTRAL -> resetSequence()
            nowMs - sequenceStartMs > config.doubleTiltWindowMs -> resetSequence()
        }
        return null
    }

    private fun handleWaitingNeutralState(newSide: Side, previousSide: Side, nowMs: Long): Command? {
        when {
            newSide == targetSide && previousSide == Side.NEUTRAL -> {
                val neutralDuration = nowMs - neutralStartMs
                if (neutralDuration >= config.neutralReturnMs &&
                    nowMs - sequenceStartMs <= config.doubleTiltWindowMs) {
                    tiltState = TiltState.SECOND_TILT
                } else {
                    resetSequence()
                }
            }
            newSide != Side.NEUTRAL && newSide != targetSide -> resetSequence()
            nowMs - sequenceStartMs > config.doubleTiltWindowMs -> resetSequence()
        }
        return null
    }

    private fun handleSecondTiltState(newSide: Side, previousSide: Side, nowMs: Long): Command? {
        return when {
            newSide == Side.NEUTRAL && previousSide == targetSide -> {
                val cmd = if (targetSide == Side.RIGHT) Command.NEXT else Command.PREVIOUS
                lastAnyFireMs = nowMs
                resetSequence()
                cmd
            }
            nowMs - sequenceStartMs > config.doubleTiltWindowMs -> {
                resetSequence()
                null
            }
            else -> null
        }
    }

    private fun resetSequence() {
        tiltState = TiltState.IDLE
        targetSide = null
        firstTiltStartMs = 0L
        neutralStartMs = 0L
        sequenceStartMs = 0L
    }
}
