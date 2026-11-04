package br.com.pichau.qrSerial.service;

import java.awt.image.BufferedImage;
import java.util.Map;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.qrcode.QRCodeWriter;
import org.springframework.stereotype.Service;

/** Gera a imagem do código independentemente do formato do arquivo de impressão. */
@Service
public class QrCodeService {
    private static final int IMAGE_SIZE = 360;
    private static final int QUIET_ZONE_MODULES = 4;

    public BufferedImage generate(String serial) throws WriterException {
        // A margem branca faz parte do QR e facilita a leitura após a impressão.
        var matrix = new QRCodeWriter().encode(serial, BarcodeFormat.QR_CODE, IMAGE_SIZE, IMAGE_SIZE,
            Map.of(EncodeHintType.MARGIN, QUIET_ZONE_MODULES, EncodeHintType.CHARACTER_SET, "UTF-8"));
        var image = new BufferedImage(matrix.getWidth(), matrix.getHeight(), BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < matrix.getHeight(); y++) {
            for (int x = 0; x < matrix.getWidth(); x++) {
                image.setRGB(x, y, matrix.get(x, y) ? 0x000000 : 0xffffff);
            }
        }
        return image;
    }
}
