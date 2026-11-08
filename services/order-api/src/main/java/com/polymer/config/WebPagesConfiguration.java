package com.polymer.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Clean URLs for the multipage site. The real files are the .html pages under static/; these
 * routes only redirect to them, so links such as /admin or /catalogo keep working.
 */
@Configuration(proxyBeanMethods = false)
public class WebPagesConfiguration implements WebMvcConfigurer {
    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        String[][] aliases = {
            {"/catalogo", "/catalogo.html"}, {"/tienda", "/catalogo.html"}, {"/carrito", "/carrito.html"},
            {"/checkout", "/checkout.html"}, {"/login", "/login.html"}, {"/registro", "/registro.html"},
            {"/nosotros", "/nosotros.html"}, {"/contacto", "/contacto.html"}, {"/laboratorio", "/laboratorio.html"},
            {"/ofertas", "/ofertas.html"}, {"/ayuda", "/ayuda.html"}, {"/empresa", "/empresa.html"}, {"/legal", "/legal.html"},
            {"/libro-reclamaciones", "/libro-reclamaciones.html"},
            {"/cliente", "/cliente/dashboard.html"}, {"/admin", "/admin/dashboard.html"},
            {"/operaciones", "/operaciones/dashboard.html"}, {"/logistica", "/logistica/dashboard.html"},
            {"/monitoreo", "/monitoreo/dashboard.html"}, {"/arquitectura", "/arquitectura/overview.html"},
        };
        for (String[] alias : aliases) {
            registry.addRedirectViewController(alias[0], alias[1]);
            registry.addRedirectViewController(alias[0] + "/", alias[1]);
        }
    }
}
