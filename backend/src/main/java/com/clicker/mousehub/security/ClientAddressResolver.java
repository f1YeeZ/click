package com.clicker.mousehub.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.web.util.matcher.IpAddressMatcher;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

/** Resolves client addresses without trusting forwarding headers from arbitrary callers. */
@Component
public class ClientAddressResolver {
    private static final int MAX_FORWARDED_HOPS = 20;
    private final List<IpAddressMatcher> trustedProxies;

    public ClientAddressResolver(@Value("${app.security.trusted-proxies:}") String configuredProxies) {
        this.trustedProxies = Arrays.stream(configuredProxies.split(","))
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .map(IpAddressMatcher::new)
                .toList();
    }

    public String resolve(HttpServletRequest request) {
        String remote = normalize(request.getRemoteAddr());
        if (remote == null) return "unknown";
        if (!isTrusted(remote)) return remote;

        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded == null || forwarded.isBlank()) return remote;
        String[] hops = forwarded.split(",");
        int start = Math.max(0, hops.length - MAX_FORWARDED_HOPS);
        String leftmostValid = null;
        for (int index = hops.length - 1; index >= start; index--) {
            String address = normalize(hops[index]);
            if (address == null) continue;
            leftmostValid = address;
            if (!isTrusted(address)) return address;
        }
        return leftmostValid == null ? remote : leftmostValid;
    }

    private boolean isTrusted(String address) {
        return trustedProxies.stream().anyMatch(matcher -> matcher.matches(address));
    }

    private static String normalize(String value) {
        if (value == null) return null;
        String address = value.trim();
        if (address.startsWith("[") && address.endsWith("]")) {
            address = address.substring(1, address.length() - 1);
        }
        if (address.isBlank() || address.length() > 64) return null;
        boolean ipv4 = address.matches("[0-9.]+");
        boolean ipv6 = address.contains(":") && address.matches("[0-9A-Fa-f:.]+");
        return ipv4 || ipv6 ? address : null;
    }
}
