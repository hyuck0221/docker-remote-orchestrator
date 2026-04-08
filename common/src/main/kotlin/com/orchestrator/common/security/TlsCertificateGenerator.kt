package com.orchestrator.common.security

import org.slf4j.LoggerFactory
import java.io.File
import java.security.KeyStore
import java.security.SecureRandom
import java.security.cert.X509Certificate
import javax.net.ssl.*

object TlsCertificateGenerator {

    private val logger = LoggerFactory.getLogger(TlsCertificateGenerator::class.java)

    data class TlsConfig(
        val keyStore: KeyStore,
        val keyStorePassword: CharArray,
        val keyStorePath: File
    )

    fun generateSelfSigned(
        outputDir: File = File(System.getProperty("user.home"), ".docker-orchestrator"),
        alias: String = "orchestrator",
        password: String = "orchestrator-tls"
    ): TlsConfig {
        outputDir.mkdirs()
        val keyStoreFile = File(outputDir, "keystore.jks")

        if (keyStoreFile.exists()) {
            logger.info("Using existing keystore: ${keyStoreFile.absolutePath}")
            val ks = KeyStore.getInstance("JKS")
            keyStoreFile.inputStream().use { ks.load(it, password.toCharArray()) }
            return TlsConfig(ks, password.toCharArray(), keyStoreFile)
        }

        logger.info("Generating self-signed TLS certificate via keytool...")

        val process = ProcessBuilder(
            "keytool",
            "-genkeypair",
            "-alias", alias,
            "-keyalg", "RSA",
            "-keysize", "2048",
            "-validity", "365",
            "-keystore", keyStoreFile.absolutePath,
            "-storepass", password,
            "-keypass", password,
            "-dname", "CN=DockerOrchestrator, O=DockerOrchestrator, L=Local",
            "-storetype", "JKS"
        ).redirectErrorStream(true).start()

        val output = process.inputStream.bufferedReader().readText()
        val exitCode = process.waitFor()

        if (exitCode != 0) {
            logger.error("keytool failed (exit=$exitCode): $output")
            throw RuntimeException("Failed to generate TLS certificate: $output")
        }

        logger.info("TLS certificate generated: ${keyStoreFile.absolutePath}")

        val ks = KeyStore.getInstance("JKS")
        keyStoreFile.inputStream().use { ks.load(it, password.toCharArray()) }
        return TlsConfig(ks, password.toCharArray(), keyStoreFile)
    }

    fun createTrustAllSslContext(): SSLContext {
        val trustAllCerts = arrayOf<TrustManager>(object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<X509Certificate>?, authType: String?) {}
            override fun checkServerTrusted(chain: Array<X509Certificate>?, authType: String?) {}
            override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
        })

        val sslContext = SSLContext.getInstance("TLS")
        sslContext.init(null, trustAllCerts, SecureRandom())
        return sslContext
    }

    fun createSslContextFromKeyStore(tlsConfig: TlsConfig): SSLContext {
        val kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm())
        kmf.init(tlsConfig.keyStore, tlsConfig.keyStorePassword)

        val tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
        tmf.init(tlsConfig.keyStore)

        val sslContext = SSLContext.getInstance("TLS")
        sslContext.init(kmf.keyManagers, tmf.trustManagers, SecureRandom())
        return sslContext
    }
}
