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
        Category.Create payload = new Category.Create();
        payload.setOwner(uuid);

        when(userRepository.findById(uuid)).thenReturn(Optional.empty());

        ResponseEntity<ApiResponse<Category>> response = categoryController.createCategory(payload, session);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertEquals("Provided user doesn't exist", Objects.requireNonNull(response.getBody()).getMessage());
        assertNull(Objects.requireNonNull(response.getBody()).getData());
    }

    @Test
    void testCreateCategory_CategoryAlreadyExists() throws JsonProcessingException {
        UUID uuid = UUID.randomUUID();
        Category.Create payload = new Category.Create();
        payload.setOwner(uuid);
        payload.setName("Shopping");

        Category existingCategory = new Category();
        existingCategory.setName("Shopping");

        User user = new User();
        user.setUuid(uuid);

        when(userRepository.findById(uuid))
                .thenReturn(Optional.of(user));
        when(categoryRepository.findByOwnerAndName(user, payload.getName()))
                .thenReturn(Optional.of(existingCategory));

        ResponseEntity<ApiResponse<Category>> response = categoryController.createCategory(payload, session);

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertEquals("There is already an category by this name",
                Objects.requireNonNull(response.getBody()).getMessage());
        assertNull(Objects.requireNonNull(response.getBody()).getData());
    }

    @Test
    void testCreateCategory_Success() throws JsonProcessingException {
        UUID uuid = UUID.randomUUID();
        Category.Create payload = new Category.Create();
        payload.setOwner(uuid);
        payload.setName("Shopping");

        User user = new User();
        user.setUuid(uuid);

        session.setAttribute("user", objMapper.writeValueAsString(user));

        Category category = new Category(user, payload.getName(), null);

        when(userRepository.findById(uuid)).thenReturn(Optional.of(user));
        when(categoryRepository.findByOwnerAndName(user, payload.getName())).thenReturn(Optional.empty());
        when(categoryRepository.save(any(Category.class))).thenReturn(category);

        ResponseEntity<ApiResponse<Category>> response = categoryController.createCategory(payload, session);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(category, Objects.requireNonNull(response.getBody()).getData());
        assertNull(Objects.requireNonNull(response.getBody()).getMessage());
    }

    @Test
    void testCreateCategory_TryForDiffSessionUser() throws JsonProcessingException {
        UUID uuid = UUID.randomUUID();
        Category.Create payload = new Category.Create();
        payload.setOwner(uuid);
        payload.setName("Shopping");

        User user = new User();
        user.setUuid(uuid);

        Category category = new Category(user, payload.getName(), null);

        User sessionUser = new User();
        sessionUser.setUuid(UUID.randomUUID());
        session.setAttribute("user", objMapper.writeValueAsString(sessionUser));

        when(userRepository.findById(uuid)).thenReturn(Optional.of(user));
        when(categoryRepository.findByOwnerAndName(user, payload.getName())).thenReturn(Optional.empty());
        when(categoryRepository.save(any(Category.class))).thenReturn(category);

        ResponseEntity<ApiResponse<Category>> response = categoryController.createCategory(payload, session);

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

        ResponseEntity<ApiResponse<List<Category>>> response = categoryController.getCategoriesByUuid(uuid, session);

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertEquals("You can't retrieve categories from different users",
                Objects.requireNonNull(response.getBody()).getMessage());
        assertNull(Objects.requireNonNull(response.getBody()).getData());
    }

    @Test
    void retrieveCategoriesWithValidSession() throws JsonProcessingException {
        List<Category> categories = new ArrayList<>();
        UUID uuid = UUID.randomUUID();
        User user = new User();
        user.setUuid(uuid);
        user.setEmail("test@test.com");
        user.setPassword("test");

        when(userRepository.findById(uuid)).thenReturn(Optional.of(user));
        when(categoryRepository.findAllByOwner(user)).thenReturn(categories);

        session.setAttribute("user", objMapper.writeValueAsString(user));
        ResponseEntity<ApiResponse<List<Category>>> response = categoryController.getCategoriesByUuid(uuid, session);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNull(Objects.requireNonNull(response.getBody()).getMessage());
        assertEquals(categories, Objects.requireNonNull(response.getBody()).getData());
    }

    @Test
    void testUpdateCategory_CategoryNotFound() throws JsonProcessingException {
        Category.Update payload = new Category.Update();
        payload.setCategoryId(1L);

        when(categoryRepository.findById(payload.getCategoryId())).thenReturn(Optional.empty());

        ResponseEntity<ApiResponse<Category>> response = categoryController.updateCategory(payload, session);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertEquals("Provided category doesn't exist", Objects.requireNonNull(response.getBody()).getMessage());
        assertNull(Objects.requireNonNull(response.getBody()).getData());
    }

    @Test
    void testUpdateCategory_CategoryNameAlreadyUsed() throws JsonProcessingException {
        User user = new User();
        user.setUuid(UUID.randomUUID());

        Category c1 = new Category();
        c1.setId(1L);
        c1.setName("Shopping");
        c1.setOwner(user);

        Category c2 = new Category();
        c2.setId(2L);
        c2.setName("Not Shopping");
        c2.setOwner(user);

        Category.Update payload = new Category.Update();
        payload.setCategoryId(c2.getId());
        payload.setName(c1.getName());

        when(categoryRepository.findByOwnerAndName(user, payload.getName())).thenReturn(Optional.of(c1));
        when(categoryRepository.findById(2L)).thenReturn(Optional.of(c2));

        session.setAttribute("user", objMapper.writeValueAsString(user));

        ResponseEntity<ApiResponse<Category>> response = categoryController.updateCategory(payload, session);

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertEquals("There is already an category by this name", Objects.requireNonNull(response.getBody()).getMessage());
        assertNull(Objects.requireNonNull(response.getBody()).getData());
    }

    @Test
    void testUpdateCategory_Success() throws JsonProcessingException {
        User user = new User();
        user.setUuid(UUID.randomUUID());

        Category category = new Category();
        category.setId(1L);
        category.setOwner(user);
        category.setName("Not Shopping");

        Category.Update payload = new Category.Update();
        payload.setCategoryId(1L);
        payload.setName("Shopping");

        Category updatedCategory = new Category();
        updatedCategory.setId(1L);
        updatedCategory.setOwner(user);
        updatedCategory.setName("Shopping");

        session.setAttribute("user", objMapper.writeValueAsString(user));

        when(categoryRepository.findById(1L)).thenReturn(Optional.of(category));
        when(categoryRepository.findByOwnerAndName(user, payload.getName())).thenReturn(Optional.empty());
        when(categoryRepository.save(any(Category.class))).thenReturn(updatedCategory);

        ResponseEntity<ApiResponse<Category>> response = categoryController.updateCategory(payload, session);

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

        Category category = new Category();
        category.setId(1L);
        category.setOwner(user);
        category.setName("Not Shopping");

        Category.Update payload = new Category.Update();
        payload.setCategoryId(1L);
        payload.setName("Shopping");

        session.setAttribute("user", objMapper.writeValueAsString(sessionUser));

        when(categoryRepository.findById(1L)).thenReturn(Optional.of(category));
        when(categoryRepository.findByOwnerAndName(user, payload.getName())).thenReturn(Optional.empty());
        when(categoryRepository.save(any(Category.class))).thenReturn(category);

        ResponseEntity<ApiResponse<Category>> response = categoryController.updateCategory(payload, session);

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertEquals("You can't modify categories from other users", Objects.requireNonNull(response.getBody()).getMessage());
        assertNull(Objects.requireNonNull(response.getBody()).getData());
    }

    @Test
    void testDeleteCategory_NotFound() throws JsonProcessingException {
        Category.Delete payload = new Category.Delete();
        payload.setCategoryId(1L);

        when(categoryRepository.findById(payload.getCategoryId())).thenReturn(Optional.empty());

        ResponseEntity<ApiResponse<Category>> response = categoryController.deleteCategory(payload, session);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertEquals("Provided category doesn't exist", Objects.requireNonNull(response.getBody()).getMessage());
        assertNull(Objects.requireNonNull(response.getBody()).getData());
    }

    @Test
    void testDeleteCategory_NoSession() throws JsonProcessingException {
        Category.Delete payload = new Category.Delete();
        payload.setCategoryId(1L);

        Category category = new Category();
        category.setId(payload.getCategoryId());
        category.setName("Shopping");

        when(categoryRepository.findById(payload.getCategoryId())).thenReturn(Optional.of(category));

        ResponseEntity<ApiResponse<Category>> response = categoryController.deleteCategory(payload, session);

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        assertEquals("No valid session found. Sign in first", Objects.requireNonNull(response.getBody()).getMessage());
        assertNull(Objects.requireNonNull(response.getBody()).getData());
    }

    @Test
    void testDeleteCategory_TryForDifferentUser() throws JsonProcessingException {
        User user = new User();
        user.setUuid(UUID.randomUUID());

        User sessionUser = new User();
        sessionUser.setUuid(UUID.randomUUID());

        session.setAttribute("user", objMapper.writeValueAsString(sessionUser));

        Category.Delete payload = new Category.Delete();
        payload.setCategoryId(1L);

        Category category = new Category();
        category.setId(payload.getCategoryId());
        category.setName("Shopping");
        category.setOwner(user);

        when(categoryRepository.findById(payload.getCategoryId())).thenReturn(Optional.of(category));

        ResponseEntity<ApiResponse<Category>> response = categoryController.deleteCategory(payload, session);

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertEquals("You can't delete categories from other users", Objects.requireNonNull(response.getBody()).getMessage());
        assertNull(Objects.requireNonNull(response.getBody()).getData());
    }

    @Test
    void testDeleteCategory_Success() throws JsonProcessingException {
        User user = new User();
        user.setUuid(UUID.randomUUID());

        session.setAttribute("user", objMapper.writeValueAsString(user));

        Category.Delete payload = new Category.Delete();
        payload.setCategoryId(1L);

        Category category = new Category();
        category.setId(payload.getCategoryId());
        category.setName("Shopping");
        category.setOwner(user);

        when(categoryRepository.findById(payload.getCategoryId())).thenReturn(Optional.of(category));

        ResponseEntity<ApiResponse<Category>> response = categoryController.deleteCategory(payload, session);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNull(Objects.requireNonNull(response.getBody()).getMessage());
        assertEquals(category, Objects.requireNonNull(response.getBody()).getData());
    }
}