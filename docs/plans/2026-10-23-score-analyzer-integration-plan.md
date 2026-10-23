# Integracja Score Analyzer z Windband Manager — plan realizacji

Data: 2026-10-23. Status: faz A-D.

## Kontekst

POC `score-analyzer` (Python, `/home/mbzowski/score-analyzer`) działa: PDF → JSON
(nazwa pliku, instrument/partytura, zakresy stron, tryb text/scan). W WM istnieje już
szkielet Epic 4 (US-4.1/4.3): encja `ScoreAnalysis`, port `AiAnalysisRunner`
(`start`/`inspect`), endpointy `POST .../score-files/{id}/analyze` + `GET .../analysis/latest`,
format artefaktu `arrangement.json` (`AIProposals: role,pageFrom,pageTo,confidence`),
modal "Analizuj" w `compositions/detail.html`. Runner jest jednak **Stub** — brak realnej
usługi AI i brak akceptacji propozycji (US-4.4/4.5 ⬜).

## Cele użytkowe

1. Do istniejącego utworu: wgram PDF → automatyczna analiza → propozycje głosów
   (instrument + zakres stron) → akceptacja tworzy `CompositionInstrument` (party).
2. Z PDF-a nowość: wpis biblioteczny zakładany automatycznie (tytuł, kompozytor,
   aranżer + partie) na podstawie analizy pliku.
3. Działa lokalnie (Ollama) i na Railway (OpenRouter; tokeny użytkowników w bazie,
   szyfrowane, zarządzane z UI).

## Fazy

### Faza A — rozbudowa Score Analyzera (Python)
- A1 **Joby asynchroniczne**: `POST /jobs` (multipart PDF + opcje) → 202 `{job_id}`;
  `GET /jobs/{id}` → `{status, progress}`; `GET /jobs/{id}/result` → JSON analizy.
  (Synchroniczny `/analyze` zostaje dla UI testowego; UI wewnętrznie przechodzi na joby.)
- A2 **Providerzy LLM**: `ollama` (lokalny, tryb domyślny) i `openrouter`
  (OpenAI-kompatybilny chat/completions + wizja przez data-URI). Klucz OpenRouter
  przekazywany per-żądanie nagłówkiem `X-LLM-Key` (fallback: env). Model
  OpenRouter konfigurowalny (domyślnie `google/gemini-2.5-flash` — wzrok + tanio).
- A3 **Ekstrakcja metadanych dzieła**: LLM z pierwszej strony → `work: {title,
  composer, arranger}` (wynik "new-from-pdf" w jednym przebiegu z klasyfikacją stron).
- A4 **Auth serwisowy**: gdy ustawiony env `ANALYZER_SHARED_TOKEN`, wszystkie endpointy
  wymagają nagłówka `X-Analyzer-Key` (usługa ma publiczny URL na Railway).
- A5 **Railway**: `Dockerfile` (slim python + deps, bez ML), `healthcheck`.

### Faza B — WM: realny runner HTTP (adapter portu `AiAnalysisRunner`)
- `ScoreAnalyzerProperties` (`app.scoreanalyzer.base-url`, `shared-token`, timeouty).
- `HttpScoreAnalyzerRunner`: `start()` = upload pliku ScoreFile do `POST /jobs`
  (nagłówek z tokenem użytkownika + shared key); zwraca `job_id` jako ref.
  `inspect(ref)` = `GET /jobs/{id}`; przy `succeeded` pobiera `result`, zapisuje
  `arrangement.json` (mapowanie parts→role/pageFrom/pageTo/confidence) +
  `analyzer-raw.json` do `outputDir` i zwraca SUCCEEDED.
- Wiązanie: `@ConditionalOnProperty(app.scoreanalyzer.base-url)` — HTTP runner jako
  `@Primary`; Stub tylko gdy brak konfiguracji (testy/dev).

### Faza C — WM: tokeny API użytkownika (szyfrowane, zarządzane z UI)
- Migracja **V49__create_user_api_tokens.sql**: `(subject, provider, label,
  encrypted_value, masked_hint, created_at, updated_at)`, unikalny `(subject,provider,label)`.
- `SecretsCryptoService`: AES-256-GCM, klucz z env `APP_TOKENS_KEY` (base64 32 B);
  brak klucza → zapis zablokowany z komunikatem (fail-closed).
- Usługa + REST + strona `Ustawienia → Tokeny AI`: lista (tylko maska `sk-or-…abcd`),
  dodaj/wymień/usuń. Pełna wartość nigdy nie wraca do UI.
- Start analizy rozwiązuje token `openrouter` bieżącego użytkownika i przekazuje go
  runnerowi (w pamięci, bez logowania).

