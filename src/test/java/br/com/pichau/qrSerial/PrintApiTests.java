package br.com.pichau.qrSerial;

import br.com.pichau.qrSerial.controller.PrintController;
import br.com.pichau.qrSerial.exception.PrintGenerationException;
import br.com.pichau.qrSerial.handller.ApiExceptionHandler;
import br.com.pichau.qrSerial.service.PrintService;
import br.com.pichau.qrSerial.service.QrCodeService;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class PrintApiTests {
    @Test
    void returnsPreviewAsImageWithoutDownload() throws Exception {
        var response = api(new PrintService(new QrCodeService())).perform(post("/api/print/preview")
                .contentType(MediaType.APPLICATION_JSON).content("{\"serials\":[\"ABC-123\"]}"))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.IMAGE_PNG))
            .andExpect(header().doesNotExist("Content-Disposition"))
            .andReturn().getResponse();
        var image = javax.imageio.ImageIO.read(new java.io.ByteArrayInputStream(response.getContentAsByteArray()));
        org.junit.jupiter.api.Assertions.assertNotNull(image);
        org.junit.jupiter.api.Assertions.assertTrue(image.getHeight() > image.getWidth());
    }
    private MockMvc api(PrintService service) {
        return MockMvcBuilders.standaloneSetup(new PrintController(service))
            .setControllerAdvice(new ApiExceptionHandler()).build();
    }

    @Test
    void returnsPdfAsAttachment() throws Exception {
        api(new PrintService(new QrCodeService())).perform(post("/api/print")
                .contentType(MediaType.APPLICATION_JSON).content("{\"serials\":[\"ABC-123\"]}"))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_PDF))
            .andExpect(header().string("Content-Disposition", "attachment; filename=seriais.pdf"));
    }

    @Test
    void returnsStructuredValidationAndMalformedJsonErrors() throws Exception {
        var api = api(new PrintService(new QrCodeService()));
        api.perform(post("/api/print").contentType(MediaType.APPLICATION_JSON).content("{\"serials\":[]}"))
            .andExpect(status().isBadRequest())
            .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.detail").value("Informe de 1 a 500 seriais."));
        api.perform(post("/api/print").contentType(MediaType.APPLICATION_JSON).content("{"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.detail").value("Lote inválido."));
    }

    @Test
    void hidesTechnicalDetailsOnGenerationFailure() throws Exception {
        var service = mock(PrintService.class);
        when(service.generate(anyList(), isNull())).thenThrow(new PrintGenerationException(new java.io.IOException("internal path")));
        api(service).perform(post("/api/print").contentType(MediaType.APPLICATION_JSON)
                .content("{\"serials\":[\"ABC\"]}"))
            .andExpect(status().isInternalServerError())
            .andExpect(jsonPath("$.detail").value("Erro ao gerar PDF."));
    }
}
