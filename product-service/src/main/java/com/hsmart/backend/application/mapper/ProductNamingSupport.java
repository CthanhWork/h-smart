package com.hsmart.backend.application.mapper;

import com.hsmart.backend.application.dto.DetectionDTO;
import java.util.Map;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class ProductNamingSupport {

    private static final Map<String, String> LABEL_TRANSLATIONS = Map.ofEntries(
            Map.entry("air_conditioner", "May lanh"),
            Map.entry("automatic_washer", "May giat"),
            Map.entry("bed", "Giuong"),
            Map.entry("bedspread", "Tam phu giuong"),
            Map.entry("bench", "Ghe dai"),
            Map.entry("blender", "May xay"),
            Map.entry("bunk_bed", "Giuong tang"),
            Map.entry("cabinet", "Tu"),
            Map.entry("chair", "Ghe"),
            Map.entry("coffee_table", "Ban tra"),
            Map.entry("cupboard", "Tu chen"),
            Map.entry("deck_chair", "Ghe thu gian"),
            Map.entry("desk", "Ban lam viec"),
            Map.entry("dining_table", "Ban an"),
            Map.entry("drawer", "Ngan keo"),
            Map.entry("electric_chair", "Ghe dien"),
            Map.entry("fan", "Quat"),
            Map.entry("faucet", "Voi nuoc"),
            Map.entry("file_cabinet", "Tu ho so"),
            Map.entry("folding_chair", "Ghe gap"),
            Map.entry("hand_glass", "Guong cam tay"),
            Map.entry("highchair", "Ghe em be"),
            Map.entry("kettle", "Am nuoc"),
            Map.entry("kitchen_sink", "Bon rua chen"),
            Map.entry("kitchen_table", "Ban bep"),
            Map.entry("lamp", "Den"),
            Map.entry("mattress", "Nem"),
            Map.entry("microwave_oven", "Lo vi song"),
            Map.entry("mirror", "Guong"),
            Map.entry("music_stool", "Ghe am nhac"),
            Map.entry("oil_lamp", "Den dau"),
            Map.entry("oven", "Lo nuong"),
            Map.entry("pew_(church_bench)", "Ghe bang"),
            Map.entry("poker_(fire_stirring_tool)", "Cay cui lua"),
            Map.entry("pool_table", "Ban bi a"),
            Map.entry("recliner", "Ghe tua"),
            Map.entry("refrigerator", "Tu lanh"),
            Map.entry("rocking_chair", "Ghe bap benh"),
            Map.entry("sink", "Bon rua"),
            Map.entry("sofa", "Sofa"),
            Map.entry("sofa_bed", "Giuong sofa"),
            Map.entry("step_stool", "Ghe bac"),
            Map.entry("stool", "Ghe don"),
            Map.entry("stove", "Bep"),
            Map.entry("table", "Ban"),
            Map.entry("table-tennis_table", "Ban bong ban"),
            Map.entry("table_lamp", "Den ban"),
            Map.entry("television_camera", "May quay"),
            Map.entry("television_set", "Tivi"),
            Map.entry("toaster_oven", "Lo nuong banh"),
            Map.entry("vacuum_cleaner", "May hut bui"),
            Map.entry("wardrobe", "Tu quan ao"),
            Map.entry("water_faucet", "Voi nuoc")
    );

    public String resolveTitle(String requestedTitle, List<DetectionDTO> detections) {
        if (StringUtils.hasText(requestedTitle)) {
            return requestedTitle.trim();
        }

        return detections.stream()
                .filter(detection -> StringUtils.hasText(detection.getLabel()))
                .max((left, right) -> Double.compare(
                        left.getScore() != null ? left.getScore() : 0.0,
                        right.getScore() != null ? right.getScore() : 0.0
                ))
                .map(DetectionDTO::getLabel)
                .map(this::humanizeLabel)
                .orElse("Unknown product");
    }

    public String resolveSuggestedName(String label) {
        if (!StringUtils.hasText(label)) {
            return "Unknown product";
        }
        return humanizeLabel(label);
    }

    private String humanizeLabel(String label) {
        String normalizedKey = label.toLowerCase(Locale.ROOT).trim();
        String translated = LABEL_TRANSLATIONS.get(normalizedKey);
        if (StringUtils.hasText(translated)) {
            return translated;
        }

        String normalized = normalizedKey.replace('_', ' ').replace('-', ' ').trim();
        if (normalized.isBlank()) {
            return "Unknown product";
        }
        return Character.toUpperCase(normalized.charAt(0)) + normalized.substring(1);
    }
}
