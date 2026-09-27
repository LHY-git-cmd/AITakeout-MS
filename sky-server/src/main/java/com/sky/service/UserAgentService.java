package com.sky.service;

/**
 * 用户端Agent业务边界。
 * 独立类型用于阻止用户Controller误注入管理端Agent实现。
 */
public interface UserAgentService extends AgentService {
}
