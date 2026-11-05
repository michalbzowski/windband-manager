# AGENTS.md — windband-manager (instrukcje dla agentów kodujących)

Ten plik czytają agenci (Codex, Claude Code, Hermes*). Hermes dodatkowo
ładuje skille `windband-manager-*` — tam jest pełna wiedza proceduralna.
Tutraj: minimum, którego nie wolno pominąć.

## Stack i zasady twarde
- Spring Boot 3.3.x, Java 21, Thymeleaf + htmx, Pico.css; testy: `./mvnw` (nigdy globalny mvn).
- Checkstyle/SpotBugs są bramowane — kod ich nie obejdzie; nie wyłączaj (`@Disabled`,
  wykluczenia w pom) bez jawnej decyzji użytkownika.
- Testy UI (`*UiTest`, Selenium+Chromium) działają w jednym JVM (forkCount=1) —
  nie dodawaj paralelizacji tej suity. `ReportCompilationTest` jest wyłączany
  przełącznikiem `-Dtest=!...` dokładnie jak w CI.

## Bramki deterministyczne (kolejność obowiązkowa)
1. `git add <pliki>`
2. `bash scripts/agent-verify.sh`  — pełne `clean verify` + stamp drzewa
   (albo `scripts/agent-quick.sh` w wewnętrznej pętli dev)
3. `git commit`
4. `git rebase origin/main` — jeśli drzewo się zmieniło, **powtórz krok 2**
5. `bash scripts/pre-push-check.sh` — bez zielonego wyniku NIE pushuj;
   override tylko z `VERIFY_OVERRIDE=1 OVERRIDE_REASON="..."` i cytatem
   powodu w opisie PR
6. `git push` → `bash scripts/ci-status.sh <branch>` — status CI jest dla agenta
   (joby: `build`, `pipeline-lint` (zizmor), `pit`). Naprawy review trafiają na
   **ten sam branch/PR**, nie nowy.
- Testy lokalne odpalasz na tej samej maszynie co przeglądarkę agenta: przy
  obciążeniu CPU zdarzają się flaki timingowe UI — diagnoza w
  `windband-manager-ga-verification` (Known flakes), nie „fix przez retry”.

## PIT (mutacje) — zmiana pakietów biznesowych
Zmiany w `pl.michalbzowski.windband.{domain,application,util}` przechodzą
`scripts/mutation-check.sh '<pakiet>.*'` (threshold 50). Przeżyły mutant =
test nic nie assertuje: napraw test, nie próg. `util.*` ma dług (18%) —
uzupełnij asercje zanim ruszasz klasy w util bez potrzeby.

## Grilling + OpenSpec przed większą zmianą
Większa zmiana (>3 plików produkcyjnych, migracja Flyway, auth/security,
nowy ekran, kontrakt API): najpierw przesłuchanie użytkownika do braku luk,
potem `openspec/changes/<id>/proposal.md` + `tasks.md` (szablony w
`openspec/templates/`). Implementacja realizuje tasks.md; po wdrożeniu
`npx -y @fission-ai/openspec archive <id>`.

## Konwencje
- Polish commit messages w stylu history (`fix(scope): opis`); język PL w
  kodzie domenowym i testach UI jest celowy (nazwy testów = scenariusze).
- Nigdy `git add -A`; drzewo może zawierać prace innych (worktrees
  `.worktrees/*` są dozwolone i ignorowane przez gate).
- Nie dotykaj sekretów/`application-*.yml` produkcyjnych; Railway deploy
  jest przez GitHub merge — szczegóły: skill `windband-manager-deployment`.

*Hermes: ten plik nie zastępuje skilli; skill jest źródłem proceduralnym.
