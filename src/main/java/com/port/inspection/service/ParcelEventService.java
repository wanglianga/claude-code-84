package com.port.inspection.service;

import com.port.inspection.model.ParcelEvent;
import com.port.inspection.model.User;
import com.port.inspection.model.enums.PackageStatus;
import com.port.inspection.repository.ParcelEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** 包裹档案事件记录：每个节点的材料、税费、时效、责任和赔付均留痕 */
@Service
@RequiredArgsConstructor
public class ParcelEventService {

    private final ParcelEventRepository eventRepository;

    @Transactional(propagation = Propagation.REQUIRED)
    public void record(Long parcelId, PackageStatus from, PackageStatus to,
                       String node, User actor, String remark) {
        ParcelEvent e = new ParcelEvent();
        e.setParcelId(parcelId);
        e.setFromStatus(from);
        e.setToStatus(to);
        e.setNode(node);
        if (actor != null) {
            e.setActor(actor.getDisplayName());
            e.setActorRole(actor.getRole().name());
        } else {
            e.setActor("系统");
            e.setActorRole("SYSTEM");
        }
        e.setRemark(remark);
        eventRepository.save(e);
    }
}
