package com.hsmart.backend.infrastructure.bootstrap;

import com.hsmart.backend.domain.entities.Category;
import com.hsmart.backend.infrastructure.persistence.CategoryRepository;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
public class AiCategoryInitializer implements ApplicationRunner {

    static final List<String> AI_CATEGORY_NAMES = List.of(
            "air_conditioner",
            "bed",
            "bedspread",
            "bench",
            "blender",
            "bunk_bed",
            "cabinet",
            "chair",
            "coffee_table",
            "sofa_bed",
            "cupboard",
            "deck_chair",
            "desk",
            "dining_table",
            "drawer",
            "electric_chair",
            "refrigerator",
            "fan",
            "faucet",
            "file_cabinet",
            "folding_chair",
            "hand_glass",
            "highchair",
            "kettle",
            "kitchen_sink",
            "kitchen_table",
            "lamp",
            "mattress",
            "microwave_oven",
            "mirror",
            "music_stool",
            "oil_lamp",
            "oven",
            "pew_(church_bench)",
            "poker_(fire_stirring_tool)",
            "pool_table",
            "recliner",
            "rocking_chair",
            "sink",
            "sofa",
            "step_stool",
            "stool",
            "stove",
            "table-tennis_table",
            "table",
            "table_lamp",
            "television_camera",
            "television_set",
            "toaster_oven",
            "vacuum_cleaner",
            "wardrobe",
            "automatic_washer",
            "water_faucet"
    );

    private final CategoryRepository categoryRepository;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        Set<String> existingNames = categoryRepository.findAll().stream()
                .map(Category::getName)
                .map(name -> name.toLowerCase(Locale.ROOT))
                .collect(Collectors.toSet());

        List<Category> missingCategories = AI_CATEGORY_NAMES.stream()
                .filter(name -> !existingNames.contains(name.toLowerCase(Locale.ROOT)))
                .map(name -> Category.builder().name(name).build())
                .toList();

        if (missingCategories.isEmpty()) {
            log.info("AI product categories are already initialized");
            return;
        }

        categoryRepository.saveAll(missingCategories);
        log.info("Initialized {} AI product categories", missingCategories.size());
    }
}
