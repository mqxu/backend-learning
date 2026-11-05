package top.mqxu.actuator.info;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.actuate.info.Info;
import org.springframework.boot.actuate.info.InfoContributor;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 往 /actuator/info 里塞自定义信息。
 * <p>
 * 部署后想知道"这个实例跑的是哪个版本、哪个环境"，看 info 端点比登录服务器查进程快得多。
 *
 * @author mqxu
 */
@Component
@RequiredArgsConstructor
public class AppInfoContributor implements InfoContributor {

    private final Environment environment;

    @Override
    public void contribute(Info.Builder builder) {
        Map<String, Object> module = new LinkedHashMap<>();
        module.put("name", environment.getProperty("spring.application.name", "unknown"));
        module.put("profiles", String.join(",", environment.getActiveProfiles()));
        module.put("port", environment.getProperty("server.port"));
        module.put("startedAt", LocalDateTime.now().toString());

        Map<String, Object> owner = new LinkedHashMap<>();
        owner.put("team", "backend-learning");
        owner.put("contact", "mqxu@example.com");

        builder.withDetail("module", module).withDetail("owner", owner);
    }
}
