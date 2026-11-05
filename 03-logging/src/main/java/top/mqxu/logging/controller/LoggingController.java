package top.mqxu.logging.controller;

import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import top.mqxu.logging.common.LogMaskUtil;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * @author mqxu
 * @date 2026/9/16
 * @description LoggingController
 **/
@RestController
@RequestMapping("/logging")
@Slf4j
public class LoggingController {

    @GetMapping("/hello")
    public String hello() {
        return "日志管理 模块";
    }

    /**
     * 基础演示：级别、参数占位符、脱敏各来一次。
     */
    @GetMapping("/demo")
    public String demo() {
        log.info("处理 /logging/demo 请求");
        log.warn("这是一条警告日志");
        log.error("这是一条错误日志");
        String name = "张三";
        log.info("用户 {} 登录成功", name);          // 推荐：只有真的输出时才拼字符串
        log.info("用户 " + name + " 登录成功");       // 禁止：无论级别是否开启都会拼接
        log.info("用户手机号：{}", LogMaskUtil.maskPhone("13812345678"));
        return "ok";
    }

    /**
     * 五级日志各打一条。当前配置 top.mqxu 包是 DEBUG，所以 trace 不输出。
     */
    @GetMapping("/levels")
    public Map<String, Object> levels() {
        log.trace("TRACE 级别：最细，只在排查具体某行逻辑时临时打开");
        log.debug("DEBUG 级别：开发期看流程，生产关掉");
        log.info("INFO 级别：关键业务节点，生产保留");
        log.warn("WARN 级别：能继续跑但值得看一眼");
        log.error("ERROR 级别：已经出错，需要人介入");

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("traceId", MDC.get("traceId"));
        result.put("已输出级别", "DEBUG / INFO / WARN / ERROR");
        result.put("被过滤级别", "TRACE（阈值是 DEBUG）");
        result.put("查看方式", "控制台或 logs/app.log 中按 traceId 搜索");
        return result;
    }

    /**
     * 脱敏演示：入参是明文，日志里只出现脱敏后的值。
     */
    @GetMapping("/mask")
    public Map<String, Object> mask(@RequestParam(defaultValue = "13812345678") String phone,
                                    @RequestParam(defaultValue = "320102200510241234") String idCard,
                                    @RequestParam(defaultValue = "zhangsan@qq.com") String email,
                                    @RequestParam(defaultValue = "张三") String name) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("phone", LogMaskUtil.maskPhone(phone));
        result.put("idCard", LogMaskUtil.maskIdCard(idCard));
        result.put("email", LogMaskUtil.maskEmail(email));
        result.put("name", LogMaskUtil.maskName(name));
        log.info("用户信息已脱敏：phone={}, idCard={}, email={}",
                result.get("phone"), result.get("idCard"), result.get("email"));
        return result;
    }

    /**
     * 异常日志：error 只打一次并带堆栈，不要再 message + 堆栈各打一条。
     */
    @GetMapping("/error")
    public ResponseEntity<Map<String, Object>> error() {
        try {
            throw new IllegalStateException("模拟业务失败：库存不足");
        } catch (IllegalStateException e) {
            log.error("处理订单失败，orderId=20261104001", e);
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("message", e.getMessage());
            body.put("traceId", MDC.get("traceId"));
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
        }
    }
}
