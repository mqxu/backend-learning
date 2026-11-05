package top.mqxu.schedule.controller;

import com.alibaba.fastjson2.JSONObject;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import top.mqxu.schedule.service.DailyWeatherService;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * @author mqxu
 * @date 2026/10/21
 * @description WeatherController 天气接口：查询 + 手动发送邮件
 **/
@RestController
@RequestMapping("/schedule/weather")
public class WeatherController {

    @Resource
    private DailyWeatherService dailyWeatherService;

    /**
     * 查询今天天气（只查数据，不发邮件）
     */
    @GetMapping
    public Map<String, Object> today() {
        Map<String, Object> result = new LinkedHashMap<>();
        try {
            JSONObject today = dailyWeatherService.getTodayWeather();
            if (today == null) {
                result.put("success", false);
                result.put("message", "获取天气数据失败");
                return result;
            }
            result.put("success", true);
            result.put("data", today);
        } catch (IOException e) {
            result.put("success", false);
            result.put("message", "获取天气数据异常：" + e.getMessage());
        }
        return result;
    }

    /**
     * 手动触发发送天气邮件
     */
    @PostMapping("/send")
    public Map<String, Object> send() {
        boolean ok = dailyWeatherService.sendWeatherEmail();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", ok);
        result.put("message", ok ? "天气邮件已发送" : "天气邮件发送失败");
        return result;
    }
}
