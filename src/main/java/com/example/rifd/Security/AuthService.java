package com.example.rifd.Security;

import com.example.rifd.Api.ApiException;
import com.example.rifd.DTOOut.LoginDTOOut;
import com.example.rifd.Entity.Staff;
import com.example.rifd.Repository.StaffRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final StaffRepository staffRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    @Transactional(readOnly = true)
    public LoginDTOOut login(String username, String password) {
        Staff staff = staffRepository.findByUsername(username.trim())
                .filter(s -> s.getPasswordHash() != null && passwordEncoder.matches(password, s.getPasswordHash()))
                .orElseThrow(() -> new ApiException("Invalid username or password")); // same message: no user enumeration

        return new LoginDTOOut(jwtService.issue(staff), staff.getId(), staff.getName(), staff.getRole(),
                jwtService.getExpirationMinutes());
    }
}
