package com.togethermusic.music.adapter;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 音乐源适配器注册表
 * Spring 自动收集所有 MusicSourceAdapter Bean，按 sourceCode 路由
 * 消除 if-else 链，新增音乐源只需实现接口并注册为 Bean
 */
@Slf4j
@Component
public class MusicSourceRegistry {

    private final Map<String, MusicSourceAdapter> adapters;

    public MusicSourceRegistry(List<MusicSourceAdapter> adapterList) {
        this.adapters = adapterList.stream()
                .collect(Collectors.toMap(MusicSourceAdapter::sourceCode, Function.identity()));
        log.info("Registered music source adapters: {}", adapters.keySet());
    }

    /**
     * 获取指定音乐源适配器，不存在时返回默认（网易云）
     */
    public MusicSourceAdapter get(String sourceCode) {
        MusicSourceAdapter adapter = adapters.get(sourceCode);
        if (adapter == null) {
            log.warn("Unknown music source '{}', falling back to 'wy'", sourceCode);
            return adapters.get("wy");
        }
        return adapter;
    }

    public boolean supports(String sourceCode) {
        return adapters.containsKey(sourceCode);
    }
}
