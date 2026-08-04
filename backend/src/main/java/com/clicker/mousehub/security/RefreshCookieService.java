package com.clicker.mousehub.security;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
public class RefreshCookieService {
    public static final String USER_COOKIE = "clicker_refresh";
    public static final String ADMIN_COOKIE = "clicker_admin_refresh";
    private final boolean secure;

    public RefreshCookieService(@Value("${app.auth.secure-cookies:false}") boolean secure) { this.secure = secure; }

    public String read(HttpServletRequest request, boolean admin) {
        String name = admin ? ADMIN_COOKIE : USER_COOKIE;
        if (request.getCookies() == null) return null;
        for (Cookie cookie : request.getCookies()) if (name.equals(cookie.getName())) return cookie.getValue();
        return null;
    }

    public void write(HttpServletResponse response, boolean admin, String token, Duration maxAge) {
        String name = admin ? ADMIN_COOKIE : USER_COOKIE;
        ResponseCookie cookie = ResponseCookie.from(name, token).httpOnly(true).secure(secure)
                .sameSite("Strict").path("/api/v1").maxAge(maxAge).build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    public void clear(HttpServletResponse response, boolean admin) { write(response, admin, "", Duration.ZERO); }
}
