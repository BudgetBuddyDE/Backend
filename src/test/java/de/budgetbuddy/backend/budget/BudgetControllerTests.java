package de.budgetbuddy.backend.budget;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import de.budgetbuddy.backend.ApiResponse;
import de.budgetbuddy.backend.category.Category;
import de.budgetbuddy.backend.category.CategoryRepository;
import de.budgetbuddy.backend.user.User;
import de.budgetbuddy.backend.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import java.util.*;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@SpringBootTest
@AutoConfigureMockMvc
public class BudgetControllerTests {
    private final UserRepository userRepository;
    private final CategoryRepository categoryRepository;
    private final BudgetRepository budgetRepository;
    private final BudgetProgressViewRepository budgetProgressViewRepository;
    private final BudgetController budgetController;
    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
    private MockHttpSession session;

    BudgetControllerTests() {
        UserRepository userRepository = Mockito.mock(UserRepository.class);
        CategoryRepository categoryRepository = Mockito.mock(CategoryRepository.class);
        BudgetRepository budgetRepository = Mockito.mock(BudgetRepository.class);
        BudgetProgressViewRepository budgetProgressViewRepository = Mockito.mock(BudgetProgressViewRepository.class);
        this.userRepository = userRepository;
        this.categoryRepository = categoryRepository;
        this.budgetRepository = budgetRepository;
        this.budgetProgressViewRepository = budgetProgressViewRepository;
        this.budgetController = new BudgetController(budgetRepository, budgetProgressViewRepository, userRepository, categoryRepository);
    }

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        session = new MockHttpSession();
    }

    @Test
    void testCreateBudget_UserNotFound() throws JsonProcessingException {
        UUID uuid = UUID.randomUUID();

        Budget.Create payload = new Budget.Create();
        payload.setOwner(uuid);

        when(userRepository.findById(uuid)).thenReturn(Optional.empty());

        ResponseEntity<ApiResponse<Budget>> response = budgetController.createBudget(payload, session);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertEquals("Provided user doesn't exist", Objects.requireNonNull(response.getBody()).getMessage());
        assertNull(Objects.requireNonNull(response.getBody()).getData());
    }

    @Test
    void testCreateBudget_CategoryNotFound() throws JsonProcessingException {
        UUID uuid = UUID.randomUUID();
        User owner = new User();
        owner.setUuid(uuid);

        Budget.Create payload = new Budget.Create();
        payload.setOwner(uuid);
        payload.setCategoryId(1L);

        when(userRepository.findById(uuid)).thenReturn(Optional.of(owner));
        when(categoryRepository.findByIdAndOwner(payload.getCategoryId(), owner)).thenReturn(Optional.empty());

        ResponseEntity<ApiResponse<Budget>> response = budgetController.createBudget(payload, session);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertEquals("Provided category doesn't exist or isn't owned by you",
                Objects.requireNonNull(response.getBody()).getMessage());
        assertNull(Objects.requireNonNull(response.getBody()).getData());
    }

    @Test
    void testCreateBudget_ObjectAlreadyExists() throws JsonProcessingException {
        UUID uuid = UUID.randomUUID();
        User owner = new User();
        owner.setUuid(uuid);

        Category category = new Category();
        category.setId(1L);
        category.setOwner(owner);

        Budget.Create payload = new Budget.Create();
        payload.setCategoryId(category.getId());
        payload.setOwner(owner.getUuid());

        Budget alreadyExistingBudget = new Budget();
        alreadyExistingBudget.setId(1L);
        alreadyExistingBudget.setOwner(owner);
        alreadyExistingBudget.setCategory(category);

        when(userRepository.findById(uuid)).thenReturn(Optional.of(owner));
        when(categoryRepository.findByIdAndOwner(category.getId(), owner)).thenReturn(Optional.of(category));
        when(budgetRepository.findByOwnerAndCategory(owner, category))
                .thenReturn(Optional.of(alreadyExistingBudget));

        ResponseEntity<ApiResponse<Budget>> response = budgetController.createBudget(payload, session);

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertEquals("There is already an budget for this category",
                Objects.requireNonNull(response.getBody()).getMessage());
        assertNull(Objects.requireNonNull(response.getBody()).getData());
    }

    @Test
    void testCreateBudget_WrongSessionUser() throws JsonProcessingException {
        UUID uuid = UUID.randomUUID();
        User owner = new User();
        owner.setUuid(uuid);

        User sessionUser = new User();
        sessionUser.setUuid(UUID.randomUUID());
        session.setAttribute("user", objectMapper.writeValueAsString(sessionUser));

        Category category = new Category();
        category.setId(1L);
        category.setOwner(owner);

        Budget.Create payload = new Budget.Create();
        payload.setCategoryId(category.getId());
        payload.setOwner(owner.getUuid());

        when(userRepository.findById(uuid)).thenReturn(Optional.of(owner));
        when(categoryRepository.findByIdAndOwner(category.getId(), owner))
                .thenReturn(Optional.of(category));
        when(budgetRepository.findByOwnerAndCategory(owner, category))
                .thenReturn(Optional.empty());

        ResponseEntity<ApiResponse<Budget>> response = budgetController.createBudget(payload, session);

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertEquals("You can't set a budget for different users",
                Objects.requireNonNull(response.getBody()).getMessage());
        assertNull(Objects.requireNonNull(response.getBody()).getData());
    }

    @Test
    void testCreateBudget_Success() throws JsonProcessingException {
        UUID uuid = UUID.randomUUID();
        User owner = new User();
        owner.setUuid(uuid);

        session.setAttribute("user", objectMapper.writeValueAsString(owner));

        Category category = new Category();
        category.setId(1L);
        category.setOwner(owner);

        Budget.Create payload = new Budget.Create();
        payload.setCategoryId(category.getId());
        payload.setOwner(owner.getUuid());

        Budget budget = new Budget();
        budget.setId(1L);
        budget.setOwner(owner);
        budget.setCategory(category);

        when(userRepository.findById(uuid)).thenReturn(Optional.of(owner));
        when(categoryRepository.findByIdAndOwner(category.getId(), owner)).thenReturn(Optional.of(category));
        when(budgetRepository.findByOwnerAndCategory(owner, category))
                .thenReturn(Optional.empty());
        when(budgetRepository.save(any(Budget.class))).thenReturn(budget);

        ResponseEntity<ApiResponse<Budget>> response = budgetController.createBudget(payload, session);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNull(Objects.requireNonNull(response.getBody()).getMessage());
        assertEquals(budget, Objects.requireNonNull(response.getBody()).getData());
    }

    @Test
    void testGetBudget_UserNotFound() throws JsonProcessingException {
        UUID uuid = UUID.randomUUID();
        List<Budget> budgetList = new ArrayList<>();

        when(userRepository.findById(uuid)).thenReturn(Optional.empty());

        ResponseEntity<ApiResponse<List<Budget>>> response = budgetController.getBudgetsByUuid(uuid, session);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertEquals("Provided user doesn't exist",
                Objects.requireNonNull(response.getBody()).getMessage());
        assertEquals(budgetList, Objects.requireNonNull(response.getBody()).getData());
    }

    @Test
    void testGetBudget_WrongSessionUser() throws JsonProcessingException {
        UUID uuid = UUID.randomUUID();
        List<Budget> budgetList = new ArrayList<>();
        User user = new User();
        user.setUuid(uuid);

        User sessionUser = new User();
        sessionUser.setUuid(UUID.randomUUID());
        session.setAttribute("user", objectMapper.writeValueAsString(sessionUser));

        when(userRepository.findById(uuid)).thenReturn(Optional.of(user));

        ResponseEntity<ApiResponse<List<Budget>>> response = budgetController.getBudgetsByUuid(uuid, session);

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertEquals("You can't retrieve budgets for different users",
                Objects.requireNonNull(response.getBody()).getMessage());
        assertEquals(budgetList, Objects.requireNonNull(response.getBody()).getData());
    }

    @Test
    void testGetBudget_Success() throws JsonProcessingException {
        UUID uuid = UUID.randomUUID();
        User user = new User();
        user.setUuid(uuid);

        session.setAttribute("user", objectMapper.writeValueAsString(user));

        List<Budget> budgetList = new ArrayList<>();

        when(userRepository.findById(uuid)).thenReturn(Optional.of(user));
        when(budgetRepository.findAllByOwner(user)).thenReturn(budgetList);

        ResponseEntity<ApiResponse<List<Budget>>> response = budgetController.getBudgetsByUuid(uuid, session);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNull(Objects.requireNonNull(response.getBody()).getMessage());
        assertEquals(budgetList, Objects.requireNonNull(response.getBody()).getData());
    }

    @Test
    void testUpdateBudget_BudgetNotFound() throws JsonProcessingException {
        Budget.Update payload = new Budget.Update();
        payload.setBudgetId(1L);

        when(budgetRepository.findById(payload.getBudgetId()))
                .thenReturn(Optional.empty());

        ResponseEntity<ApiResponse<Budget>> response = budgetController.updateBudget(payload, session);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertEquals("Provided budget doesn't exist",
                Objects.requireNonNull(response.getBody()).getMessage());
        assertNull(Objects.requireNonNull(response.getBody()).getData());
    }

    @Test
    void testUpdateBudget_CategoryNotFound() throws JsonProcessingException {
        User user = new User();
        user.setUuid(UUID.randomUUID());

        Budget budget = new Budget();
        budget.setId(1L);
        budget.setOwner(user);

        Budget.Update payload = new Budget.Update();
        payload.setBudgetId(1L);
        payload.setCategoryId(1L);

        when(budgetRepository.findById(1L))
                .thenReturn(Optional.of(budget));
        when(categoryRepository.findByIdAndOwner(payload.getCategoryId(), user))
                .thenReturn(Optional.empty());

        ResponseEntity<ApiResponse<Budget>> response = budgetController.updateBudget(payload, session);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertEquals("Provided category doesn't exist",
                Objects.requireNonNull(response.getBody()).getMessage());
        assertNull(Objects.requireNonNull(response.getBody()).getData());
    }

    @Test
    void testUpdateBudget_BudgetForCategoryAlreadyExists() throws JsonProcessingException {
        User owner = new User();
        owner.setUuid(UUID.randomUUID());
        session.setAttribute("user", objectMapper.writeValueAsString(owner));

        Category c1 = new Category();
        c1.setId(1L);
        c1.setName("Category 1");
        c1.setOwner(owner);

        Budget b1 = new Budget();
        b1.setId(1L);
        b1.setOwner(owner);
        b1.setCategory(c1);

        Category c2 = new Category();
        c2.setId(2L);
        c2.setName("Category 2");
        c2.setOwner(owner);

        Budget b2 = new Budget();
        b2.setId(2L);
        b2.setOwner(owner);
        b2.setCategory(c2);

        Budget.Update payload = new Budget.Update();
        payload.setBudgetId(b2.getId());
        payload.setCategoryId(c1.getId());

        when(budgetRepository.findById(b2.getId())).thenReturn(Optional.of(b2));
        when(categoryRepository.findByIdAndOwner(payload.getCategoryId(), owner)).thenReturn(Optional.of(c1));
        when(budgetRepository.findByOwnerAndCategory(owner, c1)).thenReturn(Optional.of(b1));

        ResponseEntity<ApiResponse<Budget>> response = budgetController.updateBudget(payload, session);

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertEquals("There is already an budget for this category",
                Objects.requireNonNull(response.getBody()).getMessage());
        assertNull(Objects.requireNonNull(response.getBody()).getData());
    }

    @Test
    void testUpdateBudget_WrongSessionUser() throws JsonProcessingException {
        User sessionUser = new User();
        sessionUser.setUuid(UUID.randomUUID());
        session.setAttribute("user", objectMapper.writeValueAsString(sessionUser));

        User owner = new User();
        owner.setUuid(UUID.randomUUID());

        Category category = new Category();
        category.setId(1L);
        category.setOwner(owner);

        Budget budget = new Budget();
        budget.setId(1L);
        budget.setCategory(category);
        budget.setOwner(owner);

        Budget.Update payload = new Budget.Update();
        payload.setBudgetId(budget.getId());
        payload.setCategoryId(category.getId());

        when(budgetRepository.findById(payload.getBudgetId()))
                .thenReturn(Optional.of(budget));
        when(categoryRepository.findByIdAndOwner(payload.getCategoryId(), owner))
                .thenReturn(Optional.of(category));
        when(budgetRepository.save(any(Budget.class)))
                .thenReturn(budget);

        ResponseEntity<ApiResponse<Budget>> response = budgetController.updateBudget(payload, session);

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertEquals("You can't update budgets for different users",
                Objects.requireNonNull(response.getBody()).getMessage());
        assertNull(Objects.requireNonNull(response.getBody()).getData());
    }

    @Test
    void testUpdateBudget_Success() throws JsonProcessingException {
        User owner = new User();
        owner.setUuid(UUID.randomUUID());
        session.setAttribute("user", objectMapper.writeValueAsString(owner));

        Category category = new Category();
        category.setId(1L);
        category.setOwner(owner);

        Budget budget = new Budget();
        budget.setId(1L);
        budget.setCategory(category);
        budget.setOwner(owner);

        Budget updatedBudget = new Budget();
        budget.setId(2L);
        budget.setCategory(category);
        budget.setOwner(owner);

        Budget.Update payload = new Budget.Update();
        payload.setBudgetId(budget.getId());
        payload.setCategoryId(category.getId());
        payload.setBudget(100.00);

        when(budgetRepository.findById(payload.getBudgetId()))
                .thenReturn(Optional.of(budget));
        when(categoryRepository.findByIdAndOwner(payload.getCategoryId(), owner))
                .thenReturn(Optional.of(category));
        when(budgetRepository.save(any(Budget.class)))
                .thenReturn(updatedBudget);

        ResponseEntity<ApiResponse<Budget>> response = budgetController.updateBudget(payload, session);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNull(Objects.requireNonNull(response.getBody()).getMessage());
        assertEquals(updatedBudget, Objects.requireNonNull(response.getBody()).getData());
    }

    @Test
    void testDeleteBudget_EmptyList() throws JsonProcessingException {
        List<Budget.Delete> payload = new ArrayList<>();

        ResponseEntity<ApiResponse<Map<String, List<?>>>> response =
                budgetController.deleteBudget(payload, session);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("No budgets we're provided",
                Objects.requireNonNull(response.getBody()).getMessage());
        assertNull(Objects.requireNonNull(response.getBody()).getData());
    }

    @Test
    void testDeleteBudget_AllItemsInvalid() throws JsonProcessingException {
        User owner = new User(UUID.randomUUID());
        User sessionUser = new User(UUID.randomUUID());
        session.setAttribute("user", objectMapper.writeValueAsString(sessionUser));

        List<Budget.Delete> payload = new ArrayList<>();
        payload.add(Budget.builder()
                .id(1L)
                .owner(owner)
                .build()
                .toDelete());


        when(budgetProgressViewRepository.findById(any(Long.class)))
                .thenReturn(Optional.empty());

        ResponseEntity<ApiResponse<Map<String, List<?>>>> response =
                budgetController.deleteBudget(payload, session);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("All provided budgets we're invalid values",
                Objects.requireNonNull(response.getBody()).getMessage());
        Map<String, List<?>> responseBody = response.getBody().getData();
        assertEquals(1, responseBody.get("failed").size());
        assertEquals(0, responseBody.get("success").size());
    }

    @Test
    void testDeleteBudget_SomeFailures() throws JsonProcessingException {
        User owner = new User(UUID.randomUUID());
        session.setAttribute("user", objectMapper.writeValueAsString(owner));

        List<Budget.Delete> payload = new ArrayList<>();
        Budget b1 = Budget.builder()
                .id(1L)
                .owner(owner)
                .build();
        payload.add(b1.toDelete());

        Budget b2 = Budget.builder()
                .id(2L)
                .build();
        payload.add(b2.toDelete());

        when(budgetRepository.findById(1L))
                .thenReturn(Optional.of(b1));
        when(budgetRepository.findById(2L))
                .thenReturn(Optional.empty());

        ResponseEntity<ApiResponse<Map<String, List<?>>>> response =
                budgetController.deleteBudget(payload, session);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNull(Objects.requireNonNull(response.getBody()).getMessage());
        Map<String, List<?>> responseBody = response.getBody().getData();
        assertEquals(1, responseBody.get("failed").size());
        assertEquals(1, responseBody.get("success").size());
    }

    @Test
    void testDeleteBudget_WrongSessionUser() throws JsonProcessingException {
        User sessionUser = new User(UUID.randomUUID());
        User owner = new User(UUID.randomUUID());
        session.setAttribute("user", objectMapper.writeValueAsString(sessionUser));

        List<Budget.Delete> payload = new ArrayList<>();
        Budget b1 = Budget.builder()
                .id(1L)
                .owner(owner)
                .build();
        payload.add(b1.toDelete());

        Budget b2 =  Budget.builder()
                .id(2L)
                .owner(sessionUser)
                .build();
        payload.add(b2.toDelete());

        when(budgetRepository.findById(1L))
                .thenReturn(Optional.of(b1));
        when(budgetRepository.findById(2L))
                .thenReturn(Optional.of(b2));

        ResponseEntity<ApiResponse<Map<String, List<?>>>> response =
                budgetController.deleteBudget(payload, session);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNull(Objects.requireNonNull(response.getBody()).getMessage());
        Map<String, List<?>> responseBody = response.getBody().getData();
        assertEquals(1, responseBody.get("failed").size());
        assertEquals(1, responseBody.get("success").size());
    }

    @Test
    void testDeleteBudget_Success() throws JsonProcessingException {
        User owner = new User(UUID.randomUUID());
        session.setAttribute("user", objectMapper.writeValueAsString(owner));

        List<Budget.Delete> payload = new ArrayList<>();
        Budget b1 = Budget.builder()
                .id(1L)
                .owner(owner)
                .build();
        payload.add(b1.toDelete());

        Budget b2 = Budget.builder()
                .id(2L)
                .owner(owner)
                .build();
        payload.add(b2.toDelete());

        when(budgetRepository.findById(1L))
                .thenReturn(Optional.of(b1));
        when(budgetRepository.findById(2L))
                .thenReturn(Optional.of(b2));

        ResponseEntity<ApiResponse<Map<String, List<?>>>> response =
                budgetController.deleteBudget(payload, session);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNull(Objects.requireNonNull(response.getBody()).getMessage());
        Map<String, List<?>> responseBody = response.getBody().getData();
        assertEquals(0, responseBody.get("failed").size());
        assertEquals(2, responseBody.get("success").size());
        assertEquals(List.of(b1, b2), responseBody.get("success"));
    }

}
