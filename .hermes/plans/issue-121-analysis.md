# Analiza Issue #121 - Przeniesienie przycisków do nagłówka

## Opis problemu

Na widokach Szczegóły wydarzenia i Szczegóły spotkania przyciski nawigacyjne (Powrót, Edytuj, Usuń) znajdują się na dole strony i nie są spójne stylistycznie między widokami.

### Aktualna sytuacja:

**Szczegóły wydarzenia** (`src/main/resources/templates/events/detail.html`):
- Linia 179-183: `<div>` z przyciskami `[← Powröt][Edytuj][Usuń wydarzenie]` na dole strony
- Przyciski używają klas `secondary`, `outline danger`

**Szczegóły spotkania** (`src/main/resources/templates/rehearsals/detail.html`):  
- Linia 83-93: `<div>` z przyciskami `[Szybka obecność][Edytuj][Powrót]` na dole strony
- Brak przycisku "Usuń spotkanie" (mimo że API endpoint `/api/rehearsals/{id}` DELETE istnieje)
- Przyciski używają klas `primary`, `secondary` z HTMX

## Oczekiwane rozwiązanie:

**Szczegóły wydarzenia**: `[←] Szczegóły wydarzenia [Edytuj][Usuń wydarzenie]` w nagłówku (jedna linia z nazwą widoku)

**Szczegóły spotkania**: `[←] Szczegóły spotkania [Szybka obecność][Edytuj][Usuń spotkanie]` w nagłówku
- Dodanie przycisku "Usuń spotkanie" i jego logiki

## Root cause:

Przyciski akcji są rozproszone na dole treści zamiast być zintegrowane z header-em. Brakuje wspólnego komponentu nagłówka dla widoków szczegółów.

## Plan implementacji:

### Faza 1 - Testy UI (NAJPIERW)
1. Stworzyć nowe testy UI używające Selenium WebDriver lub Spring Boot Test z MockMvc + browser simulation
2. Scenariusze do przetestowania:
   - Entry to Szczegóły wydarzenia from dashboard → return to dashboard  
   - Entry to Szczegóły wydarzenia from events list → return to events list
   - Entry to Szczegóły spotkania from rehearsals list → return to correct location
   - Delete spotkanie functionality (new feature)
   - Delete wydarzenie (button moved, existing delete logic works)
   - Szybka obecność still functions after changes

### Faza 2 - UI Refactoring
1. **Stworzyć wspólny fragment nagłówka** dla stron szczegółów:
   - Użyć Thymeleaf fragment w `fragments/detail-header.html` lub bezpośrednio w layout.html
   - Format: `<nav class="detail-page-actions"><a href="" class="back-btn">←</a><span class="title">...</span>[actions]</nav>`

2. **Events detail.html**:
   - Przenieść przyciski (linie 179-183) bezpośrednio pod dashboard-header lub jako część nagłówka strony
   - Zachować modal delete-event-modal i JS handler'y
   - Zmienić href w "← Powrót" na dynamiczny link do previous page

3. **Rehearsals detail.html**:
   - Odwrócić kolejność przycisków: `[←][Szybka obecność][Edytuj]` → `[←] Szczegóły spotkania [Szybka obecność][Edytuj][Usuń spotkanie]`
   - Dodać modal `delete-rehearsal-modal` analogiczny do `delete-event-modal`
   - Zaimplementować JS handler dla usuwania (fetch DELETE + redirect)
   - Przycisk "Powrót" zmienić na strzałkę w lewo z dynamicznym linkiem
   - Zachować funkcję szybkiej obecności

### Faza 3 - JavaScript/HTMX refactoring
1. Zastąpić hx-get/hx-target w przycisku Edytuj spotkania na czysty HTML redirect lub konsekwentne HTMX swap
2. Dodać delete rehearsal functionality analogiczną do eventów
3. Zapewnić, że `dashboard-header-morph` działa poprawnie z nowym layoutem

### Faza 4 - Styling
1. Użyć spójnych klas CSS dla przycisków (outline vs secondary vs danger)
2. Upewnić się, że header akcji ma odpowiednią wysoką i spacing
3. Test responsiveness na różnych urządzeniach

## Potential issues:
- **HTMX navigation**: Obecne hx-get/hx-target w rehearsals/detail.html mogą przestać działać poprawnie z nowym layoutem
- **Back button routing**: Musimy przechowywać "previous page" w session storage lub localStorage, aby → Powrót działo poprawnie
- **Test stability**: istniejące testy integration/regression mogą się złamać po zmianach struktury HTML

## Files to modify:
1. `src/main/resources/templates/events/detail.html` - move buttons up, refactor structure
2. `src/main/resources/templates/rehearsals/detail.html` - move buttons, add delete rehearsal modal + JS
3. Possibly create `src/main/resources/templates/fragments/detail-actions-bar.html` for shared component
4. Potentially `src/css/app.css` if new styles needed

## Testing strategy:
According to issue description, write UI tests FIRST before implementation using pattern established by other integration/regression tests in project. Prefer Spring Boot Test approach with @MockMvc over full Selenium unless visual verification is critical.