> **ZREALIZOWANO (2026-10-23, branch `feat/llm-key-vault`)** — z uproszczeniami:
> - migracja `V49__create_llm_api_keys.sql`, tabela `llm_api_keys`
>   `(owner_id → app_users, provider, label, key_hint, ciphertext, created_at, updated_at)`,
>   unikalny `(owner_id, provider)` — JEDEN klucz na dostawcę, ponowny zapis nadpisuje
>   (planowany wariant z wieloma etykietami na dostawcę nie był potrzebny dla OpenRouter);
> - szyfrowanie: port `SecretCipher` + `AesGcmSecretCipher` (AES-256-GCM,
>   Base64(IV[12]||ct||tag[16])), klucz główny z env `LLM_KEYS_MASTER_KEY`
>   (`app.llmkeys.master-key`; Base64 dekodujący się do 32 B **albo** surowa fraza UTF-8
>   o długości 32 B — dev/test używają czytelnych fraz, produkcja `openssl rand -base64 32`);
>   brak klucza → fail-closed (zapis = 503, analiza startuje bez klucza);
> - UI: menu `Konto → Klucze AI` (`/llm-keys`), REST `/api/llm-keys` (GET/POST/DELETE),
>   w UI tylko `key_hint` (ostatnie 4 znaki), jawna wartość nigdy nie wraca;
> - `StartScoreAnalysisRestController` bierze jawny klucz z `LlmApiKeyQueryService.plainKey()`
>   i przekazuje in-memory do `AnalysisRequest.llmApiKey` (X-LLM-Key, bez logowania).

### Faza D — WM: akceptacja propozycji + "Nowy utwór z PDF"
- D1 `GET .../analysis/{id}/proposals` — czytanie `arrangement.json` (JsonAiProposals),
  rozwiązanie roli na instrumenty zespołu (aliasOf/InstrumentRoleMap + dopasowanie
  nazwy), zwraca `AiProposalPreview` (również role nierozwiązane).
- D2 `POST .../analysis/{id}/accept` — dla zaakceptowanych wierszy tworzy
  `CompositionInstrument` z `PartSource.AI` (HYBRID gdy użytkownik zmienił instrument),
  spięte z `score_file_id` (V44). Walidacja range vs pageCount pliku.
- D3 Strona `/compositions/new-from-pdf`: upload PDF → job `mode=full` → formularz
  wstępnie wypełniony (tytuł/kompozytor/aranżer z `work`, tabela partów do edycji)
  → zatwierdzenie tworzy Composition + ScoreFile + party.

## Kolejność i weryfikacja

A (test lokalny na 3 fixture PDF + mock OpenRouter) → B+C+D (każda faza: compile,
testy jedn./integr., na końcu pełne `./mvnw clean verify`, branch `feat/score-analyzer-integration`,
PR przez automat). Railway: deployment przez GitHub Actions po merge; analyzera jako
osobna usługa (Faza A5) dodaje się w projekcie Railway ręcznie — instrukcja w README.

## Ryzyka

- Timeouty kolejki: joby trzymane w pamięci analyzera (jedna replika) — akceptowalne;
  restart = joby stracone, WM pokaże FAILED po limicie pollingów.
- Nazywanie instrumentów przez LLM bywa niespójne — D1 rozwiązuje to mapowaniem
  aliasów istniejącym w domenie WM.
- Klucz `APP_TOKENS_KEY` musi istnieć na Railway przed wdrożeniem Fazy C. (faktycznie `LLM_KEYS_MASTER_KEY`)

## FAZA D — projekt realizacji (dopisane 2026-10-23, kontynuacja pracy)

### Decyzje projektowe

