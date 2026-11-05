package top.mqxu.web.listener;

import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletContextListener;
import jakarta.servlet.annotation.WebListener;
import lombok.extern.slf4j.Slf4j;

/**
 * 应用生命周期监听器：容器启动和关闭各触发一次。
 * 适合放"整个应用只做一次"的初始化与清理，比如加载字典、关闭线程池。
 *
 * @author mqxu
 */
@Slf4j
@WebListener
public class AppLifecycleListener implements ServletContextListener {

    @Override
    public void contextInitialized(ServletContextEvent event) {
        String contextPath = event.getServletContext().getContextPath();
        log.info("ServletContext 初始化，contextPath=[{}]", contextPath.isEmpty() ? "/" : contextPath);
    }

    @Override
    public void contextDestroyed(ServletContextEvent event) {
        log.info("ServletContext 销毁，应用即将退出");
    }
}
