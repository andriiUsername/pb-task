package privatbank.task.exception;

public abstract class NotFoundException extends DomainException {

    protected NotFoundException(String code, String message) {
        super(code, message);
    }
}
