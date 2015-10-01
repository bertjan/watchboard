package nl.revolution.watchboard.utils;

import org.apache.commons.lang.StringUtils;

import javax.servlet.http.HttpServletRequest;
import java.util.Optional;

public final class IpAddressUtil {

    private static final String X_FORWARDED_FOR = "X-Forwarded-For";

    private IpAddressUtil() {
        // May not be instantiated
    }

    /**
     * Retrieves the client IP when behind a proxy.
     *
     * @param request the originating request
     * @return The IP. Null if client is not behind a proxy
     */
    public static Optional<String> getClientIpBehindProxy(HttpServletRequest request) {
        return getClientIpBehindProxy(request.getHeader(X_FORWARDED_FOR));
    }

    /**
     * Get the client ip. If behind a proxy, the ip behind the proxy will be given.
     * When not behind a proxy, the remote address of the request is returned.
     *
     * @param request the originating request
     * @return the ip address of the client
     */
    public static String getClientIp(HttpServletRequest request) {
        return getClientIpBehindProxy(request).orElseGet(request::getRemoteAddr);
    }

    private static Optional<String> getClientIpBehindProxy(String headerValue) {
        String[] ips = StringUtils.split(headerValue, ",");
        if (ips != null && ips.length > 0) {
            return Optional.of(ips[0]);
        }
        return Optional.empty();
    }
}