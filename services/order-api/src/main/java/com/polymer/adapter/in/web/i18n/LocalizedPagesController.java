package com.polymer.adapter.in.web.i18n;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.access.WebInvocationPrivilegeEvaluator;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.util.HtmlUtils;

/**
 * Rutas reales por idioma: /es/productos, /en/products, /fr/produits… (adaptador de entrada web).
 *
 * <p>Una sola página HTML generada sirve a todos los idiomas: aquí se resuelve la ruta localizada a
 * su archivo, se aplican EXACTAMENTE las reglas de acceso de Spring Security de ese archivo (con el
 * mismo evaluador que protege /checkout.html, /admin/…), y se ajustan lang, título, descripción,
 * canonical y hreflang para el idioma. El contenido lo traduce el navegador con el mismo catálogo.
 * También publica sitemap.xml (con alternativas por idioma) y robots.txt.
 */
@Controller
public class LocalizedPagesController {
    private static final String LANGS = "es|en|fr|it|pt|de";
    private static final Map<String, String> OG_LOCALES = Map.of("es", "es_PE", "en", "en_US", "fr", "fr_FR", "it", "it_IT", "pt", "pt_BR", "de", "de_DE");

    private final PageLocalization pages;
    private final WebInvocationPrivilegeEvaluator access;

    public LocalizedPagesController(ObjectMapper json, WebInvocationPrivilegeEvaluator access) {
        this.pages = new PageLocalization(json);
        this.access = access;
    }

    @GetMapping({"/{lang:" + LANGS + "}", "/{lang:" + LANGS + "}/", "/{lang:" + LANGS + "}/**"})
    public ResponseEntity<String> page(@PathVariable String lang, HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        String slug = path.substring(1 + lang.length()).replaceAll("^/+|/+$", "");
        Optional<String> file = pages.fileFor(lang, slug);
        if (file.isEmpty()) return render(lang, "/404.html", request, HttpStatus.NOT_FOUND);
        Authentication user = SecurityContextHolder.getContext().getAuthentication();
        if (!access.isAllowed(request.getContextPath(), file.get(), "GET", user)) {
            boolean anonymous = user == null || user instanceof AnonymousAuthenticationToken || !user.isAuthenticated();
            if (anonymous) {
                String target = path + (request.getQueryString() == null ? "" : "?" + request.getQueryString());
                String login = "/" + lang + "/" + pages.slug("/login.html", lang) + "?next=" + URLEncoder.encode(target, StandardCharsets.UTF_8);
                return ResponseEntity.status(HttpStatus.FOUND).header(HttpHeaders.LOCATION, login).build();
            }
            return render(lang, "/403.html", request, HttpStatus.FORBIDDEN);
        }
        return render(lang, file.get(), request, HttpStatus.OK);
    }

