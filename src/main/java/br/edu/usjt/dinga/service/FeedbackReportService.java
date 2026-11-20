package br.edu.usjt.dinga.service;

import br.edu.usjt.dinga.model.FeedbackReport;
import br.edu.usjt.dinga.model.FeedbackReportRequest;
import br.edu.usjt.dinga.storage.JsonDataStore;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Registro persistente em JSON (data/) de relatos de erro, alucinação ou viés para auditoria MTG-IA.
 */
@Service
public class FeedbackReportService {

    public static final String STORAGE_FILE = "feedback-reports.json";

    private final JsonDataStore dataStore;
    private final CopyOnWriteArrayList<FeedbackReport> reports = new CopyOnWriteArrayList<>();

    public FeedbackReportService() {
        this(null);
    }

    @Autowired
    public FeedbackReportService(JsonDataStore dataStore) {
        this.dataStore = dataStore;
        if (dataStore != null) {
            List<FeedbackReport> saved = dataStore.readList(STORAGE_FILE, FeedbackReport.class);
            reports.addAll(saved);
        }
    }

    public synchronized FeedbackReport add(FeedbackReportRequest request) {
        String id = "RPT-" + UUID.randomUUID().toString().substring(0, 6).toUpperCase(Locale.ROOT);
        FeedbackReport report = new FeedbackReport(
                id,
                request.watermarkId(),
                request.category(),
                request.comment(),
                Instant.now().toString()
        );
        reports.add(0, report);
        persist();
        return report;
    }

    public synchronized boolean remove(String id) {
        if (id == null || id.isBlank()) {
            return false;
        }
        boolean removed = reports.removeIf(r -> r.id().equalsIgnoreCase(id.trim()));
        if (removed) {
            persist();
        }
        return removed;
    }

    public synchronized void clearAll() {
        reports.clear();
        persist();
    }

    public List<FeedbackReport> listAll() {
        return List.copyOf(reports);
    }

    private void persist() {
        if (dataStore != null) {
            dataStore.writeList(STORAGE_FILE, List.copyOf(reports));
        }
    }
}
