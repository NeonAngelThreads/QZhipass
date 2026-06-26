package org.microsoft.qintelipass.controllers;

import lombok.extern.slf4j.Slf4j;
import org.microsoft.qintelipass.ILoginStrategy;
import org.microsoft.qintelipass.LoginStrategyFactory;
import org.microsoft.qintelipass.request.LoginRequest;
import org.microsoft.qintelipass.response.ResponseBody;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("api/v1/portal")
public class AuthController {
    @Autowired
    private LoginStrategyFactory factory;
    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest formData){
        String loginType = formData.getLoginType();
        Map<String, Object> params = formData.getParams();
        ILoginStrategy strategy = factory.getStrategy(loginType);
        log.info("User response: {}", formData);
        ResponseBody response = strategy.authenticate(params);
        log.info("Authenticator response: {}", response);
        if (response.isSuccess()) {
            return ResponseEntity.ok(response);
        }
        return ResponseEntity.badRequest().body(response);
    }
}
