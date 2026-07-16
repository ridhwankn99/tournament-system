package com.example.tournament.service

import com.example.tournament.model.*
import com.example.tournament.repository.MatchRepository
import com.example.tournament.repository.ParticipantRepository
import com.example.tournament.repository.TournamentRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class MatchService(
    private val matchRepository: MatchRepository,
    private val participantRepository: ParticipantRepository,
    private val tournamentRepository: TournamentRepository
) {

    fun getMatchesForTournament(tournamentId: Long): List<Match> =
        matchRepository.findByTournamentIdOrderByBracketTypeAscRoundNumberAscMatchNumberAsc(tournamentId)

    fun getMatchById(matchId: Long): Match =
        matchRepository.findById(matchId).orElseThrow { IllegalArgumentException("Match not found: $matchId") }

    /**
     * Submit match result.
     * winnerId can be null — if scores are provided and one is higher,
     * winner is derived automatically from the score.
     */
    @Transactional
    fun submitResult(matchId: Long, winnerId: Long?, score1: Int?, score2: Int?) {
        val match = matchRepository.findById(matchId)
            .orElseThrow { IllegalArgumentException("Match not found") }

        // If already completed, reset it first before applying new result
        if (match.status == MatchStatus.COMPLETED) {
            resetMatch(match)
        }

        val p1 = match.participant1 ?: throw IllegalArgumentException("No participant 1")
        val p2 = match.participant2 ?: throw IllegalArgumentException("No participant 2")

        // Determine winner: explicit selection wins, otherwise derive from score
        val resolvedWinnerId: Long = when {
            winnerId != null && winnerId != 0L -> winnerId
            score1 != null && score2 != null && score1 != score2 ->
                if (score1 > score2) p1.id else p2.id
            else -> throw IllegalArgumentException(
                "Pilih pemenang atau masukkan skor yang berbeda"
            )
        }

        val winner = participantRepository.findById(resolvedWinnerId)
            .orElseThrow { IllegalArgumentException("Participant not found") }
        val loser: Participant = if (p1.id == resolvedWinnerId) p2 else p1

        match.winner = winner
        match.loser  = loser
        match.score1 = score1
        match.score2 = score2
        match.status = MatchStatus.COMPLETED
        matchRepository.save(match)

        // Grand Final: crown champion
        if (match.bracketType == BracketType.GRAND_FINAL) {
            winner.status = ParticipantStatus.CHAMPION
            loser.status  = ParticipantStatus.ELIMINATED
            participantRepository.save(winner)
            participantRepository.save(loser)
            val t = match.tournament!!
            t.status = TournamentStatus.FINISHED
            tournamentRepository.save(t)
            return
        }

        // Advance winner to next match
        advanceParticipant(match.nextMatchWinnerId, match.nextMatchWinnerSlot ?: 1, winner)

        // Handle loser
        when (match.bracketType) {
            BracketType.UPPER -> {
                loser.status = ParticipantStatus.LOWER_BRACKET
                participantRepository.save(loser)
                advanceParticipant(match.nextMatchLoserId, match.nextMatchLoserSlot ?: 2, loser)
            }
            BracketType.LOWER -> {
                loser.status = ParticipantStatus.ELIMINATED
                participantRepository.save(loser)
            }
            BracketType.GRAND_FINAL -> { /* handled above */ }
        }
    }

    /** Place a participant into a slot of the specified match, then check readiness. */
    private fun advanceParticipant(nextMatchId: Long?, slot: Int, participant: Participant) {
        nextMatchId ?: return
        val nextMatch = matchRepository.findById(nextMatchId).orElse(null) ?: return

        if (slot == 1) nextMatch.participant1 = participant
        else           nextMatch.participant2 = participant

        checkAndActivate(nextMatch)
        matchRepository.save(nextMatch)
    }

    /**
     * Check if a match has both participants.
     * - If one is BYE → auto-complete and propagate.
     * - If both real → mark READY.
     */
    private fun checkAndActivate(match: Match) {
        val p1 = match.participant1
        val p2 = match.participant2

        if (p1 == null || p2 == null) {
            match.status = MatchStatus.PENDING
            return
        }

        if (p1.isBye && p2.isBye) {
            val winner = p1
            val loser = p2
            match.winner = winner
            match.loser = loser
            match.status = MatchStatus.COMPLETED
            matchRepository.save(match)
            advanceParticipant(match.nextMatchWinnerId, match.nextMatchWinnerSlot ?: 1, winner)
            if (match.bracketType == BracketType.UPPER) {
                advanceParticipant(match.nextMatchLoserId, match.nextMatchLoserSlot ?: 2, loser)
            }
            return
        }

        if (p1.isBye || p2.isBye) {
            val winner: Participant = if (p1.isBye) p2 else p1
            val loser:  Participant = if (p1.isBye) p1 else p2
            match.winner = winner
            match.loser  = loser
            match.status = MatchStatus.COMPLETED
            matchRepository.save(match)
            // Propagate BYE win
            advanceParticipant(match.nextMatchWinnerId, match.nextMatchWinnerSlot ?: 1, winner)
            if (match.bracketType == BracketType.UPPER) {
                advanceParticipant(match.nextMatchLoserId, match.nextMatchLoserSlot ?: 2, loser)
            }
        } else {
            match.status = MatchStatus.READY
        }
    }

    /** Public entry point — reset a single match to READY for re-editing */
    @Transactional
    fun resetToReady(matchId: Long) {
        val match = matchRepository.findById(matchId)
            .orElseThrow { IllegalArgumentException("Match not found") }
        require(match.status == MatchStatus.COMPLETED) { "Match is not completed" }
        require(match.participant1?.isBye != true && match.participant2?.isBye != true) {
            "Cannot edit a BYE match"
        }
        resetMatch(match)
    }

    /**
     * Reset a completed match back to READY and undo all downstream effects:
     * - Remove winner/loser from next matches (set slot back to null, status to PENDING)
     * - Cascade reset any downstream matches that were also completed as a result
     * - Revert participant statuses
     */
    private fun resetMatch(match: Match) {
        val prevWinner = match.winner ?: return
        val prevLoser  = match.loser  ?: return

        // Undo winner advancement
        match.nextMatchWinnerId?.let { nid ->
            matchRepository.findById(nid).orElse(null)?.let { nm ->
                // If next match was completed downstream, reset it first (recursive)
                if (nm.status == MatchStatus.COMPLETED) resetMatch(nm)
                // Remove this match's contribution from the slot
                val slot = match.nextMatchWinnerSlot ?: 1
                if (slot == 1 && nm.participant1?.id == prevWinner.id) nm.participant1 = null
                if (slot == 2 && nm.participant2?.id == prevWinner.id) nm.participant2 = null
                nm.status = MatchStatus.PENDING
                matchRepository.save(nm)
            }
        }

        // Undo loser placement
        match.nextMatchLoserId?.let { lid ->
            matchRepository.findById(lid).orElse(null)?.let { lm ->
                if (lm.status == MatchStatus.COMPLETED) resetMatch(lm)
                val slot = match.nextMatchLoserSlot ?: 2
                if (slot == 1 && lm.participant1?.id == prevLoser.id) lm.participant1 = null
                if (slot == 2 && lm.participant2?.id == prevLoser.id) lm.participant2 = null
                lm.status = MatchStatus.PENDING
                matchRepository.save(lm)
            }
        }

        // Revert participant statuses
        when (match.bracketType) {
            BracketType.UPPER -> {
                // Winner stays ACTIVE (was already active before this match)
                // Loser was moved to LOWER_BRACKET — move back to ACTIVE
                if (!prevLoser.isBye) {
                    prevLoser.status = ParticipantStatus.ACTIVE
                    participantRepository.save(prevLoser)
                }
            }
            BracketType.LOWER -> {
                // Loser was ELIMINATED — move back to LOWER_BRACKET
                if (!prevLoser.isBye) {
                    prevLoser.status = ParticipantStatus.LOWER_BRACKET
                    participantRepository.save(prevLoser)
                }
            }
            BracketType.GRAND_FINAL -> {
                // Undo champion + tournament finish
                if (!prevWinner.isBye) {
                    prevWinner.status = ParticipantStatus.ACTIVE
                    participantRepository.save(prevWinner)
                }
                if (!prevLoser.isBye) {
                    prevLoser.status = ParticipantStatus.LOWER_BRACKET
                    participantRepository.save(prevLoser)
                }
                match.tournament?.let { t ->
                    t.status = TournamentStatus.ONGOING
                    tournamentRepository.save(t)
                }
            }
        }

        // Reset the match itself back to READY (both participants still here)
        match.winner = null
        match.loser  = null
        match.score1 = null
        match.score2 = null
        match.status = MatchStatus.READY
        matchRepository.save(match)
    }

    fun getGroupedMatches(tournamentId: Long): BracketView {
        val all = matchRepository.findByTournamentIdOrderByBracketTypeAscRoundNumberAscMatchNumberAsc(tournamentId)
        return BracketView(
            upperRounds = all.filter { it.bracketType == BracketType.UPPER }
                             .groupBy { it.roundNumber }.toSortedMap(),
            lowerRounds = all.filter { it.bracketType == BracketType.LOWER }
                             .groupBy { it.roundNumber }.toSortedMap(),
            grandFinal  = all.firstOrNull { it.bracketType == BracketType.GRAND_FINAL }
        )
    }
}

data class BracketView(
    val upperRounds: Map<Int, List<Match>>,
    val lowerRounds: Map<Int, List<Match>>,
    val grandFinal: Match?
)
