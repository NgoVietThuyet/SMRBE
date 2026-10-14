package vn.com.d2s.smr.config.audit;

import org.springframework.core.MethodParameter;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.servlet.mvc.method.annotation.RequestBodyAdviceAdapter;

import java.lang.reflect.Type;

/** Giữ lại body đã parse của các method có @Audited để AuditInterceptor ghi vào payload. */
@ControllerAdvice
public class AuditRequestBodyAdvice extends RequestBodyAdviceAdapter {

    static final String BODY_ATTRIBUTE = AuditRequestBodyAdvice.class.getName() + ".BODY";

    @Override
    public boolean supports(MethodParameter methodParameter, Type targetType,
                            Class<? extends HttpMessageConverter<?>> converterType) {
        return methodParameter.hasMethodAnnotation(Audited.class);
    }

    @Override
    public Object afterBodyRead(Object body, HttpInputMessage inputMessage, MethodParameter parameter,
                                Type targetType, Class<? extends HttpMessageConverter<?>> converterType) {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (attributes != null) {
            attributes.setAttribute(BODY_ATTRIBUTE, body, RequestAttributes.SCOPE_REQUEST);
        }
        return body;
    }
}
