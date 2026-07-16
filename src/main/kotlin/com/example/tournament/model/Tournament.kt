package com.example.tournament.model

import jakarta.persistence.*
import java.time.LocalDateTime

@Entity
@Table(name = "tournaments")
data class Tournament(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Column(nullable = false)
    var name: String = "",

    @Enumerated(EnumType.STRING)
    var type: TournamentType = TournamentType.DOUBLE_ELIMINATION,

    @Enumerated(EnumType.STRING)
    var status: TournamentStatus = TournamentStatus.NOT_STARTED,

    @Column(name = "created_at")
    val createdAt: LocalDateTime = LocalDateTime.now(),

    @OneToMany(mappedBy = "tournament", cascade = [CascadeType.ALL], fetch = FetchType.LAZY)
    val participants: MutableList<Participant> = mutableListOf(),

    @OneToMany(mappedBy = "tournament", cascade = [CascadeType.ALL], fetch = FetchType.LAZY)
    val matches: MutableList<Match> = mutableListOf()
)
