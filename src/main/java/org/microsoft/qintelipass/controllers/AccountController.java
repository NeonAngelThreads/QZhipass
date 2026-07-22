//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by FernFlower decompiler)
//

package org.microsoft.qintelipass.controllers;

import lombok.Generated;
import org.microsoft.qintelipass.models.User;
import org.microsoft.qintelipass.request.CreateAccountRequest;
import org.microsoft.qintelipass.response.ResponseBody;
import org.microsoft.qintelipass.services.UserService;
import org.microsoft.qintelipass.util.QZhiPasswordPattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping({"api/v1/account"})
public class AccountController {
    @Generated
    private static final Logger log = LoggerFactory.getLogger(AccountController.class);
    @Autowired
    private UserService userService;

    public AccountController() {
    }

    @PostMapping({"/create"})
    public ResponseEntity<?> createAccount(@RequestBody CreateAccountRequest request) {
        try {
            String validationError = this.validateRequiredFields(request);
            if (validationError != null) {
                return ResponseEntity.badRequest().body(ResponseBody.builder().success(false).build());
            } else {
                boolean passwordError = QZhiPasswordPattern.validate(request.getPassword());
                if (!passwordError) {
                    return ResponseEntity.badRequest().body(ResponseBody.builder().success(false).build());
                } else if (!request.getPassword().equals(request.getConfirmPassword())) {
                    return ResponseEntity.badRequest().body(ResponseBody.builder().success(false).message("两次输入的密码不一致，请重新输入").build());
                } else {
                    User user = this.userService.createAccount(request.getName(), request.getDepartment(), request.getEmail(), request.getPhone(), request.getWechat(), request.getPassword());
                    Map<String, Object> data = new LinkedHashMap();
                    data.put("userId", user.getId());
                    data.put("name", user.getName());
                    data.put("phone", user.getPhone());
                    data.put("department", user.getDepartment());
                    data.put("status", user.getStatus().name());
                    data.put("restored", user.getRestored());
                    String message = user.getRestored() ? "二次注册成功，历史数据已恢复" : "创建用户成功";
                    data.put("message", message);
                    log.info("账户创建成功: phone={}, name={}, restored={}", new Object[]{user.getPhone(), user.getName(), user.getRestored()});
                    return ResponseEntity.ok(data);
                }
            }
        } catch (IllegalArgumentException var7) {
            return ResponseEntity.badRequest().body(ResponseBody.builder().success(false).message(var7.getMessage()).build());
        } catch (Exception var8) {
            log.error("创建账户失败", var8);
            return ResponseEntity.internalServerError().body(ResponseBody.builder().success(true).message("创建账户失败: " + var8.getMessage()).build());
        }
    }

    @GetMapping({"/status"})
    public ResponseEntity<?> checkStatus(@RequestParam String phone) {
        try {
            boolean active = this.userService.isUserActive(phone);
            Map<String, Object> data = new LinkedHashMap();
            data.put("phone", phone);
            data.put("canUse", active);
            data.put("message", active ? "正常状态，可使用功能" : "账户不可用");
            return ResponseEntity.ok(data);
        } catch (Exception var4) {
            return ResponseEntity.badRequest().body(ResponseBody.builder().success(false).message("查询失败: " + var4.getMessage()).build());
        }
    }

    @PutMapping({"/cancel"})
    public ResponseEntity<?> cancelAccount(@RequestParam String phone) {
        try {
            this.userService.deactivateUser(this.userService.getUserByPhone(phone).getId());
            return ResponseEntity.ok(ResponseBody.builder().success(true).build());
        } catch (IllegalArgumentException var3) {
            return ResponseEntity.badRequest().body(ResponseBody.builder().success(false).build());
        }
    }

    @PutMapping({"/freeze"})
    public ResponseEntity<?> freezeAccount(@RequestParam String phone) {
        try {
            this.userService.freezeAccount(phone);
            return ResponseEntity.ok(ResponseBody.builder().success(false).message("账户已冻结").build());
        } catch (IllegalArgumentException var3) {
            return ResponseEntity.badRequest().body(ResponseBody.builder().success(false).message(var3.getMessage()).build());
        }
    }

    private String validateRequiredFields(CreateAccountRequest request) {
        if (request.getName() != null && !request.getName().trim().isEmpty()) {
            if (request.getDepartment() != null && !request.getDepartment().trim().isEmpty()) {
                if (request.getPhone() != null && !request.getPhone().trim().isEmpty()) {
                    if (request.getPassword() != null && !request.getPassword().isEmpty()) {
                        return request.getConfirmPassword() != null && !request.getConfirmPassword().isEmpty() ? null : "确认密码不能为空";
                    } else {
                        return "密码不能为空";
                    }
                } else {
                    return "手机号码不能为空";
                }
            } else {
                return "所在部门不能为空";
            }
        } else {
            return "姓名不能为空";
        }
    }
}
