# Tasks — 2026-11-attendance-ui-polish

Kolejność = kolejność realizacji. Każdy task: mały, samodzielny, weryfikowalny.

- [x] T1. `fragments/icons.html`: fragmenty `clock` + `question` (stroke SVG,
      currentColor, bez emoji/hex/inline-style — polityka biblioteki ikon).
- [x] T2. `rehearsals/detail.html`: polskie etykiety opcji selecta (wartości bez zmian),
      `data-current` + `aria-label`, chipy statusów (ikona+etykieta+licznik,
      role/tabindex/aria-pressed), EXCUSED → klasa `later`; data nagłówka
      `#temporals.format(..., #locale.forLanguageTag('pl'))`; strażnik pustego
      „Miejsce:" + podpowiedź; ➕→`icons::plus`; style inline → `.uber-filter-*`.
- [x] T3. JS w szablonie: aria-pressed w pętli applyFilters, keydown Enter/Space
      w binderze chipów, `data-current` w quickAttendanceSelect (sync w trakcie modala).
- [x] T4. `app.css`: gęstość `.status-select` (max-width/margin), kolory po
      `[data-current]` light+dark, klasy filtrów 1:1 ze starymi inline'ami,
      SVG chipów 13px bez zmian wysokości linii (ISSUE #119).
- [x] T5. `fragments/layout.html`: „ | " stopki tylko gdy build-version aktywny;
      `events/detail.html`: ekstrakcja inline stylów filtrów na wspólne klasy.
- [x] T6. Test UI: `RehearsalAttendancePolishUiTest` (4 scenariusze — etykiety/kolor,
      persist przez auto-zapis, chipy klawiatura+aria, data PL + puste Miejsce).
- [x] T7. Bramka wewnętrzna: celowane UiTesty (nowy + `RehearsalDetailFilterUiTest`
      + `AttendanceListHeaderTakeoverUiTest` + `RehearsalInviteModalUiTest`
      + `QuickAttendanceModalUiTest`) — geometria sticky i kontrakty przetestowane.
- [ ] T8. `scripts/agent-verify.sh` (pełny clean verify + stamp) → commit → rebase →
      powtórzyć verify → `scripts/pre-push-check.sh` → push → PR → `ci-status.sh`.
- [ ] T9. Po merge: `npx -y @fission-ai/openspec archive 2026-11-attendance-ui-polish`.
