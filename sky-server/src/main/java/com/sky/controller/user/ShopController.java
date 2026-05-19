package com.sky.controller.user;

import com.sky.result.Result;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.web.bind.annotation.*;

@RestController("userShopController")
@Tag(name = "店铺管理")
@RequestMapping("/user/shop")
@Slf4j
public class ShopController {

    public static final String SHOP_STATUS = "SHOP_STATUS";

    @Autowired
    private RedisTemplate redisTemplate;

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
