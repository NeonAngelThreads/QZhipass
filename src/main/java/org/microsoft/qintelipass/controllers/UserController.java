package org.microsoft.qintelipass.controllers;

import org.microsoft.qintelipass.configs.RedisConfig;
import org.microsoft.qintelipass.services.RedisService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("api/v1")
public class UserController {
    @Autowired
    private RedisService redisService;

}
