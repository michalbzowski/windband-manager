package pl.michalbzowski.windband.application.command.band;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.michalbzowski.windband.application.command.member.GroupCommandService;
import pl.michalbzowski.windband.domain.band.Band;
import pl.michalbzowski.windband.domain.band.MemberAttributeDef;
import pl.michalbzowski.windband.domain.band.MemberAttributeDefRepository;
import pl.michalbzowski.windband.domain.band.MemberAttributeValue;
import pl.michalbzowski.windband.domain.band.MemberAttributeValueRepository;
import pl.michalbzowski.windband.domain.member.AttributeDefSource;
import pl.michalbzowski.windband.domain.member.GroupRepository;
import pl.michalbzowski.windband.domain.member.Member;
import pl.michalbzowski.windband.domain.member.MemberRepository;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class MemberAttributeCommandService {

    private final MemberAttributeDefRepository attributeDefRepository;
    private final MemberAttributeValueRepository attributeValueRepository;
    private final MemberRepository memberRepository;
    private final GroupCommandService groupCommandService;
    private final GroupRepository groupRepository;

    public MemberAttributeDef createAttributeDef(Band band, String name, String type, boolean required, boolean displayInList, int displayOrder, String options) {
        MemberAttributeDef def = MemberAttributeDef.create(band, name, type, required, displayInList, displayOrder, options);
        MemberAttributeDef saved = attributeDefRepository.save(def);
        // Spawn dynamic group for BOOLEAN attributes (no-op for other types).
        if ("BOOLEAN".equals(type)) {
            groupCommandService.createDynamicGroupForAttribute(saved);
        }
        return saved;
    }

    public MemberAttributeDef updateAttributeDef(Long id, String name, String type, boolean required, boolean displayInList, int displayOrder, String options) {
        MemberAttributeDef def = attributeDefRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("AttributeDef not found: " + id));
        String oldType = def.getType();
        String oldName = def.getName();
        def.update(name, type, required, displayInList, displayOrder, options);
        MemberAttributeDef saved = attributeDefRepository.save(def);
        // Dynamic group sync
        if ("BOOLEAN".equals(oldType) && !"BOOLEAN".equals(type)) {
            // Type changed AWAY from BOOLEAN → delete the dynamic group
            groupCommandService.deleteDynamicGroup(saved);
        } else if (!"BOOLEAN".equals(oldType) && "BOOLEAN".equals(type)) {
            // Type changed TO BOOLEAN → create the dynamic group
            groupCommandService.createDynamicGroupForAttribute(saved);
        } else if ("BOOLEAN".equals(type) && !oldName.equals(name)) {
            // Still BOOLEAN, but name changed → rename
            groupCommandService.renameDynamicGroup(saved);
        }
        return saved;
    }

    public void deleteAttributeDef(Long id) {
        MemberAttributeDef def = attributeDefRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("AttributeDef not found: " + id));
        // Drop the dynamic group (if any) BEFORE deleting the def, to break the FK.
        // deleteDynamicGroup is a no-op when no dynamic group exists, so safe to always call.
        groupCommandService.deleteDynamicGroup(def);
        attributeDefRepository.delete(def);
    }

    public MemberAttributeDef getAttributeDefById(Long id) {
        return attributeDefRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("AttributeDef not found: " + id));
    }

    public void setAttributeValue(Long memberId, Long attributeDefId, String value) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new pl.michalbzowski.windband.application.command.member.MemberNotFoundException(memberId));
        MemberAttributeDef def = attributeDefRepository.findById(attributeDefId)
                .orElseThrow(() -> new IllegalArgumentException("AttributeDef not found: " + attributeDefId));

        MemberAttributeValue attrValue = attributeValueRepository.findByMemberAndAttributeDef(member, def)
                .orElse(null);

        if (attrValue == null) {
            attrValue = MemberAttributeValue.create(member, def, value);
        } else {
            attrValue.setValue(value);
        }
        attributeValueRepository.save(attrValue);
        // Sync dynamic group membership based on the new value
        groupCommandService.syncMemberInDynamicGroup(
                new AttributeDefSource(def, attributeValueRepository), member);
    }

    /**
     * Ensure that a dynamic group exists for the given BOOLEAN attribute, creating it
     * (and syncing existing attribute values into it) if missing. No-op for non-BOOLEAN
     * types or when a dynamic group already exists.
     * <p>
     * Used by the {@code DynamicGroupBackfillRunner} on application startup to migrate
     * bands that had BOOLEAN attributes before this feature shipped. Safe to call
     * multiple times — fully idempotent.
     */
    public void ensureDynamicGroupExists(MemberAttributeDef def) {
        if (!"BOOLEAN".equals(def.getType())) return;
        if (groupRepository.findByDynamicSource(def).isPresent()) return;
        groupCommandService.createDynamicGroupForAttribute(def);
        // Sync existing attribute values into the group
        List<MemberAttributeValue> values = attributeValueRepository.findByAttributeDef(def);
        for (MemberAttributeValue v : values) {
            groupCommandService.syncMemberInDynamicGroup(
                    new AttributeDefSource(def, attributeValueRepository), v.getMember());
        }
    }

    public void deleteAttributeValue(Long memberId, Long attributeDefId) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new pl.michalbzowski.windband.application.command.member.MemberNotFoundException(memberId));
        MemberAttributeDef def = attributeDefRepository.findById(attributeDefId)
                .orElseThrow(() -> new IllegalArgumentException("AttributeDef not found: " + attributeDefId));
        attributeValueRepository.findByMemberAndAttributeDef(member, def)
                .ifPresent(attributeValueRepository::delete);
    }
}
