/*
 * Copyright (C) 2026 Mitch Trachtenberg — GPL v3.
 */
package com.mjtrac.builderui;

import com.mjtrac.ballot.model.*;
import com.mjtrac.ballot.repository.*;
import com.mjtrac.ballot.service.BallotGenerationService;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

import java.util.List;
import java.util.Set;

/**
 * Generates a real ballot PDF+YAML for a single "vote for two" PLURALITY
 * contest (maxChoices=2, 5 candidates) — the multi-winner scenario named
 * directly in the percentToWin feature request ("elections that allow a
 * user to vote for more than one, perhaps the candidates (plural) with the
 * most votes win"). percentToWin is irrelevant here: WinnerRules.rank()
 * ignores it entirely once maxChoices > 1 and instead marks the top-N
 * candidates by raw vote count as winners, with no majority/percentage
 * check at all. Mirrors RcvFiveCandidateBuilder's headless,
 * no-web-server pattern.
 *
 * Usage: mvn -q -o exec:java -Dexec.mainClass=com.mjtrac.builderui.TopTwoBuilder
 *   -Dexec.classpathScope=test
 *   -Dexec.args="--spring.datasource.url=jdbc:sqlite:/path/db.db --ballot.export.dir=/path/out --test-election.out=/path/election_data.json"
 */
public class TopTwoBuilder {

    private static final String JURISDICTION_NAME = "Top Two Test County";
    private static final String ELECTION_NAME = "Top Two Test Election";
    private static final String REGION_NAME = "Precinct 1";
    private static final String BALLOT_TYPE_NAME = "Precinct";
    private static final String CONTEST_TITLE = "City Council — Vote For Two";
    private static final String PRINT_USER = "toptwo-test-harness";

    @SpringBootApplication(scanBasePackages = "com.mjtrac.ballot")
    @EntityScan("com.mjtrac.ballot.model")
    static class Config {
    }

    public static void main(String[] args) throws Exception {
        try {
            run(args);
        } finally {
            System.exit(0);
        }
    }

