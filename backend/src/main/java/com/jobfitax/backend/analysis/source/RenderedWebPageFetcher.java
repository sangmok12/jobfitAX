package com.jobfitax.backend.analysis.source;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Frame;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.PlaywrightException;
import com.microsoft.playwright.Route;
import com.microsoft.playwright.options.LoadState;
import com.microsoft.playwright.options.WaitUntilState;

@Service
public class RenderedWebPageFetcher {

    private static final int MAX_RENDERED_HTML_CHARACTERS = 3 * 1024 * 1024;
    private static final Set<String> BLOCKED_RESOURCE_TYPES = Set.of("image", "media", "font");

    private final PublicUrlValidator urlValidator;

    public RenderedWebPageFetcher(PublicUrlValidator urlValidator) {
        this.urlValidator = urlValidator;
    }

    public RenderedPage fetch(String sourceUrl) {
        URI validatedSource = urlValidator.validate(sourceUrl);

        Map<String, String> playwrightEnvironment = new HashMap<>(System.getenv());
        playwrightEnvironment.put("PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD", "1");

        try (Playwright playwright = Playwright.create(
                    new Playwright.CreateOptions().setEnv(playwrightEnvironment));
                Browser browser = playwright.chromium().launch(
                        new BrowserType.LaunchOptions().setHeadless(true))) {
            Page page = browser.newPage();
            page.route("**/*", route -> handleRequest(route));
            page.navigate(
                    validatedSource.toString(),
                    new Page.NavigateOptions()
                            .setWaitUntil(WaitUntilState.DOMCONTENTLOADED)
                            .setTimeout(15_000));

            try {
                page.waitForLoadState(LoadState.NETWORKIDLE, new Page.WaitForLoadStateOptions().setTimeout(8_000));
            } catch (PlaywrightException ignored) {
                // 실시간 연결을 유지하는 페이지도 있으므로 현재까지 렌더링된 본문을 사용한다.
            }
            page.waitForTimeout(1_500);

            URI finalUri = urlValidator.validate(page.url());
            String html = page.content();
            if (html.length() > MAX_RENDERED_HTML_CHARACTERS) {
                throw new IllegalArgumentException("렌더링된 페이지 크기가 제한을 초과했습니다.");
            }
            List<RenderedFrame> frames = collectFrames(page);
            return new RenderedPage(finalUri, html, frames);
        }
    }

    private List<RenderedFrame> collectFrames(Page page) {
        List<RenderedFrame> frames = new ArrayList<>();
        int totalCharacters = 0;
        for (Frame frame : page.frames()) {
            if (frame == page.mainFrame() || frame.url() == null || frame.url().isBlank()
                    || "about:blank".equals(frame.url())) {
                continue;
            }
            try {
                URI frameUri = urlValidator.validate(frame.url());
                String frameHtml = frame.content();
                totalCharacters += frameHtml.length();
                if (totalCharacters > MAX_RENDERED_HTML_CHARACTERS) {
                    break;
                }
                frames.add(new RenderedFrame(frameUri, frameHtml));
            } catch (IllegalArgumentException | PlaywrightException ignored) {
                // 공개 주소 검증에 실패하거나 읽을 수 없는 외부 프레임은 사용하지 않는다.
            }
        }
        return List.copyOf(frames);
    }

    private void handleRequest(Route route) {
        try {
            String resourceType = route.request().resourceType();
            if (BLOCKED_RESOURCE_TYPES.contains(resourceType)) {
                route.abort();
                return;
            }

            String url = route.request().url();
            if (url.startsWith("http://") || url.startsWith("https://")) {
                urlValidator.validate(url);
            }
            route.resume();
        } catch (IllegalArgumentException exception) {
            route.abort();
        }
    }

    public record RenderedPage(URI uri, String html, List<RenderedFrame> frames) {
    }

    public record RenderedFrame(URI uri, String html) {
    }
}
