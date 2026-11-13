package com.polymer.application.port.in;

/** v9 · Lo que escribe un cliente en el formulario de testimonios. El nombre sale de su cuenta. */
public record TestimonialDraft(String comment, Integer rating, String role, String linkedinUrl) {}
