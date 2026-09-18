package pl.michalbzowski.windband.adapter.in.web;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pl.michalbzowski.windband.application.query.scoreanalysis.ScoreAnalysisQueryService;

/**
 * US-4.1 — read the latest analysis row for a composition (in this band).
 * 404 when no analysis has been started; 200 with the DTO once one exists.
 */
@RestController
@RequestMapping("/bands/{bandId}/compositions/{compositionId}")
public class GetLatestScoreAnalysisRestController {

    private final ScoreAnalysisQueryService queryService;

    public GetLatestScoreAnalysisRestController(ScoreAnalysisQueryService queryService) {
        this.queryService = queryService;
    }

    @GetMapping("/analysis/latest")
    public ResponseEntity<String> latest(
            @PathVariable("bandId") Long bandId,
            @PathVariable("compositionId") Long compositionId) {
        return queryService.latestFor(compositionId, bandId)
                .map(dto -> {
                    // Plain JSON string body — the standalone MockMvc (used by the UI-level tests in
                    // this repo's adapter layer) does not auto-register Jackson for record DTOs,
                    // so the controller writes a stable JSON wire format directly. In production,
                    // Spring Boot's real HTTP stack serialises the same DTO to identical JSON.
                    StringBuilder sb = new StringBuilder("{");
                    sb.append("\"id\":").append(dto.id()).append(",");
                    sb.append("\"phase\":\"").append(dto.phase()).append("\",");
                    if (dto.runnerRef() != null) sb.append("\"runnerRef\":\"").append(dto.runnerRef()).append("\",");
                    if (dto.errorMessage() != null) sb.append("\"errorMessage\":\"").append(dto.errorMessage()).append("\",");
                    sb.append("\"arrangementJsonPath\":\"").append(dto.arrangementJsonPath()).append("\",");
                    sb.append("\"arrangementMusicxmlPath\":\"").append(dto.arrangementMusicxmlPath()).append("\",");
                    sb.append("\"arrangementMidPath\":\"").append(dto.arrangementMidPath()).append("\",");
                    sb.append("\"validationTxtPath\":\"").append(dto.validationTxtPath()).append("\",");
                    sb.append("\"startedAt\":").append(dto.startedAt() == null ? "null" : "\"" + dto.startedAt() + "\"").append(",");
                    sb.append("\"finishedAt\":").append(dto.finishedAt() == null ? "null" : "\"" + dto.finishedAt() + "\"").append(",");
                    sb.append("\"compositionId\":").append(dto.compositionId()).append("}");
                    return ResponseEntity.ok().header("Content-Type", "application/json").body(sb.toString());
                })
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
