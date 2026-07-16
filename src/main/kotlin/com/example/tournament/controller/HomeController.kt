package com.example.tournament.controller

import com.example.tournament.service.TournamentService
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping

@Controller
class HomeController(private val tournamentService: TournamentService) {

    @GetMapping("/")
    fun home(model: Model): String {
        model.addAttribute("tournaments", tournamentService.findAll())
        return "index"
    }
}