1. **Jeden PR (#286)** dla B+C+D na `feat/score-analyzer-integration`
   (konwencja: jedna funkcjonalność = jeden PR); `feat/llm-key-vault` (C) fast-forwardowana.
2. **Akceptacja = weryfikacja**: zaakceptowane partie dostają od razu parę
   `verify(verifier, now)` — realizuje US-4.5 („AI nigdy bez weryfikacji") i otwiera
   US-3.03 (markReady po aktywowaniu partii).
3. **Nowy utwór z PDF reużywa kernel analizy**: utworzenie DRAFT → upload
   (`ScoreFileCommandService.upload`) → `ScoreAnalysisCommandService.start` → polling
   istniejącym `/analysis/latest` + nowym `/proposals` → confirmation (metadane praca
   + partie). Nie tworzymy drugiego kanału analitycznego.
4. **`RoleInstrumentResolver`** (application/query/scoreanalysis): normalizacja =
   lowercase + Normalizer.NFD + strip diacritic; instrument kandydat = instrument
   bandowy o nazwie równej roli AI LUB którego nazwa jest `source_tag` wiersza
   `instrument_role_map` z tym samym `target_role_pattern`; root (nie-alias) wygrywa nad
   aliasem (semantyka US-1.2); aktywny członek pokrywa rolę, jeśli któryś z jego
   instrumentów jest kandydatem (spójność z `EventCompositionPartsQueryService`).
5. **AI vs HYBRID przy akceptacji**: wybrany instrument == wynik rozginięcia roli →
   `PartSource.AI`; inaczej `HYBRID`.
6. **Idempotencja akceptacji**: rola już istnieje w utworze → skip (nie nadpisujemy
   ręcznego mapowania); odpowiedź `{"created": n, "skipped": [{role, reason}]}`.
7. **CSRF**: `SecurityConfig` ma CSRF globalnie wyłączony — `fetch` POST-y bez tokena OK.
8. **JSON w MockMvc**: kontrolery piszą odpowiedź przez `ObjectMapper.writeValueAsString`
   (konwencja fazy B) — kontrakty testów niezależne od autowired Jackson.

### Tablica prac

| # | Co | Jak | Test |
|---|----|-----|------|
| D1 | GET `/bands/{b}/compositions/{c}/analysis/{a}/proposals` → `{work, instruments[], proposals[]}` | `ScoreAnalysisProposalsQueryService`: banda-scoped (ISE→409), faza SUCCEEDED + plik `arrangement.json`; `JsonAiProposals.parse`; work z `analyzer-raw.json`; per-wiersz resolver; `AiProposalPreview` + `instrumentIdOrNull` (rekord dotąd bezużytkowy) | [D+T] unit (fixture JSON w tempdir) + MockMvc 200/409 |
| D2 | POST `.../analysis/{a}/accept`, body `{rows:[{role,pageFrom,pageTo,instrumentId,confidence?}]}` → 200 `{created,skipped[]}` | `AcceptAiProposalsCommandService.accept`: banda-scoped; instrument przez `findByIdAndBandId` (IAE→400); zakres vs `scoreFile.pageCount` (factory); duplikat roli→skip; `verify(verifier, now)` | [D+T] unit + MockMvc kontrakt |
| D3 | GET `/bands/{b}/compositions/new-from-pdf` (strona: plik → analiza → podgląd → potwierdzenie) | `NewFromPdfPageController` + template `newFromPdf.html`; post-start reuse polling `/analysis/latest`; formularz pracy prefilled z `work`; tabela propozycji + `<select>` instrumentów (lista z D1) | [D] |
| D4 | POST `.../new-from-pdf/start` (multipart: file, title?) → JSON `{compositionId,fileId,analysisId}` | `NewFromPdfCommandService.start`: composition DRAFT (tytuł: podany \| filename bez .pdf \| „Nowy utwór") + `ScoreFileCommandService.upload` + start analizy z kluczem `LlmApiKeyQueryService.plainKey(userId,"ollama")` (400, jeśli brak) | [D+T] unit (stub runnera) + MockMvc kontrakt |
| D5 | POST `.../new-from-pdf/confirm` JSON `{compositionId,title,composer,arranger,rows[]}` → 303 do detail | `NewFromPdfCommandService.confirm`: `updateTexts` (tytuł obowiązkowy) + wspólne kernel z D2 na latest SUCCEEDED analizie; jedno `@Transactional` | [D+T] unit + MockMvc kontrakt |
| D6 | Modal „Analizuj AI" w `detail.html`: po SUCCEEDED — tabela propozycji + „Akceptuj" zamiast linków do artefaktów; po akceptacji toast + reload partii | rozszerzenie istniejącego JS (fetch `/proposals`, `location.reload()` po sukcesie) | [D] manual e2e |

### Sekwencja realizacji (TDD)

1. `RoleInstrumentResolver` + unit-test (aliasy, diacritic, brak dopasowania).
2. D1 service + testy (fixture JSON; fazy PENDING/SUCCEEDED; plik nieobecny) → MockMvc kontrakt.
3. D2 service + testy (AI vs HYBRID, skip duplikatu, zakres stron, cross-band instrument 400) → MockMvc kontrakt.
4. D4/D5 service + testy (draft+upload+start; confirm z pustym tytułem; confirm z wierszami) → MockMvc kontrakt.
5. Template `newFromPdf.html` + modyfikacja modala `detail.html`.
6. `./mvnw clean verify` (pełne) → commit → rebase na origin/main → verify → push (PR #286 auto-odświeżony) + edycja opisu PR (B+C+D).
7. Manual E2E: WM lokalnie (analyzer :8091): nowy utwór z „Zadzwonił Polonez" → analiza → podgląd → akcept części wierszy → partie w utworze (`PartSource.AI/HYBRID`, para verify).

### Ryzyka / uwagi fazy D

- `AiProposalPreview` zyskuje pole — rekord dotąd bezużytkowy (tylko jego plik), brak break.
- DRAFT utworu pozostaje w bibliotece, jeśli user nie dokończy flow PDF — akceptowalne (DRAFT, usuwalny); auto-cleanup NIE.
- `accept` z rolą nieistniejącą w propozycjach → 400; UI wysyła tylko role znane z D1.
- Klucz LLM: start bez klucza użytkownika → 400 (komunikat o braku w `/llm-keys`).
