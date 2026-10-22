package com.eventcampus.service;

import com.eventcampus.exception.ApiException;
import java.util.Map;

/** Leitura segura dos campos de um corpo JSON recebido como Map. */
public final class Body {
    private Body() {}

    public static String str(Map<String, Object> b, String k) {
        Object v = b.get(k);
        if (v == null) return null;
        String s = v.toString().trim();
        return s.isEmpty() ? null : s;
    }

    public static String strObrig(Map<String, Object> b, String k, String nomeCampo) {
        String s = str(b, k);
        if (s == null) throw ApiException.badRequest("Informe " + nomeCampo);
        return s;
    }

    public static Integer inteiro(Map<String, Object> b, String k) {
        Object v = b.get(k);
        if (v == null || v.toString().isBlank()) return null;
        if (v instanceof Number n) return n.intValue();
        try { return (int) Math.round(Double.parseDouble(v.toString().replace(',', '.'))); }
        catch (NumberFormatException e) { throw ApiException.badRequest("Valor inválido em " + k); }
    }

    public static Double decimal(Map<String, Object> b, String k) {
        Object v = b.get(k);
        if (v == null || v.toString().isBlank()) return null;
        if (v instanceof Number n) return n.doubleValue();
        try { return Double.parseDouble(v.toString().replace(',', '.')); }
        catch (NumberFormatException e) { throw ApiException.badRequest("Valor inválido em " + k); }
    }
}
