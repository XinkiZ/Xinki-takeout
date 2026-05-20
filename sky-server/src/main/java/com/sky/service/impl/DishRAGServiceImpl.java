package com.sky.service.impl;


import com.sky.constant.StatusConstant;
import com.sky.entity.Dish;
import com.sky.mapper.DishMapper;
import com.sky.service.DishRAGService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@RequiredArgsConstructor
@Service
@Slf4j
public class DishRAGServiceImpl implements DishRAGService {

    private final DishMapper dishMapper;

    public void RAGStarter(){
        log.info("RAG数据库为空，开始初始化");

        List<Dish> dishs = dishMapper.listByStatus(StatusConstant.ENABLE);




    }

}
