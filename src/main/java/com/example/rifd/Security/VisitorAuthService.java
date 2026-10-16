package com.example.rifd.Security;

import com.example.rifd.Api.ApiException;
import com.example.rifd.DTOIn.VisitorLoginDTOIn;
import com.example.rifd.DTOIn.VisitorRegisterDTOIn;
import com.example.rifd.DTOOut.VisitorLoginDTOOut;
import com.example.rifd.Entity.Visitor;
import com.example.rifd.Repository.VisitorRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

// visitor page accounts: sign up (signed in right away) + sign in. Same JWT as staff, role VISITOR
@Service
@RequiredArgsConstructor
public class VisitorAuthService {

    private final VisitorRepository visitorRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    @Transactional
    public VisitorLoginDTOOut register(VisitorRegisterDTOIn dto) {
        String email = normalize(dto.email());
        if (visitorRepository.existsByEmail(email)) throw new ApiException("هذا البريد مسجّل مسبقًا، سجّل دخولك");
        Visitor v = new Visitor();
        v.setName(dto.name().trim());
        v.setEmail(email);
        v.setPasswordHash(passwordEncoder.encode(dto.password()));
        try {
            visitorRepository.saveAndFlush(v);
        } catch (DataIntegrityViolationException e) {
            throw new ApiException("هذا البريد مسجّل مسبقًا، سجّل دخولك"); // two sign-ups at the same moment
        }
        return out(v);
    }

    @Transactional(readOnly = true)
    public VisitorLoginDTOOut login(VisitorLoginDTOIn dto) {
        Visitor v = visitorRepository.findByEmail(normalize(dto.email()))
                .filter(x -> passwordEncoder.matches(dto.password(), x.getPasswordHash()))
                .orElseThrow(() -> new ApiException("البريد أو كلمة المرور غير صحيحة")); // same message: no enumeration
        return out(v);
    }

    private VisitorLoginDTOOut out(Visitor v) {
        return new VisitorLoginDTOOut(jwtService.issue(v), v.getId(), v.getName(), v.getEmail(),
                jwtService.getExpirationMinutes());
    }

    private static String normalize(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
