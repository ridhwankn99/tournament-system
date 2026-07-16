package com.example.tournament.service

import com.example.tournament.model.*
import com.example.tournament.repository.MatchRepository
import com.example.tournament.repository.ParticipantRepository
import com.example.tournament.repository.TournamentRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import kotlin.math.log2

/**
 * Double Elimination bracket structure for bracketSize = 2^N players:
 *
 * Upper rounds:  N rounds, R1 has bracketSize/2 matches, halves each round.
 * Lower rounds:  2*(N-1) rounds.
 *   LR1  (odd)  : bracketSize/4 matches — losers from UR1 play each other (2 UR1 losers → 1 LR1 match)
 *   LR2  (even) : bracketSize/4 matches — LR1 winner (slot1) vs loser from UR2 (slot2)
 *   LR3  (odd)  : bracketSize/8 matches — LR2 winners pair up
 *   LR4  (even) : bracketSize/8 matches — LR3 winner vs loser from UR3
 *   ...
 * Grand Final: UR winner (slot1) vs LR winner (slot2)
 */
@Service
class BracketService(
    private val matchRepository: MatchRepository,
    private val participantRepository: ParticipantRepository,
    private val tournamentRepository: TournamentRepository
) {

    @Transactional
    fun generateBracket(tournamentId: Long) {
        val tournament = tournamentRepository.findById(tournamentId)
            .orElseThrow { IllegalArgumentException("Tournament not found") }

        // Clear existing matches
        matchRepository.deleteAll(
            matchRepository.findByTournamentIdOrderByBracketTypeAscRoundNumberAscMatchNumberAsc(tournamentId)
        )

        val realParticipants = participantRepository.findByTournamentIdOrderBySeedAsc(tournamentId)
            .filter { !it.isBye }
        require(realParticipants.size >= 2) { "Need at least 2 participants" }

        val bracketSize = nextPowerOfTwo(realParticipants.size)
        val N = log2(bracketSize.toDouble()).toInt()   // upper rounds count

        // Pad with BYEs
        val allSeeded = realParticipants.toMutableList()
        repeat(bracketSize - realParticipants.size) {
            allSeeded.add(participantRepository.save(Participant(
                tournament = tournament, name = "BYE",
                seed = allSeeded.size + 1, isBye = true,
                status = ParticipantStatus.ELIMINATED
            )))
        }
        val seeded = seedParticipants(allSeeded)

        // ── Upper bracket ────────────────────────────────────
        val upper = mutableMapOf<Int, MutableList<Match>>()
        // R1: bracketSize/2 matches with participants
        upper[1] = (seeded.indices step 2).mapIndexed { idx, i ->
            matchRepository.save(Match(
                tournament = tournament, bracketType = BracketType.UPPER,
                roundNumber = 1, matchNumber = idx + 1,
                participant1 = seeded[i], participant2 = seeded[i + 1],
                status = MatchStatus.READY
            ))
        }.toMutableList()
        // R2..N: empty shells
        for (r in 2..N) {
            val cnt = bracketSize shr r
            upper[r] = (1..cnt).map { mn ->
                matchRepository.save(Match(
                    tournament = tournament, bracketType = BracketType.UPPER,
                    roundNumber = r, matchNumber = mn, status = MatchStatus.PENDING
                ))
            }.toMutableList()
        }

        // ── Lower bracket ────────────────────────────────────
        // lrCount = 2*(N-1)
        // Match count per LR round:
        //   pair 0 (LR1, LR2): bracketSize/4
        //   pair 1 (LR3, LR4): bracketSize/8
        //   pair k (LR2k+1, LR2k+2): bracketSize / 2^(k+2)
        val lrCount = 2 * (N - 1)
        val lower = mutableMapOf<Int, MutableList<Match>>()
        for (lr in 1..lrCount) {
            val pairIdx = (lr - 1) / 2
            val cnt = maxOf(1, bracketSize shr (pairIdx + 2))
            lower[lr] = (1..cnt).map { mn ->
                matchRepository.save(Match(
                    tournament = tournament, bracketType = BracketType.LOWER,
                    roundNumber = lr, matchNumber = mn, status = MatchStatus.PENDING
                ))
            }.toMutableList()
        }

        // ── Grand Final ──────────────────────────────────────
        val gf = matchRepository.save(Match(
            tournament = tournament, bracketType = BracketType.GRAND_FINAL,
            roundNumber = 1, matchNumber = 1, status = MatchStatus.PENDING
        ))

        // ── Wire upper bracket ───────────────────────────────
        for (r in 1..N) {
            upper[r]?.forEach { m ->
                val idx = m.matchNumber - 1   // 0-based

                // Winner → next upper round or grand final
                if (r < N) {
                    upper[r + 1]?.getOrNull(idx / 2)?.also { next ->
                        m.nextMatchWinnerId    = next.id
                        m.nextMatchWinnerSlot  = if (idx % 2 == 0) 1 else 2
                    }
                } else {
                    m.nextMatchWinnerId   = gf.id
                    m.nextMatchWinnerSlot = 1
                }

                // Loser → lower bracket
                //   UR1 → LR1 :  two UR1 losers share one LR1 match
                //                match pair (0,1) → LR1[0], pair (2,3) → LR1[1], etc.
                //                but we need slot assignment:
                //                  within each pair: first loser (even idx) → slot 1, second (odd) → slot 2
                //   UR2 → LR2 :  one-to-one, loser idx → LR2[idx], slot 2 (slot 1 = LR1 winner)
                //   UR3 → LR4 :  one-to-one, loser idx → LR4[idx], slot 2
                //   URr → LR(2*(r-1)) for r >= 2
                val targetLR: Int
                val lrIdx: Int
                val lrSlot: Int

                if (r == 1) {
                    targetLR = 1
                    lrIdx    = idx / 2           // pairs: (0,1)→0, (2,3)→1 …
                    lrSlot   = if (idx % 2 == 0) 1 else 2
                } else {
                    targetLR = 2 * (r - 1)
                    lrIdx    = idx.coerceIn(0, (lower[targetLR]?.size ?: 1) - 1)
                    lrSlot   = 2                 // slot 1 = LR survivor from previous round
                }

                lower[targetLR]?.getOrNull(lrIdx)?.also { lm ->
                    m.nextMatchLoserId   = lm.id
                    m.nextMatchLoserSlot = lrSlot
                }

                matchRepository.save(m)
            }
        }

        // ── Wire lower bracket ───────────────────────────────
        // Odd LR rounds (reduction): winner → same-index match in next round, slot 1
        // Even LR rounds (consolidation): bracket halves; winner → index/2, slot by parity
        for (lr in 1..lrCount) {
            lower[lr]?.forEach { m ->
                val idx = m.matchNumber - 1

                if (lr < lrCount) {
                    val nextRound = lower[lr + 1] ?: return@forEach
                    if (lr % 2 == 1) {
                        // Odd → same count next round
                        nextRound.getOrNull(idx)?.also { next ->
                            m.nextMatchWinnerId   = next.id
                            m.nextMatchWinnerSlot = 1
                        }
                    } else {
                        // Even → bracket halves
                        nextRound.getOrNull(idx / 2)?.also { next ->
                            m.nextMatchWinnerId   = next.id
                            m.nextMatchWinnerSlot = if (idx % 2 == 0) 1 else 2
                        }
                    }
                } else {
                    m.nextMatchWinnerId   = gf.id
                    m.nextMatchWinnerSlot = 2
                }

                matchRepository.save(m)
            }
        }

        // ── Auto-complete BYE matches ────────────────────────
        // Re-fetch R1 to get fresh wired state, then auto-advance BYEs
        val freshR1 = matchRepository.findByTournamentIdAndBracketTypeAndRoundNumber(
            tournamentId, BracketType.UPPER, 1
        ).sortedBy { it.matchNumber }
        freshR1.forEach { processAutoAdvance(it) }

        tournament.status = TournamentStatus.ONGOING
        tournamentRepository.save(tournament)
    }

    // ── BYE / auto-advance ───────────────────────────────────

    private fun processAutoAdvance(match: Match) {
        val p1 = match.participant1 ?: return
        val p2 = match.participant2 ?: return
        if (!p1.isBye && !p2.isBye) return

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

        val winner: Participant = if (p1.isBye) p2 else p1
        val loser:  Participant = if (p1.isBye) p1 else p2
        match.winner = winner
        match.loser  = loser
        match.status = MatchStatus.COMPLETED
        matchRepository.save(match)

        // Advance winner and (for upper bracket) route loser to lower slot.
        advanceParticipant(match.nextMatchWinnerId, match.nextMatchWinnerSlot ?: 1, winner)
        if (match.bracketType == BracketType.UPPER) {
            advanceParticipant(match.nextMatchLoserId, match.nextMatchLoserSlot ?: 2, loser)
        }
    }

    private fun checkReady(m: Match) {
        val p1 = m.participant1 ?: return
        val p2 = m.participant2 ?: return
        if (p1.isBye || p2.isBye) {
            processAutoAdvance(m)
        } else {
            m.status = MatchStatus.READY
        }
    }

    private fun advanceParticipant(nextMatchId: Long?, slot: Int, participant: Participant) {
        nextMatchId ?: return
        val nextMatch = matchRepository.findById(nextMatchId).orElse(null) ?: return
        if (slot == 1) nextMatch.participant1 = participant else nextMatch.participant2 = participant
        checkReady(nextMatch)
        matchRepository.save(nextMatch)
    }

    // ── Utilities ────────────────────────────────────────────

    private fun nextPowerOfTwo(n: Int): Int {
        var p = 1; while (p < n) p = p shl 1; return p
    }

    private fun seedParticipants(participants: List<Participant>): List<Participant> {
        val sortedBySeed = participants.sortedBy { it.seed }
        val slotSeedOrder = generateBracketSeedOrder(participants.size)
        return slotSeedOrder.map { seedNumber ->
            sortedBySeed[seedNumber - 1]
        }
    }

    /**
     * Standard tournament seeding order per slot.
     *
     * Example:
     *  - n=4  => [1,4,2,3]
     *  - n=8  => [1,8,4,5,2,7,3,6]
     */
    private fun generateBracketSeedOrder(n: Int): List<Int> {
        var order = listOf(1, 2)
        while (order.size < n) {
            val mirrorBase = order.size * 2 + 1
            order = order.flatMap { listOf(it, mirrorBase - it) }
        }
        return order
    }
}
