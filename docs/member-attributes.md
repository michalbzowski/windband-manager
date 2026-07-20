# Atrybuty członków i grupy (Member Attributes & Groups)

Ten dokument opisuje, jak w windband-manager działają atrybuty członków
(`MemberAttributeDef` / `MemberAttributeValue`) oraz grupy (`Group`) —
w tym różnicę między grupami **statycznymi** (ręcznymi) a **dynamicznymi**
(automatycznie wypełnianymi z atrybutu lub pola członka).

> Źródło prawdy: kod w `domain/band/MemberAttributeDef.java`,
> `domain/member/Group.java`, `adapter/in/web/MemberAttributeController.java`,
> `adapter/in/web/MemberAttributeApiController.java`.

---

## 1. Atrybuty członków (MemberAttributeDef)

Atrybut to zdefiniowana kolumna/metadana przypisana do członka w obrębie zespołu
(`band_id`). Każdy członek może mieć wartość (`MemberAttributeValue`) dla danego
atrybutu.

### Typy atrybutów (pole `type`)
- `BOOLEAN` — Tak/Nie (flaga).
- `TEXT` — dowolny tekst.
- `NUMBER` — liczba.
- `SELECT` — jeden wybór z listy (`options` jako JSON array).
- `MULTI_SELECT` — wiele wyborów z listy (`options` jako JSON array).
- `DATE` — data.

### Pozostałe pola
- `name` — nazwa wyświetlana (unikalna w zespole: `band_id` + `name`).
- `required` — czy obowiązkowa przy tworzeniu członka.
- `displayInList` — czy pokazywana w tabeli listy członków.
- `displayOrder` — kolejność wyświetlania.
- `active` — czy atrybut jest aktywny.
- `options` — JSON array opcji (tylko dla `SELECT` / `MULTI_SELECT`).

### Jak dodać atrybut (przez UI)
1. Wejdź w panel zespołu → zakładka **Atrybuty** (lub nawiguj do `/band/attributes`).
2. Kliknij **Dodaj atrybut** (otwiera `attribute-form.html` przez HTMX).
3. Wypełnij:
   - **Nazwa** — np. `Grający członek`.
   - **Typ** — wybierz `BOOLEAN` (Tak/Nie) dla flagi typu "czy grający".
   - **Wymagany** — zostaw niezaznaczone (opcjonalny).
   - **Pokaż na liście** — zaznacz jeśli chcesz widzieć w tabeli członków.
   - **Kolejność** — np. `0`.
4. Zapisz (POST `/band/attributes`).

### Jak dodać atrybut (przez API)
```
POST /api/bands/{bandId}/attribute-defs
Content-Type: application/json

{
  "name": "Grający członek",
  "type": "BOOLEAN",
  "required": false,
  "displayInList": true,
  "displayOrder": 0
}
```
Odpowiedź zawiera `id` nowo utworzonego atrybutu.

### Jak ustawić wartość atrybutu dla członka
- Przez UI: edycja członka (`/members/{id}`) → sekcja atrybutów.
- Przez API: `MemberAttributeCommandService` (wartość `MemberAttributeValue`
  wiąże członka z `attributeDef`).

---

## 2. Grupy (Group)

Grupy służą do grupowania członków (np. sekcje instrumentów, zespoły
koncertowe). Dzielą się na dwa rodzaje:

### Grupy statyczne (ręczne)
- Tworzone ręcznie, członkowie dodawani/usuwani przez
  `POST /api/groups/{groupId}/members/{memberId}` i
  `DELETE /api/groups/{groupId}/members/{memberId}`.
- Brak powiązania z atrybutem — skład zarządzasz sam.

### Grupy dynamiczne (automatyczne)
Grupa jest **dynamiczna** gdy `Group.isDynamic()` zwraca `true`, tj. gdy ma
ustawione źródło (`dynamicSource` lub `dynamicSourceType`):

- **Z atrybutu** (`dynamicSource` → `MemberAttributeDef`): grupa zawiera
  dokładnie tych członków, którzy mają dany atrybut ustawiony na `true`
  (dla `BOOLEAN`) lub mają wartość (dla innych typów). Synchronizacja dzieje
  się w `MemberAttributeCommandService` (zmiana wartości → dodaj/usuń z grupy)
  oraz w cyklu życia `MemberAttributeDef` (utworzenie → grupa; zmiana nazwy →
  zmiana nazwy grupy; usunięcie → usunięcie grupy).
- **Z pola członka** (`dynamicSourceType = MEMBER_FIELD`, `dynamicSourceKey`):
  grupa z członków mających dane pole (np. rola).

Dynamiczne grupy mają w UI odznakę i **nie można** ręcznie dodawać/usuwać
członków (są sterowane przez źródło).

### Jak utworzyć grupę dynamiczną z atrybutu
Grupa dynamiczna z atrybutu powstaje automatycznie gdy:
- Tworzysz atrybut `BOOLEAN`, LUB
- Wiążesz istniejący atrybut z grupą przez `Group.setDynamicSource(attributeDef)`.

Szczegóły implementacji: `docs/plans/2026-07-04-dynamic-groups.md`.

---

## 3. Wzorzec "grający członek" (zalecany dla grup aktywnych muzyków)

Jeśli chcesz mieć grupę "aktywni grający muzycy" i **nie** chcesz bawić się
w dynamiczne wykluczanie atrybutów (goście/uczniowie minus OSP), najprostszy
i niezawodny sposób to:

1. Dodaj atrybut `BOOLEAN` o nazwie **`Grający członek`** (sekcja 1 powyżej).
2. Oznacz ręcznie każdego grającego muzyka tym atrybutem (Tak).
3. Utwórz grupę dynamiczną powiązaną z tym atrybutem — grupa automatycznie
   wypełni się grającymi.
4. Goście, uczniowie i OSP **nie** dostają flagi `Grający członek`, więc
   nie wpadają do grupy. To daje dokładnie grupę "aktywni grający" bez
   złożonej logiki wykluczania.

> To podejście flagowe jest świadomym uproszczeniem (pół-automat) —
> wystarczającym dopóki nie potrzebujesz reguł typu "A i B ale nie C".

---

## 4. Testy / weryfikacja
- `DynamicGroupEndToEndUiTest` — E2E: atrybut BOOLEAN → grupa dynamiczna
  aktualizuje skład po zmianie wartości.
- `GroupCommandServiceTest` — logika synchronizacji dynamicznej grupy.
- `MemberQueryServiceTest` — filtrowanie członków po atrybutach.
