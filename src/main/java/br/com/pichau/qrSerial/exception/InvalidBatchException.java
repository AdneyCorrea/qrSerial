package br.com.pichau.qrSerial.exception;

/** Indica um lote que precisa ser corrigido antes da impressão. */
public class InvalidBatchException extends RuntimeException {
    public InvalidBatchException(String message) {
        super(message);
    }
}
