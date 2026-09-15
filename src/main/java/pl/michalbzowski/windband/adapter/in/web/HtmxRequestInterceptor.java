package pl.michalbzowski.windband.adapter.in.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Interceptor that detects HTMX requests and exposes the flag as a request attribute.
 * Controllers can then access it via {@code @RequestAttribute("isHtmx") Boolean isHtmx}.
 */
public class HtmxRequestInterceptor implements HandlerInterceptor {

    public static final String HTMX_REQUEST_ATTRIBUTE = "isHtmx";
    public static final String HX_REQUEST_HEADER = "HX-Request";

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        boolean isHtmx = "true".equals(request.getHeader(HX_REQUEST_HEADER));
        request.setAttribute(HTMX_REQUEST_ATTRIBUTE, isHtmx);
        return true;
    }
}