    private ResponseEntity<String> render(String lang, String file, HttpServletRequest request, HttpStatus status) {
        PageLocalization.PageMeta meta = pages.meta(file);
        String base = baseUrl(request);
        String id = request.getParameter("id");
        String query = "/producto.html".equals(file) && id != null ? "?id=" + URLEncoder.encode(id, StandardCharsets.UTF_8) : "";
        StringBuilder head = new StringBuilder();
        if (status == HttpStatus.OK) {
            head.append("  <link rel=\"canonical\" href=\"").append(url(base, lang, file, query)).append("\">\n");
            for (String language : pages.languages()) {
                head.append("  <link rel=\"alternate\" hreflang=\"").append(language).append("\" href=\"")
                        .append(url(base, language, file, query)).append("\">\n");
            }
            head.append("  <link rel=\"alternate\" hreflang=\"x-default\" href=\"").append(url(base, PageLocalization.DEFAULT_LANGUAGE, file, query)).append("\">\n");
        }
        if (!meta.indexable() || status != HttpStatus.OK) head.append("  <meta name=\"robots\" content=\"noindex\">\n");
        head.append("  <meta property=\"og:locale\" content=\"").append(OG_LOCALES.get(lang)).append("\">\n");
        String title = HtmlUtils.htmlEscape(pages.translate(lang, meta.title()), "UTF-8");
        String description = HtmlUtils.htmlEscape(pages.translate(lang, meta.description()), "UTF-8");
        String html = pages.html(file)
                .replaceFirst("<html lang=\"es\">", "<html lang=\"" + lang + "\">")
                .replaceFirst("<title>[^<]*</title>", java.util.regex.Matcher.quoteReplacement("<title>" + title + " · POLYMER</title>"))
                .replaceFirst("<meta name=\"description\" content=\"[^\"]*\">",
                        java.util.regex.Matcher.quoteReplacement("<meta name=\"description\" content=\"" + description + "\">"))
                .replaceFirst("</head>", java.util.regex.Matcher.quoteReplacement(head + "</head>"));
        for (String text : pages.shellTexts()) {
            String escaped = HtmlUtils.htmlEscape(text, "UTF-8");
            String translated = HtmlUtils.htmlEscape(pages.translate(lang, text), "UTF-8");
            html = html.replace(">" + escaped + "<", ">" + translated + "<").replace("\"" + escaped + "\"", "\"" + translated + "\"");
        }
        return ResponseEntity.status(status)
                .contentType(new MediaType(MediaType.TEXT_HTML, StandardCharsets.UTF_8))
                .header(HttpHeaders.CONTENT_LANGUAGE, lang)
                .cacheControl(CacheControl.noCache())
                .body(html);
    }

    @GetMapping(value = "/sitemap.xml", produces = MediaType.APPLICATION_XML_VALUE)
    public ResponseEntity<String> sitemap(HttpServletRequest request) {
        String base = baseUrl(request);
        StringBuilder xml = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
                .append("<urlset xmlns=\"http://www.sitemaps.org/schemas/sitemap/0.9\" xmlns:xhtml=\"http://www.w3.org/1999/xhtml\">\n");
        pages.pages().forEach((file, meta) -> {
            if (!meta.indexable()) return;
            for (String lang : pages.languages()) {
                xml.append("  <url>\n    <loc>").append(url(base, lang, file, "")).append("</loc>\n");
                for (String alternate : pages.languages()) {
                    xml.append("    <xhtml:link rel=\"alternate\" hreflang=\"").append(alternate).append("\" href=\"")
                            .append(url(base, alternate, file, "")).append("\"/>\n");
                }
                xml.append("  </url>\n");
            }
        });
        xml.append("</urlset>\n");
        return ResponseEntity.ok().contentType(new MediaType(MediaType.APPLICATION_XML, StandardCharsets.UTF_8)).body(xml.toString());
    }

    @GetMapping(value = "/robots.txt", produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> robots(HttpServletRequest request) {
        String body = "User-agent: *\nDisallow: /api/\nDisallow: /laboratorio.html\nAllow: /\nSitemap: " + baseUrl(request) + "/sitemap.xml\n";
        return ResponseEntity.ok().contentType(new MediaType(MediaType.TEXT_PLAIN, StandardCharsets.UTF_8)).body(body);
    }

    private String url(String base, String lang, String file, String query) {
        return HtmlUtils.htmlEscape(base + "/" + lang + "/" + pages.slug(file, lang) + query, "UTF-8");
    }

    /** Origen público (el gateway Nginx reenvía Host y X-Forwarded-Proto). */
    private static String baseUrl(HttpServletRequest request) {
        String scheme = Optional.ofNullable(request.getHeader("X-Forwarded-Proto")).orElse(request.getScheme()).startsWith("https") ? "https" : "http";
        int port = request.getServerPort();
        boolean defaultPort = port <= 0 || ("http".equals(scheme) && port == 80) || ("https".equals(scheme) && port == 443);
        String host = Optional.ofNullable(request.getHeader("Host"))
                .orElse(request.getServerName() + (defaultPort ? "" : ":" + port));
        return scheme + "://" + host.replaceAll("[^A-Za-z0-9.:\\[\\]-]", "");
    }
}
