package pl.michalbzowski.windband.config;

import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import pl.michalbzowski.windband.adapter.out.scoreanalyzer.HttpScoreAnalyzerRunner;
import pl.michalbzowski.windband.application.command.scoreanalysis.AiAnalysisRunner;
import pl.michalbzowski.windband.application.command.scoreanalysis.AiArtifactsLayout;
import pl.michalbzowski.windband.application.command.scoreanalysis.StubAiAnalysisRunner;
import pl.michalbzowski.windband.application.config.ScoreAnalysisConfig;

/**
 * US-4.1 — wires {@link AiArtifactsLayout} with the root from {@code app.scoreanalysis.output-root}
 * and selects the {@link AiAnalysisRunner} implementation:
 * <ul>
 *   <li>{@code app.scoreanalysis.analyzer-base-url} set (env {@code SCORE_ANALYZER_URL}) →
 *       {@link HttpScoreAnalyzerRunner} (external Score Analyzer service, Phase B);</li>
 *   <li>otherwise → {@link StubAiAnalysisRunner} (tests / dev without the service).</li>
 * </ul>
 */
@Configuration
public class ScoreAnalysisConfiguration {

    @Bean
    @ConditionalOnMissingBean(AiArtifactsLayout.class)
    public AiArtifactsLayout aiArtifactsLayout(ScoreAnalysisConfig cfg) {
        Path root = Path.of(cfg.outputRootOr());
        try {
            Files.createDirectories(root);
        } catch (java.io.IOException ignored) {
            // best-effort — the layout will still create the dir on demand
        }
        return new AiArtifactsLayout(root);
    }

    @Bean
    @ConditionalOnMissingBean(AiAnalysisRunner.class)
    public AiAnalysisRunner aiAnalysisRunner(ScoreAnalysisConfig cfg) {
        return cfg.hasAnalyzer()
                ? new HttpScoreAnalyzerRunner(cfg)
                : new StubAiAnalysisRunner();
    }
}
