package com.example.tournament.model

import jakarta.persistence.*

@Entity
@Table(name = "participants")
data class Participant(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tournament_id", nullable = false)
    var tournament: Tournament? = null,

    @Column(nullable = false)
    var name: String = "",

    var seed: Int = 0,

    @Enumerated(EnumType.STRING)
    var status: ParticipantStatus = ParticipantStatus.ACTIVE,

    /** true = BYE placeholder, not a real participant */
    var isBye: Boolean = false
)
