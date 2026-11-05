package top.mqxu.toolkit.service;

import cn.hutool.cache.CacheUtil;
import cn.hutool.cache.impl.TimedCache;
import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.extra.qrcode.QrCodeUtil;
import cn.hutool.extra.qrcode.QrConfig;
import cn.hutool.jwt.JWTUtil;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import top.mqxu.toolkit.common.BusinessException;
import top.mqxu.toolkit.common.ErrorCode;
import top.mqxu.toolkit.config.ToolkitProperties;
import top.mqxu.toolkit.dto.QrStatusResponse;
import top.mqxu.toolkit.dto.QrTicketResponse;
import top.mqxu.toolkit.support.QrSession;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * 扫码登录服务
 *
 * @author mqxu
 * @date 2026-10-21
 * @description 生成带 logo 的二维码，用 ticket 串起扫码、确认、签发 token 的完整流程
 */
@Service
@RequiredArgsConstructor
public class QrLoginService {

    private final ToolkitProperties properties;

    private TimedCache<String, QrSession> cache;
    private Image logo;

    @PostConstruct
    void init() {
        long ttl = properties.getQrLogin().getExpireSeconds() * 1000;
        cache = CacheUtil.newTimedCache(ttl);
        cache.schedulePrune(30_000);
        logo = drawLogo();
    }

    @PreDestroy
    void destroy() {
        cache.cancelPruneSchedule();
        cache.clear();
    }

    /**
     * 创建二维码，二维码内容里带 ticket
     */
    public QrTicketResponse create() {
        String ticket = IdUtil.fastSimpleUUID();
        String content = "toolkit://qr-login?ticket=" + ticket;

        cache.put(ticket, new QrSession(ticket, content));

        QrConfig config = QrConfig.create()
                .setWidth(260)
                .setHeight(260)
                .setMargin(1)
                .setForeColor(new Color(0x1F2D3D))
                .setBackColor(Color.WHITE)
                .setErrorCorrection(ErrorCorrectionLevel.H)
                .setImg(logo);

        String image = toDataUri(QrCodeUtil.generateAsBase64(content, config, "png"));
        return new QrTicketResponse(ticket, content, image,
                properties.getQrLogin().getExpireSeconds());
    }

    /**
     * 手机端扫码，WAITING -> SCANNED
     */
    public QrStatusResponse scan(String ticket) {
        QrSession session = get(ticket);
        synchronized (session) {
            requireState(session, QrSession.State.WAITING, "该二维码已被扫描或已失效");
            session.setState(QrSession.State.SCANNED);
        }
        return toResponse(session);
    }

    /**
     * 手机端确认登录，SCANNED -> CONFIRMED，此时签发 token
     */
    public QrStatusResponse confirm(String ticket, String phone) {
        QrSession session = get(ticket);
        synchronized (session) {
            requireState(session, QrSession.State.SCANNED, "请先扫码再确认");
            session.setPhone(phone);
            session.setToken(issueToken(ticket, phone));
            session.setState(QrSession.State.CONFIRMED);
        }
        return toResponse(session);
    }

    /**
     * 手机端取消，任意未完成状态都可以取消
     */
    public QrStatusResponse cancel(String ticket) {
        QrSession session = get(ticket);
        synchronized (session) {
            if (session.getState() == QrSession.State.CONFIRMED) {
                throw new BusinessException(ErrorCode.QR_STATE_CONFLICT, "已确认的登录不能取消");
            }
            session.setState(QrSession.State.CANCELED);
        }
        return toResponse(session);
    }

    /**
     * 前端轮询状态
     */
    public QrStatusResponse status(String ticket) {
        return toResponse(get(ticket));
    }

    /**
     * 在终端里查看二维码，调试用
     */
    public String ascii(String ticket) {
        return QrCodeUtil.generateAsAsciiArt(get(ticket).getContent());
    }

    private String issueToken(String ticket, String phone) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("phone", phone);
        payload.put("ticket", ticket);
        payload.put("iat", System.currentTimeMillis() / 1000);
        return JWTUtil.createToken(payload,
                properties.getSign().getAppSecret().getBytes(StandardCharsets.UTF_8));
    }

    private QrSession get(String ticket) {
        if (StrUtil.isBlank(ticket)) {
            throw new BusinessException(ErrorCode.QR_NOT_FOUND);
        }
        QrSession session = cache.get(ticket, false);
        if (session == null) {
            throw new BusinessException(ErrorCode.QR_NOT_FOUND);
        }
        return session;
    }

    private void requireState(QrSession session, QrSession.State expected, String message) {
        if (session.getState() != expected) {
            throw new BusinessException(ErrorCode.QR_STATE_CONFLICT, message);
        }
    }

    private QrStatusResponse toResponse(QrSession session) {
        return new QrStatusResponse(session.getTicket(), session.getState().name(),
                session.getPhone(), session.getToken());
    }

    private String toDataUri(String base64) {
        return base64.startsWith("data:") ? base64 : "data:image/png;base64," + base64;
    }

    /**
     * 程序画一个方形 logo，避免依赖外部图片文件
     */
    private Image drawLogo() {
        int size = 56;
        BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(new Color(0x2F6FED));
        g.fillRoundRect(0, 0, size, size, 12, 12);
        g.setColor(Color.WHITE);
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 32));
        String text = "T";
        int textWidth = g.getFontMetrics().stringWidth(text);
        int textHeight = g.getFontMetrics().getAscent();
        g.drawString(text, (size - textWidth) / 2, (size + textHeight) / 2 - 4);
        g.dispose();
        return image;
    }
}
