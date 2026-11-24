package privatbank.task.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@NotBlank
@Size(max = 100)
@Pattern(regexp = "[\\x21-\\x7E]*", message = "must contain only printable ASCII characters without spaces")
@Constraint(validatedBy = {})
@Target({ElementType.PARAMETER, ElementType.FIELD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface IdempotencyKey {

    String message() default "invalid idempotency key";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
