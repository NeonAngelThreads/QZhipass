package org.microsoft.qintelipass.controllers;

import org.microsoft.qintelipass.entity.hotkey.Function;
import org.microsoft.qintelipass.entity.hotkey.Hotkey;
import org.microsoft.qintelipass.entity.hotkey.HotkeyConfig;
import org.microsoft.qintelipass.entity.hotkey.HotkeyConfigID;
import org.microsoft.qintelipass.exceptions.ApiException;
import org.microsoft.qintelipass.repository.FunctionKeyRepository;
import org.microsoft.qintelipass.repository.HotkeyConfigRepository;
import org.microsoft.qintelipass.repository.HotkeyRepository;
import org.microsoft.qintelipass.util.security.SecurityUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Validated
@RestController
@RequestMapping("api/v1/user/config")
public class UserConfigController {
    private final HotkeyConfigRepository hotkeyConfigRepository;
    private final FunctionKeyRepository functionKeyRepository;
    private final HotkeyRepository hotkeyRepository;
    @Autowired
    public UserConfigController(HotkeyConfigRepository hotkeyRepository, FunctionKeyRepository functionKeyRepository, HotkeyRepository hotkeyRepository1) {
        this.hotkeyConfigRepository = hotkeyRepository;
        this.functionKeyRepository = functionKeyRepository;
        this.hotkeyRepository = hotkeyRepository1;
    }
    @GetMapping("/hotkey")
    public ResponseEntity<?> getHotkeys(@RequestParam(required = false) Integer funcId){
        SecurityUtil.requireAuthentication();
        Long userId = SecurityUtil.getCurrentUserId();
        if (userId == null){
            throw new SecurityException("User not found");
        }
        if (funcId == null){
            List<HotkeyConfig> configs = hotkeyConfigRepository.findAllByUserIdIs(userId);
            List<Map<?, ?>> userConfigs = new ArrayList<>();
            for (HotkeyConfig config : configs) {
                userConfigs.add(Map.of(
                        "keyId", config.getKeyId(),
                        "funcId", config.getFuncId(),
                        "createAt", config.getCreateAt()
                ));
            }
            return ResponseEntity.ok().body(userConfigs);
        }
        HotkeyConfigID id = new HotkeyConfigID(userId, funcId);
        Optional<HotkeyConfig> config = hotkeyConfigRepository.findById(id);
        if(config.isPresent()){
            return ResponseEntity.ok(config.get());
        } else {
            return ResponseEntity.notFound().build();
        }
    }

    @PostMapping("/hotkey")
    public ResponseEntity<?> createKeyConfig(@RequestParam int funcId,
                                             @RequestParam int keyId){
        SecurityUtil.requireAuthentication();
        Long userId = SecurityUtil.getCurrentUserId();

        if (hotkeyConfigRepository.existsByKeyId(keyId)){
            throw new ApiException(HttpStatus.CONFLICT, "Conflict Key Id");
        }
        Optional<Hotkey> hotkey = hotkeyRepository.findById(keyId);
        Optional<Function> function = functionKeyRepository.findById(funcId);

        hotkey.orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "Invalid Hotkey Id"));
        function.orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "Invalid Function Id"));

        HotkeyConfigID id = new HotkeyConfigID(userId, funcId);
        if (hotkeyConfigRepository.existsById(id)) {
            throw new ApiException(HttpStatus.CONFLICT, "Hotkey config already exists");
        }

        HotkeyConfig config = HotkeyConfig
                .builder()
                .funcId(funcId)
                .userId(userId)
                .keyId(keyId)
                .build();
        HotkeyConfig saved = hotkeyConfigRepository.save(config);

        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    @PutMapping("/hotkey")
    public ResponseEntity<?> updateKeyConfig(@RequestParam int funcId,
                                             @RequestParam int keyId){
        SecurityUtil.requireAuthentication();
        Long userId = SecurityUtil.getCurrentUserId();

        if (hotkeyConfigRepository.existsByKeyId(keyId)){
            throw new ApiException(HttpStatus.CONFLICT, "Conflict Key Id");
        }
        Optional<Hotkey> hotkey = hotkeyRepository.findById(keyId);
        Optional<Function> function = functionKeyRepository.findById(funcId);

        hotkey.orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "Invalid Hotkey Id"));
        function.orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "Invalid Function Id"));

        HotkeyConfigID id = new HotkeyConfigID(userId, funcId);
        Optional<HotkeyConfig> existing = hotkeyConfigRepository.findById(id);
        if (existing.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        HotkeyConfig config = existing.get();
        config.setKeyId(keyId);
        HotkeyConfig saved = hotkeyConfigRepository.save(config);

        return ResponseEntity.ok(saved);
    }

    @DeleteMapping("/hotkey")
    public ResponseEntity<?> resetConfig(@RequestParam int funcId){
        SecurityUtil.requireAuthentication();
        Long userId = SecurityUtil.getCurrentUserId();
        HotkeyConfigID id = new HotkeyConfigID(userId, funcId);
        if (!hotkeyConfigRepository.existsById(id)) {
            return ResponseEntity.notFound().build();
        }
        hotkeyConfigRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }
}
