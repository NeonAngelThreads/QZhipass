package org.microsoft.qintelipass.controllers;

import org.microsoft.qintelipass.entity.hotkey.Function;
import org.microsoft.qintelipass.entity.hotkey.Hotkey;
import org.microsoft.qintelipass.repository.FunctionKeyRepository;
import org.microsoft.qintelipass.repository.HotkeyRepository;
import org.microsoft.qintelipass.util.security.SecurityUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("api/v1/configs/registry")
public class ConfigurationRegistryController {
    private final HotkeyRepository hotkeyRepository;
    private final FunctionKeyRepository functionRepository;
    @Autowired
    public ConfigurationRegistryController(HotkeyRepository hotkeyRepository, FunctionKeyRepository functionRepository) {
        this.hotkeyRepository = hotkeyRepository;
        this.functionRepository = functionRepository;
    }

    @GetMapping("/hotkeys")
    private ResponseEntity<?> getHotkeyRegistry(){
        SecurityUtil.requireAuthentication();
        List<Hotkey> keys = hotkeyRepository.findAll();
        Map<Number, String> keyMap = new HashMap<>();
        for (Hotkey key : keys) {
            keyMap.put(key.getKeyId(), key.getKeyName());
        }

        return ResponseEntity.ok(Map.of(
                "success", true,
                "mapping", keyMap
        ));
    }

    @GetMapping("/functions")
    private ResponseEntity<?> getFunctionKeyRegistry(){
        SecurityUtil.requireAuthentication();
        List<Function> keys = functionRepository.findAll();
        Map<Number, String> keyMap = new HashMap<>();
        for (Function key : keys) {
            keyMap.put(key.getFuncId(), key.getFuncName());
        }
        return ResponseEntity.ok(Map.of(
                "success", true,
                "mapping", keyMap
        ));
    }
}
