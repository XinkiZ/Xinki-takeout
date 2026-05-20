package com.sky.service;

import com.sky.constant.StatusConstant;
import com.sky.entity.Category;
import com.sky.entity.Dish;
import com.sky.mapper.CategoryMapper;
import com.sky.mapper.DishMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class DishVectorStoreInitializer implements ApplicationRunner {

    private final DishMapper dishMapper;
    private final CategoryMapper categoryMapper;
    private final DishVectorStoreService dishVectorStoreService;

    @Override
    public void run(ApplicationArguments args) {
        log.info("开始初始化菜品向量库...");
        try {
            List<Dish> dishes = dishMapper.list(Dish.builder()
                    .status(StatusConstant.ENABLE)
                    .build());

            List<Category> categories = categoryMapper.list(null);

            Map<Long, String> categoryMap = new HashMap<>();
            for (Category c : categories) {
                categoryMap.put(c.getId(), c.getName());
            }

            dishVectorStoreService.syncDishes(dishes, categoryMap);
            log.info("菜品向量库初始化完成，共加载{}条菜品", dishes.size());
        } catch (Exception e) {
            log.error("菜品向量库初始化失败", e);
        }
    }
}
