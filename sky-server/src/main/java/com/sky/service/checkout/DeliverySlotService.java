package com.sky.service.checkout;

import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import static com.sky.service.checkout.CheckoutModels.DeliverySlot;

/** 依据距离生成半小时粒度的立即送达时间与预约时段。 */
@Service
public class DeliverySlotService {
    private static final DateTimeFormatter LABEL = DateTimeFormatter.ofPattern("MM月dd日 HH:mm");

    public LocalDateTime earliest(LocalDateTime now, int distanceMeters) {
        return now.plusMinutes(distanceMeters <= 3000 ? 30 : 45);
    }

    public List<DeliverySlot> availableSlots(LocalDateTime now, int distanceMeters) {
        LocalDateTime cursor = roundUp(earliest(now, distanceMeters));
        List<DeliverySlot> slots = new ArrayList<>();
        for (int index = 0; index < 16; index++) {
            LocalDateTime end = cursor.plusMinutes(30);
            slots.add(new DeliverySlot(cursor, end, cursor.format(LABEL) + "–" + end.format(DateTimeFormatter.ofPattern("HH:mm"))));
            cursor = end;
        }
        return slots;
    }

    public DeliverySlot requireAvailable(LocalDateTime requested, List<DeliverySlot> slots) {
        return slots.stream().filter(slot -> slot.start().equals(requested)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("预约配送时段已失效，请重新选择"));
    }

    private LocalDateTime roundUp(LocalDateTime value) {
        int remainder = value.getMinute() % 30;
        LocalDateTime rounded = value.withSecond(0).withNano(0);
        return remainder == 0 ? rounded : rounded.plusMinutes(30 - remainder);
    }
}
