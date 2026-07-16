package com.example.tournament

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class TournamentApplication

fun main(args: Array<String>) {
    runApplication<TournamentApplication>(*args)
}
