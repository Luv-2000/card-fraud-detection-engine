package com.luv.fraud_service.controller;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class RedisPingController {

    private final StringRedisTemplate redisTemplate;

    public RedisPingController(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @GetMapping("/redis-ping")
    public String redisPing() {
        Long count = redisTemplate.opsForValue().increment("ping-test");
        return "ping-test counter = " + count;
    }
}