package com.sky.controller.admin;

import com.sky.dto.DishDTO;
import com.sky.dto.DishPageQueryDTO;
import com.sky.entity.Dish;
import com.sky.result.PageResult;
import com.sky.result.Result;
import com.sky.service.DishService;
import com.sky.vo.DishVO;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Set;

@RestController
@RequestMapping("/admin/dish")
@Tag(name = "菜品相关接口")
@Slf4j
public class DishController {

    @Autowired
    private DishService dishService;
    @Autowired
    private RedisTemplate redisTemplate;

    /**
     * 添加菜品
     * @param dishDTO
     * @return
     */
    @PostMapping
    @Operation(summary = "添加菜品")
    public Result save(@RequestBody DishDTO dishDTO){
        log.info("添加菜品，菜品数据：{}",dishDTO);
        dishService.saveWithFlavor(dishDTO);


        //清理缓存数据
        redisTemplate.delete("dish_"+dishDTO.getCategoryId());

        return Result.success();
    }

    /**
     * 菜品分页查询
     * @param dishPageQueryDTO
     * @return
     */
    @GetMapping("/page")
    @Operation(summary = "菜品分页查询")
    public Result<PageResult> page(DishPageQueryDTO dishPageQueryDTO){
        log.info("菜品分页查询，参数：{}",dishPageQueryDTO);
        PageResult pageResult = dishService.pageQuery(dishPageQueryDTO);
        return Result.success(pageResult);
    }

    /**
     * 批量删除菜品
     * @param ids
     * @return
     */
    @DeleteMapping
    @Operation(summary = "批量删除菜品")
    public Result delete(@RequestParam List<Long> ids){
        log.info("批量删除菜品，ids：{}",ids);
        dishService.deleteBatch(ids);

        //清理所有菜品缓存
        cleanCache("dish_*");


        return Result.success();
    }

    /**
     * 根据id查询菜品和对应的口味数据
     * @param id
     * @return
     */
    @GetMapping("/{id}")
    @Operation(summary = "根据id查询菜品")
    public Result<DishVO> getById(@PathVariable Long id){
        log.info("根据id查询菜品信息，id：{}",id);
        DishVO dishVO = dishService.getByIdWithFlavor(id);
        return Result.success(dishVO);
    }



    /**
     * 修改菜品
     * @param dishDTO
     * @return
     */
    @PutMapping
    @Operation(summary = "编辑菜品")
    public Result update(@RequestBody DishDTO dishDTO){
        log.info("编辑菜品：{}",dishDTO);
        dishService.updateWithFlavor(dishDTO);

        //清理所有菜品缓存
        cleanCache("dish_*");

        return Result.success();
    }

    /**
     * 根据分类id查询菜品
     * @param categoryId
     * @return
     */
    @GetMapping("/list")
    @Operation(summary = "根据分类id查询菜品")
    public Result<List<Dish>> list(Long categoryId){
        List<Dish> list = dishService.list(categoryId);
        return Result.success(list);
    }

    @PostMapping("/status/{status}")
    @Operation(summary = "起售停售")
    public Result startOrStop(@PathVariable Integer status, Long id){
        dishService.startOrStop(status,id);

        //清理所有菜品缓存
        cleanCache("dish_*");

        return Result.success();
    }

    /**
     * 清理缓存
     * @param key
     */
    private void cleanCache(String key){
        Set keys = redisTemplate.keys(key);
        redisTemplate.delete(keys);
    }

}
