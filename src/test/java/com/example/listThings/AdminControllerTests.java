package com.example.listThings;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentMatchers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementCreator;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AdminControllerTests {

    private static final String ITEMS = "/api/admin/items";
    private static final String INSERT_LINK = "INSERT INTO item_categories (item_id, category_id) VALUES (?, ?)";
    private static final String UPDATE_NAME = "UPDATE items SET name = ? WHERE id = ?";
    private static final String DELETE_LINKS = "DELETE FROM item_categories WHERE item_id = ?";

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private JdbcTemplate jdbc;

    @MockitoBean
    private PlatformTransactionManager transactions;

    private final SimpleTransactionStatus transaction = new SimpleTransactionStatus();

    @BeforeEach
    void configureTransaction() {
        when(transactions.getTransaction(any(TransactionDefinition.class))).thenReturn(transaction);
    }

    @Test
    void createsItemWithMultipleCategoriesAndDeduplicatesLinks() throws Exception {
        stubGeneratedId();

        mvc.perform(post(ITEMS).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Travel adapter","categoryIds":["2","7","2"]}
                                """))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", ITEMS + "/42"));

        verify(jdbc).batchUpdate(eq(INSERT_LINK), ArgumentMatchers.<List<Object[]>>argThat(links ->
                links.size() == 2
                        && Arrays.equals(links.get(0), new Object[]{42L, 2L})
                        && Arrays.equals(links.get(1), new Object[]{42L, 7L})));
        verify(transactions).commit(transaction);
    }

    @Test
    void createsItemWithoutCategories() throws Exception {
        stubGeneratedId();

        mvc.perform(post(ITEMS).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Umbrella","categoryIds":[]}
                                """))
                .andExpect(status().isCreated());

        verify(jdbc).update(any(PreparedStatementCreator.class), any(KeyHolder.class));
        verifyNoMoreInteractions(jdbc);
        verify(transactions).commit(transaction);
    }

    @Test
    void rejectsBlankNamesOnCreateAndRename() throws Exception {
        String body = """
                {"name":"  \u00a0  "}
                """;

        mvc.perform(post(ITEMS).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Введите название вещи."));
        mvc.perform(put(ITEMS + "/42").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Введите название вещи."));
        verifyNoInteractions(jdbc);
    }

    @Test
    void rejectsNamesTooLongForMysql() throws Exception {
        mvc.perform(post(ITEMS).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + "a".repeat(256) + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Название должно содержать не более 255 символов."));
        verifyNoInteractions(jdbc);
    }

    @Test
    void rejectsMalformedCategoryIdBeforeInsert() throws Exception {
        mvc.perform(post(ITEMS).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Umbrella","categoryIds":["invalid"]}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Указана недопустимая категория."));
        verifyNoInteractions(jdbc);
    }

    @Test
    void rollsBackItemWhenCategoryLinkFails() throws Exception {
        stubGeneratedId();
        when(jdbc.batchUpdate(eq(INSERT_LINK), anyList()))
                .thenThrow(new DataIntegrityViolationException("foreign key"));

        mvc.perform(post(ITEMS).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Umbrella","categoryIds":["2","999"]}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Одна из выбранных категорий больше не существует."));

        verify(transactions).rollback(transaction);
    }

    @Test
    void rejectsDuplicateNameOnCreateAndRename() throws Exception {
        when(jdbc.update(any(PreparedStatementCreator.class), any(KeyHolder.class)))
                .thenThrow(new DuplicateKeyException("unique name"));
        when(jdbc.update("UPDATE items SET name = ? WHERE id = ?", "Umbrella", 42L))
                .thenThrow(new DuplicateKeyException("unique name"));
        String body = """
                {"name":"Umbrella"}
                """;

        mvc.perform(post(ITEMS).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Вещь с таким названием уже существует."));
        mvc.perform(put(ITEMS + "/42").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Вещь с таким названием уже существует."));
    }

    @Test
    void trimsAndUpdatesNameWithoutChangingCategories() throws Exception {
        String sql = "UPDATE items SET name = ? WHERE id = ?";
        when(jdbc.update(sql, "New name", 42L)).thenReturn(1);

        mvc.perform(put(ITEMS + "/42").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"  New name  "}
                                """))
                .andExpect(status().isNoContent());

        verify(jdbc).update(sql, "New name", 42L);
        verifyNoMoreInteractions(jdbc);
    }

    @Test
    void replacesExistingLinksWithSelectedCategoriesAndDeduplicatesThem() throws Exception {
        when(jdbc.update(UPDATE_NAME, "New name", 42L)).thenReturn(1);

        mvc.perform(put(ITEMS + "/42").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"New name","categoryIds":["2","7","2"]}
                                """))
                .andExpect(status().isNoContent());

        var order = inOrder(jdbc, transactions);
        order.verify(jdbc).update(UPDATE_NAME, "New name", 42L);
        order.verify(jdbc).update(DELETE_LINKS, 42L);
        order.verify(jdbc).batchUpdate(eq(INSERT_LINK), ArgumentMatchers.<List<Object[]>>argThat(links ->
                links.size() == 2
                        && Arrays.equals(links.get(0), new Object[]{42L, 2L})
                        && Arrays.equals(links.get(1), new Object[]{42L, 7L})));
        order.verify(transactions).commit(transaction);
    }

    @Test
    void clearsAllLinksForAnEmptySelection() throws Exception {
        when(jdbc.update(UPDATE_NAME, "Umbrella", 42L)).thenReturn(1);

        mvc.perform(put(ITEMS + "/42").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Umbrella","categoryIds":[]}
                                """))
                .andExpect(status().isNoContent());

        verify(jdbc).update(UPDATE_NAME, "Umbrella", 42L);
        verify(jdbc).update(DELETE_LINKS, 42L);
        verifyNoMoreInteractions(jdbc);
        verify(transactions).commit(transaction);
    }

    @Test
    void rollsBackNameAndLinkChangesWhenASelectedCategoryDoesNotExist() throws Exception {
        when(jdbc.update(UPDATE_NAME, "New name", 42L)).thenReturn(1);
        when(jdbc.batchUpdate(eq(INSERT_LINK), anyList()))
                .thenThrow(new DataIntegrityViolationException("foreign key"));

        mvc.perform(put(ITEMS + "/42").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"New name","categoryIds":["2","999"]}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Одна из выбранных категорий больше не существует."));

        var order = inOrder(jdbc, transactions);
        order.verify(jdbc).update(UPDATE_NAME, "New name", 42L);
        order.verify(jdbc).update(DELETE_LINKS, 42L);
        order.verify(jdbc).batchUpdate(eq(INSERT_LINK), anyList());
        order.verify(transactions).rollback(transaction);
    }

    @Test
    void deletesOnlyTheItemThroughTheCascadingForeignKey() throws Exception {
        String sql = "DELETE FROM items WHERE id = ?";
        when(jdbc.update(sql, 42L)).thenReturn(1);

        mvc.perform(delete(ITEMS + "/42")).andExpect(status().isNoContent());

        verify(jdbc).update(sql, 42L);
        verifyNoMoreInteractions(jdbc);
    }

    @ParameterizedTest
    @ValueSource(strings = {"PUT", "DELETE"})
    void reportsMissingItem(String method) throws Exception {
        var request = method.equals("PUT")
                ? put(ITEMS + "/42").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Umbrella\"}")
                : delete(ITEMS + "/42");

        mvc.perform(request)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Вещь уже удалена или не существует."));
    }

    private void stubGeneratedId() {
        when(jdbc.update(any(PreparedStatementCreator.class), any(KeyHolder.class)))
                .thenAnswer(invocation -> {
                    KeyHolder key = invocation.getArgument(1);
                    key.getKeyList().add(Map.of("id", 42L));
                    return 1;
                });
    }
}
