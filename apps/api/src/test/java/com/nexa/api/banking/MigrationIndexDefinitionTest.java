package com.nexa.api.banking;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

/** Complements H2: H2 accepts duplicate index column lists that Oracle rejects. */
class MigrationIndexDefinitionTest {
  private record Migration(String name, String sql) {}
  private record Index(String name, String table, List<List<String>> columns, String migration) {}
  // Keep string literals intact (including escaped quotes), and ignore comments as SQL does.
  private static final Pattern TOKENS = Pattern.compile(
      "--[^\\r\\n]*|/\\*[\\s\\S]*?\\*/|'(?:''|[^'])*'|\"(?:\"\"|[^\"])*\"|[A-Za-z_][A-Za-z0-9_$#]*|[0-9]+|\\S");

  @Test
  void packagedSqlMigrationsDoNotCreateDuplicateVisibleIndexColumnLists() throws Exception {
    var resolver = new PathMatchingResourcePatternResolver();
    List<Resource> resources = new ArrayList<>();
    resources.addAll(Arrays.asList(resolver.getResources("classpath*:db/migration/V*__*.sql")));
    resources.addAll(Arrays.asList(resolver.getResources("classpath*:db/local-migration/V*__*.sql")));
    resources.sort(Comparator.comparing(resource -> version(resource.getFilename())));
    List<Migration> migrations = new ArrayList<>();
    for (Resource resource : resources) {
      migrations.add(new Migration(resource.getFilename(), resource.getContentAsString(StandardCharsets.UTF_8)));
    }
    assertThat(migrations.stream().map(Migration::name).toList())
        .contains("V21__admin_loan_approval_and_bank_funding.sql", "V31__local_card_applications.sql");
    assertThat(duplicates(migrations))
        .as("Oracle permits only one visible index for the same ordered column/expression list; reuse the existing index")
        .isEmpty();
  }

  @Test
  void renamedIndexesAndFormattingCannotHideAnOracleEquivalentColumnList() {
    var errors = duplicates(List.of(
        new Migration("V21.sql", "CREATE INDEX idx_loan_review_queue ON accounts(account_type,product_status,created_at);"),
        new Migration("V31.sql", "CREATE INDEX idx_card_review_queue ON \"ACCOUNTS\" (account_type ASC, /* spacing */ product_status, created_at asc);")));
    assertThat(errors).singleElement().asString()
        .contains("V21.sql", "IDX_LOAN_REVIEW_QUEUE", "V31.sql", "IDX_CARD_REVIEW_QUEUE");
  }

  @Test
  void expressionIndexesPreserveLiteralsOrderAndDescendingDirection() {
    assertThat(duplicates(List.of(new Migration("V1.sql", """
        CREATE INDEX first_index ON accounts(account_type,created_at);
        CREATE INDEX reordered_index ON accounts(created_at,account_type);
        CREATE INDEX descending_index ON accounts(account_type,created_at DESC);
        CREATE INDEX other_table_index ON transactions(account_type,created_at);
        CREATE UNIQUE INDEX expression_one ON accounts(CASE WHEN account_type='CARD' THEN lower(product_status) END,created_at);
        CREATE UNIQUE INDEX expression_two ON accounts(CASE WHEN account_type='card' THEN lower(product_status) END,created_at);
        -- CREATE INDEX ignored_comment ON accounts(account_type,created_at);
        BEGIN EXECUTE IMMEDIATE 'CREATE INDEX not_static_ddl ON accounts(account_type,created_at)'; END;
        """)))).isEmpty();
    assertThat(duplicates(List.of(new Migration("V1.sql", """
        CREATE UNIQUE INDEX first_expression ON accounts(lower(product_status),created_at);
        CREATE UNIQUE INDEX same_expression ON accounts(LOWER ( product_status ) ASC, created_at);
        """)))).hasSize(1);
  }

  @Test
  void droppedIndexesColumnsAndRenamedTablesDoNotCreateFalseCollisions() {
    assertThat(duplicates(List.of(new Migration("V1.sql", """
        CREATE INDEX old_column ON transfers(source_account_id,status);
        ALTER TABLE transfers DROP COLUMN source_account_id;
        ALTER TABLE transfers RENAME COLUMN migrated_account_id TO source_account_id;
        CREATE INDEX new_column ON transfers(source_account_id,status);
        DROP INDEX new_column;
        CREATE INDEX replacement ON transfers(source_account_id,status);
        ALTER TABLE transfers RENAME TO archived_transfers;
        CREATE INDEX fresh_table ON transfers(source_account_id,status);
        DROP TABLE transfers;
        CREATE INDEX recreated_table ON transfers(source_account_id,status);
        """)))).isEmpty();
    assertThat(duplicates(List.of(new Migration("V1.sql", """
        CREATE INDEX old_column ON accounts(old_name);
        ALTER TABLE accounts RENAME COLUMN old_name TO new_name;
        CREATE INDEX duplicate_after_rename ON accounts(new_name);
        """)))).hasSize(1);
  }

