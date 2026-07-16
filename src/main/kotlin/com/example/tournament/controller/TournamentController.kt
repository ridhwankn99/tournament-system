package com.example.tournament.controller

import com.example.tournament.service.BracketService
import com.example.tournament.service.MatchService
import com.example.tournament.service.TournamentService
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.*
import org.springframework.web.servlet.mvc.support.RedirectAttributes

@Controller
@RequestMapping("/tournaments")
class TournamentController(
    private val tournamentService: TournamentService,
    private val bracketService: BracketService,
    private val matchService: MatchService
) {

    @GetMapping("/create")
    fun createForm(): String = "tournament/create"

    @PostMapping("/create")
    fun createTournament(
        @RequestParam name: String,
        @RequestParam participants: String,
        redirectAttributes: RedirectAttributes
    ): String {
        val names = participants.lines()
            .map { it.trim() }
            .filter { it.isNotBlank() }

        if (names.size < 2) {
            redirectAttributes.addFlashAttribute("error", "Minimal 2 peserta diperlukan")
            return "redirect:/tournaments/create"
        }

        val tournament = tournamentService.createTournament(name.trim(), names)
        return "redirect:/tournaments/${tournament.id}"
    }

    @GetMapping("/{id}")
    fun detail(@PathVariable id: Long, model: Model): String {
        val tournament = tournamentService.findById(id)
        val participants = tournamentService.getParticipants(id)
        val bracketView = matchService.getGroupedMatches(id)

        // Pass champion name directly so Thymeleaf doesn't need Java stream
        val champion = participants.firstOrNull { it.status.name == "CHAMPION" }

        model.addAttribute("tournament", tournament)
        model.addAttribute("participants", participants)
        model.addAttribute("bracketView", bracketView)
        model.addAttribute("champion", champion)

        return "tournament/detail"
    }

    @GetMapping("/{id}/view")
    fun publicView(@PathVariable id: Long, model: Model): String {
        val tournament = tournamentService.findById(id)
        val participants = tournamentService.getParticipants(id)
        val bracketView = matchService.getGroupedMatches(id)
        val champion = participants.firstOrNull { it.status.name == "CHAMPION" }

        model.addAttribute("tournament", tournament)
        model.addAttribute("participants", participants)
        model.addAttribute("bracketView", bracketView)
        model.addAttribute("champion", champion)

        return "tournament/view"
    }
    @PostMapping("/{id}/shuffle")
    fun shuffleParticipants(@PathVariable id: Long, redirectAttributes: RedirectAttributes): String {
        return try {
            tournamentService.shuffleParticipants(id)
            redirectAttributes.addFlashAttribute("success", "Urutan peserta berhasil diacak!")
            "redirect:/tournaments/$id"
        } catch (e: Exception) {
            redirectAttributes.addFlashAttribute("error", e.message)
            "redirect:/tournaments/$id"
        }
    }

    @PostMapping("/{id}/generate")
    fun generateBracket(@PathVariable id: Long, redirectAttributes: RedirectAttributes): String {
        return try {
            bracketService.generateBracket(id)
            redirectAttributes.addFlashAttribute("success", "Bracket berhasil dibuat!")
            "redirect:/tournaments/$id"
        } catch (e: Exception) {
            redirectAttributes.addFlashAttribute("error", "Gagal generate bracket: ${e.message}")
            "redirect:/tournaments/$id"
        }
    }

    @PostMapping("/{tournamentId}/participants/{participantId}/delete")
    fun deleteParticipant(
        @PathVariable tournamentId: Long,
        @PathVariable participantId: Long,
        redirectAttributes: RedirectAttributes
    ): String {
        return try {
            tournamentService.deleteParticipant(tournamentId, participantId)
            "redirect:/tournaments/$tournamentId"
        } catch (e: Exception) {
            redirectAttributes.addFlashAttribute("error", e.message)
            "redirect:/tournaments/$tournamentId"
        }
    }
}
