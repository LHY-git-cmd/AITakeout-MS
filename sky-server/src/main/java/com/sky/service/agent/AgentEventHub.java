package com.sky.service.agent;

import com.sky.agent.model.AgentStreamEvent;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/** Java进程内的前端SSE事件扇出中心。 */
@Component
public class AgentEventHub {

    private final Map<String, CopyOnWriteArrayList<Consumer<AgentStreamEvent>>> subscribers =
            new ConcurrentHashMap<>();

    /**
     * 订阅指定任务的SSE事件流
     *
     * @param taskId   任务ID
     * @param consumer 事件消费回调
     * @return 可关闭的订阅句柄，调用close()取消订阅
     */
    public AutoCloseable subscribe(String taskId, Consumer<AgentStreamEvent> consumer) {
        subscribers.computeIfAbsent(taskId, ignored -> new CopyOnWriteArrayList<>()).add(consumer);
        return () -> remove(taskId, consumer);
    }

    /**
     * 向订阅了该任务的所有消费者广播事件
     */
    public void publish(AgentStreamEvent event) {
        subscribers.getOrDefault(event.taskId(), new CopyOnWriteArrayList<>())
                .forEach(consumer -> consumer.accept(event));
    }

    /** 取消单个消费者订阅，任务无剩余订阅者时清理整个taskId条目 */
    private void remove(String taskId, Consumer<AgentStreamEvent> consumer) {
        CopyOnWriteArrayList<Consumer<AgentStreamEvent>> taskSubscribers = subscribers.get(taskId);
        if (taskSubscribers == null) {
            return;
        }
        taskSubscribers.remove(consumer);
        if (taskSubscribers.isEmpty()) {
            subscribers.remove(taskId, taskSubscribers);
        }
    }
}