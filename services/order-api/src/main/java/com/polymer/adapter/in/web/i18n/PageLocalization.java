package com.polymer.adapter.in.web.i18n;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Rutas localizadas y textos de las páginas (adaptador de presentación). Lee los mismos archivos que
 * el navegador: static/i18n/routes.json (generado por tools/generate_pages.py) y los catálogos
 * static/i18n/&lt;idioma&gt;.json, cuya clave es el texto en español. Sin estado de dominio.
 */
public final class PageLocalization {
    public static final String DEFAULT_LANGUAGE = "es";

    public record PageMeta(String title, String description, List<String> roles, boolean indexable) {}

    private final List<String> languages;
    private final Map<String, Map<String, String>> slugsByFile;      // /catalogo.html → {es: productos, en: products…}
    private final Map<String, String> fileBySlug = new LinkedHashMap<>(); // "en:products" → /catalogo.html
    private final Map<String, PageMeta> meta;
    private final List<String> shellTexts;
    private final Map<String, Map<String, String>> catalogs = new ConcurrentHashMap<>();
    private final Map<String, String> html = new ConcurrentHashMap<>();
    private final ObjectMapper json;

    @SuppressWarnings("unchecked")
    public PageLocalization(ObjectMapper json) {
        this.json = json;
        Map<String, Object> routes = read("/static/i18n/routes.json", new TypeReference<>() {});
        languages = List.copyOf((List<String>) routes.get("languages"));
        slugsByFile = (Map<String, Map<String, String>>) routes.get("pages");
        Map<String, Map<String, Object>> rawMeta = (Map<String, Map<String, Object>>) routes.get("meta");
        Map<String, PageMeta> parsed = new LinkedHashMap<>();
        rawMeta.forEach((file, value) -> parsed.put(file, new PageMeta((String) value.get("title"), (String) value.get("description"),
                List.copyOf((List<String>) value.get("roles")), Boolean.TRUE.equals(value.get("indexable")))));
        meta = Map.copyOf(parsed);
        shellTexts = List.copyOf((List<String>) routes.getOrDefault("shell", List.of()));
        slugsByFile.forEach((file, slugs) -> slugs.forEach((lang, slug) -> fileBySlug.put(lang + ":" + slug, file)));
    }

    public List<String> languages() { return languages; }

    public boolean supports(String language) { return languages.contains(language); }

    public Optional<String> fileFor(String language, String slug) { return Optional.ofNullable(fileBySlug.get(language + ":" + slug)); }

    public String slug(String file, String language) { return slugsByFile.get(file).get(language); }

    public Map<String, PageMeta> pages() { return meta; }

    public PageMeta meta(String file) { return meta.get(file); }

    /** Textos fijos de la plantilla HTML (enlace «Saltar al contenido», etiquetas ARIA, noscript). */
    public List<String> shellTexts() { return shellTexts; }

    /** Texto traducido; sin traducción se devuelve el original en español (alternativa válida). */
    public String translate(String language, String text) {
        if (text == null || DEFAULT_LANGUAGE.equals(language)) return text;
        return catalogs.computeIfAbsent(language, lang -> {
            try (InputStream in = PageLocalization.class.getResourceAsStream("/static/i18n/" + lang + ".json")) {
                return in == null ? Map.of() : Map.copyOf(json.readValue(in, new TypeReference<Map<String, String>>() {}));
            } catch (IOException error) {
                throw new UncheckedIOException(error);
            }
        }).getOrDefault(text, text);
    }

    /** HTML generado de la página (se lee una vez del classpath). */
    public String html(String file) {
        return html.computeIfAbsent(file, name -> {
            try (InputStream in = PageLocalization.class.getResourceAsStream("/static" + name)) {
                if (in == null) throw new IllegalStateException("No existe la página " + name);
                return new String(in.readAllBytes(), StandardCharsets.UTF_8);
            } catch (IOException error) {
                throw new UncheckedIOException(error);
            }
        });
    }

    private <T> T read(String resource, TypeReference<T> type) {
        try (InputStream in = PageLocalization.class.getResourceAsStream(resource)) {
            if (in == null) throw new IllegalStateException("Falta " + resource + " (ejecuta tools/generate_pages.py)");
            return json.readValue(in, type);
        } catch (IOException error) {
            throw new UncheckedIOException(error);
        }
    }
}
