package com.sky.service;

import com.sky.entity.Dish;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class DishVectorStoreService {

    @Autowired
    private VectorStore vectorStore;

    public static final String ID_PREFIX = "dish_";

    public void syncDish(Dish dish, String categoryName) {
        vectorStore.add(List.of(buildDocument(dish, categoryName)));
    }

    public void syncDishes(List<Dish> dishes, Map<Long, String> categoryMap) {
        List<Document> documents = dishes.stream()
                .filter(d -> d.getStatus() != null && d.getStatus() == 1)
                .map(d -> buildDocument(d, categoryMap.getOrDefault(d.getCategoryId(), "未知分类")))
                .toList();
        if (!documents.isEmpty()) {
            vectorStore.add(documents);
        }
    }

    public void removeDish(Long dishId) {
        vectorStore.delete(List.of(ID_PREFIX + dishId));
    }

    public void removeDishes(List<Long> dishIds) {
        vectorStore.delete(dishIds.stream().map(id -> ID_PREFIX + id).toList());
    }

    public void clearAll() {
        vectorStore.delete(List.of());
    }

    private Document buildDocument(Dish dish, String categoryName) {
        String statusText = (dish.getStatus() != null && dish.getStatus() == 1) ? "启售" : "停售";
        String text = String.format("""
                        菜品名称：%s
                        菜品分类：%s
                        菜品价格：%.2f元
                        菜品描述：%s
                        销售状态：%s
                        """,
                dish.getName(),
                categoryName,
                dish.getPrice() != null ? dish.getPrice().doubleValue() : 0,
                dish.getDescription() != null ? dish.getDescription() : "暂无描述",
                statusText
        );

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("dishId", dish.getId());
        metadata.put("name", dish.getName());
        metadata.put("categoryId", dish.getCategoryId());
        metadata.put("categoryName", categoryName);
        metadata.put("price", dish.getPrice());
        metadata.put("status", dish.getStatus());

        return Document.builder()
                .id(ID_PREFIX + dish.getId())
                .text(text)
                .metadata(metadata)
                .build();
    }
}
