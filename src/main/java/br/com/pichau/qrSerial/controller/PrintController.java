package br.com.pichau.qrSerial.controller;

import java.util.List;
import java.nio.charset.StandardCharsets;

import br.com.pichau.qrSerial.service.PrintService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ContentDisposition;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Recebe o lote pela API e devolve o arquivo para download. */
@RestController
@RequestMapping("/api/print")
public class PrintController {
    private final PrintService printService;

    public PrintController(PrintService printService) {
        this.printService = printService;
    }

    public record PrintRequest(List<String> serials, String title) {}

    /** Renderiza o próprio PDF para manter a prévia fiel em navegadores sem leitor de PDF. */
    @PostMapping(value = "/preview", produces = MediaType.IMAGE_PNG_VALUE)
    public ResponseEntity<byte[]> preview(@RequestBody PrintRequest request) {
        return ResponseEntity.ok().body(printService.preview(request.serials(), request.title()));
    }

    @PostMapping(produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> print(@RequestBody PrintRequest request) {
        byte[] pdf = printService.generate(request.serials(), request.title());
        // Remove caracteres proibidos em nomes de arquivo no Windows e no Android.
        String title = request.title() == null ? "" : request.title()
            .replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]", "")
            .strip().replaceAll("[. ]+$", "");
        String disposition = title.isEmpty() ? "attachment; filename=seriais.pdf"
            : ContentDisposition.attachment()
                .filename(title + " seriais.pdf", StandardCharsets.UTF_8).build().toString();
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, disposition)
            .body(pdf);
    }
}
