package com.orsconsulting.orssuitepdf.core;

import java.io.IOException;

import org.apache.pdfbox.pdmodel.encryption.AccessPermission;
import org.apache.pdfbox.pdmodel.encryption.StandardProtectionPolicy;

/**
 * Cifrado y descifrado de PDF con contraseña (estándar PDF, AES-256).
 *
 * <p>La protección se aplica al documento y surte efecto al guardarlo; la
 * eliminación de la protección requiere que el documento se haya abierto con
 * su contraseña.</p>
 */
public final class SecurityService {

    private SecurityService() {
    }

    /**
     * Protege el documento con una contraseña de usuario (requerida para abrir).
     * Se usa la misma como contraseña de propietario.
     */
    public static void protect(PdfDocument document, String password) throws IOException {
        AccessPermission permissions = new AccessPermission();
        StandardProtectionPolicy policy =
                new StandardProtectionPolicy(password, password, permissions);
        policy.setEncryptionKeyLength(256);
        policy.setPermissions(permissions);
        document.pdbox().protect(policy);
    }

    /** Marca la protección para eliminarse al guardar (documento sin cifrar). */
    public static void removeProtection(PdfDocument document) {
        document.pdbox().setAllSecurityToBeRemoved(true);
    }

    /** {@code true} si el documento está cifrado. */
    public static boolean isEncrypted(PdfDocument document) {
        return document.pdbox().isEncrypted();
    }
}
