package pl.michalbzowski.windband.application.command.scoreanalysis;

/**
 * Zakres stron (od/do, 1-based, włącznie) po stronie strony — neutralny wobec
 * domeny (nie traci na wartości przy serializacji JSON wyników akceptacji).
 */
public record ExcludedScope(int pageFrom, int pageTo) {
}
