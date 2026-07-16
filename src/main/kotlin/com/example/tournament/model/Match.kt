package com.example.tournament.model

import jakarta.persistence.*

@Entity
@Table(name = "matches")
data class Match(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tournament_id", nullable = false)
    var tournament: Tournament? = null,

    @Enumerated(EnumType.STRING)
    @Column(name = "bracket_type")
    var bracketType: BracketType = BracketType.UPPER,

    @Column(name = "round_number")
    var roundNumber: Int = 0,

    @Column(name = "match_number")
    var matchNumber: Int = 0,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "participant1_id")
    var participant1: Participant? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "participant2_id")
    var participant2: Participant? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "winner_id")
    var winner: Participant? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "loser_id")
    var loser: Participant? = null,

    var score1: Int? = null,
    var score2: Int? = null,

    @Enumerated(EnumType.STRING)
    var status: MatchStatus = MatchStatus.PENDING,

    /**
     * ID of the next match in the winner's progression path.
     * null if this match has no subsequent match (e.g., Grand Final).
     */
    @Column(name = "next_match_winner_id")
    var nextMatchWinnerId: Long? = null,

    /**
     * ID of the next match in the loser's progression path (for upper bracket losers
     * dropping to lower bracket).
     * null for lower bracket matches (loser is eliminated).
     */
    @Column(name = "next_match_loser_id")
    var nextMatchLoserId: Long? = null,

    /**
     * Which slot (1 or 2) in the next winner match this match's winner will fill.
     */
    @Column(name = "next_match_winner_slot")
    var nextMatchWinnerSlot: Int? = null,

    /**
     * Which slot (1 or 2) in the next loser match this match's loser will fill.
     */
    @Column(name = "next_match_loser_slot")
    var nextMatchLoserSlot: Int? = null
)
