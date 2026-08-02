package pl.michalbzowski.windband.application.report;

import jakarta.annotation.PostConstruct;
import net.sf.jasperreports.engine.JRException;
import net.sf.jasperreports.engine.JasperCompileManager;
import net.sf.jasperreports.engine.JasperReport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.w3c.dom.Document;
import org.w3c.dom.NodeList;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;

/**
 * Kompiluje raporty Jasper z .jrxml do .jasper przy starcie aplikacji.
 * Dodatkowo parsuje metadane (nazwa, opis, parametry) i udostępnia je via getReportMetadata().
 */
@Component
public class ReportCompiler {

    private static final Logger log = LoggerFactory.getLogger(ReportCompiler.class);

    private final ResourceLoader resourceLoader;

    /** Cache: reportKey -> metadata */
    private final Map<String, ReportMetadata> metadataCache = new ConcurrentHashMap<>();

    /**
     * Cache: reportKey -> skompilowany JasperReport (w pamięci).
     * Trzymamy skompilowany raport w pamięci zamiast zapisywać plik .jasper na
     * dysk. Dzięki temu działa poprawnie także w spakowanym fat-JAR (Railway),
     * gdzie ścieżka target/classes/reports/ nie jest zapisywalna w runtime.
     */
    private final Map<String, JasperReport> compiledCache = new ConcurrentHashMap<>();

    public ReportCompiler(ResourceLoader resourceLoader) {
        this.resourceLoader = resourceLoader;
    }

    @PostConstruct
    public void compileReportsAndExtractMetadata() {
        try {
            List<String> reports = scanJrxmlFiles();
            log.info("Found {} .jrxml files: {}", reports.size(), reports);

            for (String reportName : reports) {
                String key = reportName.replace(".jrxml", "");

                // Parse metadata first
                ReportMetadata metadata = extractMetadataFromJrxml(key);
                if (metadata != null) {
                    this.metadataCache.put(key, metadata);
                    log.info("Extracted metadata for {}: {} ({})", key,
                            metadata.getDisplayName(),
                            String.valueOf(metadata.getParameters().size()) + " params");
                }

                // Then compile to an in-memory JasperReport (fat-JAR safe)
                try {
                    JasperReport compiled = compileReport(reportName);
                    if (compiled != null) {
                        this.compiledCache.put(key, compiled);
                    }
                } catch (Exception e) {
                    log.error("Failed to compile report: {}", reportName, e);
                }
            }
        } catch (IOException | SAXException e) {
            log.error("Error scanning/processing reports", e);
        }
    }

    /** Skanuje katalog classpath:reports/ w poszukiwaniu plików .jrxml */
    private List<String> scanJrxmlFiles() throws IOException {
        List<String> reports = new ArrayList<>();

        // Znane raporty na classpath:reports/
        String[] knownReports = {
            "sprawozdanie-miesieczne.jrxml",
            "sprawozdanie.jrxml",
            "members.jrxml",
            "hello.jrxml"
        };

        for (String reportName : knownReports) {
            Resource resource = resourceLoader.getResource("classpath:reports/" + reportName);
            if (resource.exists()) {
                reports.add(reportName);
                log.debug("Found report on classpath: {}", reportName);
            }
        }

        return reports.isEmpty() ? Collections.emptyList() : reports;
    }

