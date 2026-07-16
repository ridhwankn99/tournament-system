package com.example.tournament.model

enum class TournamentStatus {
    NOT_STARTED, ONGOING, FINISHED
}

enum class TournamentType {
    DOUBLE_ELIMINATION
}

enum class ParticipantStatus {
    ACTIVE, LOWER_BRACKET, ELIMINATED, CHAMPION
}

enum class BracketType {
    UPPER, LOWER, GRAND_FINAL
}

enum class MatchStatus {
    PENDING, READY, COMPLETED
}
