package com.orsconsulting.orssuitepdf.signing;

import java.security.cert.X509Certificate;
import java.util.HashMap;
import java.util.Map;

import javax.naming.InvalidNameException;
import javax.naming.ldap.LdapName;
import javax.naming.ldap.Rdn;
import javax.security.auth.x500.X500Principal;

import eu.europa.esig.dss.token.DSSPrivateKeyEntry;

/**
 * Datos legibles extraídos del certificado del firmante (nombre, apellidos,
 * NIF/DNI, organización), con soporte específico para el DNIe español.
 *
 * <p>El DNIe codifica el nombre en {@code GIVENNAME}, los apellidos en
 * {@code SURNAME} y el DNI en {@code SERIALNUMBER} (p. ej.
 * {@code IDCES-12345678Z}); si no están presentes, se recurre al CN.</p>
 */
public record CertificateInfo(String givenName, String surname, String nif,
                              String organization, String commonName) {

    // OID → etiqueta, para que LdapName reconozca los campos del DNIe.
    private static final Map<String, String> OID_LABELS = new HashMap<>();

    static {
        OID_LABELS.put("2.5.4.3", "CN");
        OID_LABELS.put("2.5.4.4", "SURNAME");
        OID_LABELS.put("2.5.4.5", "SERIALNUMBER");
        OID_LABELS.put("2.5.4.42", "GIVENNAME");
        OID_LABELS.put("2.5.4.10", "O");
    }

    public static CertificateInfo from(DSSPrivateKeyEntry key) {
        return from(key.getCertificate().getCertificate());
    }

    public static CertificateInfo from(X509Certificate certificate) {
        X500Principal principal = certificate.getSubjectX500Principal();
        Map<String, String> fields = new HashMap<>();
        try {
            LdapName dn = new LdapName(principal.getName(X500Principal.RFC2253, OID_LABELS));
            for (Rdn rdn : dn.getRdns()) {
                fields.putIfAbsent(rdn.getType().toUpperCase(), String.valueOf(rdn.getValue()));
            }
        } catch (InvalidNameException ignored) {
            // DN no parseable: se queda con lo que haya (posiblemente nada).
        }

        String cn = fields.getOrDefault("CN", "");
        String given = fields.getOrDefault("GIVENNAME", "");
        String surname = fields.getOrDefault("SURNAME", "");
        String org = fields.getOrDefault("O", "");
        String nif = cleanNif(fields.getOrDefault("SERIALNUMBER", ""));

        if (given.isBlank() && surname.isBlank() && !cn.isBlank()) {
            // Sin campos separados: se usa el CN como nombre completo.
            given = stripQualifier(cn);
        }
        return new CertificateInfo(given.trim(), surname.trim(), nif, org.trim(), cn.trim());
    }

    /** Quita prefijos habituales del DNIe (p. ej. "IDCES-12345678Z" → "12345678Z"). */
    private static String cleanNif(String raw) {
        if (raw == null) {
            return "";
        }
        String value = raw.trim();
        int dash = value.lastIndexOf('-');
        if (dash >= 0 && dash < value.length() - 1) {
            value = value.substring(dash + 1);
        }
        return value;
    }

    /** Quita cualificadores entre paréntesis del CN, p. ej. "(FIRMA)". */
    private static String stripQualifier(String cn) {
        int paren = cn.indexOf('(');
        return paren > 0 ? cn.substring(0, paren).trim() : cn;
    }

    /** Nombre completo "Nombre Apellidos" o el CN si no hay campos separados. */
    public String fullName(boolean surnameFirst) {
        if (givenName.isBlank() && surname.isBlank()) {
            return commonName;
        }
        if (surname.isBlank()) {
            return givenName;
        }
        if (givenName.isBlank()) {
            return surname;
        }
        return surnameFirst ? surname + ", " + givenName : givenName + " " + surname;
    }

    public boolean hasNif() {
        return nif != null && !nif.isBlank();
    }
}
