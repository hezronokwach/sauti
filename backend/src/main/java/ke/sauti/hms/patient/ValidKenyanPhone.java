package ke.sauti.hms.patient;

import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** A Kenyan mobile number in any common form (see {@link KenyanPhone}); empty is allowed. */
@Documented
@Constraint(validatedBy = ValidKenyanPhone.Validator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidKenyanPhone {

    String message() default "must be a Kenyan mobile number, for example 0712 345 678";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    class Validator implements ConstraintValidator<ValidKenyanPhone, String> {

        @Override
        public boolean isValid(String value, ConstraintValidatorContext context) {
            return value == null
                    || value.isBlank()
                    || KenyanPhone.normalise(value).isPresent();
        }
    }
}
