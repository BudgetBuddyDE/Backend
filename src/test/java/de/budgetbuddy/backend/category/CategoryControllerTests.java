package de.budgetbuddy.backend.category;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import de.budgetbuddy.backend.ApiResponse;
import de.budgetbuddy.backend.user.User;
import de.budgetbuddy.backend.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpSession;

import java.util.*;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@SpringBootTest
@AutoConfigureMockMvc
public class CategoryControllerTests {
//    @Mock
//    private UserRepository userRepository;
//    @Mock
//    private CategoryRepository categoryRepository;
//    @InjectMocks
//    private CategoryController categoryController;
    private final UserRepository userRepository;
    private final CategoryRepository categoryRepository;
    private final CategoryController categoryController;
    private MockHttpSession session;
    private final ObjectMapper objMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    CategoryControllerTests() {
        UserRepository userRepository = Mockito.mock(UserRepository.class);
        CategoryRepository categoryRepository = Mockito.mock(CategoryRepository.class);
        this.userRepository = userRepository;
        this.categoryRepository = categoryRepository;
        this.categoryController = new CategoryController(categoryRepository, userRepository);
    }

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        session = new MockHttpSession();
    }

    @Test
    void testCreateCategory_UserNotFound() throws JsonProcessingException {
        UUID uuid = UUID.randomUUID();
        de.budgetbuddy.backend.category.Category.Create payload = new de.budgetbuddy.backend.category.Category.Create();
        payload.setOwner(uuid);

        when(userRepository.findById(uuid)).thenReturn(Optional.empty());

        ResponseEntity<ApiResponse<de.budgetbuddy.backend.category.Category>> response = categoryController.createCategory(payload, session);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertEquals("Provided user doesn't exist", Objects.requireNonNull(response.getBody()).getMessage());
        assertNull(Objects.requireNonNull(response.getBody()).getData());
    }

    @Test
    void testCreateCategory_CategoryAlreadyExists() throws JsonProcessingException {
        UUID uuid = UUID.randomUUID();
        de.budgetbuddy.backend.category.Category.Create payload = new de.budgetbuddy.backend.category.Category.Create();
        payload.setOwner(uuid);
        payload.setName("Shopping");

        de.budgetbuddy.backend.category.Category existingCategory = new de.budgetbuddy.backend.category.Category();
        existingCategory.setName("Shopping");

        User user = new User();
        user.setUuid(uuid);

        when(userRepository.findById(uuid))
                .thenReturn(Optional.of(user));
        when(categoryRepository.findByOwnerAndName(user, payload.getName()))
                .thenReturn(Optional.of(existingCategory));

        ResponseEntity<ApiResponse<de.budgetbuddy.backend.category.Category>> response = categoryController.createCategory(payload, session);

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertEquals("There is already an category by this name",
                Objects.requireNonNull(response.getBody()).getMessage());
        assertNull(Objects.requireNonNull(response.getBody()).getData());
    }

    @Test
    void testCreateCategory_Success() throws JsonProcessingException {
        UUID uuid = UUID.randomUUID();
        de.budgetbuddy.backend.category.Category.Create payload = new de.budgetbuddy.backend.category.Category.Create();
        payload.setOwner(uuid);
        payload.setName("Shopping");

        User user = new User();
        user.setUuid(uuid);

        session.setAttribute("user", objMapper.writeValueAsString(user));

        de.budgetbuddy.backend.category.Category category = new de.budgetbuddy.backend.category.Category(user, payload.getName(), null);

        when(userRepository.findById(uuid)).thenReturn(Optional.of(user));
        when(categoryRepository.findByOwnerAndName(user, payload.getName())).thenReturn(Optional.empty());
        when(categoryRepository.save(any(de.budgetbuddy.backend.category.Category.class))).thenReturn(category);

        ResponseEntity<ApiResponse<de.budgetbuddy.backend.category.Category>> response = categoryController.createCategory(payload, session);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(category, Objects.requireNonNull(response.getBody()).getData());
        assertNull(Objects.requireNonNull(response.getBody()).getMessage());
    }

    @Test
    void testCreateCategory_TryForDiffSessionUser() throws JsonProcessingException {
        UUID uuid = UUID.randomUUID();
        de.budgetbuddy.backend.category.Category.Create payload = new de.budgetbuddy.backend.category.Category.Create();
        payload.setOwner(uuid);
        payload.setName("Shopping");

        User user = new User();
        user.setUuid(uuid);

        de.budgetbuddy.backend.category.Category category = new de.budgetbuddy.backend.category.Category(user, payload.getName(), null);

        User sessionUser = new User();
        sessionUser.setUuid(UUID.randomUUID());
        session.setAttribute("user", objMapper.writeValueAsString(sessionUser));

        when(userRepository.findById(uuid)).thenReturn(Optional.of(user));
        when(categoryRepository.findByOwnerAndName(user, payload.getName())).thenReturn(Optional.empty());
        when(categoryRepository.save(any(de.budgetbuddy.backend.category.Category.class))).thenReturn(category);

        ResponseEntity<ApiResponse<de.budgetbuddy.backend.category.Category>> response = categoryController.createCategory(payload, session);

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertEquals("You can't create categories for other users", Objects.requireNonNull(response.getBody()).getMessage());
        assertNull(Objects.requireNonNull(response.getBody()).getData());
    }

    @Test
    void testGetCategories_WrongSessionUser() throws JsonProcessingException {
        UUID uuid = UUID.randomUUID();
        User user = new User();
        user.setUuid(uuid);

        User sessionUser = new User();
        sessionUser.setUuid(UUID.randomUUID());
        session.setAttribute("user", objMapper.writeValueAsString(sessionUser));

        when(userRepository.findById(uuid)).thenReturn(Optional.of(user));

        ResponseEntity<ApiResponse<List<de.budgetbuddy.backend.category.Category>>> response = categoryController.getCategoriesByUuid(uuid, session);

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertEquals("You can't retrieve categories from different users",
                Objects.requireNonNull(response.getBody()).getMessage());
        assertNull(Objects.requireNonNull(response.getBody()).getData());
    }

    @Test
    void retrieveCategoriesWithValidSession() throws JsonProcessingException {
        List<de.budgetbuddy.backend.category.Category> categories = new ArrayList<>();
        UUID uuid = UUID.randomUUID();
        User user = new User();
        user.setUuid(uuid);
        user.setEmail("test@test.com");
        user.setPassword("test");

        when(userRepository.findById(uuid)).thenReturn(Optional.of(user));
        when(categoryRepository.findAllByOwner(user)).thenReturn(categories);

        session.setAttribute("user", objMapper.writeValueAsString(user));
        ResponseEntity<ApiResponse<List<de.budgetbuddy.backend.category.Category>>> response = categoryController.getCategoriesByUuid(uuid, session);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNull(Objects.requireNonNull(response.getBody()).getMessage());
        assertEquals(categories, Objects.requireNonNull(response.getBody()).getData());
    }

    @Test
    void testUpdateCategory_CategoryNotFound() throws JsonProcessingException {
        de.budgetbuddy.backend.category.Category.Update payload = new de.budgetbuddy.backend.category.Category.Update();
        payload.setCategoryId(1L);

        when(categoryRepository.findById(payload.getCategoryId())).thenReturn(Optional.empty());

        ResponseEntity<ApiResponse<de.budgetbuddy.backend.category.Category>> response = categoryController.updateCategory(payload, session);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertEquals("Provided category doesn't exist", Objects.requireNonNull(response.getBody()).getMessage());
        assertNull(Objects.requireNonNull(response.getBody()).getData());
    }

    @Test
    void testUpdateCategory_CategoryNameAlreadyUsed() throws JsonProcessingException {
        User user = new User();
        user.setUuid(UUID.randomUUID());

        de.budgetbuddy.backend.category.Category c1 = new de.budgetbuddy.backend.category.Category();
        c1.setId(1L);
        c1.setName("Shopping");
        c1.setOwner(user);

        de.budgetbuddy.backend.category.Category c2 = new de.budgetbuddy.backend.category.Category();
        c2.setId(2L);
        c2.setName("Not Shopping");
        c2.setOwner(user);

        de.budgetbuddy.backend.category.Category.Update payload = new de.budgetbuddy.backend.category.Category.Update();
        payload.setCategoryId(c2.getId());
        payload.setName(c1.getName());

        when(categoryRepository.findByOwnerAndName(user, payload.getName())).thenReturn(Optional.of(c1));
        when(categoryRepository.findById(2L)).thenReturn(Optional.of(c2));

        session.setAttribute("user", objMapper.writeValueAsString(user));

        ResponseEntity<ApiResponse<de.budgetbuddy.backend.category.Category>> response = categoryController.updateCategory(payload, session);

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertEquals("There is already an category by this name", Objects.requireNonNull(response.getBody()).getMessage());
        assertNull(Objects.requireNonNull(response.getBody()).getData());
    }

    @Test
    void testUpdateCategory_Success() throws JsonProcessingException {
        User user = new User();
        user.setUuid(UUID.randomUUID());

        de.budgetbuddy.backend.category.Category category = new de.budgetbuddy.backend.category.Category();
        category.setId(1L);
        category.setOwner(user);
        category.setName("Not Shopping");

        de.budgetbuddy.backend.category.Category.Update payload = new de.budgetbuddy.backend.category.Category.Update();
        payload.setCategoryId(1L);
        payload.setName("Shopping");

        de.budgetbuddy.backend.category.Category updatedCategory = new de.budgetbuddy.backend.category.Category();
        updatedCategory.setId(1L);
        updatedCategory.setOwner(user);
        updatedCategory.setName("Shopping");

        session.setAttribute("user", objMapper.writeValueAsString(user));

        when(categoryRepository.findById(1L)).thenReturn(Optional.of(category));
        when(categoryRepository.findByOwnerAndName(user, payload.getName())).thenReturn(Optional.empty());
        when(categoryRepository.save(any(de.budgetbuddy.backend.category.Category.class))).thenReturn(updatedCategory);

        ResponseEntity<ApiResponse<de.budgetbuddy.backend.category.Category>> response = categoryController.updateCategory(payload, session);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNull(Objects.requireNonNull(response.getBody()).getMessage());
        assertEquals(updatedCategory, Objects.requireNonNull(response.getBody()).getData());
    }

    @Test
    void testUpdateCategory_TryForDiffUser() throws JsonProcessingException {
        User user = new User();
        user.setUuid(UUID.randomUUID());

        User sessionUser = new User();
        sessionUser.setUuid(UUID.randomUUID());

        de.budgetbuddy.backend.category.Category category = new de.budgetbuddy.backend.category.Category();
        category.setId(1L);
        category.setOwner(user);
        category.setName("Not Shopping");

        de.budgetbuddy.backend.category.Category.Update payload = new de.budgetbuddy.backend.category.Category.Update();
        payload.setCategoryId(1L);
        payload.setName("Shopping");

        session.setAttribute("user", objMapper.writeValueAsString(sessionUser));

        when(categoryRepository.findById(1L)).thenReturn(Optional.of(category));
        when(categoryRepository.findByOwnerAndName(user, payload.getName())).thenReturn(Optional.empty());
        when(categoryRepository.save(any(de.budgetbuddy.backend.category.Category.class))).thenReturn(category);

        ResponseEntity<ApiResponse<de.budgetbuddy.backend.category.Category>> response = categoryController.updateCategory(payload, session);

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertEquals("You can't modify categories from other users", Objects.requireNonNull(response.getBody()).getMessage());
        assertNull(Objects.requireNonNull(response.getBody()).getData());
    }
    @Test
    void testDeleteCategory_EmptyList() throws JsonProcessingException {
        List<Category.Delete> payload = new ArrayList<>();

        ResponseEntity<ApiResponse<Map<String, List<?>>>> response =
                categoryController.deleteCategories(payload, session);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("No categories we're provided",
                Objects.requireNonNull(response.getBody()).getMessage());
        assertNull(Objects.requireNonNull(response.getBody()).getData());
    }

    @Test
    void testDeleteCategory_AllItemsInvalid() throws JsonProcessingException {
        User owner = new User(UUID.randomUUID());
        User sessionUser = new User(UUID.randomUUID());
        session.setAttribute("user", objMapper.writeValueAsString(sessionUser));

        List<Category.Delete> payload = new ArrayList<>();
        payload.add(Category.builder()
                .id(1L)
                .owner(owner)
                .build()
                .toDelete());


        when(categoryRepository.findById(any(Long.class)))
                .thenReturn(Optional.empty());

        ResponseEntity<ApiResponse<Map<String, List<?>>>> response =
                categoryController.deleteCategories(payload, session);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("All provided categories we're invalid values",
                Objects.requireNonNull(response.getBody()).getMessage());
        Map<String, List<?>> responseBody = response.getBody().getData();
        assertEquals(1, responseBody.get("failed").size());
        assertEquals(0, responseBody.get("success").size());
    }

    @Test
    void testDeleteCategory_SomeFailures() throws JsonProcessingException {
        User owner = new User(UUID.randomUUID());
        session.setAttribute("user", objMapper.writeValueAsString(owner));

        List<Category.Delete> payload = new ArrayList<>();
        Category c1 = Category.builder()
                .id(1L)
                .owner(owner)
                .build();
        payload.add(c1.toDelete());

        Category c2 = Category.builder()
                .id(2L)
                .build();
        payload.add(c2.toDelete());

        when(categoryRepository.findById(1L))
                .thenReturn(Optional.of(c1));
        when(categoryRepository.findById(2L))
                .thenReturn(Optional.empty());

        ResponseEntity<ApiResponse<Map<String, List<?>>>> response =
                categoryController.deleteCategories(payload, session);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNull(Objects.requireNonNull(response.getBody()).getMessage());
        Map<String, List<?>> responseBody = response.getBody().getData();
        assertEquals(1, responseBody.get("failed").size());
        assertEquals(1, responseBody.get("success").size());
    }

    @Test
    void testDeleteCategory_WrongSessionUser() throws JsonProcessingException {
        User sessionUser = new User(UUID.randomUUID());
        User owner = new User(UUID.randomUUID());
        session.setAttribute("user", objMapper.writeValueAsString(sessionUser));

        List<Category.Delete> payload = new ArrayList<>();
        Category c1 = Category.builder()
                .id(1L)
                .owner(owner)
                .build();
        payload.add(c1.toDelete());

        Category c2 = Category.builder()
                .id(2L)
                .owner(sessionUser)
                .build();
        payload.add(c2.toDelete());

        when(categoryRepository.findById(1L))
                .thenReturn(Optional.of(c1));
        when(categoryRepository.findById(2L))
                .thenReturn(Optional.of(c2));

        ResponseEntity<ApiResponse<Map<String, List<?>>>> response =
                categoryController.deleteCategories(payload, session);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNull(Objects.requireNonNull(response.getBody()).getMessage());
        Map<String, List<?>> responseBody = response.getBody().getData();
        assertEquals(1, responseBody.get("failed").size());
        assertEquals(1, responseBody.get("success").size());
    }

    @Test
    void testDeleteCategory_Success() throws JsonProcessingException {
        User owner = new User(UUID.randomUUID());
        session.setAttribute("user", objMapper.writeValueAsString(owner));

        List<Category.Delete> payload = new ArrayList<>();
        Category c1 = Category.builder()
                .id(1L)
                .owner(owner)
                .build();
        payload.add(c1.toDelete());

        Category c2 = Category.builder()
                .id(2L)
                .owner(owner)
                .build();
        payload.add(c2.toDelete());

        when(categoryRepository.findById(1L))
                .thenReturn(Optional.of(c1));
        when(categoryRepository.findById(2L))
                .thenReturn(Optional.of(c2));

        ResponseEntity<ApiResponse<Map<String, List<?>>>> response =
                categoryController.deleteCategories(payload, session);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNull(Objects.requireNonNull(response.getBody()).getMessage());
        Map<String, List<?>> responseBody = response.getBody().getData();
        assertEquals(0, responseBody.get("failed").size());
        assertEquals(2, responseBody.get("success").size());
        assertEquals(List.of(c1, c2), responseBody.get("success"));
    }
}