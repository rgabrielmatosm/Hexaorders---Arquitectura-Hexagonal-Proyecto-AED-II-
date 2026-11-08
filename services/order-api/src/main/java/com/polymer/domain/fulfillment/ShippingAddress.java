package com.polymer.domain.fulfillment;

/**
 * Where and to whom a shop order is delivered. Pure value object: validated on creation and
 * never stored with personal data in metrics. Phone is optional; instructions are optional.
 */
public record ShippingAddress(String recipient, String address, String city, String phone, String instructions) {
    public ShippingAddress {
        recipient = required(recipient, 2, 80, "recipient");
        address = required(address, 5, 160, "address");
        city = required(city, 2, 60, "city");
        phone = optional(phone, 20, "phone");
        if (phone != null && !phone.matches("[+0-9 ()-]{6,20}")) {
            throw new IllegalArgumentException("phone: solo dígitos, espacios, +, ( ) y guiones (6 a 20)");
        }
        instructions = optional(instructions, 300, "instructions");
    }

    private static String required(String value, int min, int max, String field) {
        String clean = value == null ? "" : value.strip();
        if (clean.length() < min || clean.length() > max) {
            throw new IllegalArgumentException(field + ": entre " + min + " y " + max + " caracteres");
        }
        return clean;
    }

    private static String optional(String value, int max, String field) {
        if (value == null || value.isBlank()) return null;
        String clean = value.strip();
        if (clean.length() > max) throw new IllegalArgumentException(field + ": máximo " + max + " caracteres");
        return clean;
    }
}
