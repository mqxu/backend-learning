package top.mqxu.web.listener;

import jakarta.servlet.annotation.WebListener;
import jakarta.servlet.http.HttpSessionEvent;
import jakarta.servlet.http.HttpSessionListener;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * 在线人数监听器：session 创建加一，销毁减一。
 * 这是 HttpSessionListener 最典型的用法——不侵入业务代码就能拿到在线量。
 *
 * @author mqxu
 */
@Slf4j
@WebListener
public class OnlineCountListener implements HttpSessionListener {

    private static final AtomicInteger ONLINE = new AtomicInteger();

    @Override
    public void sessionCreated(HttpSessionEvent event) {
        int now = ONLINE.incrementAndGet();
        log.info("session 创建，id={}，当前在线 {} 人", event.getSession().getId(), now);
    }

    @Override
    public void sessionDestroyed(HttpSessionEvent event) {
        int now = ONLINE.decrementAndGet();
        log.info("session 销毁，id={}，当前在线 {} 人", event.getSession().getId(), now);
    }

    public static int online() {
        return ONLINE.get();
    }
}
