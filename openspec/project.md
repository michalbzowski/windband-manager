# Projekt: windband-manager

Kontekst dla agenta (OpenSpec — jedyne źródło wymagań dla zmian). Reszta wiedzy vive w skilach
Hermes (`windband-manager-workflow` itd.) i w `AGENTS.md` na górze repo.

## Stack
- Spring Boot 3.3.x, Java 21, Maven wrapper (`./mvnw`), port/adapter hexagonal (ArchUnit pilnuje).
- PostgreSQL (prod, Flyway w `src/main/resources/db/migration`), H2 w testach; Testcontainers
  z graceful fallback na H2 gdy brak Dockera.
- Widoki: Thymeleaf + HTMX + Pico.css (bez JS frameworka). UI testy Selenium w pakiecie `webui`
  (pojedynczy worker, `UiTestBase`, brak paralelizmu).
- Auth: Keycloak OIDC (prod na Railway; rejestracja = redirect `:/oauth2/authorization/keycloak`).
- Raporty: JasperReports (`src/main/resources/reports`).
- Deploy: GitHub Actions (main) → Railway webhook; domena prod: michalbzowski.pl.

## Bramki (mechaniczne — patrz AGENTS.md + scripts/)
`agent-quick.sh` → w pętli; `agent-verify.sh` → przed commitem; `pre-push-check.sh` → przed pushem;
CI: `build` (verify), `pipeline-lint` (zizmor), `pit` (mutacje dla zmienionych pakietów biznesu).

## Konwencje zmian
- Zmiana większa (>3 pliki / migracja / security / API / nowy ekran): grilling →
  `openspec/changes/<id>/proposal.md` + `tasks.md` → implementacja wg tasks → po merge
  archiwizacja do `openspec/specs/`.
- Review fixes i poprawki CI: na tym samym branchu co oryginalny PR.
- Małe, niezależne jednostki dostarczeniowe; bez stacked PR.
