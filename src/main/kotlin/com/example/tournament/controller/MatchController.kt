package com.example.tournament.controller

import com.example.tournament.service.MatchService
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.*
import org.springframework.web.servlet.mvc.support.RedirectAttributes

@Controller
@RequestMapping("/matches")
class MatchController(private val matchService: MatchService) {

    @PostMapping("/{matchId}/result")
    fun submitResult(
        @PathVariable matchId: Long,
        @RequestParam(required = false) winnerId: Long?,
        @RequestParam(required = false) score1: Int?,
        @RequestParam(required = false) score2: Int?,
        @RequestParam tournamentId: Long,
        redirectAttributes: RedirectAttributes
    ): String {
        return try {
            matchService.submitResult(matchId, winnerId, score1, score2)
            redirectAttributes.addFlashAttribute("success", "Hasil match berhasil disimpan!")
            "redirect:/tournaments/$tournamentId"
        } catch (e: Exception) {
            redirectAttributes.addFlashAttribute("error", "Gagal simpan hasil: ${e.message}")
            "redirect:/tournaments/$tournamentId"
        }
    }

    /** Reset a completed match back to READY so it can be re-edited */
    @PostMapping("/{matchId}/edit")
    fun editMatch(
        @PathVariable matchId: Long,
        @RequestParam tournamentId: Long,
        redirectAttributes: RedirectAttributes
    ): String {
        return try {
            matchService.resetToReady(matchId)
            redirectAttributes.addFlashAttribute("success", "Match di-reset, silakan input ulang hasilnya.")
            "redirect:/tournaments/$tournamentId"
        } catch (e: Exception) {
            redirectAttributes.addFlashAttribute("error", "Gagal reset match: ${e.message}")
            "redirect:/tournaments/$tournamentId"
        }
    }
}
