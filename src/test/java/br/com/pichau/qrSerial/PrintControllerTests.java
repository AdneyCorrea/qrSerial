package br.com.pichau.qrSerial;

import java.util.List;
import br.com.pichau.qrSerial.controller.PrintController;
import br.com.pichau.qrSerial.exception.InvalidBatchException;
import br.com.pichau.qrSerial.service.PrintService;
import br.com.pichau.qrSerial.service.QrCodeService;
import com.google.zxing.BinaryBitmap;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.RGBLuminanceSource;
import com.google.zxing.common.HybridBinarizer;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.PDFTextStripperByArea;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PrintControllerTests {
    private final PrintController controller = new PrintController(new PrintService(new QrCodeService()));

    @Test
    void createsSinglePageWithReadableQrAndSerials() throws Exception {
        var serials = List.of("PROD-001", "PROD-002", "PROD-003", "PROD-004", "PROD-005", "PROD-006");
        var response = controller.print(new PrintController.PrintRequest(serials, "Impressão de produção"));
        try (var pdf = Loader.loadPDF(response.getBody())) {
            assertEquals(1, pdf.getNumberOfPages());
            String text = new PDFTextStripper().getText(pdf);
            for (String serial : serials) assertTrue(text.contains(serial));
            assertEquals(1, text.split("Impressão de produção", -1).length - 1);
            var column = new PDFTextStripperByArea();
            column.addRegion("serials", new java.awt.geom.Rectangle2D.Float(36, 90, 174, 680));
            column.extractRegions(pdf.getPage(0));
            for (String serial : serials.subList(0, 6)) {
                assertTrue(column.getTextForRegion("serials").contains(serial));
            }
            var image = new PDFRenderer(pdf).renderImageWithDPI(0, 144);
            // Recorta o primeiro QR na imagem renderizada a 144 DPI.
            var crop = image.getSubimage(499, 190, 194, 196);
            int[] pixels = crop.getRGB(0, 0, crop.getWidth(), crop.getHeight(), null, 0, crop.getWidth());
            var source = new RGBLuminanceSource(crop.getWidth(), crop.getHeight(), pixels);
            var decoded = new MultiFormatReader().decode(new BinaryBitmap(new HybridBinarizer(source)));
            assertEquals(serials.get(0), decoded.getText());
            // O segundo código fica na terceira coluna e na linha seguinte.
            crop = image.getSubimage(848, 410, 194, 196);
            pixels = crop.getRGB(0, 0, crop.getWidth(), crop.getHeight(), null, 0, crop.getWidth());
            source = new RGBLuminanceSource(crop.getWidth(), crop.getHeight(), pixels);
            assertEquals(serials.get(1), new MultiFormatReader().decode(new BinaryBitmap(new HybridBinarizer(source))).getText());
        }
    }

    @Test
    void fitsEntireBatchOnOneA4Page() throws Exception {
        for (int count : List.of(7, 30, 100, 500)) {
            var serials = java.util.stream.IntStream.range(0, count)
                .mapToObj(index -> "SERIAL-" + String.format("%04d", index)).toList();
            var response = controller.print(new PrintController.PrintRequest(serials, "W".repeat(120)));
            try (var pdf = Loader.loadPDF(response.getBody())) {
                assertEquals(1, pdf.getNumberOfPages());
                assertEquals(org.apache.pdfbox.pdmodel.common.PDRectangle.A4.getHeight(), pdf.getPage(0).getMediaBox().getHeight());
                String text = new PDFTextStripper().getText(pdf);
                for (String serial : serials) assertTrue(text.contains(serial), serial);
            }
        }
    }

    @Test
    void rejectsEmptyBatchAndInvalidSerials() {
        assertThrows(InvalidBatchException.class, () -> controller.print(new PrintController.PrintRequest(List.of(), null)));
        assertThrows(InvalidBatchException.class, () -> controller.print(new PrintController.PrintRequest(List.of("\n"), null)));
        assertThrows(InvalidBatchException.class, () -> controller.print(new PrintController.PrintRequest(List.of("a".repeat(121)), null)));
        assertThrows(InvalidBatchException.class, () -> controller.print(new PrintController.PrintRequest(null, null)));
        assertThrows(InvalidBatchException.class, () -> controller.print(new PrintController.PrintRequest(List.of("ABC", "ABC"), null)));
    }

    @Test
    void rejectsInvalidTitles() {
        for (String title : List.of("a".repeat(121), "Título\nOutro", "Teste 😀")) {
            assertThrows(InvalidBatchException.class,
                () -> controller.print(new PrintController.PrintRequest(List.of("ABC"), title)));
        }
    }
}
