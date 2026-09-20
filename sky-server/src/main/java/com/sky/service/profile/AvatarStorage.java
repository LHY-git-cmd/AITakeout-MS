package com.sky.service.profile;

/** 用户头像对象存储适配层，隔离具体云存储实现。 */
public interface AvatarStorage {
    String upload(byte[] content, String objectName);
}
