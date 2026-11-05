package top.mqxu.web.controller;

import jakarta.servlet.http.HttpSession;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import top.mqxu.web.common.Result;
import top.mqxu.web.listener.OnlineCountListener;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 演示 Filter / Interceptor / Listener 三者的生效位置与差别。
 * 这些接口本身没有业务含义，只为观察请求链路上的各个钩子。
 *
 * @author mqxu
 */
@Slf4j
@RestController
@RequestMapping("/demo")
public class DemoController {

    /**
     * 被 ApiKeyFilter 拦截，必须在请求头带 X-Api-Key: demo-key。
     */
    @GetMapping("/secure")
    public Result<String> secure() {
        return Result.ok("通过 ApiKeyFilter 校验，已进入控制器");
    }

    /**
     * 被 @WebFilter 注解注册的 AnnotationDemoFilter 拦截。
     */
    @GetMapping("/annotation/hello")
    public Result<String> annotation() {
        return Result.ok("通过 @WebFilter 注解过滤器");
    }

    /**
     * 演示参数清洗：请求里带前后空格，到这里已经被去掉。
     */
    @GetMapping("/trim")
    public Result<Map<String, Object>> trim(@RequestParam(defaultValue = "") String keyword) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("收到参数", keyword);
        data.put("长度", keyword.length());
        data.put("说明", "ParameterCleanFilter 已去掉两端空格");
        return Result.ok(data);
    }

    /**
     * 演示耗时统计：睡 ms 毫秒，观察 Filter 与 Interceptor 分别统计出的耗时。
     */
    @GetMapping("/slow")
    public Result<String> slow(@RequestParam(defaultValue = "200") long ms) throws InterruptedException {
        Thread.sleep(ms);
        return Result.ok("已耗时 " + ms + " ms");
    }

    /**
     * 被 TokenInterceptor 拦截，必须在请求头带 X-Token: demo-token。
     */
    @GetMapping("/token")
    public Result<String> token() {
        return Result.ok("通过 TokenInterceptor 校验，Controller 正常执行");
    }

    /**
     * 观察 preHandle / postHandle / afterCompletion 的调用顺序。
     */
    @GetMapping("/callback")
    public Result<String> callback() {
        log.info("Controller 方法执行中");
        return Result.ok("请查看控制台里三个回调的打印顺序");
    }

    /**
     * 主动创建 session，触发 OnlineCountListener 的 sessionCreated。
     */
    @GetMapping("/session")
    public Result<Map<String, Object>> session(HttpSession session) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("sessionId", session.getId());
        data.put("在线人数", OnlineCountListener.online());
        return Result.ok(data);
    }
}
