package top.mqxu.config.service;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

/**
 * @author mqxu
 * @date 2026/9/16
 * @description 开发环境实现，仅在 dev 环境生效
 **/
@Service
@Profile("dev")
public class DevEnvService implements EnvService {

    @Override
    public String envInfo() {
        return "我是 dev 环境专属的 Bean";
    }
}