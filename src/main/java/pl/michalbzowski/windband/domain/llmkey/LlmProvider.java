package pl.michalbzowski.windband.domain.llmkey;

/**
 * Dostawca LLM, którego dotyczy zapisany klucz API użytkownika.
 *
 * <p>Obecnie wyłącznie OpenRouter (chmurowa analiza partytur, np. z Railway);
 * enum przewiduje rozszerzenie o kolejnych dostawców.
 */
public enum LlmProvider {
    OPENROUTER
}
