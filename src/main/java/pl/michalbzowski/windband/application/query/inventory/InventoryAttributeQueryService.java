package pl.michalbzowski.windband.application.query.inventory;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.michalbzowski.windband.application.dto.UniformAttributeDefDto;
import pl.michalbzowski.windband.application.dto.InstrumentAttributeDefDto;
import pl.michalbzowski.windband.application.dto.OrderAttributeDefDto;
import pl.michalbzowski.windband.application.dto.AwardAttributeDefDto;
import pl.michalbzowski.windband.domain.band.Band;
import pl.michalbzowski.windband.domain.inventory.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class InventoryAttributeQueryService {

    private final UniformAttributeDefRepository uniformDefRepo;
    private final UniformAttributeValueRepository uniformValueRepo;
    private final InstrumentAttributeDefRepository instrumentDefRepo;
    private final InstrumentAttributeValueRepository instrumentValueRepo;
    private final OrderAttributeDefRepository orderDefRepo;
    private final OrderAttributeValueRepository orderValueRepo;
    private final AwardAttributeDefRepository awardDefRepo;
    private final AwardAttributeValueRepository awardValueRepo;

    // === Uniform attributes ===

    public List<UniformAttributeDefDto> getUniformAttributeDefs(Band band) {
        List<UniformAttributeDef> defs = uniformDefRepo.findByBand(band);
        return defs.stream()
                .map(d -> new UniformAttributeDefDto(d.getId(), d.getName(), d.getType(), d.isRequired(), d.isDisplayInList(), d.getDisplayOrder(), d.getOptions(), d.getDependsOnAttributeId(), d.getDependsOnValue()))
                .toList();
    }

    public Map<Long, String> getUniformAttributeValues(UniformItem item) {
        return uniformValueRepo.findByUniformItem(item).stream()
                .collect(Collectors.toMap(v -> v.getAttributeDef().getId(), UniformAttributeValue::getValue));
    }

    // === Instrument attributes ===

    public List<InstrumentAttributeDefDto> getInstrumentAttributeDefs(Band band) {
        return instrumentDefRepo.findByBand(band).stream()
                .map(d -> new InstrumentAttributeDefDto(d.getId(), d.getName(), d.getType(), d.isRequired(), d.isDisplayInList(), d.getDisplayOrder(), d.getOptions(), d.getDependsOnAttributeId(), d.getDependsOnValue()))
                .toList();
    }

    public Map<Long, String> getInstrumentAttributeValues(InstrumentItem item) {
        return instrumentValueRepo.findByInstrumentItem(item).stream()
                .collect(Collectors.toMap(v -> v.getAttributeDef().getId(), InstrumentAttributeValue::getValue));
    }

    // === Order attributes ===

    public List<OrderAttributeDefDto> getOrderAttributeDefs(Band band) {
        return orderDefRepo.findByBand(band).stream()
                .map(d -> new OrderAttributeDefDto(d.getId(), d.getName(), d.getType(), d.isRequired(), d.getDisplayOrder(), d.getOptions(), d.getDependsOnAttributeId(), d.getDependsOnValue()))
                .toList();
    }

    public Map<Long, String> getOrderAttributeValues(InventoryOrder order) {
        // First try to get from OrderAttributeValue table (legacy)
        if (orderValueRepo.findByOrder(order).size() > 0) {
            return orderValueRepo.findByOrder(order).stream()
                    .collect(Collectors.toMap(v -> v.getAttributeDef().getId(), OrderAttributeValue::getValue));
        }
        // If not found, parse from JSON string in order
        String attrsJson = order.getAttributesJson();
        if (attrsJson == null || attrsJson.isEmpty()) {
            return Map.of();
        }
        Map<Long, String> result = new java.util.HashMap<>();
        for (String pair : attrsJson.split(";")) {
            String[] kv = pair.split("=", 2);
            if (kv.length == 2) {
                try {
                    Long attrId = Long.parseLong(kv[0]);
                    result.put(attrId, kv[1]);
                } catch (NumberFormatException e) {
                    // Skip invalid attribute IDs
                }
            }
        }
        return result;
    }

    // === Award attributes ===

    public List<AwardAttributeDefDto> getAwardAttributeDefs(Band band) {
        return awardDefRepo.findByBandAndActiveTrueOrderByDisplayOrderAsc(band).stream()
                .map(d -> new AwardAttributeDefDto(d.getId(), d.getName(), d.getType(), d.isRequired(), d.isDisplayInList(), d.getDisplayOrder(), d.getOptions(), d.getDependsOnAttributeId(), d.getDependsOnValue()))
                .toList();
    }

    public Map<Long, String> getAwardAttributeValues(AwardItem item) {
        return awardValueRepo.findByAwardItemId(item.getId()).stream()
                .collect(Collectors.toMap(v -> v.getAttributeDef().getId(), AwardAttributeValue::getValue));
    }
}
