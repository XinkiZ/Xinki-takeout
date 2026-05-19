package com.sky;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.transaction.annotation.EnableTransactionManagement;

@SpringBootApplication // Spring Boot核心注解，用于自动配置和组件扫描
@EnableTransactionManagement // 开启基于注解的事务管理功能
@EnableCaching  //  开启缓存注解
@EnableScheduling
@Slf4j // Lombok注解，自动生成SLF4J日志对象
public class SkyApplication {
    public static void main(String[] args) {
        SpringApplication.run(SkyApplication.class, args);
        log.info("server started");
    }
}
