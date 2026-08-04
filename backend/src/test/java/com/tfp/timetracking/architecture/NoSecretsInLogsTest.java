package com.tfp.timetracking.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * RS-014 / T140-01: no se registran contrasenas, tokens, cookies ni credenciales.
 *
 * <p>Es una regla que se incumple por descuido, no por decision: alguien anade
 * un {@code log.debug} con el objeto entero mientras depura y se queda. Y es
 * cara de detectar en revision, porque el fallo no esta en lo que el codigo
 * hace sino en lo que deja escrito. De ahi este test.
 *
 * <p>Se implementa sobre el <b>codigo fuente</b> y no con ArchUnit a proposito:
 * el problema no es una dependencia entre clases (que es lo que ArchUnit ve)
 * sino el contenido literal de los argumentos de una llamada al logger, que en
 * bytecode ya se ha perdido en un {@code StringBuilder} o en un array de
 * {@code Object}.
 *
 * <p>Cobertura y limites: detecta el descuido habitual —pasar al logger algo
 * que se llama {@code password}, {@code token}, {@code cookie}, {@code secret}
 * o {@code authorization}—. No detecta un secreto guardado en una variable con
 * nombre neutro; para eso esta la revision de codigo. Un falso positivo se
 * resuelve renombrando la variable, que suele ser la mejora correcta.
 */
class NoSecretsInLogsTest {

    /**
     * Fragmentos prohibidos dentro de los argumentos de una llamada al logger,
     * en minusculas. {@code cookie} cubre tambien {@code setCookie}/{@code getCookies}.
     */
    private static final List<String> FORBIDDEN_FRAGMENTS =
            List.of("password", "passwd", "token", "secret", "cookie", "authorization", "credential", "apikey");

    /**
     * Excepciones justificadas: nombres que contienen un fragmento prohibido
     * pero que no publican el secreto, solo lo cuentan o lo nombran.
     */
    private static final List<String> ALLOWED_EXPRESSIONS = List.of(
            // Nombres de metrica y de clase, no valores.
            "\"outbox.messages",
            "token-de-un-solo-uso");

    private static final Pattern LOG_CALL =
            Pattern.compile("\\b(?:log|logger|LOG|LOGGER)\\s*\\.\\s*(?:trace|debug|info|warn|error)\\s*\\(");

    @Test
    void noLogStatementPublishesCredentials() throws IOException {
        List<String> offenders = new ArrayList<>();
        for (Path source : mainSources()) {
            String content = Files.readString(source, StandardCharsets.UTF_8);
            Matcher matcher = LOG_CALL.matcher(content);
            while (matcher.find()) {
                String arguments = argumentsOf(content, matcher.end() - 1);
                String normalized = arguments.toLowerCase(Locale.ROOT);
                if (ALLOWED_EXPRESSIONS.stream().anyMatch(normalized::contains)) {
                    continue;
                }
                FORBIDDEN_FRAGMENTS.stream()
                        .filter(normalized::contains)
                        .forEach(fragment -> offenders.add(
                                source.getFileName() + ": '" + fragment + "' en " + oneLine(arguments)));
            }
        }

        assertThat(offenders)
                .as("RS-014: ninguna llamada al logger puede recibir contrasenas, tokens, cookies ni credenciales")
                .isEmpty();
    }

    /** El log de acceso HTTP no puede incluir cabeceras ni query string. */
    @Test
    void requestLoggingDoesNotTouchHeadersOrQueryString() throws IOException {
        Path interceptor = Paths.get(
                "src/main/java/com/tfp/timetracking/shared/infrastructure/observability/RequestObservabilityInterceptor.java");
        String content = Files.readString(interceptor, StandardCharsets.UTF_8);

        assertThat(content).doesNotContain("getHeader(").doesNotContain("getQueryString(").doesNotContain("getCookies(");
    }

    private List<Path> mainSources() throws IOException {
        try (Stream<Path> paths = Files.walk(Paths.get("src/main/java"))) {
            return paths.filter(path -> path.toString().endsWith(".java")).toList();
        }
    }

    /** Devuelve el texto entre el parentesis de apertura y su cierre equilibrado. */
    private String argumentsOf(String content, int openParenIndex) {
        int depth = 0;
        for (int i = openParenIndex; i < content.length(); i++) {
            char c = content.charAt(i);
            if (c == '(') {
                depth++;
            } else if (c == ')') {
                depth--;
                if (depth == 0) {
                    return content.substring(openParenIndex + 1, i);
                }
            }
        }
        return content.substring(openParenIndex);
    }

    private String oneLine(String text) {
        return text.replaceAll("\\s+", " ").trim();
    }
}
