package com.port.inspection.service;

import com.port.inspection.dto.Dtos;
import com.port.inspection.exception.BizException;
import com.port.inspection.model.*;
import com.port.inspection.model.enums.*;
import com.port.inspection.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/** 客服：消费者催件处理 */
@Service
@RequiredArgsConstructor
public class UrgeService {

    private final ConsumerUrgeRepository urgeRepository;
    private final ParcelRepository parcelRepository;
    private final ParcelEventService eventService;

    /** 消费者催件（仅本人包裹） */
    @Transactional
    public ConsumerUrge urge(String waybillNo, String message, User consumer) {
        Parcel p = parcelRepository.findByWaybillNo(waybillNo)
                .orElseThrow(() -> BizException.notFound("运单"));
        if (consumer.getRole() == Role.CONSUMER && !p.getRecipientIdCard().equals(consumer.getIdCard())) {
            throw BizException.forbidden("只能催件本人包裹");
        }
        ConsumerUrge u = new ConsumerUrge();
        u.setParcelId(p.getId());
        u.setConsumerName(consumer.getDisplayName());
        u.setMessage(message);
        urgeRepository.save(u);
        eventService.record(p.getId(), p.getStatus(), p.getStatus(), "消费者催件", consumer, message);
        return u;
    }

    /** 客服处理催件 */
    @Transactional
    public ConsumerUrge handle(Long urgeId, String note, User cs) {
        ConsumerUrge u = urgeRepository.findById(urgeId).orElseThrow(() -> BizException.notFound("催件"));
        if (u.getStatus() != UrgeStatus.OPEN) {
            throw new BizException("该催件已处理");
        }
        u.setStatus(UrgeStatus.HANDLED);
        u.setHandledBy(cs.getDisplayName());
        u.setHandleNote(note);
        u.setHandledAt(LocalDateTime.now());
        urgeRepository.save(u);
        eventService.record(u.getParcelId(), null,
                parcelRepository.findById(u.getParcelId()).orElseThrow().getStatus(),
                "催件处理", cs, note);
        return u;
    }

    public List<ConsumerUrge> list(String status) {
        if (status != null && !status.isBlank()) {
            return urgeRepository.findByStatus(UrgeStatus.valueOf(status));
        }
        return urgeRepository.findAllByOrderByCreatedAtDesc();
    }
}
