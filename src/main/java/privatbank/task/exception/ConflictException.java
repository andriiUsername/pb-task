package privatbank.task.exception;

public abstract class ConflictException extends DomainException {

    protected ConflictException(String code, String message) {
        super(code, message);
    }
}
