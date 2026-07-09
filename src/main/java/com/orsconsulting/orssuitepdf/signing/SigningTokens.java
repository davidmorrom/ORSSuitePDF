package com.orsconsulting.orssuitepdf.signing;

import java.io.IOException;
import java.nio.file.Path;
import java.security.KeyStore;

import eu.europa.esig.dss.token.MSCAPISignatureToken;
import eu.europa.esig.dss.token.Pkcs11SignatureToken;
import eu.europa.esig.dss.token.Pkcs12SignatureToken;
import eu.europa.esig.dss.token.SignatureTokenConnection;

/**
 * Fábrica de conexiones a distintos orígenes de certificado para firmar:
 * fichero PKCS#12, almacén de certificados de Windows (MSCAPI) y tarjeta
 * criptográfica o DNIe (PKCS#11). El llamante es responsable de cerrar el
 * {@link SignatureTokenConnection} devuelto.
 */
public final class SigningTokens {

    private SigningTokens() {
    }

    /** Certificado en un fichero PKCS#12 (.p12/.pfx). */
    public static SignatureTokenConnection pkcs12(Path keystore, char[] password) throws IOException {
        return new Pkcs12SignatureToken(keystore.toFile(),
                new KeyStore.PasswordProtection(password));
    }

    /** Certificados instalados en el almacén de Windows (proveedor SunMSCAPI). */
    public static SignatureTokenConnection windowsStore() {
        return new MSCAPISignatureToken();
    }

    /**
     * Tarjeta criptográfica o DNIe vía PKCS#11.
     *
     * @param driverPath ruta al módulo PKCS#11 (DLL) del proveedor/lector
     * @param pin        PIN de la tarjeta
     */
    public static SignatureTokenConnection pkcs11(Path driverPath, char[] pin) {
        return new Pkcs11SignatureToken(driverPath.toString(),
                new KeyStore.PasswordProtection(pin));
    }
}
