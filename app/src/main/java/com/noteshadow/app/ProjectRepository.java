package com.noteshadow.app;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** File-backed project registry. The default project is implicit for old installs. */
public final class ProjectRepository {
    private static final String FILE_NAME = "projects.json";
    private static final ConcurrentHashMap<String, Object> TRANSACTION_LOCKS = new ConcurrentHashMap<>();
    private final File registry;
    private final Object transactionLock;

    public ProjectRepository(File filesDir) {
        if (filesDir == null) throw new IllegalArgumentException("filesDir must not be null");
        registry = new File(filesDir, FILE_NAME);
        String key; try { key = registry.getCanonicalPath(); } catch (IOException e) { key = registry.getAbsolutePath(); }
        Object fresh = new Object(); Object old = TRANSACTION_LOCKS.putIfAbsent(key, fresh); transactionLock = old == null ? fresh : old;
    }

    public synchronized List<ProjectRecord> list() {
        synchronized (transactionLock) {
        Map<String, ProjectRecord> projects = read();
        if (!projects.containsKey(ProjectRecord.DEFAULT_ID)) {
            long now = System.currentTimeMillis();
            projects.put(ProjectRecord.DEFAULT_ID, new ProjectRecord(ProjectRecord.DEFAULT_ID, "默认项目", now, now));
        }
        List<ProjectRecord> result = new ArrayList<>(projects.values());
        result.sort(Comparator.comparing(ProjectRecord::getProjectId));
        return result; }
    }

    public synchronized ProjectRecord load(String projectId) {
        synchronized (transactionLock) {
        if (!isSafeId(projectId)) return null;
        for (ProjectRecord item : list()) if (item.getProjectId().equals(projectId)) return item;
        return null; }
    }

    public synchronized ProjectRecord create(String name) throws IOException {
        synchronized (transactionLock) {
        String clean = validateName(name);
        Map<String, ProjectRecord> projects = read();
        if (!projects.containsKey(ProjectRecord.DEFAULT_ID)) {
            long now = System.currentTimeMillis();
            projects.put(ProjectRecord.DEFAULT_ID, new ProjectRecord(ProjectRecord.DEFAULT_ID, "默认项目", now, now));
        }
        for (ProjectRecord item : projects.values()) if (item.getName().toLowerCase(java.util.Locale.ROOT).equals(clean.toLowerCase(java.util.Locale.ROOT))) throw new IllegalArgumentException("duplicate project name");
        String id = "project_" + System.currentTimeMillis();
        while (projects.containsKey(id)) id += "_";
        long now = System.currentTimeMillis();
        ProjectRecord result = new ProjectRecord(id, clean, now, now);
        projects.put(id, result);
        write(projects);
        return result; }
    }

    public synchronized ProjectRecord rename(String projectId, String name) throws IOException {
        synchronized (transactionLock) {
        if (!isSafeId(projectId)) throw new IllegalArgumentException("invalid project id");
        String clean = validateName(name);
        Map<String, ProjectRecord> projects = read();
        if (!projects.containsKey(ProjectRecord.DEFAULT_ID)) {
            long now = System.currentTimeMillis();
            projects.put(ProjectRecord.DEFAULT_ID, new ProjectRecord(ProjectRecord.DEFAULT_ID, "默认项目", now, now));
        }
        ProjectRecord old = loadFrom(projects, projectId);
        if (old == null) throw new IllegalArgumentException("unknown project");
        for (ProjectRecord item : projects.values()) if (!item.getProjectId().equals(projectId) && item.getName().toLowerCase(java.util.Locale.ROOT).equals(clean.toLowerCase(java.util.Locale.ROOT))) throw new IllegalArgumentException("duplicate project name");
        ProjectRecord result = new ProjectRecord(projectId, clean, old.getCreatedAt(), System.currentTimeMillis());
        projects.put(projectId, result);
        write(projects);
        return result; }
    }

    public static boolean isSafeId(String id) {
        if (id == null || id.length() == 0 || id.length() > 80) return false;
        for (int i = 0; i < id.length(); i++) {
            char c = id.charAt(i);
            if (!(Character.isLetterOrDigit(c) || c == '_' || c == '-')) return false;
        }
        return true;
    }

    static String validateName(String name) {
        String clean = name == null ? "" : name.trim();
        if (clean.length() == 0 || clean.length() > 80 || clean.indexOf('\n') >= 0 || clean.indexOf('\r') >= 0) throw new IllegalArgumentException("invalid project name");
        return clean;
    }

    private ProjectRecord loadFrom(Map<String, ProjectRecord> projects, String id) { return projects.get(id); }
    private Map<String, ProjectRecord> read() {
        Map<String, ProjectRecord> result = new HashMap<>();
        String json = SafeFileWriter.read(registry);
        for (String object : objects(json)) {
            Map<String, String> v = JsonCodec.parse(object);
            String id = v.get("id");
            if (isSafeId(id)) result.put(id, new ProjectRecord(id, v.get("name"), number(v.get("createdAt")), number(v.get("updatedAt"))));
        }
        return result;
    }
    private void write(Map<String, ProjectRecord> projects) throws IOException {
        StringBuilder out = new StringBuilder("[\n");
        for (ProjectRecord p : projects.values()) out.append("{\"id\":").append(JsonCodec.quote(p.getProjectId())).append(",\"name\":").append(JsonCodec.quote(p.getName())).append(",\"createdAt\":").append(p.getCreatedAt()).append(",\"updatedAt\":").append(p.getUpdatedAt()).append("},\n");
        out.append("]\n");
        SafeFileWriter.write(registry, out.toString());
    }
    private static List<String> objects(String json) { List<String> out = new ArrayList<>(); boolean quote = false, escape = false; int start = -1, depth = 0; for (int i = 0; i < json.length(); i++) { char c = json.charAt(i); if (quote) { if (escape) escape = false; else if (c == '\\') escape = true; else if (c == '"') quote = false; continue; } if (c == '"') { quote = true; continue; } if (c == '{') { if (depth++ == 0) start = i; } else if (c == '}' && depth > 0 && --depth == 0) out.add(json.substring(start, i + 1)); } return out; }
    private static long number(String value) { try { return Long.parseLong(value); } catch (Exception e) { return 0L; } }
}
