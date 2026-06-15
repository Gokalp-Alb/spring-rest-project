package com.springrest.springrestproject.service.implementations;

import com.springrest.springrestproject.dto.response.user.UserResponse;
import com.springrest.springrestproject.model.AppUser;
import com.springrest.springrestproject.repository.IUserRepo; // Using IUserRepo exclusively
import com.springrest.springrestproject.service.interfaces.IUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Service
@RequiredArgsConstructor
public class UserService implements IUserService {

    private final IUserRepo userRepo;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public UserResponse createUser(AppUser user) {
        // 1. Securely encrypt the real password
        user.setPassword(passwordEncoder.encode(user.getPassword()));

        // 2. Commit the clean entity safely to PostgreSQL
        AppUser savedUser = userRepo.save(user);

        // 3. Return a decoupled response record with masked asterisks
        return new UserResponse(
                savedUser.getId(),
                savedUser.getUsername(),
                savedUser.getRole(),
                "********"
        );
    }

    @Override
    @Transactional(readOnly = true)
    public List<AppUser> getAllUsers() {
        List<AppUser> users = userRepo.findAll();
        users.forEach(user -> user.setPassword("********"));
        return users;
    }
}