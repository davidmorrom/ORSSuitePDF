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
        protect(document, password, true, true);
    }

    /**
     * Protege el documento con una contraseña y permisos de uso: si se
     * deniegan la impresión o la copia de texto, el visor del destinatario
     * los bloqueará (según el estándar PDF).
     */
    public static void protect(PdfDocument document, String password,
                               boolean allowPrint, boolean allowCopy) throws IOException {
        AccessPermission permissions = new AccessPermission();
        permissions.setCanPrint(allowPrint);
        permissions.setCanPrintFaithful(allowPrint);
        permissions.setCanExtractContent(allowCopy);
        permissions.setCanExtractForAccessibility(true);
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
