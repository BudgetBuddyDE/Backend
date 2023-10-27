package de.budgetbuddy.backend.user;

import de.budgetbuddy.backend.ApiResponse;
import de.budgetbuddy.backend.user.avatar.UserAvatar;
import de.budgetbuddy.backend.user.avatar.UserAvatarRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/v1/user")
public class UserController {
    private final UserRepository userRepository;
    private final UserAvatarRepository userAvatarRepository;

    @Autowired
    public UserController(UserRepository userRepository, UserAvatarRepository userAvatarRepository) {
        this.userRepository = userRepository;
        this.userAvatarRepository = userAvatarRepository;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<UserAvatar>> getUser(@RequestParam UUID uuid) {
        Optional<User> user = userRepository.findById(uuid);
        if (user.isEmpty()) {
            return ResponseEntity
                    .status(HttpStatus.NOT_FOUND)
                    .body(new ApiResponse<>(404, "Provided user not found"));
        }

        Optional<UserAvatar> avatar = userAvatarRepository.findByOwner(user.get());
        if (avatar.isEmpty()) {
            return ResponseEntity
                    .status(HttpStatus.NOT_FOUND)
                    .body(new ApiResponse<>(404, "User doens't have a custom avatar"));
        }

        return ResponseEntity
                .status(HttpStatus.OK)
                .body(new ApiResponse<>(avatar.get()));
    }
}
