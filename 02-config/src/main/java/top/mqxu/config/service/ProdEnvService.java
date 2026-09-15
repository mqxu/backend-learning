package top.mqxu.config.service;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

/**
 * @author mqxu
 * @date 2026/9/16
 * @description 生产环境实现，仅在 prod 环境生效
 **/
@Service
@Profile("prod")
public class ProdEnvService implements EnvService {

    @Override
    public String envInfo() {
        return "我是 prod 环境专属的 Bean";
    }
}
