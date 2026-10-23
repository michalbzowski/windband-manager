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
- Klucz `APP_TOKENS_KEY` musi istnieć na Railway przed wdrożeniem Fazy C.
