package com.jobfitax.backend.analysis.source;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;

import org.springframework.stereotype.Component;

@Component
public class PublicUrlValidator {

    public URI validate(String value) {
        URI uri = URI.create(value);
        String scheme = uri.getScheme();

        if (!("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme))
                || uri.getHost() == null) {
            throw new IllegalArgumentException("http 또는 https 공개 URL만 사용할 수 있습니다.");
        }

        try {
            for (InetAddress address : InetAddress.getAllByName(uri.getHost())) {
                if (!isPublicAddress(address)) {
                    throw new IllegalArgumentException("서버 내부 또는 로컬 주소에는 접근할 수 없습니다.");
                }
            }
        } catch (UnknownHostException exception) {
            throw new IllegalArgumentException("URL의 호스트를 찾을 수 없습니다.", exception);
        }

        return uri;
    }

    private boolean isPublicAddress(InetAddress address) {
        if (address.isAnyLocalAddress()
                || address.isLoopbackAddress()
                || address.isLinkLocalAddress()
                || address.isSiteLocalAddress()
                || address.isMulticastAddress()) {
            return false;
        }

        byte[] bytes = address.getAddress();
        return bytes.length != 16 || (bytes[0] & 0xfe) != 0xfc;
    }
}