  private static MigrationVersion version(String filename) {
    return MigrationVersion.fromVersion(filename.substring(1, filename.indexOf("__")));
  }

  private static List<String> duplicates(List<Migration> migrations) {
    Map<String, Index> indexes = new LinkedHashMap<>();
    List<String> errors = new ArrayList<>();
    for (Migration migration : migrations) {
      List<String> statement = new ArrayList<>();
      var matcher = TOKENS.matcher(migration.sql());
      while (matcher.find()) {
        String token = matcher.group();
        if (token.startsWith("--") || token.startsWith("/*")) continue;
        if (token.equals(";")) {
          apply(statement, migration.name(), indexes, errors);
          statement.clear();
        } else if (!token.equals("/") || !statement.isEmpty()) {
          statement.add(normalize(token));
        }
      }
      apply(statement, migration.name(), indexes, errors);
    }
    return errors;
  }

  private static String normalize(String token) {
    if (token.startsWith("'")) return token;
    if (token.startsWith("\"")) {
      String identifier = token.substring(1, token.length() - 1).replace("\"\"", "\"");
      return identifier.matches("[A-Z_][A-Z0-9_$#]*") ? identifier : token;
    }
    return token.toUpperCase(Locale.ROOT);
  }

  private static void apply(List<String> sql, String migration, Map<String, Index> indexes, List<String> errors) {
    if (sql.size() < 3) return;
    if (sql.get(0).equals("CREATE") && (sql.get(1).equals("INDEX")
        || sql.size() > 3 && sql.get(1).equals("UNIQUE") && sql.get(2).equals("INDEX"))) {
      int offset = sql.get(1).equals("UNIQUE") ? 3 : 2;
      assertThat(sql.get(offset + 1)).as("Static index syntax in %s", migration).isEqualTo("ON");
      assertThat(sql.get(offset + 3)).as("Static index column list in %s", migration).isEqualTo("(");
      List<List<String>> columns = new ArrayList<>();
      List<String> column = new ArrayList<>();
      int depth = 1, end = offset + 4;
      for (; end < sql.size(); end++) {
        String token = sql.get(end);
        if (token.equals("(")) depth++;
        if (token.equals(")")) depth--;
        if (depth == 0 || depth == 1 && token.equals(",")) {
          if (!column.isEmpty() && column.get(column.size() - 1).equals("ASC")) column.remove(column.size() - 1);
          columns.add(List.copyOf(column)); column.clear();
          if (depth == 0) break;
        } else column.add(token);
      }
      assertThat(depth).as("Balanced index expression in %s", migration).isZero();
      // Current migrations only create ordinary visible B-tree indexes. Fail explicitly if
      // future DDL needs support rather than silently claiming a complete Oracle SQL parser.
      assertThat(sql.subList(end + 1, sql.size())).as("Index options need explicit audit support in %s", migration)
          .satisfies(options -> assertThat(options.isEmpty() || options.equals(List.of("VISIBLE"))).isTrue());
      Index next = new Index(sql.get(offset), sql.get(offset + 2), List.copyOf(columns), migration);
      for (Index existing : indexes.values()) {
        if (existing.table().equals(next.table()) && existing.columns().equals(next.columns())) {
          errors.add(next.migration() + " / " + next.name() + " duplicates " + existing.migration() + " / "
              + existing.name() + " on " + next.table() + next.columns());
        }
      }
      indexes.put(next.name(), next);
    } else if (sql.get(0).equals("DROP") && sql.get(1).equals("INDEX")) {
      indexes.remove(sql.get(2));
    } else if (sql.get(0).equals("DROP") && sql.get(1).equals("TABLE")) {
      indexes.values().removeIf(index -> index.table().equals(sql.get(2)));
    } else if (sql.size() >= 6 && sql.get(0).equals("ALTER") && sql.get(1).equals("TABLE")) {
      String table = sql.get(2);
      if (sql.get(3).equals("DROP") && sql.get(4).equals("COLUMN")) {
        indexes.values().removeIf(index -> index.table().equals(table)
            && index.columns().stream().anyMatch(column -> column.contains(sql.get(5))));
      } else if (sql.get(3).equals("RENAME") && sql.get(4).equals("TO")) {
        indexes.replaceAll((name, index) -> index.table().equals(table)
            ? new Index(name, sql.get(5), index.columns(), index.migration()) : index);
      } else if (sql.size() >= 8 && sql.get(3).equals("RENAME") && sql.get(4).equals("COLUMN") && sql.get(6).equals("TO")) {
        indexes.replaceAll((name, index) -> index.table().equals(table)
            ? new Index(name, table, index.columns().stream().map(column -> column.stream()
                .map(token -> token.equals(sql.get(5)) ? sql.get(7) : token).toList()).toList(), index.migration()) : index);
      }
    }
  }
}
