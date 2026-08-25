# Plan: Ujednolicony Page-Header (system-wide) for windband-manager

> Zabezpieczenie przed utratą kontekstu. Źródło prawdy: skill `unified-page-header`
> (`~/.hermes/skills/windband-manager/unified-page-header/SKILL.md`). Ten plik jest
> jego skrót i checkpointy — po compresji kontekstu odczytaj TEGO PLIKU + SKILLE.

## Cel
Jeden element nawigacji na KAŻDYM widoku (detail / list / form):
**strzałka powrotna + tytuł widoku + przyciski akcji** — jeden fragment, zero duplikatów,
zero inline-stylów, ikony stroke-based i świadome motywu, CI kontrakt blokujący regres.

## 5 zasad (zobacz skill po szczegóły)
1. Single source of truth — jeden fragment `fragments/page-header.html`; `fragments/icons.html` dla ikon.
2. Parametry, nie conditionaly — host podaje dane (`backUrl`, `title`, `actions[]`), fragment renderuje.
3. Emoji → SVG — każdy emoji w nagłówku (🎪 ✏️ ➕ 🎵 📋) → stroke-based `<svg fill=none stroke=currentColor>`.
4. Akcje = dane — `{label, url, hx?, danger?, icon?}` zamiast wklejania `<button>` po stronie hosta.
5. Kontrakt CI — `PageHeaderConsistencyUiTest` (parametryzowany per widok): pasek istnieje
   (`data-page-header="v1"`), wyrównanie ≤ 1px, ikony theme-aware, brak inline-stylów.

## API fragmentu
```html
<nav th:replace="~{fragments/page-header :: page-header(
     ~{/events},                       ! backUrl (null dla top-level)
     'Wydarzenia',                     ! title
     ${[{'label':'Dodaj wydarzenie','url':'/events/new','hx':true,'target':'#events-list-container'}]}  ! actions[]
)}"></nav>
```

Warianty wyznaczane przez PRESENCJĘ parametrów (nie jawnym typem):
- **detail** — backUrl + actions[edit,delete] → ⬅ + tytuł + [✎] [⋮→Usuń]
- **list**   — tytuł + actions[dodatki] → (⬅ jeśli backUrl) + tytuł + [+ Dodaj …]
- **form**   — backUrl + actions[] (pusto lub Anuluj) → ⬅ + tytuł (+ Anuluj)

Klucze mapy akcji: `label`, `url`, `hx`(+`target`), `danger`, `icon`.

## Migration — 4 PR-y, każdy po jednym wariancie (TDD + zielony `mvn verify`)
| PR | Zakres | Co robi |
|----|--------|---------|
| **A** — foundation + detail | events/detail, rehearsals/detail | Tworzy `page-header` + `icons.html`; przesuwa oba detail-page'y; dodaje `PageHeaderConsistencyUiTest` (2 wiersze detail). Zero zmian UX. |
| **B** — list | /events /rehearsals /members /groups /tags /instruments /inventory /orders | Emoj→ikony; "+ Dodaj" → acion data; CSV rośnie do 8+ widoków. |
| **C** — form | */form + */edit (members, groups, tags, instruments, events, rehearsals) | back + tytuł (+ Anuluj opcjonalnie). |
| **D** — ogon | dashboards/*, reports/*, public/event, consent, admin/* | Pełny pokrycie; kontrakt blokuje nowe widoki bez wiersza CSV. |

## Checkpointy (stan po kolejnym etapie)
- [ ] **PR A:** `fragments/page-header.html` + `fragments/icons.html` istnieją; events/detail i rehearsals/detail używają `page-header`; `PageHeaderConsistencyUiTest` zielony (2 wiersze); pełny `mvn clean verify` SUCCESS; PR otwarty z linkiem + opisem.
- [ ] **PR B:** 8 listów na `page-header`; emoji znikły z nagłówków; test z 10+ wierszami zielony; `mvn verify` green.
- [ ] **PR C:** formularze z ⬅ powrotem + (Anuluj); `mvn verify` green.
- [ ] **PR D:** wszystkie pozostałe widoki; pełna mapa CSV; brak inline-nagłówków w repo (`grep -rn '🎪\|✏️\|➕' src/main/resources/templates/*/*list*.html` = pusto).

## Known pitfalls (zostawione z PR #132)
- Pico nadaje `<button>` `margin-bottom` → `.icon-btn` MUSI mieć `margin:0;padding:0;border:0;min-height:0`.
- Testy lokalizują po id → fragment nadaje `th:id` po stronie serwera (param `editId`/`deleteId`/per-akcja `id`), NIE JS.
- Host-tag attributes merge'ją po `th:replace` → użyj tego do `data-page-header="v1"` dla testów, bez brudzenia API.
- Zero inline-stylów na hostach (`style=""` w nagłówku = błąd kontraktu).

## Historia / powiązania
- **PR #132** (merged 2026-08-25) zbudował `detail-page-actions-bar.html` — PR A go generalizuje i renamuje.
- Skills: `unified-page-header`, `windband-manager`, `windband-coding-standards`, `selenium-htmx-testing`.
