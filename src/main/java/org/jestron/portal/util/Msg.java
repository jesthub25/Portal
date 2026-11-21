package org.jestron.portal.util;

import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * config.yml icindeki mesajlari okur, renklendirir ve gonderir.
 * &-renk kodlari ve 1.16+ HEX renkleri (&#RRGGBB) desteklenir.
 */
public final class Msg {

    private static final Pattern HEX = Pattern.compile("&#([0-9a-fA-F]{6})");
    private static volatile Boolean hexSupported;

    private final FileConfiguration config;

    public Msg(FileConfiguration config) {
        this.config = config;
    }

    /** Tek satirlik mesaji prefix ile gonderir. Mesaj bos ise hicbir sey gondermez. */
    public void send(CommandSender to, String key, String... placeholders) {
        String raw = config.getString("messages." + key);
        if (raw == null || raw.isEmpty()) return;
        String prefix = config.getString("messages.prefix", "");
        to.sendMessage(color(apply((prefix == null ? "" : prefix) + raw, placeholders)));
    }

    /** Coklu satirli (liste) mesaji prefix olmadan gonderir. */
    public void sendLines(CommandSender to, String key, String... placeholders) {
        for (String line : lines(key)) {
            to.sendMessage(color(apply(line, placeholders)));
        }
    }

    /** Tek satirlik mesaji prefix olmadan gonderir. */
    public void sendRaw(CommandSender to, String key, String... placeholders) {
        String raw = config.getString("messages." + key);
        if (raw == null || raw.isEmpty()) return;
        to.sendMessage(color(apply(raw, placeholders)));
    }

    /** Ham mesaji (renklendirilmemis) dondurur. */
    public String raw(String key, String def) {
        String v = config.getString("messages." + key);
        return v == null ? def : v;
    }

    private List<String> lines(String key) {
        String path = "messages." + key;
        if (config.isList(path)) {
            return config.getStringList(path);
        }
        String single = config.getString(path);
        if (single == null || single.isEmpty()) return Collections.emptyList();
        List<String> l = new ArrayList<>(1);
        l.add(single);
        return l;
    }

    public static String apply(String text, String... placeholders) {
        if (text == null) return "";
        String out = text;
        for (int i = 0; i + 1 < placeholders.length; i += 2) {
            String value = placeholders[i + 1] == null ? "" : placeholders[i + 1];
            out = out.replace(placeholders[i], value);
        }
        return out;
    }

    public static String color(String text) {
        if (text == null || text.isEmpty()) return "";
        String out = text;
        if (out.indexOf("&#") >= 0) {
            out = translateHex(out);
        }
        return ChatColor.translateAlternateColorCodes('&', out);
    }

    private static String translateHex(String text) {
        Matcher m = HEX.matcher(text);
        StringBuffer sb = new StringBuffer();
        boolean supported = isHexSupported();
        while (m.find()) {
            String replacement;
            if (supported) {
                StringBuilder b = new StringBuilder("§x");
                for (char c : m.group(1).toCharArray()) {
                    b.append('§').append(Character.toLowerCase(c));
                }
                replacement = b.toString();
            } else {
                replacement = "";
            }
            m.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    private static boolean isHexSupported() {
        Boolean cached = hexSupported;
        if (cached != null) return cached;
        boolean ok;
        try {
            Class.forName("net.md_5.bungee.api.ChatColor").getMethod("of", String.class);
            ok = true;
        } catch (Throwable t) {
            ok = false;
        }
        hexSupported = ok;
        return ok;
    }
}
