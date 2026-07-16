package com.example.tournament.service

import com.example.tournament.model.*
import com.example.tournament.repository.ParticipantRepository
import com.example.tournament.repository.TournamentRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class TournamentService(
    private val tournamentRepository: TournamentRepository,
    private val participantRepository: ParticipantRepository
) {

    fun findAll(): List<Tournament> = tournamentRepository.findAll()

    fun findById(id: Long): Tournament =
        tournamentRepository.findById(id).orElseThrow { IllegalArgumentException("Tournament not found: $id") }

    @Transactional
    fun createTournament(name: String, participantNames: List<String>): Tournament {
        val tournament = Tournament(name = name, type = TournamentType.DOUBLE_ELIMINATION)
        val saved = tournamentRepository.save(tournament)

        participantNames.forEachIndexed { idx, participantName ->
            val participant = Participant(
                tournament = saved,
                name = participantName.trim(),
                seed = idx + 1
            )
            participantRepository.save(participant)
        }

        return saved
    }

    @Transactional
    fun deleteParticipant(tournamentId: Long, participantId: Long) {
        val tournament = findById(tournamentId)
        require(tournament.status == TournamentStatus.NOT_STARTED) {
            "Cannot edit participants after bracket is generated"
        }
        participantRepository.deleteById(participantId)
    }

    @Transactional
    fun shuffleParticipants(tournamentId: Long) {
        val tournament = findById(tournamentId)
        require(tournament.status == TournamentStatus.NOT_STARTED) {
            "Cannot shuffle after bracket is generated"
        }
        val participants = participantRepository.findByTournamentIdOrderBySeedAsc(tournamentId)
            .filter { !it.isBye }
            .shuffled()
        participants.forEachIndexed { idx, p ->
            p.seed = idx + 1
            participantRepository.save(p)
        }
    }

    fun getParticipants(tournamentId: Long): List<Participant> =
        participantRepository.findByTournamentIdOrderBySeedAsc(tournamentId)
            .filter { !it.isBye }
}
