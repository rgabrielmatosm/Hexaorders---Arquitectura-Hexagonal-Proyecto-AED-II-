package com.polymer.application.service;

/** Plantilla HTML sencilla y compatible con clientes de correo (tablas y estilos en línea, sin imágenes externas). */
final class MailLayout {
    private MailLayout() {}

    static String page(String title, String content, String signature) {
        return "<!doctype html><html><head><meta charset=\"utf-8\"><meta name=\"viewport\" content=\"width=device-width\">"
                + "<title>" + escape(title) + "</title></head>"
                + "<body style=\"margin:0;background:#f3f5f9;font-family:Segoe UI,Roboto,Helvetica,Arial,sans-serif;color:#1d2433\">"
                + "<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" style=\"background:#f3f5f9;padding:24px 12px\"><tr><td align=\"center\">"
                + "<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" style=\"max-width:600px;background:#ffffff;border-radius:16px;overflow:hidden;border:1px solid #e3e8f0\">"
                + "<tr><td style=\"background:linear-gradient(120deg,#0b1220,#13305c);padding:22px 28px\">"
                + "<span style=\"font:800 22px/1 Segoe UI,Roboto,Arial,sans-serif;letter-spacing:4px;color:#ffffff\">POLYMER</span>"
                + "<span style=\"display:block;margin-top:6px;color:#9fc3ff;font-size:12px;letter-spacing:1px\">Tecnología que conecta contigo</span></td></tr>"
                + "<tr><td style=\"padding:26px 28px;font-size:15px;line-height:1.55\">" + content + "</td></tr>"
                + "<tr><td style=\"padding:16px 28px 26px;color:#5b6475;font-size:13px;border-top:1px solid #eef1f6\">" + escape(signature) + "</td></tr>"
                + "</table></td></tr></table></body></html>";
    }

    static String escape(String value) {
        if (value == null) return "";
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }
}
