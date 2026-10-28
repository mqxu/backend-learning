package top.mqxu.web.common.listener;

import jakarta.servlet.http.HttpSessionAttributeListener;
import jakarta.servlet.http.HttpSessionBindingEvent;
import jakarta.servlet.http.HttpSessionEvent;
import jakarta.servlet.http.HttpSessionListener;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * 在线人数监听器：会话创建加一、销毁减一，顺带记录会话属性的变化
 *
 * @author mqxu
 */
@Slf4j
@Component
public class OnlineCountListener implements HttpSessionListener, HttpSessionAttributeListener {

    private final AtomicInteger online = new AtomicInteger();

    public int getOnline() {
        return online.get();
    }

    @Override
    public void sessionCreated(HttpSessionEvent event) {
        log.info("会话创建：{}，当前在线 {}", event.getSession().getId(), online.incrementAndGet());
    }

    @Override
    public void sessionDestroyed(HttpSessionEvent event) {
        log.info("会话销毁：{}，当前在线 {}", event.getSession().getId(), online.decrementAndGet());
    }

    @Override
    public void attributeAdded(HttpSessionBindingEvent event) {
        log.info("会话属性新增：{} = {}", event.getName(), event.getValue());
    }

    @Override
    public void attributeReplaced(HttpSessionBindingEvent event) {
        log.info("会话属性替换：{}，旧值 {}", event.getName(), event.getValue());
    }

    @Override
    public void attributeRemoved(HttpSessionBindingEvent event) {
        log.info("会话属性移除：{}，原值 {}", event.getName(), event.getValue());
    }
}
