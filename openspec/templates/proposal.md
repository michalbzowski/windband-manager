# Zmiana: <tytuł feature po polsku>

Identyfikator: `<id — np. 2026-11-attendance-export>` · Branch: `<feat/...>` · Data: YYYY-MM-DD

## 1. Problem i cel
Co teraz jest nie tak / czego brak i co ma być po zmianie. 2–5 zdań, bez rozwiązań.

## 2. Kryteria akceptacji (obowiązkowe, mierzalne)
- [ ] Given/When/Then…
- [ ] …

## 3. Grilling — rozstrzygnięte pytania (brak = zatrzymaj implementację)
| Pytanie | Odpowiedź | Kto rozstrzygnął |
|---|---|---|
| Które role zespołu widzą tę funkcję? | | |
| Co z danymi historycznymi / migracją Flyway? | | |
| Jaki jest zachowanie przy braku uprawnień? | | |
| Czy to zmienia kontrakt API używany przez windband-inventory? | | |
| Co NIE jest w zakresie? | | |

## 4. Rozwiązanie (skrót techniczny)
Kluczowe byty/endpointy/fragmency Thymeleaf; migracje; ryzyka. NIE tu kod — kod w taskach.

## 5. Weryfikacja
- [ ] `scripts/agent-quick.sh` (loop)
- [ ] `scripts/agent-verify.sh` (bramka przed commitem)
- [ ] `scripts/mutation-check.sh '<ruszane.pakiety>.*'` (bramka fake-coverage)
- [ ] Nowe testy: nazwa + co udowadniają (bez asercji „nie null")
- [ ] `scripts/ci-status.sh` zielony po pushu

## 6. Decyzje/odrzucone alternatywy
Jednozdaniowo po każdej, łącznie z „dlaczego nie".
