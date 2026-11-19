package com.example.listThings;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.mockito.ArgumentMatchers;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.TransientDataAccessResourceException;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AdminCategoryControllerTests {

    private static final String CATEGORIES = "/api/admin/categories";
    private static final String INSERT = "INSERT INTO categories (name, category_type) VALUES (?, ?)";
    private static final String UPDATE = "UPDATE categories SET name = ?, category_type = ? WHERE id = ?";
    private static final String DELETE = "DELETE FROM categories WHERE id = ?";

    private static final String DELETE_LINKS = "DELETE FROM item_categories WHERE category_id = ?";
    private static final String INSERT_LINK = "INSERT INTO item_categories (item_id, category_id) VALUES (?, ?)";

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
    void opensCategoryAdminPage() throws Exception {
        mvc.perform(get("/admin/categories"))
                .andExpect(status().isOk())
                .andExpect(forwardedUrl("/admin-categories.html"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"UNIVERSAL", "TRANSPORT", "ACCOMMODATION"})
    void createsCategoryWithAValidType(String type) throws Exception {
        mvc.perform(post(CATEGORIES).contentType(MediaType.APPLICATION_JSON)
                        .content(body("  Travel category  ", type)))
                .andExpect(status().isCreated());

        verify(jdbc).update(INSERT, "Travel category", type);
    }

    @Test
    void editsNameAndType() throws Exception {
        when(jdbc.update(UPDATE, "New name", "ACCOMMODATION", 7L)).thenReturn(1);

        mvc.perform(put(CATEGORIES + "/7").contentType(MediaType.APPLICATION_JSON)
                        .content(body("New name", "ACCOMMODATION")))
                .andExpect(status().isNoContent());

        verify(jdbc).update(UPDATE, "New name", "ACCOMMODATION", 7L);
    }

    @Test
    void deletesCategoryThroughCascadingForeignKeyInATransaction() throws Exception {
        when(jdbc.update(DELETE, 7L)).thenReturn(1);

        mvc.perform(delete(CATEGORIES + "/7")).andExpect(status().isNoContent());

        verify(transactions).getTransaction(any(TransactionDefinition.class));
        verify(jdbc).update(DELETE, 7L);
        verifyNoMoreInteractions(jdbc);
        verify(transactions).commit(transaction);
    }

    @Test
    void rejectsBlankAndOverlongNames() throws Exception {
        mvc.perform(post(CATEGORIES).contentType(MediaType.APPLICATION_JSON)
                        .content(body("  \u00a0  ", "UNIVERSAL")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Введите название категории."));
        mvc.perform(put(CATEGORIES + "/7").contentType(MediaType.APPLICATION_JSON)
                        .content(body("a".repeat(101), "UNIVERSAL")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Название категории должно содержать не более 100 символов."));

        verifyNoInteractions(jdbc);
    }

    @Test
    void rejectsUnknownAndMissingTypes() throws Exception {
        mvc.perform(post(CATEGORIES).contentType(MediaType.APPLICATION_JSON)
                        .content(body("Hotel", "UNKNOWN")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Выберите допустимый тип категории."));
        mvc.perform(put(CATEGORIES + "/7").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Hotel"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Выберите допустимый тип категории."));

        verifyNoInteractions(jdbc);
    }

    @Test
    void reportsDuplicateNamesOnCreateAndUpdate() throws Exception {
        when(jdbc.update(INSERT, "Hotel", "ACCOMMODATION")).thenThrow(new DuplicateKeyException("unique name"));
        when(jdbc.update(UPDATE, "Hotel", "ACCOMMODATION", 7L)).thenThrow(new DuplicateKeyException("unique name"));
        String body = body("Hotel", "ACCOMMODATION");

        mvc.perform(post(CATEGORIES).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Категория с таким названием уже существует."));
        mvc.perform(put(CATEGORIES + "/7").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Категория с таким названием уже существует."));
    }

    @Test
    void reportsMissingCategoryOnUpdateAndDelete() throws Exception {
        mvc.perform(put(CATEGORIES + "/7").contentType(MediaType.APPLICATION_JSON)
                        .content(body("Hotel", "ACCOMMODATION")))
                .andExpect(status().isNotFound());
        mvc.perform(delete(CATEGORIES + "/7"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Категория уже удалена или не существует."));
    }

    @Test
    void rollsBackAndReturnsSafeMessageWhenDeleteFails() throws Exception {
        when(jdbc.update(DELETE, 7L)).thenThrow(new TransientDataAccessResourceException("database unavailable"));

        mvc.perform(delete(CATEGORIES + "/7"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.detail").value("Не удалось выполнить операцию с категориями. Попробуйте ещё раз."));

        verify(transactions).rollback(transaction);
    }

    @Test
    void createsCategoryWithMultipleItemsAndDeduplicatesThem() throws Exception {
        stubGeneratedId();

        mvc.perform(post(CATEGORIES).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Rail","type":"TRANSPORT","itemIds":["2","3","2"]}
                                """))
                .andExpect(status().isCreated());

        var order = inOrder(jdbc, transactions);
        order.verify(jdbc).update(any(PreparedStatementCreator.class), any(KeyHolder.class));
        order.verify(jdbc).batchUpdate(eq(INSERT_LINK), selectedLinks());
        order.verify(transactions).commit(transaction);
    }

    @Test
    void replacesTheCategoryAssignmentsOnEdit() throws Exception {
        when(jdbc.update(UPDATE, "Rail", "TRANSPORT", 7L)).thenReturn(1);

        mvc.perform(put(CATEGORIES + "/7").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Rail","type":"TRANSPORT","itemIds":["2","3","2"]}
                                """))
                .andExpect(status().isNoContent());

        var order = inOrder(jdbc, transactions);
        order.verify(jdbc).update(UPDATE, "Rail", "TRANSPORT", 7L);
        order.verify(jdbc).update(DELETE_LINKS, 7L);
        order.verify(jdbc).batchUpdate(eq(INSERT_LINK), selectedLinks());
        order.verify(transactions).commit(transaction);
    }

    @Test
    void clearsLinksForAnEmptySelectionWithoutDeletingItems() throws Exception {
        when(jdbc.update(UPDATE, "Rail", "TRANSPORT", 7L)).thenReturn(1);

        mvc.perform(put(CATEGORIES + "/7").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Rail","type":"TRANSPORT","itemIds":[]}
                                """))
                .andExpect(status().isNoContent());

        verify(jdbc).update(UPDATE, "Rail", "TRANSPORT", 7L);
        verify(jdbc).update(DELETE_LINKS, 7L);
        verifyNoMoreInteractions(jdbc);
        verify(transactions).commit(transaction);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void rollsBackCategoryChangesForNonexistentItems(boolean creating) throws Exception {
        if (creating) {
            stubGeneratedId();
        } else {
            when(jdbc.update(UPDATE, "Rail", "TRANSPORT", 7L)).thenReturn(1);
        }
        when(jdbc.batchUpdate(eq(INSERT_LINK), anyList()))
                .thenThrow(new DataIntegrityViolationException("foreign key"));

        var request = creating ? post(CATEGORIES) : put(CATEGORIES + "/7");
        mvc.perform(request.contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Rail","type":"TRANSPORT","itemIds":["2","999"]}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(
                        "Одна из выбранных вещей больше не существует. Откройте форму заново."));

        var order = inOrder(jdbc, transactions);
        order.verify(jdbc).batchUpdate(eq(INSERT_LINK), anyList());
        order.verify(transactions).rollback(transaction);
    }

    private void stubGeneratedId() {
        when(jdbc.update(any(PreparedStatementCreator.class), any(KeyHolder.class)))
                .thenAnswer(invocation -> {
                    KeyHolder key = invocation.getArgument(1);
                    key.getKeyList().add(Map.of("id", 7L));
                    return 1;
                });
    }

    private static List<Object[]> selectedLinks() {
        return ArgumentMatchers.argThat(links ->
                links.size() == 2
                        && Arrays.equals(links.get(0), new Object[]{2L, 7L})
                        && Arrays.equals(links.get(1), new Object[]{3L, 7L}));
    }

    private static String body(String name, String type) {
        return """
                {"name":"%s","type":"%s"}
                """.formatted(name, type);
    }
}
