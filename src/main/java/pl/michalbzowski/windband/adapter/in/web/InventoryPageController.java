package pl.michalbzowski.windband.adapter.in.web;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import pl.michalbzowski.windband.application.query.band.BandQueryService;
import pl.michalbzowski.windband.application.query.inventory.UnifiedInventoryItemQueryService;
import pl.michalbzowski.windband.application.query.member.MemberQueryService;
import pl.michalbzowski.windband.domain.band.Band;
import pl.michalbzowski.windband.domain.inventory.InventoryItem;
import pl.michalbzowski.windband.domain.inventory.ItemAttributeDef;
import pl.michalbzowski.windband.domain.inventory.ItemType;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Inventory list page (F2 S2) over the unified item model.
 *
 * <p>One view: {@code inventory/list} renders all band items as mobile-first cards,
 * attribute definitions (driving the two-step create form + attribute filter) and
 * per-item value maps. Actions run client-side against the unified REST API
 * ({@code /api/inventory/items/*}).</p>
 */
@Controller
@RequestMapping("/inventory")
@RequiredArgsConstructor
public class InventoryPageController {

    private final UnifiedInventoryItemQueryService inventoryQueryService;
    private final MemberQueryService memberQueryService;
    private final BandQueryService bandQueryService;
    private final ObjectMapper objectMapper;

    @GetMapping
    public String listPage(@ModelAttribute("activeTeamId") Long activeTeamId, Model model) throws JsonProcessingException {
        Band band = activeTeamId != null ? bandQueryService.getBandById(activeTeamId) : null;

        if (band == null) {
            // No team context — render empty state
            model.addAttribute("cards", List.of());
            model.addAttribute("members", List.of());
            model.addAttribute("attributeDefsJson", "[]");
            model.addAttribute("attrValueMaps", Map.of());
            return "inventory/list";
        }

        // All items of the band (single unified model), display order: UNIFORM then other types.
        List<InventoryItem> bandItems = new java.util.ArrayList<>(inventoryQueryService.getAllItemsByBand(band.getId()));

        // Attribute definitions across all item types — used by both the create form
        // (rendered per selected type in displayOrder) and the attribute filter chips.
        // Built as plain JSON-friendly maps: ItemAttributeDef has a LAZY Band ref and a
        // circular values↔attributeDef graph, neither of which Jackson may serialize here.
        java.util.ArrayList<Map<String, Object>> defs = new java.util.ArrayList<>();
        for (ItemType type : ItemType.values()) {
            for (ItemAttributeDef d : inventoryQueryService.getAttributeDefsForType(band.getId(), type)) {
                if (!d.isActive()) {
                    continue;
                }
                Map<String, Object> m = new HashMap<>();
                m.put("id", d.getId());
                m.put("name", d.getName());
                m.put("dataType", d.getDataType() != null ? d.getDataType().name() : "TEXT");
                m.put("itemType", type.name());
                m.put("required", d.isRequired());
                m.put("filterable", d.isFilterable());
                m.put("displayInList", d.isDisplayInList());
                m.put("displayOrder", d.getDisplayOrder());
                m.put("options", d.getOptions());
                m.put("optionsList", d.getOptionsList());
                m.put("active", true);
                m.put("bandScoped", d.getBand() != null && band.getId().equals(idOf(d.getBand())));
                defs.add(m);
            }
        }
        defs.sort((a, b) -> Integer.compare((Integer) a.get("displayOrder"), (Integer) b.get("displayOrder")));

        model.addAttribute("itemDefs", defs);
        model.addAttribute("members", memberQueryService.getAllActiveMembers(band.getId()));
        model.addAttribute("attributeDefsJson", objectMapper.writeValueAsString(defs));

        // Cards: a flat list of view objects {item, chips} — pure property navigation in the
        // template (no Thymeleaf map lookup by dynamic key; see codebase SpEL lesson).
        List<CardView> cards = new java.util.ArrayList<>();
        for (InventoryItem item : bandItems) {
            ItemType t = item.getType();
            String typeKey = t != null ? t.name() : null;
            Map<Long, String> values = inventoryQueryService.getAttributeValueMap(item);
            List<DefChip> chips = new java.util.ArrayList<>();
            for (Map<String, Object> d : defs) {
                if (typeKey == null || !typeKey.equals(String.valueOf(d.get("itemType")))) {
                    continue;
                }
                Long defId = asLong(d.get("id"));
                String val = defId != null ? values.get(defId) : null;
                if (val != null && !val.isBlank()) {
                    chips.add(new DefChip(defId, asString(d.get("name")), val));
                }
            }
            cards.add(new CardView(item, chips));
        }
        model.addAttribute("cards", cards);
        return "inventory/list";
    }

    private static Long asLong(Object o) {
        if (o instanceof Number n) { return n.longValue(); }
        if (o != null) { try { return Long.valueOf(String.valueOf(o)); } catch (NumberFormatException ignored) { /* not numeric */ } }
        return null;
    }

    private static String asString(Object o) { return o == null ? "" : String.valueOf(o); }

    /** A single list card: the domain item plus its computed attribute chips. */
    public static class CardView {
        private final InventoryItem item;
        private final List<DefChip> chips;

        public CardView(InventoryItem item, List<DefChip> chips) {
            this.item = item;
            this.chips = chips;
        }

        public InventoryItem getItem() { return item; }
        public List<DefChip> getChips() { return chips; }
    }

    /** A rendered attribute chip: definition id, label and this item's stored value. */
    public static class DefChip {
        private final Long id;
        private final String name;
        private final String value;

        public DefChip(Long id, String name, String value) { this.id = id; this.name = name; this.value = value; }

        public Long getId() { return id; }
        public String getName() { return name; }
        public String getValue() { return value; }
    }
    private Long idOf(Band band) {
        return band != null ? band.getId() : null;
    }
}
