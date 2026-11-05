package top.mqxu.toolkit.controller;

import cn.hutool.core.lang.Validator;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import top.mqxu.toolkit.common.BusinessException;
import top.mqxu.toolkit.common.ErrorCode;
import top.mqxu.toolkit.common.Result;
import top.mqxu.toolkit.dto.QrStatusResponse;
import top.mqxu.toolkit.dto.QrTicketResponse;
import top.mqxu.toolkit.service.QrLoginService;

/**
 * 案例 2：扫码登录
 *
 * @author mqxu
 * @date 2026-10-21
 */
@RestController
@RequestMapping("/toolkit/qr-login")
@RequiredArgsConstructor
public class QrLoginController {

    private final QrLoginService qrLoginService;

    /**
     * 生成二维码
     */
    @PostMapping("/create")
    public Result<QrTicketResponse> create() {
        return Result.ok(qrLoginService.create());
    }

    /**
     * 前端轮询，建议 1 到 2 秒一次
     */
    @GetMapping("/status")
    public Result<QrStatusResponse> status(@RequestParam String ticket) {
        return Result.ok(qrLoginService.status(ticket));
    }

    /**
     * 模拟手机端扫码
     */
    @PostMapping("/scan")
    public Result<QrStatusResponse> scan(@RequestParam String ticket) {
        return Result.ok("扫码成功", qrLoginService.scan(ticket));
    }

    /**
     * 模拟手机端确认登录
     */
    @PostMapping("/confirm")
    public Result<QrStatusResponse> confirm(@RequestParam String ticket,
                                            @RequestParam String phone) {
        if (!Validator.isMobile(phone)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "手机号格式不正确");
        }
        return Result.ok("登录成功", qrLoginService.confirm(ticket, phone));
    }

    /**
     * 模拟手机端取消
     */
    @PostMapping("/cancel")
    public Result<QrStatusResponse> cancel(@RequestParam String ticket) {
        return Result.ok("已取消", qrLoginService.cancel(ticket));
    }

    /**
     * 终端里查看二维码，纯文本
     */
    @GetMapping(value = "/ascii", produces = "text/plain;charset=UTF-8")
    public String ascii(@RequestParam String ticket) {
        return qrLoginService.ascii(ticket);
    }
}
