package br.com.pichau.qrSerial.service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import javax.imageio.ImageIO;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.rendering.PDFRenderer;
import java.util.HashSet;
import java.util.List;
import java.util.ArrayList;
import java.util.regex.Pattern;

import br.com.pichau.qrSerial.exception.InvalidBatchException;
import br.com.pichau.qrSerial.exception.PrintGenerationException;
import com.google.zxing.WriterException;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.springframework.stereotype.Service;

/** Valida o lote e organiza os QR Codes nas páginas A4. */
@Service
public class PrintService {
    private static final int MAX_BATCH_SIZE = 500;
    private static final Pattern SERIAL_PATTERN = Pattern.compile("[\\x20-\\x7E]{1,120}");
    // O PDF usa pontos: 72 pontos equivalem a uma polegada.
    private static final float QR_SIZE = 96;
    private static final float PAGE_MARGIN = 36;
    private static final float COLUMN_WIDTH = (PDRectangle.A4.getWidth() - PAGE_MARGIN * 2) / 3;
    private static final float TABLE_TOP = 753;
    private static final float ROW_SPACING = 110;
    private static final float FONT_SIZE = 11;
    private static final float TITLE_SIZE = 16;
    private static final float TITLE_SPACING = 20;
    private static final float TITLE_BASELINE = 780;
    private static final float HEADER_TOP = 802;

    private final QrCodeService qrCodeService;

    public PrintService(QrCodeService qrCodeService) {
        this.qrCodeService = qrCodeService;
    }

    /** A prévia e a impressão web usam a mesma página do PDF, renderizada a 200 DPI. */
    public byte[] preview(List<String> serials, String title) {
        byte[] pdf = generate(serials, title);
        try (var document = Loader.loadPDF(pdf); var output = new ByteArrayOutputStream()) {
            ImageIO.write(new PDFRenderer(document).renderImageWithDPI(0, 200), "png", output);
            return output.toByteArray();
        } catch (IOException exception) {
            throw new PrintGenerationException(exception);
        }
    }

    public byte[] generate(List<String> serials, String title) {
        validate(serials);
        try (var document = new PDDocument(); var output = new ByteArrayOutputStream()) {
            var font = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
            var titleFont = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
            String printTitle = validateTitle(title, titleFont);
            var page = new PDPage(PDRectangle.A4);
            document.addPage(page);
            try (var content = new PDPageContentStream(document, page)) {
                float tableTop = writeTitle(content, titleFont, printTitle);
                // Divide a área útil por todo o lote: o documento sempre tem uma única página.
                float rowHeight = Math.min(ROW_SPACING, (tableTop - PAGE_MARGIN) / serials.size());
                float qrSize = Math.min(QR_SIZE, rowHeight * 0.88f);
                drawColumns(content, serials.size(), tableTop, rowHeight);
                for (int index = 0; index < serials.size(); index++) {
                    float center = PAGE_MARGIN + COLUMN_WIDTH * (index % 2 == 0 ? 1.5f : 2.5f);
                    float rowCenter = tableTop - (index + 0.5f) * rowHeight;
                    String serial = serials.get(index);
                    var image = LosslessFactory.createFromImage(document, qrCodeService.generate(serial));
                    content.drawImage(image, center - qrSize / 2, rowCenter - qrSize / 2, qrSize, qrSize);
                    writeSerial(content, font, serial, rowCenter, rowHeight);
                }
            }
            document.save(output);
            return output.toByteArray();
        } catch (IOException | WriterException exception) {
            throw new PrintGenerationException(exception);
        }
    }

    private void validate(List<String> serials) {
        if (serials == null || serials.isEmpty() || serials.size() > MAX_BATCH_SIZE) {
            throw new InvalidBatchException("Informe de 1 a 500 seriais.");
        }
        var uniqueSerials = new HashSet<String>();
        for (String serial : serials) {
            // A fonte padrão do PDF suporta os caracteres permitidos nesta validação.
            if (serial == null || !SERIAL_PATTERN.matcher(serial).matches() || serial.isBlank()) {
                throw new InvalidBatchException("Serial inválido: use de 1 a 120 caracteres ASCII imprimíveis.");
            }
            if (!uniqueSerials.add(serial)) {
                throw new InvalidBatchException("Serial duplicado.");
            }
        }
    }

