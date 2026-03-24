package com.example.gitsqlrunner;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class CompatibilityRegressionTest {
  private static final Path TEMP_ROOT = createTempRoot();
  private static final Path BASE_DIR = TEMP_ROOT.resolve("workspace");
  private static final Path SQL_DIR = BASE_DIR.resolve("sql");
  private static final Path DB_FILE = TEMP_ROOT.resolve("meta-test.db");

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private ObjectMapper objectMapper;

  @DynamicPropertySource
  static void registerProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", () -> "jdbc:sqlite:" + DB_FILE.toAbsolutePath());
    registry.add("app.sync.base-dir", () -> BASE_DIR.toAbsolutePath().toString());
    registry.add("app.sync.sql-dir", () -> "sql");
  }

  @BeforeAll
  static void prepareSqlFiles() throws Exception {
    Files.createDirectories(SQL_DIR.resolve("nested"));
    Files.write(SQL_DIR.resolve("001_init.sql"), "select 1;\n".getBytes(StandardCharsets.UTF_8));
    Files.write(SQL_DIR.resolve("nested").resolve("002_more.sql"), "select 2;\n".getBytes(StandardCharsets.UTF_8));
  }

  @Test
  void keepsApiContractsAndHomePageCompatible() throws Exception {
    String homePage = mockMvc.perform(get("/"))
      .andExpect(status().isOk())
      .andReturn()
      .getResponse()
      .getContentAsString();
    assertTrue(homePage.contains("SQL 历史"));
    assertTrue(homePage.contains("执行记录"));
    assertTrue(homePage.contains("数据库配置"));

    Map<String, Object> syncDirSettings = readMap(mockMvc.perform(get("/api/settings/sync-dir"))
      .andExpect(status().isOk())
      .andReturn());
    assertEquals(BASE_DIR.toAbsolutePath().toString(), syncDirSettings.get("baseDir"));
    assertEquals("sql", syncDirSettings.get("sqlDir"));

    Map<String, Object> targetSettings = readMap(mockMvc.perform(get("/api/settings/targets"))
      .andExpect(status().isOk())
      .andReturn());
    assertTrue(targetSettings.containsKey("targets"));

    Map<String, Object> targetList = readMap(mockMvc.perform(get("/api/targets"))
      .andExpect(status().isOk())
      .andReturn());
    assertTrue(targetList.containsKey("items"));

    Map<String, Object> syncResult = readMap(mockMvc.perform(post("/api/sync/local")
        .param("baseDir", BASE_DIR.toAbsolutePath().toString())
        .param("sqlDir", "sql"))
      .andExpect(status().isOk())
      .andReturn());
    assertEquals(Boolean.TRUE, syncResult.get("ok"));

    List<Map<String, Object>> files = readList(mockMvc.perform(get("/api/files"))
      .andExpect(status().isOk())
      .andReturn());
    assertEquals(2, files.size());
    assertTrue(files.get(0).containsKey("id"));
    assertTrue(files.get(0).containsKey("fileName"));
    assertTrue(files.get(0).containsKey("filePath"));
    assertTrue(files.get(0).containsKey("executed"));

    int firstFileId = ((Number) files.get(0).get("id")).intValue();
    Map<String, Object> preview = readMap(mockMvc.perform(get("/api/files/{id}/preview", firstFileId))
      .andExpect(status().isOk())
      .andReturn());
    assertEquals(firstFileId, ((Number) preview.get("id")).intValue());
    assertTrue(String.valueOf(preview.get("content")).contains("select"));
    assertTrue(preview.containsKey("syntaxCheck"));

    Map<String, Object> tree = readMap(mockMvc.perform(get("/api/files/tree")
        .param("baseDir", BASE_DIR.toAbsolutePath().toString())
        .param("sqlDir", "sql"))
      .andExpect(status().isOk())
      .andReturn());
    assertEquals("dir", tree.get("type"));
    assertTrue(tree.containsKey("children"));
    assertFalse(((List<?>) tree.get("children")).isEmpty());

    List<Map<String, Object>> records = readList(mockMvc.perform(get("/api/records/view"))
      .andExpect(status().isOk())
      .andReturn());
    assertTrue(records.isEmpty());
  }

  private Map<String, Object> readMap(MvcResult result) throws Exception {
    return objectMapper.readValue(result.getResponse().getContentAsByteArray(), new TypeReference<Map<String, Object>>() {
    });
  }

  private List<Map<String, Object>> readList(MvcResult result) throws Exception {
    return objectMapper.readValue(result.getResponse().getContentAsByteArray(), new TypeReference<List<Map<String, Object>>>() {
    });
  }

  private static Path createTempRoot() {
    try {
      return Files.createTempDirectory("git-sql-runner-regression-");
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }
}
