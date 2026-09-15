package top.mqxu.config.service;

/**
 * @author mqxu
 * @date 2026/9/16
 * @description 环境信息服务，用于演示 @Profile 注解
 * <p>
 * 同一个接口，不同环境下注入不同的实现。
 **/
public interface EnvService {

    String envInfo();
}