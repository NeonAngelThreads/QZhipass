package org.microsoft.qintelipass.controllers;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.microsoft.qintelipass.dtos.request.EmailBindingSendCodeRequest;
import org.microsoft.qintelipass.dtos.request.EmailBindingVerifyRequest;
import org.microsoft.qintelipass.dtos.response.ApiResponse;
import org.microsoft.qintelipass.dtos.response.EmailBindingSendCodeResponse;
import org.microsoft.qintelipass.dtos.response.EmailBindingStatusResponse;
import org.microsoft.qintelipass.dtos.response.EmailBindingVerifyResponse;
import org.microsoft.qintelipass.exceptions.UnauthorizedException;
import org.microsoft.qintelipass.security.SecurityUtil;
import org.microsoft.qintelipass.services.auth.EmailBindingService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/account/email-binding")
public class EmailBindingController {
    private final SecurityUtil currentUserService;
    private final EmailBindingService emailBindingService;

    public EmailBindingController(
            SecurityUtil currentUserService,
            EmailBindingService emailBindingService
    ) {
        this.currentUserService = currentUserService;
        this.emailBindingService = emailBindingService;
    }

    @GetMapping
    public ApiResponse<EmailBindingStatusResponse> getStatus(HttpServletRequest request) {
        Long userId = requireUserId(request);
        return ApiResponse.ok("查询成功", emailBindingService.getStatus(userId));
    }

    @PostMapping("/code")
    public ApiResponse<EmailBindingSendCodeResponse> sendCode(
            HttpServletRequest request,
            @Valid @RequestBody EmailBindingSendCodeRequest body
    ) {
        Long userId = requireUserId(request);
        return ApiResponse.ok("验证码已发送", emailBindingService.sendCode(userId, body.email()));
    }

    @PostMapping("/verify")
    public ApiResponse<EmailBindingVerifyResponse> verifyAndBind(
            HttpServletRequest request,
            @Valid @RequestBody EmailBindingVerifyRequest body
    ) {
        Long userId = requireUserId(request);
        return ApiResponse.ok(
                "邮箱绑定成功",
                emailBindingService.verifyAndBind(userId, body.email(), body.code())
        );
    }

    private Long requireUserId(HttpServletRequest request) {
        try {
            return SecurityUtil.getCurrentUserId();
        } catch (UnauthorizedException exception) {
            throw new UnauthorizedException("请先登录");
        }
    }
}