package com.orsconsulting.orssuitepdf.signing;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Collection;

import eu.europa.esig.dss.model.x509.CertificateToken;
import eu.europa.esig.dss.service.http.commons.CommonsDataLoader;
import eu.europa.esig.dss.service.http.commons.FileCacheDataLoader;
import eu.europa.esig.dss.spi.client.http.DSSFileLoader;
import eu.europa.esig.dss.spi.client.http.IgnoreDataLoader;
import eu.europa.esig.dss.spi.tsl.TrustedListsCertificateSource;
import eu.europa.esig.dss.spi.x509.CertificateSource;
import eu.europa.esig.dss.spi.x509.CommonCertificateSource;
import eu.europa.esig.dss.tsl.function.OfficialJournalSchemeInformationURI;
import eu.europa.esig.dss.tsl.job.TLValidationJob;
import eu.europa.esig.dss.tsl.source.LOTLSource;

/**
 * Fuente de confianza basada en las listas de confianza europeas (EU LOTL):
 * descarga y cachea la <em>List of Trusted Lists</em> y las listas nacionales,
 * de modo que la validación de firmas puede construir la cadena hasta las CA
 * cualificadas (FNMT, DNIe, ACCV, Camerfirma…) y emitir un veredicto de
 * confianza real en lugar de {@code INDETERMINATE / NO_CERTIFICATE_CHAIN_FOUND}.
 *
 * <p><strong>Offline-first:</strong> el arranque carga la confianza desde la
 * caché en disco de forma instantánea ({@code offlineRefresh}); la
 * actualización desde Internet ({@code onlineRefresh}) se hace en segundo plano
 * y solo re-descarga cuando la caché caduca. Si nunca ha habido conexión y no
 * hay caché, la validación sigue funcionando pero sin cadena de confianza.</p>
 */
public final class TrustService {

    /** URL oficial de la Lista de Listas de Confianza de la UE. */
    private static final String LOTL_URL = "https://ec.europa.eu/tools/lotl/eu-lotl.xml";

    /**
     * URL del Diario Oficial de la UE donde se publican los certificados que
     * firman el LOTL. DSS la usa para localizar, entre los pivots, el conjunto
     * de certificados de confianza vigente; sin ella el LOTL se valida pero no
     * se procesan las listas nacionales.
     */
    private static final String OJ_URL =
            "https://eur-lex.europa.eu/legal-content/EN/TXT/?uri=uriserv:OJ.C_.2019.276.01.0001.01.ENG";

    /**
     * Certificados públicos del Diario Oficial de la UE que firman el LOTL, en
     * PEM. Son material público (no contienen clave privada), por eso se
     * versionan como PEM en lugar de un almacén .p12.
     */
    private static final String OJ_CERTS_PEM = "/trust/eu-oj-lotl-signers.pem";

    /** La caché online se considera fresca durante 7 días. */
    private static final long CACHE_EXPIRATION_MS = 7L * 24 * 60 * 60 * 1000;

    private static final Path CACHE_DIR =
            Path.of(System.getProperty("user.home"), ".ors-suite-pdf", "tl-cache");

    private static TrustedListsCertificateSource trustedSource;
    private static TLValidationJob job;
    private static boolean initialized;

    private TrustService() {
    }

    /**
     * Fuente de confianza para la validación. Nunca es {@code null}: si aún no
     * se ha sincronizado el LOTL, devuelve una fuente vacía (la validación dará
     * un resultado sin confianza, no un error).
     */
    public static synchronized TrustedListsCertificateSource trustedSource() {
        ensureInitialized();
        return trustedSource;
    }

    /**
     * Lanza en segundo plano la carga desde caché y, tras ella, la
     * actualización online del LOTL. Pensado para llamarse una vez al arrancar
     * la aplicación.
     */
    public static void refreshInBackground() {
        Thread worker = new Thread(TrustService::refreshNow, "tl-refresh");
        worker.setDaemon(true);
        worker.start();
    }

    /**
     * Actualiza el LOTL desde Internet de forma síncrona (o desde la caché si no
     * hay red). No lanza: ante cualquier fallo, la confianza queda como estaba.
     */
    public static synchronized void refreshNow() {
        ensureInitialized();
        try {
            if (job != null) {
                job.onlineRefresh();
            }
        } catch (Exception ignored) {
            // Sin red o LOTL no disponible: se sigue con lo que haya en caché.
        }
    }

    /** Solo para diagnóstico: resumen del estado del LOTL y las TL. */
    public static synchronized eu.europa.esig.dss.spi.tsl.TLValidationJobSummary diagnosticSummary() {
        ensureInitialized();
        return job != null ? job.getSummary() : null;
    }

    private static synchronized void ensureInitialized() {
        if (initialized) {
            return;
        }
        initialized = true;
        trustedSource = new TrustedListsCertificateSource();
        try {
            Files.createDirectories(CACHE_DIR);
            job = buildJob(trustedSource);
            job.offlineRefresh();
        } catch (Exception ex) {
            // La confianza queda vacía; la validación seguirá dando un veredicto
            // (sin cadena de confianza) en lugar de fallar.
            job = null;
        }
    }

    private static TLValidationJob buildJob(TrustedListsCertificateSource source) {
        TLValidationJob validationJob = new TLValidationJob();
        validationJob.setTrustedListCertificateSource(source);
        validationJob.setOfflineDataLoader(offlineLoader());
        validationJob.setOnlineDataLoader(onlineLoader());

        LOTLSource lotl = new LOTLSource();
        lotl.setUrl(LOTL_URL);
        lotl.setCertificateSource(officialJournalCertificates());
        lotl.setSigningCertificatesAnnouncementPredicate(
                new OfficialJournalSchemeInformationURI(OJ_URL));
        lotl.setPivotSupport(true);
        validationJob.setListOfTrustedListSources(lotl);
        return validationJob;
    }

    @SuppressWarnings("unchecked")
    private static CertificateSource officialJournalCertificates() {
        try (InputStream in = TrustService.class.getResourceAsStream(OJ_CERTS_PEM)) {
            if (in == null) {
                throw new IllegalStateException(
                        "No se encontraron los certificados del LOTL: " + OJ_CERTS_PEM);
            }
            CertificateFactory factory = CertificateFactory.getInstance("X.509");
            CommonCertificateSource source = new CommonCertificateSource();
            for (X509Certificate certificate :
                    (Collection<X509Certificate>) factory.generateCertificates(in)) {
                source.addCertificate(new CertificateToken(certificate));
            }
            return source;
        } catch (Exception ex) {
            throw new IllegalStateException("No se pudieron cargar los certificados del LOTL", ex);
        }
    }

    /** Descarga desde Internet y cachea; re-descarga solo si la caché caducó. */
    private static DSSFileLoader onlineLoader() {
        FileCacheDataLoader loader = new FileCacheDataLoader();
        loader.setCacheExpirationTime(CACHE_EXPIRATION_MS);
        loader.setDataLoader(new CommonsDataLoader());
        loader.setFileCacheDirectory(CACHE_DIR.toFile());
        return loader;
    }

    /** Solo lee de la caché en disco, nunca accede a la red. */
    private static DSSFileLoader offlineLoader() {
        FileCacheDataLoader loader = new FileCacheDataLoader();
        loader.setCacheExpirationTime(Long.MAX_VALUE);
        loader.setDataLoader(new IgnoreDataLoader());
        loader.setFileCacheDirectory(CACHE_DIR.toFile());
        return loader;
    }
}
