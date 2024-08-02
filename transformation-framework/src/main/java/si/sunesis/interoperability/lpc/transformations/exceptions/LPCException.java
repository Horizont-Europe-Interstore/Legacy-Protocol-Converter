package si.sunesis.interoperability.lpc.transformations.exceptions;

public class LPCException extends Exception {

    public LPCException(String message) {
        super(message);
    }

    public LPCException(String message, Throwable cause) {
        super(message, cause);
    }
}