/*
 * Copyright (C) 2026 Mitch Trachtenberg
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package gov.election.ballot.controller;

import gov.election.ballot.model.Election;
import gov.election.ballot.model.Jurisdiction;
import gov.election.ballot.repository.BallotCombinationRepository;
import gov.election.ballot.repository.BallotDesignTemplateRepository;
import gov.election.ballot.repository.ContestRepository;
import gov.election.ballot.repository.ElectionRepository;
import gov.election.ballot.repository.JurisdictionRepository;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;

@Controller
@RequestMapping("/data/elections")
@PreAuthorize("hasAnyRole('ADMIN','DATA_ENTRY')")
public class ElectionController {

    private final ElectionRepository          electionRepo;
    private final JurisdictionRepository      jurisdictionRepo;
    private final BallotCombinationRepository combinationRepo;
    private final BallotDesignTemplateRepository templateRepo;
    private final ContestRepository           contestRepo;

    public ElectionController(ElectionRepository electionRepo,
                              JurisdictionRepository jurisdictionRepo,
                              BallotCombinationRepository combinationRepo,
                              BallotDesignTemplateRepository templateRepo,
                              ContestRepository contestRepo) {
        this.electionRepo     = electionRepo;
        this.jurisdictionRepo = jurisdictionRepo;
        this.combinationRepo  = combinationRepo;
        this.templateRepo     = templateRepo;
        this.contestRepo      = contestRepo;
    }

    @GetMapping
    public String list(Model model) {
        var elections = electionRepo.findAll();
        model.addAttribute("elections",     elections);
        model.addAttribute("jurisdictions", jurisdictionRepo.findAll());
        model.addAttribute("electionTypes", Election.ElectionType.values());
        if (elections.isEmpty()) {
            model.addAttribute("info",
                "No elections yet. Use Quick Setup to create your first election, " +
                "or create one here and then add contests and ballot combinations.");
        }
        return "data/elections/list";
    }

    @GetMapping("/new")
    public String newForm(Model model) {
        model.addAttribute("election",      new Election());
        model.addAttribute("jurisdictions", jurisdictionRepo.findAll());
        model.addAttribute("electionTypes", Election.ElectionType.values());
        model.addAttribute("formTitle",     "New Election");
        return "data/elections/form";
    }

    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable Long id, Model model,
                           RedirectAttributes ra) {
        Election e = electionRepo.findById(id).orElse(null);
        if (e == null) {
            ra.addFlashAttribute("error",
                "Election not found — it may have been deleted.");
            return "redirect:/data/elections";
        }
        model.addAttribute("election",      e);
        model.addAttribute("jurisdictions", jurisdictionRepo.findAll());
        model.addAttribute("electionTypes", Election.ElectionType.values());
        model.addAttribute("formTitle",     "Edit Election: " + e.getName());
        return "data/elections/form";
    }

    @PostMapping("/save")
    public String save(@RequestParam(required = false) Long   id,
                       @RequestParam(required = false) String name,
                       @RequestParam(required = false) Long   jurisdictionId,
                       @RequestParam(required = false) String electionDate,
                       @RequestParam(required = false) String electionType,
                       @RequestParam(defaultValue = "false") boolean uniformBallot,
                       Model model,
                       RedirectAttributes ra) {

        if (name == null || name.isBlank()) {
            return returnToForm(id, "Election name is required.", model);
        }
        if (jurisdictionId == null) {
            return returnToForm(id, "Please select a jurisdiction.", model);
        }
        if (electionType == null || electionType.isBlank()) {
            return returnToForm(id, "Please select an election type.", model);
        }

        Jurisdiction jurisdiction = jurisdictionRepo.findById(jurisdictionId).orElse(null);
        if (jurisdiction == null) {
            return returnToForm(id, "Selected jurisdiction not found — please choose again.", model);
        }

        Election.ElectionType type;
        try {
            type = Election.ElectionType.valueOf(electionType);
        } catch (IllegalArgumentException ex) {
            return returnToForm(id, "Unknown election type: " + electionType, model);
        }

        Election election = (id != null)
            ? electionRepo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Election not found: " + id))
            : new Election();

        election.setName(name.trim());
        election.setJurisdiction(jurisdiction);
        election.setElectionType(type);
        election.setUniformBallot(uniformBallot);
        election.setElectionDate(
            (electionDate != null && !electionDate.isBlank()) ? LocalDate.parse(electionDate) : null);

        electionRepo.save(election);
        ra.addFlashAttribute("success",
            (id != null ? "Updated" : "Created") + " election \"" + election.getName() + "\".");
        return "redirect:/data/elections";
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id, RedirectAttributes ra) {
        Election e = electionRepo.findById(id).orElse(null);
        if (e == null) {
            ra.addFlashAttribute("error", "Election not found (already deleted?).");
            return "redirect:/data/elections";
        }
        String name = e.getName();
        try {
            // Cascade delete in dependency order:
            // 1. Ballot combinations (reference election directly)
            var combos = combinationRepo.findByElectionId(id);
            if (!combos.isEmpty()) {
                combinationRepo.deleteAll(combos);
            }
            // 2. Design templates (reference election directly)
            var templates = templateRepo.findByElectionId(id);
            if (!templates.isEmpty()) {
                templateRepo.deleteAll(templates);
            }
            // 3. Contests (each cascades to its candidates via CascadeType.ALL)
            var contests = contestRepo.findByElectionId(id);
            if (!contests.isEmpty()) {
                contestRepo.deleteAll(contests);
            }
            // 4. Election itself
            electionRepo.delete(e);

            int nCombos    = combos.size();
            int nTemplates = templates.size();
            int nContests  = contests.size();
            StringBuilder msg = new StringBuilder(
                "Deleted election \"" + name + "\"");
            if (nCombos + nTemplates + nContests > 0) {
                msg.append(" and its ");
                if (nCombos    > 0) msg.append(nCombos).append(" combination(s), ");
                if (nTemplates > 0) msg.append(nTemplates).append(" template(s), ");
                if (nContests  > 0) msg.append(nContests).append(" contest(s) ");
                msg.append("(candidates removed automatically).");
            } else {
                msg.append(".");
            }
            ra.addFlashAttribute("success", msg.toString());
        } catch (Exception ex) {
            ra.addFlashAttribute("error",
                "Could not delete \"" + name + "\": " + ex.getMessage());
        }
        return "redirect:/data/elections";
    }

    private String returnToForm(Long id, String error, Model model) {
        Election e = (id != null)
            ? electionRepo.findById(id).orElse(new Election())
            : new Election();
        model.addAttribute("election",      e);
        model.addAttribute("jurisdictions", jurisdictionRepo.findAll());
        model.addAttribute("electionTypes", Election.ElectionType.values());
        model.addAttribute("formTitle",     id != null ? "Edit Election" : "New Election");
        model.addAttribute("error",         error);
        return "data/elections/form";
    }
}
