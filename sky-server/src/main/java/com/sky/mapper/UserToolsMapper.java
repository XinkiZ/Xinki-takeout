package com.sky.mapper;

import com.sky.entity.Dish;
import com.sky.entity.Setmeal;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface UserToolsMapper {

    List<Dish> searchDishByKeyWords(List<String> keyWords);

    List<Setmeal> searchSetmealByKeyWords(List<String> keyWords);
}