    /** Parsuje plik .jrxml i extrahuje metadane oraz parametry */
    private ReportMetadata extractMetadataFromJrxml(String key) throws IOException, SAXException {
        Resource resource = resourceLoader.getResource("classpath:reports/" + key + ".jrxml");
        if (!resource.exists()) {
            log.warn("Report not found: classpath:reports/{}.jrxml", key);
            return null;
        }

        try (InputStream is = resource.getInputStream()) {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();

            // Disable external entity resolution for security
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);

            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(is);
            ((org.w3c.dom.Document) doc).normalizeDocument();

            return buildReportMetadata(doc, key);
        } catch (Exception e) {
            log.error("Failed to parse jrxml: {}", key, e);
            return null;
        }
    }

    /** Buduje ReportMetadata z dokumentu XML */
    private ReportMetadata buildReportMetadata(Document doc, String key) {
        NodeList reportNodes = doc.getElementsByTagName("jasperReport");
        if (reportNodes.getLength() == 0) {
            log.warn("No <jasperReport> found in {}", key);
            return null;
        }

        Node reportNode = reportNodes.item(0);

        // Ekstrahuje nazwę z atrybutu name
        String displayName = getAttribute(reportNode, "name", key);

        // Ekstrahuje opis z <property name="com.jaspersoft.studio.report.description">
        String description = extractDescription(doc);

        // Ekstrahuje parametry (tylko na poziomie głównym)
        List<ReportParameter> parameters = new ArrayList<>();
        NodeList parameterNodes = doc.getElementsByTagName("parameter");

        for (int i = 0; i < parameterNodes.getLength(); i++) {
            Node paramNode = parameterNodes.item(i);

            // Skip parameters inside datasets - only get root-level parameters
            if (!isRootElement(paramNode)) {
                continue;
            }

            String paramName = getAttribute(paramNode, "name");
            String className = getAttribute(paramNode, "class", "java.lang.String");
            String forPromptingAttr = getAttribute(paramNode, "forPrompting", "true");
            boolean forPrompting = Boolean.parseBoolean(forPromptingAttr);

            if (paramName != null && !paramName.isEmpty()) {
                parameters.add(new ReportParameter(
                    paramName,
                    className,
                    forPrompting
                ));
            }
        }

        return new ReportMetadata(key, displayName, description, parameters);
    }

    /** Sprawdza czy element jest na poziomie głównym (nie wewnątrz dataset) */
    private boolean isRootElement(Node node) {
        Node parent = node.getParentNode();
        if (parent == null || "jasperReport".equals(parent.getNodeName())) {
            return true;
        }

        String parentName = parent.getNodeName();
        List<String> childContainers = List.of("dataset", "queryString", "subdataset");
        return !childContainers.contains(parentName);
    }

    /** Pobiera wartość atrybutu, lub wartość domyślną */
    private String getAttribute(Node node, String attributeName, String defaultValue) {
        NamedNodeMap attrs = node.getAttributes();
        if (attrs == null) {
            return defaultValue;
        }

        Node attrNode = attrs.getNamedItem(attributeName);
        if (attrNode != null) {
            return attrNode.getNodeValue() != null ? attrNode.getNodeValue() : defaultValue;
        }

        return defaultValue;
    }

    private String getAttribute(Node node, String attributeName) {
        return getAttribute(node, attributeName, "");
    }

    /** Ekstrahuje opis z <property> elementu */
    private String extractDescription(Document doc) {
        NodeList propertyNodes = doc.getElementsByTagName("property");

        for (int i = 0; i < propertyNodes.getLength(); i++) {
            Node propNode = propertyNodes.item(i);

            // Sprawdź attribute name="com.jaspersoft.studio.report.description"
            String propName = getAttribute(propNode, "name");

            if ("com.jaspersoft.studio.report.description".equals(propName)) {
                return getAttribute(propNode, "value", "");
            }
        }

        return "";
    }

    /**
     * Kompiluje .jrxml do obiektu {@link JasperReport} w pamięci.
     * NIE zapisuje pliku .jasper na dysk — kompilacja trzymana jest w cache,
     * co jest bezpieczne dla spakowanego fat-JAR (Railway).
     */
    private JasperReport compileReport(String reportName) throws JRException, IOException {
        Resource source = resourceLoader.getResource("classpath:reports/" + reportName);
        if (!source.exists()) {
            log.warn("Report source not found: classpath:reports/{}", reportName);
            return null;
        }

        try (InputStream inputStream = source.getInputStream()) {
            JasperReport compiled = JasperCompileManager.compileReport(inputStream);
            log.info("Compiled report in-memory: {}", reportName);
            return compiled;
        }
    }

    /** Zwraca cache metadanych */
    public Map<String, ReportMetadata> getMetadataCache() {
        return Collections.unmodifiableMap(metadataCache);
    }

    /** Sprawdza czy dany raport został wykompilowany/naparsowany */
    public boolean hasReport(String key) {
        return metadataCache.containsKey(key);
    }

    /** Zwraca metadane dla danego klucza, lub null jeśli nie istnieje */
    public ReportMetadata getReportMetadata(String key) {
        return metadataCache.get(key);
    }

    /**
     * Zwraca skompilowany raport (w pamięci) dla danego klucza,
     * lub null jeśli raport nie został wykompilowany.
     */
    public JasperReport getCompiledReport(String key) {
        return compiledCache.get(key);
    }
}
