package kz.damulab.parentlink;

import java.util.EnumMap;
import java.util.Map;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import org.springframework.stereotype.Component;

@Component
public class QrCodeSvgRenderer {

    private static final int SIZE = 160;

    public String render(String content) {
        Map<EncodeHintType, Object> hints = new EnumMap<>(EncodeHintType.class);
        hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M);
        hints.put(EncodeHintType.MARGIN, 2);
        try {
            BitMatrix matrix = new QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, SIZE, SIZE, hints);
            StringBuilder svg = new StringBuilder(12_000);
            svg.append("<svg class=\"qr-code\" viewBox=\"0 0 ")
                    .append(SIZE)
                    .append(' ')
                    .append(SIZE)
                    .append("\" role=\"img\" aria-label=\"QR код привязки\" xmlns=\"http://www.w3.org/2000/svg\">");
            svg.append("<rect width=\"").append(SIZE).append("\" height=\"").append(SIZE).append("\" fill=\"#ffffff\"/>");
            for (int y = 0; y < SIZE; y++) {
                for (int x = 0; x < SIZE; x++) {
                    if (matrix.get(x, y)) {
                        svg.append("<rect x=\"").append(x).append("\" y=\"").append(y).append("\" width=\"1\" height=\"1\"/>");
                    }
                }
            }
            svg.append("</svg>");
            return svg.toString();
        } catch (WriterException exception) {
            throw new IllegalStateException("Could not render QR code", exception);
        }
    }
}
