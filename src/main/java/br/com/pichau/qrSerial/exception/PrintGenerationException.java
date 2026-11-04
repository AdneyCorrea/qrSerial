package br.com.pichau.qrSerial.exception;

/** Preserva a causa técnica sem expor detalhes internos pela API. */
public class PrintGenerationException extends RuntimeException {
    public PrintGenerationException(Throwable cause) {
        super("Erro ao gerar PDF.", cause);
    }
}
