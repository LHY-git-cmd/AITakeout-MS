package com.sky.controller.admin;

import com.sky.agent.AgentClient;
import com.sky.result.Result;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/admin/agent")
@RequiredArgsConstructor
public class AgentController {

    private final AgentClient agentClient;

    @GetMapping("/health")
    public Result<String> health() {
        return Result.success(agentClient.healthCheck());
    }

    @PostMapping("/query")
    public Result<String> query(@RequestBody Map<String, Object> params) {
        return Result.success(agentClient.query(params));
    }

    @PostMapping("/stream")
    public SseEmitter stream(@RequestBody Map<String, Object> params) {
        SseEmitter emitter = new SseEmitter(60000L);

        // 在独立线程中执行真正的流式转发，避免阻塞 Servlet 线程
        new Thread(() -> {
            try {
                agentClient.streamQuery(params, event -> {
                    try {
                        emitter.send(SseEmitter.event().data(event));
                    } catch (Exception e) {
                        log.error("推送SSE事件到前端失败", e);
                    }
                });
                emitter.complete();
            } catch (Exception e) {
                log.error("Agent流式查询异常", e);
                emitter.completeWithError(e);
            }
        }, "agent-stream-thread").start();

        return emitter;
    }
}