package com.example.tournament.repository

import com.example.tournament.model.*
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository

@Repository
interface TournamentRepository : JpaRepository<Tournament, Long>

@Repository
interface ParticipantRepository : JpaRepository<Participant, Long> {
    fun findByTournamentIdOrderBySeedAsc(tournamentId: Long): List<Participant>
    fun countByTournamentId(tournamentId: Long): Long
}

@Repository
interface MatchRepository : JpaRepository<Match, Long> {
    fun findByTournamentIdOrderByBracketTypeAscRoundNumberAscMatchNumberAsc(tournamentId: Long): List<Match>

    fun findByTournamentIdAndBracketTypeOrderByRoundNumberAscMatchNumberAsc(
        tournamentId: Long,
        bracketType: BracketType
    ): List<Match>

    fun findByTournamentIdAndBracketTypeAndRoundNumber(
        tournamentId: Long,
        bracketType: BracketType,
        roundNumber: Int
    ): List<Match>

    @Query("SELECT m FROM Match m WHERE m.tournament.id = :tournamentId AND m.status != 'COMPLETED'")
    fun findIncompleteByTournamentId(tournamentId: Long): List<Match>
}
