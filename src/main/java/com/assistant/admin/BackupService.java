package com.assistant.admin;

import com.assistant.auth.AppSession;
import com.assistant.proxy.ProxySetting;
import com.assistant.quota.ExternalApiQuota;
import com.assistant.integrations.ExternalApiMode;
import com.assistant.config.AssistantProperties;
import jakarta.persistence.Column;
import jakarta.persistence.EntityManager;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.metamodel.Attribute;
import jakarta.persistence.metamodel.EntityType;
import jakarta.persistence.metamodel.SingularAttribute;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.lang.reflect.Field;
import java.sql.Date;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;
import tools.jackson.core.JsonGenerator;
import tools.jackson.core.TokenStreamFactory;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

@Service
public class BackupService {

    private static final int FORMAT_VERSION = 1;
    private static final Set<Class<?>> EXCLUDED_FROM_BACKUP = Set.of(AppSession.class, ProxySetting.class,
            ExternalApiQuota.class, ExternalApiMode.class);
    private static final int PAGE_SIZE = 200;
    // Tables ajoutees apres la v1 du format : absentes d'un ancien dump, elles sont restaurees vides.
    private static final Set<String> OPTIONAL_TABLES = Set.of("prim_setting", "news_category", "guest_setting");

    private final EntityManager entityManager;
    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;
    private final AssistantProperties properties;
    private final Clock clock;
    private final TransactionTemplate readTransaction;
    private final TransactionTemplate writeTransaction;
    private final TokenStreamFactory tokenStreamFactory;

    public BackupService(EntityManager entityManager, JdbcTemplate jdbc, ObjectMapper objectMapper,
                         AssistantProperties properties, Clock clock,
                         PlatformTransactionManager transactionManager) {
        this.entityManager = entityManager;
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.clock = clock;
        this.tokenStreamFactory = objectMapper.tokenStreamFactory();
        this.readTransaction = new TransactionTemplate(transactionManager);
        this.readTransaction.setReadOnly(true);
        this.writeTransaction = new TransactionTemplate(transactionManager);
    }

    public StreamingResponseBody export() {
        return output -> {
            try {
                readTransaction.executeWithoutResult(status -> {
                    try {
                        writeExport(output);
                    } catch (IOException exception) {
                        throw new UncheckedIOException(exception);
                    }
                });
            } catch (UncheckedIOException exception) {
                throw exception.getCause();
            }
        };
    }

