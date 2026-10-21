package top.mqxu.toolkit.service;

import cn.hutool.core.img.Img;
import cn.hutool.core.img.ImgUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.extra.qrcode.QrCodeUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import top.mqxu.toolkit.common.BusinessException;
import top.mqxu.toolkit.common.ErrorCode;

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Font;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;

/**
 * 商品海报生成
 *
 * @author mqxu
 * @date 2026-10-21
 * @description 缩放、圆角、半透明底栏、文字、二维码，一次合成
 */
@Service
@RequiredArgsConstructor
public class PosterService {

    private static final int WIDTH = 600;
    private static final int BAR_HEIGHT = 180;

    public byte[] render(MultipartFile file, String title, String price, String qrContent) {
        BufferedImage source = loadSource(file);
        BufferedImage canvas = fit(source);
        // round 的参数是圆角半径占短边的比例，不是像素值
        canvas = ImgUtil.toBufferedImage(Img.from(canvas).round(0.03).getImg());

        Graphics2D g = canvas.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        // 底部半透明黑条，保证文字在任何底图上都能看清
        int barTop = canvas.getHeight() - BAR_HEIGHT;
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.62f));
        g.setColor(Color.BLACK);
        g.fillRect(0, barTop, canvas.getWidth(), BAR_HEIGHT);
        g.setComposite(AlphaComposite.SrcOver);

        g.setColor(Color.WHITE);
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 30));
        g.drawString(StrUtil.maxLength(StrUtil.blankToDefault(title, "商品名称"), 16),
                32, barTop + 62);

        g.setColor(new Color(0xFFB020));
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 42));
        g.drawString("¥ " + StrUtil.blankToDefault(price, "0.00"), 32, barTop + 130);

        String content = StrUtil.blankToDefault(qrContent, "https://example.com");
        BufferedImage qr = QrCodeUtil.generate(content, 140, 140);
        g.drawImage(qr, canvas.getWidth() - 172, canvas.getHeight() - 160, null);

        g.dispose();
        return ImgUtil.toBytes(canvas, "png");
    }

    /**
     * 没有上传图片时生成一张渐变底图，保证接口随时可测
     */
    private BufferedImage loadSource(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            BufferedImage image = new BufferedImage(WIDTH, 800, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = image.createGraphics();
            g.setPaint(new GradientPaint(0, 0, new Color(0x2F6FED),
                    0, 800, new Color(0x8A4BF0)));
            g.fillRect(0, 0, WIDTH, 800);
            g.setColor(new Color(255, 255, 255, 90));
            g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 120));
            g.drawString("商品图", 150, 420);
            g.dispose();
            return image;
        }
        try {
            return ImgUtil.toBufferedImage(Img.from(file.getInputStream()).getImg());
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "图片读取失败：" + e.getMessage());
        }
    }

    /**
     * 按宽度 600 等比缩放，高度至少 800，不足的补白
     */
    private BufferedImage fit(BufferedImage source) {
        double ratio = (double) WIDTH / source.getWidth();
        int height = Math.max(800, (int) Math.round(source.getHeight() * ratio));

        BufferedImage target = new BufferedImage(WIDTH, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = target.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, WIDTH, height);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(source, 0, 0, WIDTH, height, null);
        g.dispose();
        return target;
    }
}
