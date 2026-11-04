package br.com.pichau.qrSerial.handller;

import br.com.pichau.qrSerial.exception.InvalidBatchException;
import br.com.pichau.qrSerial.exception.PrintGenerationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Centraliza as respostas de erro, mantendo o controller focado no transporte HTTP. */
@RestControllerAdvice
public class ApiExceptionHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(InvalidBatchException.class)
    public ProblemDetail handleInvalidBatch(InvalidBatchException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, exception.getMessage());
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ProblemDetail handleInvalidBody(HttpMessageNotReadableException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Lote inválido.");
    }

    @ExceptionHandler(PrintGenerationException.class)
    public ProblemDetail handleGeneration(PrintGenerationException exception) {
        LOGGER.error("Falha na geração do PDF", exception);
        return ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, exception.getMessage());
    }
}