    public void restore(InputStream input, long sizeBytes) throws IOException {
        long maxBytes = properties.backup().maxUploadMb() * 1024L * 1024L;
        if (sizeBytes > maxBytes) {
            throw new BackupException(HttpStatus.PAYLOAD_TOO_LARGE,
                    "Le fichier dépasse la limite de " + properties.backup().maxUploadMb() + " Mo.");
        }

        BackupPlan plan;
        try {
            plan = validate(input);
        } catch (BackupException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new BackupException(HttpStatus.BAD_REQUEST, "Le fichier de sauvegarde est invalide.", exception);
        }
        try {
            writeTransaction.executeWithoutResult(status -> apply(plan));
        } catch (BackupException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new BackupException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "La restauration a échoué ; les données précédentes ont été conservées.");
        }
    }

    private void writeExport(OutputStream output) throws IOException {
        List<EntityModel> ordered = orderedModels();
        try (JsonGenerator generator = tokenStreamFactory.createGenerator(output)) {
            generator.writeStartObject();
            generator.writeNumberProperty("version", FORMAT_VERSION);
            generator.writeStringProperty("exportedAt", Instant.now(clock).toString());
            generator.writeObjectPropertyStart("tables");
            for (EntityModel model : ordered) {
                generator.writeName(model.tableName());
                generator.writeStartArray();
                int offset = 0;
                List<?> page;
                do {
                    String query = "select row from " + model.entityType().getName() + " row order by row."
                            + model.id().getName();
                    page = entityManager.createQuery(query, model.entityType().getJavaType())
                            .setFirstResult(offset)
                            .setMaxResults(PAGE_SIZE)
                            .getResultList();
                    for (Object entity : page) {
                        ObjectNode row = objectMapper.createObjectNode();
                        for (SingularAttribute<?, ?> attribute : model.attributes()) {
                            Object value = readField(entity, attribute.getName());
                            if (attribute.isAssociation() && value != null) {
                                value = entityManager.getEntityManagerFactory().getPersistenceUnitUtil()
                                        .getIdentifier(value);
                            }
                            row.set(attribute.getName(), objectMapper.valueToTree(value));
                        }
                        objectMapper.writeValue(generator, row);
                    }
                    offset += page.size();
                    entityManager.clear();
                } while (page.size() == PAGE_SIZE);
                generator.writeEndArray();
            }
            generator.writeEndObject();
            generator.writeEndObject();
        }
    }

    private BackupPlan validate(InputStream input) throws IOException {
        JsonNode root = objectMapper.readTree(input);
        if (root == null || !root.isObject() || root.size() != 3
                || !root.path("version").isIntegralNumber()
                || root.path("version").intValue() != FORMAT_VERSION
                || !root.path("exportedAt").isTextual()
                || !root.path("tables").isObject()) {
            throw invalid("Structure ou version de sauvegarde invalide.");
        }
        try {
            Instant.parse(root.path("exportedAt").stringValue());
        } catch (RuntimeException exception) {
            throw invalid("Date d’export invalide.");
        }

        List<EntityModel> ordered = orderedModels();
        Map<String, EntityModel> byTable = new HashMap<>();
        ordered.forEach(model -> byTable.put(model.tableName(), model));
        JsonNode tables = root.path("tables");
        Set<String> providedTables = new HashSet<>();
        providedTables.addAll(tables.propertyNames());
        Set<String> missingTables = new HashSet<>(byTable.keySet());
        missingTables.removeAll(providedTables);
        if (!byTable.keySet().containsAll(providedTables) || !OPTIONAL_TABLES.containsAll(missingTables)) {
            throw invalid("Les tables du fichier ne correspondent pas au schéma actuel.");
        }

        Map<EntityModel, List<BackupRow>> rowsByModel = new LinkedHashMap<>();
        Map<EntityModel, Set<Object>> idsByModel = new HashMap<>();
        for (EntityModel model : ordered) {
            JsonNode tableRows = providedTables.contains(model.tableName())
                    ? tables.path(model.tableName()) : objectMapper.createArrayNode();
            if (!tableRows.isArray()) {
                throw invalid("La table " + model.tableName() + " doit contenir un tableau.");
            }
            Set<String> expectedAttributes = new LinkedHashSet<>();
            model.attributes().forEach(attribute -> expectedAttributes.add(attribute.getName()));
            Set<Object> identifiers = new HashSet<>();
            List<BackupRow> validatedRows = new ArrayList<>();
            for (JsonNode rowNode : tableRows) {
                if (!rowNode.isObject()) {
                    throw invalid("Une ligne de " + model.tableName() + " n’est pas un objet.");
                }
                Set<String> rowAttributes = new HashSet<>();
                rowAttributes.addAll(rowNode.propertyNames());
                if (!rowAttributes.equals(expectedAttributes)) {
                    throw invalid("Les colonnes de " + model.tableName() + " sont incompatibles.");
                }

                Map<String, Object> values = new LinkedHashMap<>();
                for (SingularAttribute<?, ?> attribute : model.attributes()) {
                    JsonNode valueNode = rowNode.path(attribute.getName());
                    Object value = decodeValue(model, attribute, valueNode);
                    if (value == null && isRequired(model, attribute)) {
                        throw invalid("Une valeur obligatoire manque dans " + model.tableName() + ".");
                    }
                    values.put(attribute.getName(), value);
                }
                Object id = values.get(model.id().getName());
                if (id == null || !identifiers.add(id)) {
                    throw invalid("Identifiant manquant ou dupliqué dans " + model.tableName() + ".");
                }
                validatedRows.add(new BackupRow(values));
            }
            rowsByModel.put(model, validatedRows);
            idsByModel.put(model, identifiers);
        }

        for (EntityModel model : ordered) {
            for (BackupRow row : rowsByModel.get(model)) {
                for (SingularAttribute<?, ?> attribute : model.attributes()) {
                    if (attribute.isAssociation()) {
                        Object id = row.values().get(attribute.getName());
                        EntityModel target = modelFor(attribute.getJavaType());
                        if (id != null && !idsByModel.get(target).contains(id)) {
                            throw invalid("Une référence est absente dans " + model.tableName() + ".");
                        }
                    }
                }
            }
        }
        return new BackupPlan(ordered, rowsByModel);
    }

    private Object decodeValue(EntityModel model, SingularAttribute<?, ?> attribute, JsonNode valueNode) {
        if (valueNode.isMissingNode()) {
            throw invalid("Une colonne manque dans " + model.tableName() + ".");
        }
        if (valueNode.isNull()) {
            return null;
        }
        Class<?> targetType = attribute.isAssociation()
            ? modelFor(attribute.getJavaType()).id().getJavaType() : attribute.getJavaType();
        try {
            return objectMapper.treeToValue(valueNode, targetType);
        } catch (RuntimeException exception) {
            throw invalid("Type de valeur invalide dans " + model.tableName() + ".");
        }
    }

    private void apply(BackupPlan plan) {
        List<EntityModel> ordered = plan.orderedModels();
        EntityModel session = modelFor(AppSession.class);
        jdbc.update("delete from " + quote(session.tableName()));
        List<EntityModel> reverse = new ArrayList<>(ordered);
        java.util.Collections.reverse(reverse);
        reverse.forEach(model -> jdbc.update("delete from " + quote(model.tableName())));
        entityManager.clear();

        for (EntityModel model : ordered) {
            String columnList = model.attributes().stream()
                    .map(attribute -> quote(model.columnNames().get(attribute.getName())))
                    .collect(java.util.stream.Collectors.joining(", "));
            String placeholders = String.join(", ", java.util.Collections.nCopies(model.attributes().size(), "?"));
            String sql = "insert into " + quote(model.tableName()) + " (" + columnList + ") values ("
                    + placeholders + ")";
            for (BackupRow row : plan.rowsByModel().get(model)) {
                Object[] values = model.attributes().stream()
                        .map(attribute -> jdbcValue(row.values().get(attribute.getName())))
                        .toArray();
                jdbc.update(sql, values);
            }
        }
        resetPostgresSequences(ordered);
        entityManager.clear();
    }

    private void resetPostgresSequences(List<EntityModel> ordered) {
        Boolean postgres = jdbc.execute((org.springframework.jdbc.core.ConnectionCallback<Boolean>) connection ->
            connection.getMetaData().getDatabaseProductName()
                .toLowerCase(java.util.Locale.ROOT).contains("postgresql"));
        if (!Boolean.TRUE.equals(postgres)) {
            return;
        }
        for (EntityModel model : ordered) {
            Field idField = field(model.entityType().getJavaType(), model.id().getName());
            if (idField.getAnnotation(GeneratedValue.class) == null
                    || !Number.class.isAssignableFrom(model.id().getJavaType())) {
                continue;
            }
            String idColumn = model.columnNames().get(model.id().getName());
            String quotedTable = quote(model.tableName());
            String quotedId = quote(idColumn);
            String sql = "select setval(pg_get_serial_sequence(?, ?), coalesce(max(" + quotedId + "), 1), max("
                    + quotedId + ") is not null) from " + quotedTable;
            jdbc.queryForObject(sql, Long.class, model.tableName(), idColumn);
        }
    }

    private List<EntityModel> orderedModels() {
        List<EntityModel> models = new ArrayList<>();
        for (EntityType<?> entityType : entityManager.getMetamodel().getEntities()) {
            if (!EXCLUDED_FROM_BACKUP.contains(entityType.getJavaType())) {
                models.add(createModel(entityType));
            }
        }
        models.sort(Comparator.comparing(EntityModel::tableName));
        Map<Class<?>, EntityModel> byClass = new HashMap<>();
        models.forEach(model -> byClass.put(model.entityType().getJavaType(), model));

        List<EntityModel> ordered = new ArrayList<>();
        Set<EntityModel> added = new LinkedHashSet<>();
        while (ordered.size() < models.size()) {
            boolean progress = false;
            for (EntityModel model : models) {
                if (added.contains(model)) {
                    continue;
                }
                boolean ready = model.attributes().stream()
                        .filter(Attribute::isAssociation)
                        .allMatch(attribute -> byClass.containsKey(attribute.getJavaType())
                                && (attribute.getJavaType() == model.entityType().getJavaType()
                                        || added.contains(byClass.get(attribute.getJavaType()))));
                if (ready) {
                    ordered.add(model);
                    added.add(model);
                    progress = true;
                }
            }
            if (!progress) {
                throw new IllegalStateException("Le graphe des clés étrangères contient une dépendance non prise en charge.");
            }
        }
        return ordered;
    }

    private EntityModel modelFor(Class<?> entityClass) {
        return entityManager.getMetamodel().getEntities().stream()
                .filter(type -> type.getJavaType() == entityClass)
                .findFirst()
                .map(this::createModel)
                .orElseThrow(() -> new IllegalStateException("Entité JPA introuvable : " + entityClass.getName()));
    }

    private EntityModel createModel(EntityType<?> entityType) {
        Table table = entityType.getJavaType().getAnnotation(Table.class);
        if (table != null && !table.schema().isBlank()) {
            throw new IllegalStateException("Les schémas SQL explicites ne sont pas pris en charge par les sauvegardes.");
        }
        String tableName = table != null && !table.name().isBlank()
                ? table.name() : snakeCase(entityType.getName());
        List<SingularAttribute<?, ?>> attributes = new ArrayList<>();
        Map<String, String> columnNames = new LinkedHashMap<>();
        for (Attribute<?, ?> attribute : entityType.getAttributes()) {
            if (attribute.isCollection()) {
                ensureInverseCollection(entityType, attribute);
                continue;
            }
            if (!(attribute instanceof SingularAttribute<?, ?> singular)) {
                throw new IllegalStateException("Attribut JPA non pris en charge : " + attribute.getName());
            }
            Attribute.PersistentAttributeType kind = singular.getPersistentAttributeType();
            if (kind != Attribute.PersistentAttributeType.BASIC
                    && kind != Attribute.PersistentAttributeType.MANY_TO_ONE
                    && kind != Attribute.PersistentAttributeType.ONE_TO_ONE) {
                throw new IllegalStateException("Type d’attribut JPA non pris en charge : " + attribute.getName());
            }
            attributes.add(singular);
            columnNames.put(attribute.getName(), columnName(entityType.getJavaType(), attribute.getName()));
        }
        attributes.sort(Comparator.comparing(SingularAttribute::getName));
        SingularAttribute<?, ?> id = entityType.getId(entityType.getIdType().getJavaType());
        return new EntityModel(entityType, tableName, id, List.copyOf(attributes), Map.copyOf(columnNames));
    }

    private void ensureInverseCollection(EntityType<?> entityType, Attribute<?, ?> attribute) {
        Field field = field(entityType.getJavaType(), attribute.getName());
        jakarta.persistence.OneToMany oneToMany = field.getAnnotation(jakarta.persistence.OneToMany.class);
        jakarta.persistence.ManyToMany manyToMany = field.getAnnotation(jakarta.persistence.ManyToMany.class);
        if ((oneToMany != null && !oneToMany.mappedBy().isBlank())
                || (manyToMany != null && !manyToMany.mappedBy().isBlank())) {
            return;
        }
        throw new IllegalStateException("Collection JPA propriétaire non prise en charge : " + attribute.getName());
    }

    private static String columnName(Class<?> entityClass, String attributeName) {
        Field field = field(entityClass, attributeName);
        Column column = field.getAnnotation(Column.class);
        JoinColumn joinColumn = field.getAnnotation(JoinColumn.class);
        String name = joinColumn != null ? joinColumn.name() : column != null ? column.name() : "";
        return name.isBlank() ? snakeCase(attributeName) : name;
    }

    private static boolean isRequired(EntityModel model, SingularAttribute<?, ?> attribute) {
        if (attribute.equals(model.id())) {
            return true;
        }
        Field field = field(model.entityType().getJavaType(), attribute.getName());
        JoinColumn joinColumn = field.getAnnotation(JoinColumn.class);
        if (joinColumn != null && !joinColumn.nullable()) {
            return true;
        }
        ManyToOne manyToOne = field.getAnnotation(ManyToOne.class);
        if (manyToOne != null && !manyToOne.optional()) {
            return true;
        }
        OneToOne oneToOne = field.getAnnotation(OneToOne.class);
        if (oneToOne != null && !oneToOne.optional()) {
            return true;
        }
        Column column = field.getAnnotation(Column.class);
        return (column != null && !column.nullable()) || attribute.getJavaType().isPrimitive();
    }

    private static Object readField(Object target, String name) {
        try {
            Field field = field(target.getClass(), name);
            field.setAccessible(true);
            return field.get(target);
        } catch (IllegalAccessException exception) {
            throw new IllegalStateException("Attribut JPA illisible : " + name, exception);
        }
    }

    private static Field field(Class<?> type, String name) {
        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            try {
                return current.getDeclaredField(name);
            } catch (NoSuchFieldException ignored) {
                // Continue through the entity hierarchy.
            }
        }
        throw new IllegalStateException("Attribut JPA introuvable : " + type.getName() + "." + name);
    }

    private static Object jdbcValue(Object value) {
        if (value instanceof LocalDate date) {
            return Date.valueOf(date);
        }
        if (value instanceof Enum<?> enumValue) {
            return enumValue.name();
        }
        return value;
    }

    private static String quote(String identifier) {
        return "\"" + identifier.replace("\"", "\"\"") + "\"";
    }

    private static String snakeCase(String name) {
        return name.replaceAll("([a-z0-9])([A-Z])", "$1_$2").toLowerCase(java.util.Locale.ROOT);
    }

    private static BackupException invalid(String message) {
        return new BackupException(HttpStatus.BAD_REQUEST, message);
    }

    private record EntityModel(EntityType<?> entityType, String tableName, SingularAttribute<?, ?> id,
                               List<SingularAttribute<?, ?>> attributes, Map<String, String> columnNames) {
    }

    private record BackupRow(Map<String, Object> values) {
    }

    private record BackupPlan(List<EntityModel> orderedModels, Map<EntityModel, List<BackupRow>> rowsByModel) {
    }

    public static final class BackupException extends RuntimeException {
        private final HttpStatus status;

        public BackupException(HttpStatus status, String message) {
            super(message);
            this.status = status;
        }

        public BackupException(HttpStatus status, String message, Throwable cause) {
            super(message, cause);
            this.status = status;
        }

        public HttpStatus status() {
            return status;
        }
    }
}