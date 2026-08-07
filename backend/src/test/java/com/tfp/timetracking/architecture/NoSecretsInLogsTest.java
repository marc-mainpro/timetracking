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
 * nombre neutro; para eso esta la revision de codigo.
 *
 * <p><b>Los literales de cadena se excluyen del rastreo de identificadores</b>,
 * porque un literal es una constante del codigo: puede <i>nombrar</i> un
 * secreto, nunca contener su valor en tiempo de ejecucion. Sin esa distincion,
 * un mensaje perfectamente correcto como
 * {@code log.info("password-reset.email.sent aggregateId={}", event.aggregateId())}
 * se marcaba como fuga, cuando sus unicos argumentos son identificadores.
 *
 * <p>El literal si se revisa aparte para un caso concreto: si el mensaje anuncia
 * que el valor que viene a continuacion es un secreto ({@code "token={}"},
 * {@code "password: {}"}), entonces el argumento <i>es</i> el secreto, aunque la
 * variable se llame de forma neutra. Ese patron si es una fuga.
 */
class NoSecretsInLogsTest {

    /**
     * Fragmentos prohibidos dentro de los argumentos de una llamada al logger,
     * en minusculas. {@code cookie} cubre tambien {@code setCookie}/{@code getCookies}.
     */
    private static final List<String> FORBIDDEN_FRAGMENTS =
            List.of("password", "passwd", "token", "secret", "cookie", "authorization", "credential", "apikey");

    private static final Pattern LOG_CALL =
            Pattern.compile("\\b(?:log|logger|LOG|LOGGER)\\s*\\.\\s*(?:trace|debug|info|warn|error)\\s*\\(");

    /** Literales de cadena, para poder excluirlos del rastreo de identificadores. */
    private static final Pattern STRING_LITERAL = Pattern.compile("\"(?:\\\\.|[^\"\\\\])*\"");

    /**
     * Mensaje que anuncia que el siguiente valor es un secreto: {@code "token={}"},
     * {@code "password: {}"}. Ahi el argumento es el secreto, se llame como se llame.
     */
    private static final Pattern ANNOUNCES_SECRET_VALUE = Pattern.compile(
            "(?:" + String.join("|", FORBIDDEN_FRAGMENTS) + ")\\s*[:=]\\s*\\{\\}");

    @Test
    void noLogStatementPublishesCredentials() throws IOException {
        List<String> offenders = new ArrayList<>();
        for (Path source : mainSources()) {
            offenders.addAll(offendersIn(
                    source.getFileName().toString(), Files.readString(source, StandardCharsets.UTF_8)));
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

    /**
     * El propio detector bajo prueba: al excluir los literales se gana precision,
     * pero hay que demostrar que no se ha perdido capacidad de deteccion.
     */
    @Test
    void detectorIgnoresEventNamesButStillCatchesRealLeaks() {
        assertThat(offendersIn("Ok.java",
                "log.info(\"password-reset.email.sent aggregateId={}\", event.aggregateId());"))
                .as("un literal solo nombra el evento, no publica ningun valor")
                .isEmpty();
        assertThat(offendersIn("Ok.java", "log.debug(\"outbox.messages.published={}\", count);"))
                .isEmpty();

        assertThat(offendersIn("Leak.java", "log.debug(\"credenciales {}\", user.password());"))
                .as("pasar el valor al logger es una fuga aunque el mensaje sea inocente")
                .isNotEmpty();
        assertThat(offendersIn("Leak.java", "log.info(\"nuevo login {}\", refreshToken);")).isNotEmpty();
        assertThat(offendersIn("Leak.java", "log.info(\"cabecera {}\", request.getCookies());")).isNotEmpty();
        assertThat(offendersIn("Leak.java", "log.info(\"token={}\", value);"))
                .as("si el mensaje anuncia un secreto, el argumento lo es aunque se llame de forma neutra")
                .isNotEmpty();
        assertThat(offendersIn("Leak.java", "log.warn(\"password: {}\", raw);")).isNotEmpty();
    }

    private List<String> offendersIn(String fileName, String content) {
        List<String> offenders = new ArrayList<>();
        Matcher matcher = LOG_CALL.matcher(content);
        while (matcher.find()) {
            String arguments = argumentsOf(content, matcher.end() - 1);
            String normalized = arguments.toLowerCase(Locale.ROOT);
            if (ANNOUNCES_SECRET_VALUE.matcher(normalized).find()) {
                offenders.add(fileName + ": el mensaje anuncia un secreto como valor en " + oneLine(arguments));
                continue;
            }
            String identifiers = STRING_LITERAL.matcher(normalized).replaceAll("\"\"");
            FORBIDDEN_FRAGMENTS.stream()
                    .filter(identifiers::contains)
                    .forEach(fragment ->
                            offenders.add(fileName + ": '" + fragment + "' en " + oneLine(arguments)));
        }
        return offenders;
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
