package com.sky.controller.admin;

import com.sky.result.Result;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.web.bind.annotation.*;

@RestController("adminShopController")
@Tag(name = "店铺管理")
@RequestMapping("/admin/shop")
@Slf4j
public class ShopController {

    public static final String SHOP_STATUS = "SHOP_STATUS";

    @Autowired
    private RedisTemplate redisTemplate;

    /**
     * 设置营业状态
     * @param status
     * @return
     */
    @PutMapping("/{status}")
    @Operation(summary = "设置营业状态")
    public Result setStatus(@PathVariable Integer status){
        log.info("设置店铺营业状态:{}",status == 1 ? "营业中" : "打烊中");
        redisTemplate.opsForValue().set(SHOP_STATUS,status);
        return Result.success();
    }

    /**
     * 获取营业状态
     * @return
     */
    @GetMapping("/status")
    @Operation(summary = "获取营业状态")
    public Result<Integer> gerStatus(){
        Integer status = (Integer) redisTemplate.opsForValue().get(SHOP_STATUS);
        log.info("获取店铺营业状态:{}",status == 1 ? "营业中" : "打烊中");
        return Result.success(status);
    }


}
