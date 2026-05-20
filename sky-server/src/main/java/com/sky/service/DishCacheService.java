package com.sky.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.sky.constant.StatusConstant;
import com.sky.dto.CacheData;
import com.sky.entity.Dish;
import com.sky.vo.DishVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

import static com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS;

@Service
@Slf4j
public class DishCacheService {

    private static final String CACHE_KEY_PREFIX = "dish:";
    private static final String LOCK_KEY_PREFIX = "dish:lock:";
    private static final long LOGICAL_TTL_MINUTES = 30;
    private static final int PHYSICAL_TTL_BASE_MINUTES = 50;
    private static final int PHYSICAL_TTL_DELTA_MINUTES = 20;
    private static final long LOCK_TTL_SECONDS = 5;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private DishService dishService;

    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(WRITE_DATES_AS_TIMESTAMPS);

    private final ExecutorService refreshExecutor = Executors.newFixedThreadPool(2, r -> {
        Thread t = new Thread(r, "dish-cache-refresh");
        t.setDaemon(true);
        return t;
    });

    public List<DishVO> getDishListByCategory(Long categoryId) {
        String key = CACHE_KEY_PREFIX + categoryId;
        String json = stringRedisTemplate.opsForValue().get(key);

        if (json != null) {
            CacheData cacheData = parseJson(json);
            if (cacheData != null && cacheData.getExpireTime() != null
                    && cacheData.getExpireTime().isAfter(LocalDateTime.now())) {
                return convertToDishVOList(cacheData.getData());
            }

            if (cacheData != null) {
                List<DishVO> oldData = convertToDishVOList(cacheData.getData());
                refreshCacheAsync(categoryId);
                return oldData;
            }
        }

        return loadWithMutexLock(categoryId);
    }

    private List<DishVO> loadWithMutexLock(Long categoryId) {
        String key = CACHE_KEY_PREFIX + categoryId;
        String lockKey = LOCK_KEY_PREFIX + categoryId;

        while (true) {
            Boolean locked = stringRedisTemplate.opsForValue()
                    .setIfAbsent(lockKey, "1", Duration.ofSeconds(LOCK_TTL_SECONDS));
            if (Boolean.TRUE.equals(locked)) {
                try {
                    String json = stringRedisTemplate.opsForValue().get(key);
                    if (json != null) {
                        CacheData cacheData = parseJson(json);
                        if (cacheData != null && cacheData.getExpireTime() != null
                                && cacheData.getExpireTime().isAfter(LocalDateTime.now())) {
                            return convertToDishVOList(cacheData.getData());
                        }
                    }

                    Dish dish = new Dish();
                    dish.setCategoryId(categoryId);
                    dish.setStatus(StatusConstant.ENABLE);
                    List<DishVO> list = dishService.listWithFlavor(dish);

                    writeCache(key, list);
                    return list;
                } catch (Exception e) {
                    log.error("加载菜品缓存失败，categoryId={}", categoryId, e);
                    return Collections.emptyList();
                } finally {
                    stringRedisTemplate.delete(lockKey);
                }
            }

            try {
                TimeUnit.MILLISECONDS.sleep(60);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return Collections.emptyList();
            }
        }
    }

    private void refreshCacheAsync(Long categoryId) {
        refreshExecutor.submit(() -> {
            String lockKey = LOCK_KEY_PREFIX + categoryId;
            Boolean locked = stringRedisTemplate.opsForValue()
                    .setIfAbsent(lockKey, "1", Duration.ofSeconds(LOCK_TTL_SECONDS));
            if (Boolean.TRUE.equals(locked)) {
                try {
                    Dish dish = new Dish();
                    dish.setCategoryId(categoryId);
                    dish.setStatus(StatusConstant.ENABLE);
                    List<DishVO> list = dishService.listWithFlavor(dish);

                    String key = CACHE_KEY_PREFIX + categoryId;
                    writeCache(key, list);
                    log.info("异步刷新菜品缓存成功，categoryId={}", categoryId);
                } catch (Exception e) {
                    log.error("异步刷新菜品缓存失败，categoryId={}", categoryId, e);
                } finally {
                    stringRedisTemplate.delete(lockKey);
                }
            }
        });
    }

    private void writeCache(String key, List<DishVO> list) {
        LocalDateTime expireTime = LocalDateTime.now().plusMinutes(LOGICAL_TTL_MINUTES);
        CacheData cacheData = CacheData.builder()
                .data(list)
                .expireTime(expireTime)
                .build();

        int randomDelta = ThreadLocalRandom.current().nextInt(PHYSICAL_TTL_DELTA_MINUTES + 1);
        Duration physicalTTL = Duration.ofMinutes(PHYSICAL_TTL_BASE_MINUTES + randomDelta);

        try {
            String json = objectMapper.writeValueAsString(cacheData);
            stringRedisTemplate.opsForValue().set(key, json, physicalTTL);
            log.info("写入菜品缓存，key={}, 逻辑过期={}, 物理TTL={}分钟",
                    key, expireTime, physicalTTL.toMinutes());
        } catch (Exception e) {
            log.error("写入菜品缓存失败，key={}", key, e);
        }
    }

    private CacheData parseJson(String json) {
        try {
            return objectMapper.readValue(json, CacheData.class);
        } catch (Exception e) {
            log.error("解析缓存JSON失败", e);
            return null;
        }
    }

    private List<DishVO> convertToDishVOList(Object data) {
        if (data == null) {
            return Collections.emptyList();
        }
        try {
            return objectMapper.convertValue(data, new TypeReference<List<DishVO>>() {});
        } catch (Exception e) {
            log.error("转换缓存数据为List<DishVO>失败", e);
            return Collections.emptyList();
        }
    }

    public void clearCache(Long categoryId) {
        stringRedisTemplate.delete(CACHE_KEY_PREFIX + categoryId);
    }

    public void clearAllCache() {
        Set<String> keys = stringRedisTemplate.keys(CACHE_KEY_PREFIX + "*");
        if (keys != null && !keys.isEmpty()) {
            stringRedisTemplate.delete(keys);
            log.info("已清理所有菜品缓存，共{}个key", keys.size());
        }
    }
}