    static void run(String[] args) throws Exception {
        ConfigurableApplicationContext ctx = new SpringApplicationBuilder(Config.class)
            .web(WebApplicationType.NONE)
            .headless(true)
            .run(args);

        try {
            String outPath = ctx.getEnvironment().getProperty("test-election.out", "toptwo_election_data.json");

            JurisdictionRepository jurisdictionRepo = ctx.getBean(JurisdictionRepository.class);
            RegionRepository regionRepo = ctx.getBean(RegionRepository.class);
            ElectionRepository electionRepo = ctx.getBean(ElectionRepository.class);
            BallotTypeRepository ballotTypeRepo = ctx.getBean(BallotTypeRepository.class);
            ContestRepository contestRepo = ctx.getBean(ContestRepository.class);
            BallotCombinationRepository combinationRepo = ctx.getBean(BallotCombinationRepository.class);
            BallotDesignTemplateRepository templateRepo = ctx.getBean(BallotDesignTemplateRepository.class);
            UserRepository userRepo = ctx.getBean(UserRepository.class);
            BallotGenerationService ballotService = ctx.getBean(BallotGenerationService.class);

            Jurisdiction jurisdiction = jurisdictionRepo.findByName(JURISDICTION_NAME).orElseGet(() -> {
                Jurisdiction j = new Jurisdiction();
                j.setName(JURISDICTION_NAME);
                return jurisdictionRepo.save(j);
            });

            Region region = regionRepo.findByJurisdictionIdAndRegionTypeOrderByName(
                    jurisdiction.getId(), Region.RegionType.SINGLE_PRECINCT)
                .stream().filter(r -> REGION_NAME.equals(r.getName())).findFirst()
                .orElseGet(() -> {
                    Region r = new Region();
                    r.setJurisdiction(jurisdiction);
                    r.setName(REGION_NAME);
                    r.setRegionType(Region.RegionType.SINGLE_PRECINCT);
                    return regionRepo.save(r);
                });

            BallotType ballotType = ballotTypeRepo.findAll().stream()
                .filter(bt -> jurisdiction.getId().equals(bt.getJurisdiction().getId()) && BALLOT_TYPE_NAME.equals(bt.getName()))
                .findFirst().orElseGet(() -> {
                    BallotType bt = new BallotType();
                    bt.setJurisdiction(jurisdiction);
                    bt.setName(BALLOT_TYPE_NAME);
                    return ballotTypeRepo.save(bt);
                });

            Election election = electionRepo.findByJurisdictionIdOrderByElectionDateDesc(jurisdiction.getId())
                .stream().filter(e -> ELECTION_NAME.equals(e.getName())).findFirst()
                .orElseGet(() -> {
                    Election e = new Election();
                    e.setJurisdiction(jurisdiction);
                    e.setName(ELECTION_NAME);
                    e.setElectionType(Election.ElectionType.GENERAL);
                    return electionRepo.save(e);
                });

            List<Contest> contests = contestRepo.findByElectionId(election.getId());
            if (contests.isEmpty()) {
                Contest contest = new Contest();
                contest.setElection(election);
                contest.setTitle(CONTEST_TITLE);
                contest.setMaxChoices(2);
                contest.setVotingMethod(Contest.VotingMethod.PLURALITY);
                contest.setInstructions("Vote for two.");

                java.util.List<Candidate> candidates = new java.util.ArrayList<>();
                for (int i = 1; i <= 5; i++) {
                    Candidate c = new Candidate();
                    c.setName("Candidate " + i);
                    c.setDisplayOrder(i);
                    c.setContest(contest);
                    candidates.add(c);
                }
                contest.setCandidates(candidates);
                contest.setAssignedRegions(List.of(region));
                contestRepo.save(contest);
            }

            BallotCombination combination = combinationRepo.findByElectionId(election.getId()).stream()
                .filter(c -> c.getParty() == null
                    && region.getId().equals(c.getRegion().getId())
                    && ballotType.getId().equals(c.getBallotType().getId()))
                .findFirst()
                .orElseGet(() -> {
                    BallotCombination c = new BallotCombination();
                    c.setRegion(region);
                    c.setBallotType(ballotType);
                    c.setElection(election);
                    return combinationRepo.save(c);
                });

            BallotDesignTemplate template = templateRepo.findAll().stream()
                .filter(t -> t.getElection() != null && election.getId().equals(t.getElection().getId()))
                .findFirst().orElseGet(() -> {
                    BallotDesignTemplate t = new BallotDesignTemplate();
                    t.setElection(election);
                    t.setPaperSize(BallotDesignTemplate.PaperSize.LETTER_8_5x11);
                    t.setVoteIndicatorStyle(BallotDesignTemplate.VoteIndicatorStyle.OVAL);
                    t.setColumns(1);
                    return templateRepo.save(t);
                });

            User printedBy = userRepo.findByUsername(PRINT_USER).orElseGet(() -> {
                User u = new User();
                u.setUsername(PRINT_USER);
                u.setPasswordHash("unused");
                u.setRoles(Set.of(User.Role.ADMIN));
                return userRepo.save(u);
            });

            BallotCombination reloadedCombo = combinationRepo.findById(combination.getId()).orElseThrow();
            BallotDesignTemplate reloadedTemplate = templateRepo.findById(template.getId()).orElseThrow();
            User reloadedUser = userRepo.findById(printedBy.getId()).orElseThrow();

            ballotService.generateBallot(reloadedCombo, reloadedTemplate, reloadedUser, 1, "en");
            List<String> written = ballotService.getLastWrittenFiles();
            List<String> yamlFiles = written.stream().filter(f -> f.endsWith(".yaml")).toList();
            List<String> pdfFiles = written.stream().filter(f -> f.endsWith(".pdf")).toList();

            if (yamlFiles.isEmpty() || pdfFiles.isEmpty()) {
                throw new IllegalStateException("generateBallot() did not auto-export the expected "
                    + "YAML/PDF pair — got: " + written);
            }

            TestElectionBuilder.writeElectionDataJson(outPath, combination.getId(), region.getName(), yamlFiles, pdfFiles);
            System.out.println("TopTwoBuilder: wrote " + outPath + " (combination "
                + combination.getId() + ")");
            System.out.println("YAML: " + yamlFiles.get(0));
            System.out.println("PDF: " + pdfFiles.get(0));
        } finally {
            ctx.close();
        }
    }
}
