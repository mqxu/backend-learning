package top.mqxu.web.common.listener;

import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletContextListener;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 应用级监听器：跟着 Web 容器启动、关闭各触发一次
 *
 * @author mqxu
 */
@Slf4j
@Component
public class AppLifecycleListener implements ServletContextListener {

    @Override
    public void contextInitialized(ServletContextEvent event) {
        log.info("应用启动：contextInitialized，容器 {}", event.getServletContext().getServerInfo());
    }

    @Override
    public void contextDestroyed(ServletContextEvent event) {
        log.info("应用关闭：contextDestroyed，开始释放资源");
    }
}