    private String validateTitle(String title, PDType1Font font) throws IOException {
        if (title == null) return "";
        if (title.length() > 120 || title.codePoints().anyMatch(Character::isISOControl)) {
            throw new InvalidBatchException("Título inválido: limite de 120 caracteres, sem quebras de linha.");
        }
        try {
            // Acentos portugueses são aceitos; símbolos sem glifo devem gerar erro de validação.
            font.encode(title);
        } catch (IllegalArgumentException exception) {
            throw new InvalidBatchException("Título com caracteres não suportados.");
        }
        return title.strip();
    }

    private void drawColumns(PDPageContentStream content, int rows, float top, float rowHeight) throws IOException {
        float bottom = top - rows * rowHeight;
        // A grade fecha todas as células, incluindo as bordas externas da lista.
        content.setStrokingColor(0.45f);
        content.setLineWidth(0.6f);
        for (int column = 1; column < 3; column++) {
            float x = PAGE_MARGIN + column * COLUMN_WIDTH;
            content.moveTo(x, bottom);
            content.lineTo(x, top);
        }
        content.stroke();
        for (int row = 0; row <= rows; row++) {
            float y = top - row * rowHeight;
            content.moveTo(PAGE_MARGIN, y);
            content.lineTo(PAGE_MARGIN + 3 * COLUMN_WIDTH, y);
        }
        content.stroke();
        content.setLineWidth(0.9f);
        content.addRect(PAGE_MARGIN, bottom, 3 * COLUMN_WIDTH, rows * rowHeight);
        content.stroke();
    }

    private float writeTitle(PDPageContentStream content, PDType1Font font, String title) throws IOException {
        var lines = wrapText(font, title, TITLE_SIZE, 3 * COLUMN_WIDTH - 24);
        if (lines.isEmpty()) return TABLE_TOP;
        // O cabeçalho integra o contorno da tabela e cresce quando o título quebra de linha.
        float tableTop = Math.min(TABLE_TOP, TITLE_BASELINE - (lines.size() - 1) * TITLE_SPACING - 24);
        content.setNonStrokingColor(0.95f);
        content.addRect(PAGE_MARGIN, tableTop, 3 * COLUMN_WIDTH, HEADER_TOP - tableTop);
        content.fill();
        content.setNonStrokingColor(0f);
        content.setStrokingColor(0.45f);
        content.setLineWidth(0.9f);
        content.addRect(PAGE_MARGIN, tableTop, 3 * COLUMN_WIDTH, HEADER_TOP - tableTop);
        content.stroke();
        int row = 0;
        for (String line : lines) {
            float width = font.getStringWidth(line) / 1000 * TITLE_SIZE;
            writeText(content, font, line, TITLE_SIZE, (PDRectangle.A4.getWidth() - width) / 2,
                TITLE_BASELINE - row++ * TITLE_SPACING);
        }
        // Títulos longos deslocam a lista para evitar sobreposição com a primeira linha.
        return tableTop;
    }

    private void writeSerial(PDPageContentStream content, PDType1Font font, String serial,
                             float rowCenter, float rowHeight) throws IOException {
        // Ajusta altura e largura sem truncar o serial ou invadir as colunas vizinhas.
        float textWidth = font.getStringWidth(serial) / 1000;
        float size = Math.min(FONT_SIZE, Math.min(rowHeight * 0.55f, (COLUMN_WIDTH - 16) / textWidth));
        var metrics = font.getFontDescriptor();
        float opticalOffset = (metrics.getCapHeight() + metrics.getDescent()) / 1000 * size / 2;
        float width = textWidth * size;
        writeText(content, font, serial, size, PAGE_MARGIN + (COLUMN_WIDTH - width) / 2,
            rowCenter - opticalOffset);
    }
    private List<String> wrapText(PDType1Font font, String text, float size, float maxWidth) throws IOException {
        // Mede os glifos para que seriais largos não invadam a coluna dos QR Codes.
        var lines = new ArrayList<String>();
        var line = new StringBuilder();
        for (int index = 0; index < text.length(); index++) {
            char character = text.charAt(index);
            if (!line.isEmpty() && font.getStringWidth(line.toString() + character) / 1000 * size > maxWidth) {
                lines.add(line.toString());
                line.setLength(0);
            }
            line.append(character);
        }
        if (!line.isEmpty()) lines.add(line.toString());
        return lines;
    }

    private void writeText(PDPageContentStream content, PDType1Font font, String text,
                           float size, float x, float y) throws IOException {
        content.beginText();
        content.setFont(font, size);
        content.newLineAtOffset(x, y);
        content.showText(text);
        content.endText();
    }
}

